package sugarrush;

// A client supplies a Firebase ID token and an action, never a trusted UID or role.
public record Command(String idToken, String action) {}
