package com.shopstack.kafka.events;

import com.shopstack.utils.ContactType;
import com.shopstack.utils.NotificationType;

import java.util.Map;

/**
 * Event sent to Kafka topic.
 *
 * @param notificationType is the notification type. Ex: PAYMENT_FAILED, USER_REGISTERED.
 * @param contactType is the contact type. Ex: email, sms
 * @param contact is the contact data.
 * @param payload is the payload.
 */
public record NotificationRequestedEvent(
        NotificationType notificationType,
        ContactType contactType,
        String contact,
        Map<String, Object> payload
) {}
