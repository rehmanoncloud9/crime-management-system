package com.cms.service;

import com.cms.model.MedicalRecord;
import com.cms.model.Person;
import com.cms.model.geo.*;
import com.cms.repository.PersonRepository;

import java.util.List;
import java.util.Optional;

public class PersonService {

    public List<Person> findAll(int limit, int offset) {
        return HibernateUtil.executeTransaction(session ->
            new PersonRepository(session).findAll(limit, offset));
    }

    public List<Person> findByName(String firstName, String lastName, int limit, int offset) {
        return HibernateUtil.executeTransaction(session ->
            new PersonRepository(session).findByName(firstName, lastName, limit, offset));
    }

    public List<Person> searchPersons(String keyword) {
        return HibernateUtil.<List<Person>>executeTransaction(session ->
            new PersonRepository(session).search(keyword));
    }

    public Optional<Person> findById(Long id) {
        return HibernateUtil.<Optional<Person>>executeTransaction(session ->
            new PersonRepository(session).findDetailedById(id));
    }

    public void save(Person person) {
        boolean isNew = person.getId() == null;

        // Execute the save in one clean transaction
        HibernateUtil.executeTransaction(session -> {

            // Re-attach all geo/lookup entities to THIS session
            if (person.getNationality() != null && person.getNationality().getId() != null) {
                Country c = session.get(Country.class, person.getNationality().getId());
                if (c != null) person.setNationality(c);
            }
            if (person.getDistrict() != null && person.getDistrict().getId() != null) {
                District d = session.get(District.class, person.getDistrict().getId());
                if (d != null) person.setDistrict(d);
            }
            if (person.getCity() != null && person.getCity().getId() != null) {
                City c = session.get(City.class, person.getCity().getId());
                if (c != null) person.setCity(c);
            }
            if (person.getArea() != null && person.getArea().getId() != null) {
                Area a = session.get(Area.class, person.getArea().getId());
                if (a != null) person.setArea(a);
            }

            // Ensure MedicalRecord bidirectional link is set
            MedicalRecord mr = person.getMedicalRecord();
            if (mr != null) {
                mr.setPerson(person);
            }

            if (isNew) session.persist(person);
            else       session.merge(person);

            return null;
        });

        // Audit AFTER transaction so no nested TX
        try {
            AuditService.getInstance().log(
                isNew ? com.cms.model.enums.AuditAction.CREATE : com.cms.model.enums.AuditAction.UPDATE,
                "Person", person.getId(),
                (isNew ? "Registered new person: " : "Updated person: ")
                    + person.getFirstName() + " " + person.getLastName()
            );
        } catch (Exception ignored) {}

        // Notification AFTER transaction
        try {
            if (isNew) {
                NotificationService.getInstance().createNotification(
                    "New Person Record",
                    "Profile for " + person.getFirstName() + " " + person.getLastName() + " was registered.",
                    com.cms.model.enums.NotificationType.PERSON,
                    com.cms.model.enums.NotificationPriority.INFO
                );
            }
        } catch (Exception ignored) {}
    }

    public void updatePersonStatus(Long personId, com.cms.model.enums.PersonStatus newStatus) {
        HibernateUtil.executeTransaction(session -> {
            var opt = new PersonRepository(session).findById(personId);
            if (opt.isPresent()) {
                Person p = opt.get();
                p.setPersonStatus(newStatus);
                session.merge(p);
            }
            return null;
        });
    }
    public void deletePerson(Long id) {
        HibernateUtil.executeTransaction(session -> {
            PersonRepository repo = new PersonRepository(session);
            repo.delete(id);   // ✅ FIXED
            return null;
        });

        try {
            AuditService.getInstance().log(
                    com.cms.model.enums.AuditAction.DELETE,
                    "Person",
                    id,
                    "Deleted person with ID: " + id
            );
        } catch (Exception ignored) {}
    }
}

