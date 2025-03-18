package com.shopstack.kafka;

import com.shopstack.kafka.events.PaymentRequestedEvent;
import com.shopstack.outbox.OutboxService;
import org.springframework.stereotype.Service;

/**
 * Kafka's payment events producer.
 */
@Service
public class PaymentEventProducer {

    /**
     * Kafka template used to send events.
     */
    private final OutboxService outboxService;

    /**
     * Constructor to inject dependencies.
     *
     * @param outboxService persists events for asynchronous publishing.
     */
    public PaymentEventProducer(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    /**
     * Sends a payment request event to Kafka topic.
     *
     * @param event is the Kafka event object.
     */
    public void sendPaymentRequested(PaymentRequestedEvent event) {
        outboxService.enqueue(
            event.eventId(), "payment.requested", event.orderId().toString(), event
        );
    }
}
