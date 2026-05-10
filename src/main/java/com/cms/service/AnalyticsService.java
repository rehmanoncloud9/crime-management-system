package com.cms.service;

import com.cms.dto.GeoPointDTO;
import com.cms.dto.OfficerStatsDTO;
import com.cms.model.Person;
import com.cms.model.User;
import com.cms.model.Warrant;
import com.cms.model.enums.CaseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for advanced analytics: Crime Hotspots, Repeat Offenders, Officer Performance.
 * Synchronized with Part 3 Review fixes.
 */
public class AnalyticsService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    /**
     * Get top crime hotspots by district.
     */
    public Map<String, Long> getTopCrimeHotspots(int limit) {
        return HibernateUtil.executeTransaction(session -> {
            List<Object[]> results = session.createQuery(
                "SELECT d.name, COUNT(i) FROM CrimeIncident i JOIN i.area.city.district d " +
                "GROUP BY d.name ORDER BY COUNT(i) DESC", Object[].class
            ).setMaxResults(limit).list();

            Map<String, Long> hotspots = new java.util.LinkedHashMap<>();
            for (Object[] row : results) {
                hotspots.put((String) row[0], (Long) row[1]);
            }
            return hotspots;
        });
    }

    /**
     * Get Geospatial Heatmap Data (S-10).
     */
    public List<GeoPointDTO> getGeoCrimeHeatmap() {
        return HibernateUtil.executeTransaction(session -> {
            List<Object[]> results = session.createQuery(
                "SELECT a.latitude, a.longitude, COUNT(i), a.name " +
                "FROM CrimeIncident i JOIN i.area a " +
                "WHERE a.latitude IS NOT NULL " +
                "GROUP BY a.latitude, a.longitude, a.name", Object[].class
            ).list();

            List<GeoPointDTO> points = new ArrayList<>();
            for (Object[] row : results) {
                java.math.BigDecimal lat = (java.math.BigDecimal) row[0];
                java.math.BigDecimal lon = (java.math.BigDecimal) row[1];
                long count = ((Number) row[2]).longValue();
                points.add(new GeoPointDTO(lat.doubleValue(), lon.doubleValue(), count, (String) row[3]));
            }
            return points;
        });
    }

    /**
     * Identify repeat offenders (W-34: Soft-delete aware).
     */
    public Map<Person, Long> getRepeatOffenders(int minCases) {
        return HibernateUtil.executeTransaction(session -> {
            List<Object[]> results = session.createQuery(
                "SELECT cs.person, COUNT(c) FROM CaseFile c JOIN c.caseSuspects cs " +
                "WHERE cs.person.deletedAt IS NULL AND c.deletedAt IS NULL " +
                "GROUP BY cs.person.id HAVING COUNT(c) >= :min " +
                "ORDER BY COUNT(c) DESC", Object[].class
            ).setParameter("min", (long) minCases).list();

            Map<Person, Long> offenders = new java.util.LinkedHashMap<>();
            for (Object[] row : results) {
                offenders.put((Person) row[0], (Long) row[1]);
            }
            return offenders;
        });
    }

    /**
     * Get structured officer performance (C-12 Fix, W-37 DTO).
     */
    public List<OfficerStatsDTO> getOfficerPerformanceReport() {
        return HibernateUtil.executeTransaction(session -> {
            List<Object[]> results = session.createQuery(
                "SELECT u, " +
                "COUNT(c), " +
                "SUM(CASE WHEN c.status IN (:closedStates) THEN 1 ELSE 0 END), " +
                "SUM(CASE WHEN c.status = :convictedState THEN 1 ELSE 0 END) " +
                "FROM CaseFile c JOIN c.primaryInvestigator u " +
                "WHERE c.deletedAt IS NULL " +
                "GROUP BY u.id " +
                "ORDER BY COUNT(c) DESC", Object[].class
            )
            .setParameter("closedStates", List.of(CaseStatus.CLOSED_CONVICTED, CaseStatus.CLOSED_ACQUITTED, CaseStatus.CLOSED_UNSOLVED))
            .setParameter("convictedState", CaseStatus.CLOSED_CONVICTED)
            .list();

            List<OfficerStatsDTO> stats = new ArrayList<>();
            for (Object[] row : results) {
                stats.add(new OfficerStatsDTO(
                    (User) row[0], 
                    (Long) row[1], 
                    (Long) row[2], 
                    (Long) row[3]
                ));
            }
            return stats;
        });
    }

    /**
     * Identify Warrants approaching expiry (W-36).
     */
    public List<Warrant> getExpiringWarrants(int days) {
        java.time.LocalDate deadline = java.time.LocalDate.now().plusDays(days);
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery(
                "SELECT w FROM Warrant w WHERE w.expiresAt <= :deadline AND w.status = :activeStatus", 
                Warrant.class
            )
            .setParameter("deadline", deadline)
            .setParameter("activeStatus", com.cms.model.enums.WarrantStatus.ISSUED)
            .list()
        );
    }
}

