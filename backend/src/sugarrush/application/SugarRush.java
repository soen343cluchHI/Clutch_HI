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
import sugarrush.domain.restaurant.Restaurant;
import sugarrush.domain.user.*;

public final class SugarRush {
    private final RegistrationService registration = new RegistrationService();
    private final AuthenticationService authentication = new AuthenticationService(registration);
    private final ProfileService profiles = new ProfileService();
    private final Properties config;
    // The simulated world below is rebuilt empty by the operator's RESET_SYSTEM command; accounts survive it.
    private RestaurantService restaurants;
    private EventLogger events;
    private DashboardObserver dashboard;
    private OrderEventPublisher publisher;
    private CommandLog log;
    private OrderService orders;
    private CourierService couriers;
    private CommandProcessor commands;
    private final Map<String, Session> firebaseSessions = new HashMap<>();
    private final FirebaseRegistrationService firebaseRegistrations = new FirebaseRegistrationService();

    public synchronized FirebaseRegistrationService.Enrollment enrollment(String uid) {
        return firebaseRegistrations.find(uid);
    }

    // Records the role a Firebase account signed up with. The sign-up restaurant is created separately.
    public synchronized User registerFirebaseAccount(String uid, String email, Role role, String restaurantName) {
        var enrollment = new FirebaseRegistrationService.Enrollment(role, restaurantName);
        firebaseRegistrations.register(uid, enrollment);
        User user = new User("firebase:" + uid, email, enrollment.role());
        Session previous = firebaseSessions.remove(uid);
        if (previous != null) {
            try { authentication.logout(previous.token()); } catch (SecurityException ignored) { }
        }
        return user;
    }

    // Only called when a restaurant account first registers, so a Reset does not bring deleted restaurants back.
    public synchronized RestaurantService.View createSignupRestaurant(User owner, String name) {
        return restaurants.provision(owner, name);
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

    // Deletes every restaurant, order, ledger entry and log; accounts and sessions stay signed in and
    // signed-in couriers start again off duty.
    private void reset() {
        buildWorld();
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

    // Settings only (pricing, map, simulation); restaurants and accounts come from Firebase.
    public static SugarRush fromConfig(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return new SugarRush(properties);
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

    // Roles come from the account's persisted sign-up enrollment, never from profile labels.
    public synchronized Session firebaseSession(String uid, String email) {
        if (uid == null || uid.isBlank())
            throw new SecurityException("Verified Firebase UID required");
        var enrollment = firebaseRegistrations.find(uid);
        Role role = enrollment == null ? Role.CUSTOMER : enrollment.role();
        Session current = firebaseSessions.get(uid);
        if (current != null) {
            try {
                if (authentication.verify(current.token()).role() == role) return current;
                authentication.logout(current.token());
            } catch (SecurityException expired) {
                // Open a fresh session below.
            }
            firebaseSessions.remove(uid);
        }
        User user = new User("firebase:" + uid, email, role);
        if (role == Role.COURIER) couriers.provision(user);
        Session session = authentication.openSession(user);
        firebaseSessions.put(uid, session);
        return session;
    }

    public synchronized Object execute(Command command) {
        Object result = commands.process(command);
        if (command.action() == Action.RESET_SYSTEM) reset();
        return result;
    }

    public synchronized void importRestaurant(Restaurant restaurant) {
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
