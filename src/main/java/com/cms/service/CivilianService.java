package com.cms.service;

import com.cms.model.Civilian;
import com.cms.repository.CivilianRepository;
import java.util.List;

public class CivilianService {

    public List<Civilian> searchCivilians(String keyword) {
        return HibernateUtil.executeTransaction(session -> {
            CivilianRepository repo = new CivilianRepository(session);
            return repo.search(keyword);
        });
    }

    public List<Civilian> getAllCivilians() {
        return HibernateUtil.executeTransaction(session -> {
            CivilianRepository repo = new CivilianRepository(session);
            return repo.findAll();
        });
    }

    public Civilian saveOrUpdate(Civilian civilian) {
        return HibernateUtil.executeTransaction(session -> {
            CivilianRepository repo = new CivilianRepository(session);
            if (civilian.getId() == null) repo.save(civilian);
            else repo.update(civilian);
            return civilian;
        });
    }

    public void deleteById(Long id) {
        HibernateUtil.executeVoidTransaction(session -> {
            CivilianRepository repo = new CivilianRepository(session);
            Civilian c = repo.findById(id);
            if (c != null) repo.delete(c);
        });
    }
}
