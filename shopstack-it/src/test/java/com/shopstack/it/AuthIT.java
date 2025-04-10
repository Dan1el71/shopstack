package com.shopstack.it;

import io.restassured.http.Cookie;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;

/**
 * Register / login / refresh / logout, plus the security rules protecting the API.
 */
class AuthIT extends AbstractIT {

    @Test
    void registeredUserCanLogInAndUseTheAccessToken() {
        String username = unique("buyer");
        register("USER", username, PASSWORD);

        String accessToken = login(username, PASSWORD);

        assertThat(accessToken).isNotBlank();
        api(accessToken)
                .queryParam("username", username)
                .get("/cartItem")
                .then()
                .statusCode(200)
                .body("totalItems", equalTo(0));
    }

    @Test
    void registrationReturnsSuccessPayload() {
        String username = unique("buyer");

        Map<String, Object> body = new HashMap<>();
        body.put("role", "USER");
        body.put("email", username + "@shopstack.test");
        body.put("username", username);
        body.put("password", PASSWORD);

        api().body(body)
                .post("/auth/register")
                .then()
                .statusCode(200)
                .body("status", equalTo(200))
                .body("message", equalTo("user_registered_successfully"));
    }

    @Test
    void duplicateUsernameIsRejectedWithConflict() {
        String username = unique("buyer");
        register("USER", username, PASSWORD);

        Map<String, Object> duplicate = new HashMap<>();
        duplicate.put("role", "USER");
        duplicate.put("email", unique("other") + "@shopstack.test");
        duplicate.put("username", username);
        duplicate.put("password", PASSWORD);

        api().body(duplicate)
                .post("/auth/register")
                .then()
                .statusCode(409)
                .body("status", equalTo(409))
                .body("error", equalTo("username_taken"));
    }

    @Test
    void registrationValidatesRequiredFields() {
        Map<String, Object> body = new HashMap<>();
        body.put("role", "USER");
        body.put("email", "not-an-email");
        body.put("username", "ab");
        body.put("password", "");

        api().body(body)
                .post("/auth/register")
                .then()
                .statusCode(400)
                .body("fieldErrors", notNullValue());
    }

    @Test
    void loginReturnsJwtAndHttpOnlyRefreshCookie() {
        String username = unique("buyer");
        register("USER", username, PASSWORD);

        Response response = api()
                .body(Map.of("username", username, "password", PASSWORD))
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue())
                .body("tokenType", startsWith("Bearer"))
                .extract().response();

        Cookie refreshCookie = response.getDetailedCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.getValue()).isNotBlank();
        assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly");
    }

    @Test
    void loginWithWrongPasswordIsRejected() {
        String username = unique("buyer");
        register("USER", username, PASSWORD);

        api().body(Map.of("username", username, "password", "wrong-password"))
                .post("/auth/login")
                .then()
                .statusCode(400);
    }

    @Test
    void loginWithUnknownUserReturnsNotFound() {
        api().body(Map.of("username", unique("ghost"), "password", PASSWORD))
                .post("/auth/login")
                .then()
                .statusCode(404)
                .body("error", equalTo("username_not_found"));
    }

    @Test
    void refreshCookieIssuesANewUsableAccessToken() {
        String username = unique("buyer");
        register("USER", username, PASSWORD);

        Response loginResponse = api()
                .body(Map.of("username", username, "password", PASSWORD))
                .post("/auth/login")
                .then().statusCode(200)
                .extract().response();
        String refreshToken = loginResponse.getCookie("refreshToken");

        Response refreshResponse = given()
                .contentType("application/json")
                .cookie("refreshToken", refreshToken)
                .post("/auth/refresh")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .extract().response();

        String refreshedToken = refreshResponse.path("accessToken");
        api(refreshedToken)
                .queryParam("username", username)
                .get("/cartItem")
                .then()
                .statusCode(200);
    }

    @Test
    void refreshWithoutCookieIsUnauthorized() {
        given()
                .contentType("application/json")
                .post("/auth/refresh")
                .then()
                .statusCode(401);
    }

    @Test
    void logoutRevokesTheRefreshToken() {
        String username = unique("buyer");
        register("USER", username, PASSWORD);

        Response loginResponse = api()
                .body(Map.of("username", username, "password", PASSWORD))
                .post("/auth/login")
                .then().statusCode(200)
                .extract().response();
        String accessToken = loginResponse.path("accessToken");
        String refreshToken = loginResponse.getCookie("refreshToken");

        api(accessToken).post("/auth/logout").then().statusCode(200);

        given()
                .contentType("application/json")
                .cookie("refreshToken", refreshToken)
                .post("/auth/refresh")
                .then()
                .statusCode(401);
    }

    @Test
    void anonymousRequestToProtectedEndpointIsUnauthorized() {
        api()
                .queryParam("username", unique("someone"))
                .get("/cartItem")
                .then()
                .statusCode(401);
    }

    @Test
    void forgedAccessTokenIsRejected() {
        api("not-a-real-jwt")
                .queryParam("username", unique("someone"))
                .get("/cartItem")
                .then()
                .statusCode(401);
    }
}
