package com.cms.service;

import com.cms.model.*;
import com.cms.model.enums.*;
import com.cms.model.geo.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        String dbUrl = props.getProperty("db.url", "jdbc:mysql://localhost:3306/cms_db");
        String user = props.getProperty("db.username", "root");
        String pass = props.getProperty("db.password", "");

        String dbName = extractDatabaseName(dbUrl);
        String serverUrl = buildServerUrl(dbUrl);

        try (Connection conn = DriverManager.getConnection(serverUrl, user, pass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE IF NOT EXISTS " + dbName);
            logger.info("JDBC Bootstrap: Database '{}' confirmed.", dbName);
        } catch (Exception e) {
            logger.error("JDBC Bootstrap failed: {}", e.getMessage());
            return;
        }

        applySchemaIfMissing(dbUrl, user, pass, dbName);
    }

    private static void applySchemaIfMissing(String dbUrl, String user, String pass, String dbName) {
        try (Connection conn = DriverManager.getConnection(dbUrl, user, pass)) {
            if (schemaExists(conn, dbName)) {
                logger.info("Schema already present. Skipping auto-apply.");
                return;
            }

            Path schemaPath = Paths.get(System.getProperty("user.dir"), "schema_enhanced.sql");
            if (!Files.exists(schemaPath)) {
                logger.warn("Schema file not found at {}", schemaPath);
                return;
            }

            String sql = Files.readString(schemaPath, StandardCharsets.UTF_8);
            executeSqlScript(conn, sql);
            logger.info("Schema auto-apply completed.");
        } catch (Exception e) {
            logger.error("Schema auto-apply failed", e);
        }
    }

    private static boolean schemaExists(Connection conn, String dbName) {
        String sql = "SELECT COUNT(*) FROM information_schema.tables " +
                     "WHERE table_schema = ? AND table_name IN (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dbName);
            ps.setString(2, "users");
            ps.setString(3, "crime_incidents");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (Exception e) {
            logger.warn("Schema check failed", e);
            return false;
        }
    }

    private static void executeSqlScript(Connection conn, String script) throws java.sql.SQLException {
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        boolean inBlockComment = false;

        try (Statement stmt = conn.createStatement()) {
            for (String rawLine : script.split("\n")) {
                String line = rawLine.trim();

                if (inBlockComment) {
                    if (line.endsWith("*/")) {
                        inBlockComment = false;
                    }
                    continue;
                }

                if (line.startsWith("/*")) {
                    if (!line.endsWith("*/")) {
                        inBlockComment = true;
                    }
                    continue;
                }

                if (line.isEmpty() || line.startsWith("--") || line.startsWith("*")) {
                    continue;
                }

                if (line.toUpperCase(Locale.ROOT).startsWith("DELIMITER")) {
                    delimiter = line.substring("DELIMITER".length()).trim();
                    statement.setLength(0);
                    continue;
                }

                statement.append(rawLine).append('\n');

                if (line.endsWith(delimiter)) {
                    String sql = statement.toString().trim();
                    sql = sql.substring(0, sql.lastIndexOf(delimiter)).trim();
                    if (!sql.isBlank()) stmt.execute(sql);
                    statement.setLength(0);
                }
            }

            String remaining = statement.toString().trim();
            if (!remaining.isBlank()) stmt.execute(remaining);
        }
    }

    private static String extractDatabaseName(String dbUrl) {
        try {
            String withoutJdbc = dbUrl.substring("jdbc:".length());
            java.net.URI uri = java.net.URI.create(withoutJdbc);
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                String name = path.substring(1);
                int idx = name.indexOf('?');
                return idx >= 0 ? name.substring(0, idx) : name;
            }
        } catch (Exception ignore) { }
        int slash = dbUrl.lastIndexOf('/');
        if (slash >= 0) {
            String tail = dbUrl.substring(slash + 1);
            int q = tail.indexOf('?');
            return q >= 0 ? tail.substring(0, q) : tail;
        }
        return "cms_db";
    }

    private static String buildServerUrl(String dbUrl) {
        try {
            String withoutJdbc = dbUrl.substring("jdbc:".length());
            java.net.URI uri = java.net.URI.create(withoutJdbc);
            String host = uri.getHost();
            int port = uri.getPort();
            String query = uri.getQuery();
            String base = "jdbc:mysql://" + host + (port > 0 ? ":" + port : "") + "/";
            return query != null ? base + "?" + query : base;
        } catch (Exception ignore) { }
        return "jdbc:mysql://localhost:3306/";
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
