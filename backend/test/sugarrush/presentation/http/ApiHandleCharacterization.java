package sugarrush.presentation.http;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import sugarrush.TestWorld;
import sugarrush.infrastructure.auth.FirebaseTokenVerifier;
import sugarrush.infrastructure.json.Json;

/** HTTP behavior to preserve when ApiServer.handle is split into smaller methods. */
public final class ApiHandleCharacterization {
    private static final String PROJECT = "soen343-f701c";
    private static final long NOW = 1_800_000_000L;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static int checks;
    private static KeyPair keys;

    private record Response(int status, String body) {
        Map<String, Object> object() { return Json.object(body); }
        List<?> array() { return (List<?>) Json.object("{\"items\":" + body + "}").get("items"); }
    }

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        FirebaseTokenVerifier verifier = new FirebaseTokenVerifier(PROJECT, () -> Map.of("test-key", keys.getPublic()),
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        HttpServer server = new ApiServer(TestWorld.create().app, verifier).start(0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            String customer = token("customer");
            String staff = token("restaurant");
            String otherStaff = token("restaurant2");
            responseContract(base, customer);
            commandContract(base, customer, staff, otherStaff);
            System.out.println("Passed " + checks + " API handle characterization checks.");
        } finally {
            server.stop(0);
        }
    }

    private static void responseContract(String base, String customer) throws Exception {
        var health = HTTP.send(HttpRequest.newBuilder(URI.create(base + "/api/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        check(health.statusCode() == 200 && Json.object(health.body()).get("status").equals("UP"),
                "Health route");
        check(request(base, "/api/missing", customer, Map.of()).status == 404, "Unknown route");
        check(request(base, "/api/command", null, Map.of("action", "ROLE_MENU")).status == 401,
                "Missing bearer token");
        var wrongType = HTTP.send(HttpRequest.newBuilder(URI.create(base + "/api/command"))
                .header("Authorization", "Bearer " + customer)
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("action=ROLE_MENU")).build(),
                HttpResponse.BodyHandlers.ofString());
        check(wrongType.statusCode() == 415, "Wrong content type");
        check(request(base, "/api/command", customer, Map.of()).status == 400, "Missing action");
        check(request(base, "/api/command", customer, Map.of("action", "NO_SUCH_ACTION")).status == 400,
                "Unknown action");
        var session = request(base, "/api/firebase-session", customer, Map.of());
        check(session.status == 200 && session.object().get("role").equals("CUSTOMER"), "Session role");
    }

    private static void commandContract(String base, String customer, String staff, String otherStaff) throws Exception {
        Map<String, String> items = Map.of("restaurantId", "sugar-shop", "item.cake", "1", "item.cookie", "2");
        Response quote = command(base, customer, "PRICE_QUOTE", items);
        check(quote.status == 200 && quote.object().get("total").toString().equals("20.50"),
                "Item fields become the quoted order lines");
        check(command(base, customer, "PRICE_QUOTE", Map.of("restaurantId", "sugar-shop", "item.cake", "bad"))
                .status == 400, "Invalid quantity");
        check(command(base, customer, "PRICE_QUOTE", Map.of("item.cake", "1")).status == 400,
                "Missing restaurant ID");
        check(command(base, staff, "PRICE_QUOTE", items).status == 403, "Role authorization");

        Map<String, String> placement = new LinkedHashMap<>(items);
        placement.put("address", "Concordia");
        placement.put("addressX", "-0.91");
        placement.put("addressY", "-0.50");
        Response placed = command(base, customer, "PLACE_ORDER", placement);
        check(placed.status == 200 && placed.object().get("state").equals("Placed"), "Place order response");
        String orderId = (String) placed.object().get("id");
        check(((Map<?, ?>) placed.object().get("deliveryAddress")).get("label").equals("Concordia"),
                "Address fields passed to order");
        check(((List<?>) placed.object().get("lines")).size() == 2, "Two item fields passed to order");
        check(command(base, otherStaff, "ACCEPT_ORDER", Map.of("orderId", orderId)).status == 403,
                "Other restaurant cannot accept order");
        check(command(base, staff, "ACCEPT_ORDER", Map.of("orderId", orderId)).status == 200,
                "Order ID passed to staff command");
        check(command(base, customer, "LIST_ORDERS", Map.of()).array().size() == 1,
                "Successful command updated state");
        check(command(base, staff, "ACCEPT_ORDER", Map.of()).status == 400, "Missing order ID");

        Response profile = command(base, customer, "UPDATE_PROFILE", Map.of("profile.displayName", "Ada"));
        check(profile.status == 200, "Profile-prefixed field accepted");
        Response viewed = command(base, customer, "VIEW_PROFILE", Map.of());
        check(((Map<?, ?>) viewed.object().get("profile")).get("displayName").equals("Ada"),
                "Profile-prefixed field reaches profile service");
    }

    private static Response command(String base, String token, String action, Map<String, String> fields)
            throws Exception {
        Map<String, String> all = new LinkedHashMap<>(fields);
        all.put("action", action);
        return request(base, "/api/command", token, all);
    }

    private static Response request(String base, String path, String token, Map<String, String> fields)
            throws Exception {
        StringJoiner body = new StringJoiner("&");
        fields.forEach((key, value) -> body.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var response = HTTP.send(builder.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }

    private static String token(String uid) throws Exception {
        String header = "{\"alg\":\"RS256\",\"kid\":\"test-key\"}";
        String claims = "{\"sub\":\"" + uid + "\",\"email\":\"" + uid
                + "@example.com\",\"email_verified\":true,\"aud\":\"" + PROJECT
                + "\",\"iss\":\"https://securetoken.google.com/" + PROJECT
                + "\",\"exp\":" + (NOW + 3600) + ",\"iat\":" + (NOW - 30)
                + ",\"auth_time\":" + (NOW - 60) + "}";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String data = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update(data.getBytes(StandardCharsets.US_ASCII));
        return data + "." + encoder.encodeToString(signature.sign());
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
