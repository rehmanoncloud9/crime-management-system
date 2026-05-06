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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.Optional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final Properties APP_CONFIG = loadAppConfig();

    private boolean sessionEnabled = true;

    public AuthService() {
    }

    public void setSessionEnabled(boolean sessionEnabled) {
        this.sessionEnabled = sessionEnabled;
    }

    public String hashPassword(String plainText) {
        return BCrypt.hashpw(plainText, BCrypt.gensalt());
    }

    /**
     * Verifies a plain-text password against a stored hash.
     * Used by the change-password screen to confirm the user knows their current password.
     */
    public boolean verifyCurrentPassword(String plainText, String storedHash) {
        if (plainText == null || storedHash == null || storedHash.isBlank()) return false;
        try {
            if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
                return BCrypt.checkpw(plainText, storedHash);
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] a = digest.digest(storedHash.getBytes(StandardCharsets.UTF_8));
            byte[] b = digest.digest(plainText.getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(a, b);
        } catch (Exception e) {
            logger.warn("Password verification error", e);
            return false;
        }
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
        if (user.getLockedUntil() != null && user.getLockedUntil().isBefore(LocalDateTime.now())) {
            boolean wasLocked = user.getStatus() == UserStatus.LOCKED
                    || user.getFailedLoginAttempts() >= getLockoutAttempts();
            if (user.getStatus() == UserStatus.LOCKED) {
                user.setStatus(UserStatus.ACTIVE);
            }
            user.setLockedUntil(null);
            if (wasLocked) {
                user.setFailedLoginAttempts(0);
            }
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

        int maxAttempts = getLockoutAttempts();
        int lockMinutes = getLockoutMinutes();

        if (attempts >= maxAttempts) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            user.setStatus(UserStatus.LOCKED);
            logger.info("User {} locked due to failed attempts", user.getUsername());
        }

        userRepository.update(user);
    }

    private int getLockoutAttempts() {
        return parsePositiveInt(APP_CONFIG.getProperty("app.password.lockout.attempts"), 5);
    }

    private int getLockoutMinutes() {
        return parsePositiveInt(APP_CONFIG.getProperty("app.password.lockout.minutes"), 30);
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static Properties loadAppConfig() {
        Properties props = new Properties();
        Logger bootstrapLogger = LoggerFactory.getLogger(AuthService.class);
        try (InputStream in = AuthService.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                bootstrapLogger.warn("config.properties not found; using defaults for auth config.");
            }
        } catch (Exception e) {
            bootstrapLogger.warn("Failed to load config.properties for auth config: {}", e.getMessage());
        }
        return props;
    }

    protected void createSession(User user, Session session) {
        try {
            LoginSession loginSession = new LoginSession();
            loginSession.setUser(user);
            loginSession.setLoginAt(LocalDateTime.now());
            loginSession.setSessionStatus(SessionStatus.ACTIVE);
            loginSession.setWorkstationId(System.getProperty("user.name"));
            // IP address is not reliably available in a desktop app — record workstation name instead
            loginSession.setIpAddress("desktop-client");

            session.persist(loginSession);
            SessionManager.getInstance().setCurrentSession(loginSession);
        } catch (Exception e) {
            logger.error("Session creation failed for user: {}", user.getUsername(), e);
            throw e;
        }
    }
    
    public void logout(LoginSession loginSession) {
        if (loginSession == null) return;

        try {
            HibernateUtil.executeVoidTransaction(session -> {
                LoginSession managedSession = session.get(LoginSession.class, loginSession.getId());
                if (managedSession != null && managedSession.isActive()) {
                    managedSession.logout();
                    session.merge(managedSession);
                    
                    logger.info("Session {} for user {} terminated in database.", 
                        managedSession.getId(), managedSession.getUser().getUsername());

                    try {
                        AuditService.getInstance().logAction(
                            managedSession.getUser(),
                            AuditAction.LOGOUT,
                            "User logged out successfully."
                        );
                    } catch (Exception e) {
                        logger.warn("Audit logging for logout failed: {}", e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            logger.error("Database logout failed for session {}", loginSession.getId(), e);
        }
    }
}
