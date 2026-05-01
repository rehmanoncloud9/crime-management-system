package com.cms.repository;

import com.cms.model.CourtCase;
import com.cms.model.CourtHearing;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class CourtRepository {

    private final EntityManager entityManager;

    public CourtRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<CourtCase> findAllCases(int limit, int offset) {
        TypedQuery<CourtCase> query = entityManager.createQuery(
                "SELECT c FROM CourtCase c ORDER BY c.id DESC",
                CourtCase.class
        );

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public List<CourtHearing> findUpcomingHearings(int limit, int offset) {
        TypedQuery<CourtHearing> query = entityManager.createQuery(
                "SELECT h FROM CourtHearing h WHERE h.hearingDate >= CURRENT_TIMESTAMP ORDER BY h.hearingDate ASC",
                CourtHearing.class
        );

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public Optional<CourtCase> findByInternalCaseNumber(String caseNumber) {
        if (caseNumber == null || caseNumber.isBlank()) {
            return Optional.empty();
        }

        TypedQuery<CourtCase> query = entityManager.createQuery(
                "SELECT c FROM CourtCase c WHERE c.caseFile.caseNumber = :num",
                CourtCase.class
        );

        query.setParameter("num", caseNumber);

        List<CourtCase> results = query.getResultList();
        return results.stream().findFirst();
    }

    public void saveCase(CourtCase courtCase) {
        entityManager.persist(courtCase);
    }

    public void saveHearing(CourtHearing hearing) {
        entityManager.persist(hearing);
    }
}