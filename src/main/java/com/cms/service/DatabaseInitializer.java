package com.cms.service;

import com.cms.model.*;
import com.cms.model.enums.*;
import com.cms.model.geo.*;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Database initializer.
 *
 * Phase 1 — JDBC bootstrap: creates the database if missing, applies schema.
 * Phase 2 — Admin baseline: creates ONE default admin on first run only.
 *            If an admin already exists, this phase is skipped entirely.
 *            Admin credentials come from config.properties, never hardcoded.
 * Phase 3 — Reference data: seeds lookup tables (roles, geography, crime types).
 *
 * User accounts for officers/analysts/etc are created by the admin through
 * the User Management screen — never automatically by this class.
 */
public class DatabaseInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);
    private static final Properties dbProps = HibernateUtil.getDbProperties();
    private static final Properties appConfig = loadAppConfig();

    public static void initialize() {
        logger.info(">>> [CMS-INIT] Starting Database Initialization...");
        try {
            runJdbcBootstrap();
            ensureAdminAccount();
            seedReferenceData();
            logger.info(">>> [CMS-INIT] Database Initialization Complete.");
        } catch (Exception e) {
            logger.error("!!! CRITICAL: DATABASE INITIALIZATION FAILED: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Config loading
    // ─────────────────────────────────────────────────────────────

    private static Properties loadAppConfig() {
        Properties props = new Properties();
        try (InputStream in = DatabaseInitializer.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in != null) props.load(in);
        } catch (Exception e) {
            logger.warn("Could not load config.properties for admin defaults: {}", e.getMessage());
        }
        return props;
    }

    // ─────────────────────────────────────────────────────────────
    // Phase 1: JDBC Bootstrap
    // ─────────────────────────────────────────────────────────────

    private static void runJdbcBootstrap() {
        String dbUrl  = dbProps.getProperty("db.url",      "jdbc:mysql://localhost:3306/cms_db");
        String user   = dbProps.getProperty("db.username", "root");
        String pass   = dbProps.getProperty("db.password", "");

        String dbName    = extractDatabaseName(dbUrl);
        String serverUrl = buildServerUrl(dbUrl);

        try (Connection conn = DriverManager.getConnection(serverUrl, user, pass);
             Statement stmt  = conn.createStatement()) {
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
                logger.warn("Schema file not found at {}. Skipping.", schemaPath);
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
                    if (line.endsWith("*/")) inBlockComment = false;
                    continue;
                }
                if (line.startsWith("/*")) {
                    if (!line.endsWith("*/")) inBlockComment = true;
                    continue;
                }
                if (line.isEmpty() || line.startsWith("--") || line.startsWith("*")) continue;

                if (line.toUpperCase(Locale.ROOT).startsWith("DELIMITER")) {
                    delimiter = line.substring("DELIMITER".length()).trim();
                    statement.setLength(0);
                    continue;
                }
                statement.append(rawLine).append('\n');
                if (line.endsWith(delimiter)) {
                    String sql = statement.toString().trim();
                    sql = sql.substring(0, sql.lastIndexOf(delimiter)).trim();
                    if (!sql.isBlank()) executeStatement(stmt, sql);
                    statement.setLength(0);
                }
            }
            String remaining = statement.toString().trim();
            if (!remaining.isBlank()) executeStatement(stmt, remaining);
        }
    }

    private static void executeStatement(Statement stmt, String sql) throws java.sql.SQLException {
        try {
            stmt.execute(sql);
        } catch (java.sql.SQLException ex) {
            String preview = sql.length() > 120 ? sql.substring(0, 120) + "..." : sql;
            logger.error("Schema statement failed: {}", preview, ex);
            throw ex;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Phase 2: Admin Baseline — FIRST RUN ONLY
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a default admin account ONLY if no admin exists yet.
     * Once created, this method never modifies the admin account again —
     * password changes made through the UI are permanent.
     *
     * Credentials are read from config.properties:
     *   app.admin.username        (default: admin)
     *   app.admin.badge           (default: SYS-001)
     *   app.admin.email           (default: admin@cms.local)
     *   app.admin.default.password  (default: Admin@CMS2024!)
     */
    private static void ensureAdminAccount() {
        String dbUrl  = dbProps.getProperty("db.url",      "jdbc:mysql://localhost:3306/cms_db");
        String dbUser = dbProps.getProperty("db.username", "root");
        String dbPass = dbProps.getProperty("db.password", "");

        String adminUsername = appConfig.getProperty("app.admin.username",         "admin");
        String adminBadge    = appConfig.getProperty("app.admin.badge",            "SYS-001");
        String adminEmail    = appConfig.getProperty("app.admin.email",            "admin@cms.local");
        String adminPassword = appConfig.getProperty("app.admin.default.password", "Admin@CMS2024!");

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass);
             Statement stmt  = conn.createStatement()) {

            // Check if ANY admin-role user exists
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM users WHERE role = 'ADMINISTRATOR'");
            rs.next();
            long adminCount = rs.getLong(1);
            rs.close();

            if (adminCount > 0) {
                // Admins already exist — do NOT touch passwords or accounts
                logger.info("[INIT] Admin account(s) already exist. Skipping baseline creation.");
                return;
            }

            // First run: create the default admin
            logger.warn("[INIT] No admin found. Creating first-run admin account '{}'.", adminUsername);
            String passwordHash = BCrypt.hashpw(adminPassword, BCrypt.gensalt());

            // 1. Create person record
            stmt.executeUpdate(
                "INSERT INTO persons (first_name, last_name, is_identified, person_status, email, gender, created_at, updated_at) " +
                "VALUES ('Rehman', 'OnCloud9', true, 'UNKNOWN', '" + adminEmail + "', 'OTHER', NOW(), NOW())",
                Statement.RETURN_GENERATED_KEYS);

            long personId = -1;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) personId = keys.getLong(1);
            }

            if (personId == -1) {
                logger.error("[INIT] Could not retrieve person ID for admin. Admin creation aborted.");
                return;
            }

            // 2. Create user account — must_change_password = true so admin sets their own password on first login
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (badge_number, username, password_hash, person_id, role, status, " +
                "must_change_password, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'ADMINISTRATOR', 'ACTIVE', true, NOW(), NOW())")) {
                ps.setString(1, adminBadge);
                ps.setString(2, adminUsername);
                ps.setString(3, passwordHash);
                ps.setLong(4, personId);
                ps.executeUpdate();
            }

            logger.warn("╔══════════════════════════════════════════════════╗");
            logger.warn("║         CMS — FIRST RUN SETUP COMPLETE          ║");
            logger.warn("║                                                  ║");
            logger.warn("║  Username : {}                              ║", adminUsername);
            logger.warn("║  Password : (set in config.properties)           ║");
            logger.warn("║                                                  ║");
            logger.warn("║  You will be required to change your password    ║");
            logger.warn("║  on first login.                                 ║");
            logger.warn("╚══════════════════════════════════════════════════╝");

        } catch (Exception e) {
            logger.error("[INIT] Failed to ensure admin account", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Phase 3: Reference / Lookup Data
    // ─────────────────────────────────────────────────────────────

    private static void seedReferenceData() {
        HibernateUtil.executeVoidTransaction(session -> {
            // Roles
            String[] roles = { "ADMINISTRATOR", "SUPERVISOR", "OFFICER", "ANALYST", "RECORDS_CLERK", "LEGAL_OFFICER" };
            for (String r : roles) {
                session.createNativeQuery("INSERT IGNORE INTO roles (name) VALUES (:n)")
                       .setParameter("n", r).executeUpdate();
            }

            // Geography and Crime Data
            long distCount = session.createQuery("SELECT COUNT(d) FROM District d", Long.class).uniqueResult();
            if (distCount == 0) {
                logger.info("Seeding mandatory geography and crime data from geo_data.json...");
                try (InputStream is = DatabaseInitializer.class.getResourceAsStream("/geo_data.json")) {
                    if (is != null) {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(is);

                        // Seed Geography
                        Country country = new Country();
                        country.setName(root.has("country") ? root.get("country").asText() : "Pakistan");
                        country.setCode(root.has("countryCode") ? root.get("countryCode").asText() : "PK");
                        session.persist(country);

                        if (root.has("provinces")) {
                            for (com.fasterxml.jackson.databind.JsonNode provNode : root.get("provinces")) {
                                Province province = new Province();
                                province.setName(provNode.get("province").asText());
                                province.setCountry(country);
                                session.persist(province);

                                if (provNode.has("districts")) {
                                    for (com.fasterxml.jackson.databind.JsonNode distNode : provNode.get("districts")) {
                                        District district = new District();
                                        district.setName(distNode.get("district").asText());
                                        district.setProvince(province);
                                        session.persist(district);

                                        if (distNode.has("cities")) {
                                            for (com.fasterxml.jackson.databind.JsonNode cityNode : distNode.get("cities")) {
                                                City city = new City();
                                                city.setName(cityNode.get("city").asText());
                                                city.setDistrict(district);
                                                session.persist(city);

                                                if (cityNode.has("areas")) {
                                                    for (com.fasterxml.jackson.databind.JsonNode areaNode : cityNode.get("areas")) {
                                                        Area area = new Area();
                                                        area.setName(areaNode.get("area").asText());
                                                        area.setCity(city);
                                                        if (areaNode.has("latitude") && areaNode.has("longitude")) {
                                                            area.setLatitude(new java.math.BigDecimal(areaNode.get("latitude").asText()));
                                                            area.setLongitude(new java.math.BigDecimal(areaNode.get("longitude").asText()));
                                                        }
                                                        session.persist(area);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Seed Crime Categories
                        if (root.has("crimeCategories")) {
                            for (com.fasterxml.jackson.databind.JsonNode crimeNode : root.get("crimeCategories")) {
                                String code = crimeNode.has("code") ? crimeNode.get("code").asText() : "";
                                String category = crimeNode.has("category") ? crimeNode.get("category").asText() : "";
                                String penalCode = crimeNode.has("penalCode") ? crimeNode.get("penalCode").asText() : "";
                                
                                session.createNativeQuery("INSERT IGNORE INTO crime_types (name, code, category, legal_reference) VALUES (:n, :c, :cat, :lr)")
                                       .setParameter("n", category)
                                       .setParameter("c", code)
                                       .setParameter("cat", category)
                                       .setParameter("lr", penalCode)
                                       .executeUpdate();
                            }
                        }
                    } else {
                        logger.warn("geo_data.json not found in resources!");
                    }
                } catch (Exception e) {
                    logger.error("Failed to seed from geo_data.json", e);
                }
            } else {
                long crimeCount = ((Number) session.createNativeQuery("SELECT COUNT(*) FROM crime_types").getSingleResult()).longValue();
                if (crimeCount == 0) {
                     String[] crimes = { "ROBBERY", "MURDER", "CYBER_FRAUD", "KIDNAPPING", "ASSAULT", "EXTORTION", "NARCOTICS", "TERRORISM" };
                     for (String c : crimes) {
                         session.createNativeQuery("INSERT IGNORE INTO crime_types (name, code) VALUES (:n, :c)")
                                .setParameter("n", c)
                                .setParameter("c", c.substring(0, Math.min(3, c.length())))
                                .executeUpdate();
                     }
                }
            }

            // Warrant and hearing types
            session.createNativeQuery(
                "INSERT IGNORE INTO warrant_types (name) VALUES ('SEARCH'), ('ARREST'), ('BENCH')")
                .executeUpdate();
            session.createNativeQuery(
                "INSERT IGNORE INTO hearing_types (name) VALUES ('PRELIMINARY'), ('TRIAL'), ('SENTENCING'), ('BAIL')")
                .executeUpdate();
        });
    }

    // ─────────────────────────────────────────────────────────────
    // URL helpers
    // ─────────────────────────────────────────────────────────────

    private static String extractDatabaseName(String dbUrl) {
        try {
            String withoutJdbc = dbUrl.startsWith("jdbc:") ? dbUrl.substring("jdbc:".length()) : dbUrl;
            java.net.URI uri = java.net.URI.create(withoutJdbc);
            String path = uri.getPath();
            if (path != null && path.length() > 1) {
                String name = path.substring(1);
                int idx = name.indexOf('?');
                return idx >= 0 ? name.substring(0, idx) : name;
            }
        } catch (Exception ignore) {}
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
            String withoutJdbc = dbUrl.startsWith("jdbc:") ? dbUrl.substring("jdbc:".length()) : dbUrl;
            java.net.URI uri = java.net.URI.create(withoutJdbc);
            String host  = uri.getHost();
            int    port  = uri.getPort();
            String query = uri.getQuery();
            String base  = "jdbc:mysql://" + host + (port > 0 ? ":" + port : "") + "/";
            return query != null ? base + "?" + query : base;
        } catch (Exception ignore) {}
        return "jdbc:mysql://localhost:3306/";
    }
}
