package sugarrush.presentation.http;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import sugarrush.application.SugarRush;
import sugarrush.domain.user.Role;
import sugarrush.infrastructure.auth.FirebaseTokenVerifier;
import sugarrush.infrastructure.json.Json;

public final class DemoModeChecks {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static String base;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("sugarrush-demo-");
        Path store = directory.resolve("restaurants.properties");
        Path accountStore = directory.resolve("accounts.properties");
        HttpServer server = null;
        try {
            SugarRush application = SugarRush.fromConfig(Path.of("backend/config/app.properties"));
            for (Role role : Role.values())
                application.provisionDemoAccount(role.name().toLowerCase() + "@demo.local", "demo1234", role);
            application.useDemoAccountStore(accountStore);
            application.useDemoRestaurantStore(store);
            ApiServer.seedDemoRestaurants(application, Path.of("backend/demo/seed.json"));
            ApiServer.seedDemoRestaurants(application, Path.of("backend/demo/seed.json"));
            server = new ApiServer(application, new FirebaseTokenVerifier("soen343-f701c"), null, false, true).start(0);
            base = "http://127.0.0.1:" + server.getAddress().getPort();

            check(get("/api/health").contains("\"UP\""), "health");
            check(Boolean.TRUE.equals(Json.object(get("/api/mode")).get("demo")), "demo mode");
            Map<String, String> tokens = new LinkedHashMap<>();
            for (Role role : Role.values()) {
                String name = role.name().toLowerCase();
                Map<String, Object> login = Json.object(post("/api/demo-login", null,
                        Map.of("username", name + "@demo.local", "password", "demo1234")));
                String token = String.valueOf(login.get("token"));
                tokens.put(name, token);
                check(role.name().equals(Json.object(post("/api/firebase-session", token, Map.of())).get("role")),
                        "demo role " + role);
            }
            check(getStatus("/api/demo-login", null, Map.of("username", "operator@demo.local", "password", "wrong")) == 401,
                    "bad password rejected");
            Map<String, Object> newCustomer = Json.object(post("/api/demo-register", null,
                    Map.of("username", "new-customer@demo.local", "password", "newdemo1234", "role", "CUSTOMER")));
            String newCustomerToken = String.valueOf(newCustomer.get("token"));
            check("CUSTOMER".equals(newCustomer.get("role")), "customer registration");
            check("CUSTOMER".equals(Json.object(post("/api/firebase-session", newCustomerToken, Map.of())).get("role")),
                    "new customer session");
            check(getStatus("/api/demo-register", null, Map.of("username", "new-customer@demo.local",
                    "password", "newdemo1234", "role", "CUSTOMER")) == 400, "duplicate registration rejected");
            check(getStatus("/api/demo-register", null, Map.of("username", "new-operator@demo.local",
                    "password", "newdemo1234", "role", "OPERATOR")) == 400, "operator registration rejected");
            Map<String, Object> newRestaurant = Json.object(post("/api/demo-register", null,
                    Map.of("username", "new-restaurant@demo.local", "password", "newdemo1234",
                            "role", "RESTAURANT", "restaurantName", "Sample Shop")));
            String newRestaurantToken = String.valueOf(newRestaurant.get("token"));
            check("RESTAURANT".equals(newRestaurant.get("role")), "restaurant registration");
            check(action(newRestaurantToken, "BROWSE_RESTAURANTS", Map.of()).contains("Sample Shop"),
                    "new restaurant provisioned");
            Map<String, Object> newCourier = Json.object(post("/api/demo-register", null,
                    Map.of("username", "new-courier@demo.local", "password", "newdemo1234", "role", "COURIER")));
            check("COURIER".equals(newCourier.get("role")), "courier registration");
            check(Json.object(action(String.valueOf(newCourier.get("token")), "COURIER_STATUS", Map.of())).get("userId") != null,
                    "new courier provisioned");
            SugarRush restarted = SugarRush.fromConfig(Path.of("backend/config/app.properties"));
            for (Role role : Role.values())
                restarted.provisionDemoAccount(role.name().toLowerCase() + "@demo.local", "demo1234", role);
            restarted.useDemoAccountStore(accountStore);
            restarted.useDemoRestaurantStore(store);
            check(restarted.login("new-customer@demo.local", "newdemo1234").user().role() == Role.CUSTOMER,
                    "registered password survived restart");
            check(restarted.ownsRestaurant(restarted.login("new-restaurant@demo.local", "newdemo1234").user().id()),
                    "new restaurant ownership survived restart");
            check(!Files.readString(accountStore).contains("newdemo1234"), "passwords stored as hashes");
            String listed = action(tokens.get("operator"), "BROWSE_RESTAURANTS", Map.of());
            check(listed.contains("demo-restaurant-1") && listed.contains("demo-restaurant-2"), "seeded restaurants");
            check(application.restaurantView("demo-restaurant-2").menu().size() == 2, "seeded menu");

            Map<String, String> cart = Map.of("restaurantId", "demo-restaurant-2", "item.item-2-2", "1", "promoCode", "SAVE10");
            Map<String, Object> quote = Json.object(action(tokens.get("customer"), "PRICE_QUOTE", cart));
            check("SAVE10".equals(quote.get("promoCode")) && ((BigDecimal) quote.get("promoDiscount")).signum() > 0,
                    "pricing and promotion");
            action(tokens.get("courier"), "GO_ON_DUTY", Map.of());
            Map<String, String> checkout = new LinkedHashMap<>(cart);
            checkout.put("address", "Demo delivery address");
            checkout.put("addressX", "0");
            checkout.put("addressY", "0");
            Map<String, Object> order = Json.object(action(tokens.get("customer"), "PLACE_ORDER", checkout));
            String orderId = String.valueOf(order.get("id"));
            check("Placed".equals(order.get("state")), "order placement");
            for (String step : new String[] { "ACCEPT_ORDER", "START_PREPARING", "MARK_READY" })
                action(tokens.get("restaurant"), step, Map.of("orderId", orderId));
            check(orderId.equals(Json.object(action(tokens.get("courier"), "COURIER_OFFER", Map.of())).get("id")),
                    "courier dispatch");
            for (String step : new String[] { "ACCEPT_DELIVERY_OFFER", "CONFIRM_PICKUP", "CONFIRM_DELIVERY" })
                order = Json.object(action(tokens.get("courier"), step, Map.of("orderId", orderId)));
            check("Delivered".equals(order.get("state")), "delivery lifecycle");
            check("SAVE10".equals(Json.object(action(tokens.get("customer"), "VIEW_ORDER_SUMMARY",
                    Map.of("orderId", orderId))).get("promoCode")), "delivered promotion summary");

            action(tokens.get("operator"), "RESET_SYSTEM", Map.of());
            check("[]".equals(action(tokens.get("operator"), "BROWSE_RESTAURANTS", Map.of())), "reset cleared restaurants");
            SugarRush reloaded = SugarRush.fromConfig(Path.of("backend/config/app.properties"));
            reloaded.useDemoRestaurantStore(store);
            check(!reloaded.hasRestaurant("demo-restaurant-1"), "reset persisted on disk");
            System.out.println("Passed demo signup, login, persistence, seed, pricing, promotion and full order lifecycle checks.");
        } finally {
            if (server != null) server.stop(0);
            Files.deleteIfExists(store);
            Files.deleteIfExists(accountStore);
            Files.deleteIfExists(directory);
        }
    }

    private static String get(String path) throws Exception {
        HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "GET " + path + " returned " + response.statusCode());
        return response.body();
    }

    private static int getStatus(String path, String token, Map<String, String> fields) throws Exception {
        return request(path, token, fields).statusCode();
    }

    private static String post(String path, String token, Map<String, String> fields) throws Exception {
        HttpResponse<String> response = request(path, token, fields);
        check(response.statusCode() == 200, path + " returned " + response.statusCode() + ": " + response.body());
        return response.body();
    }

    private static HttpResponse<String> request(String path, String token, Map<String, String> fields) throws Exception {
        StringBuilder body = new StringBuilder();
        for (var entry : fields.entrySet()) {
            if (!body.isEmpty()) body.append('&');
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String action(String token, String name, Map<String, String> fields) throws Exception {
        Map<String, String> request = new LinkedHashMap<>(fields);
        request.put("action", name);
        return post("/api/command", token, request);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
