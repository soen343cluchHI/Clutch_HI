package sugarrush.domain.user;

import java.util.*;

public record Profile(String displayName, String phone, String deliveryAddress, String vehicle) {
    public static final Profile EMPTY = new Profile("", "", "", "");
    public static final List<String> VEHICLES = List.of("Bike", "Scooter", "Car", "On foot");

    public Profile {
        displayName = clean(displayName, "Name", 80);
        phone = clean(phone, "Phone number", 20);
        deliveryAddress = clean(deliveryAddress, "Delivery address", 160);
        vehicle = clean(vehicle, "Vehicle", 20);
        if (!phone.isEmpty() && !phone.matches("[0-9+()\\- ]{7,20}"))
            throw new IllegalArgumentException("Enter a valid phone number");
        if (!vehicle.isEmpty() && !VEHICLES.contains(vehicle))
            throw new IllegalArgumentException("Vehicle must be one of " + String.join(", ", VEHICLES));
    }

    public static List<String> editableFields(Role role) {
        return switch (Objects.requireNonNull(role)) {
        case CUSTOMER -> List.of("displayName", "phone", "deliveryAddress");
        case COURIER -> List.of("displayName", "phone", "vehicle");
        case RESTAURANT, OPERATOR -> List.of("displayName", "phone");
        };
    }

    public Profile update(Role role, Map<String, String> changes) {
        List<String> allowed = editableFields(role);
        for (String field : changes.keySet())
            if (!allowed.contains(field))
                throw new SecurityException("A " + role + " account cannot edit " + field);
        return new Profile(changes.getOrDefault("displayName", displayName), changes.getOrDefault("phone", phone),
                changes.getOrDefault("deliveryAddress", deliveryAddress), changes.getOrDefault("vehicle", vehicle));
    }

    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("displayName", displayName);
        values.put("phone", phone);
        values.put("deliveryAddress", deliveryAddress);
        values.put("vehicle", vehicle);
        return values;
    }

    private static String clean(String value, String label, int limit) {
        String text = value == null ? "" : value.trim();
        if (text.length() > limit)
            throw new IllegalArgumentException(label + " must be at most " + limit + " characters");
        return text;
    }
}
