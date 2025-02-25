package com.shopstack.service;

import com.shopstack.kafka.events.PaymentRequestedEvent;

/**
 * Interface for payment service.
 */
public interface PaymentService {
    /**
     * Processes the payment.
     *
     * @param event is the event from Kafka topic.
     */
    void processPayment(PaymentRequestedEvent event);
}
