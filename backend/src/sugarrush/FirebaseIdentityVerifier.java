package sugarrush;

public interface FirebaseIdentityVerifier {
    // Future adapter: verify the Firebase ID token, then load a server-approved role.
    // Self-selected profile roles are NOT authority for privileged commands.
    AuthenticatedUser verify(String idToken);
}
