package com.cms.service;

import com.cms.model.enums.IncidentStatus;
import java.util.HashMap;
import java.util.Map;

public class DashboardService {

    public Map<String, Long> getStatistics() {
        return HibernateUtil.executeTransaction(session -> {
            Map<String, Long> stats = new HashMap<>();

            // Total persons/criminals
            try { stats.put("totalCriminals",
                session.createQuery("SELECT COUNT(p) FROM Person p", Long.class).getSingleResult());
            } catch (Exception e) { stats.put("totalCriminals", 0L); }

            // Active cases (any non-closed status)
            try { stats.put("activeCases",
                session.createQuery(
                    "SELECT COUNT(c) FROM CaseFile c WHERE c.status = :s", Long.class)
                    .setParameter("s", IncidentStatus.OPEN).getSingleResult());
            } catch (Exception e) { stats.put("activeCases", 0L); }

            // Officers count
            try { stats.put("totalOfficers",
                session.createQuery("SELECT COUNT(u) FROM User u", Long.class).getSingleResult());
            } catch (Exception e) { stats.put("totalOfficers", 0L); }

            // Closed cases (all closed statuses)
            try { stats.put("closedCases",
                session.createQuery(
                    "SELECT COUNT(c) FROM CaseFile c WHERE c.status IN (:s1,:s2,:s3,:s4)", Long.class)
                    .setParameter("s1", IncidentStatus.CLOSED)
                    .setParameter("s2", IncidentStatus.CLOSED_CONVICTED)
                    .setParameter("s3", IncidentStatus.CLOSED_ACQUITTED)
                    .setParameter("s4", IncidentStatus.CLOSED_UNSOLVED)
                    .getSingleResult());
            } catch (Exception e) { stats.put("closedCases", 0L); }

            // Total incidents
            try { stats.put("totalIncidents",
                session.createQuery("SELECT COUNT(i) FROM CrimeIncident i", Long.class).getSingleResult());
            } catch (Exception e) { stats.put("totalIncidents", 0L); }

            // Total cases
            try { stats.put("totalCases",
                session.createQuery("SELECT COUNT(c) FROM CaseFile c", Long.class).getSingleResult());
            } catch (Exception e) { stats.put("totalCases", 0L); }

            return stats;
        });
    }
}
