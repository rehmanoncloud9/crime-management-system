package com.cms.service;

import com.cms.model.CaseFile;
import com.cms.model.Person;
import com.cms.model.enums.RiskScore;
import com.cms.repository.CaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.Classifier;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class AIService {

    private static final Logger logger = LoggerFactory.getLogger(AIService.class);

    private Properties config;

    private Classifier riskModel;
    private Classifier recidivismModel;
    private Instances datasetStructure;

    public AIService() {
        this.config = HibernateUtil.getDbProperties();
        initModels();
    }

    // For Testing
    public AIService(Properties config) {
        this.config = config != null ? config : new Properties();
        initModels();
    }

    private void initModels() {
        String modelPath = config.getProperty("ai.model.path",
                System.getProperty("user.home") + "/cms/models/");

        modelPath = modelPath.replace("${user.home}", System.getProperty("user.home"));

        try {
            File riskFile = new File(modelPath + "risk_model.model");
            if (riskFile.exists()) {
                riskModel = (Classifier) SerializationHelper.read(riskFile.getAbsolutePath());
                logger.info("Loaded Risk Model");
            }

            File recFile = new File(modelPath + "recidivism_model.model");
            if (recFile.exists()) {
                recidivismModel = (Classifier) SerializationHelper.read(recFile.getAbsolutePath());
                logger.info("Loaded Recidivism Model");
            }

            datasetStructure = buildDataset();

        } catch (Exception e) {
            logger.error("Model initialization failed", e);
        }
    }

    private Instances buildDataset() {
        ArrayList<Attribute> attributes = new ArrayList<>();

        attributes.add(new Attribute("priorCases"));
        attributes.add(new Attribute("hasWarrant"));
        attributes.add(new Attribute("hasGangAffiliation"));

        ArrayList<String> classVals = new ArrayList<>();
        classVals.add("LOW");
        classVals.add("MEDIUM");
        classVals.add("HIGH");
        classVals.add("CRITICAL");

        attributes.add(new Attribute("riskClass", classVals));

        Instances data = new Instances("Inference", attributes, 0);
        data.setClassIndex(data.numAttributes() - 1);

        return data;
    }

    public int calculateRiskScore(Person person) {
        if (person == null) return 0;

        try {
            if (riskModel != null) {
                Instance instance = createInstance(person);
                double[] dist = riskModel.distributionForInstance(instance);

                validateDistribution(dist);

                return (int) (
                        dist[1] * 33 +
                                dist[2] * 66 +
                                dist[3] * 100
                );
            }
        } catch (Exception e) {
            logger.warn("Model inference failed, using fallback", e);
        }

        return fallbackRiskScore(person);
    }

    private int fallbackRiskScore(Person person) {
        int score = 0;

        if (person.isHasActiveWarrant()) score += 40;

        if (person.getGangAffiliation() != null && !person.getGangAffiliation().isBlank()) {
            score += 20;
        }

        int pastCases = getPastCasesCount(person);

        score += pastCases * 10;

        return Math.min(score, 100);
    }

    public double predictRecidivism(Person person) {
        if (person == null) return 0.0;

        try {
            if (recidivismModel != null) {
                Instance instance = createInstance(person);
                return recidivismModel.classifyInstance(instance);
            }
        } catch (Exception e) {
            logger.warn("Recidivism model failed", e);
        }

        return calculateRiskScore(person) / 100.0;
    }

    private Instance createInstance(Person person) {
        Instance instance = new DenseInstance(datasetStructure.numAttributes());
        instance.setDataset(datasetStructure);

        instance.setValue(0, getPastCasesCount(person));
        instance.setValue(1, person.isHasActiveWarrant() ? 1.0 : 0.0);
        instance.setValue(2, hasGangAffiliation(person) ? 1.0 : 0.0);

        instance.setMissing(3);

        return instance;
    }

    private boolean hasGangAffiliation(Person person) {
        return person.getGangAffiliation() != null && !person.getGangAffiliation().isBlank();
    }

    private int getPastCasesCount(Person person) {
        return HibernateUtil.executeTransaction(session -> {
            Long count = session.createQuery(
                            "SELECT COUNT(c) FROM CaseFile c WHERE c.suspect.id = :id",
                            Long.class)
                    .setParameter("id", person.getId())
                    .getSingleResult();

            return count != null ? count.intValue() : 0;
        });
    }

    private void validateDistribution(double[] dist) {
        if (dist == null || dist.length < 4) {
            throw new IllegalStateException("Invalid model output");
        }
    }

    public RiskScore mapToEnum(int score) {
        if (score < 30) return RiskScore.LOW;
        if (score < 60) return RiskScore.MEDIUM;
        if (score < 85) return RiskScore.HIGH;
        return RiskScore.CRITICAL;
    }

    public List<CaseFile> findSimilarCases(CaseFile target) {

        if (target == null || target.getIncident() == null ||
                target.getIncident().getTitle() == null) {
            return Collections.emptyList();
        }

        String[] tokens = target.getIncident()
                .getTitle()
                .toLowerCase()
                .split("\\s+");

        return HibernateUtil.executeTransaction(session -> {
            CaseRepository caseRepository = new CaseRepository(session);
            return caseRepository.findAll(1000, 0).stream()
                    .filter(cf -> cf.getId() != null && !cf.getId().equals(target.getId()))
                    .filter(cf -> cf.getIncident() != null && cf.getIncident().getTitle() != null)
                    .filter(cf -> matchesTokens(cf, tokens))
                    .limit(5)
                    .collect(Collectors.toList());
        });
    }

    private boolean matchesTokens(CaseFile cf, String[] tokens) {

        String title = cf.getIncident().getTitle().toLowerCase();
        int matches = 0;

        for (String token : tokens) {
            if (token.length() > 2 && title.contains(token)) {
                matches++;
            }
        }

        return matches >= Math.max(1, tokens.length / 2);
    }

    public Map<String, Long> analyzeMODistribution() {
        return HibernateUtil.executeTransaction(session -> {
            List<Object[]> results = session.createQuery(
                    "SELECT mo.tags, COUNT(mo) FROM ModusOperandi mo GROUP BY mo.tags",
                    Object[].class
            ).getResultList();

            return results.stream()
                    .filter(r -> r[0] != null)
                    .collect(Collectors.toMap(
                            r -> (String) r[0],
                            r -> (Long) r[1]
                    ));
        });
    }

    public List<String> checkForAnomalies() {

        List<String> alerts = new ArrayList<>();

        try {
            Long count = HibernateUtil.executeTransaction(session -> {
                LocalDateTime since = LocalDateTime.now().minusHours(24);
                return session.createQuery(
                                "SELECT COUNT(ci) FROM CrimeIncident ci WHERE ci.reportedAt >= :since",
                                Long.class)
                        .setParameter("since", since)
                        .getSingleResult();
            });

            if (count != null && count > 10) {
                alerts.add("Spike detected: " + count + " incidents in last 24h");
            }

        } catch (Exception e) {
            logger.error("Anomaly detection failed", e);
        }

        return alerts;
    }
}