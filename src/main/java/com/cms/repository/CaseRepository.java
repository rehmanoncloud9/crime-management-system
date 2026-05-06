package com.cms.repository;

import com.cms.model.CaseFile;
import com.cms.model.User;
import com.cms.model.enums.Role;
import com.cms.model.enums.UserStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class CaseRepository {

    private final EntityManager entityManager;

    public CaseRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<CaseFile> findAll(int limit, int offset) {
        TypedQuery<CaseFile> query = entityManager.createQuery(
                "SELECT c FROM CaseFile c " +
                "LEFT JOIN FETCH c.incident i " +
                "LEFT JOIN FETCH i.crimeType " +
                "LEFT JOIN FETCH c.primaryInvestigator " +
                "ORDER BY c.id DESC",
                CaseFile.class
        );

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public List<CaseFile> search(String keyword, int limit, int offset) {
        if (keyword == null || keyword.isBlank()) {
            return findAll(limit, offset);
        }

        TypedQuery<CaseFile> query = entityManager.createQuery(
                "SELECT c FROM CaseFile c " +
                "LEFT JOIN FETCH c.incident i " +
                "LEFT JOIN FETCH i.crimeType " +
                "LEFT JOIN FETCH c.primaryInvestigator " +
                "WHERE LOWER(c.caseNumber) LIKE LOWER(:kw) " +
                "OR LOWER(i.title) LIKE LOWER(:kw) " +
                "OR LOWER(c.primaryInvestigator.person.firstName) LIKE LOWER(:kw) " +
                "OR LOWER(c.primaryInvestigator.person.lastName) LIKE LOWER(:kw) " +
                "ORDER BY c.id DESC",
                CaseFile.class
        );

        query.setParameter("kw", "%" + keyword.trim() + "%");
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public Optional<CaseFile> findByNumber(String caseNumber) {
        if (caseNumber == null || caseNumber.isBlank()) {
            return Optional.empty();
        }

        TypedQuery<CaseFile> query = entityManager.createQuery(
                "SELECT c FROM CaseFile c " +
                "LEFT JOIN FETCH c.incident i " +
                "LEFT JOIN FETCH i.crimeType " +
                "LEFT JOIN FETCH c.primaryInvestigator " +
                "WHERE c.caseNumber = :num",
                CaseFile.class
        );

        query.setParameter("num", caseNumber);

        List<CaseFile> results = query.getResultList();

        return results.stream().findFirst();
    }

    public Optional<CaseFile> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(entityManager.find(CaseFile.class, id));
    }

    public Optional<CaseFile> findDetailedById(Long id) {
        if (id == null) return Optional.empty();

        TypedQuery<CaseFile> query = entityManager.createQuery(
            "SELECT c FROM CaseFile c " +
            "LEFT JOIN FETCH c.incident i " +
            "LEFT JOIN FETCH i.crimeType " +
            "LEFT JOIN FETCH c.primaryInvestigator " +
            "LEFT JOIN FETCH c.caseSuspects cs LEFT JOIN FETCH cs.person " +
            "LEFT JOIN FETCH c.caseVictims cv LEFT JOIN FETCH cv.person " +
            "LEFT JOIN FETCH c.caseWitnesses cw LEFT JOIN FETCH cw.person " +
            "WHERE c.id = :id",
            CaseFile.class
        );
        query.setParameter("id", id);
        
        try {
            return Optional.of(query.getSingleResult());
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    public void save(CaseFile caseFile) {
        entityManager.persist(caseFile);
    }

    public List<CaseFile> findByInvestigator(Long investigatorId, int limit, int offset) {
        if (investigatorId == null) {
            throw new IllegalArgumentException("investigatorId cannot be null");
        }

        TypedQuery<CaseFile> query = entityManager.createQuery(
                "SELECT c FROM CaseFile c " +
                "LEFT JOIN FETCH c.incident i " +
                "LEFT JOIN FETCH i.crimeType " +
                "LEFT JOIN FETCH c.primaryInvestigator " +
                "WHERE c.primaryInvestigator.id = :id ORDER BY c.id DESC",
                CaseFile.class
        );

        query.setParameter("id", investigatorId);
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public long countByInvestigator(Long investigatorId) {
        if (investigatorId == null) return 0;
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(c) FROM CaseFile c WHERE c.primaryInvestigator.id = :id",
                Long.class
        );
        query.setParameter("id", investigatorId);
        return query.getSingleResult();
    }

    public List<User> findAvailableInvestigators(int limit) {
        // Find officers currently assigned to ACTIVE cases (not closed)
        List<Long> busyIds = entityManager.createQuery(
                "SELECT DISTINCT cf.primaryInvestigator.id FROM CaseFile cf " +
                        "WHERE cf.status IN (:activeStatuses) AND cf.primaryInvestigator IS NOT NULL",
                Long.class
        ).setParameter("activeStatuses", java.util.List.of(
                com.cms.model.enums.CaseStatus.UNDER_INVESTIGATION,
                com.cms.model.enums.CaseStatus.ARRESTED,
                com.cms.model.enums.CaseStatus.CHARGED,
                com.cms.model.enums.CaseStatus.IN_TRIAL
         ))
         .getResultList();

        String hql =
                "SELECT u FROM User u " +
                "LEFT JOIN FETCH u.person " +
                "WHERE u.role IN (:roles) " +
                "AND u.status = :activeStatus " +
                (busyIds.isEmpty() ? "" : "AND u.id NOT IN :busyIds ") +
                "ORDER BY u.person.firstName, u.person.lastName";

        TypedQuery<User> query = entityManager.createQuery(hql, User.class)
                .setParameter("roles", java.util.List.of(
                    Role.OFFICER, Role.SUPERVISOR, Role.DETECTIVE,
                    Role.ADMINISTRATOR, Role.FIELD_AGENT))
                .setParameter("activeStatus", UserStatus.ACTIVE)
                .setMaxResults(limit);

        if (!busyIds.isEmpty()) {
            query.setParameter("busyIds", busyIds);
        }
        return query.getResultList();
    }
}