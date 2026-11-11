package sugarrush;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import sugarrush.application.SugarRush;
import sugarrush.domain.restaurant.*;
import sugarrush.domain.user.*;

// Test fixture: the app has no seed data, so the checks build their accounts and restaurants through the same
// sign-up and import calls the API server uses for Firebase accounts.
public final class TestWorld {
    public static final Path CONFIG = Path.of("backend/config/app.properties");

    public final SugarRush app;
    public final Session customer, staff, other, courier, operator;

    private TestWorld(String customerUid, String staffUid, String otherUid, String courierUid, String operatorUid)
            throws IOException {
        app = SugarRush.fromConfig(CONFIG);
        customer = app.firebaseSession(customerUid, customerUid);
        staff = account(staffUid, Role.RESTAURANT);
        other = account(otherUid, Role.RESTAURANT);
        courier = account(courierUid, Role.COURIER);
        operator = account(operatorUid, Role.OPERATOR);
        app.importRestaurant(restaurant("sugar-shop", staff, "Pastry Montreal", "4350 Rue Saint-Denis, Montréal", -1.05, 2.35,
                item("cake", "Chocolate Cake", "8.50"), item("cookie", "Chocolate Chip Cookie", "3.00"),
                item("croissant", "Butter Croissant", "4.50")));
        app.importRestaurant(restaurant("other-shop", other, "Nice Ice", "201 Rue Saint-Paul Est, Montréal", 1.25, 0.97,
                item("vanilla", "Vanilla Ice Cream", "5.00"), item("chocolate", "Chocolate Ice Cream", "5.00"),
                item("strawberry", "Strawberry Ice Cream", "5.50")));
    }

    public static TestWorld create() throws IOException {
        return new TestWorld("customer", "restaurant", "restaurant2", "courier", "operator");
    }

    public static TestWorld create(String customerUid, String staffUid, String otherUid, String courierUid,
            String operatorUid) throws IOException {
        return new TestWorld(customerUid, staffUid, otherUid, courierUid, operatorUid);
    }

    private Session account(String uid, Role role) {
        app.registerFirebaseAccount(uid, uid, role, "");
        return app.firebaseSession(uid, uid);
    }

    private static Restaurant restaurant(String id, Session owner, String name, String address, double x, double y,
            MenuItem... menu) {
        Restaurant restaurant = new Restaurant(id, owner.user().id(), name, x, y, Restaurant.Status.OPEN);
        restaurant.relocate(address, x, y);
        for (MenuItem item : menu) restaurant.putItem(item);
        return restaurant;
    }

    private static MenuItem item(String id, String name, String price) {
        return new MenuItem(id, name, new BigDecimal(price), MenuItem.Status.AVAILABLE);
    }
}
