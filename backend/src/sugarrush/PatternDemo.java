package sugarrush;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PatternDemo {
    public static void main(String[] args) {
        OrderEventRepository repository = new OrderEventRepository();
        OrderEventPublisher publisher = new OrderEventPublisher();
        DashboardObserver dashboard = new DashboardObserver();
        publisher.subscribe(new EventLogger());
        publisher.subscribe(dashboard);
        Order order = new Order("demo-1", repository, publisher);
        expect(IllegalStateException.class, order::ready);
        check(repository.all().isEmpty() && order.state().equals("Placed"), "Invalid transition changed order");
        order.accept();
        expect(IllegalStateException.class, order::reject);
        order.prepare();
        order.ready();
        expect(IllegalStateException.class, order::accept);
        check("Ready".equals(dashboard.latestState("demo-1")), "Observer not notified");
        Order rejected = new Order("demo-2", repository, publisher);
        rejected.reject();
        expect(IllegalStateException.class, rejected::accept);
        check("Rejected".equals(dashboard.latestState("demo-2")), "Rejection not published");
        Set<UUID> ids = new HashSet<>();
        for (OrderEvent event : repository.all()) {
            check(ids.add(event.eventId()), "Duplicate event ID");
            check(event.timestamp() != null, "Missing timestamp");
        }
        check(ids.size() == 4, "Wrong event count");
        check(repository.all().get(0).previousState().equals("Placed") &&
              repository.all().get(0).newState().equals("Accepted"), "Incorrect transition record");

        CommandProcessor unconfigured = new CommandProcessor();
        expect(SecurityException.class, () -> unconfigured.process(new Command("", "customer-demo")));
        expect(SecurityException.class, () -> unconfigured.process(new Command("fake-token", "operator-demo")));
        // TEST DOUBLE ONLY: these fixed strings are not Firebase tokens or a second login system.
        CommandProcessor fixture = new CommandProcessor(token -> {
            if (!"fixture".equals(token)) throw new SecurityException("Invalid test token");
            return new AuthenticatedUser("test-user", "Customer");
        });
        check(fixture.process(new Command("fixture", "customer-demo")).startsWith("Executed"), "Execution missing");
        expect(SecurityException.class, () -> fixture.process(new Command("bad", "customer-demo")));
        expect(SecurityException.class, () -> fixture.process(new Command("fixture", "operator-demo")));
        expect(SecurityException.class, () -> fixture.process(new Command("fixture", "unknown")));
        System.out.println("All State, Observer, and Chain of Responsibility checks passed.");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void expect(Class<? extends RuntimeException> type, Runnable action) {
        try { action.run(); }
        catch (RuntimeException failure) {
            if (type.isInstance(failure)) return;
            throw failure;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
