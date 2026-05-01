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
        logAction(currentUser, action, entityType, entityId, description);
    }

    public void logAction(User actor, AuditAction action, String entityType, Long entityId, String description) {
        try {
            HibernateUtil.executeVoidTransaction(session -> {
                User managedActor = null;
                String actorName = "SYSTEM";

                if (actor != null && actor.getId() != null) {
                    managedActor = session.get(User.class, actor.getId());
                    actorName = managedActor != null ? managedActor.getFullName() : "UNKNOWN";
                }

                AuditLog log = new AuditLog();
                log.setUser(managedActor); // Can be null for system actions
                log.setUserName(actorName);
                log.setAction(action);
                log.setEntityType(entityType);
                log.setEntityId(entityId);
                log.setDescription(description);
                log.setIpAddress("127.0.0.1");
                log.setWorkstationId(System.getProperty("user.name", "workstation"));
                session.persist(log);
            });
        } catch (Exception e) {
            logger.warn("Audit logging failed (non-critical): {}", e.getMessage());
        }
    }

    public void logAction(User actor, AuditAction action, String description) {
        logAction(actor, action, null, null, description);
    }

    // Backwards-compatible overload
    public void logAction(User actor, String actionName, String description) {
        AuditAction action;
        try { action = AuditAction.valueOf(actionName); }
        catch (Exception e) { action = AuditAction.UPDATE; }
        logAction(actor, action, description);
    }
}
