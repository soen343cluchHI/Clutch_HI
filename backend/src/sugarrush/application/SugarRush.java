package sugarrush.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.io.Reader;
import java.nio.file.Files;
import sugarrush.application.auth.*;
import sugarrush.application.commands.*;
import sugarrush.application.events.*;
import sugarrush.application.order.OrderService;
import sugarrush.application.profile.ProfileService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.application.seed.SeedLoader;
import sugarrush.domain.user.*;

public final class SugarRush {
    private final RegistrationService registration = new RegistrationService();
    private final AuthenticationService authentication = new AuthenticationService(registration);
    private final RestaurantService restaurants = new RestaurantService();
    private final EventLogger events = new EventLogger();
    private final DashboardObserver dashboard = new DashboardObserver();
    private final OrderEventPublisher publisher = new OrderEventPublisher();
    private final CommandLog log = new CommandLog();
    private final OrderService orders = new OrderService(restaurants, publisher);
    private final ProfileService profiles = new ProfileService();
    private final CommandProcessor commands = new CommandProcessor(authentication, restaurants, orders, profiles,
            events, dashboard, log);
    private final Map<String, User> firebaseAccounts = new HashMap<>();
    private final Map<String, Session> firebaseSessions = new HashMap<>();

    private SugarRush() {
        publisher.subscribe(events);
        publisher.subscribe(dashboard);
    }

    public static SugarRush fromSeed(Path path) throws IOException {
        SugarRush app = new SugarRush();
        new SeedLoader().load(path, app.registration, app.restaurants);
        if (path.getFileName().toString().equals("live.properties")
                && !"true".equalsIgnoreCase(System.getenv("SUGARRUSH_REMOTE_RESTAURANTS"))) {
            app.restaurants.useStore(Path.of("backend/data/restaurants.properties"));
            app.profiles.useStore(Path.of("backend/data/profiles.properties"));
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
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
        return registration.register(username, password, roles);
    }

    public synchronized Session login(String username, String password) {
        return authentication.login(username, password);
    }

    public synchronized Session firebaseSession(String uid, String email, Role selectedRole) {
        if (uid == null || uid.isBlank())
            throw new SecurityException("Verified Firebase UID required");
        Session current = firebaseSessions.get(uid);
        if (current != null) {
            try {
                User existing = authentication.verify(current.token());
                User approvedAccount = firebaseAccounts.get(uid);
                Role expectedRole = approvedAccount == null ? selectedRole : approvedAccount.role();
                if (existing.role() == expectedRole) return current;
                authentication.logout(current.token());
                firebaseSessions.remove(uid);
            } catch (SecurityException expired) {
                firebaseSessions.remove(uid);
            }
        }
        User approved = firebaseAccounts.get(uid);
        User user = approved == null ? new User("firebase:" + uid, email, selectedRole)
                : new User(approved.id(), email, approved.role());
        Session session = authentication.openSession(user);
        firebaseSessions.put(uid, session);
        return session;
    }

    public synchronized Session firebaseSession(String uid, String email) {
        return firebaseSession(uid, email, Role.CUSTOMER);
    }

    public synchronized Object execute(Command command) {
        return commands.process(command);
    }

    public synchronized void importRestaurant(sugarrush.domain.restaurant.Restaurant restaurant) {
        restaurants.add(restaurant);
    }

    public synchronized void importProfile(String userId, Profile profile) {
        profiles.put(userId, profile);
    }

    public synchronized RestaurantService.View restaurantView(String id) {
        return restaurants.browse(new User("system", "system", Role.OPERATOR)).stream()
                .filter(view -> view.id().equals(id)).findFirst().orElseThrow();
    }
}
