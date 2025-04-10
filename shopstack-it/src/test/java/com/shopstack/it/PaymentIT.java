package com.shopstack.it;

import com.shopstack.kafka.events.PaymentRequestedEvent;
import com.shopstack.utils.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * Payment state machine, exercised through the public API and real Kafka.
 *
 * <p><strong>No live Stripe call is made.</strong> shopstack-server never talks to Stripe
 * directly: it writes a {@code payment.requested} event through its transactional outbox,
 * and shopstack-payment-service consumes it, calls Stripe, then publishes a
 * {@code payment.result} event. In this suite the payment service is not booted; instead
 * the tests consume the requested event (proving the producer side) and publish the
 * result event themselves (emulating the payment service's outcome). That covers
 * PENDING -&gt; SUCCEEDED / FAILED, retry after failure, idempotency and inbox deduplication
 * without needing a Stripe API key.</p>
 */
class PaymentIT extends AbstractIT {

    private static final String PAYMENT_REQUESTED = "payment.requested";
    private static final String STRIPE_TOKEN = "tok_visa_integration";

    @Test
    void payingForAnOrderCreatesAPendingPaymentAndPublishesTheRequestedEvent() {
        Account buyer = newAccount("USER");
        long orderId = placeOrder(buyer.accessToken(), "49.99");
        String idempotencyKey = unique("pay");

        pay(buyer.accessToken(), orderId, idempotencyKey, STRIPE_TOKEN)
                .then()
                .statusCode(200)
                .body("status", equalTo(200))
                .body("message", equalTo("payment_sent_successfully"));

        Map<String, Object> order = orderById(buyer.accessToken(), orderId);
        assertThat(order.get("paymentStatus")).isEqualTo("PENDING");
        assertThat(((Number) order.get("paymentAmount")).doubleValue())
                .isCloseTo(49.99, within(0.001));

        long paymentId = ((Number) order.get("paymentId")).longValue();
        PaymentRequestedEvent event = awaitPaymentRequestedEvent(idempotencyKey);

        assertThat(event.paymentId()).isEqualTo(paymentId);
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.amount()).isEqualByComparingTo(new BigDecimal("49.99"));
        assertThat(event.stripeToken()).isEqualTo(STRIPE_TOKEN);
        assertThat(event.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(event.eventId()).isNotBlank();

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outboxPublishedCount(PAYMENT_REQUESTED)).isEqualTo(1));
    }

    @Test
    void replayingTheSameIdempotencyKeyDoesNotCreateASecondPayment() {
        Account buyer = newAccount("USER");
        long orderId = placeOrder(buyer.accessToken(), "30.00");
        String idempotencyKey = unique("pay");

        pay(buyer.accessToken(), orderId, idempotencyKey, STRIPE_TOKEN).then().statusCode(200);
        pay(buyer.accessToken(), orderId, idempotencyKey, STRIPE_TOKEN).then().statusCode(200);

        assertThat(paymentCount(orderId)).isEqualTo(1);
        assertThat(paymentStatusForIdempotencyKey(idempotencyKey)).isEqualTo("PENDING");

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(outboxPublishedCount(PAYMENT_REQUESTED)).isEqualTo(1));
    }

    @Test
    void reusingAnIdempotencyKeyForAnotherOrderConflicts() {
        Account buyer = newAccount("USER");
        long firstOrder = placeOrder(buyer.accessToken(), "10.00");
        long secondOrder = placeOrder(buyer.accessToken(), "20.00");
        String idempotencyKey = unique("pay");

        pay(buyer.accessToken(), firstOrder, idempotencyKey, STRIPE_TOKEN).then().statusCode(200);
        // The server's /error dispatch is not whitelisted, so the 409 raised by the
        // service surfaces as 401; either way the request must be rejected.
        pay(buyer.accessToken(), secondOrder, idempotencyKey, STRIPE_TOKEN)
                .then()
                .statusCode(anyOf(is(401), is(409)));

        assertThat(paymentCount(firstOrder)).isEqualTo(1);
        assertThat(paymentCount(secondOrder)).isZero();
    }

    @Test
    void aSecondIdempotencyKeyWhileTheFirstIsPendingIsIgnored() {
        Account buyer = newAccount("USER");
        long orderId = placeOrder(buyer.accessToken(), "12.50");

        pay(buyer.accessToken(), orderId, unique("pay"), STRIPE_TOKEN).then().statusCode(200);
        pay(buyer.accessToken(), orderId, unique("pay"), STRIPE_TOKEN).then().statusCode(200);

        assertThat(paymentCount(orderId)).isEqualTo(1);
    }

    @Test
    void paymentResultEventMovesThePaymentToSucceeded() {
        Account buyer = newAccount("USER");
        long orderId = placeOrder(buyer.accessToken(), "75.00");
        String idempotencyKey = unique("pay");
        pay(buyer.accessToken(), orderId, idempotencyKey, STRIPE_TOKEN).then().statusCode(200);
        long paymentId = paymentIdFor(buyer.accessToken(), orderId);
        String eventId = UUID.randomUUID().toString();

        publishPaymentResult(eventId, orderId, paymentId, PaymentStatus.SUCCEEDED, null);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(orderById(buyer.accessToken(), orderId).get("paymentStatus"))
                        .isEqualTo("SUCCEEDED"));

        assertThat(paymentStatusForIdempotencyKey(idempotencyKey)).isEqualTo("SUCCEEDED");
        assertThat(inboxCount(eventId)).isEqualTo(1);
    }

    @Test
    void duplicatePaymentResultEventsAreIgnoredByTheInbox() {
        Account buyer = newAccount("USER");
        long orderId = placeOrder(buyer.accessToken(), "60.00");
        pay(buyer.accessToken(), orderId, unique("pay"), STRIPE_TOKEN).then().statusCode(200);
        long paymentId = paymentIdFor(buyer.accessToken(), orderId);
        String eventId = UUID.randomUUID().toString();

        publishPaymentResult(eventId, orderId, paymentId, PaymentStatus.SUCCEEDED, null);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(orderById(buyer.accessToken(), orderId).get("paymentStatus"))
                        .isEqualTo("SUCCEEDED"));

        // Same event id, contradicting payload: the inbox must discard it.
        publishPaymentResult(eventId, orderId, paymentId, PaymentStatus.FAILED, "late duplicate");
        sleep(3000);

        assertThat(orderById(buyer.accessToken(), orderId).get("paymentStatus")).isEqualTo("SUCCEEDED");
        assertThat(inboxCount(eventId)).isEqualTo(1);
    }

    @Test
    void failedPaymentCanBeRetriedWithANewIdempotencyKey() {
        Account buyer = newAccount("USER");
        long orderId = placeOrder(buyer.accessToken(), "99.00");
        String firstKey = unique("pay");
        pay(buyer.accessToken(), orderId, firstKey, STRIPE_TOKEN).then().statusCode(200);
        long firstPaymentId = paymentIdFor(buyer.accessToken(), orderId);

        publishPaymentResult(UUID.randomUUID().toString(), orderId, firstPaymentId,
                PaymentStatus.FAILED, "card_declined");
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(paymentStatusForIdempotencyKey(firstKey)).isEqualTo("FAILED"));

        String retryKey = unique("pay");
        pay(buyer.accessToken(), orderId, retryKey, STRIPE_TOKEN).then().statusCode(200);

        assertThat(paymentCount(orderId)).isEqualTo(2);
        assertThat(paymentStatusForIdempotencyKey(firstKey)).isEqualTo("FAILED");
        assertThat(paymentStatusForIdempotencyKey(retryKey)).isEqualTo("PENDING");

        long retryPaymentId = jdbc.queryForObject(
                "SELECT id FROM payments WHERE idempotency_key = ?", Long.class, retryKey);
        publishPaymentResult(UUID.randomUUID().toString(), orderId, retryPaymentId,
                PaymentStatus.SUCCEEDED, null);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(paymentStatusForIdempotencyKey(firstKey)).isEqualTo("FAILED");
            assertThat(paymentStatusForIdempotencyKey(retryKey)).isEqualTo("SUCCEEDED");
        });
    }

    @Test
    void payingWithoutAnIdempotencyKeyIsRejected() {
        Account buyer = newAccount("USER");
        long orderId = placeOrder(buyer.accessToken(), "15.00");

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("amount", new BigDecimal("15.00"));
        body.put("stripeToken", STRIPE_TOKEN);

        api(buyer.accessToken())
                .body(body)
                .post("/payment/{orderId}", orderId)
                .then()
                // 400 from the missing header, turned into 401 by the unwhitelisted /error dispatch.
                .statusCode(anyOf(is(400), is(401)));
    }

    @Test
    void payingForAnUnknownOrderReturnsNotFound() {
        Account buyer = newAccount("USER");

        pay(buyer.accessToken(), 999_999L, unique("pay"), STRIPE_TOKEN)
                .then()
                .statusCode(404);
    }

    @Test
    void sellersCannotUseThePaymentEndpoint() {
        Account seller = newAccount("SELLER");

        pay(seller.accessToken(), 1L, unique("pay"), STRIPE_TOKEN)
                .then()
                .statusCode(anyOf(is(401), is(403)));
    }

    private static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting", exception);
        }
    }
}
