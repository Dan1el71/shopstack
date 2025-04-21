package com.shopstack.service;

import com.shopstack.kafka.PaymentResultProducer;
import com.shopstack.model.Payment;
import com.shopstack.repository.PaymentRepository;
import com.shopstack.utils.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies Stripe payment events to the local payment record and notifies the
 * order service. The operation is idempotent: a payment that already reached a
 * final state is never published twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final PaymentRepository paymentRepository;
    private final PaymentResultProducer paymentResultProducer;

    public enum Outcome {
        PROCESSED,
        DUPLICATE,
        UNKNOWN_PAYMENT,
        IGNORED
    }

    @Transactional
    public Outcome handle(String eventType, String paymentIntentId, String failureMessage) {
        Payment payment = paymentRepository.findByStripePaymentId(paymentIntentId).orElse(null);
        if (payment == null) {
            log.warn("Stripe webhook for an unknown payment intent: {}", paymentIntentId);
            return Outcome.UNKNOWN_PAYMENT;
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Stripe webhook ignored, payment {} is already {}", payment.getPaymentId(), payment.getStatus());
            return Outcome.DUPLICATE;
        }

        if ("payment_intent.succeeded".equals(eventType)) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            paymentRepository.save(payment);
            paymentResultProducer.sendSucceeded(payment.getOrderId(), payment.getPaymentId());
            log.info("Payment {} marked as succeeded from Stripe webhook", payment.getPaymentId());
            return Outcome.PROCESSED;
        }

        if ("payment_intent.payment_failed".equals(eventType)) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            paymentResultProducer.sendFailed(payment.getOrderId(), payment.getPaymentId(),
                    failureMessage != null ? failureMessage : "Payment failed");
            log.info("Payment {} marked as failed from Stripe webhook", payment.getPaymentId());
            return Outcome.PROCESSED;
        }

        return Outcome.IGNORED;
    }
}
