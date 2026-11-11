package sugarrush.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.io.Reader;
import java.nio.file.Files;
import java.time.Clock;
import sugarrush.application.auth.*;
import sugarrush.application.commands.*;
import sugarrush.application.courier.CourierService;
import sugarrush.application.events.*;
import sugarrush.application.dispatch.DispatchService;
import sugarrush.application.map.CityMapService;
import sugarrush.application.order.OrderService;
import sugarrush.application.pricing.PricingService;
import sugarrush.application.profile.ProfileService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.application.seed.SeedLoader;
import sugarrush.domain.user.*;

public final class SugarRush {
    private final RegistrationService registration = new RegistrationService();
    private final AuthenticationService authentication = new AuthenticationService(registration);
    private final ProfileService profiles = new ProfileService();
    private final Properties config;
    // The simulated world below is rebuilt from the seed by the operator's RESET_SYSTEM command.
    private Path seed, restaurantStore;
    private RestaurantService restaurants;
    private EventLogger events;
    private DashboardObserver dashboard;
    private OrderEventPublisher publisher;
    private CommandLog log;
    private OrderService orders;
    private CourierService couriers;
    private CommandProcessor commands;
    private final Map<String, User> firebaseAccounts = new HashMap<>();
    private final Map<String, Session> firebaseSessions = new HashMap<>();
    private final FirebaseRegistrationService firebaseRegistrations = new FirebaseRegistrationService();

    public synchronized void useRegistrationStore(Path accounts, Path restaurantStore) throws IOException {
        firebaseRegistrations.useStore(accounts);
        restaurants.useStore(restaurantStore);
        this.restaurantStore = restaurantStore;
    }

    public synchronized boolean hasConfiguredFirebaseAccount(String uid) {
        return firebaseAccounts.containsKey(uid);
    }

    public synchronized User registerFirebaseAccount(String uid, String email, Role role, String restaurantName) {
        if (firebaseAccounts.containsKey(uid)) throw new IllegalStateException("This account already has a configured backend identity");
        var enrollment = new FirebaseRegistrationService.Enrollment(role, restaurantName);
        firebaseRegistrations.register(uid, enrollment);
        User user = new User("firebase:" + uid, email, enrollment.role());
        if (role == Role.RESTAURANT) restaurants.provision(user, enrollment.restaurantName());
        Session previous = firebaseSessions.remove(uid);
        if (previous != null) {
            try { authentication.logout(previous.token()); } catch (SecurityException ignored) { }
        }
        return user;
    }

    private SugarRush(Properties config) {
        this.config = config;
        buildWorld();
    }

    private void buildWorld() {
        restaurants = new RestaurantService();
        events = new EventLogger();
        dashboard = new DashboardObserver();
        publisher = new OrderEventPublisher();
        log = new CommandLog();
        double kmPerSecond = number(config, "simulation.courierKmPerSecond", 0);
        orders = new OrderService(restaurants, publisher, new PricingService(config));
        couriers = new CourierService(orders, Clock.systemUTC(), kmPerSecond);
        DispatchService dispatch = new DispatchService(orders, couriers, restaurants);
        orders.onReady(dispatch::onOrderReady);
        couriers.onAvailable(dispatch::retryWaitingOrders);
        CityMapService map = new CityMapService(new CityMapService.Origin(
                number(config, "map.originLatitude", 45.5017), number(config, "map.originLongitude", -73.5673)),
                kmPerSecond, couriers, orders);
        commands = new CommandProcessor(authentication, restaurants, orders, profiles,
                couriers, dispatch, map, events, dashboard, log);
        publisher.subscribe(events);
        publisher.subscribe(dashboard);
    }

    // Returns orders, couriers, queues and logs to the seeded state; accounts and sessions stay signed in.
    private void reset() throws IOException {
        buildWorld();
        new SeedLoader().loadWorld(seed, registration, restaurants, couriers);
        if (restaurantStore != null) restaurants.useStore(restaurantStore);
        for (Session session : firebaseSessions.values()) {
            try {
                User user = authentication.verify(session.token());
                if (user.role() == Role.COURIER) couriers.provision(user);
            } catch (SecurityException expired) {
                // An expired session provisions its courier again on the next sign-in.
            }
        }
    }

    private static double number(Properties config, String key, double fallback) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Invalid number for " + key, invalid);
        }
    }

    public static SugarRush fromSeed(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        SugarRush app = new SugarRush(properties);
        app.seed = path;
        new SeedLoader().load(path, app.registration, app.restaurants, app.couriers);
        if ("true".equalsIgnoreCase(System.getenv("SUGARRUSH_DEMO_PROFILES")))
            new SeedLoader().loadProfiles(Path.of("backend/config/demo-profiles.properties"), app.registration, app.profiles);
        if (path.getFileName().toString().equals("live.properties")
                && !"true".equalsIgnoreCase(System.getenv("SUGARRUSH_REMOTE_RESTAURANTS"))) {
            app.restaurantStore = Path.of("backend/data/restaurants.properties");
            app.restaurants.useStore(app.restaurantStore);
            app.profiles.useStore(Path.of("backend/data/profiles.properties"));
        }
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("firebase.user.") && key.endsWith(".account")) {
                String uid = key.substring("firebase.user.".length(), key.length() - ".account".length());
                if (uid.isBlank())
                    throw new IllegalArgumentException("Firebase UID required");
                User approved = app.registration.byUsername(properties.getProperty(key).trim());
                if (approved.role() == Role.CUSTOMER)
                    throw new IllegalArgumentException(
                            "Customers use their own Firebase identity, not a shared seed account");
                app.firebaseAccounts.put(uid, approved);
            }
        }
        return app;
    }

    public synchronized User register(String username, String password, Collection<Role> roles) {
        if (roles != null && roles.contains(Role.OPERATOR))
            throw new SecurityException("Operator accounts require trusted provisioning");
        User user = registration.register(username, password, roles);
        if (user.role() == Role.COURIER) couriers.provision(user);
        return user;
    }

    public synchronized Session login(String username, String password) {
        return authentication.login(username, password);
    }

    // Roles come from configured legacy mappings or explicit persisted enrollment, never profile labels.
    public synchronized Session firebaseSession(String uid, String email, Role selectedRole) {
        if (uid == null || uid.isBlank())
            throw new SecurityException("Verified Firebase UID required");
        Session current = firebaseSessions.get(uid);
        if (current != null) {
            try {
                User existing = authentication.verify(current.token());
                User approvedAccount = firebaseAccounts.get(uid);
                var enrollment = firebaseRegistrations.find(uid);
                Role expectedRole = approvedAccount != null ? approvedAccount.role()
                        : enrollment == null ? Role.CUSTOMER : enrollment.role();
                if (existing.role() == expectedRole) return current;
                authentication.logout(current.token());
                firebaseSessions.remove(uid);
            } catch (SecurityException expired) {
                firebaseSessions.remove(uid);
            }
        }
        User approved = firebaseAccounts.get(uid);
        var enrollment = firebaseRegistrations.find(uid);
        User user = approved == null ? new User("firebase:" + uid, email, enrollment == null ? Role.CUSTOMER : enrollment.role())
                : new User(approved.id(), email, approved.role());
        if (approved == null && enrollment != null && enrollment.role() == Role.RESTAURANT)
            restaurants.provision(user, enrollment.restaurantName());
        if (user.role() == Role.COURIER) couriers.provision(user);
        Session session = authentication.openSession(user);
        firebaseSessions.put(uid, session);
        return session;
    }

    public synchronized Session firebaseSession(String uid, String email) {
        return firebaseSession(uid, email, Role.CUSTOMER);
    }

    public synchronized Object execute(Command command) {
        Object result = commands.process(command);
        if (command.action() == Action.RESET_SYSTEM) {
            try {
                reset();
            } catch (IOException failure) {
                throw new IllegalStateException("Could not reload the seed", failure);
            }
        }
        return result;
    }

    public synchronized void importRestaurant(sugarrush.domain.restaurant.Restaurant restaurant) {
        restaurants.importIfAbsent(restaurant);
    }

    public synchronized void importProfile(String userId, Profile profile) {
        profiles.put(userId, profile);
    }

    public synchronized RestaurantService.View restaurantView(String id) {
        return restaurants.browse(new User("system", "system", Role.OPERATOR)).stream()
                .filter(view -> view.id().equals(id)).findFirst().orElseThrow();
    }
}
