package com.shopstack.service.impl;

import com.shopstack.model.stripe.StripePaymentResult;
import com.stripe.Stripe;
import com.stripe.exception.CardException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests that talk to Stripe in test mode.
 *
 * They only run when STRIPE_API_KEY holds a test key. The dedicated workflow
 * (.github/workflows/stripe-it.yml) runs them on demand so a regular build
 * never depends on the network.
 */
@Tag("stripe")
@EnabledIfEnvironmentVariable(named = "STRIPE_API_KEY", matches = "sk_test_.*")
class StripePaymentServiceIT {

    private static String previousKey;

    private final StripePaymentServiceImpl service = new StripePaymentServiceImpl();

    @BeforeAll
    static void configureStripe() {
        previousKey = Stripe.apiKey;
        Stripe.apiKey = System.getenv("STRIPE_API_KEY");
    }

    @AfterAll
    static void restoreStripe() {
        Stripe.apiKey = previousKey;
    }

    @Test
    void confirmsATestCardPayment() throws Exception {
        StripePaymentResult result = service.charge(
                new BigDecimal("49.99"),
                "usd",
                "pm_card_visa",
                "shopstack-it-" + UUID.randomUUID());

        assertEquals("succeeded", result.getStatus());
        assertTrue(result.getPaymentIntentId().startsWith("pi_"));
    }

    @Test
    void surfacesADeclinedTestCard() {
        CardException error = assertThrows(CardException.class, () -> service.charge(
                new BigDecimal("49.99"),
                "usd",
                "pm_card_visa_chargeDeclined",
                "shopstack-it-declined-" + UUID.randomUUID()));

        assertEquals("card_declined", error.getCode());
    }
}
