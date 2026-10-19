package sugarrush;

import java.nio.file.*;
import java.util.*;
import sugarrush.application.SugarRush;
import sugarrush.application.commands.*;
import sugarrush.application.profile.ProfileService;
import sugarrush.domain.user.*;

public final class ProfileChecks {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SugarRush app = SugarRush.fromSeed(Path.of("backend/config/demo.properties"));
        Session customer = app.login("customer", "customer-demo");
        Session staff = app.login("restaurant", "restaurant-demo");
        Session courier = app.login("courier", "courier-demo");
        Session operator = app.login("operator", "operator-demo");
        expect(SecurityException.class, () -> app.execute(new Command("", Action.VIEW_PROFILE)));
        expect(SecurityException.class, () -> app.execute(Command.profile("fake", Map.of("displayName", "Nobody"))));
        ProfileService.View empty = view(app, customer);
        check(empty.profile().equals(Profile.EMPTY), "New profile not empty");
        check(empty.email().equals("customer") && empty.role() == Role.CUSTOMER, "Profile identity incorrect");
        check(empty.editableFields().equals(List.of("displayName", "phone", "deliveryAddress")),
                "Customer fields incorrect");
        check(view(app, courier).editableFields().equals(List.of("displayName", "phone", "vehicle")),
                "Courier fields incorrect");
        check(view(app, staff).editableFields().equals(List.of("displayName", "phone")), "Staff fields incorrect");
        check(view(app, operator).editableFields().equals(List.of("displayName", "phone")),
                "Operator fields incorrect");
        ProfileService.View saved = update(app, customer,
                Map.of("displayName", "Ada Customer", "phone", "514-555-0100", "deliveryAddress", "1455 De Maisonneuve"));
        check(saved.profile().displayName().equals("Ada Customer"), "Customer name not saved");
        check(view(app, customer).profile().deliveryAddress().equals("1455 De Maisonneuve"), "Address not saved");
        update(app, customer, Map.of("phone", "514 555 0199"));
        check(view(app, customer).profile().displayName().equals("Ada Customer"), "Partial update erased name");
        check(view(app, customer).profile().phone().equals("514 555 0199"), "Partial update lost phone");
        expect(SecurityException.class, () -> update(app, customer, Map.of("vehicle", "Car")));
        expect(SecurityException.class, () -> update(app, courier, Map.of("deliveryAddress", "Somewhere")));
        expect(SecurityException.class, () -> update(app, staff, Map.of("vehicle", "Bike")));
        expect(SecurityException.class, () -> update(app, operator, Map.of("role", "CUSTOMER")));
        expect(IllegalArgumentException.class, () -> update(app, customer, Map.of("phone", "call me")));
        expect(IllegalArgumentException.class, () -> update(app, courier, Map.of("vehicle", "Rocket")));
        expect(IllegalArgumentException.class, () -> update(app, staff, Map.of("displayName", "x".repeat(81))));
        expect(IllegalArgumentException.class, () -> update(app, staff, Map.of()));
        check(view(app, customer).profile().phone().equals("514 555 0199"), "Rejected update changed profile");
        update(app, courier, Map.of("displayName", "Cora Courier", "vehicle", "Bike"));
        check(view(app, courier).profile().vehicle().equals("Bike"), "Courier vehicle not saved");
        check(view(app, staff).profile().equals(Profile.EMPTY), "Profiles leaked between users");
        List<?> commands = (List<?>) app.execute(new Command(operator.token(), Action.COMMAND_LOG));
        check(commands.stream().map(CommandLog.Entry.class::cast)
                .anyMatch(e -> e.action() == Action.UPDATE_PROFILE && !e.success()), "Profile errors not logged");

        Path folder = Files.createTempDirectory("profile-checks");
        Path store = folder.resolve("profiles.properties");
        try {
            User owner = new User("firebase:owner", "owner@example.com", Role.CUSTOMER);
            ProfileService first = new ProfileService();
            first.useStore(store);
            first.update(owner, Map.of("displayName", "Owner Name", "deliveryAddress", "123 Rue Sainte-Catherine"));
            ProfileService restored = new ProfileService();
            restored.useStore(store);
            check(restored.view(owner).profile().displayName().equals("Owner Name"), "Profile not persisted");
            check(restored.view(owner).profile().deliveryAddress().equals("123 Rue Sainte-Catherine"),
                    "Address not persisted");
        } finally {
            Files.deleteIfExists(store);
            Files.deleteIfExists(folder);
        }
        System.out.println("Passed " + checks + " profile checks.");
    }

    private static ProfileService.View view(SugarRush app, Session session) {
        return (ProfileService.View) app.execute(new Command(session.token(), Action.VIEW_PROFILE));
    }

    private static ProfileService.View update(SugarRush app, Session session, Map<String, String> fields) {
        return (ProfileService.View) app.execute(Command.profile(session.token(), fields));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition)
            throw new AssertionError(message);
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        checks++;
        try {
            action.run();
        } catch (RuntimeException failure) {
            if (type.isInstance(failure))
                return;
            throw failure;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
