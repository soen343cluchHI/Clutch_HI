package sugarrush;

public final class AuthenticationHandler extends CommandHandler {
    private final FirebaseIdentityVerifier verifier;
    public AuthenticationHandler(FirebaseIdentityVerifier verifier, CommandHandler next) {
        super(next);
        this.verifier = verifier;
    }
    public String handle(Command command, AuthenticatedUser ignored) {
        if (command == null || command.idToken() == null || command.idToken().isBlank())
            throw new SecurityException("Firebase ID token required");
        AuthenticatedUser verified = verifier.verify(command.idToken());
        if (verified == null || verified.uid() == null || verified.uid().isBlank())
            throw new SecurityException("Authentication failed");
        return forward(command, verified);
    }
}
