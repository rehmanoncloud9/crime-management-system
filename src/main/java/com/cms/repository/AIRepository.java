package com.cms.repository;

import com.cms.model.AIAnalysisResult;
import com.cms.model.ModusOperandi;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class AIRepository {

    private final EntityManager entityManager;

    public AIRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<AIAnalysisResult> findBySubject(String type, Long id) {
        TypedQuery<AIAnalysisResult> query = entityManager.createQuery(
                "SELECT a FROM AIAnalysisResult a WHERE a.subjectType = :type AND a.subjectId = :id ORDER BY a.id DESC",
                AIAnalysisResult.class
        );

        query.setParameter("type", type);
        query.setParameter("id", id);

        return query.getResultList();
    }

    public List<ModusOperandi> findByPerson(Long personId) {
        TypedQuery<ModusOperandi> query = entityManager.createQuery(
                "SELECT m FROM ModusOperandi m WHERE m.person.id = :id",
                ModusOperandi.class
        );

        query.setParameter("id", personId);

        return query.getResultList();
    }

    public void saveAnalysis(AIAnalysisResult result) {
        entityManager.persist(result);
    }

    public void saveMO(ModusOperandi mo) {
        entityManager.persist(mo);
    }
}