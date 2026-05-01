package com.cms.repository;

import com.cms.model.Warrant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class WarrantRepository {

    private final EntityManager entityManager;

    public WarrantRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Warrant> findAll(int limit, int offset) {
        TypedQuery<Warrant> query = entityManager.createQuery(
                "SELECT w FROM Warrant w ORDER BY w.id DESC",
                Warrant.class
        );

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public List<Warrant> findActiveByPerson(Long personId, int limit, int offset) {
        if (personId == null) {
            return List.of();
        }

        TypedQuery<Warrant> query = entityManager.createQuery(
                "SELECT w FROM Warrant w WHERE w.suspect.id = :id AND w.status = :status ORDER BY w.id DESC",
                Warrant.class
        );

        query.setParameter("id", personId);
        query.setParameter("status", "ISSUED"); // ideally enum

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public void save(Warrant warrant) {
        entityManager.persist(warrant);
    }
}