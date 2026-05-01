package com.cms.service;

import com.cms.model.Person;
import com.cms.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for advanced analytics: Crime Hotspots, Repeat Offenders, Officer Performance.
 */
public class AnalyticsService {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsService.class);

    /**
     * Get top crime hotspots by district.
     * @param limit max results (e.g. top 5)
     * @return Map of District Name to Crime Count
     */
    public Map<String, Long> getTopCrimeHotspots(int limit) {
        return HibernateUtil.executeTransaction(session -> {
            List<Object[]> results = session.createQuery(
                "SELECT d.name, COUNT(i) FROM CrimeIncident i JOIN i.district d " +
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
     * Identify repeat offenders (Persons linked to > threshold incidents/cases).
     */
    public Map<Person, Long> getRepeatOffenders(int minCases) {
        return HibernateUtil.executeTransaction(session -> {
            List<Object[]> results = session.createQuery(
                "SELECT p, COUNT(c) FROM CaseFile c JOIN c.suspects p " +
                "GROUP BY p.id HAVING COUNT(c) >= :min " +
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
     * Get basic officer performance (Resolved vs Total Cases).
     * @return Map of Officer to a string like "10 / 15 Cases Resolved"
     */
    public Map<User, String> getOfficerPerformance() {
        return HibernateUtil.executeTransaction(session -> {
            List<Object[]> results = session.createQuery(
                "SELECT u, " +
                "SUM(CASE WHEN c.status = com.cms.model.enums.IncidentStatus.CLOSED THEN 1 ELSE 0 END), " +
                "COUNT(c) " +
                "FROM CaseFile c JOIN c.primaryInvestigator u " +
                "GROUP BY u.id " +
                "ORDER BY COUNT(c) DESC", Object[].class
            ).list();

            Map<User, String> performance = new java.util.LinkedHashMap<>();
            for (Object[] row : results) {
                User u = (User) row[0];
                Long closed = (Long) row[1];
                Long total = (Long) row[2];
                if (closed == null) closed = 0L;
                performance.put(u, closed + " / " + total + " Cases Resolved");
            }
            return performance;
        });
    }
}
