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
import sugarrush.domain.courier.Courier;

public final class OrderService {
    public record View(String id, String customerId, String restaurantId, List<OrderLine> lines, BigDecimal total,
            String state, String rejectionReason, DeliveryAddress deliveryAddress) {
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

    private final Map<String, Set<String>> carriersByOrder = new HashMap<>(); //remembers which courier carried an order
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
        return place(user, restaurantId, items, null);
    }

    public View place(User user, String restaurantId, Map<String, Integer> items, DeliveryAddress address) {
        Restaurant r = restaurants.require(restaurantId);
        List<OrderLine> lines = lines(r, items);
        Order order = address == null ? new Order(UUID.randomUUID().toString(), user.id(), r, lines)
                : new Order(UUID.randomUUID().toString(), user.id(), r, lines, address);
        pricing.calculate(order.total());
        orders.put(order.id(), order);
        OrderEvent event = order.placedEvent(); //every order gets a ledger entry when placed
        ledger.record(order, event, BigDecimal.ZERO.setScale(2)); //reuse the same event and timestamp as the existing event logger
        publisher.publish(event);
        return view(order);
    }

    // Same subtotal and pricing rules as place(), so the checkout preview matches the charge.
    public PricingService.Quote quote(String restaurantId, Map<String, Integer> items) {
        Restaurant r = restaurants.require(restaurantId);
        return pricing.quote(lines(r, items).stream().map(OrderLine::total).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static List<OrderLine> lines(Restaurant r, Map<String, Integer> items) {
        return items.entrySet().stream().map(e -> new OrderLine(r.item(e.getKey()), e.getValue())).toList();
    }

    // Where a courier collects the order and where it drops it off.
    public Courier.Point pickupPoint(String orderId) {
        Restaurant r = restaurants.require(require(orderId).restaurantId());
        return new Courier.Point(r.x(), r.y());
    }

    public Courier.Point dropoffPoint(String orderId) {
        DeliveryAddress address = require(orderId).deliveryAddress();
        return new Courier.Point(address.x(), address.y());
    }

    public Order require(String id) {
        Order order = orders.get(id);
        if (order == null)
            throw new IllegalArgumentException("Unknown order: " + id);
        return order;
    }

    //remembers a courier after pickup
    public void recordCarrier(String orderId, User courier) {
        Order order = require(orderId);

        if (courier.role() != Role.COURIER) {
            throw new SecurityException("Courier account required");
        }

        if (!"PickedUp".equals(order.state())) {
            throw new IllegalStateException(
                    "Record a carrier only after pickup");
        }

        carriersByOrder
                .computeIfAbsent(orderId, ignored -> new HashSet<>())
                .add(courier.id());
    }

    //defines the four role-access rules
    public boolean canView(User user, String orderId) {
        Order order = orders.get(orderId);
        return order != null && canView(user, order);
    }

    private boolean canView(User user, Order order) {
        return switch (user.role()) {
            case CUSTOMER ->
                order.customerId().equals(user.id());

            case RESTAURANT ->
                restaurants.require(order.restaurantId())
                        .ownerId().equals(user.id());

            case COURIER ->
                carriersByOrder
                        .getOrDefault(order.id(), Set.of())
                        .contains(user.id());

            case OPERATOR -> true;
        };
    }

    //applies those rules to the order list
    public List<View> visible(User user) {
        return orders.values().stream()
                .filter(order -> canView(user, order))
                .map(this::view)
                .toList();
    }

    public View transition(String id, String action, String reason) {
        Order order = require(id);
        Restaurant r = restaurants.require(order.restaurantId());
        OrderEvent event;
        BigDecimal refundAmount = BigDecimal.ZERO.setScale(2);
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
        case "REFUND_ORDER" -> {
            refundAmount = ledger.refundableAmount(id);
            event = order.refund();
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
        }//every successful transition is recorded
        BigDecimal charged = switch (event.newState()) {
            case "Delivered" -> summary(id).totalCharged();
            case "Refunded" -> refundAmount;
            default -> BigDecimal.ZERO.setScale(2);
        };
        ledger.record(order, event, charged);
        publisher.publish(event);
        if (action.equals("MARK_READY")) readyHandler.accept(id);
        return view(order);
    }

    //ledger now uses the same role filters as the order list
    public List<LedgerEntry> ledgerEntries(User user) {
        return ledger.all().stream()
                .filter(entry -> canView(user, require(entry.orderId())))
                .toList();
    }

    public View view(Order o) {
        return new View(o.id(), o.customerId(), o.restaurantId(), o.lines(), pricing.calculate(o.total()), o.state(), o.rejectionReason(),
                o.deliveryAddress());
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
