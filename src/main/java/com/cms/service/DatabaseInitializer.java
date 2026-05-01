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
        String url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/cms_db?createDatabaseIfNotExist=true");
        String user = props.getProperty("db.username", "root");
        String pass = props.getProperty("db.password", "admin");

        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            
            logger.info("Running JDBC Schema Migrations...");

            // --- USER & RBAC ---
            stmt.execute("ALTER TABLE users MODIFY COLUMN role VARCHAR(30) NOT NULL");
            stmt.execute("CREATE TABLE IF NOT EXISTS roles (id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, name VARCHAR(50) NOT NULL UNIQUE, description VARCHAR(255)) ENGINE=InnoDB");
            stmt.execute("CREATE TABLE IF NOT EXISTS permissions (id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE, description VARCHAR(255)) ENGINE=InnoDB");
            stmt.execute("CREATE TABLE IF NOT EXISTS role_permissions (role_id INT UNSIGNED NOT NULL, permission_id INT UNSIGNED NOT NULL, PRIMARY KEY (role_id, permission_id), FOREIGN KEY (role_id) REFERENCES roles(id), FOREIGN KEY (permission_id) REFERENCES permissions(id)) ENGINE=InnoDB");

            // --- PERSONS & SOFT DELETE ---
            stmt.execute("ALTER TABLE persons MODIFY COLUMN gender VARCHAR(20)");
            stmt.execute("ALTER TABLE persons ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL");
            stmt.execute("ALTER TABLE persons ADD COLUMN IF NOT EXISTS national_id VARCHAR(20) UNIQUE");

            // --- CASES & WORKFLOW ---
            stmt.execute("ALTER TABLE case_files MODIFY COLUMN status VARCHAR(50) NOT NULL");
            stmt.execute("ALTER TABLE case_files ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_case_investigator ON case_files (primary_investigator_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_case_status_deleted ON case_files (status, deleted_at)");

            // --- GEO ---
            stmt.execute("ALTER TABLE areas ADD COLUMN IF NOT EXISTS latitude DECIMAL(10,7), ADD COLUMN IF NOT EXISTS longitude DECIMAL(10,7)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_area_geo ON areas (latitude, longitude)");

            // --- MEDIA ---
            stmt.execute("CREATE TABLE IF NOT EXISTS media_files (id BIGINT AUTO_INCREMENT PRIMARY KEY, entity_type VARCHAR(50) NOT NULL, entity_id BIGINT NOT NULL, file_name VARCHAR(255) NOT NULL, storage_uri VARCHAR(1000) NOT NULL, mime_type VARCHAR(100), file_size_bytes BIGINT, uploaded_by BIGINT, uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP)");

            logger.info("JDBC Bootstrap successful.");
        } catch (Exception e) {
            logger.warn("JDBC Bootstrap encountered issues (likely already applied): {}", e.getMessage());
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
        HibernateUtil.executeVoidTransaction(session -> {
            long adminCount = session.createQuery("SELECT COUNT(u) FROM User u WHERE u.username = 'admin'", Long.class).uniqueResult();
            if (adminCount == 0) {
                logger.info("No admin user found. Creating default security principal 'admin'...");
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(BCrypt.hashpw("admin123", BCrypt.gensalt()));
                admin.setFirstName("System");
                admin.setLastName("Administrator");
                admin.setBadgeNumber("SYS-001");
                admin.setRole(Role.ADMINISTRATOR);
                admin.setStatus(UserStatus.ACTIVE);
                admin.setMustChangePassword(true); // 🛡️ Security Requirement
                session.persist(admin);
                logger.info("Default administrator 'admin' / 'admin123' initialized. PASSWORD CHANGE REQUIRED ON FIRST LOGIN.");
            }
        });
    }
}
