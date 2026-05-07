package com.cms.repository;

import com.cms.model.CrimeType;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

public class CrimeTypeRepository {

    private final EntityManager entityManager;

    public CrimeTypeRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<CrimeType> findAll() {
        return entityManager
                .createQuery("SELECT c FROM CrimeType c ORDER BY c.id ASC", CrimeType.class)
                .getResultList();
    }

    public Optional<CrimeType> findById(Integer id) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(entityManager.find(CrimeType.class, id));
    }

    public void save(CrimeType crimeType) {
        entityManager.persist(crimeType);
    }
}
