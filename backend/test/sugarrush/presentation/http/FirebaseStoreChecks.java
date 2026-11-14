package sugarrush.presentation.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import sugarrush.application.SugarRush;
import sugarrush.infrastructure.auth.FirebaseTokenVerifier;
import sugarrush.infrastructure.json.Json;

// Runs the API server against a fake Firebase Realtime Database (same REST protocol) to check what is saved
// remotely: restaurant sign-up, retries after failures, reset, restarts, several backends and damaged records.
public final class FirebaseStoreChecks {
    private static final String PROJECT = "soen343-f701c";
    private static final long NOW = 1800000000L;
    private static int checks;
    private static KeyPair pair;

    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        pair = generator.generateKeyPair();
        var verifier = new FirebaseTokenVerifier(PROJECT, () -> Map.of("test-key", pair.getPublic()),
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
        FakeFirebase firebase = new FakeFirebase();
        ApiServer.restaurantSyncMs = 0;
        HttpServer first = server(verifier, firebase.url);
        String a = base(first);
        try {
            String owner = token("owner-1"), customer = token("customer-1"), admin = token("admin-1");
            register(a, customer, "CUSTOMER", "");
            register(a, admin, "OPERATOR", "");

            // Restaurant sign-up saves the enrollment and the first restaurant in Firebase.
            Response signup = register(a, owner, "RESTAURANT", "Fresh Bakery");
            check(signup.status == 200, "Restaurant sign-up failed: " + signup.body);
            check("RESTAURANT".equals(firebase.get("accountRegistrations/owner-1/role")), "Enrollment not saved");
            Map<?, ?> stored = restaurantOf(firebase, "firebase:owner-1");
            check(stored != null && "Fresh Bakery".equals(stored.get("name")), "Sign-up restaurant not saved in Firebase");
            check(stored.get("address") instanceof String address && !address.isBlank(), "Sign-up restaurant has no address");
            Response session = request(a, "/api/firebase-session", owner, Map.of());
            check(session.object().get("role").equals("RESTAURANT") && Boolean.TRUE.equals(session.object().get("enrolled")),
                    "Restaurant session wrong: " + session.body);
            check(command(a, customer, "BROWSE_RESTAURANTS", Map.of()).array().size() == 1, "Customer cannot see new restaurant");
            check(register(a, owner, "RESTAURANT", "Fresh Bakery").status == 200, "Repeated sign-up not idempotent");
            check(restaurantsOf(firebase, "firebase:owner-1") == 1, "Repeated sign-up duplicated the restaurant");
            Response changed = register(a, owner, "CUSTOMER", "");
            check(changed.status == 400 && changed.body.contains("already registered as a Restaurant"),
                    "Changed sign-up details not explained: " + changed.body);

            // A sign-up whose restaurant could not be saved creates it on retry instead of leaving the owner without one.
            firebase.denyWrites("restaurants");
            Response failed = register(a, token("owner-2"), "RESTAURANT", "Retry Cafe");
            check(failed.status == 400, "Failed restaurant save reported as success");
            check("RESTAURANT".equals(firebase.get("accountRegistrations/owner-2/role")), "Enrollment lost");
            check(command(a, admin, "BROWSE_RESTAURANTS", Map.of()).array().size() == 1, "Unsaved restaurant kept in memory");
            firebase.allowWrites();
            check(register(a, token("owner-2"), "RESTAURANT", "Retry Cafe").status == 200, "Retry after failure failed");
            check(restaurantOf(firebase, "firebase:owner-2") != null, "Retry did not create the sign-up restaurant");

            // Older account without an enrollment: reported, and it can choose its account type once.
            String legacy = token("legacy-1");
            Response legacySession = request(a, "/api/firebase-session", legacy, Map.of());
            check(legacySession.object().get("role").equals("CUSTOMER")
                    && Boolean.FALSE.equals(legacySession.object().get("enrolled")), "Missing enrollment not reported");
            firebase.put("users/legacy-1", Map.of("email", "legacy-1@example.com", "role", "Customer"));
            check(register(a, legacy, "RESTAURANT", "Late Bistro").status == 200, "Legacy account cannot choose a type");
            check(request(a, "/api/firebase-session", legacy, Map.of()).object().get("role").equals("RESTAURANT"),
                    "Legacy account type not applied");
            check(restaurantOf(firebase, "firebase:legacy-1") != null, "Legacy restaurant account got no restaurant");

            // Polling does not ask Firebase for a known enrollment again.
            int before = firebase.reads("accountRegistrations");
            for (int i = 0; i < 5; i++) command(a, owner, "LIST_ORDERS", Map.of());
            check(firebase.reads("accountRegistrations") == before, "Enrollment fetched on every request");

            // A failed create is undone; a failed update is kept and saved again on the owner's next request.
            String ownerRestaurant = (String) restaurantIdOf(firebase, "firebase:owner-1");
            firebase.denyWrites("restaurants");
            check(command(a, owner, "CREATE_RESTAURANT", Map.of("name", "Ghost Kitchen")).status == 400, "Failed create succeeded");
            check(command(a, owner, "BROWSE_RESTAURANTS", Map.of()).array().size() == 1, "Failed create left a restaurant");
            Response update = command(a, owner, "PUT_MENU_ITEM", Map.of("restaurantId", ownerRestaurant, "itemId", "bread",
                    "name", "Bread", "price", "4.00", "itemStatus", "AVAILABLE"));
            check(update.status == 400 && update.body.contains("retried"), "Failed update not explained: " + update.body);
            firebase.allowWrites();
            command(a, owner, "LIST_ORDERS", Map.of());
            check(firebase.toJson(firebase.get("restaurants/" + ownerRestaurant + "/menu")).contains("Bread"),
                    "Unsaved menu change not retried");

            // A restaurant created through another backend shows up here too.
            HttpServer second = server(verifier, firebase.url);
            try {
                String b = base(second);
                check(command(b, customer, "BROWSE_RESTAURANTS", Map.of()).array().size() == 3, "Restart lost restaurants");
                check(request(b, "/api/firebase-session", owner, Map.of()).object().get("role").equals("RESTAURANT"),
                        "Restart lost the restaurant role");
                check(register(b, token("owner-3"), "RESTAURANT", "Second Server").status == 200, "Sign-up on second backend");
                check(command(a, customer, "BROWSE_RESTAURANTS", Map.of()).array().size() == 4,
                        "Restaurant from another backend not imported");
            } finally {
                second.stop(0);
            }

            // Reset deletes restaurants everywhere; signing in again after a finished sign-up does not recreate them.
            for (String uid : List.of("owner-1", "owner-2", "owner-3"))
                firebase.put("users/" + uid, Map.of("email", uid + "@example.com", "role", "Customer"));
            check(command(a, admin, "RESET_SYSTEM", Map.of()).status == 200, "Reset failed");
            check(firebase.get("restaurants") == null, "Reset kept restaurants in Firebase");
            check("RESTAURANT".equals(firebase.get("accountRegistrations/owner-1/role")), "Reset removed an account");
            check(register(a, owner, "RESTAURANT", "Fresh Bakery").status == 200, "Sign-in after reset failed");
            check(firebase.get("restaurants") == null, "Finished sign-up recreated a deleted restaurant");
            check(command(a, owner, "CREATE_RESTAURANT", Map.of("name", "Fresh Bakery")).status == 200,
                    "Owner cannot reopen after reset");

            // A damaged record is skipped instead of breaking every request.
            firebase.put("restaurants/broken", Map.of("ownerId", "firebase:x", "name", "Broken"));
            firebase.put("restaurants/sparse", Map.of("ownerId", "firebase:y", "name", "Sparse Menu", "x", 0, "y", 0,
                    "status", "OPEN", "menu", Map.of("2", Map.of("id", "tea", "name", "Tea", "price", "2.00",
                            "status", "AVAILABLE"))));
            HttpServer third = server(verifier, firebase.url);
            try {
                List<?> all = command(base(third), customer, "BROWSE_RESTAURANTS", Map.of()).array();
                check(all.size() == 2, "Damaged record broke loading: " + all.size());
                check(all.stream().anyMatch(r -> ((Map<?, ?>) r).get("name").equals("Sparse Menu")
                        && ((List<?>) ((Map<?, ?>) r).get("menu")).size() == 1), "Menu stored as an object was lost");
            } finally {
                third.stop(0);
            }

            // Both Firebase Hosting domains may call the API from a browser.
            for (String origin : List.of("https://soen343-f701c.web.app", "https://soen343-f701c.firebaseapp.com")) {
                var preflight = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(a + "/api/command"))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", origin).build(),
                        HttpResponse.BodyHandlers.discarding());
                check(origin.equals(preflight.headers().firstValue("Access-Control-Allow-Origin").orElse("")),
                        "CORS blocks " + origin);
            }
            System.out.println("Passed " + checks + " Firebase storage checks.");
        } finally {
            first.stop(0);
            firebase.stop();
        }
    }

    private static HttpServer server(FirebaseTokenVerifier verifier, String url) throws Exception {
        return new ApiServer(SugarRush.fromConfig(sugarrush.TestWorld.CONFIG), verifier, url, true).start(0);
    }

    private static String base(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static Map<?, ?> restaurantOf(FakeFirebase firebase, String ownerId) {
        Object id = restaurantIdOf(firebase, ownerId);
        return id == null ? null : (Map<?, ?>) firebase.get("restaurants/" + id);
    }

    private static Object restaurantIdOf(FakeFirebase firebase, String ownerId) {
        if (!(firebase.get("restaurants") instanceof Map<?, ?> all)) return null;
        return all.entrySet().stream().filter(e -> ownerId.equals(((Map<?, ?>) e.getValue()).get("ownerId")))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private static long restaurantsOf(FakeFirebase firebase, String ownerId) {
        if (!(firebase.get("restaurants") instanceof Map<?, ?> all)) return 0;
        return all.values().stream().filter(r -> ownerId.equals(((Map<?, ?>) r).get("ownerId"))).count();
    }

    private static Response register(String base, String token, String role, String name) throws Exception {
        return request(base, "/api/register-account", token, Map.of("role", role, "restaurantName", name));
    }

    private record Response(int status, String body) {
        Map<String, Object> object() {
            return Json.object(body);
        }

        List<?> array() {
            return (List<?>) Json.object("{\"items\":" + body + "}").get("items");
        }
    }

    private static Response command(String base, String token, String action, Map<String, String> fields) throws Exception {
        Map<String, String> all = new LinkedHashMap<>(fields);
        all.put("action", action);
        return request(base, "/api/command", token, all);
    }

    private static Response request(String base, String path, String token, Map<String, String> fields) throws Exception {
        StringJoiner form = new StringJoiner("&");
        fields.forEach((key, value) -> form.add(URLEncoder.encode(key, StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(form.toString())).build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }

    private static String token(String uid) throws Exception {
        String claims = "{\"sub\":\"" + uid + "\",\"email\":\"" + uid + "@example.com\",\"email_verified\":true,\"aud\":\""
                + PROJECT + "\",\"iss\":\"https://securetoken.google.com/" + PROJECT + "\",\"exp\":" + (NOW + 3600)
                + ",\"iat\":" + (NOW - 30) + ",\"auth_time\":" + (NOW - 60) + "}";
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String data = encoder.encodeToString("{\"alg\":\"RS256\",\"kid\":\"test-key\"}".getBytes(StandardCharsets.UTF_8))
                + "." + encoder.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(pair.getPrivate());
        signature.update(data.getBytes(StandardCharsets.US_ASCII));
        return data + "." + encoder.encodeToString(signature.sign());
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    // Minimal in-memory Firebase Realtime Database REST API: GET (shallow, ETag), conditional PUT and DELETE.
    private static final class FakeFirebase {
        private final Map<String, Object> root = new LinkedHashMap<>();
        private final Map<String, Integer> reads = new HashMap<>();
        private final HttpServer server;
        private final String url;
        private String denied;

        FakeFirebase() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
            url = "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }

        void denyWrites(String top) {
            denied = top;
        }

        void allowWrites() {
            denied = null;
        }

        synchronized int reads(String top) {
            return reads.getOrDefault(top, 0);
        }

        synchronized Object get(String path) {
            Object node = root;
            for (String key : path.split("/")) {
                if (!(node instanceof Map<?, ?> map)) return null;
                node = map.get(key);
            }
            return node;
        }

        @SuppressWarnings("unchecked")
        synchronized void put(String path, Object value) {
            String[] keys = path.split("/");
            Map<String, Object> node = root;
            for (int i = 0; i < keys.length - 1; i++)
                node = (Map<String, Object>) node.computeIfAbsent(keys[i], key -> new LinkedHashMap<String, Object>());
            if (value == null) node.remove(keys[keys.length - 1]);
            else node.put(keys[keys.length - 1], value);
            prune(root);
        }

        private static boolean prune(Map<?, ?> map) {
            map.values().removeIf(value -> value instanceof Map<?, ?> child && prune(child));
            return map.isEmpty();
        }

        private synchronized void handle(HttpExchange exchange) throws java.io.IOException {
            String path = exchange.getRequestURI().getPath().replaceAll("^/|\\.json$", "");
            String query = Objects.requireNonNullElse(exchange.getRequestURI().getQuery(), "");
            String method = exchange.getRequestMethod();
            int status = 200;
            String body;
            Object current = path.isEmpty() ? root : get(path);
            String etag = Integer.toHexString(toJson(current).hashCode());
            if (method.equals("GET")) {
                reads.merge(path.split("/")[0], 1, Integer::sum);
                Object value = current;
                if (query.contains("shallow=true") && current instanceof Map<?, ?> map) {
                    Map<Object, Object> keys = new LinkedHashMap<>();
                    map.keySet().forEach(key -> keys.put(key, true));
                    value = keys;
                }
                body = toJson(value);
                if ("true".equals(exchange.getRequestHeaders().getFirst("X-Firebase-ETag")))
                    exchange.getResponseHeaders().set("ETag", etag);
            } else if (denied != null && path.startsWith(denied)) {
                status = 401;
                body = "{\"error\":\"Permission denied\"}";
            } else if (method.equals("PUT")) {
                String match = exchange.getRequestHeaders().getFirst("if-match");
                if (match != null && !match.equals(etag)) {
                    status = 412;
                    body = "{\"error\":\"ETag mismatch\"}";
                } else {
                    body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    put(path, Json.object(body));
                }
            } else if (method.equals("DELETE")) {
                put(path, null);
                body = "null";
            } else {
                status = 405;
                body = "{}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        String toJson(Object value) {
            if (value == null) return "null";
            if (value instanceof Map<?, ?> map) {
                StringJoiner joined = new StringJoiner(",", "{", "}");
                map.forEach((key, item) -> joined.add(toJson(String.valueOf(key)) + ":" + toJson(item)));
                return joined.toString();
            }
            if (value instanceof List<?> list) {
                StringJoiner joined = new StringJoiner(",", "[", "]");
                list.forEach(item -> joined.add(toJson(item)));
                return joined.toString();
            }
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
