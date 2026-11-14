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
import sugarrush.application.auth.FirebaseRegistrationService.Enrollment;
import sugarrush.application.commands.*;
import sugarrush.application.profile.ProfileService;
import sugarrush.domain.restaurant.Restaurant;
import sugarrush.domain.restaurant.MenuItem;
import sugarrush.domain.user.Profile;
import sugarrush.domain.user.Session;
import sugarrush.domain.user.Role;
import sugarrush.infrastructure.auth.FirebaseTokenVerifier;
import sugarrush.infrastructure.json.Json;

public final class ApiServer {
    
    private final SugarRush application;
    private final FirebaseTokenVerifier verifier;
    private final HttpClient http = HttpClient.newHttpClient();
    private final String databaseUrl;
    // When true, restaurants, sign-up roles and profiles are read from and saved to the Firebase Realtime Database.
    private final boolean remoteRestaurants;
    private static final java.time.Duration FIREBASE_TIMEOUT = java.time.Duration.ofSeconds(10);
    static long restaurantSyncMs = 15_000; // lowered by the checks
    private static final long ENROLLMENT_RECHECK_MS = 30_000;
    // Both Firebase Hosting domains serve the app; SUGARRUSH_ALLOWED_ORIGIN can add more (comma-separated).
    private static final Set<String> ALLOWED_ORIGINS = allowedOrigins();
    private long restaurantsSyncedAt;
    private final Map<String, Long> unenrolledCheckedAt = new HashMap<>();
    private final Set<String> unsavedRestaurants = new LinkedHashSet<>();
    private final Set<String> profilesLoaded = new HashSet<>();

    // In-memory only; used by the automated checks.
    ApiServer(SugarRush application, FirebaseTokenVerifier verifier) {
        this(application, verifier, null, false);
    }

    ApiServer(SugarRush application, FirebaseTokenVerifier verifier, String databaseUrl, boolean remoteRestaurants) {
        if (remoteRestaurants && (databaseUrl == null || databaseUrl.isBlank()))
            throw new IllegalStateException("Firebase database URL required");
        this.application = application;
        this.verifier = verifier;
        this.databaseUrl = databaseUrl;
        this.remoteRestaurants = remoteRestaurants;
    }

    private static Set<String> allowedOrigins() {
        Set<String> origins = new HashSet<>(List.of("https://soen343-f701c.web.app", "https://soen343-f701c.firebaseapp.com"));
        for (String origin : System.getenv().getOrDefault("SUGARRUSH_ALLOWED_ORIGIN", "").split(","))
            if (!origin.isBlank()) origins.add(origin.trim().replaceAll("/+$", ""));
        return Set.copyOf(origins);
    }

    public static void main(String[] args) throws IOException {
        Path config = Path.of(args.length > 0 ? args[0] : "backend/config/app.properties");
        System.out.println("SugarRush configuration: " + config.toAbsolutePath());
        int port = args.length > 1 ? Integer.parseInt(args[1])
                : Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Properties settings = new Properties();

        try (Reader reader = Files.newBufferedReader(config)) {
            settings.load(reader);
        }

        String projectId = settings.getProperty("firebase.projectId", "soen343-f701c").trim();
        SugarRush application = SugarRush.fromConfig(config);
        // Firebase is the only data store, locally and when deployed.
        ApiServer api = new ApiServer(application, new FirebaseTokenVerifier(projectId), databaseUrl(), true);
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
        String bindHost = System.getenv("SUGARRUSH_BIND_HOST");
        InetSocketAddress address = bindHost == null ? new InetSocketAddress(port) : new InetSocketAddress(bindHost, port);
        HttpServer server = HttpServer.create(address, 0);
        server.createContext("/api/", this::handle);
        server.start();
        return server;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && ALLOWED_ORIGINS.contains(origin)) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
                exchange.getResponseHeaders().set("Vary", "Origin");
            }
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/api/health")) {
                if (!method(exchange, "GET"))
                    return;
                send(exchange, 200, Map.of("status", "UP", "service", "SugarRush backend"));
                return;
            }
            if (!path.equals("/api/firebase-session") && !path.equals("/api/command") && !path.equals("/api/register-account")) {
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
                identity = path.equals("/api/register-account")
                        ? verifier.verifyRegistration(authorization.substring(7))
                        : verifier.verify(authorization.substring(7));
            } catch (SecurityException failure) {
                send(exchange, 401, Map.of("error", message(failure)));
                return;
            }
            String firebaseToken = authorization.substring(7);
            if (remoteRestaurants) syncRestaurants(firebaseToken);
            if (path.equals("/api/register-account")) {
                send(exchange, 200, registerAccount(identity, fields, firebaseToken));
                return;
            }
            // The backend starts empty; the account's sign-up role is restored from Firebase without creating anything.
            if (remoteRestaurants) enrollmentFor(identity, firebaseToken, false);
            Session session = application.firebaseSession(identity.uid(), identity.email());
            String token = session.token();
            if (path.equals("/api/firebase-session")) {
                var user = (sugarrush.domain.user.User) application.execute(new Command(token, Action.SESSION_INFO));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", user.id());
                result.put("username", user.username());
                result.put("role", user.role());
                // False for accounts that never chose an account type (older accounts or an interrupted sign-up).
                result.put("enrolled", application.enrollment(identity.uid()) != null);
                send(exchange, 200, result);
                return;
            }
            Action action = Action.valueOf(required(fields, "action"));
            Map<String, Integer> items = new LinkedHashMap<>();
            Map<String, String> profile = new LinkedHashMap<>();
            for (var entry : fields.entrySet()) {
                if (entry.getKey().startsWith("item.")) {
                    items.put(entry.getKey().substring(5), Integer.valueOf(entry.getValue()));
                }
                if (entry.getKey().startsWith("profile.")) {
                    profile.put(entry.getKey().substring(8), entry.getValue());
                }
            }
            if (remoteRestaurants) retryUnsavedRestaurants(identity.uid(), session.user().role(), firebaseToken);
            if (remoteRestaurants && (action == Action.VIEW_PROFILE || action == Action.UPDATE_PROFILE))
                loadProfile(identity.uid(), session.user().id(), firebaseToken);
            Command command = switch (action) {
            case CREATE_RESTAURANT -> new Command(token, action, null, null, null, required(fields, "name"), null, null);
            case PUT_MENU_ITEM -> new Command(token, action, required(fields, "restaurantId"), null, null, null, null,
                    new MenuItem(required(fields, "itemId"), required(fields, "name"),
                            new BigDecimal(required(fields, "price")), MenuItem.Status.valueOf(required(fields, "itemStatus")),
                            fields.get("description"), fields.get("imageUrl")));
            case DELETE_MENU_ITEM -> new Command(token, action, required(fields, "restaurantId"), null, null,
                    required(fields, "itemId"), null, null);
            case SET_RESTAURANT_LOCATION -> new Command(token, action, required(fields, "restaurantId"), null, null, null,
                    null, null, Map.of("address", required(fields, "address"), "addressX", required(fields, "addressX"),
                            "addressY", required(fields, "addressY")));
            case RESTAURANT_QUEUE ->
                new Command(token, action, required(fields, "restaurantId"), null, null, null, null, null);
            case SET_RESTAURANT_STATUS -> new Command(token, action, required(fields, "restaurantId"), null, null, null,
                    Restaurant.Status.valueOf(required(fields, "status")), null);

            case PLACE_ORDER -> Command.place(token, required(fields, "restaurantId"), items, Map.of(
                    "address", required(fields, "address"), "addressX", required(fields, "addressX"),
                    "addressY", required(fields, "addressY")));
            case PRICE_QUOTE -> new Command(token, action, required(fields, "restaurantId"), null, items, null, null, null);
            case CANCEL_ORDER, ACCEPT_ORDER, REJECT_ORDER, START_PREPARING, MARK_READY, ACCEPT_DELIVERY_OFFER, CONFIRM_PICKUP, CONFIRM_DELIVERY, REASSIGN_ORDER, VIEW_ORDER_SUMMARY, REFUND_ORDER -> // Dashboard can send commands to backend through APi
                Command.order(token, action, required(fields, "orderId"), fields.get("reason"));        // The commands requests ertrival of summaries
            case UPDATE_PROFILE -> Command.profile(token, profile);
            case BROWSE_RESTAURANTS, LIST_ORDERS, SESSION_INFO, ROLE_MENU, LOGOUT, DASHBOARD, EVENT_LOG, COMMAND_LOG, LIST_LEDGER, VIEW_PROFILE, COURIER_OFFER, COURIER_STATUS, GO_ON_DUTY, GO_OFF_DUTY, CITY_MAP, RESET_SYSTEM ->
                new Command(token, action);
            default -> throw new IllegalArgumentException("This endpoint does not support that command yet");

            };
            if (remoteRestaurants && action == Action.RESET_SYSTEM) {
                // Check the role before deleting anything in Firebase; the command chain checks it again below.
                if (!action.permits(session.user().role()))
                    throw new SecurityException("Action not permitted for " + session.user().role());
                deleteRemoteRestaurants(firebaseToken);
            }
            Object result = application.execute(command);
            if (action == Action.RESET_SYSTEM) {
                restaurantsSyncedAt = 0; // reload the (now empty) remote restaurants
                unsavedRestaurants.clear();
            }
            if (remoteRestaurants && switch (action) {
                case CREATE_RESTAURANT, PUT_MENU_ITEM, DELETE_MENU_ITEM, SET_RESTAURANT_STATUS, SET_RESTAURANT_LOCATION -> true;
                default -> false;
            }) {
                String restaurantId = action == Action.CREATE_RESTAURANT
                        ? ((sugarrush.application.restaurant.RestaurantService.View) result).id() : command.restaurantId();
                try {
                    saveRestaurant(restaurantId, firebaseToken);
                } catch (RuntimeException failure) {
                    if (action == Action.CREATE_RESTAURANT) {
                        // Not saved, so not created: otherwise it would vanish on the next server restart.
                        application.removeRestaurant(restaurantId);
                        throw failure;
                    }
                    unsavedRestaurants.add(restaurantId);
                    throw new IllegalStateException("Your change is applied but could not be saved to Firebase yet; it is retried "
                            + "automatically. " + message(failure), failure);
                }
            }
            if (remoteRestaurants && action == Action.UPDATE_PROFILE)
                saveProfile(identity.uid(), ((ProfileService.View) result).profile(), firebaseToken);
            send(exchange, 200, result);
        } catch (FirebaseTokenVerifier.UnavailableException failure) {
            send(exchange, 503, Map.of("error", message(failure)));
        } catch (SecurityException failure) {
            send(exchange, 403, Map.of("error", message(failure)));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            send(exchange, 400, Map.of("error", message(failure)));
        } catch (Exception failure) {
            // Logged so the cause shows up in the server log (for example on Render) instead of being lost.
            System.err.println("Unexpected error for " + exchange.getRequestURI().getPath() + ":");
            failure.printStackTrace();
            send(exchange, 500, Map.of("error", "Unexpected server error"));
        } finally {
            exchange.close();
        }
    }

    // Records the account type chosen at sign-up and, for restaurant accounts, opens their first restaurant.
    private sugarrush.domain.user.User registerAccount(FirebaseTokenVerifier.Identity identity, Map<String, String> fields,
            String firebaseToken) {
        Enrollment requested = new Enrollment(Role.valueOf(required(fields, "role")), fields.get("restaurantName"));
        Enrollment stored = remoteRestaurants ? enrollmentFor(identity, firebaseToken, true) : application.enrollment(identity.uid());
        if (stored != null && !stored.equals(requested))
            throw new IllegalStateException(alreadyRegistered(stored));
        Enrollment enrollment = remoteRestaurants && stored == null ? remoteEnrollment(identity.uid(), firebaseToken, requested)
                : requested;
        var user = application.registerFirebaseAccount(identity.uid(), identity.email(), enrollment.role(),
                enrollment.restaurantName());
        unenrolledCheckedAt.remove(identity.uid());
        // The sign-up restaurant is created on first registration. A retry also creates it when an earlier attempt
        // failed before the sign-up finished, but never after that, so an operator Reset is not undone.
        boolean firstSignup = stored == null || (remoteRestaurants && !signupFinished(identity.uid(), firebaseToken));
        if (enrollment.role() == Role.RESTAURANT && firstSignup && !application.ownsRestaurant(user.id())) {
            var created = application.createSignupRestaurant(user, enrollment.restaurantName());
            if (remoteRestaurants) {
                try {
                    saveRestaurant(created.id(), firebaseToken);
                } catch (RuntimeException failure) {
                    application.removeRestaurant(created.id());
                    throw failure;
                }
            }
        }
        return user;
    }

    private static String alreadyRegistered(Enrollment stored) {
        String type = stored.role().name().charAt(0) + stored.role().name().substring(1).toLowerCase(Locale.ROOT);
        return "This email is already registered as a " + type + " account"
                + (stored.restaurantName().isEmpty() ? "" : " (" + stored.restaurantName() + ")")
                + ". Choose that account type to continue; it cannot be changed.";
    }

    // The account's enrollment: from memory when known, otherwise from Firebase. Accounts without one are looked
    // up again at most every 30 seconds, so polling does not query Firebase on every request.
    private Enrollment enrollmentFor(FirebaseTokenVerifier.Identity identity, String token, boolean fresh) {
        Enrollment known = application.enrollment(identity.uid());
        if (known != null) return known;
        long now = System.currentTimeMillis();
        Long checked = unenrolledCheckedAt.get(identity.uid());
        if (!fresh && checked != null && now - checked < ENROLLMENT_RECHECK_MS) return null;
        Enrollment remote = remoteEnrollment(identity.uid(), token, null);
        if (remote == null) {
            unenrolledCheckedAt.put(identity.uid(), now);
            return null;
        }
        application.registerFirebaseAccount(identity.uid(), identity.email(), remote.role(), remote.restaurantName());
        unenrolledCheckedAt.remove(identity.uid());
        return remote;
    }

    // The client writes users/{uid} only after the backend registration succeeded, so its presence marks a finished sign-up.
    private boolean signupFinished(String uid, String token) {
        try {
            HttpResponse<String> response = http.send(firebase(databaseUrl.replaceAll("/+$", "") + "/users/"
                    + java.net.URLEncoder.encode(uid, StandardCharsets.UTF_8) + ".json?shallow=true&auth="
                    + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8)).GET().build(), HttpResponse.BodyHandlers.ofString());
            // When Firebase cannot answer, assume finished: the owner can still create a restaurant from the dashboard.
            return response.statusCode() != 200 || !response.body().trim().equals("null");
        } catch (IOException failure) {
            return true;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return true;
        }
    }

    private static HttpRequest.Builder firebase(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(FIREBASE_TIMEOUT);
    }

    private static Restaurant decodeRestaurant(String id, Map<?, ?> data) {
        if (!(data.get("x") instanceof Number x) || !(data.get("y") instanceof Number y))
            throw new IllegalArgumentException("Restaurant position missing");
        Restaurant restaurant = new Restaurant(id, String.valueOf(data.get("ownerId")), String.valueOf(data.get("name")),
                x.doubleValue(), y.doubleValue(), Restaurant.Status.valueOf(String.valueOf(data.get("status"))));
        // Restaurants saved before street addresses existed get a real Montreal address.
        if (data.get("address") instanceof String address && !address.isBlank())
            restaurant.relocate(address, restaurant.x(), restaurant.y());
        else
            sugarrush.application.restaurant.RestaurantService.placeInMontreal(restaurant);
        // Firebase returns a list as an array, or as an object when some indexes are missing.
        Object menu = data.get("menu");
        Collection<?> entries = menu instanceof List<?> list ? list : menu instanceof Map<?, ?> map ? map.values() : List.of();
        for (Object raw : entries) {
            if (!(raw instanceof Map<?, ?> item)) continue;
            try {
                restaurant.putItem(new MenuItem(String.valueOf(item.get("id")), String.valueOf(item.get("name")),
                        new BigDecimal(String.valueOf(item.get("price"))), MenuItem.Status.valueOf(String.valueOf(item.get("status"))),
                        item.get("description") == null ? "" : String.valueOf(item.get("description")),
                        item.get("imageUrl") == null ? "" : String.valueOf(item.get("imageUrl"))));
            } catch (RuntimeException invalid) {
                System.err.println("Skipped an invalid menu item in restaurant " + id + ": " + message(invalid));
            }
        }
        return restaurant;
    }

    private Enrollment remoteEnrollment(String uid, String token, Enrollment requested) {
        if (databaseUrl == null || databaseUrl.isBlank()) throw new IllegalStateException("Database URL required");
        String url = databaseUrl.replaceAll("/+$", "") + "/accountRegistrations/"
                + java.net.URLEncoder.encode(uid, StandardCharsets.UTF_8) + ".json?auth="
                + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
        try {
            HttpResponse<String> response = http.send(firebase(url).header("X-Firebase-ETag", "true").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw firebaseFailure("Could not load account registration; check the deployed database rules", response);
            if (!response.body().trim().equals("null")) {
                var data = Json.object(response.body());
                Enrollment existing = new Enrollment(Role.valueOf(String.valueOf(data.get("role"))), String.valueOf(data.getOrDefault("restaurantName", "")));
                if (requested != null && !requested.equals(existing)) throw new IllegalStateException(alreadyRegistered(existing));
                return existing;
            }
            if (requested == null) return null;
            String body = json(Map.of("role", requested.role(), "restaurantName", requested.restaurantName()));
            HttpResponse<String> saved = http.send(firebase(url).header("Content-Type", "application/json")
                    .header("if-match", response.headers().firstValue("etag").orElse("null_etag"))
                    .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (saved.statusCode() == 412) {
                Enrollment existing = remoteEnrollment(uid, token, null);
                if (requested.equals(existing)) return existing;
                throw new IllegalStateException(existing == null ? "Account registration changed; retry" : alreadyRegistered(existing));
            }
            if (saved.statusCode() != 200)
                throw firebaseFailure("Could not save account registration; check the deployed database rules", saved);
            return requested;
        } catch (IOException failure) {
            throw new IllegalStateException("Account registration service unavailable", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Account registration interrupted", failure);
        }
    }

    private static IllegalStateException firebaseFailure(String message, HttpResponse<String> response) {
        String detail = response.body() == null ? "" : response.body().replaceAll("\\s+", " ");
        System.err.println(message + " (Firebase " + response.statusCode() + ": "
                + detail.substring(0, Math.min(200, detail.length())) + ")");
        return new IllegalStateException(message + " (Firebase answered " + response.statusCode() + ")");
    }

    private String restaurantUrl(String id, String token) {
        return databaseUrl.replaceAll("/+$", "") + "/restaurants" + (id == null ? "" : "/" + id)
                + ".json?auth=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    // Loads restaurants from Firebase on the first request, then imports new ones (for example created through
    // another running backend) at most every 15 seconds. Only the first load is required to succeed.
    private synchronized void syncRestaurants(String token) {
        long now = System.currentTimeMillis();
        if (restaurantsSyncedAt != 0 && now - restaurantsSyncedAt < restaurantSyncMs) return;
        if (databaseUrl == null || databaseUrl.isBlank()) throw new IllegalStateException("Database URL required");
        try {
            HttpResponse<String> response = http.send(firebase(restaurantUrl(null, token)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw firebaseFailure("Could not load restaurants from Firebase", response);
            if (!response.body().trim().equals("null")) {
                for (var entry : Json.object(response.body()).entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?> data)) continue;
                    try {
                        application.importRestaurant(decodeRestaurant(entry.getKey(), data));
                    } catch (RuntimeException invalid) {
                        // One damaged record must not take the whole service down.
                        System.err.println("Skipped invalid restaurant " + entry.getKey() + " in Firebase: " + message(invalid));
                    }
                }
            }
            restaurantsSyncedAt = now;
        } catch (IOException | RuntimeException failure) {
            if (restaurantsSyncedAt == 0)
                throw failure instanceof IllegalStateException state ? state
                        : new IllegalStateException("Could not load restaurants from Firebase", failure);
            System.err.println("Restaurant refresh from Firebase failed; keeping the current list: " + message(failure));
            restaurantsSyncedAt = now;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not load restaurants from Firebase", failure);
        }
    }

    // Changes whose Firebase save failed are saved again on a later request from the owner (or an operator).
    private void retryUnsavedRestaurants(String uid, Role role, String token) {
        for (Iterator<String> pending = unsavedRestaurants.iterator(); pending.hasNext();) {
            String id = pending.next();
            if (!application.hasRestaurant(id)) {
                pending.remove();
                continue;
            }
            if (role != Role.OPERATOR && !application.restaurantView(id).ownerId().equals("firebase:" + uid)) continue;
            try {
                saveRestaurant(id, token);
                pending.remove();
            } catch (RuntimeException stillFailing) {
                // Kept for the next request.
            }
        }
    }

    // Operator reset: removes every restaurant document. Accounts (users, accountRegistrations, profiles) are kept.
    private void deleteRemoteRestaurants(String token) {
        try {
            HttpResponse<String> listed = http.send(firebase(restaurantUrl(null, token) + "&shallow=true").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (listed.statusCode() != 200) throw firebaseFailure("Could not list restaurants in Firebase", listed);
            if (listed.body().trim().equals("null")) return;
            for (String id : Json.object(listed.body()).keySet()) {
                HttpResponse<String> deleted = http.send(firebase(restaurantUrl(id, token)).DELETE().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (deleted.statusCode() != 200)
                    throw firebaseFailure("Could not delete restaurants in Firebase; deploy the updated database rules", deleted);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not delete restaurants in Firebase", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Restaurant deletion interrupted", failure);
        }
    }

    private void saveRestaurant(String id, String token) {
        try {
            HttpResponse<String> response = http.send(firebase(restaurantUrl(id, token))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json(application.restaurantView(id))))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw firebaseFailure("Could not save restaurant to Firebase; check the deployed database rules", response);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save restaurant to Firebase", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not save restaurant to Firebase", failure);
        }
    }

    private String profileUrl(String uid, String token) {
        return databaseUrl.replaceAll("/+$", "") + "/profiles/" + uid + ".json?auth="
                + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private synchronized void loadProfile(String uid, String userId, String token) {
        if (!profilesLoaded.add(uid)) return;
        try {
            HttpResponse<String> response = http.send(firebase(profileUrl(uid, token)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw firebaseFailure("Could not load profile from Firebase", response);
            if (!response.body().trim().equals("null")) {
                Map<String, Object> data = Json.object(response.body());
                application.importProfile(userId, new Profile(text(data, "displayName"), text(data, "phone"),
                        text(data, "deliveryAddress"), text(data, "vehicle")));
            }
        } catch (IOException failure) {
            profilesLoaded.remove(uid);
            throw new IllegalStateException("Could not load profile from Firebase", failure);
        } catch (InterruptedException failure) {
            profilesLoaded.remove(uid);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not load profile from Firebase", failure);
        } catch (RuntimeException failure) {
            profilesLoaded.remove(uid);
            throw failure;
        }
    }

    private void saveProfile(String uid, Profile profile, String token) {
        try {
            HttpResponse<String> response = http.send(firebase(profileUrl(uid, token))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json(profile.values())))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw firebaseFailure("Could not save profile to Firebase", response);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not save profile to Firebase", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Could not save profile to Firebase", failure);
        }
    }

    private static String text(Map<String, Object> data, String key) {
        return data.get(key) instanceof String value ? value : "";
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
