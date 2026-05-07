package com.cms.repository;

import com.cms.model.CrimeIncident;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class IncidentRepository {

    private final EntityManager entityManager;

    public IncidentRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<CrimeIncident> findAll(int limit, int offset) {
        TypedQuery<CrimeIncident> query = entityManager.createQuery(
                "SELECT i FROM CrimeIncident i ORDER BY i.occurredAt DESC",
                CrimeIncident.class
        );
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    public Optional<CrimeIncident> findByNumber(String incidentNumber) {
        if (incidentNumber == null || incidentNumber.isBlank()) return Optional.empty();
        return entityManager.createQuery(
                "SELECT i FROM CrimeIncident i WHERE i.incidentNumber = :num",
                CrimeIncident.class)
            .setParameter("num", incidentNumber)
            .getResultStream().findFirst();
    }

    public Optional<CrimeIncident> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(entityManager.find(CrimeIncident.class, id));
    }

    public Optional<CrimeIncident> findDetailedById(Long id) {
        if (id == null) return Optional.empty();
        return entityManager.createQuery(
                "SELECT i FROM CrimeIncident i " +
                "LEFT JOIN FETCH i.crimeType " +
                "LEFT JOIN FETCH i.district " +
                "LEFT JOIN FETCH i.city " +
                "LEFT JOIN FETCH i.area " +
                "LEFT JOIN FETCH i.reportingOfficer ro " +
                "LEFT JOIN FETCH ro.person " +
                "WHERE i.id = :id", CrimeIncident.class)
            .setParameter("id", id)
            .getResultStream().findFirst();
    }

    public Optional<com.cms.model.CaseFile> findCaseByIncidentId(Long incidentId) {
        if (incidentId == null) return Optional.empty();
        return entityManager.createQuery(
                "SELECT c FROM com.cms.model.CaseFile c WHERE c.incident.id = :id", com.cms.model.CaseFile.class)
            .setParameter("id", incidentId)
            .getResultStream().findFirst();
    }

    public void save(CrimeIncident incident) {
        if (incident.getId() == null) entityManager.persist(incident);
        else                          entityManager.merge(incident);
    }

    public void delete(Long id) {
        findById(id).ifPresent(entityManager::remove);
    }

    public long countInMonth(int year, int month) {
        LocalDateTime start = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime end   = start.plusMonths(1);
        Long result = entityManager.createQuery(
                "SELECT COUNT(i) FROM CrimeIncident i WHERE i.occurredAt >= :start AND i.occurredAt < :end",
                Long.class)
            .setParameter("start", start)
            .setParameter("end", end)
            .getSingleResult();
        return result != null ? result : 0L;
    }

    public List<CrimeIncident> searchByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return findAll(100, 0);
        return entityManager.createQuery(
                "SELECT i FROM CrimeIncident i " +
                        "LEFT JOIN i.crimeType ct " +
                        "LEFT JOIN i.district d " +
                        "WHERE LOWER(i.title) LIKE :kw " +
                        "OR LOWER(i.incidentNumber) LIKE :kw " +
                        "OR LOWER(ct.name) LIKE :kw " +
                        "OR LOWER(d.name) LIKE :kw " +
                        "ORDER BY i.occurredAt DESC",
                CrimeIncident.class)
            .setParameter("kw", "%" + keyword.toLowerCase() + "%")
            .setMaxResults(50)
            .getResultList();
    }

    public List<CrimeIncident> findUnlinkedIncidents() {
        // Find incidents that are NOT present in any CaseFile
        return entityManager.createQuery(
                "SELECT i FROM CrimeIncident i WHERE i.id NOT IN (SELECT c.incident.id FROM CaseFile c WHERE c.incident IS NOT NULL) ORDER BY i.occurredAt DESC",
                CrimeIncident.class)
            .getResultList();
    }
}

