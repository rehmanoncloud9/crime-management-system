package com.cms.service;

import com.cms.model.User;
import com.cms.model.enums.UserStatus;
import com.cms.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    public List<User> searchUsers(String keyword) {
        return HibernateUtil.executeTransaction(session -> {
            UserRepository repo = new UserRepository(session);
            return repo.search(keyword == null ? "" : keyword, 100, 0);
        });
    }

    public Optional<User> findById(Long id) {
        return HibernateUtil.<Optional<User>>executeTransaction(session -> {
            User user = session.find(User.class, id);
            return Optional.ofNullable(user);
        });
    }

    public void saveUser(User user) {
        HibernateUtil.executeVoidTransaction(session -> {
            if (user.getId() == null) {
                session.persist(user);
            } else {
                session.merge(user);
            }
        });
    }

    public void updateUser(User detachedUser) {
        HibernateUtil.executeVoidTransaction(session -> {
            session.merge(detachedUser);
        });
    }

    public void toggleUserStatus(Long userId, User actor) {
        HibernateUtil.executeVoidTransaction(session -> {
            User managed = session.find(User.class, userId);
            if (managed == null) return;
            
            UserStatus oldStatus = managed.getStatus();
            managed.setStatus(oldStatus == UserStatus.ACTIVE ? UserStatus.SUSPENDED : UserStatus.ACTIVE);
            
            AuditService.getInstance().logAction(
                actor, "USER_STATUS_CHANGE", "User: " + managed.getUsername() + " from " + oldStatus + " to " + managed.getStatus()
            );
        });
    }

    public void deleteUser(Long userId, User actor) {
        HibernateUtil.executeVoidTransaction(session -> {
            User managed = session.find(User.class, userId);
            if (managed != null) {
                String username = managed.getUsername();
                session.remove(managed);
                AuditService.getInstance().logAction(actor, "USER_DELETE", "Deleted officer: " + username);
            }
        });
    }

    /**
     * Clears the must_change_password flag after a user successfully sets their own password.
     * Called by ChangePasswordController after a successful password change.
     */
    public void clearMustChangePassword(Long userId) {
        HibernateUtil.executeVoidTransaction(session -> {
            User managed = session.find(User.class, userId);
            if (managed != null) {
                managed.setMustChangePassword(false);
                session.merge(managed);
                logger.info("Cleared must_change_password for user: {}", managed.getUsername());
            }
        });
    }

    /**
     * Admin-initiated password reset. Sets must_change_password = true so the
     * officer is forced to choose their own password on next login.
     */
    public void resetPassword(Long userId, String newPassword) {
        HibernateUtil.executeVoidTransaction(session -> {
            User managed = session.find(User.class, userId);
            if (managed != null) {
                String hashed = org.mindrot.jbcrypt.BCrypt.hashpw(newPassword, org.mindrot.jbcrypt.BCrypt.gensalt());
                managed.setPasswordHash(hashed);
                managed.setMustChangePassword(true); // Force change on next login
                session.merge(managed);
                logger.info("Password reset by admin for user: {}", managed.getUsername());
            }
        });
    }
}
