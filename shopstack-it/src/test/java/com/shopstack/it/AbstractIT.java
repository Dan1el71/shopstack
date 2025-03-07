package com.shopstack.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopstack.EcommerceServerApplication;
import com.shopstack.kafka.events.PaymentRequestedEvent;
import com.shopstack.kafka.events.PaymentResultEvent;
import com.shopstack.utils.PaymentStatus;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for the black-box integration suite.
 *
 * <p>It boots the real {@link EcommerceServerApplication} on a random port against
 * MySQL 8, Kafka (KRaft) and Redis 7 containers. Flyway owns the schema; the application
 * runs with {@code ddl-auto=validate}, so the tests also assert that the JPA mappings and
 * the migrations agree.</p>
 *
 * <p>Every test starts from an empty business schema. Reference data (roles and
 * categories) is seeded by the Flyway migrations and is intentionally kept.</p>
 */
@SpringBootTest(
        classes = EcommerceServerApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=" + AbstractIT.JWT_SECRET,
                "app.outbox.poll-interval-ms=200",
                "spring.jpa.hibernate.ddl-auto=validate",
                "spring.datasource.hikari.maximum-pool-size=6",
                "spring.main.banner-mode=off",
                "management.endpoint.health.show-details=always",
                "logging.level.org.apache.kafka=WARN",
                "logging.level.org.hibernate.SQL=WARN",
                "logging.level.org.testcontainers=INFO"
        })
public abstract class AbstractIT {

    /**
     * HS512 signing key (96 random bytes, base64 encoded). Test-only, never used outside CI.
     */
    static final String JWT_SECRET =
            "GjatI995FV7mAcHUvAXp+BjQWhkEuq9AZ4FydPbPjqVFMPEPq+rCDDN9kiqxSOR54jFhcqxk1zw/sX4Fy+E63WThxLvjwwo0EGLtR/ouK+vPWSnWNNS9W80JV5B5tO/E";

    /**
     * Password used for every generated account.
     */
    protected static final String PASSWORD = "Str0ngPass!";

    private static final String PAYMENT_REQUESTED_TOPIC = "payment.requested";
    private static final String PAYMENT_RESULT_TOPIC = "payment.result";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("shopstack")
            .withUsername("shopstack")
            .withPassword("shopstack")
            .withStartupTimeout(Duration.ofMinutes(3));

    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.7.0"))
                    .withStartupTimeout(Duration.ofMinutes(3));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort())
                    .withStartupTimeout(Duration.ofMinutes(2));

    static {
        MYSQL.start();
        KAFKA.start();
        REDIS.start();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    /**
     * Tables truncated before every test. Ordered child-first; foreign key checks are
     * disabled around the loop so the order is not load-bearing.
     */
    private static final List<String> BUSINESS_TABLES = List.of(
            "inbox_events",
            "outbox_events",
            "payments",
            "orders",
            "cart_items",
            "products_categories",
            "products",
            "user_roles",
            "refresh_tokens",
            "users",
            "carts"
    );

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RedisConnectionFactory redisConnectionFactory;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> withMysqlParams(MYSQL.getJdbcUrl()));
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @BeforeEach
    void resetState() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : BUSINESS_TABLES) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            connection.serverCommands().flushDb();
        }

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
        RestAssured.basePath = "/api";
    }

    // ---------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------

    protected RequestSpecification api() {
        return given().contentType(ContentType.JSON).accept(ContentType.JSON);
    }

    protected RequestSpecification api(String accessToken) {
        return api().header("Authorization", "Bearer " + accessToken);
    }

    protected String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected record Account(String username, String accessToken) {
    }

    protected void register(String role, String username, String password) {
        Map<String, Object> body = new HashMap<>();
        body.put("role", role);
        body.put("email", username + "@shopstack.test");
        body.put("username", username);
        body.put("password", password);

        api().body(body)
                .post("/auth/register")
                .then().statusCode(200);
    }

    protected String login(String username, String password) {
        return api().body(Map.of("username", username, "password", password))
                .post("/auth/login")
                .then().statusCode(200)
                .extract().path("accessToken");
    }

    /**
     * Registers a fresh account with the given role and logs it in.
     */
    protected Account newAccount(String role) {
        String username = unique(role.toLowerCase(Locale.ROOT));
        register(role, username, PASSWORD);
        return new Account(username, login(username, PASSWORD));
    }

    protected long createProduct(String accessToken, String name, double price) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", "Created by the integration suite");
        body.put("price", price);
        body.put("categories", List.of());

        return api(accessToken).body(body)
                .post("/product")
                .then().statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    protected void addToCart(String accessToken, long productId, int quantity) {
        api(accessToken)
                .queryParam("quantity", quantity)
                .get("/product/{id}/add", productId)
                .then().statusCode(200);
    }

    protected JsonPath cart(String accessToken, String username) {
        return api(accessToken)
                .queryParam("username", username)
                .queryParam("page", 0)
                .queryParam("size", 50)
                .get("/cartItem")
                .then().statusCode(200)
                .extract().jsonPath();
    }

    protected long placeOrder(String accessToken, String totalAmount) {
        Map<String, Object> body = new HashMap<>();
        body.put("totalAmount", new BigDecimal(totalAmount));
        body.put("status", "CREATED");

        return api(accessToken).body(body)
                .post("/order")
                .then().statusCode(200)
                .extract().jsonPath().getLong("id");
    }

    protected Response pay(String accessToken, long orderId, String idempotencyKey, String stripeToken) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("amount", new BigDecimal("1.00"));
        body.put("stripeToken", stripeToken);

        return api(accessToken)
                .header("Idempotency-Key", idempotencyKey)
                .body(body)
                .post("/payment/{orderId}", orderId);
    }

    protected JsonPath myOrders(String accessToken) {
        return api(accessToken)
                .queryParam("page", 0)
                .queryParam("size", 50)
                .get("/order/my-orders")
                .then().statusCode(200)
                .extract().jsonPath();
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> orderById(String accessToken, long orderId) {
        List<Map<String, Object>> orders = myOrders(accessToken).getList("data");
        return orders.stream()
                .filter(order -> ((Number) order.get("orderId")).longValue() == orderId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Order " + orderId + " not returned by /order/my-orders"));
    }

    // ---------------------------------------------------------------------
    // Kafka helpers
    // ---------------------------------------------------------------------

    /**
     * Consumes the {@code payment.requested} event produced for the given idempotency key.
     *
     * <p>Order and payment ids are reset by the per-test truncation, so the idempotency
     * key (unique per test) is the only safe correlation id when the topic still holds
     * records from earlier tests.</p>
     *
     * <p>This is the message that the real payment service consumes before calling
     * Stripe; asserting it proves the outbox publisher, Kafka template and event
     * serialization work without any Stripe dependency.</p>
     */
    protected PaymentRequestedEvent awaitPaymentRequestedEvent(String idempotencyKey) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-payment-requested-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(PAYMENT_REQUESTED_TOPIC));
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    PaymentRequestedEvent event = readEvent(record.value(), PaymentRequestedEvent.class);
                    if (idempotencyKey.equals(event.idempotencyKey())) {
                        return event;
                    }
                }
            }
        }
        throw new AssertionError("No payment.requested event received for key " + idempotencyKey);
    }

    /**
     * Publishes a {@code payment.result} event, emulating what shopstack-payment-service
     * sends after it has called Stripe. The server's real Kafka listener consumes it and
     * updates the payment aggregate; no Stripe SDK or API key is involved.
     */
    protected void publishPaymentResult(String eventId, long orderId, long paymentId,
                                        PaymentStatus status, String errorMessage) {
        PaymentResultEvent event = new PaymentResultEvent(eventId, orderId, paymentId, status, errorMessage);

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(PAYMENT_RESULT_TOPIC, Long.toString(orderId), event))
                    .get(20, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not publish payment.result event", exception);
        }
    }

    protected long paymentIdFor(String accessToken, long orderId) {
        Object paymentId = orderById(accessToken, orderId).get("paymentId");
        assertThat(paymentId).as("payment id for order %s", orderId).isNotNull();
        return ((Number) paymentId).longValue();
    }

    protected int paymentCount(long orderId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, orderId);
        return count == null ? 0 : count;
    }

    protected int outboxPublishedCount(String topic) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE topic = ? AND published_at IS NOT NULL",
                Integer.class, topic);
        return count == null ? 0 : count;
    }

    protected int inboxCount(String eventId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inbox_events WHERE event_id = ?", Integer.class, eventId);
        return count == null ? 0 : count;
    }

    protected String paymentStatusForIdempotencyKey(String idempotencyKey) {
        return jdbc.queryForObject(
                "SELECT status FROM payments WHERE idempotency_key = ?", String.class, idempotencyKey);
    }

    private static <T> T readEvent(String payload, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(payload, type);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not deserialize Kafka payload: " + payload, exception);
        }
    }

    private static String withMysqlParams(String jdbcUrl) {
        String separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "useSSL=false&allowPublicKeyRetrieval=true";
    }
}
