package sugarrush.application.commands;

import sugarrush.domain.user.User;

public abstract class CommandHandler {
    private final CommandHandler next;

    protected CommandHandler(CommandHandler next) {
        this.next = next;
    }

    public abstract Object handle(Command command, User user);

    protected Object forward(Command command, User user) {
        if (next == null)
            throw new IllegalStateException("Missing execution handler");
        return next.handle(command, user);
    }
}
