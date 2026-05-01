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
        System.out.println(">>> [CMS-INIT] Starting Database Initialization...");
        logger.info(">>> [CMS-INIT] Starting Database Initialization...");
        
        try {
            // Phase 1: JDBC Bootstrap (Schema Migrations)
            runJdbcBootstrap();
            
            // Phase 2: Mandatory Reference Data (Lookups)
            seedReferenceData();
            
            // Phase 3: Security Baseline (Admin Account)
            ensureAdminAccount();
            
            logger.info(">>> [CMS-INIT] Database Initialization Complete.");
        } catch (Exception e) {
            logger.error("Database initialization failed", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private static void runJdbcBootstrap() {
        // We now favor the schema_enhanced.sql for the baseline.
        // This method is kept for minor hotfixes or ensuring the DB exists.
        String url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/?createDatabaseIfNotExist=true");
        String user = props.getProperty("db.username", "root");
        String pass = props.getProperty("db.password", "");

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
            String url = props.getProperty("db.url", "jdbc:mysql://localhost:3306/cms_db");
            String user = props.getProperty("db.username", "root");
            String pass = props.getProperty("db.password", "");
            
            try (Connection conn = DriverManager.getConnection(url, user, pass);
                 Statement stmt = conn.createStatement()) {
                
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM users WHERE username = 'admin'");
                rs.next();
                long adminCount = rs.getLong(1);
                
                if (adminCount == 0) {
                    String passwordHash = BCrypt.hashpw("admin123", BCrypt.gensalt());
                    
                    stmt.executeUpdate(
                        "INSERT INTO persons (first_name, last_name, is_identified, person_status, created_at, updated_at) " +
                        "VALUES ('System', 'Administrator', true, 'OFFICER', NOW(), NOW())"
                    );
                    
                    var rs2 = stmt.executeQuery("SELECT id FROM persons ORDER BY id DESC LIMIT 1");
                    rs2.next();
                    long personId = rs2.getLong(1);
                    
                    var ps = conn.prepareStatement(
                        "INSERT INTO users (badge_number, username, password_hash, person_id, role, status, must_change_password, created_at, updated_at) " +
                        "VALUES ('SYS-001', 'admin', ?, ?, 'ADMINISTRATOR', 'ACTIVE', true, NOW(), NOW())"
                    );
                    ps.setString(1, passwordHash);
                    ps.setLong(2, personId);
                    ps.executeUpdate();
                    
                    logger.info("Default administrator 'admin' initialized.");
                }
            }
        } catch (Exception e) {
            logger.error("Error in ensureAdminAccount", e);
        }
    }
}
