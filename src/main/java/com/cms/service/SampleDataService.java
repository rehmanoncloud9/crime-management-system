package com.cms.service;

import com.cms.model.*;
import com.cms.model.enums.*;
import com.cms.model.geo.*;
import com.cms.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class SampleDataService {
    private static final Logger logger = LoggerFactory.getLogger(SampleDataService.class);
    private static final Random random = new Random();

    public static void seedAll() {
        // ── GUARANTEED TEST LOGIN ───────────────────────────────────────────
        HibernateUtil.executeVoidTransaction(session -> {
            UserRepository repo = new UserRepository(session);
            ensureUser(session, repo, "TEST001", "admin", "Test Administrator", "admin123", Role.ADMINISTRATOR);
            System.out.println(">>> [SEEDER] Primary Test Account 'admin' / 'admin123' verified.");
        });

        // ── SCHEMA MIGRATION: Convert legacy ENUM/narrow columns to VARCHAR ────────
        // Hibernate hbm2ddl=update cannot ALTER existing ENUM columns in MySQL.
        // Each ALTER is wrapped individually so one failure won't block the rest.
        runMigration("ALTER TABLE users               MODIFY COLUMN role            VARCHAR(30)  NOT NULL");
        runMigration("ALTER TABLE users               MODIFY COLUMN status          VARCHAR(30)");
        runMigration("ALTER TABLE persons             MODIFY COLUMN gender          VARCHAR(20)");
        runMigration("ALTER TABLE persons             MODIFY COLUMN risk_score      VARCHAR(20)");
        runMigration("ALTER TABLE persons             MODIFY COLUMN personStatus    VARCHAR(20)  NOT NULL");
        // Fix aliases column: old schema had it as JSON, new schema expects TEXT
        runMigration("ALTER TABLE persons             MODIFY COLUMN aliases         TEXT");
        // Fix join table column types to match BIGINT PKs (persons.id, case_files.id
        // are BIGINT)
        runMigration("ALTER TABLE case_suspects       MODIFY COLUMN case_id         BIGINT NOT NULL");
        runMigration("ALTER TABLE case_suspects       MODIFY COLUMN person_id       BIGINT NOT NULL");
        runMigration("ALTER TABLE case_victims        MODIFY COLUMN case_id         BIGINT NOT NULL");
        runMigration("ALTER TABLE case_victims        MODIFY COLUMN person_id       BIGINT NOT NULL");
        runMigration("ALTER TABLE case_witnesses      MODIFY COLUMN case_id         BIGINT NOT NULL");
        runMigration("ALTER TABLE case_witnesses      MODIFY COLUMN person_id       BIGINT NOT NULL");
        runMigration("ALTER TABLE evidence            MODIFY COLUMN type            VARCHAR(30)  NOT NULL");
        runMigration("ALTER TABLE evidence            MODIFY COLUMN status          VARCHAR(30)  NOT NULL");
        runMigration("ALTER TABLE crime_incidents     MODIFY COLUMN status          VARCHAR(50)  NOT NULL");
        runMigration("ALTER TABLE case_files          MODIFY COLUMN status          VARCHAR(50)  NOT NULL");
        runMigration("ALTER TABLE warrants            MODIFY COLUMN status          VARCHAR(20)");
        runMigration("ALTER TABLE notifications       MODIFY COLUMN type            VARCHAR(30)  NOT NULL");
        runMigration("ALTER TABLE notifications       MODIFY COLUMN priority        VARCHAR(20)  NOT NULL");
        runMigration("ALTER TABLE login_sessions      MODIFY COLUMN session_status  VARCHAR(20)  NOT NULL");
        runMigration("ALTER TABLE audit_logs          MODIFY COLUMN action          VARCHAR(50)  NOT NULL");
        runMigration("ALTER TABLE medical_records     MODIFY COLUMN blood_group     VARCHAR(20)");
        runMigration("ALTER TABLE court_cases         MODIFY COLUMN status          VARCHAR(30)");
        runMigration("ALTER TABLE ai_analysis_results MODIFY COLUMN analysis_type  VARCHAR(30)");
        // Fix old role names from previous schema versions
        runMigration("UPDATE users SET role = 'SUPERVISOR' WHERE role IN ('DETECTIVE','MANAGEMENT')");
        runMigration("UPDATE users SET role = 'ANALYST' WHERE role IN ('RECORDS_CLERK','PROSECUTOR','AUDITOR')");

        // Check ALL key tables - if ANY is missing data, reseed that data
        long userCount = HibernateUtil.executeTransaction(
                session -> session.createQuery("SELECT COUNT(u) FROM User u", Long.class).getSingleResult());
        long personCount = HibernateUtil.executeTransaction(
                session -> session.createQuery("SELECT COUNT(p) FROM Person p", Long.class).getSingleResult());
        long caseCount = HibernateUtil.executeTransaction(
                session -> session.createQuery("SELECT COUNT(c) FROM CaseFile c", Long.class).getSingleResult());
        long districtCount = HibernateUtil.executeTransaction(
                session -> session.createQuery("SELECT COUNT(d) FROM District d", Long.class).getSingleResult());

        // If we have users AND persons AND cases AND districts, nothing to do
        System.out.println(">>> [SEEDER] Current user count: " + userCount);
        if (userCount > 0 && personCount > 0 && caseCount > 0 && districtCount > 0) {
            System.out.println(">>> [SEEDER] Database already contains data. Running critical updates...");
            ensureCriticalAccountsExist();
            System.out.println(">>> [SEEDER] Critical updates complete.");
            return;
        }

        // If we have users but missing other data, we need to wipe and reseed
        if (userCount > 0) {
            logger.info("Partial DB detected (users={}, persons={}, cases={}, districts={}). Full reseed...",
                    userCount, personCount, caseCount, districtCount);
            // Wipe all data so full reseed can run cleanly
            HibernateUtil.executeVoidTransaction(session -> {
                session.createMutationQuery("DELETE FROM AuditLog").executeUpdate();
                session.createMutationQuery("DELETE FROM Notification").executeUpdate();
                session.createMutationQuery("DELETE FROM ChatMessage").executeUpdate();
                session.createMutationQuery("DELETE FROM ChatSession").executeUpdate();
                session.createMutationQuery("DELETE FROM Evidence").executeUpdate();
                session.createMutationQuery("DELETE FROM ArrestRecord").executeUpdate();
                session.createMutationQuery("DELETE FROM Warrant").executeUpdate();
                session.createMutationQuery("DELETE FROM CourtHearing").executeUpdate();
                session.createMutationQuery("DELETE FROM CourtCase").executeUpdate();
                session.createMutationQuery("DELETE FROM AIAnalysisResult").executeUpdate();
                try {
                    session.createMutationQuery("DELETE FROM LoginSession").executeUpdate();
                } catch (Exception ignore) {
                }
                // Remove case-person links first
                session.createMutationQuery("UPDATE CaseFile c SET c.primaryInvestigator = null").executeUpdate();
                session.createNativeQuery("DELETE FROM case_suspects").executeUpdate();
                session.createNativeQuery("DELETE FROM case_victims").executeUpdate();
                session.createNativeQuery("DELETE FROM case_witnesses").executeUpdate();
                session.createMutationQuery("DELETE FROM CaseFile").executeUpdate();
                session.createMutationQuery("DELETE FROM CrimeIncident").executeUpdate();
                session.createMutationQuery("DELETE FROM MedicalRecord").executeUpdate();
                session.createMutationQuery("DELETE FROM Person").executeUpdate();
                session.createMutationQuery("DELETE FROM User").executeUpdate();

                // FIX self-referencing CrimeType hierarchy
                session.createMutationQuery("UPDATE CrimeType c SET c.parent = null").executeUpdate();
                session.createMutationQuery("DELETE FROM CrimeType").executeUpdate();

                // Wipe geography
                session.createMutationQuery("DELETE FROM Area").executeUpdate();
                session.createMutationQuery("DELETE FROM City").executeUpdate();
                session.createMutationQuery("DELETE FROM District").executeUpdate();
                session.createMutationQuery("DELETE FROM Province").executeUpdate();
                session.createMutationQuery("DELETE FROM Country").executeUpdate();
            });
        }

        logger.info("Seeding comprehensive CMS data...");

        HibernateUtil.executeVoidTransaction(session -> {

            // ── CRIME TYPES ──────────────────────────────────────────────────────
            CrimeType robbery = ct(session, "Robbery", "ROB", "Theft with force or intimidation");
            CrimeType murder = ct(session, "Murder", "MRD", "Unlawful killing of a human being");
            CrimeType cyberFraud = ct(session, "Cyber Fraud", "CF", "Financial fraud via digital means");
            CrimeType kidnapping = ct(session, "Kidnapping", "KID", "Unlawful abduction and confinement");
            CrimeType assault = ct(session, "Assault", "ASL", "Physical attack on an individual");
            CrimeType extortion = ct(session, "Extortion", "EXT", "Obtaining something through threats");
            CrimeType narcotics = ct(session, "Narcotics Operations", "NAR", "Drug trafficking and distribution");
            CrimeType vehicleTheft = ct(session, "Vehicle Theft", "VT", "Theft of motorized vehicles");
            CrimeType arson = ct(session, "Arson", "ARS", "Deliberate setting of fire to property");
            CrimeType terrorism = ct(session, "Terrorism", "TER", "Acts intended to cause terror");
            CrimeType dacoity = ct(session, "Dacoity", "DAC", "Robbery by group of five or more persons");
            CrimeType forgery = ct(session, "Forgery / Document Fraud", "FRG", "Falsification of official documents");
            List<CrimeType> crimeTypes = List.of(robbery, murder, cyberFraud, kidnapping, assault,
                    extortion, narcotics, vehicleTheft, arson, terrorism, dacoity, forgery);

            // ── GEOGRAPHY — ALL 36 PUNJAB DISTRICTS + CITIES + AREAS ─────────────
            Country pakistan = new Country();
            pakistan.setName("Pakistan");
            pakistan.setCode("PK");
            session.persist(pakistan);

            Province punjab = new Province();
            punjab.setName("Punjab");
            punjab.setCountry(pakistan);
            Province sindh = new Province();
            sindh.setName("Sindh");
            sindh.setCountry(pakistan);
            Province kpk = new Province();
            kpk.setName("KPK");
            kpk.setCountry(pakistan);
            Province isb = new Province();
            isb.setName("Islamabad Capital Territory");
            isb.setCountry(pakistan);
            Province baloch = new Province();
            baloch.setName("Balochistan");
            baloch.setCountry(pakistan);
            session.persist(punjab);
            session.persist(sindh);
            session.persist(kpk);
            session.persist(isb);
            session.persist(baloch);

            // ── 36 PUNJAB DISTRICTS ──────────────────────────────────────────────
            District lahore = dist(session, "Lahore", punjab);
            District faisalabad = dist(session, "Faisalabad", punjab);
            District multan = dist(session, "Multan", punjab);
            District rawalpindi = dist(session, "Rawalpindi", punjab);
            District gujranwala = dist(session, "Gujranwala", punjab);
            District sargodha = dist(session, "Sargodha", punjab);
            District sialkot = dist(session, "Sialkot", punjab);
            District bahawalpur = dist(session, "Bahawalpur", punjab);
            District sahiwal = dist(session, "Sahiwal", punjab);
            District sheikhupura = dist(session, "Sheikhupura", punjab);
            District gujrat = dist(session, "Gujrat", punjab);
            District jhang = dist(session, "Jhang", punjab);
            District kasur = dist(session, "Kasur", punjab);
            District okara = dist(session, "Okara", punjab);
            District nankana = dist(session, "Nankana Sahib", punjab);
            District hafizabad = dist(session, "Hafizabad", punjab);
            District chiniot = dist(session, "Chiniot", punjab);
            District narowal = dist(session, "Narowal", punjab);
            District mandi = dist(session, "Mandi Bahauddin", punjab);
            District toba = dist(session, "Toba Tek Singh", punjab);
            District vehari = dist(session, "Vehari", punjab);
            District pakpattan = dist(session, "Pakpattan", punjab);
            District khanewal = dist(session, "Khanewal", punjab);
            District lodhran = dist(session, "Lodhran", punjab);
            District bahawalnagar = dist(session, "Bahawalnagar", punjab);
            District rahimYar = dist(session, "Rahim Yar Khan", punjab);
            District muzaffargarh = dist(session, "Muzaffargarh", punjab);
            District dgkhan = dist(session, "Dera Ghazi Khan", punjab);
            District rajanpur = dist(session, "Rajanpur", punjab);
            District layyah = dist(session, "Layyah", punjab);
            District mianwali = dist(session, "Mianwali", punjab);
            District bhakkarD = dist(session, "Bhakkar", punjab);
            District khushab = dist(session, "Khushab", punjab);
            District jhelum = dist(session, "Jhelum", punjab);
            District chakwal = dist(session, "Chakwal", punjab);
            District attock = dist(session, "Attock", punjab);
            // Other provinces
            District karachi = dist(session, "Karachi", sindh);
            District islamabadD = dist(session, "Islamabad", isb);

            List<District> punjabDistricts = List.of(lahore, faisalabad, multan, rawalpindi, gujranwala,
                    sargodha, sialkot, bahawalpur, sahiwal, sheikhupura);
            List<District> allDistricts = List.of(lahore, faisalabad, multan, rawalpindi, gujranwala,
                    sargodha, sialkot, bahawalpur, sahiwal, sheikhupura, karachi, islamabadD);

            // ── CITIES ───────────────────────────────────────────────────────────
            City lahoreCity = city(session, "Lahore", lahore);
            city(session, "Wagah", lahore);
            city(session, "Raiwind", lahore);
            City fcityData = city(session, "Faisalabad", faisalabad);
            city(session, "Lyallpur Colony", faisalabad);
            city(session, "Samundri", faisalabad);
            city(session, "Jaranwala", faisalabad);
            City multanCity = city(session, "Multan", multan);
            city(session, "Shujabad", multan);
            City rawalpindiCity = city(session, "Rawalpindi", rawalpindi);
            city(session, "Murree", rawalpindi);
            city(session, "Taxila", rawalpindi);
            city(session, "Gujar Khan", rawalpindi);
            City gujranwalaCity = city(session, "Gujranwala", gujranwala);
            city(session, "Wazirabad", gujranwala);
            city(session, "Kamoke", gujranwala);
            City sargodhaCity = city(session, "Sargodha", sargodha);
            city(session, "Bhalwal", sargodha);
            City sialkotCity = city(session, "Sialkot", sialkot);
            city(session, "Daska", sialkot);
            city(session, "Pasrur", sialkot);
            City bahawalpurCity = city(session, "Bahawalpur", bahawalpur);
            city(session, "Rahim Yar Khan", rahimYar);
            city(session, "Ahmadpur East", bahawalpur);
            City sahiwalCity = city(session, "Sahiwal", sahiwal);
            city(session, "Okara", okara);
            city(session, "Depalpur", okara);
            City sheikhupuraCity = city(session, "Sheikhupura", sheikhupura);
            city(session, "Nankana Sahib", nankana);
            city(session, "Muridke", sheikhupura);
            City gujratCity = city(session, "Gujrat", gujrat);
            city(session, "Kharian", gujrat);
            City jhangCity = city(session, "Jhang", jhang);
            city(session, "Shorkot", jhang);
            city(session, "Chiniot", chiniot);
            city(session, "Chiniot City", chiniot);
            City kasurCity = city(session, "Kasur", kasur);
            city(session, "Pattoki", kasur);
            city(session, "Chunian", kasur);
            City jhelumCity = city(session, "Jhelum", jhelum);
            city(session, "Chakwal", chakwal);
            City attockCity = city(session, "Attock", attock);
            city(session, "Hasan Abdal", attock);
            city(session, "Hazro", attock);
            city(session, "Hafizabad", hafizabad);
            city(session, "Pindi Bhattian", hafizabad);
            city(session, "Mandi Bahauddin", mandi);
            city(session, "Toba Tek Singh", toba);
            city(session, "Gojra", toba);
            city(session, "Vehari", vehari);
            city(session, "Burewala", vehari);
            city(session, "Pakpattan", pakpattan);
            city(session, "Arifwala", pakpattan);
            city(session, "Khanewal", khanewal);
            city(session, "Kabirwala", khanewal);
            city(session, "Lodhran", lodhran);
            city(session, "Bahawalnagar", bahawalnagar);
            city(session, "Muzaffargarh", muzaffargarh);
            city(session, "Dera Ghazi Khan", dgkhan);
            city(session, "Rajanpur", rajanpur);
            city(session, "Layyah", layyah);
            city(session, "Mianwali", mianwali);
            city(session, "Bhakkar", bhakkarD);
            city(session, "Khushab", khushab);
            city(session, "Narowal", narowal);
            City karachiCity = city(session, "Karachi", karachi);
            City isbCity = city(session, "Islamabad", islamabadD);

            // ── AREAS ────────────────────────────────────────────────────────────
            // Lahore
            area(session, "Model Town", lahoreCity);
            area(session, "DHA Phase 1", lahoreCity);
            area(session, "DHA Phase 5", lahoreCity);
            area(session, "DHA Phase 6", lahoreCity);
            area(session, "Gulberg I", lahoreCity);
            area(session, "Gulberg II", lahoreCity);
            area(session, "Gulberg III", lahoreCity);
            area(session, "Johar Town", lahoreCity);
            area(session, "Wapda Town", lahoreCity);
            area(session, "Samanabad", lahoreCity);
            area(session, "Iqbal Town", lahoreCity);
            area(session, "Data Gunj Bakhsh", lahoreCity);
            area(session, "Canal Road", lahoreCity);
            area(session, "Township", lahoreCity);
            area(session, "Shadman", lahoreCity);
            area(session, "Garden Town", lahoreCity);
            area(session, "Cavalry Ground", lahoreCity);
            area(session, "Cantt", lahoreCity);
            area(session, "Baghban Pura", lahoreCity);
            area(session, "Shahdara", lahoreCity);
            area(session, "Ichra", lahoreCity);
            area(session, "Anarkali", lahoreCity);
            area(session, "Mall Road", lahoreCity);
            area(session, "Thokar Niaz Baig", lahoreCity);
            area(session, "Bahria Town Lahore", lahoreCity);
            area(session, "Lake City", lahoreCity);
            area(session, "Valencia", lahoreCity);
            // Faisalabad
            area(session, "D-Ground", fcityData);
            area(session, "Millat Town", fcityData);
            area(session, "Peoples Colony", fcityData);
            area(session, "Gulshan Colony", fcityData);
            area(session, "Madina Town", fcityData);
            area(session, "Saeed Colony", fcityData);
            area(session, "Jinnah Colony", fcityData);
            area(session, "Ghulam Muhammad Abad", fcityData);
            area(session, "Susan Road", fcityData);
            area(session, "Kohinoor Town", fcityData);
            area(session, "Batala Colony", fcityData);
            area(session, "57 JB", fcityData);
            area(session, "Nishatabad", fcityData);
            area(session, "Dijkot Road", fcityData);
            area(session, "Canal View", fcityData);
            // Multan
            area(session, "Sadar", multanCity);
            area(session, "Gulgasht", multanCity);
            area(session, "New Multan", multanCity);
            area(session, "Shah Rukn-e-Alam", multanCity);
            area(session, "Cantt", multanCity);
            area(session, "Katchery Road", multanCity);
            area(session, "Hussain Agahi", multanCity);
            area(session, "Wapda Town Multan", multanCity);
            area(session, "Model Town Multan", multanCity);
            // Rawalpindi
            area(session, "Satellite Town", rawalpindiCity);
            area(session, "Bahria Town", rawalpindiCity);
            area(session, "Chaklala", rawalpindiCity);
            area(session, "Saddar", rawalpindiCity);
            area(session, "Raja Bazaar", rawalpindiCity);
            area(session, "Cantt", rawalpindiCity);
            area(session, "Westridge", rawalpindiCity);
            area(session, "Gulistan Colony", rawalpindiCity);
            area(session, "Airport Road", rawalpindiCity);
            // Gujranwala
            area(session, "Civil Lines", gujranwalaCity);
            area(session, "Peoples Colony", gujranwalaCity);
            area(session, "Satellite Town", gujranwalaCity);
            area(session, "Model Town", gujranwalaCity);
            area(session, "Link Road", gujranwalaCity);
            // Sargodha
            area(session, "University Road", sargodhaCity);
            area(session, "Satellite Town", sargodhaCity);
            area(session, "Canal Road", sargodhaCity);
            area(session, "Cantt", sargodhaCity);
            // Sialkot
            area(session, "Cantt", sialkotCity);
            area(session, "Saddar", sialkotCity);
            area(session, "Paris Road", sialkotCity);
            area(session, "Allama Iqbal Town", sialkotCity);
            area(session, "Model Town", sialkotCity);
            // Bahawalpur
            area(session, "Model Town", bahawalpurCity);
            area(session, "Satellite Town", bahawalpurCity);
            area(session, "Cantt", bahawalpurCity);
            area(session, "Saddar", bahawalpurCity);
            // Sheikhupura
            area(session, "Model Town", sheikhupuraCity);
            area(session, "Satellite Town", sheikhupuraCity);
            area(session, "Tajpura", sheikhupuraCity);
            // Islamabad
            area(session, "F-6 Sector", isbCity);
            area(session, "F-7 Sector", isbCity);
            area(session, "F-8 Sector", isbCity);
            area(session, "G-9 Sector", isbCity);
            area(session, "G-10 Sector", isbCity);
            area(session, "G-11 Sector", isbCity);
            area(session, "I-8 Sector", isbCity);
            area(session, "Blue Area", isbCity);
            area(session, "DHA Islamabad", isbCity);

            // ── OFFICERS (10 named + extras) ─────────────────────────────────────
            List<User> officers = new ArrayList<>();

            User kaleem = user(session, "KALEEM001", "kaleem", "Kaleem Sajjad", "atifaslam", Role.ADMINISTRATOR, "Central HQ");
            User rehmanMalik = user(session, "REHMAN001", "rehman.malik", "Rehman Malik", "atifaslam", Role.ADMINISTRATOR, "HQ");
            User abdulRehman = user(session, "ADMIN001", "rehman", "Abdul Rehman", "atifaslam", Role.ADMINISTRATOR, "HQ");
            officers.add(kaleem);
            officers.add(rehmanMalik);
            officers.add(abdulRehman);

            // 10 named investigators/officers
            String[][] namedOfficers = {
                    { "OFF001", "ali.khan", "Ali Hassan Khan", "Lahore Precinct" },
                    { "OFF002", "sara.ali", "Sara Ali", "Faisalabad Precinct" },
                    { "OFF003", "usman.raza", "Usman Raza", "Multan Precinct" },
                    { "OFF004", "bilal.h", "Bilal Hussain", "Rawalpindi Precinct" },
                    { "OFF005", "fatima.m", "Fatima Malik", "Gujranwala Precinct" },
                    { "SUP001", "sup.ahmed", "Ahmed Nawaz (Sup.)", "Punjab HQ" },
                    { "SUP002", "sup.nadia", "Nadia Sheikh (Sup.)", "Lahore Division" },
                    { "ANA001", "ana.zain", "Zain ul Abideen (Ana.)", "CID Analytics" },
                    { "INV001", "inv.kamran", "Kamran Javed (Inv.)", "Special Branch" },
                    { "INV002", "inv.hina", "Hina Iqbal (Inv.)", "Cyber Crime Unit" },
            };
            Role[] namedRoles = { Role.OFFICER, Role.OFFICER, Role.OFFICER, Role.OFFICER, Role.OFFICER,
                    Role.SUPERVISOR, Role.SUPERVISOR, Role.ANALYST, Role.OFFICER, Role.OFFICER };
            for (int i = 0; i < namedOfficers.length; i++) {
                String[] o = namedOfficers[i];
                User u = user(session, o[0], o[1], o[2], o[1] + "123!", namedRoles[i], o[3]);
                officers.add(u);
            }

            // ── PERSONS / CRIMINALS ──────────────────────────────────────────────
            List<Person> persons = new ArrayList<>();
            String[][] criminalData = {
                    { "Tariq", "Mehmood", "35201-1234567-1", "CRIMINAL" },
                    { "Naeem", "Butt", "35202-7654321-2", "SUSPECT" },
                    { "Sohail", "Akhtar", "35203-1111111-3", "SUSPECT" },
                    { "Waseem", "Baig", "35204-2222222-1", "SUSPECT" },
                    { "Irfan", "Chaudhry", "35205-3333333-2", "CRIMINAL" },
                    { "Noman", "Gill", "35206-4444444-3", "CRIMINAL" },
                    { "Adnan", "Qureshi", "35207-5555555-1", "SUSPECT" },
                    { "Zaheer", "Abbas", "35208-6666666-2", "SUSPECT" },
                    { "Shahid", "Latif", "35209-7777777-3", "CRIMINAL" },
                    { "Pervez", "Mirza", "35210-8888888-1", "CRIMINAL" },
                    { "Mukhtar", "Ahmed", "35211-9999999-2", "SUSPECT" },
                    { "Farhan", "Siddiqui", "35212-1234321-3", "SUSPECT" },
                    { "Rashid", "Nawaz", "35213-3214321-1", "CRIMINAL" },
                    { "Hamid", "Zafar", "35214-4321234-2", "SUSPECT" },
                    { "Rizwan", "Sultan", "35215-5432123-3", "SUSPECT" },
            };
            District[] personDistricts = { lahore, faisalabad, multan, rawalpindi, gujranwala,
                    sargodha, sialkot, bahawalpur, sahiwal, sheikhupura, lahore, faisalabad, multan, rawalpindi,
                    gujranwala };

            for (int i = 0; i < criminalData.length; i++) {
                String[] cd = criminalData[i];
                Person p = new Person();
                p.setFirstName(cd[0]);
                p.setLastName(cd[1]);
                p.setNationalId(cd[2]);
                p.setGender(Gender.MALE);
                p.setDateOfBirth(LocalDate.now().minusYears(20 + random.nextInt(35)));
                p.setPersonStatus(PersonStatus.valueOf(cd[3]));
                p.setNationality(pakistan);
                p.setDistrict(personDistricts[i % personDistricts.length]);
                p.setHasActiveWarrant(cd[3].equals("CRIMINAL") || cd[3].equals("SUSPECT"));
                p.setAliases(cd[0].charAt(0) + "." + cd[1]);

                MedicalRecord mr = new MedicalRecord();
                mr.setBloodGroup(BloodGroup.values()[random.nextInt(BloodGroup.values().length - 1)]);
                p.setMedicalRecord(mr);
                session.persist(p);
                session.persist(mr);
                persons.add(p);
            }

            // ── INCIDENTS & CASES (15 rich incidents) ────────────────────────────
            String[][] incidentData = {
                    { "Armed Bank Robbery – MCB Lahore",
                            "Masked gunmen robbed MCB Bank on Mall Road. Cash of 8.5M PKR taken.", "ROB" },
                    { "Double Homicide – Faisalabad",
                            "Two bodies found near industrial estate. Suspected gang rivalry.", "MRD" },
                    { "Cyber Fraud – Online Banking Scam",
                            "Victims tricked via phishing emails. 50+ accounts compromised.", "CF" },
                    { "Kidnapping for Ransom – Multan", "Businessman's son abducted. 10M PKR ransom demanded.", "KID" },
                    { "Street Assault – Gulberg Lahore", "Man seriously injured in unprovoked knife attack.", "ASL" },
                    { "Drug Trafficking – Motorway M2",
                            "12kg heroin recovered during routine check. 3 suspects arrested.", "NAR" },
                    { "Vehicle Theft Ring – Rawalpindi", "Organized ring stealing cars, 15 vehicles recovered.", "VT" },
                    { "Extortion – Gujranwala Business Owner",
                            "Shop owner received death threats, forced to pay Rs.500K monthly.", "EXT" },
                    { "Arson – Warehouse Sialkot", "Industrial warehouse deliberately set on fire. Loss: 20M PKR.",
                            "ARS" },
                    { "Terrorism Suspect – Lahore", "High-value terrorism suspect apprehended at airport.", "TER" },
                    { "Dacoity – Sargodha Highway", "Armed gang robbed 3 vehicles on N-5. Shots fired.", "DAC" },
                    { "Document Forgery – NADRA Records", "Forged CNIC discovered during immigration check.", "FRG" },
                    { "Robbery – Gold Shop Faisalabad",
                            "Jewellery shop looted in broad daylight. CCTV footage available.", "ROB" },
                    { "Murder – Domestic Violence Case", "Victim found at residence. Husband prime suspect.", "MRD" },
                    { "Narcotics Lab Discovered – Lahore", "Illegal drug manufacturing unit raided. 4 arrested.",
                            "NAR" },
            };

            Map<String, CrimeType> ctMap = new HashMap<>();
            for (CrimeType ct : crimeTypes)
                ctMap.put(ct.getCode(), ct);

            IncidentStatus[] statusCycle = {
                    IncidentStatus.OPEN, IncidentStatus.UNDER_INVESTIGATION, IncidentStatus.OPEN,
                    IncidentStatus.CLOSED, IncidentStatus.UNDER_INVESTIGATION, IncidentStatus.CLOSED_CONVICTED,
                    IncidentStatus.OPEN, IncidentStatus.UNDER_INVESTIGATION, IncidentStatus.CLOSED,
                    IncidentStatus.OPEN, IncidentStatus.UNDER_INVESTIGATION, IncidentStatus.CLOSED_CONVICTED,
                    IncidentStatus.OPEN, IncidentStatus.CLOSED, IncidentStatus.UNDER_INVESTIGATION
            };

            for (int i = 0; i < incidentData.length; i++) {
                String[] id = incidentData[i];
                District d = punjabDistricts.get(i % punjabDistricts.size());
                User officer = officers.get((i + 3) % officers.size());

                CrimeIncident inc = new CrimeIncident();
                inc.setIncidentNumber("INC-2026-" + String.format("%03d", i + 1));
                inc.setTitle(id[0]);
                inc.setDescription(id[1]);
                inc.setCrimeType(ctMap.getOrDefault(id[2], robbery));
                inc.setOccurredAt(LocalDateTime.now().minusDays(random.nextInt(90)).minusHours(random.nextInt(24)));
                inc.setReportingOfficer(officer);
                inc.setDistrict(d);
                inc.setPrecinct(d.getName() + " Police Station");
                inc.setLocationAddress(d.getName() + " City, Block " + (random.nextInt(15) + 1));
                session.persist(inc);

                CaseFile cf = new CaseFile();
                cf.setCaseNumber("CASE-2026-" + String.format("%03d", i + 1));
                cf.setIncident(inc);
                cf.setPrimaryInvestigator(officers.get((i + 4) % officers.size()));
                cf.setStatus(statusCycle[i]);

                // Add 1-3 suspects
                int ns = random.nextInt(3) + 1;
                for (int s = 0; s < ns && s < persons.size(); s++) {
                    cf.addSuspect(persons.get((i * 3 + s) % persons.size()));
                }
                session.persist(cf);

                // Evidence
                int ne = random.nextInt(3) + 1;
                for (int e = 0; e < ne; e++) {
                    EvidenceType et = EvidenceType.values()[random.nextInt(EvidenceType.values().length)];
                    Evidence ev = new Evidence("EVD-2026-" + (i + 1) + "-" + e, cf, et, officer);
                    ev.setDescription(
                            "Physical evidence recovered at scene: " + et.name().toLowerCase().replace("_", " "));
                    ev.setCollectionLocation(d.getName() + " Crime Scene");
                    ev.setCurrentStorageLocation("Evidence Locker " + (random.nextInt(20) + 1));
                    ev.setStatus(EvidenceStatus.values()[random.nextInt(EvidenceStatus.values().length)]);
                    ev.setCollectedAt(LocalDateTime.now().minusDays(random.nextInt(5))); // Explicitly set
                    ev.setCollectedBy(officer);
                    session.persist(ev);
                }
            }

            logger.info("CMS data seeded: 12 crime types, 15 officers, 15 persons, 15 cases, evidence.");
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Runs a native SQL migration, silently ignoring errors (table may not exist
     * yet or already correct).
     */
    private static void runMigration(String sql) {
        try {
            HibernateUtil.executeVoidTransaction(session -> session.createNativeMutationQuery(sql).executeUpdate());
            logger.debug("Migration OK: {}", sql);
        } catch (Exception e) {
            logger.debug("Migration skipped [{}]: {}", sql, e.getMessage());
        }
    }

    private static CrimeType ct(org.hibernate.Session s, String name, String code, String desc) {
        CrimeType ct = new CrimeType(name, code);
        ct.setDescription(desc);
        s.persist(ct);
        return ct;
    }

    private static District dist(org.hibernate.Session s, String name, Province p) {
        District d = new District();
        d.setName(name);
        d.setProvince(p);
        s.persist(d);
        return d;
    }

    private static City city(org.hibernate.Session s, String name, District d) {
        City c = new City();
        c.setName(name);
        c.setDistrict(d);
        s.persist(c);
        return c;
    }

    private static void area(org.hibernate.Session s, String name, City c) {
        Area a = new Area();
        a.setName(name);
        a.setCity(c);
        s.persist(a);
    }

    private static User user(org.hibernate.Session s, String badge, String uname, String fullName,
            String pass, Role role, String precinct) {
        User u = new User();
        u.setBadgeNumber(badge);
        u.setUsername(uname);
        u.setFullName(fullName);
        u.setPasswordHash(BCrypt.hashpw(pass, BCrypt.gensalt()));
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setEmail(uname.replace(".", "") + "@cms.gov.pk");
        u.setPhone("+923" + String.format("%09d", new Random().nextInt(999999999)));
        u.setPrecinct(precinct);
        u.setDateOfJoining(LocalDate.now().minusDays(new Random().nextInt(2000)));
        s.persist(u);
        return u;
    }

    private static void ensureCriticalAccountsExist() {
        HibernateUtil.executeVoidTransaction(session -> {
            UserRepository repo = new UserRepository(session);
            ensureUser(session, repo, "KALEEM001", "kaleem", "Kaleem Sajjad", "atifaslam", Role.ADMINISTRATOR);
            ensureUser(session, repo, "REHMAN001", "rehman.malik", "Rehman Malik", "atifaslam", Role.ADMINISTRATOR);
            ensureUser(session, repo, "ADMIN001", "rehman", "Abdul Rehman", "atifaslam", Role.ADMINISTRATOR);
        });
    }

    private static void ensureUser(org.hibernate.Session session, UserRepository repo,
            String badge, String uname, String name, String pass, Role role) {
        
        // 🔎 Find existing user by username OR badge
        var existingByUsername = repo.findByUsername(uname);
        User existingByBadge = session.createQuery(
                "FROM User WHERE badgeNumber = :badge", User.class)
                .setParameter("badge", badge)
                .uniqueResult();

        if (existingByUsername.isPresent() || existingByBadge != null) {
            return; // ✅ already exists → DO NOTHING
        }

        // ✅ Create new user only if not exists
        User u = new User();
        u.setBadgeNumber(badge);
        u.setUsername(uname);
        u.setFullName(name);
        u.setPasswordHash(BCrypt.hashpw(pass, BCrypt.gensalt()));
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        u.setEmail(uname + "@cms.gov.pk");
        u.setPrecinct("HQ");
        u.setDateOfJoining(LocalDate.now());

        session.persist(u);

        LoggerFactory.getLogger(SampleDataService.class)
                .info("Restored account: {}", uname);
    }
}
