package com.cms.service;

import com.cms.model.ArrestRecord;
import com.cms.model.CaseFile;
import com.cms.model.CrimeIncident;
import com.cms.model.Evidence;
import com.cms.model.Person;
import com.cms.model.User;
import com.cms.model.enums.ArrestStatus;
import com.cms.model.enums.CasePriority;
import com.cms.model.enums.EvidenceStatus;
import com.cms.model.enums.IncidentStatus;
import com.cms.model.enums.PersonStatus;
import com.cms.repository.CaseRepository;
import com.cms.repository.IncidentRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CaseWorkflowService {

    private static final Set<IncidentStatus> CASE_CLOSURE_STATES = Set.of(
            IncidentStatus.CLOSED_CONVICTED,
            IncidentStatus.CLOSED_ACQUITTED,
            IncidentStatus.CLOSED_UNSOLVED
    );

    public CrimeIncident verifyIncident(Long incidentId) {
        return HibernateUtil.executeTransaction(session -> {
            CrimeIncident incident = new IncidentRepository(session).findById(incidentId)
                    .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
            ensureTransition(incident.getStatus(), IncidentStatus.VERIFIED, allowedIncidentTransitions());
            incident.setStatus(IncidentStatus.VERIFIED);
            session.merge(incident);
            return incident;
        });
    }

    public CaseFile convertIncidentToCase(Long incidentId, Long leadOfficerId, CasePriority priority) {
        return HibernateUtil.executeTransaction(session -> {
            CrimeIncident incident = new IncidentRepository(session).findById(incidentId)
                    .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

            ensureTransition(incident.getStatus(), IncidentStatus.CONVERTED, allowedIncidentTransitions());

            Long existing = session.createQuery(
                    "SELECT COUNT(c) FROM CaseFile c WHERE c.incident.id = :incidentId", Long.class)
                .setParameter("incidentId", incidentId)
                .getSingleResult();
            if (existing != null && existing > 0) {
                throw new IllegalStateException("Incident already converted to case");
            }

            User lead = session.get(User.class, leadOfficerId);
            if (lead == null) {
                throw new IllegalArgumentException("Lead officer not found: " + leadOfficerId);
            }

            CaseFile caseFile = new CaseFile(nextCaseNumber(), incident);
            caseFile.setPriority(priority == null ? CasePriority.MEDIUM : priority);
            caseFile.setPrimaryInvestigator(lead);
            caseFile.setStatus(IncidentStatus.OPEN);
            session.persist(caseFile);

            incident.setStatus(IncidentStatus.CONVERTED);
            session.merge(incident);
            return caseFile;
        });
    }

    public void assignOfficers(Long caseId, Long leadOfficerId, List<Long> supportingOfficerIds, Long assignedById) {
        HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
            User lead = session.get(User.class, leadOfficerId);
            if (lead == null) {
                throw new IllegalArgumentException("Lead officer not found: " + leadOfficerId);
            }

            Set<Long> officers = new HashSet<>();
            officers.add(leadOfficerId);
            if (supportingOfficerIds != null) {
                officers.addAll(supportingOfficerIds);
            }
            if (officers.isEmpty()) {
                throw new IllegalStateException("At least one officer is required");
            }

            for (Long officerId : officers) {
                String role = officerId.equals(leadOfficerId) ? "LEAD_INVESTIGATOR" : "SUPPORTING_OFFICER";
                session.createNativeQuery(
                        "INSERT INTO case_officers(case_id, officer_id, role_in_case, assigned_by) " +
                                "VALUES (:caseId, :officerId, :role, :assignedBy)")
                    .setParameter("caseId", caseId)
                    .setParameter("officerId", officerId)
                    .setParameter("role", role)
                    .setParameter("assignedBy", assignedById)
                    .executeUpdate();
            }

            ensureTransition(caseFile.getStatus(), IncidentStatus.UNDER_INVESTIGATION, allowedCaseTransitions());
            caseFile.setPrimaryInvestigator(lead);
            caseFile.setStatus(IncidentStatus.UNDER_INVESTIGATION);
            session.merge(caseFile);
            return null;
        });
    }

    public void addPersonToCase(Long caseId, Long personId, PersonStatus role, Long addedBy) {
        if (!(role == PersonStatus.SUSPECT || role == PersonStatus.VICTIM || role == PersonStatus.WITNESS)) {
            throw new IllegalArgumentException("Only SUSPECT, VICTIM, WITNESS roles are allowed");
        }
        HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
            Person person = session.get(Person.class, personId);
            if (person == null) {
                throw new IllegalArgumentException("Person not found: " + personId);
            }

            Long duplicate = session.createNativeQuery(
                    "SELECT COUNT(*) FROM case_persons WHERE case_id = :caseId AND person_id = :personId AND role = :role",
                    Long.class)
                .setParameter("caseId", caseId)
                .setParameter("personId", personId)
                .setParameter("role", role.name())
                .getSingleResult();
            if (duplicate != null && duplicate > 0) {
                throw new IllegalStateException("Duplicate person-role mapping for this case");
            }

            session.createNativeQuery(
                    "INSERT INTO case_persons(case_id, person_id, role, added_by) VALUES (:caseId, :personId, :role, :addedBy)")
                .setParameter("caseId", caseId)
                .setParameter("personId", personId)
                .setParameter("role", role.name())
                .setParameter("addedBy", addedBy)
                .executeUpdate();

            // Keep existing compatibility mappings in sync with current entity relationships.
            switch (role) {
                case SUSPECT -> caseFile.addSuspect(person);
                case VICTIM -> caseFile.addVictim(person);
                case WITNESS -> caseFile.addWitness(person);
                default -> { }
            }
            session.merge(caseFile);
            return null;
        });
    }

    public void advanceEvidenceStatus(Long evidenceId, EvidenceStatus newStatus) {
        HibernateUtil.executeTransaction(session -> {
            Evidence evidence = session.get(Evidence.class, evidenceId);
            if (evidence == null) {
                throw new IllegalArgumentException("Evidence not found: " + evidenceId);
            }
            ensureTransition(evidence.getStatus(), newStatus, allowedEvidenceTransitions());
            evidence.setStatus(newStatus);
            session.merge(evidence);
            return null;
        });
    }

    public ArrestRecord registerArrest(Long caseId, Long suspectId, Long officerId, String bookingRef) {
        return HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

            if (caseFile.getStatus() != IncidentStatus.UNDER_INVESTIGATION) {
                throw new IllegalStateException("Arrest allowed only when case is UNDER_INVESTIGATION");
            }

            Long suspectLinkCount = session.createNativeQuery(
                    "SELECT COUNT(*) FROM case_persons WHERE case_id = :caseId AND person_id = :personId AND role = 'SUSPECT'",
                    Long.class)
                .setParameter("caseId", caseId)
                .setParameter("personId", suspectId)
                .getSingleResult();
            if (suspectLinkCount == null || suspectLinkCount == 0) {
                throw new IllegalStateException("Person must be linked as SUSPECT in this case");
            }

            Person suspect = session.get(Person.class, suspectId);
            User officer = session.get(User.class, officerId);
            if (suspect == null || officer == null) {
                throw new IllegalArgumentException("Suspect/officer not found");
            }

            ArrestRecord arrest = new ArrestRecord(suspect, officer);
            arrest.setCaseFile(caseFile);
            arrest.setBookingReference(bookingRef == null || bookingRef.isBlank()
                    ? "ARR-" + System.currentTimeMillis()
                    : bookingRef.trim());
            arrest.setArrestStatus(ArrestStatus.COMPLETED);
            session.persist(arrest);

            suspect.setPersonStatus(PersonStatus.IN_CUSTODY);
            session.merge(suspect);
            caseFile.setStatus(IncidentStatus.ARRESTED);
            session.merge(caseFile);
            return arrest;
        });
    }

    public void generateChargeSheet(Long caseId, Long filedBy, String summary, String legalSections) {
        HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

            long evidenceCount = session.createQuery(
                    "SELECT COUNT(e) FROM Evidence e WHERE e.caseFile.id = :caseId", Long.class)
                .setParameter("caseId", caseId)
                .getSingleResult();
            if (evidenceCount <= 0) {
                throw new IllegalStateException("Cannot generate charge sheet without evidence");
            }

            long suspectCount = session.createNativeQuery(
                    "SELECT COUNT(*) FROM case_persons WHERE case_id = :caseId AND role = 'SUSPECT'", Long.class)
                .setParameter("caseId", caseId)
                .getSingleResult();
            if (suspectCount <= 0) {
                throw new IllegalStateException("Cannot generate charge sheet without suspect");
            }

            Long existing = session.createNativeQuery(
                    "SELECT COUNT(*) FROM charge_sheets WHERE case_id = :caseId", Long.class)
                .setParameter("caseId", caseId)
                .getSingleResult();
            if (existing != null && existing > 0) {
                throw new IllegalStateException("Only one charge sheet allowed per case");
            }

            session.createNativeQuery(
                    "INSERT INTO charge_sheets(case_id, sheet_number, filed_by, filed_on, summary, legal_sections, status) " +
                            "VALUES (:caseId, :sheetNumber, :filedBy, :filedOn, :summary, :sections, 'FILED')")
                .setParameter("caseId", caseId)
                .setParameter("sheetNumber", "CS-" + caseFile.getCaseNumber() + "-" + System.currentTimeMillis())
                .setParameter("filedBy", filedBy)
                .setParameter("filedOn", LocalDate.now())
                .setParameter("summary", summary == null ? "N/A" : summary)
                .setParameter("sections", legalSections)
                .executeUpdate();

            ensureTransition(caseFile.getStatus(), IncidentStatus.CHARGED, allowedCaseTransitions());
            caseFile.setStatus(IncidentStatus.CHARGED);
            session.merge(caseFile);
            return null;
        });
    }

    public void markCaseInTrial(Long caseId) {
        HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
            ensureTransition(caseFile.getStatus(), IncidentStatus.IN_TRIAL, allowedCaseTransitions());
            caseFile.setStatus(IncidentStatus.IN_TRIAL);
            session.merge(caseFile);
            return null;
        });
    }

    public void closeCase(Long caseId, IncidentStatus closureState, String reason) {
        if (!CASE_CLOSURE_STATES.contains(closureState)) {
            throw new IllegalArgumentException("Invalid closure state");
        }

        HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

            if (closureState == IncidentStatus.CLOSED_UNSOLVED) {
                if (reason == null || reason.isBlank()) {
                    throw new IllegalStateException("Unsolved case requires explicit reason");
                }
            } else {
                Long completedHearings = session.createNativeQuery(
                        "SELECT COUNT(*) FROM court_hearings h " +
                                "JOIN court_cases cc ON cc.id = h.court_case_id " +
                                "WHERE cc.case_id = :caseId AND h.hearing_status = 'COMPLETED'",
                        Long.class)
                    .setParameter("caseId", caseId)
                    .getSingleResult();
                if (completedHearings == null || completedHearings == 0) {
                    throw new IllegalStateException("Convicted/acquitted closure requires completed hearing");
                }
            }

            enforceSeriousCrimeRules(session, caseFile);

            caseFile.closeCase(closureState, reason == null || reason.isBlank() ? closureState.name() : reason);
            session.merge(caseFile);
            return null;
        });
    }

    private void enforceSeriousCrimeRules(org.hibernate.Session session, CaseFile caseFile) {
        String codeOrName = session.createQuery(
                "SELECT LOWER(CONCAT(COALESCE(ct.code,''),' ',COALESCE(ct.name,''))) " +
                        "FROM CrimeIncident ci JOIN ci.crimeType ct WHERE ci.id = :incidentId",
                String.class)
            .setParameter("incidentId", caseFile.getIncident().getId())
            .getSingleResult();

        boolean isMurder = codeOrName != null && (codeOrName.contains("murder") || codeOrName.contains("homicide"));
        boolean isAssault = codeOrName != null && codeOrName.contains("assault");
        if (!isMurder && !isAssault) {
            return;
        }

        Long victimCount = session.createNativeQuery(
                "SELECT COUNT(*) FROM case_persons WHERE case_id = :caseId AND role = 'VICTIM'", Long.class)
            .setParameter("caseId", caseFile.getId())
            .getSingleResult();
        if (victimCount == null || victimCount == 0) {
            throw new IllegalStateException("Victim is required for murder/assault closure");
        }

        Long medicalCount = session.createNativeQuery(
                "SELECT COUNT(*) FROM medical_records mr " +
                        "JOIN case_persons cp ON cp.person_id = mr.person_id " +
                        "WHERE cp.case_id = :caseId AND cp.role = 'VICTIM'",
                Long.class)
            .setParameter("caseId", caseFile.getId())
            .getSingleResult();
        if (medicalCount == null || medicalCount == 0) {
            throw new IllegalStateException("Medical/postmortem record is required for victim");
        }

        if (isMurder) {
            Long forensicCount = session.createQuery(
                    "SELECT COUNT(e) FROM Evidence e WHERE e.caseFile.id = :caseId AND e.type = :etype",
                    Long.class)
                .setParameter("caseId", caseFile.getId())
                .setParameter("etype", com.cms.model.enums.EvidenceType.FORENSIC)
                .getSingleResult();
            if (forensicCount == null || forensicCount == 0) {
                throw new IllegalStateException("Murder case requires at least one forensic evidence");
            }

            Long causeOfDeathCount = session.createNativeQuery(
                    "SELECT COUNT(*) FROM medical_records mr " +
                            "JOIN case_persons cp ON cp.person_id = mr.person_id " +
                            "WHERE cp.case_id = :caseId AND cp.role = 'VICTIM' " +
                            "AND mr.medical_notes IS NOT NULL AND TRIM(mr.medical_notes) <> ''",
                    Long.class)
                .setParameter("caseId", caseFile.getId())
                .getSingleResult();
            if (causeOfDeathCount == null || causeOfDeathCount == 0) {
                throw new IllegalStateException("Murder closure requires cause of death in medical notes");
            }
        }
    }

    private String nextCaseNumber() {
        return "CASE-" + LocalDate.now().getYear() + "-" + System.currentTimeMillis();
    }

    private static <T extends Enum<T>> void ensureTransition(T current, T next,
                                         java.util.Map<T, Set<T>> map) {
        Set<T> allowed = map.getOrDefault(current, Set.of());
        if (!allowed.contains(next) && current != next) {
            throw new IllegalStateException("Invalid state transition: " + current + " -> " + next);
        }
    }

    private static java.util.Map<IncidentStatus, Set<IncidentStatus>> allowedIncidentTransitions() {
        return java.util.Map.of(
                IncidentStatus.REPORTED, Set.of(IncidentStatus.VERIFIED),
                IncidentStatus.VERIFIED, Set.of(IncidentStatus.CONVERTED),
                IncidentStatus.CONVERTED, Set.of()
        );
    }

    private static java.util.Map<IncidentStatus, Set<IncidentStatus>> allowedCaseTransitions() {
        return java.util.Map.of(
                IncidentStatus.OPEN, Set.of(IncidentStatus.UNDER_INVESTIGATION),
                IncidentStatus.UNDER_INVESTIGATION, Set.of(IncidentStatus.ARRESTED, IncidentStatus.CHARGED),
                IncidentStatus.ARRESTED, Set.of(IncidentStatus.CHARGED),
                IncidentStatus.CHARGED, Set.of(IncidentStatus.IN_TRIAL),
                IncidentStatus.IN_TRIAL, CASE_CLOSURE_STATES,
                IncidentStatus.CLOSED_UNSOLVED, Set.of(),
                IncidentStatus.CLOSED_ACQUITTED, Set.of(),
                IncidentStatus.CLOSED_CONVICTED, Set.of()
        );
    }

    private static java.util.Map<EvidenceStatus, Set<EvidenceStatus>> allowedEvidenceTransitions() {
        return java.util.Map.of(
                EvidenceStatus.COLLECTED, Set.of(EvidenceStatus.ANALYZED),
                EvidenceStatus.ANALYZED, Set.of(EvidenceStatus.VERIFIED),
                EvidenceStatus.VERIFIED, Set.of()
        );
    }
}
