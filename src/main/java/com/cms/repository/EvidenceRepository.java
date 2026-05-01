package com.cms.repository;

import com.cms.model.Evidence;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class EvidenceRepository {

    private final EntityManager entityManager;

    public EvidenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Evidence> findByCase(Long caseId, int limit, int offset) {
        if (caseId == null) {
            return List.of();
        }

        TypedQuery<Evidence> query = entityManager.createQuery(
                "SELECT e FROM Evidence e WHERE e.caseFile.id = :id ORDER BY e.id DESC",
                Evidence.class
        );

        query.setParameter("id", caseId);
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public Optional<Evidence> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(entityManager.find(Evidence.class, id));
    }

    public void save(Evidence evidence) {
        entityManager.persist(evidence);
    }
}