package sugarrush;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Order {
    private final String id;
    private OrderState state = new PlacedState();
    private final OrderEventRepository repository;
    private final OrderEventPublisher publisher;

    public Order(String id, OrderEventRepository repository, OrderEventPublisher publisher) {
        this.id = Objects.requireNonNull(id);
        this.repository = Objects.requireNonNull(repository);
        this.publisher = Objects.requireNonNull(publisher);
    }
    public String state() { return state.name(); }
    public void accept() { transition(state.accept()); }
    public void reject() { transition(state.reject()); }
    public void prepare() { transition(state.prepare()); }
    public void ready() { transition(state.ready()); }
    private void transition(OrderState next) {
        OrderEvent event = new OrderEvent(UUID.randomUUID(), id, state.name(), next.name(), Instant.now());
        repository.save(event);
        state = next;
        publisher.publish(event);
    }
}
