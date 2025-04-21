package com.shopstack.service;

import com.shopstack.kafka.PaymentResultProducer;
import com.shopstack.model.Payment;
import com.shopstack.repository.PaymentRepository;
import com.shopstack.utils.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentResultProducer paymentResultProducer;

    @InjectMocks
    private StripeWebhookService service;

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment();
        payment.setPaymentId(7L);
        payment.setOrderId(11L);
        payment.setStripePaymentId("pi_123");
    }

    @Test
    void succeededEventMarksPaymentAndPublishes() {
        when(paymentRepository.findByStripePaymentId("pi_123")).thenReturn(Optional.of(payment));

        var outcome = service.handle("payment_intent.succeeded", "pi_123", null);

        assertEquals(StripeWebhookService.Outcome.PROCESSED, outcome);
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        verify(paymentResultProducer).sendSucceeded(11L, 7L);
    }

    @Test
    void failedEventMarksPaymentAndPublishesTheReason() {
        when(paymentRepository.findByStripePaymentId("pi_123")).thenReturn(Optional.of(payment));

        var outcome = service.handle("payment_intent.payment_failed", "pi_123", "card declined");

        assertEquals(StripeWebhookService.Outcome.PROCESSED, outcome);
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(paymentResultProducer).sendFailed(11L, 7L, "card declined");
    }

    @Test
    void alreadyFinalPaymentIsNotPublishedTwice() {
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByStripePaymentId("pi_123")).thenReturn(Optional.of(payment));

        var outcome = service.handle("payment_intent.succeeded", "pi_123", null);

        assertEquals(StripeWebhookService.Outcome.DUPLICATE, outcome);
        verifyNoInteractions(paymentResultProducer);
    }

    @Test
    void unknownPaymentIsIgnored() {
        when(paymentRepository.findByStripePaymentId("pi_missing")).thenReturn(Optional.empty());

        var outcome = service.handle("payment_intent.succeeded", "pi_missing", null);

        assertEquals(StripeWebhookService.Outcome.UNKNOWN_PAYMENT, outcome);
        verifyNoInteractions(paymentResultProducer);
    }
}
