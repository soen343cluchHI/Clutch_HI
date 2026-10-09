package sugarrush;

// Must be created by a trusted backend verifier, never deserialized from client input.
public record AuthenticatedUser(String uid, String role) {}
