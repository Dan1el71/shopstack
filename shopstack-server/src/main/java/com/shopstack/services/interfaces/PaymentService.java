package com.shopstack.services.interfaces;

import com.shopstack.model.dto.PaymentDTO;

/**
 * Interface for payment service.
 */
public interface PaymentService {
    /**
     * Sends an event to Kafka topic to process the payment for an order.
     *
     * @param orderId is the payment's order ID.
     * @param paymentDTO is the payment's DTO.
     */
    void pay(final Long orderId, PaymentDTO paymentDTO, String idempotencyKey);
}
