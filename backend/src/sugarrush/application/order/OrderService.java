package sugarrush.application.order;

import java.math.BigDecimal;
import java.util.*;
import sugarrush.application.events.OrderEventPublisher;
import sugarrush.application.pricing.PricingService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.order.*;
import sugarrush.domain.restaurant.Restaurant;
import sugarrush.domain.user.*;
import java.time.Instant;
import sugarrush.application.billing.LedgerService;
import sugarrush.domain.billing.LedgerEntry;

public final class OrderService {
    public record View(String id, String customerId, String restaurantId, List<OrderLine> lines, BigDecimal total,
            String state, String rejectionReason) {
    }

    //To define the info the summary of the order contains
    public record Summary(
        String orderId,
        String restaurantId,
        String restaurantName,
        List<OrderLine> lines,
        BigDecimal subtotal,
        BigDecimal totalCharged,
        Instant deliveredAt) {

        public Summary {
            lines = List.copyOf(lines);
        }
    }

    private final Map<String, Order> orders = new LinkedHashMap<>();
    private final Map<String, Summary> summaries = new LinkedHashMap<>();// store the summaries
    private final LedgerService ledger = new LedgerService(); //order service needs a ledger instance to record order activity
    private final RestaurantService restaurants;
    private final OrderEventPublisher publisher;
    private final PricingService pricing;
    private java.util.function.Consumer<String> readyHandler = id -> {};

    public void onReady(java.util.function.Consumer<String> handler) {
        readyHandler = Objects.requireNonNull(handler);
    }

    public OrderService(RestaurantService restaurants, OrderEventPublisher publisher, PricingService pricing) {
        this.restaurants = restaurants;
        this.publisher = publisher;
        this.pricing = pricing;
    }

    public View place(User user, String restaurantId, Map<String, Integer> items) {
        Restaurant r = restaurants.require(restaurantId);
        List<OrderLine> lines = items.entrySet().stream().map(e -> new OrderLine(r.item(e.getKey()), e.getValue()))
                .toList();
        Order order = new Order(UUID.randomUUID().toString(), user.id(), r, lines);
        pricing.calculate(order.total());
        orders.put(order.id(), order);
        OrderEvent event = order.placedEvent(); //every order gets a ledger entry when placed
        ledger.record(order, event, BigDecimal.ZERO.setScale(2)); //reuse the same event and timestamp as the existing event logger
        publisher.publish(event);
        return view(order);
    }

    public Order require(String id) {
        Order order = orders.get(id);
        if (order == null)
            throw new IllegalArgumentException("Unknown order: " + id);
        return order;
    }

    public List<View> visible(User user) {
        return orders.values().stream().filter(o -> switch (user.role()) {
        case CUSTOMER -> o.customerId().equals(user.id());
        case RESTAURANT -> restaurants.require(o.restaurantId()).ownerId().equals(user.id());
        case OPERATOR -> true;
        case COURIER -> false;
        }).map(this::view).toList();
    }

    public View transition(String id, String action, String reason) {
        Order order = require(id);
        Restaurant r = restaurants.require(order.restaurantId());
        OrderEvent event;
        switch (action) {
        case "CANCEL_ORDER" -> {
            event = order.cancel();
            r.queue().remove(id);
        }
        case "ACCEPT_ORDER" -> {
            event = order.accept();
            r.queue().add(id);
        }
        case "REJECT_ORDER" -> event = order.reject(reason);
        case "START_PREPARING" -> {
            r.queue().requireHead(id);
            event = order.prepare();
        }
        case "MARK_READY" -> {
            r.queue().requireHead(id);
            event = order.ready();
            r.queue().complete(id);
        }
        case "CONFIRM_PICKUP" -> event = order.pickUp();
        case "CONFIRM_DELIVERY" -> {                        //items subtotal
            BigDecimal totalCharged = pricing.calculate(order.total());//provides the total including the fees
            event = order.deliver(); //Summary saved when this succeeds                       
            summaries.put(id, new Summary(
                    order.id(),
                    r.id(),
                    r.name(),
                    order.lines(),
                    order.total(),
                    totalCharged,
                    event.timestamp()));
        }
        default -> throw new IllegalArgumentException("Unknown order action");
        }
        BigDecimal charged = "Delivered".equals(event.newState()) //every successful transition is recorded
                ? summary(id).totalCharged()
                : BigDecimal.ZERO.setScale(2);
        ledger.record(order, event, charged);
        publisher.publish(event);
        if (action.equals("MARK_READY")) readyHandler.accept(id);
        return view(order);
    }

        public List<LedgerEntry> ledgerEntries() {
        return ledger.all();
    }

    public View view(Order o) {
        return new View(o.id(), o.customerId(), o.restaurantId(), o.lines(), pricing.calculate(o.total()), o.state(), o.rejectionReason());
    }

    public Summary summary(String orderId) {
        require(orderId);

        Summary summary = summaries.get(orderId);

        if (summary == null) {
            throw new IllegalStateException(
                    "Summary is available only after delivery");
        }

        return summary;
    }
    
}
