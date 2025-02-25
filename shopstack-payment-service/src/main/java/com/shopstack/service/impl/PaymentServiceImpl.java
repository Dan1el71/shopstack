package com.shopstack.service.impl;

import com.shopstack.kafka.PaymentResultProducer;
import com.shopstack.kafka.events.PaymentRequestedEvent;
import com.shopstack.inbox.InboxEvent;
import com.shopstack.inbox.InboxEventRepository;
import com.shopstack.model.Payment;
import com.shopstack.model.stripe.StripePaymentResult;
import com.shopstack.repository.PaymentRepository;
import com.shopstack.service.PaymentService;
import com.shopstack.service.StripePaymentService;
import com.shopstack.utils.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

/**
 * Payment service implementation.
 */
@Service
@AllArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    /**
     * Stripe payment service.
     */
    private final StripePaymentService stripeService;

    /**
     * Payment repository.
     */
    private final PaymentRepository paymentRepository;

    /**
     * Producer that produces Kafka payment result event.
     */
    private final PaymentResultProducer resultProducer;

    private final InboxEventRepository inboxEventRepository;

    /**
     * Processes the payment.
     *
     * @param event is the event from Kafka topic.
     */
    @Override
    @Transactional
    public void processPayment(PaymentRequestedEvent event) {
        String eventId = event.eventId() != null
            ? event.eventId()
            : "payment-request:" + event.paymentId();
        if (inboxEventRepository.existsById(eventId)) {
            log.info("Ignoring duplicate payment request event {}", eventId);
            return;
        }
        inboxEventRepository.saveAndFlush(new InboxEvent(eventId, "payment.requested"));

        if (paymentRepository.findByPaymentId(event.paymentId()).isPresent()) {
            log.info("Ignoring duplicate payment event for server payment ID {}", event.paymentId());
            return;
        }

        StripePaymentResult result;
        try {
            result = stripeService.charge(
                event.amount(), "EUR", event.stripeToken(), event.idempotencyKey()
            );
        } catch (Exception e) {
            log.error("Payment for order with ID {} couldn't be made.", event.orderId(), e);
            paymentRepository.save(
                    new Payment(event.paymentId(), event.orderId(), null, event.amount(), PaymentStatus.FAILED)
            );
            resultProducer.sendFailed(event.orderId(), event.paymentId(), e.getMessage());
            return;
        }

        paymentRepository.save(new Payment(
            event.paymentId(), event.orderId(), result.getPaymentIntentId(),
            event.amount(), PaymentStatus.SUCCEEDED
        ));
        resultProducer.sendSucceeded(event.orderId(), event.paymentId());
        log.info("Payment processed successfully for order with ID {} (server payment ID: {})", event.orderId(), event.paymentId());
    }
}
