package sugarrush.infrastructure.auth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import sugarrush.infrastructure.json.Json;

public final class FirebaseTokenVerifier {
    public record Identity(String uid, String email) {}

    @FunctionalInterface
    public interface KeySource {
        Map<String, PublicKey> keys() throws IOException;
    }

    public static final class UnavailableException extends RuntimeException {
        public UnavailableException(Throwable cause) {
            super("Cannot verify login right now. Java needs internet access to Google's Firebase certificates.",
                    cause);
        }
    }

    private final String projectId;
    private final KeySource keySource;
    private final Clock clock;

    public FirebaseTokenVerifier(String projectId) {
        this(projectId, new GoogleKeys(), Clock.systemUTC());
    }

    public FirebaseTokenVerifier(String projectId, KeySource keySource, Clock clock) {
        if (projectId == null || projectId.isBlank())
            throw new IllegalArgumentException("Firebase project ID required");
        this.projectId = projectId;
        this.keySource = Objects.requireNonNull(keySource);
        this.clock = Objects.requireNonNull(clock);
    }

    public Identity verify(String token) {
        if (token == null || token.length() > 16384)
            throw invalid();
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3)
                throw invalid();

            Map<String, Object> header = Json.object(decode(parts[0]));
            if (!"RS256".equals(header.get("alg")) || !(header.get("kid") instanceof String kid) || kid.isBlank())
                throw invalid();

            PublicKey key = keySource.keys().get(kid);
            if (key == null)
                throw invalid();

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2])))
                throw invalid();

            Map<String, Object> claims = Json.object(decode(parts[1]));
            
            long now = clock.instant().getEpochSecond();

            long expiry = number(claims, "exp"), issued = number(claims, "iat"),
                    authenticated = number(claims, "auth_time");

            if (!projectId.equals(claims.get("aud"))
                    || !("https://securetoken.google.com/" + projectId).equals(claims.get("iss")) || expiry <= now
                    || issued > now || issued < 0 || authenticated > now || authenticated < 0 || expiry <= issued)
                throw invalid();

            if (!(claims.get("sub") instanceof String uid) || uid.isBlank() || uid.length() > 128)
                throw invalid();

            if (claims.containsKey("user_id") && !uid.equals(claims.get("user_id")))
                throw invalid();

            if (!Boolean.TRUE.equals(claims.get("email_verified")))
                throw new SecurityException("Verify your email before using SugarRush.");

            String email = claims.get("email") instanceof String text && !text.isBlank() ? text : uid;
            return new Identity(uid, email);

        } catch (IOException failure) {
            throw new UnavailableException(failure);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw invalid();
        }
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    private static long number(Map<String, Object> claims, String name) {
        if (!(claims.get(name) instanceof BigDecimal number))
            throw invalid();
        try {
            return number.longValueExact();
        } catch (ArithmeticException failure) {
            throw invalid();
        }
    }

    private static SecurityException invalid() {
        return new SecurityException("Your login could not be verified. Sign in again.");
    }

    private static final class GoogleKeys implements KeySource {
        private static final URI URL = URI
                .create("https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com");
        private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        private Map<String, PublicKey> cached = Map.of();
        private Instant validUntil = Instant.EPOCH;

        @Override
        public synchronized Map<String, PublicKey> keys() throws IOException {
            if (Instant.now().isBefore(validUntil) && !cached.isEmpty())
                return cached;
            try {
                HttpResponse<String> response = http.send(
                        HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200 || response.body().length() > 1048576)
                    throw new IOException("Certificate service unavailable");
                Map<String, PublicKey> loaded = new HashMap<>();
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                for (var entry : Json.object(response.body()).entrySet()) {
                    if (!(entry.getValue() instanceof String pem))
                        throw new IOException("Invalid certificate response");
                    byte[] bytes = pem.getBytes(StandardCharsets.US_ASCII);
                    java.security.cert.X509Certificate certificate = (java.security.cert.X509Certificate) factory
                            .generateCertificate(new ByteArrayInputStream(bytes));
                    certificate.checkValidity();
                    loaded.put(entry.getKey(), certificate.getPublicKey());
                }
                if (loaded.isEmpty())
                    throw new IOException("No Firebase public keys");
                Matcher age = Pattern.compile("(?:^|[,\\s])max-age=(\\d+)")
                        .matcher(response.headers().firstValue("cache-control").orElse(""));
                long seconds = age.find() ? Long.parseLong(age.group(1)) : 300;
                cached = Map.copyOf(loaded);
                validUntil = Instant.now().plusSeconds(Math.min(seconds, 86400));
                return cached;
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IOException("Verification interrupted", failure);
            } catch (GeneralSecurityException | IllegalArgumentException failure) {
                throw new IOException("Invalid certificate response", failure);
            }
        }
    }

}
