package com.shopstack.kafka;

import com.shopstack.kafka.events.NotificationRequestedEvent;
import com.shopstack.kafka.events.PaymentResultEvent;
import com.shopstack.model.Payment;
import com.shopstack.model.User;
import com.shopstack.outbox.OutboxService;
import com.shopstack.repositories.PaymentRepository;
import com.shopstack.utils.ContactType;
import com.shopstack.utils.NotificationType;
import com.shopstack.utils.PaymentStatus;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka's notification events producer.
 */
@Service
@AllArgsConstructor
public class NotificationEventProducer {

    /**
     * Payment repository.
     */
    private final PaymentRepository paymentRepository;

    /**
     * Kafka template used to send events.
     */
    private final OutboxService outboxService;

    /**
     * Sends a payment notification request event to Kafka topic.
     *
     * @param event is the Kafka event object.
     */
    public void sendPaymentNotificationRequested(PaymentResultEvent event) {
        Payment payment = this.paymentRepository.findById(event.paymentId()).orElse(null);
        if (payment == null) {
            return;
        }
        User user = payment.getOrder().getUser();

        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentId", payment.getId());
        payload.put("orderId", event.orderId());
        payload.put("amount", payment.getAmount());

        NotificationRequestedEvent notificationRequestedEvent =
                new NotificationRequestedEvent(
                        PaymentStatus.SUCCEEDED.equals(event.status()) ? NotificationType.PAYMENT_SUCCESS : NotificationType.PAYMENT_FAILED,
                        ContactType.EMAIL,
                        user.getEmail(),
                        payload
                );

        outboxService.enqueue(
            "notification.requested", event.paymentId().toString(), notificationRequestedEvent
        );
    }

    /**
     * Sends a welcome notification request event to Kafka topic.
     */
    public void sendWelcomeNotificationRequested(final String email) {
        NotificationRequestedEvent notificationRequestedEvent =
                new NotificationRequestedEvent(
                        NotificationType.USER_REGISTERED,
                        ContactType.EMAIL,
                        email,
                        null
                );

        outboxService.enqueue("notification.requested", email, notificationRequestedEvent);
    }
}
