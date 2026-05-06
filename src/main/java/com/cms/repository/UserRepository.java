package com.cms.repository;

import com.cms.model.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

public class UserRepository {

    private final EntityManager entityManager;

    public UserRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Optional<User> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u WHERE u.username = :username",
                User.class
        );
        query.setParameter("username", username);
        
        List<User> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<User> findByBadgeNumber(String badgeNumber) {
        if (badgeNumber == null || badgeNumber.isBlank()) {
            return Optional.empty();
        }

        TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u WHERE u.badgeNumber = :badge",
                User.class
        );
        query.setParameter("badge", badgeNumber);

        List<User> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }

        TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u WHERE u.person.email = :email",
                User.class
        );
        query.setParameter("email", email);

        List<User> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<User> findAll(int limit, int offset) {
        TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u ORDER BY u.id DESC",
                User.class
        );

        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public List<User> search(String keyword, int limit, int offset) {
        if (keyword == null || keyword.isBlank()) {
            return findAll(limit, offset);
        }

        TypedQuery<User> query = entityManager.createQuery(
                "SELECT u FROM User u " +
                        "LEFT JOIN u.person p " +
                        "WHERE LOWER(u.badgeNumber) LIKE LOWER(:kw) " +
                        "OR LOWER(p.firstName) LIKE LOWER(:kw) " +
                        "OR LOWER(p.lastName) LIKE LOWER(:kw) " +
                        "OR LOWER(u.username) LIKE LOWER(:kw) " +
                        "ORDER BY u.id DESC",
                User.class
        );

        query.setParameter("kw", "%" + keyword.trim().toLowerCase() + "%");
        query.setFirstResult(offset);
        query.setMaxResults(limit);

        return query.getResultList();
    }

    public Optional<User> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(entityManager.find(User.class, id));
    }

    public Optional<User> findDetailedById(Long id) {
        if (id == null) return Optional.empty();
        // Currently User has no lazy collections that need fetching, 
        // but adding for consistency and future-proofing.
        return findById(id);
    }

    public void save(User user) {
        entityManager.persist(user);
    }

    public void update(User user) {
        entityManager.merge(user);
    }
}