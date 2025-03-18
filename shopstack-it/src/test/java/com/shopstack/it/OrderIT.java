package com.shopstack.it;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

/**
 * Order creation and the per-user order history, including the payment fields that are
 * null until a payment is requested.
 */
class OrderIT extends AbstractIT {

    @Test
    void placingAnOrderPersistsItForTheCurrentUser() {
        Account buyer = newAccount("USER");

        long orderId = placeOrder(buyer.accessToken(), "49.99");

        JsonPath orders = myOrders(buyer.accessToken());
        assertThat(orders.getLong("totalItems")).isEqualTo(1);

        Map<String, Object> order = orderById(buyer.accessToken(), orderId);
        assertThat(((Number) order.get("totalAmount")).doubleValue()).isCloseTo(49.99, within(0.001));
        assertThat(order.get("orderStatus")).isEqualTo("CREATED");
        assertThat(order.get("paymentId")).isNull();
        assertThat(order.get("paymentStatus")).isNull();
    }

    @Test
    void ordersAreScopedToTheAuthenticatedUser() {
        Account buyer = newAccount("USER");
        Account otherBuyer = newAccount("USER");

        long firstOrder = placeOrder(buyer.accessToken(), "10.00");
        long secondOrder = placeOrder(buyer.accessToken(), "20.00");

        JsonPath buyerOrders = myOrders(buyer.accessToken());
        assertThat(buyerOrders.getLong("totalItems")).isEqualTo(2);
        assertThat(buyerOrders.getList("data.orderId", Long.class))
                .containsExactlyInAnyOrder(firstOrder, secondOrder);

        JsonPath otherOrders = myOrders(otherBuyer.accessToken());
        assertThat(otherOrders.getLong("totalItems")).isZero();
        assertThat(otherOrders.getList("data")).isEmpty();
    }

    @Test
    void placingAnOrderWithoutAmountIsRejected() {
        Account buyer = newAccount("USER");
        Map<String, Object> body = new HashMap<>();
        body.put("status", "CREATED");

        api(buyer.accessToken())
                .body(body)
                .post("/order")
                .then()
                .statusCode(400)
                .body("fieldErrors.field", hasItem("totalAmount"));
    }

    @Test
    void placingAnOrderRequiresAuthentication() {
        Map<String, Object> body = new HashMap<>();
        body.put("totalAmount", 12.34);
        body.put("status", "CREATED");

        api()
                .body(body)
                .post("/order")
                .then()
                .statusCode(401);
    }

    @Test
    void orderListingIsPaginated() {
        Account buyer = newAccount("USER");
        long olderOrder = placeOrder(buyer.accessToken(), "5.00");
        long newerOrder = placeOrder(buyer.accessToken(), "6.00");

        JsonPath page = api(buyer.accessToken())
                .queryParam("page", 0)
                .queryParam("size", 1)
                .get("/order/my-orders")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(2))
                .body("pageSize", equalTo(1))
                .body("totalPages", equalTo(2))
                .extract().jsonPath();

        assertThat(page.getList("data.orderId", Long.class)).hasSize(1);
        assertThat(page.getList("data.orderId", Long.class)).containsAnyOf(olderOrder, newerOrder);
    }
}
