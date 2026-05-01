package com.cms.service;

import com.cms.model.*;
import com.cms.model.enums.*;
import com.cms.model.geo.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.*;

/**
 * Enterprise-grade database initializer (Mini-Migration Engine).
 * 1. Runs Schema Migrations via JDBC (Bootstrap).
 * 2. Seeds Mandatory Lookup Data (Reference Data).
 * 3. Ensures Default Security Baseline.
 */
public class DatabaseInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    private static final Properties props = HibernateUtil.getDbProperties();

    public static void initialize() {
        logger.info(">>> [CMS-INIT] Starting Database Initialization...");
        
        // Phase 1: JDBC Bootstrap (Schema Migrations)
        runJdbcBootstrap();
        
        // Phase 2: Mandatory Reference Data (Lookups)
        seedReferenceData();
        
        // Phase 3: Security Baseline (Admin Account)
        ensureAdminAccount();
        
        logger.info(">>> [CMS-INIT] Database Initialization Complete.");
    }

    private static void runJdbcBootstrap() {
        // We now favor the schema_enhanced.sql for the baseline.
        // This method is kept for minor hotfixes or ensuring the DB exists.
        String url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/?createDatabaseIfNotExist=true");
        String user = props.getProperty("db.username", "root");
        String pass = props.getProperty("db.password", "admin");

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS cms_db");
            logger.info("JDBC Bootstrap: Database 'cms_db' confirmed.");
        } catch (Exception e) {
            logger.error("JDBC Bootstrap failed: {}", e.getMessage());
        }
    }

    private static void seedReferenceData() {
        HibernateUtil.executeVoidTransaction(session -> {
            // 1. Roles
            String[] roles = {"ADMINISTRATOR", "SUPERVISOR", "OFFICER", "ANALYST", "RECORDS_CLERK", "LEGAL_OFFICER"};
            for (String r : roles) {
                session.createNativeQuery("INSERT IGNORE INTO roles (name) VALUES (:n)")
                        .setParameter("n", r).executeUpdate();
            }

            // 2. Mandatory Geography Check
            long distCount = session.createQuery("SELECT COUNT(d) FROM District d", Long.class).uniqueResult();
            if (distCount == 0) {
                logger.info("Seeding mandatory geography data...");
                Country pk = new Country(); pk.setName("Pakistan"); pk.setCode("PK"); session.persist(pk);
                Province punjab = new Province(); punjab.setName("Punjab"); punjab.setCountry(pk); session.persist(punjab);
                
                String[] districts = {"Lahore", "Faisalabad", "Multan", "Rawalpindi", "Gujranwala", "Sargodha", "Sialkot", "Bahawalpur", "Sahiwal", "Sheikhupura"};
                for (String dName : districts) {
                    District d = new District(); d.setName(dName); d.setProvince(punjab); session.persist(d);
                    City c = new City(); c.setName(dName + " City"); c.setDistrict(d); session.persist(c);
                    Area a = new Area(); a.setName(dName + " Center"); a.setCity(c); session.persist(a);
                }
            }

            // 3. Crime Types
            String[] crimes = {"ROBBERY", "MURDER", "CYBER_FRAUD", "KIDNAPPING", "ASSAULT", "EXTORTION", "NARCOTICS", "TERRORISM"};
            for (String c : crimes) {
                session.createNativeQuery("INSERT IGNORE INTO crime_types (name, code) VALUES (:n, :c)")
                        .setParameter("n", c).setParameter("c", c.substring(0,3)).executeUpdate();
            }
            
            // 4. Warrant/Hearing Types
            session.createNativeQuery("INSERT IGNORE INTO warrant_types (name) VALUES ('SEARCH'), ('ARREST'), ('BENCH')").executeUpdate();
            session.createNativeQuery("INSERT IGNORE INTO hearing_types (name) VALUES ('PRELIMINARY'), ('TRIAL'), ('SENTENCING'), ('BAIL')").executeUpdate();
        });
    }

    private static void ensureAdminAccount() {
        try {
            HibernateUtil.executeVoidTransaction(session -> {
                long adminCount = session.createQuery("SELECT COUNT(u) FROM User u WHERE u.username = 'admin'", Long.class).uniqueResult();
                System.out.println(">>> [CMS-INIT] Admin count: " + adminCount);
                logger.info("Admin count: {}", adminCount);
                
                if (adminCount == 0) {
                    logger.info("No admin user found. Creating default security principal 'admin'...");
                    System.out.println(">>> [CMS-INIT] Creating admin user...");
                    
                    try {
                        // 1. Create the Person base record (Mandatory for ISA relationship)
                        Person adminPerson = new Person();
                        adminPerson.setFirstName("System");
                        adminPerson.setLastName("Administrator");
                        adminPerson.setIdentified(true);
                        adminPerson.setPersonStatus(PersonStatus.OFFICER);
                        session.persist(adminPerson);
                        session.flush();
                        System.out.println(">>> [CMS-INIT] Person created with ID: " + adminPerson.getId());
                        
                        // 2. Create the User account linked to that Person
                        User admin = new User();
                        admin.setUsername("admin");
                        admin.setPasswordHash(BCrypt.hashpw("admin123", BCrypt.gensalt()));
                        admin.setPerson(adminPerson);
                        admin.setBadgeNumber("SYS-001");
                        admin.setRole(Role.ADMINISTRATOR);
                        admin.setStatus(UserStatus.ACTIVE);
                        admin.setMustChangePassword(true);
                        
                        session.persist(admin);
                        session.flush();
                        System.out.println(">>> [CMS-INIT] SUCCESS: Admin 'admin' created with ID: " + admin.getId());
                        logger.info("Default administrator 'admin' / 'admin123' initialized with linked Person record.");
                    } catch (Exception e) {
                        System.err.println(">>> [CMS-INIT] ERROR creating admin: " + e.getMessage());
                        logger.error("Failed to create admin user", e);
                        throw e;
                    }
                } else {
                    System.out.println(">>> [CMS-INIT] Admin already exists (Count: " + adminCount + ")");
                    logger.info("Admin account already exists (Count: {}).", adminCount);
                }
                
                // Final verification
                List<String> usernames = session.createQuery("SELECT u.username FROM User u", String.class).getResultList();
                logger.info("[CMS-INIT] Users in database: {}", usernames);
                System.out.println(">>> [CMS-INIT] Users in database: " + usernames);
            });
        } catch (Exception e) {
            System.err.println(">>> [CMS-INIT] CRITICAL ERROR in ensureAdminAccount: " + e.getMessage());
            logger.error("Critical error in ensureAdminAccount", e);
            throw new RuntimeException("Failed to ensure admin account", e);
        }
    }
}
