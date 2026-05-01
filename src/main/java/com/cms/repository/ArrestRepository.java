package com.cms.repository;

import com.cms.model.ArrestRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class ArrestRepository {

    private final EntityManager entityManager;

    public ArrestRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<ArrestRecord> findAll(int limit, int offset) {
        TypedQuery<ArrestRecord> query = entityManager.createQuery(
                "SELECT a FROM ArrestRecord a " +
                        "LEFT JOIN FETCH a.suspect " +
                        "LEFT JOIN FETCH a.caseFile " +
                        "LEFT JOIN FETCH a.arrestingOfficer " +
                        "ORDER BY a.arrestedAt DESC",
                ArrestRecord.class
        );

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public List<ArrestRecord> findByCase(Long caseId) {
        if (caseId == null) {
            throw new IllegalArgumentException("caseId cannot be null");
        }

        TypedQuery<ArrestRecord> query = entityManager.createQuery(
                "SELECT a FROM ArrestRecord a WHERE a.caseFile.id = :id ORDER BY a.id DESC",
                ArrestRecord.class
        );

        query.setParameter("id", caseId);

        return query.getResultList();
    }

    public void save(ArrestRecord arrest) {
        entityManager.persist(arrest);
    }

    public long countAll() {
        Long count = entityManager.createQuery("SELECT COUNT(a) FROM ArrestRecord a", Long.class)
                .getSingleResult();
        return count == null ? 0L : count;
    }

    public long countSince(java.time.LocalDateTime startInclusive) {
        Long count = entityManager.createQuery(
                        "SELECT COUNT(a) FROM ArrestRecord a WHERE a.arrestedAt >= :start", Long.class)
                .setParameter("start", startInclusive)
                .getSingleResult();
        return count == null ? 0L : count;
    }
}