package sugarrush.domain.courier;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class Courier {
    public enum Status {
        OFFLINE, IDLE, ASSIGNED, CARRYING
    }

    // Map coordinates in kilometres; travel is simulated along a straight line (route planning is out of scope).
    public record Point(double x, double y) {
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y))
                throw new IllegalArgumentException("Invalid position");
        }

        double distanceTo(Point other) {
            return Math.hypot(other.x - x, other.y - y);
        }
    }

    private final int id;
    private final String userId;
    private Point origin;
    private Point destination;
    private Instant departedAt, arrivesAt;
    private Status status = Status.OFFLINE;
    private String orderId;
    private boolean offerAccepted;

    public boolean offerAccepted() { return offerAccepted; }

    public void acceptOffer(String orderId) {
        if (status != Status.ASSIGNED || !Objects.equals(this.orderId, orderId) || offerAccepted)
            throw new IllegalStateException("No pending offer for this order");
        offerAccepted = true;
    }

    public Courier(int id, String userId, double x, double y) {
        if (id <= 0 || userId == null || userId.isBlank())
            throw new IllegalArgumentException("Courier identity required");
        this.id = id;
        this.userId = userId;
        this.origin = new Point(x, y);
        this.destination = origin;
    }

    public int id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public double x() {
        return positionAt(Instant.now()).x();
    }

    public double y() {
        return positionAt(Instant.now()).y();
    }

    public Point positionAt(Instant now) {
        if (arrivesAt == null || !now.isBefore(arrivesAt)) return destination;
        if (!now.isAfter(departedAt)) return origin;
        double progress = (double) Duration.between(departedAt, now).toMillis()
                / Duration.between(departedAt, arrivesAt).toMillis();
        return new Point(origin.x() + (destination.x() - origin.x()) * progress,
                origin.y() + (destination.y() - origin.y()) * progress);
    }

    public Point destination() {
        return destination;
    }

    public Instant arrivesAt() {
        return arrivesAt;
    }

    public boolean arrived(Instant now) {
        return arrivesAt == null || !now.isBefore(arrivesAt);
    }

    // A non-positive speed means the simulation is disabled and the courier is placed at the target at once.
    public void travelTo(Point target, Instant now, double kmPerSecond) {
        Objects.requireNonNull(target);
        Point start = positionAt(now);
        double seconds = kmPerSecond > 0 ? start.distanceTo(target) / kmPerSecond : 0;
        origin = start;
        destination = target;
        departedAt = now;
        arrivesAt = seconds > 0 ? now.plusMillis(Math.round(seconds * 1000)) : null;
    }

    public void stopAt(Point position) {
        origin = destination = Objects.requireNonNull(position);
        departedAt = arrivesAt = null;
    }

    public void halt(Instant now) {
        stopAt(positionAt(now));
    }

    public Status status() {
        return status;
    }

    public String orderId() {
        return orderId;
    }

    public void goOnDuty() {
        if (status != Status.OFFLINE)
            throw new IllegalStateException("Courier is already on duty");
        status = Status.IDLE;
    }

    public void goOffDuty() {
        if (status == Status.OFFLINE)
            throw new IllegalStateException("Courier is already off duty");
        if (status != Status.IDLE)
            throw new IllegalStateException("Cannot go off duty while holding an order");
        status = Status.OFFLINE;
    }

    public void assign(String orderId) {
        if (orderId == null || orderId.isBlank())
            throw new IllegalArgumentException("Order required");
        if (status != Status.IDLE)
            throw new IllegalStateException("Courier is " + status + ", not IDLE");
        this.orderId = orderId;
        offerAccepted = false;
        status = Status.ASSIGNED;
    }

    public void release(String orderId) {
        if (status != Status.ASSIGNED || !Objects.equals(this.orderId, orderId))
            throw new IllegalStateException("Only an assigned order can be released");
        this.orderId = null;
        offerAccepted = false;
        status = Status.IDLE;
    }

    public void pickUp(String orderId) {
        if (status != Status.ASSIGNED || !Objects.equals(this.orderId, orderId) || !offerAccepted)
            throw new IllegalStateException("Cannot pick up in " + status);
        status = Status.CARRYING;
    }

    public void deliver(String orderId) {
        if (status != Status.CARRYING || !Objects.equals(this.orderId, orderId))
            throw new IllegalStateException("Cannot deliver in " + status);
        this.orderId = null;
        offerAccepted = false;
        status = Status.IDLE;
    }
}
