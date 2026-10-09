package sugarrush.application.auth;
import java.time.Instant;
import java.util.*;
import sugarrush.domain.user.*;
public final class AuthenticationService {
 private final RegistrationService registration;
 private final Map<String,Session> sessions=new HashMap<>();
 public AuthenticationService(RegistrationService registration){this.registration=registration;}
 public Session login(String username,String password){
  User user=registration.authenticate(username,password);
  Session session=new Session(UUID.randomUUID().toString(),user,Instant.now());
  sessions.put(session.token(),session);return session;
 }
 public User verify(String token){
  Session session=sessions.get(token);
  if(session==null) throw new SecurityException("Valid session token required");
  return session.user();
 }
 public void logout(String token){verify(token);sessions.remove(token);}
}
