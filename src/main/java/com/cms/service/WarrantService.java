package com.cms.service;

import com.cms.model.Warrant;
import com.cms.repository.WarrantRepository;
import java.util.List;

public class WarrantService {
    public List<Warrant> findAll(int limit, int offset) {
        return HibernateUtil.executeTransaction(session -> {
            WarrantRepository repo = new WarrantRepository(session);
            return repo.findAll(limit, offset);
        });
    }

    public List<Warrant> findActiveByPerson(Long personId, int limit, int offset) {
        return HibernateUtil.executeTransaction(session -> {
            WarrantRepository repo = new WarrantRepository(session);
            return repo.findActiveByPerson(personId, limit, offset);
        });
    }

    public void save(Warrant warrant) {
        HibernateUtil.executeTransaction(session -> {
            WarrantRepository repo = new WarrantRepository(session);
            repo.save(warrant);
            return null;
        });
    }
}
