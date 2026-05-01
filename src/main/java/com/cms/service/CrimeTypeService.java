package com.cms.service;

import com.cms.model.CrimeType;
import com.cms.repository.CrimeTypeRepository;
import java.util.List;

public class CrimeTypeService {

    public List<CrimeType> findAll() {
        return HibernateUtil.executeTransaction(session -> {
            CrimeTypeRepository repo = new CrimeTypeRepository(session);
            return repo.findAll();
        });
    }

    public void save(CrimeType crimeType) {
        HibernateUtil.executeTransaction(session -> {
            if (crimeType.getId() == null) {
                session.persist(crimeType);
            } else {
                session.merge(crimeType);
            }
            return null;
        });
    }

    public void delete(CrimeType crimeType) {
        HibernateUtil.executeTransaction(session -> {
            CrimeTypeRepository repo = new CrimeTypeRepository(session);
            var opt = repo.findById(crimeType.getId());
            if (opt.isPresent()) {
                session.remove(opt.get());
            }
            return null;
        });
    }

    public CrimeType findCrimeType(String searchTerm) {
        try (var session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM CrimeType WHERE LOWER(name) LIKE :term OR LOWER(code) LIKE :term", CrimeType.class)
                    .setParameter("term", "%" + searchTerm.toLowerCase() + "%")
                    .uniqueResult();
        }
    }

    public String getCrimeTypeInfo(CrimeType ct) {
        if (ct == null) return "Crime type information not available.";

        StringBuilder sb = new StringBuilder();
        sb.append("📋 **").append(ct.getName()).append("** (").append(ct.getCategory()).append(")\n\n");
        sb.append("**Description**: ").append(ct.getDescription()).append("\n");
        
        if (ct.getExamples() != null) {
            sb.append("**Examples**: ").append(ct.getExamples()).append("\n");
        }
        
        if (ct.getInvestigationTips() != null) {
            sb.append("**Investigation Tips**: ").append(ct.getInvestigationTips()).append("\n");
        }
        
        if (ct.getLegalReference() != null) {
            sb.append("**Legal Reference**: ").append(ct.getLegalReference()).append("\n");
        }

        return sb.toString();
    }
}
