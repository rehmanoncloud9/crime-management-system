package com.cms.repository;

import com.cms.model.Person;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class PersonRepository {

    private final EntityManager entityManager;

    public PersonRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Person> findAll(int limit, int offset) {
        TypedQuery<Person> query = entityManager.createQuery(
                "SELECT p FROM Person p WHERE p.deletedAt IS NULL ORDER BY p.id DESC",
                Person.class
        );

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public List<Person> findByName(String firstName, String lastName, int limit, int offset) {
        if (firstName == null) firstName = "";
        if (lastName == null) lastName = "";

        TypedQuery<Person> query = entityManager.createQuery(
                "SELECT p FROM Person p " +
                        "WHERE p.deletedAt IS NULL " +
                        "AND LOWER(p.firstName) LIKE LOWER(:fn) " +
                        "AND LOWER(p.lastName) LIKE LOWER(:ln) " +
                        "ORDER BY p.id DESC",
                Person.class
        );

        query.setParameter("fn", "%" + firstName.trim() + "%");
        query.setParameter("ln", "%" + lastName.trim() + "%");

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public List<Person> findCriminals(int limit, int offset) {
        TypedQuery<Person> query = entityManager.createQuery(
                "SELECT p FROM Person p WHERE p.deletedAt IS NULL " +
                "AND p.personStatus NOT IN ('OFFICER', 'ACTIVE', 'WITNESS', 'VICTIM') " +
                "ORDER BY p.id DESC",
                Person.class
        );

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public List<Person> findCriminalsByName(String firstName, String lastName, int limit, int offset) {
        if (firstName == null) firstName = "";
        if (lastName == null) lastName = "";

        TypedQuery<Person> query = entityManager.createQuery(
                "SELECT p FROM Person p " +
                        "WHERE p.deletedAt IS NULL " +
                        "AND p.personStatus NOT IN ('OFFICER', 'ACTIVE', 'WITNESS', 'VICTIM') " +
                        "AND (LOWER(p.firstName) LIKE LOWER(:fn) AND LOWER(p.lastName) LIKE LOWER(:ln)) " +
                        "ORDER BY p.id DESC",
                Person.class
        );

        query.setParameter("fn", "%" + firstName.trim() + "%");
        query.setParameter("ln", "%" + lastName.trim() + "%");

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public Optional<Person> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(entityManager.find(Person.class, id));
    }

    public Optional<Person> findDetailedById(Long id) {
        if (id == null) return Optional.empty();

        TypedQuery<Person> query = entityManager.createQuery(
            "SELECT p FROM Person p " +
            "LEFT JOIN FETCH p.nationality " +
            "LEFT JOIN FETCH p.district " +
            "LEFT JOIN FETCH p.city " +
            "LEFT JOIN FETCH p.area " +
            "LEFT JOIN FETCH p.medicalRecord " +
            "WHERE p.id = :id AND p.deletedAt IS NULL",
            Person.class
        );
        query.setParameter("id", id);
        
        try {
            return Optional.of(query.getSingleResult());
        } catch (jakarta.persistence.NoResultException e) {
            return Optional.empty();
        }
    }

    public void save(Person person) {
        if (person.getId() == null) {
            entityManager.persist(person);
        } else {
            entityManager.merge(person);
        }
    }

    public void delete(Long id) {
        findById(id).ifPresent(p -> {
            p.setDeletedAt(java.time.LocalDateTime.now());
            entityManager.merge(p);
        });
    }

    public List<Person> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll(50, 0);
        }
        
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        TypedQuery<Person> query = entityManager.createQuery(
            "SELECT p FROM Person p " +
            "WHERE p.deletedAt IS NULL AND (" +
            "LOWER(p.firstName) LIKE :p " +
            "OR LOWER(p.lastName) LIKE :p " +
            "OR LOWER(p.nationalId) LIKE :p) " +
            "ORDER BY p.id DESC",
            Person.class
        );
        query.setParameter("p", pattern);
        query.setMaxResults(50);
        return query.getResultList();
    }
}