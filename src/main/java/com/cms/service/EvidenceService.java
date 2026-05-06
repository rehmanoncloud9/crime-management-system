package com.cms.service;

import com.cms.model.CaseFile;
import com.cms.model.Evidence;
import com.cms.repository.EvidenceRepository;
import java.util.List;
import java.util.Optional;

public class EvidenceService {

    public List<Evidence> findAll(int limit, int offset) {
        return HibernateUtil.executeTransaction(session ->
            session.createQuery(
                "SELECT e FROM Evidence e LEFT JOIN FETCH e.caseFile ORDER BY e.id DESC",
                Evidence.class)
            .setMaxResults(limit).setFirstResult(offset).list()
        );
    }

    public List<Evidence> findByCase(Long caseId, int limit, int offset) {
        return HibernateUtil.executeTransaction(session ->
            new EvidenceRepository(session).findByCase(caseId, limit, offset));
    }

    public Optional<Evidence> findById(Long id) {
        return HibernateUtil.<Optional<Evidence>>executeTransaction(session ->
            new EvidenceRepository(session).findById(id));
    }

    public void save(Evidence evidence) {
        boolean isNew = evidence.getId() == null;

        HibernateUtil.executeTransaction(session -> {
            // Re-attach all associated entities to THIS session
            if (evidence.getCaseFile() != null && evidence.getCaseFile().getId() != null) {
                CaseFile managed = session.get(CaseFile.class, evidence.getCaseFile().getId());
                if (managed != null) evidence.setCaseFile(managed);
            }
            if (evidence.getSuspect() != null && evidence.getSuspect().getId() != null) {
                com.cms.model.Person mp = session.get(com.cms.model.Person.class, evidence.getSuspect().getId());
                if (mp != null) evidence.setSuspect(mp);
            }
            if (evidence.getCollectedBy() != null && evidence.getCollectedBy().getId() != null) {
                com.cms.model.User mu = session.get(com.cms.model.User.class, evidence.getCollectedBy().getId());
                if (mu != null) evidence.setCollectedBy(mu);
            }
            if (isNew) session.persist(evidence);
            else       session.merge(evidence);
            return null;
        });

        // Notification after transaction
        try {
            if (isNew) {
                NotificationService.getInstance().createNotification(
                    "Evidence Logged",
                    "New evidence (" + evidence.getType() + ") recorded for case "
                        + (evidence.getCaseFile() != null ? evidence.getCaseFile().getCaseNumber() : ""),
                    com.cms.model.enums.NotificationType.EVIDENCE,
                    com.cms.model.enums.NotificationPriority.INFO
                );
            }
            
            AuditService.getInstance().log(
                isNew ? com.cms.model.enums.AuditAction.CREATE : com.cms.model.enums.AuditAction.UPDATE,
                "Evidence",
                evidence.getId(),
                (isNew ? "Created" : "Updated") + " evidence: " + evidence.getType()
            );
        } catch (Exception ignored) {}
    }
}
