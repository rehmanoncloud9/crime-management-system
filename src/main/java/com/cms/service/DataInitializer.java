package com.cms.service;

import com.cms.model.*;
import com.cms.model.enums.*;
import org.mindrot.jbcrypt.BCrypt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds the database with sample data if it is empty.
 * Called once on application startup.
 */
public class DataInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    public static void seedIfEmpty() {
        try {
            HibernateUtil.executeTransaction(session -> {
                session.createNativeMutationQuery("UPDATE users SET role = 'SUPERVISOR' WHERE role IN ('DETECTIVE', 'MANAGEMENT')").executeUpdate();
                session.createNativeMutationQuery("UPDATE users SET role = 'ANALYST' WHERE role IN ('RECORDS_CLERK', 'PROSECUTOR', 'AUDITOR')").executeUpdate();
                session.createNativeMutationQuery("UPDATE users SET failed_login_attempts = 0, locked_until = NULL, status = 'ACTIVE'").executeUpdate();
                return null;
            });
            logger.info("Executed legacy role migration script.");
        } catch (Exception e) {
            logger.warn("Legacy role migration script failed or unsupported: " + e.getMessage());
        }

        long userCount = HibernateUtil.executeTransaction((org.hibernate.Session session) ->
            session.createQuery("SELECT COUNT(u) FROM User u", Long.class).getSingleResult()
        );

        if (userCount > 0) {
            logger.info("Database already has {} users. Skipping seed.", userCount);
            return;
        }

        logger.info("Database is empty. Seeding sample data...");

        HibernateUtil.executeTransaction(session -> {
            // ======================== GEOGRAPHY ========================
            com.cms.model.geo.Country pk = new com.cms.model.geo.Country();
            pk.setName("Pakistan");
            pk.setCode("PK");
            session.persist(pk);

            // Provinces
            com.cms.model.geo.Province punjab = new com.cms.model.geo.Province();
            punjab.setName("Punjab"); punjab.setCountry(pk);
            session.persist(punjab);

            com.cms.model.geo.Province sindh = new com.cms.model.geo.Province();
            sindh.setName("Sindh"); sindh.setCountry(pk);
            session.persist(sindh);

            com.cms.model.geo.Province kpk = new com.cms.model.geo.Province();
            kpk.setName("Khyber Pakhtunkhwa"); kpk.setCountry(pk);
            session.persist(kpk);

            com.cms.model.geo.Province balochistan = new com.cms.model.geo.Province();
            balochistan.setName("Balochistan"); balochistan.setCountry(pk);
            session.persist(balochistan);

            com.cms.model.geo.Province ict = new com.cms.model.geo.Province();
            ict.setName("Islamabad Capital Territory"); ict.setCountry(pk);
            session.persist(ict);

            // Districts
            String[] punjabDistricts = {"Lahore", "Faisalabad", "Rawalpindi", "Multan", "Gujranwala"};
            for (String d : punjabDistricts) {
                com.cms.model.geo.District dist = new com.cms.model.geo.District();
                dist.setName(d); dist.setProvince(punjab);
                session.persist(dist);
            }

            String[] sindhDistricts = {"Karachi Central", "Karachi South", "Karachi East", "Hyderabad", "Sukkur"};
            for (String d : sindhDistricts) {
                com.cms.model.geo.District dist = new com.cms.model.geo.District();
                dist.setName(d); dist.setProvince(sindh);
                session.persist(dist);
            }

            com.cms.model.geo.District peshawar = new com.cms.model.geo.District();
            peshawar.setName("Peshawar"); peshawar.setProvince(kpk);
            session.persist(peshawar);

            com.cms.model.geo.District quetta = new com.cms.model.geo.District();
            quetta.setName("Quetta"); quetta.setProvince(balochistan);
            session.persist(quetta);

            com.cms.model.geo.District islamabad = new com.cms.model.geo.District();
            islamabad.setName("Islamabad"); islamabad.setProvince(ict);
            session.persist(islamabad);

            // ======================== CRIME TYPES ========================
            CrimeType robbery = new CrimeType("Robbery", "ROB");
            robbery.setDescription("Theft with force or intimidation");
            session.persist(robbery);

            CrimeType murder = new CrimeType("Murder", "MRD");
            murder.setDescription("Unlawful killing of a person");
            session.persist(murder);

            // ======================== OFFICERS (Users) ========================
            User admin = new User();
            admin.setBadgeNumber("ADMIN001");
            admin.setUsername("admin001");
            admin.setFullName("Abdul Rehman");
            admin.setPasswordHash(BCrypt.hashpw("admin001123!", BCrypt.gensalt()));
            admin.setRole(Role.ADMINISTRATOR);
            admin.setStatus(UserStatus.ACTIVE);
            admin.setEmail("abdulrehman@cms.gov.pk");
            admin.setPhone("+923001234567");
            admin.setPrecinct("HQ");
            admin.setDateOfJoining(LocalDate.of(2020, 1, 15));
            session.persist(admin);

            User detective = new User();
            detective.setBadgeNumber("DET001");
            detective.setUsername("det001");
            detective.setFullName("James Carter");
            detective.setPasswordHash(BCrypt.hashpw("det001123!", BCrypt.gensalt()));
            detective.setRole(Role.SUPERVISOR);
            detective.setStatus(UserStatus.ACTIVE);
            detective.setEmail("jamescarter@cms.gov.pk");
            detective.setPhone("+923007654321");
            detective.setPrecinct("Central");
            detective.setDateOfJoining(LocalDate.of(2021, 6, 10));
            session.persist(detective);

            User officer = new User();
            officer.setBadgeNumber("OFF002");
            officer.setUsername("off002");
            officer.setFullName("Sarah Khan");
            officer.setPasswordHash(BCrypt.hashpw("off002123!", BCrypt.gensalt()));
            officer.setRole(Role.OFFICER);
            officer.setStatus(UserStatus.ACTIVE);
            officer.setEmail("sarahkhan@cms.gov.pk");
            officer.setPhone("+923009876543");
            officer.setPrecinct("North");
            officer.setDateOfJoining(LocalDate.of(2022, 3, 20));
            session.persist(officer);

            // ======================== PERSONS ========================
            Person aliKhan = new Person();
            aliKhan.setFirstName("Ali");
            aliKhan.setLastName("Khan");
            aliKhan.setNationalId("35202-1234567-1");
            aliKhan.setGender(Gender.MALE);
            aliKhan.setDateOfBirth(LocalDate.of(1990, 5, 12));
            aliKhan.setGangAffiliation("Karachi Street Gang");
            aliKhan.setHighRisk(true);
            aliKhan.setRiskScore(RiskScore.HIGH);
            aliKhan.setPersonStatus(PersonStatus.CRIMINAL);
            aliKhan.setAddress("Block 14, Gulshan-e-Iqbal, Karachi");
            session.persist(aliKhan);

            Person ahmedRaza = new Person();
            ahmedRaza.setFirstName("Ahmed");
            ahmedRaza.setLastName("Raza");
            ahmedRaza.setNationalId("35202-7654321-2");
            ahmedRaza.setGender(Gender.MALE);
            ahmedRaza.setDateOfBirth(LocalDate.of(1985, 11, 3));
            ahmedRaza.setPersonStatus(PersonStatus.SUSPECT);
            ahmedRaza.setAddress("Model Town, Lahore");
            session.persist(ahmedRaza);

            Person unknownMale = new Person();
            unknownMale.setFirstName("Unknown");
            unknownMale.setLastName("Male");
            unknownMale.setGender(Gender.MALE);
            unknownMale.setPersonStatus(PersonStatus.UNKNOWN);
            session.persist(unknownMale);

            // ======================== INCIDENTS ========================
            CrimeIncident robberyIncident = new CrimeIncident(
                "INC-2025-001", robbery,
                "Armed Robbery at National Bank",
                "Two masked individuals robbed the National Bank branch in Saddar at gunpoint. Approx PKR 2.5M stolen.",
                LocalDateTime.of(2025, 12, 15, 14, 30),
                detective
            );
            robberyIncident.setLocationAddress("National Bank, Saddar, Karachi");
            // robberyIncident.setDistrict("South"); // Pending structural update in Phase 4
            robberyIncident.setPrecinct("Saddar");
            session.persist(robberyIncident);

            CrimeIncident murderIncident = new CrimeIncident(
                "INC-2025-002", murder,
                "Homicide Investigation - Defence Phase 5",
                "Male victim found deceased in residence. Signs of forced entry and struggle. Neighbors reported commotion at approx 02:00 AM.",
                LocalDateTime.of(2025, 12, 20, 2, 0),
                officer
            );
            murderIncident.setLocationAddress("House 42, Street 7, Defence Phase 5, Karachi");
            // murderIncident.setDistrict("South"); // Pending structural update in Phase 4
            murderIncident.setPrecinct("Defence");
            session.persist(murderIncident);

            // ======================== CASES ========================
            CaseFile caseC001 = new CaseFile("C001", robberyIncident);
            caseC001.setPrimaryInvestigator(detective);
            caseC001.setStatus(IncidentStatus.UNDER_INVESTIGATION);
            caseC001.addSuspect(aliKhan);
            session.persist(caseC001);

            CaseFile caseC002 = new CaseFile("C002", murderIncident);
            caseC002.setPrimaryInvestigator(detective);
            caseC002.setStatus(IncidentStatus.OPEN);
            caseC002.addSuspect(ahmedRaza);
            session.persist(caseC002);

            // ======================== EVIDENCE ========================
            Evidence bloodSample = new Evidence("EVD-001", caseC001, EvidenceType.BLOOD_SAMPLE, detective);
            bloodSample.setDescription("Blood sample collected from crime scene floor near vault entrance.");
            bloodSample.setSuspect(aliKhan);
            bloodSample.setCollectionLocation("National Bank vault area");
            bloodSample.setCurrentStorageLocation("Forensic Lab, Locker B-12");
            session.persist(bloodSample);

            Evidence fingerprint = new Evidence("EVD-002", caseC001, EvidenceType.FINGERPRINT, officer);
            fingerprint.setDescription("Fingerprint lifted from bank counter surface. Matches Ali Khan's record.");
            fingerprint.setCollectionLocation("National Bank counter");
            fingerprint.setCurrentStorageLocation("Evidence Room, Cabinet A-3");
            session.persist(fingerprint);

            logger.info("Sample data seeded successfully: 3 officers, 3 persons, 2 cases, 2 evidence records.");
            return null;
        });
    }
}
