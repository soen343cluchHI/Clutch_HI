package sugarrush.application.map;

import java.util.*;
import sugarrush.application.courier.CourierService;
import sugarrush.application.order.OrderService;
import sugarrush.domain.courier.Courier;
import sugarrush.domain.user.*;

// Live map data for the dashboard: where the city grid sits on the real map and every on-duty courier.
public final class CityMapService {
    public record Origin(double latitude, double longitude) {
        public Origin {
            if (!Double.isFinite(latitude) || Math.abs(latitude) > 90 || !Double.isFinite(longitude)
                    || Math.abs(longitude) > 180)
                throw new IllegalArgumentException("Invalid map origin");
        }
    }

    // orderId is only shared with users allowed to see that order.
    public record CourierMarker(int id, double x, double y, Courier.Status status, String orderId, boolean offerAccepted,
            boolean mine, double targetX, double targetY, long arrivesInMs) {
    }

    public record CityMap(Origin origin, double kmPerSecond, List<CourierMarker> couriers) {
    }

    private final Origin origin;
    private final double kmPerSecond;
    private final CourierService couriers;
    private final OrderService orders;

    public CityMapService(Origin origin, double kmPerSecond, CourierService couriers, OrderService orders) {
        this.origin = Objects.requireNonNull(origin);
        this.kmPerSecond = kmPerSecond;
        this.couriers = couriers;
        this.orders = orders;
    }

    public CityMap view(User user) {
        List<CourierMarker> markers = new ArrayList<>();
        for (CourierService.View c : couriers.all()) {
            boolean mine = user.role() == Role.COURIER && c.userId().equals(user.id());
            if (c.status() == Courier.Status.OFFLINE && !mine) continue;
            String orderId = c.orderId() != null && (mine || orders.canView(user, c.orderId())) ? c.orderId() : null;
            markers.add(new CourierMarker(c.id(), c.x(), c.y(), c.status(), orderId, orderId != null && c.offerAccepted(),
                    mine, c.targetX(), c.targetY(), c.arrivesInMs()));
        }
        return new CityMap(origin, kmPerSecond, markers);
    }
}
