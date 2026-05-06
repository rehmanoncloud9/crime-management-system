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
}
