package sugarrush.application.commands;

import sugarrush.application.auth.AuthenticationService;
import sugarrush.domain.user.User;

public final class AuthenticationHandler extends CommandHandler {
    private final AuthenticationService auth;

    public AuthenticationHandler(AuthenticationService auth, CommandHandler next) {
        super(next);
        this.auth = auth;
    }

    @Override
    public Object handle(Command command, User ignored) {
        if (command == null || command.sessionToken() == null || command.sessionToken().isBlank())
            throw new SecurityException("Session token required");
        return forward(command, auth.verify(command.sessionToken()));
    }
}
