package com.cms.service;

import com.cms.model.LoginSession;
import com.cms.model.User;
import com.cms.model.enums.AuditAction;
import com.cms.model.enums.Role;
import com.cms.model.enums.SessionStatus;
import com.cms.model.enums.UserStatus;
import com.cms.repository.UserRepository;
import org.hibernate.Session;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private boolean sessionEnabled = true;

    public AuthService() {
    }

    public void setSessionEnabled(boolean sessionEnabled) {
        this.sessionEnabled = sessionEnabled;
    }

    public String hashPassword(String plainText) {
        return BCrypt.hashpw(plainText, BCrypt.gensalt());
    }

    public User authenticate(String identifier, String password) throws Exception {
        if (identifier == null || password == null) {
            throw new IllegalArgumentException("Username/Email and password are required.");
        }

        User user = HibernateUtil.executeTransaction(session -> {
            UserRepository userRepository = new UserRepository(session);
            logger.info("[AUTH-DIAG] Searching for user: {}", identifier);
            return authenticateInternal(identifier, password, userRepository, session);
        });

        // Log SUCCESSful login outside the transaction to avoid session nesting issues
        if (user != null) {
            logger.info("[AUTH-DIAG] Success for user: {}", identifier);
            try {
                AuditService.getInstance().log(
                    AuditAction.LOGIN,
                    "User",
                    user.getId(),
                    "User " + identifier + " logged in successfully."
                );
            } catch (Exception e) {
                logger.warn("Audit logging failed but login succeeded: {}", e.getMessage());
            }
        }

        return user;
    }

    User authenticateInternal(String identifier, String password, UserRepository userRepository, org.hibernate.Session session) {
        // Try to find by email first, then by username
        Optional<User> userOpt = userRepository.findByEmail(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByUsername(identifier);
        }
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByBadgeNumber(identifier);
        }

        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found: " + identifier);
        }

        User user = userOpt.get();

        normalizeLockState(user, userRepository);

        if (isLocked(user)) {
            logger.warn("Locked user login attempt: {}", identifier);
            throw new IllegalStateException("Account disabled");
        }

        if (!isActive(user)) {
            logger.warn("Inactive user login attempt: {}", identifier);
            throw new IllegalStateException("Account disabled");
        }

        PasswordCheckResult result = verifyPassword(password, user.getPasswordHash());
        if (result.matched()) {
            if (result.requiresRehash()) {
                user.setPasswordHash(hashPassword(password));
                userRepository.update(user);
            }
            resetLoginState(user, userRepository);

            if (sessionEnabled) {
                createSession(user, session);
            }

            return user;
        } else {
            handleFailedLogin(user, userRepository);
            
            // Log FAILED login (we can do this inside as it opens its own session but usually safe)
            // Or better, let the exception handle it
            throw new IllegalArgumentException("Invalid password for user: " + identifier);
        }
    }

    private boolean isLocked(User user) {
        return user.getLockedUntil() != null &&
                user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void normalizeLockState(User user, UserRepository userRepository) {
        if (user.getStatus() == UserStatus.LOCKED && user.getLockedUntil() != null
                && user.getLockedUntil().isBefore(LocalDateTime.now())) {
            user.setStatus(UserStatus.ACTIVE);
            user.setLockedUntil(null);
            userRepository.update(user);
        }
    }

    private boolean isActive(User user) {
        return user.getStatus() == UserStatus.ACTIVE;
    }

    private PasswordCheckResult verifyPassword(String rawPassword, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return PasswordCheckResult.noMatch();
        }

        if (isBcryptHash(storedHash)) {
            try {
                return BCrypt.checkpw(rawPassword, storedHash)
                        ? PasswordCheckResult.match(false)
                        : PasswordCheckResult.noMatch();
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid stored hash format");
                return PasswordCheckResult.noMatch();
            }
        }

        return constantTimeEquals(storedHash, rawPassword)
                ? PasswordCheckResult.match(true)
                : PasswordCheckResult.noMatch();
    }

    private boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private boolean constantTimeEquals(String a, String b) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] aHash = digest.digest(a.getBytes(StandardCharsets.UTF_8));
            byte[] bHash = digest.digest(b.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(aHash, bHash);
        } catch (NoSuchAlgorithmException e) {
            return a.equals(b);
        }
    }

    private record PasswordCheckResult(boolean matched, boolean requiresRehash) {
        static PasswordCheckResult match(boolean needsRehash) {
            return new PasswordCheckResult(true, needsRehash);
        }

        static PasswordCheckResult noMatch() {
            return new PasswordCheckResult(false, false);
        }
    }

    private void resetLoginState(User user, UserRepository userRepository) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.update(user);
    }

    protected void handleFailedLogin(User user, UserRepository userRepository) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= 5) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            logger.info("User {} locked due to failed attempts", user.getUsername());
        }

        userRepository.update(user);
    }

    protected void createSession(User user, Session session) {
        try {
            LoginSession loginSession = new LoginSession();
            loginSession.setUser(user);
            loginSession.setLoginAt(LocalDateTime.now());
            loginSession.setSessionStatus(SessionStatus.ACTIVE);
            loginSession.setWorkstationId(System.getProperty("user.name"));
            loginSession.setIpAddress("127.0.0.1");

            session.persist(loginSession);
            SessionManager.getInstance().setCurrentSession(loginSession);
        } catch (Exception e) {
            logger.error("Session creation failed for user: {}", user.getUsername(), e);
            throw e;
        }
    }
}
