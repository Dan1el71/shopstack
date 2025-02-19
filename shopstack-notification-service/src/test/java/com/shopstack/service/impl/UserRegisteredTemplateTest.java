package com.shopstack.service.impl;

import com.shopstack.model.NotificationContent;
import com.shopstack.utils.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserRegisteredTemplate.
 */
class UserRegisteredTemplateTest {

    private UserRegisteredTemplate template;

    @BeforeEach
    void setUp() {
        template = new UserRegisteredTemplate();
    }

    @Test
    void testGetTypeReturnsUserRegistered() {
        assertEquals(NotificationType.USER_REGISTERED, template.getType());
    }

    @Test
    void testBuildCreatesWelcomeMessage() {
        Map<String, Object> data = new HashMap<>();

        NotificationContent content = template.build(data);

        assertNotNull(content);
        assertEquals("Welcome to Shopstack!", content.subject());
        assertEquals("We're happy to see you using our platform.", content.body());
    }
}
