package com.cms.service.ai;

import com.cms.model.CaseFile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.*;

/**
 * Computes similarity between cases based on shared attributes.
 */
public class SimilarityEngine {
    private final EntityManager em;

    public SimilarityEngine(EntityManager em) {
        this.em = em;
    }

    public List<CaseFile> findSimilarCases(CaseFile original) {
        if (original.getIncident() == null) return Collections.emptyList();

        String jpql = "SELECT cf FROM CaseFile cf JOIN cf.incident i WHERE i.crimeType = :crimeType " +
                      "AND cf.id != :caseId";
        
        TypedQuery<CaseFile> query = em.createQuery(jpql, CaseFile.class);
        query.setParameter("crimeType", original.getIncident().getCrimeType());
        query.setParameter("caseId", original.getId());
        
        List<CaseFile> results = query.getResultList();

        return results.stream()
            .limit(5)
            .toList();
    }
}
