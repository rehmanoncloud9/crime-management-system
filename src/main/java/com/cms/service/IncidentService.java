package com.cms.service;

import com.cms.model.CrimeIncident;
import com.cms.model.CrimeType;
import com.cms.model.User;
import com.cms.model.geo.Area;
import com.cms.model.geo.City;
import com.cms.model.geo.District;
import com.cms.repository.CrimeTypeRepository;
import com.cms.repository.IncidentRepository;

import java.util.List;

public class IncidentService {

    public List<CrimeType> getAllCrimeTypes() {
        return HibernateUtil.executeTransaction(session ->
            new CrimeTypeRepository(session).findAll()
        );
    }

    public CrimeIncident registerIncident(CrimeIncident incident) {
        HibernateUtil.executeTransaction(session -> {
            // Re-attach all associated entities
            if (incident.getCrimeType() != null && incident.getCrimeType().getId() != null) {
                CrimeType ct = session.get(CrimeType.class, incident.getCrimeType().getId());
                if (ct != null) incident.setCrimeType(ct);
            }
            if (incident.getDistrict() != null && incident.getDistrict().getId() != null) {
                District d = session.get(District.class, incident.getDistrict().getId());
                if (d != null) incident.setDistrict(d);
            }
            if (incident.getCity() != null && incident.getCity().getId() != null) {
                City c = session.get(City.class, incident.getCity().getId());
                if (c != null) incident.setCity(c);
            }
            if (incident.getArea() != null && incident.getArea().getId() != null) {
                Area a = session.get(Area.class, incident.getArea().getId());
                if (a != null) incident.setArea(a);
            }
            if (incident.getReportingOfficer() != null && incident.getReportingOfficer().getId() != null) {
                User u = session.get(User.class, incident.getReportingOfficer().getId());
                if (u != null) incident.setReportingOfficer(u);
            }
            new IncidentRepository(session).save(incident);
            return null;
        });

        // Audit Trail
        AuditService.getInstance().logAction(
            incident.getReportingOfficer(),
            com.cms.model.enums.AuditAction.CREATE,
            "INCIDENT",
            incident.getId(),
            "Registered new incident: " + incident.getIncidentNumber() + " - " + incident.getTitle()
        );

        // Notification after transaction (no nested TX)
        try {
            NotificationService.getInstance().createNotification(
                "New Incident Registered",
                "Incident " + incident.getIncidentNumber() + ": " + incident.getTitle(),
                com.cms.model.enums.NotificationType.INCIDENT,
                com.cms.model.enums.NotificationPriority.IMPORTANT
            );
        } catch (Exception ignored) {}
        
        return incident;
    }

    public List<CrimeIncident> findAll(int limit, int offset) {
        return HibernateUtil.executeTransaction(session ->
            new IncidentRepository(session).findAll(limit, offset));
    }

    public CrimeIncident updateIncident(CrimeIncident incident) {
        return HibernateUtil.executeTransaction(session -> {
            new IncidentRepository(session).save(incident);
            return incident;
        });
    }

    public void deleteIncident(Long id) {
        HibernateUtil.executeVoidTransaction(session -> {
            new IncidentRepository(session).delete(id);
        });
    }

    public long countInMonth(int year, int month) {
        return HibernateUtil.<Long>executeTransaction(session ->
            new IncidentRepository(session).countInMonth(year, month));
    }

    public List<CrimeIncident> searchIncidents(String keyword) {
        return HibernateUtil.executeTransaction(session ->
            new IncidentRepository(session).searchByKeyword(keyword));
    }

    public List<CrimeIncident> getUnlinkedIncidents() {
        return HibernateUtil.executeTransaction(session ->
            new IncidentRepository(session).findUnlinkedIncidents());
    }

    public Object[] getIncidentDossier(Long incidentId) {
        return HibernateUtil.executeTransaction(session -> {
            IncidentRepository repo = new IncidentRepository(session);
            CrimeIncident incident = repo.findDetailedById(incidentId).orElse(null);
            com.cms.model.CaseFile caseFile = repo.findCaseByIncidentId(incidentId).orElse(null);
            return new Object[]{incident, caseFile};
        });
    }
}

