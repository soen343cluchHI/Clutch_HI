package sugarrush;

public abstract class CommandHandler {
    private final CommandHandler next;
    protected CommandHandler(CommandHandler next) { this.next = next; }
    public abstract String handle(Command command, AuthenticatedUser user);
    protected String forward(Command command, AuthenticatedUser user) {
        if (next == null) throw new IllegalStateException("Missing execution handler");
        return next.handle(command, user);
    }
}
