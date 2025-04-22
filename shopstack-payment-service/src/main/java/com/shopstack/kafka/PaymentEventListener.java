package com.shopstack.kafka;

import com.shopstack.kafka.events.PaymentRequestedEvent;
import com.shopstack.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka payment events listener.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentService paymentService;

    @KafkaListener(
        topics = "payment.requested",
        groupId = "payment-service"
    )
    public void onPaymentRequested(PaymentRequestedEvent event) {
        paymentService.processPayment(event);
    }
}
