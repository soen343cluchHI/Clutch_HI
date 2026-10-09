package sugarrush;

import java.util.Map;

public final class AuthorizationHandler extends CommandHandler {
    private static final Map<String, String> ACTIONS = Map.of(
        "Customer", "customer-demo", "Restaurant", "restaurant-demo",
        "Courier", "courier-demo", "Operator", "operator-demo");
    public AuthorizationHandler(CommandHandler next) { super(next); }
    public String handle(Command command, AuthenticatedUser user) {
        if (user == null || user.role() == null || command.action() == null ||
            !command.action().equals(ACTIONS.get(user.role())))
            throw new SecurityException("Action not permitted");
        return forward(command, user);
    }
}
