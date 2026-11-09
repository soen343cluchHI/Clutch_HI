package sugarrush.domain.courier;

import java.util.Objects;

public final class Courier {
    public enum Status {
        OFFLINE, IDLE, ASSIGNED, CARRYING
    }

    private final int id;
    private final String userId;
    private final double x, y;
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
        if (!Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalArgumentException("Invalid position");
        this.id = id;
        this.userId = userId;
        this.x = x;
        this.y = y;
    }

    public int id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
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
