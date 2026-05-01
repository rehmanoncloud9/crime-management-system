package com.cms.service;

import com.cms.model.ArrestRecord;
import com.cms.model.CaseFile;
import com.cms.model.Person;
import com.cms.model.User;
import com.cms.model.enums.IncidentStatus;
import com.cms.model.enums.PersonStatus;
import com.cms.repository.ArrestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Service for managing arrest records.
 * Handles arrest registration, automatic case closure when a single-suspect case
 * has an arrest, and performance tracking.
 */
public class ArrestService {
    private static final Logger logger = LoggerFactory.getLogger(ArrestService.class);

    /**
     * Register a new arrest and persist it.
     * If the linked case has only one suspect and this arrest is made,
     * the case is automatically closed.
     */
    public ArrestRecord registerArrest(Long caseId, Long suspectId, Long officerId,
                                       String charges, String arrestLocation,
                                       String custodyLocation, String bookingReference) {
        if (caseId == null || suspectId == null || officerId == null) {
            throw new IllegalArgumentException("caseId, suspectId, and officerId are required");
        }
        if (bookingReference == null || bookingReference.isBlank()) {
            throw new IllegalArgumentException("bookingReference is required");
        }

        ArrestRecord[] created = {null};

        HibernateUtil.executeTransaction(session -> {
            // Resolve managed entities
            CaseFile cf = session.get(CaseFile.class, caseId);
            if (cf == null) throw new IllegalArgumentException("Case not found: " + caseId);

            Person suspect = session.get(Person.class, suspectId);
            if (suspect == null) throw new IllegalArgumentException("Suspect not found: " + suspectId);

            User officer = session.get(User.class, officerId);
            if (officer == null) throw new IllegalArgumentException("Officer not found: " + officerId);

            // Create and populate the arrest record
            ArrestRecord ar = new ArrestRecord(suspect, officer);
            ar.setCaseFile(cf);
            if (charges != null && !charges.isBlank()) {
                for (String c : charges.split("[,\\n]")) {
                    if (!c.trim().isEmpty()) ar.addCharge(c.trim());
                }
            }
            ar.setArrestLocation(arrestLocation);
            ar.setCustodyLocation(custodyLocation);
            ar.setBookingReference(bookingReference.trim());

            // Prevent inconsistent workflow: suspect should be linked to the case before arrest entry
            if (!cf.getSuspects().contains(suspect)) {
                throw new IllegalStateException("Selected suspect is not linked to the selected case");
            }

            // Mark suspect as arrested
            suspect.setPersonStatus(PersonStatus.SUSPECT);

            session.persist(ar);
            session.merge(suspect);

            // AUTO-CLOSE: If the case has only 1 suspect, close case immediately
            int suspectCount = cf.getSuspects().size();
            if (suspectCount <= 1) {
                cf.closeCase(com.cms.model.enums.CaseStatus.CLOSED_CONVICTED, "Auto-closed: Arrest made for sole suspect");
                logger.info("Auto-closed case {} after arrest of sole suspect", cf.getCaseNumber());
            } else {
                // If multiple suspects, just update status to ARRESTED if not already
                if (cf.getStatus() != com.cms.model.enums.CaseStatus.CLOSED_CONVICTED &&
                    cf.getStatus() != com.cms.model.enums.CaseStatus.CLOSED_ACQUITTED &&
                    cf.getStatus() != com.cms.model.enums.CaseStatus.CLOSED_UNSOLVED) {
                    cf.setStatus(com.cms.model.enums.CaseStatus.ARRESTED);
                }
            }
            session.merge(cf);

            created[0] = ar;
            return null;
        });

        // Post-transaction notifications
        if (created[0] != null) {
            try {
                AuditService.getInstance().log(
                    com.cms.model.enums.AuditAction.CREATE, "ArrestRecord", created[0].getId(),
                    "Arrest registered for case " + caseId);
            } catch (Exception ignored) {}
            try {
                NotificationService.getInstance().createNotification(
                    "Arrest Made", "A suspect has been arrested for Case ID " + caseId,
                    com.cms.model.enums.NotificationType.CASE,
                    com.cms.model.enums.NotificationPriority.IMPORTANT);
            } catch (Exception ignored) {}
        }

        return created[0];
    }

    /** Find all arrests across the system */
    public List<ArrestRecord> findAll(int limit, int offset) {
        return HibernateUtil.executeTransaction(session ->
            new ArrestRepository(session).findAll(limit, offset));
    }

    /** Find arrests for a specific case */
    public List<ArrestRecord> findByCase(Long caseId) {
        return HibernateUtil.executeTransaction(session ->
            new ArrestRepository(session).findByCase(caseId));
    }

    /** Count total arrests */
    public long countAll() {
        return HibernateUtil.executeTransaction(session -> new ArrestRepository(session).countAll());
    }

    public long countForCurrentMonth() {
        return HibernateUtil.executeTransaction(session -> {
            var start = java.time.LocalDate.now().withDayOfMonth(1).atStartOfDay();
            return new ArrestRepository(session).countSince(start);
        });
    }

    public long countAutoClosedCases() {
        return HibernateUtil.executeTransaction(session -> {
            Long autoClosed = session.createQuery(
                    "SELECT COUNT(cf) FROM CaseFile cf WHERE cf.closureReason LIKE :reason", Long.class)
                .setParameter("reason", "%Auto-closed%")
                .getSingleResult();
            return autoClosed == null ? 0L : autoClosed;
        });
    }
}
