package sugarrush;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import sugarrush.application.SugarRush;
import sugarrush.application.commands.Command;
import sugarrush.application.order.OrderService;
import sugarrush.application.pricing.PricingService;
import sugarrush.domain.pricing.*;

public final class PricingChecks {
    public static void main(String[] args) throws Exception {
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("backend/config/demo.properties"))) {
            values.load(reader);
        }
        PricingService pricing = new PricingService(values);
        check(pricing.calculate(new BigDecimal("8.50")), "14.50");
        check(pricing.calculate(new BigDecimal("14.99")), "20.99");
        check(pricing.calculate(new BigDecimal("15.00")), "19.00");
        check(pricing.calculate(new BigDecimal("17.00")), "21.00");

        SugarRush app = SugarRush.fromSeed(Path.of("backend/config/demo.properties"));
        var customer = app.login("customer", "customer-demo");
        OrderService.View small = (OrderService.View) app.execute(Command.place(customer.token(),
                "sugar-shop", Map.of("cake", 1)));
        check(small.total(), "14.50");
        OrderService.View standard = (OrderService.View) app.execute(Command.place(customer.token(),
                "sugar-shop", Map.of("cake", 2)));
        check(standard.total(), "21.00");

        Properties missing = new Properties();
        for (String name : values.stringPropertyNames())
            if (name.startsWith("pricing.") && !name.equals("pricing.serviceFee"))
                missing.setProperty(name, values.getProperty(name));
        try {
            new PricingService(missing);
            throw new AssertionError("Missing pricing value accepted");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Passed pricing strategy and order fare checks.");
    }

    private static void check(BigDecimal actual, String expected) {
        if (actual.compareTo(new BigDecimal(expected)) != 0)
            throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
