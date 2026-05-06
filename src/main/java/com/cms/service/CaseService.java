package com.cms.service;

import com.cms.model.CaseFile;
import com.cms.model.User;
import com.cms.repository.CaseRepository;

import java.util.List;

public class CaseService {

    public List<CaseFile> findAllCases() {
        return HibernateUtil.executeTransaction(session -> {
            List<CaseFile> cases = session.createQuery(
                "SELECT DISTINCT cf FROM CaseFile cf " +
                "LEFT JOIN FETCH cf.incident i " +
                "LEFT JOIN FETCH i.crimeType " +
                "LEFT JOIN FETCH cf.primaryInvestigator pi " +
                "LEFT JOIN FETCH pi.person " +
                "ORDER BY cf.id DESC", CaseFile.class)
                .setMaxResults(500).list();
            // Force-initialize lazy collections while session is open
            cases.forEach(cf -> {
                try { if (cf.getCaseSuspects() != null) cf.getCaseSuspects().size(); } catch (Exception ignore) {}
                try { if (cf.getCaseVictims() != null) cf.getCaseVictims().size(); } catch (Exception ignore) {}
                try { if (cf.getCaseWitnesses() != null) cf.getCaseWitnesses().size(); } catch (Exception ignore) {}
            });
            return cases;
        });
    }

    /** Fetch a single case with ALL relationships eagerly loaded for the detail view. */
    public CaseFile findCaseDetailById(Long caseId) {
        return HibernateUtil.executeTransaction(session -> {
            CaseFile cf = session.createQuery(
                "SELECT cf FROM CaseFile cf " +
                "LEFT JOIN FETCH cf.incident i " +
                "LEFT JOIN FETCH i.crimeType " +
                "LEFT JOIN FETCH i.reportingOfficer ro " +
                "LEFT JOIN FETCH ro.person " +
                "LEFT JOIN FETCH cf.primaryInvestigator pi " +
                "LEFT JOIN FETCH pi.person " +
                "WHERE cf.id = :id", CaseFile.class)
                .setParameter("id", caseId)
                .uniqueResult();
            if (cf != null) {
                // Force-initialize all lazy collections while session is open
                if (cf.getCaseSuspects() != null) cf.getCaseSuspects().forEach(cs -> {
                    if (cs.getPerson() != null) cs.getPerson().getFirstName();
                });
                if (cf.getCaseVictims() != null) cf.getCaseVictims().forEach(cv -> {
                    if (cv.getPerson() != null) cv.getPerson().getFirstName();
                });
                if (cf.getCaseWitnesses() != null) cf.getCaseWitnesses().forEach(cw -> {
                    if (cw.getPerson() != null) cw.getPerson().getFirstName();
                });
            }
            return cf;
        });
    }

    public List<CaseFile> searchCases(String keyword) {
        return HibernateUtil.executeTransaction(session ->
            new CaseRepository(session).search(keyword == null ? "" : keyword, 100, 0));
    }

    public List<CaseFile> getCasesByInvestigator(Long investigatorId) {
        return HibernateUtil.executeTransaction(session ->
            new CaseRepository(session).findByInvestigator(investigatorId, 100, 0));
    }

    public long countCasesByInvestigator(Long investigatorId) {
        if (investigatorId == null) return 0;
        return HibernateUtil.executeTransaction(session ->
            new CaseRepository(session).countByInvestigator(investigatorId));
    }

    public void createNewCaseFromIncident(Long incidentId, com.cms.model.User currentUser) {
        // Persist the case first
        CaseFile[] created = {null};
        HibernateUtil.executeTransaction(session -> {
            com.cms.repository.IncidentRepository ir = new com.cms.repository.IncidentRepository(session);
            var opt = ir.findById(incidentId);
            if (opt.isEmpty()) throw new IllegalArgumentException("No incident found: " + incidentId);

            // Re-attach currentUser
            com.cms.model.User managedUser = currentUser;
            if (currentUser != null && currentUser.getId() != null) {
                com.cms.model.User u = session.get(com.cms.model.User.class, currentUser.getId());
                if (u != null) managedUser = u;
            }

            CaseFile newCase = new CaseFile("CASE-" + System.currentTimeMillis(), opt.get());
            newCase.setPrimaryInvestigator(managedUser);
            newCase.setStatus(com.cms.model.enums.CaseStatus.OPEN);
            session.persist(newCase);
            created[0] = newCase;
            return null;
        });

        // Notifications AFTER transaction
        if (created[0] != null) {
            try { AuditService.getInstance().log(
                com.cms.model.enums.AuditAction.CREATE, "CaseFile", created[0].getId(),
                "Created case " + created[0].getCaseNumber());
            } catch (Exception ignored) {}
            try { NotificationService.getInstance().createNotification(
                "New Case Created", "Case " + created[0].getCaseNumber() + " created.",
                com.cms.model.enums.NotificationType.CASE,
                com.cms.model.enums.NotificationPriority.IMPORTANT);
            } catch (Exception ignored) {}
        }
    }

    public void updateCaseStatus(Long caseId, com.cms.model.enums.CaseStatus newStatus, String closureReason) {
        com.cms.model.enums.CaseStatus[] oldStatus = {null};
        HibernateUtil.executeTransaction(session -> {
            var opt = new CaseRepository(session).findById(caseId);
            if (opt.isPresent()) {
                CaseFile cf = opt.get();
                oldStatus[0] = cf.getStatus();
                if (newStatus == com.cms.model.enums.CaseStatus.CLOSED_CONVICTED || 
                    newStatus == com.cms.model.enums.CaseStatus.CLOSED_ACQUITTED || 
                    newStatus == com.cms.model.enums.CaseStatus.CLOSED_UNSOLVED)
                    cf.closeCase(newStatus, closureReason == null ? "Closed" : closureReason);
                else
                    cf.setStatus(newStatus);
                session.merge(cf);
            }
            return null;
        });
        try { AuditService.getInstance().log(
            com.cms.model.enums.AuditAction.UPDATE, "CaseFile", caseId,
            "Status changed from " + oldStatus[0] + " to " + newStatus);
        } catch (Exception ignored) {}
    }

    public void addPersonToCase(Long caseId, Long personId, com.cms.model.enums.PersonStatus role) {
        HibernateUtil.executeTransaction(session -> {
            var caseOpt   = new CaseRepository(session).findById(caseId);
            var personOpt = new com.cms.repository.PersonRepository(session).findById(personId);
            if (caseOpt.isPresent() && personOpt.isPresent()) {
                CaseFile cf = caseOpt.get();
                com.cms.model.Person p = personOpt.get();
                p.setPersonStatus(role);
                switch (role) {
                    case SUSPECT -> cf.addSuspect(p);
                    case VICTIM  -> cf.addVictim(p);
                    case WITNESS -> cf.addWitness(p);
                    default      -> {}
                }
                session.merge(cf);
                session.merge(p);
            }
            return null;
        });
    }

    public List<User> findAvailableInvestigators(int limit) {
        return HibernateUtil.executeTransaction(session ->
                new CaseRepository(session).findAvailableInvestigators(limit));
    }

    public void assignInvestigatorAndStart(Long caseId, Long officerId) {
        String[] caseNumber = {null};
        String[] officerName = {null};
        HibernateUtil.executeTransaction(session -> {
            var caseOpt = new CaseRepository(session).findById(caseId);
            if (caseOpt.isEmpty()) {
                throw new IllegalArgumentException("Case not found: " + caseId);
            }
            User officer = session.get(User.class, officerId);
            if (officer == null) {
                throw new IllegalArgumentException("Officer not found: " + officerId);
            }

            CaseFile cf = caseOpt.get();
            cf.setPrimaryInvestigator(officer);
            cf.setStatus(com.cms.model.enums.CaseStatus.UNDER_INVESTIGATION);
            session.merge(cf);

            // Also update the linked incident status to reflect the investigation
            if (cf.getIncident() != null) {
                cf.getIncident().setStatus(com.cms.model.enums.IncidentStatus.UNDER_INVESTIGATION);
                session.merge(cf.getIncident());
            }

            caseNumber[0] = cf.getCaseNumber();
            officerName[0] = officer.getFullName();
            return null;
        });

        // Post-transaction: audit and notification
        try { AuditService.getInstance().log(
            com.cms.model.enums.AuditAction.UPDATE, "CaseFile", caseId,
            "Investigation started. Assigned to: " + officerName[0]);
        } catch (Exception ignored) {}
        try { NotificationService.getInstance().createNotification(
            "Investigation Started", "Case " + caseNumber[0] + " assigned to " + officerName[0],
            com.cms.model.enums.NotificationType.CASE,
            com.cms.model.enums.NotificationPriority.IMPORTANT);
        } catch (Exception ignored) {}
    }
}
