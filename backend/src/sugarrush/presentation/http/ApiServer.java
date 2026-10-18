package sugarrush.presentation.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import sugarrush.application.SugarRush;
import sugarrush.application.commands.*;
import sugarrush.domain.restaurant.Restaurant;
import sugarrush.domain.restaurant.MenuItem;
import sugarrush.domain.user.Session;
import sugarrush.domain.user.Role;
import sugarrush.infrastructure.auth.FirebaseTokenVerifier;
import sugarrush.infrastructure.json.Json;

public final class ApiServer {
    
    private final SugarRush application;
    private final FirebaseTokenVerifier verifier;
    private final HttpClient http = HttpClient.newHttpClient();
    private final String databaseUrl;

    ApiServer(SugarRush application, FirebaseTokenVerifier verifier) {
        this(application, verifier, null);
    }

    ApiServer(SugarRush application, FirebaseTokenVerifier verifier, String databaseUrl) {
        this.application = application;
        this.verifier = verifier;
        this.databaseUrl = databaseUrl;
    }

    public static void main(String[] args) throws IOException {
        Path seed = Path.of(args.length > 0 ? args[0] : "backend/config/live.properties");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        Properties settings = new Properties();

        try (Reader reader = Files.newBufferedReader(seed)) {
            settings.load(reader);
        }

        String projectId = settings.getProperty("firebase.projectId", "soen343-f701c").trim();
        ApiServer api = new ApiServer(SugarRush.fromSeed(seed), new FirebaseTokenVerifier(projectId), databaseUrl());
        HttpServer server = api.start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        System.out.println("SugarRush backend: http://localhost:" + port);
    }

    private static String databaseUrl() throws IOException {
        String configured = System.getenv("SUGARRUSH_DATABASE_URL");
        if (configured != null && !configured.isBlank()) return configured.trim();
        for (String file : List.of("ui/.env.local", "ui/.env.production")) {
            Path path = Path.of(file);
            if (!Files.exists(path)) continue;
            for (String line : Files.readAllLines(path)) {
                if (!line.startsWith("VITE_FIREBASE_DATABASE_URL=")) continue;
                String value = line.substring("VITE_FIREBASE_DATABASE_URL=".length()).trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
                if (!value.isBlank()) return value;
            }
        }
        throw new IllegalStateException("Set SUGARRUSH_DATABASE_URL or VITE_FIREBASE_DATABASE_URL in ui/.env.local");
    }

    HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/api/", this::handle);
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/health")) {
                if (!method(exchange, "GET"))
                    return;
                send(exchange, 200, Map.of("status", "UP", "service", "SugarRush backend"));
                return;
            }
            if (!path.equals("/api/firebase-session") && !path.equals("/api/command")) {
                send(exchange, 404, Map.of("error", "Endpoint not found"));
                return;
            }
            if (!method(exchange, "POST"))
                return;

            String type = exchange.getRequestHeaders().getFirst("Content-Type");
            if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
                send(exchange, 415, Map.of("error", "Use application/x-www-form-urlencoded"));
                return;
            }

            byte[] body = exchange.getRequestBody().readNBytes(65537);
            if (body.length > 65536) {
                send(exchange, 413, Map.of("error", "Request body too large"));
                return;
            }
            
            Map<String, String> fields = form(new String(body, StandardCharsets.UTF_8));
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            FirebaseTokenVerifier.Identity identity;
            try {
                if (authorization == null || !authorization.startsWith("Bearer ")) {
                    throw new SecurityException("Sign in required");
                }
                identity = verifier.verify(authorization.substring(7));
            } catch (SecurityException failure) {
                send(exchange, 401, Map.of("error", message(failure)));
                return;
            }
            Session session = application.firebaseSession(identity.uid(), identity.email(), profileRole(identity.uid(), identity.email(), authorization.substring(7)));
            String token = session.token();
            if (path.equals("/api/firebase-session")) {
                send(exchange, 200, application.execute(new Command(token, Action.SESSION_INFO)));
                return;
            }
            Action action = Action.valueOf(required(fields, "action"));
            Map<String, Integer> items = new LinkedHashMap<>();
            for (var entry : fields.entrySet()) {
                if (entry.getKey().startsWith("item.")) {
                    items.put(entry.getKey().substring(5), Integer.valueOf(entry.getValue()));
                }
            }
            Command command = switch (action) {
            case CREATE_RESTAURANT -> new Command(token, action, null, null, null, required(fields, "name"), null, null);
            case PUT_MENU_ITEM -> new Command(token, action, required(fields, "restaurantId"), null, null, null, null,
                    new MenuItem(required(fields, "itemId"), required(fields, "name"),
                            new BigDecimal(required(fields, "price")), MenuItem.Status.valueOf(required(fields, "itemStatus"))));
            case DELETE_MENU_ITEM -> new Command(token, action, required(fields, "restaurantId"), null, null,
                    required(fields, "itemId"), null, null);
            case RESTAURANT_QUEUE ->
                new Command(token, action, required(fields, "restaurantId"), null, null, null, null, null);
            case SET_RESTAURANT_STATUS -> new Command(token, action, required(fields, "restaurantId"), null, null, null,
                    Restaurant.Status.valueOf(required(fields, "status")), null);
            case PLACE_ORDER -> Command.place(token, required(fields, "restaurantId"), items);
            case CANCEL_ORDER, ACCEPT_ORDER, REJECT_ORDER, START_PREPARING, MARK_READY ->
                Command.order(token, action, required(fields, "orderId"), fields.get("reason"));
            case BROWSE_RESTAURANTS, LIST_ORDERS, SESSION_INFO, ROLE_MENU, LOGOUT, DASHBOARD, EVENT_LOG, COMMAND_LOG ->
                new Command(token, action);
            default -> throw new IllegalArgumentException("This endpoint does not support that command yet");
            };
            send(exchange, 200, application.execute(command));
        } catch (FirebaseTokenVerifier.UnavailableException failure) {
            send(exchange, 503, Map.of("error", message(failure)));
        } catch (SecurityException failure) {
            send(exchange, 403, Map.of("error", message(failure)));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            send(exchange, 400, Map.of("error", message(failure)));
        } catch (Exception failure) {
            send(exchange, 500, Map.of("error", "Unexpected server error"));
        }
    }

    private Role profileRole(String uid, String email, String token) {
        if (databaseUrl == null || databaseUrl.isBlank()) return Role.CUSTOMER;
        try {
            String url = databaseUrl.replaceAll("/+$", "") + "/users/" + uid + ".json?auth=" +
                    java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Could not verify account role");
            Map<String, Object> profile = Json.object(response.body());
            if (!email.equals(profile.get("email"))) throw new SecurityException("Account profile mismatch");
            return Role.valueOf(String.valueOf(profile.get("role")).toUpperCase(Locale.ROOT));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not verify account role");
        } catch (IOException failure) {
            throw new IllegalStateException("Could not verify account role");
        }
    }


    private static String message(Exception failure) {
        return failure.getMessage() == null ? "Command failed" : failure.getMessage();
    }

    private static boolean method(HttpExchange exchange, String expected) throws IOException {
        if (expected.equals(exchange.getRequestMethod()))
            return true;
        exchange.getResponseHeaders().set("Allow", expected);
        send(exchange, 405, Map.of("error", "Use " + expected));
        return false;
    }

    private static Map<String, String> form(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        if (body.isEmpty())
            return result;
        for (String part : body.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8);
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate form field");
            }
        }
        return result;
    }

    private static String required(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static void send(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = json(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static String json(Object value) {
        if (value == null)
            return "null";
        if (value instanceof Number || value instanceof Boolean)
            return value.toString();
        if (value instanceof CharSequence || value instanceof Enum<?> || value instanceof UUID
                || value instanceof TemporalAccessor)
            return quote(value.toString());
        if (value instanceof Map<?, ?> map) {
            StringJoiner joined = new StringJoiner(",", "{", "}");
            map.forEach((key, item) -> joined.add(quote(key.toString()) + ":" + json(item)));
            return joined.toString();
        }
        if (value instanceof Iterable<?> items) {
            StringJoiner joined = new StringJoiner(",", "[", "]");
            items.forEach(item -> joined.add(json(item)));
            return joined.toString();
        }
        if (value.getClass().isRecord()) {
            StringJoiner joined = new StringJoiner(",", "{", "}");
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    joined.add(quote(component.getName()) + ":" + json(component.getAccessor().invoke(value)));
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException("Cannot serialize response", failure);
                }
            }
            return joined.toString();
        }
        throw new IllegalArgumentException("Unsupported response type");
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
            case '"' -> result.append("\\\"");
            case '\\' -> result.append("\\\\");
            case '\n' -> result.append("\\n");
            case '\r' -> result.append("\\r");
            case '\t' -> result.append("\\t");
            default -> {
                if (character < 32)
                    result.append(String.format("\\u%04x", (int) character));
                else
                    result.append(character);
            }
            }
        }
        return result.append('"').toString();
    }
}
