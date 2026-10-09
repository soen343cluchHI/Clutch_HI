package sugarrush;

public final class CommandProcessor {
    private final CommandHandler first;
    public CommandProcessor() {
        this(token -> {
            throw new SecurityException("Firebase Admin token verification is not configured");
        });
    }
    // Package-private injection is for the local pattern demo, not a client-facing auth mechanism.
    CommandProcessor(FirebaseIdentityVerifier verifier) {
        first = new AuthenticationHandler(verifier,
            new AuthorizationHandler(new ExecutionHandler()));
    }
    public String process(Command command) { return first.handle(command, null); }
}
