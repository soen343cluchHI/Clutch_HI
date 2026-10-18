package sugarrush.presentation.http;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import sugarrush.application.SugarRush;
import sugarrush.infrastructure.auth.FirebaseTokenVerifier;
import sugarrush.infrastructure.json.Json;

public final class FirebaseLoginChecks {
    private static final String PROJECT = "soen343-f701c";
    private static final long NOW = 1800000000L;
    private static int checks;
    private static KeyPair pair;
    private static FirebaseTokenVerifier verifier;

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048); pair = generator.generateKeyPair();
        verifier = new FirebaseTokenVerifier(PROJECT, () -> Map.of("test-key", pair.getPublic()),
            Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        String customer = token("customer-a", "a@example.com", PROJECT, NOW + 3600, NOW - 30, NOW - 60);
        check(verifier.verify(customer).uid().equals("customer-a"), "Signed UID lost");
        rejected(() -> verifier.verify("unsigned"));
        rejected(() -> verifier.verify(customer.substring(0, customer.lastIndexOf('.') + 1) + "invalid"));
        rejected(() -> verifier.verify(token("a", "a@example.com", "other-project", NOW + 3600, NOW - 30, NOW - 60)));
        rejected(() -> verifier.verify(token("a", "a@example.com", PROJECT, NOW, NOW - 30, NOW - 60)));
        rejected(() -> verifier.verify(token("a", "a@example.com", PROJECT, NOW + 3600, NOW + 30, NOW - 60)));
        rejected(() -> verifier.verify(token("a", "a@example.com", PROJECT, NOW + 3600, NOW - 30, NOW + 30)));
        rejected(() -> verifier.verify(signed("{\"alg\":\"none\",\"kid\":\"test-key\"}", claims("a", "a@example.com", PROJECT, NOW + 3600, NOW - 30, NOW - 60))));
        rejected(() -> verifier.verify(signed("{\"alg\":\"RS256\",\"kid\":\"unknown\"}", claims("a", "a@example.com", PROJECT, NOW + 3600, NOW - 30, NOW - 60))));
        rejected(() -> verifier.verify(signed("{\"alg\":\"RS256\",\"kid\":\"test-key\"}", claims("a", "a@example.com", PROJECT, NOW + 3600, NOW - 30, NOW - 60).replace("https://securetoken.google.com/", "https://forged.example/"))));
        try { Json.object("{\"sub\":\"a\",\"sub\":\"b\"}"); throw new AssertionError("Duplicate JSON claims accepted"); }
        catch (IllegalArgumentException expected) { checks++; }

        Path seed = Files.createTempFile("firebase-login-checks", ".properties");
        Files.writeString(seed, Files.readString(Path.of("backend/config/demo.properties")) +
            "\nfirebase.user.staff-a.account=restaurant\nfirebase.user.admin-a.account=operator\n");
        var server = new ApiServer(SugarRush.fromSeed(seed), verifier).start(0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        try {
            Response missing = request(base, "/api/firebase-session", null, Map.of());
            check(missing.status == 401, "Anonymous login accepted");
            check(request(base, "/api/command", "fake", Map.of("action", "BROWSE_RESTAURANTS")).status == 401, "Fake token accepted");
            check(request(base, "/api/login", null, Map.of("username", "customer", "password", "customer-demo")).status == 404, "Manual HTTP login still exposed");
            Response identity = request(base, "/api/firebase-session", customer, Map.of("role", "OPERATOR", "username", "operator"));
            check(identity.status == 200, "Automatic Firebase session failed");
            check(identity.object().get("role").equals("CUSTOMER"), "Client role trusted");
            check(identity.object().get("id").equals("firebase:customer-a"), "Shared demo customer used");
            check(identity.object().get("username").equals("a@example.com"), "Firebase email lost");
            Response browse = command(base, customer, "BROWSE_RESTAURANTS", Map.of());
            check(browse.status == 200 && browse.array().size() == 2, "Authenticated menus missing");
            Response order = command(base, customer, "PLACE_ORDER", Map.of("restaurantId", "sugar-shop", "item.cake", "2"));
            check(order.status == 200, "Signed-in customer cannot place");
            check(order.object().get("customerId").equals("firebase:customer-a"), "Wrong order owner");
            String id = (String) order.object().get("id");
            check(command(base, customer, "ACCEPT_ORDER", Map.of("orderId", id)).status == 403, "Customer can prepare orders");
            String second = token("customer-b", "b@example.com", PROJECT, NOW + 3600, NOW - 30, NOW - 60);
            check(command(base, second, "LIST_ORDERS", Map.of()).array().isEmpty(), "Another customer sees private orders");
            String renewed = token("customer-a", "a@example.com", PROJECT, NOW + 7200, NOW - 1, NOW - 60);
            check(command(base, renewed, "LIST_ORDERS", Map.of()).array().size() == 1, "Token refresh changed identity");
            String staff = token("staff-a", "staff@example.com", PROJECT, NOW + 3600, NOW - 30, NOW - 60);
            check(request(base, "/api/firebase-session", staff, Map.of()).object().get("role").equals("RESTAURANT"), "Approved staff mapping lost");
            for (String action : List.of("ACCEPT_ORDER", "START_PREPARING", "MARK_READY")) {
                check(command(base, staff, action, Map.of("orderId", id)).status == 200, "Staff lifecycle command failed");
            }
            check(command(base, customer, "LIST_ORDERS", Map.of()).body.contains("\"Ready\""), "Ready order not reflected");
            String admin = token("admin-a", "admin@example.com", PROJECT, NOW + 3600, NOW - 30, NOW - 60);
            check(request(base, "/api/firebase-session", admin, Map.of()).object().get("role").equals("OPERATOR"), "Approved operator mapping lost");
            check(command(base, admin, "EVENT_LOG", Map.of()).array().size() == 4, "Transition events missing");
            check(command(base, customer, "EVENT_LOG", Map.of()).status == 403, "Customer received operator events");
            String accepted = (String) command(base, second, "PLACE_ORDER", Map.of("restaurantId", "sugar-shop", "item.cake", "1")).object().get("id");
            check(command(base, staff, "ACCEPT_ORDER", Map.of("orderId", accepted)).status == 200, "Staff cannot accept before pausing");
            check(command(base, staff, "SET_RESTAURANT_STATUS", Map.of("restaurantId", "sugar-shop", "status", "PAUSED")).status == 200, "Staff cannot pause own restaurant");
            check(command(base, second, "PLACE_ORDER", Map.of("restaurantId", "sugar-shop", "item.cake", "1")).status == 400, "Paused restaurant accepted a new order");
            for (String action : List.of("START_PREPARING", "MARK_READY")) {
                check(command(base, staff, action, Map.of("orderId", accepted)).status == 200, "Accepted order blocked by pause");
            }
            check(command(base, staff, "SET_RESTAURANT_STATUS", Map.of("restaurantId", "other-shop", "status", "PAUSED")).status == 403, "Staff paused another restaurant");
            check(command(base, customer, "SET_RESTAURANT_STATUS", Map.of("restaurantId", "sugar-shop", "status", "OPEN")).status == 403, "Customer changed restaurant status");
            check(command(base, admin, "SET_RESTAURANT_STATUS", Map.of("restaurantId", "sugar-shop", "status", "BROKEN")).status == 400, "Invalid status accepted");
            check(command(base, admin, "SET_RESTAURANT_STATUS", Map.of("restaurantId", "sugar-shop")).status == 400, "Missing status accepted");
            check(command(base, admin, "SET_RESTAURANT_STATUS", Map.of("restaurantId", "other-shop", "status", "CLOSED")).status == 200, "Operator cannot close any restaurant");
            check(command(base, second, "PLACE_ORDER", Map.of("restaurantId", "other-shop", "item.vanilla", "1")).status == 400, "Closed restaurant accepted a new order");
            check(command(base, admin, "SET_RESTAURANT_STATUS", Map.of("restaurantId", "sugar-shop", "status", "OPEN")).status == 200, "Operator cannot reopen any restaurant");
            check(command(base, second, "PLACE_ORDER", Map.of("restaurantId", "sugar-shop", "item.cake", "1")).status == 200, "Reopened restaurant rejected an order");
            List<String> queued = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                String placed = (String) command(base, second, "PLACE_ORDER", Map.of("restaurantId", "sugar-shop", "item.cake", "1")).object().get("id");
                check(command(base, staff, "ACCEPT_ORDER", Map.of("orderId", placed)).status == 200, "Staff cannot accept order");
                queued.add(placed);
            }
            check(command(base, admin, "CANCEL_ORDER", Map.of("orderId", queued.get(1))).status == 200, "Operator cannot cancel accepted order");
            check(command(base, staff, "RESTAURANT_QUEUE", Map.of("restaurantId", "sugar-shop")).array().equals(List.of(queued.get(0), queued.get(2), queued.get(3))), "Cancellation broke queue order");
            check(command(base, staff, "START_PREPARING", Map.of("orderId", queued.get(0))).status == 200, "Staff cannot start preparing");
            check(command(base, admin, "CANCEL_ORDER", Map.of("orderId", queued.get(0))).status == 200, "Operator cannot cancel preparing order");
            check(command(base, staff, "START_PREPARING", Map.of("orderId", queued.get(2))).status == 200, "Next queued order blocked after cancellation");
            check(command(base, staff, "MARK_READY", Map.of("orderId", queued.get(2))).status == 200, "Staff cannot mark ready");
            check(command(base, admin, "CANCEL_ORDER", Map.of("orderId", queued.get(2))).status == 400, "Ready order cancelled");
            check(command(base, admin, "CANCEL_ORDER", Map.of("orderId", queued.get(1))).status == 400, "Cancelled order cancelled twice");
            check(command(base, second, "CANCEL_ORDER", Map.of("orderId", queued.get(3))).status == 403, "Customer cancelled accepted order");
            check(command(base, staff, "CANCEL_ORDER", Map.of("orderId", queued.get(3))).status == 403, "Staff cancelled order");
            String fresh = (String) command(base, second, "PLACE_ORDER", Map.of("restaurantId", "sugar-shop", "item.cake", "1")).object().get("id");
            check(command(base, admin, "CANCEL_ORDER", Map.of("orderId", fresh)).status == 403, "Operator cancelled placed order");
            check(command(base, second, "CANCEL_ORDER", Map.of("orderId", fresh)).status == 200, "Customer cannot cancel placed order");
            List<?> events = command(base, admin, "EVENT_LOG", Map.of()).array();
            check(events.stream().map(e -> ((Map<?, ?>) e).get("eventId")).distinct().count() == events.size(), "Duplicate event ID");
            check(events.stream().filter(e -> "Cancelled".equals(((Map<?, ?>) e).get("newState"))).count() == 3, "Cancellation events missing");
            check(command(base, customer, "LOGOUT", Map.of()).status == 200, "Internal logout failed");
            check(command(base, renewed, "LIST_ORDERS", Map.of()).array().size() == 1, "Firebase re-login lost order ownership");
            System.out.println("Passed " + checks + " Firebase identity, cryptographic and HTTP checks.");
        } finally { server.stop(0); Files.deleteIfExists(seed); }
    }

    private record Response(int status, String body) {
        Map<String, Object> object() { return Json.object(body); }
        List<?> array() { return (List<?>) Json.object("{\"items\":" + body + "}").get("items"); }
    }
    private static Response command(String base, String token, String action, Map<String, String> fields) throws Exception {
        Map<String, String> all = new LinkedHashMap<>(fields); all.put("action", action);
        return request(base, "/api/command", token, all);
    }
    private static Response request(String base, String path, String token, Map<String, String> fields) throws Exception {
        StringJoiner form = new StringJoiner("&");
        fields.forEach((key, value) -> form.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        var builder = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/x-www-form-urlencoded");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var response = HttpClient.newHttpClient().send(builder.POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
    private static String claims(String uid, String email, String audience, long exp, long iat, long auth) {
        return "{\"sub\":\"" + uid + "\",\"email\":\"" + email + "\",\"aud\":\"" + audience +
            "\",\"iss\":\"https://securetoken.google.com/" + PROJECT + "\",\"exp\":" + exp + ",\"iat\":" + iat + ",\"auth_time\":" + auth + "}";
    }
    private static String token(String uid, String email, String audience, long exp, long iat, long auth) throws Exception {
        return signed("{\"alg\":\"RS256\",\"kid\":\"test-key\"}", claims(uid, email, audience, exp, iat, auth));
    }
    private static String signed(String header, String claims) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String data = encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "." + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate()); signature.update(data.getBytes(StandardCharsets.US_ASCII));
        return data + "." + encoder.encodeToString(signature.sign());
    }
    @FunctionalInterface private interface Task { void run() throws Exception; }
    private static void rejected(Task task) throws Exception {
        try { task.run(); throw new AssertionError("Invalid token accepted"); }
        catch (SecurityException expected) { checks++; }
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
