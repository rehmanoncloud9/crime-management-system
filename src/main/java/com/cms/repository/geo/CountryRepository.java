package com.cms.repository.geo;

import com.cms.model.geo.Country;
import com.cms.service.HibernateUtil;
import org.hibernate.Session;
import java.util.List;
import java.util.Optional;

public class CountryRepository {
    public Optional<Country> findById(Long id) {
        return HibernateUtil.executeTransaction(session -> Optional.ofNullable(session.get(Country.class, id)));
    }
    public Optional<Country> findByName(String name) {
        return HibernateUtil.executeTransaction(session -> session.createQuery("FROM Country c WHERE c.name = :name", Country.class)
            .setParameter("name", name)
            .uniqueResultOptional());
    }
    public List<Country> findAll() {
        return HibernateUtil.executeTransaction(session -> session.createQuery("FROM Country c ORDER BY c.name ASC", Country.class).list());
    }

    public List<Country> searchByName(String keyword) {
        return HibernateUtil.executeTransaction(session ->
            session.createQuery("FROM Country c WHERE LOWER(c.name) LIKE LOWER(:kw) ORDER BY c.name ASC", Country.class)
                   .setParameter("kw", "%" + keyword + "%")
                   .setMaxResults(20)
                   .list()
        );
    }
    public void save(Country country) {
        HibernateUtil.executeVoidTransaction(session -> {
            session.persist(country);
        });
    }
}
