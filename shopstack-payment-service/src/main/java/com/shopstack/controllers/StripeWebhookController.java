package com.shopstack.controllers;

import com.shopstack.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Stripe events. The signature is verified against the raw payload, so
 * the body is read as a string and never deserialised before verification.
 */
@RestController
@RequestMapping("/api/webhooks")
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookService webhookService;
    private final String webhookSecret;

    public StripeWebhookController(StripeWebhookService webhookService,
                                   @Value("${stripe.webhook.secret:}") String webhookSecret) {
        this.webhookService = webhookService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.error("Stripe webhook received but STRIPE_WEBHOOK_SECRET is not configured");
            return ResponseEntity.status(503).body("webhook_not_configured");
        }
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body("missing_signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed");
            return ResponseEntity.badRequest().body("invalid_signature");
        }

        if (!event.getType().startsWith("payment_intent.")) {
            return ResponseEntity.ok("ignored");
        }

        PaymentIntent intent = event.getDataObjectDeserializer().getObject()
                .filter(PaymentIntent.class::isInstance)
                .map(PaymentIntent.class::cast)
                .orElse(null);

        if (intent == null) {
            log.warn("Stripe webhook {} without a payment intent payload", event.getId());
            return ResponseEntity.ok("ignored");
        }

        String failureMessage = intent.getLastPaymentError() != null
                ? intent.getLastPaymentError().getMessage()
                : null;

        StripeWebhookService.Outcome outcome = webhookService.handle(event.getType(), intent.getId(), failureMessage);
        return ResponseEntity.ok(outcome.name().toLowerCase());
    }
}
