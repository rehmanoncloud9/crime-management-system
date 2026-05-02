package com.cms.service;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class HibernateUtil {

    private static final Logger logger = LoggerFactory.getLogger(HibernateUtil.class);

    private static volatile SessionFactory sessionFactory;
    private static final Properties dbProperties = loadDbProperties();

    private HibernateUtil() {}

    public static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            synchronized (HibernateUtil.class) {
                if (sessionFactory == null) {
                    sessionFactory = buildSessionFactory();
                }
            }
        }
        return sessionFactory;
    }

    private static SessionFactory buildSessionFactory() {
        try {
            Configuration configuration = new Configuration().configure();

            // Override values from db.properties if available
            if (!dbProperties.isEmpty()) {
                if (dbProperties.containsKey("db.url")) configuration.setProperty("hibernate.connection.url", dbProperties.getProperty("db.url"));
                if (dbProperties.containsKey("db.username")) configuration.setProperty("hibernate.connection.username", dbProperties.getProperty("db.username"));
                if (dbProperties.containsKey("db.password")) configuration.setProperty("hibernate.connection.password", dbProperties.getProperty("db.password"));
                if (dbProperties.containsKey("db.driver")) configuration.setProperty("hibernate.connection.driver_class", dbProperties.getProperty("db.driver"));
            }
            
            logger.info("[DB-CONNECT] Using URL: {}", configuration.getProperty("hibernate.connection.url"));

            // Insecure logging removed for C-26
            SessionFactory sf = configuration.buildSessionFactory();
            return sf;

        } catch (Throwable ex) {
            logger.error("SessionFactory creation failed", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static Properties loadDbProperties() {
        Properties props = new Properties();
        Properties configProps = new Properties();

        loadProperties("db.properties", props);
        loadProperties("config.properties", configProps);

        String envUrl = System.getenv("CMS_DB_URL");
        String envUser = System.getenv("CMS_DB_USER");
        String envPass = System.getenv("CMS_DB_PASSWORD");

        if (envUrl != null && !envUrl.isBlank()) props.setProperty("db.url", envUrl.trim());
        if (envUser != null && !envUser.isBlank()) props.setProperty("db.username", envUser.trim());
        if (envPass != null) props.setProperty("db.password", envPass);

        applyDefaults(props, configProps);
        return props;
    }

    private static void loadProperties(String resourceName, Properties target) {
        try (InputStream input = HibernateUtil.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) {
                logger.error("[DB-CONFIG] Resource not found: {}", resourceName);
                return;
            }
            target.load(input);
            logger.info("[DB-CONFIG] Successfully loaded {} with {} keys.", resourceName, target.size());
        } catch (IOException e) {
            logger.warn("Failed to load {}", resourceName, e);
        }
    }

    private static void applyDefaults(Properties props, Properties configProps) {
        String host = configProps.getProperty("db.host", "localhost");
        String port = configProps.getProperty("db.port", "3306");
        String name = configProps.getProperty("db.name", "cms_db");
        String ssl = configProps.getProperty("db.ssl", "false");

        if (!props.containsKey("db.url")) {
            String params = "useSSL=" + ssl + "&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true";
            props.setProperty("db.url", "jdbc:mysql://" + host + ":" + port + "/" + name + "?" + params);
        }

        if (!props.containsKey("db.username")) {
            props.setProperty("db.username", configProps.getProperty("db.user", "root"));
        }

        if (!props.containsKey("db.password")) {
            // Read from config.properties — no hardcoded fallback. Set db.password in db.properties.
            props.setProperty("db.password", configProps.getProperty("db.password", ""));
        }

        if (!props.containsKey("db.driver")) {
            props.setProperty("db.driver", "com.mysql.cj.jdbc.Driver");
        }
    }

    public static Properties getDbProperties() {
        return dbProperties;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            synchronized (HibernateUtil.class) {
                if (sessionFactory != null) {
                    sessionFactory.close();
                    sessionFactory = null;
                }
            }
        }
    }

    public static <T> T executeTransaction(java.util.function.Function<org.hibernate.Session, T> action) {
        org.hibernate.Session session = getSessionFactory().openSession();
        org.hibernate.Transaction tx = null;

        try {
            tx = session.beginTransaction();

            T result = action.apply(session);

            tx.commit();
            return result;

        } catch (RuntimeException e) {
            if (tx != null && tx.isActive()) {
                try {
                    tx.rollback();
                } catch (Exception rollbackEx) {
                    logger.error("Rollback failed", rollbackEx);
                }
            }
            throw e;
        } finally {
            if (session != null && session.isOpen()) {
                session.close();
            }
        }
    }

    public static void executeVoidTransaction(java.util.function.Consumer<org.hibernate.Session> action) {
        executeTransaction((org.hibernate.Session session) -> {
            action.accept(session);
            return null;
        });
    }
}
