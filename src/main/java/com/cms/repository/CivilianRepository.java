package com.cms.repository;

import com.cms.model.Civilian;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CivilianRepository {
    private static final Logger logger = LoggerFactory.getLogger(CivilianRepository.class);
    private final Session session;

    public CivilianRepository(Session session) {
        this.session = session;
    }

    public List<Civilian> findAll() {
        return session.createQuery("SELECT c FROM Civilian c JOIN c.person p WHERE p.deletedAt IS NULL", Civilian.class).list();
    }

    public List<Civilian> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return findAll();
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return session.createQuery(
            "SELECT c FROM Civilian c JOIN c.person p " +
            "WHERE p.deletedAt IS NULL AND (" +
            "LOWER(p.firstName) LIKE :p OR LOWER(p.lastName) LIKE :p " +
            "OR LOWER(p.nationalId) LIKE :p OR LOWER(c.occupation) LIKE :p " +
            "OR LOWER(c.employer) LIKE :p)", Civilian.class)
            .setParameter("p", pattern)
            .getResultList();
    }

    public Civilian findById(Long id) {
        return session.get(Civilian.class, id);
    }

    public void save(Civilian civilian) {
        session.persist(civilian);
    }

    public void update(Civilian civilian) {
        session.merge(civilian);
    }

    public void delete(Civilian civilian) {
        session.remove(civilian);
    }
}
