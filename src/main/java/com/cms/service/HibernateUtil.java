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
            }

            // Insecure logging removed for C-26
            System.out.println(">>> [CMS-BOOT] Initializing Hibernate SessionFactory...");
            logger.info("Building SessionFactory...");

            SessionFactory sf = configuration.buildSessionFactory();
            System.out.println(">>> [CMS-BOOT] SessionFactory created successfully!");
            return sf;

        } catch (Throwable ex) {
            logger.error("SessionFactory creation failed", ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    private static Properties loadDbProperties() {
        Properties props = new Properties();

        try (InputStream input = HibernateUtil.class.getClassLoader()
                .getResourceAsStream("db.properties")) {

            if (input == null) {
                logger.warn("db.properties not found. Using hibernate.cfg.xml defaults.");
                return props;
            }

            props.load(input);

        } catch (IOException e) {
            logger.error("Failed to load db.properties", e);
        }

        return props;
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