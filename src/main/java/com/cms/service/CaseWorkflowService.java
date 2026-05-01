package com.cms.service;

import com.cms.model.ArrestRecord;
import com.cms.model.CaseFile;
import com.cms.model.CrimeIncident;
import com.cms.model.Evidence;
import com.cms.model.Person;
import com.cms.model.User;
import com.cms.model.enums.ArrestStatus;
import com.cms.model.enums.CasePriority;
import com.cms.model.enums.CaseStatus;
import com.cms.model.enums.EvidenceStatus;
import com.cms.model.enums.IncidentStatus;
import com.cms.model.enums.PersonStatus;
import com.cms.model.enums.Role;
import com.cms.repository.CaseRepository;
import com.cms.repository.IncidentRepository;
import com.cms.util.SecurityUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CaseWorkflowService {

    private static final Set<CaseStatus> CASE_CLOSURE_STATES = Set.of(
            CaseStatus.CLOSED_CONVICTED,
            CaseStatus.CLOSED_ACQUITTED,
            CaseStatus.CLOSED_UNSOLVED
    );

    public CrimeIncident verifyIncident(Long incidentId) {
        SecurityUtils.requireRole(Role.OFFICER, Role.SUPERVISOR, Role.ADMINISTRATOR);
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
        SecurityUtils.requireRole(Role.SUPERVISOR, Role.ADMINISTRATOR);
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
            caseFile.setStatus(CaseStatus.OPEN);
            session.persist(caseFile);

            incident.setStatus(IncidentStatus.CONVERTED);
            session.merge(incident);
            return caseFile;
        });
    }

    public void assignOfficers(Long caseId, Long leadOfficerId, List<Long> supportingOfficerIds, Long assignedById) {
        SecurityUtils.requireRole(Role.SUPERVISOR, Role.ADMINISTRATOR);
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

            ensureTransition(caseFile.getStatus(), CaseStatus.UNDER_INVESTIGATION, allowedCaseTransitions());
            caseFile.setPrimaryInvestigator(lead);
            caseFile.setStatus(CaseStatus.UNDER_INVESTIGATION);
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

            // Determine the correct weak-entity table based on role
            String tableName = switch (role) {
                case SUSPECT -> "case_suspects";
                case VICTIM  -> "case_victims";
                case WITNESS -> "case_witnesses";
                default -> throw new IllegalArgumentException("Invalid role");
            };

            Long duplicate = session.createNativeQuery(
                    "SELECT COUNT(*) FROM " + tableName + " WHERE case_id = :caseId AND person_id = :personId",
                    Long.class)
                .setParameter("caseId", caseId)
                .setParameter("personId", personId)
                .getSingleResult();
            if (duplicate != null && duplicate > 0) {
                throw new IllegalStateException("Duplicate person-role mapping for this case");
            }

            // ERD Disjoint constraint: person cannot hold more than one role in the same case
            // (enforced by DB trigger trg_disjoint_*, but double-check here too)
            for (String otherTable : new String[]{"case_suspects", "case_victims", "case_witnesses"}) {
                if (otherTable.equals(tableName)) continue;
                Long conflict = session.createNativeQuery(
                        "SELECT COUNT(*) FROM " + otherTable + " WHERE case_id = :caseId AND person_id = :personId",
                        Long.class)
                    .setParameter("caseId", caseId)
                    .setParameter("personId", personId)
                    .getSingleResult();
                if (conflict != null && conflict > 0) {
                    throw new IllegalStateException("ERD Disjoint Violation: Person already has a role in this case");
                }
            }

            // Insert into the correct weak entity table via the ORM convenience method
            switch (role) {
                case SUSPECT -> caseFile.addSuspect(person);
                case VICTIM  -> caseFile.addVictim(person);
                case WITNESS -> caseFile.addWitness(person);
                default -> { }
            }
            session.merge(caseFile);
            return null;
        });
    }

    public void advanceEvidenceStatus(Long evidenceId, EvidenceStatus newStatus) {
        SecurityUtils.requireRole(Role.OFFICER, Role.SUPERVISOR, Role.ADMINISTRATOR, Role.RECORDS_CLERK);
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
        SecurityUtils.requireRole(Role.OFFICER, Role.SUPERVISOR, Role.ADMINISTRATOR);
        return HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

            if (caseFile.getStatus() != CaseStatus.UNDER_INVESTIGATION) {
                throw new IllegalStateException("Arrest allowed only when case is UNDER_INVESTIGATION");
            }

            Long suspectLinkCount = session.createNativeQuery(
                    "SELECT COUNT(*) FROM case_suspects WHERE case_id = :caseId AND person_id = :personId",
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
            caseFile.setStatus(CaseStatus.ARRESTED);
            session.merge(caseFile);
            return arrest;
        });
    }

    public void generateChargeSheet(Long caseId, Long filedBy, String summary, String legalSections) {
        SecurityUtils.requireRole(Role.SUPERVISOR, Role.ADMINISTRATOR, Role.LEGAL_OFFICER);
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
                    "SELECT COUNT(*) FROM case_suspects WHERE case_id = :caseId", Long.class)
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

            ensureTransition(caseFile.getStatus(), CaseStatus.CHARGED, allowedCaseTransitions());
            caseFile.setStatus(CaseStatus.CHARGED);
            session.merge(caseFile);
            return null;
        });
    }

    public void markCaseInTrial(Long caseId) {
        SecurityUtils.requireRole(Role.SUPERVISOR, Role.ADMINISTRATOR, Role.LEGAL_OFFICER);
        HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));
            ensureTransition(caseFile.getStatus(), CaseStatus.IN_TRIAL, allowedCaseTransitions());
            caseFile.setStatus(CaseStatus.IN_TRIAL);
            session.merge(caseFile);
            return null;
        });
    }

    public void closeCase(Long caseId, CaseStatus closureState, String reason) {
        SecurityUtils.requireRole(Role.SUPERVISOR, Role.ADMINISTRATOR);
        if (!CASE_CLOSURE_STATES.contains(closureState)) {
            throw new IllegalArgumentException("Invalid closure state");
        }

        HibernateUtil.executeTransaction(session -> {
            CaseFile caseFile = new CaseRepository(session).findById(caseId)
                    .orElseThrow(() -> new IllegalArgumentException("Case not found: " + caseId));

            if (closureState == CaseStatus.CLOSED_UNSOLVED) {
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
                "SELECT COUNT(*) FROM case_victims WHERE case_id = :caseId", Long.class)
            .setParameter("caseId", caseFile.getId())
            .getSingleResult();
        if (victimCount == null || victimCount == 0) {
            throw new IllegalStateException("Victim is required for murder/assault closure");
        }

        Long medicalCount = session.createNativeQuery(
                "SELECT COUNT(*) FROM medical_records mr " +
                        "JOIN case_victims cv ON cv.person_id = mr.person_id " +
                        "WHERE cv.case_id = :caseId",
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
                            "JOIN case_victims cv ON cv.person_id = mr.person_id " +
                            "WHERE cv.case_id = :caseId " +
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
        // W-33: Use high-precision timestamp + random salt to prevent collisions
        return String.format("CASE-%d-%04d", 
            System.currentTimeMillis() % 100000000L, 
            new java.util.Random().nextInt(9999));
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

    private static java.util.Map<CaseStatus, Set<CaseStatus>> allowedCaseTransitions() {
        return java.util.Map.of(
                CaseStatus.OPEN, Set.of(CaseStatus.UNDER_INVESTIGATION),
                CaseStatus.UNDER_INVESTIGATION, Set.of(CaseStatus.ARRESTED, CaseStatus.CHARGED),
                CaseStatus.ARRESTED, Set.of(CaseStatus.CHARGED),
                CaseStatus.CHARGED, Set.of(CaseStatus.IN_TRIAL),
                CaseStatus.IN_TRIAL, CASE_CLOSURE_STATES,
                CaseStatus.CLOSED_UNSOLVED, Set.of(CaseStatus.REOPENED),
                CaseStatus.CLOSED_ACQUITTED, Set.of(CaseStatus.REOPENED),
                CaseStatus.CLOSED_CONVICTED, Set.of(CaseStatus.REOPENED),
                CaseStatus.REOPENED, Set.of(CaseStatus.UNDER_INVESTIGATION)
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
