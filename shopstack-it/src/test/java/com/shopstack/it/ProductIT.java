package com.shopstack.it;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Product catalogue: seller-only creation, validation, read-back, search and
 * seller scoping.
 */
class ProductIT extends AbstractIT {

    @Test
    void sellerCreatesAProductAndReadsItBack() {
        Account seller = newAccount("SELLER");
        String name = unique("Wireless Mouse");

        long productId = createProduct(seller.accessToken(), name, 19.99);

        api(seller.accessToken())
                .get("/product/{id}", productId)
                .then()
                .statusCode(200)
                .body("id", equalTo(Long.toString(productId)))
                .body("name", equalTo(name))
                .body("description", equalTo("Created by the integration suite"))
                .body("price", equalTo(19.99f))
                .body("categories", notNullValue());
    }

    @Test
    void createdProductAppearsInSellerListingAndSearch() {
        Account seller = newAccount("SELLER");
        String marker = unique("Lamp");
        long productId = createProduct(seller.accessToken(), marker, 42.50);

        JsonPath sellerProducts = api(seller.accessToken())
                .queryParam("page", 0)
                .queryParam("size", 50)
                .get("/product/seller/{username}", seller.username())
                .then().statusCode(200)
                .extract().jsonPath();

        List<String> sellerProductIds = sellerProducts.getList("data.id", String.class);
        assertThat(sellerProductIds).contains(Long.toString(productId));

        JsonPath search = api(seller.accessToken())
                .queryParam("query", marker)
                .queryParam("page", 0)
                .queryParam("size", 50)
                .get("/product/search")
                .then().statusCode(200)
                .extract().jsonPath();

        List<String> searchIds = search.getList("data.id", String.class);
        assertThat(searchIds).containsExactly(Long.toString(productId));
    }

    @Test
    void regularUserCannotCreateAProduct() {
        Account buyer = newAccount("USER");
        Map<String, Object> body = productBody(unique("Forbidden"), 10.00);

        // shopstack-server does not whitelist /error, so a security-filter denial is
        // re-dispatched to /error and surfaces as 401 instead of 403. Both mean "denied".
        api(buyer.accessToken())
                .body(body)
                .post("/product")
                .then()
                .statusCode(anyOf(is(401), is(403)));
    }

    @Test
    void anonymousUserCannotCreateAProduct() {
        api()
                .body(productBody(unique("Anonymous"), 10.00))
                .post("/product")
                .then()
                .statusCode(401);
    }

    @Test
    void productCreationValidatesThePrice() {
        Account seller = newAccount("SELLER");

        api(seller.accessToken())
                .body(productBody(unique("Freebie"), 0.00))
                .post("/product")
                .then()
                .statusCode(400)
                .body("fieldErrors.field", hasItem("price"));
    }

    @Test
    void sellerCanUpdateAndDeleteOwnProduct() {
        Account seller = newAccount("SELLER");
        long productId = createProduct(seller.accessToken(), unique("Desk"), 100.00);
        String updatedName = unique("Standing Desk");

        Map<String, Object> update = productBody(updatedName, 149.00);
        api(seller.accessToken())
                .body(update)
                .put("/product/{id}", productId)
                .then()
                .statusCode(200);

        api(seller.accessToken())
                .get("/product/{id}", productId)
                .then()
                .statusCode(200)
                .body("name", equalTo(updatedName))
                .body("price", equalTo(149.00f));

        api(seller.accessToken())
                .delete("/product/{id}", productId)
                .then()
                .statusCode(200);

        api(seller.accessToken())
                .get("/product/{id}", productId)
                .then()
                .statusCode(404);
    }

    @Test
    void unknownProductReturnsNotFound() {
        api()
                .get("/product/{id}", 999_999L)
                .then()
                .statusCode(404);
    }

    private static Map<String, Object> productBody(String name, double price) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("description", "Created by the integration suite");
        body.put("price", price);
        body.put("categories", List.of());
        return body;
    }
}
