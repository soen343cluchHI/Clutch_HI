package sugarrush.application.seed;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import sugarrush.application.auth.RegistrationService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.domain.restaurant.*;
import sugarrush.domain.user.*;

public final class SeedLoader {
    public void load(Path path, RegistrationService registration, RestaurantService restaurants) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            p.load(reader);
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (String key : ids(p, "users")) {
            String base = "user." + key + ".";
            Role role = Role.valueOf(required(p, base + "role"));
            registration.register(required(p, base + "username"), required(p, base + "password"), List.of(role));
            roles.add(role);
        }
        if (!roles.containsAll(EnumSet.allOf(Role.class)))
            throw new IllegalArgumentException("Seed needs an account per role");
        for (String key : ids(p, "restaurants")) {
            String base = "restaurant." + key + ".";
            User owner = registration.byUsername(required(p, base + "owner"));
            if (owner.role() != Role.RESTAURANT)
                throw new IllegalArgumentException("Owner must have restaurant role");
            Restaurant r = new Restaurant(key, owner.id(), required(p, base + "name"),
                    Double.parseDouble(required(p, base + "x")), Double.parseDouble(required(p, base + "y")),
                    Restaurant.Status.valueOf(required(p, base + "status")));
            for (String item : ids(p, base + "items")) {
                String prefix = base + "item." + item + ".";
                r.putItem(
                        new MenuItem(item, required(p, prefix + "name"), new BigDecimal(required(p, prefix + "price")),
                                MenuItem.Status.valueOf(required(p, prefix + "status"))));
            }
            restaurants.add(r);
        }
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("Missing seed field: " + key);
        return value.trim();
    }

    private static List<String> ids(Properties p, String key) {
        return Arrays.stream(required(p, key).split(",")).map(String::trim).toList();
    }
}
