package sugarrush;

public final class ExecutionHandler extends CommandHandler {
    public ExecutionHandler() { super(null); }
    public String handle(Command command, AuthenticatedUser user) {
        return "Executed placeholder: " + command.action() + " for " + user.uid();
    }
}
