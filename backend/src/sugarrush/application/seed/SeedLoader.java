package sugarrush.application.seed;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import sugarrush.application.auth.RegistrationService;
import sugarrush.application.restaurant.RestaurantService;
import sugarrush.application.profile.ProfileService;
import sugarrush.domain.restaurant.*;
import sugarrush.domain.user.*;

public final class SeedLoader {
    public void loadProfiles(Path path, RegistrationService registration, ProfileService profiles) throws IOException {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) { p.load(reader); }
        for (String username : ids(p, "profiles")) {
            User user = registration.byUsername(username);
            String base = "profile." + username + ".";
            Profile profile = new Profile(p.getProperty(base + "displayName", ""),
                    p.getProperty(base + "phone", ""), p.getProperty(base + "deliveryAddress", ""),
                    p.getProperty(base + "vehicle", ""));
            for (var entry : profile.values().entrySet())
                if (!entry.getValue().isEmpty() && !Profile.editableFields(user.role()).contains(entry.getKey()))
                    throw new IllegalArgumentException("Profile field " + entry.getKey() + " is not editable by " + user.role());
            profiles.put(user.id(), profile);
        }
    }

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
        for (String key : optionalIds(p, "restaurants")) {
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

    private static List<String> optionalIds(Properties p, String key) {
        String value = p.getProperty(key, "").trim();
        return value.isEmpty() ? List.of() : Arrays.stream(value.split(",")).map(String::trim).toList();
    }
}
