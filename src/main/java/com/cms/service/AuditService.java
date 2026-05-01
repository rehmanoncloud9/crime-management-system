package com.cms.service;

import com.cms.model.AuditLog;
import com.cms.model.User;
import com.cms.model.enums.AuditAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditService {
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static AuditService instance;

    private AuditService() {}

    public static synchronized AuditService getInstance() {
        if (instance == null) instance = new AuditService();
        return instance;
    }

    public void log(AuditAction action, String entityType, Long entityId, String description) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return;
        logAction(currentUser, action, description);
    }

    public void logAction(User actor, AuditAction action, String description) {
        if (actor == null) return;
        try {
            HibernateUtil.executeVoidTransaction(session -> {
                // Re-attach user to this session
                User managedActor = actor;
                if (actor.getId() != null) {
                    User found = session.get(User.class, actor.getId());
                    if (found != null) managedActor = found;
                }
                AuditLog log = new AuditLog(managedActor, managedActor.getFullName(), action);
                log.setEntityType(action.name());
                log.setDescription(description);
                log.setIpAddress("127.0.0.1");
                log.setWorkstationId(System.getProperty("user.name", "workstation"));
                session.persist(log);
            });
        } catch (Exception e) {
            logger.warn("Audit logging failed (non-critical): {}", e.getMessage());
        }
    }

    // Backwards-compatible overload
    public void logAction(User actor, String actionName, String description) {
        AuditAction action;
        try { action = AuditAction.valueOf(actionName); }
        catch (Exception e) { action = AuditAction.UPDATE; }
        logAction(actor, action, description);
    }
}
