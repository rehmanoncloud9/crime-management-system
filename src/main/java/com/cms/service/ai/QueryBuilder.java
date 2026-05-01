package com.cms.service.ai;

import com.cms.model.ai.ParsedQuery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.*;

/**
 * Generates dynamic JPQL queries based on ParsedQuery attributes.
 */
public class QueryBuilder {
    private final EntityManager em;

    public QueryBuilder(EntityManager em) {
        this.em = em;
    }

    public TypedQuery<Object> buildCriminalSearchQuery(ParsedQuery pq) {
        StringBuilder jpql = new StringBuilder("SELECT p FROM Person p WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        if (pq.hasAttribute("name")) {
            jpql.append(" AND (LOWER(p.firstName) LIKE :name OR LOWER(p.lastName) LIKE :name)");
            params.put("name", "%" + ((String) pq.getAttribute("name")).toLowerCase() + "%");
        }

        if (pq.hasAttribute("location")) {
            jpql.append(" AND LOWER(p.address) LIKE :loc");
            params.put("loc", "%" + ((String) pq.getAttribute("location")).toLowerCase() + "%");
        }

        // status is an ENUM (PersonStatus)
        if (pq.hasAttribute("status")) {
            jpql.append(" AND CAST(p.personStatus AS string) = :status");
            params.put("status", pq.getAttribute("status"));
        }

        // Physical marks
        if (pq.hasAttribute("hasTattoo") || pq.hasAttribute("hasScar")) {
            jpql.append(" AND LOWER(p.distinguishingMarks) LIKE :marks");
            String mark = pq.hasAttribute("hasTattoo") ? "tattoo" : "scar";
            params.put("marks", "%" + mark + "%");
        }

        TypedQuery<Object> query = em.createQuery(jpql.toString(), Object.class);
        params.forEach(query::setParameter);
        return query;
    }

    public TypedQuery<Object> buildCaseSearchQuery(ParsedQuery pq) {
        StringBuilder jpql = new StringBuilder("SELECT cf FROM CaseFile cf JOIN cf.incident i WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        if (pq.hasAttribute("caseId")) {
            jpql.append(" AND cf.id = :id");
            params.put("id", pq.getAttribute("caseId"));
        }
        
        if (pq.hasAttribute("crimeType")) {
            jpql.append(" AND i.crimeType.name = :ctype");
            params.put("ctype", pq.getAttribute("crimeType"));
        }

        TypedQuery<Object> query = em.createQuery(jpql.toString(), Object.class);
        params.forEach(query::setParameter);
        return query;
    }
}
