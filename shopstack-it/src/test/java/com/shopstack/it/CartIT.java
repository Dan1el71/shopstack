package com.shopstack.it;

import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.equalTo;

/**
 * Cart lifecycle: add a seller's product, inspect the cart, change quantity, remove and
 * clear. Cart ownership is enforced by the server and checked here as well.
 */
class CartIT extends AbstractIT {

    @Test
    void addingAProductToTheCartStoresAPriceSnapshot() {
        Account seller = newAccount("SELLER");
        Account buyer = newAccount("USER");
        String productName = unique("Mechanical Keyboard");
        long productId = createProduct(seller.accessToken(), productName, 19.99);

        addToCart(buyer.accessToken(), productId, 2);

        JsonPath cart = cart(buyer.accessToken(), buyer.username());
        assertThat(cart.getInt("totalItems")).isEqualTo(1);
        assertThat(cart.getLong("data[0].productId")).isEqualTo(productId);
        assertThat(cart.getString("data[0].name")).isEqualTo(productName);
        assertThat(cart.getDouble("data[0].price")).isCloseTo(19.99, within(0.001));
        assertThat(cart.getInt("data[0].quantity")).isEqualTo(2);
        assertThat(cart.getDouble("totalPrice")).isCloseTo(39.98, within(0.001));
    }

    @Test
    void changingTheQuantityUpdatesTheCartTotal() {
        Account seller = newAccount("SELLER");
        Account buyer = newAccount("USER");
        long productId = createProduct(seller.accessToken(), unique("USB Hub"), 10.00);
        addToCart(buyer.accessToken(), productId, 1);

        long cartItemId = cart(buyer.accessToken(), buyer.username()).getLong("data[0].id");

        api(buyer.accessToken())
                .queryParam("quantity", 4)
                .post("/cartItem/{id}/quantity", cartItemId)
                .then()
                .statusCode(200);

        JsonPath cart = cart(buyer.accessToken(), buyer.username());
        assertThat(cart.getInt("data[0].quantity")).isEqualTo(4);
        assertThat(cart.getDouble("totalPrice")).isCloseTo(40.00, within(0.001));
    }

    @Test
    void deletingACartItemEmptiesTheCart() {
        Account seller = newAccount("SELLER");
        Account buyer = newAccount("USER");
        long productId = createProduct(seller.accessToken(), unique("Monitor Arm"), 25.00);
        addToCart(buyer.accessToken(), productId, 1);

        long cartItemId = cart(buyer.accessToken(), buyer.username()).getLong("data[0].id");

        api(buyer.accessToken())
                .delete("/cartItem/{id}", cartItemId)
                .then()
                .statusCode(200);

        JsonPath cart = cart(buyer.accessToken(), buyer.username());
        assertThat(cart.getInt("totalItems")).isZero();
        assertThat(cart.getDouble("totalPrice")).isZero();
    }

    @Test
    void clearingTheCartRemovesEveryItem() {
        Account seller = newAccount("SELLER");
        Account buyer = newAccount("USER");
        long firstProduct = createProduct(seller.accessToken(), unique("Cable"), 5.00);
        long secondProduct = createProduct(seller.accessToken(), unique("Adapter"), 7.50);
        addToCart(buyer.accessToken(), firstProduct, 1);
        addToCart(buyer.accessToken(), secondProduct, 2);

        assertThat(cart(buyer.accessToken(), buyer.username()).getInt("totalItems")).isEqualTo(2);

        api(buyer.accessToken())
                .delete("/cart/clear")
                .then()
                .statusCode(200);

        JsonPath cart = cart(buyer.accessToken(), buyer.username());
        assertThat(cart.getInt("totalItems")).isZero();
        assertThat(cart.getDouble("totalPrice")).isZero();
    }

    @Test
    void oneUserCannotReadAnotherUsersCart() {
        Account seller = newAccount("SELLER");
        Account buyer = newAccount("USER");
        Account intruder = newAccount("USER");
        long productId = createProduct(seller.accessToken(), unique("Webcam"), 30.00);
        addToCart(buyer.accessToken(), productId, 1);

        api(intruder.accessToken())
                .queryParam("username", buyer.username())
                .get("/cartItem")
                .then()
                .statusCode(403)
                .body("error", equalTo("access_denied"));
    }

    @Test
    void addingToTheCartRequiresAuthentication() {
        Account seller = newAccount("SELLER");
        long productId = createProduct(seller.accessToken(), unique("Speaker"), 45.00);

        api()
                .queryParam("quantity", 1)
                .get("/product/{id}/add", productId)
                .then()
                .statusCode(401);
    }
}
