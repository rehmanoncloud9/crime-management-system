package com.cms.repository.geo;

import com.cms.model.geo.Province;
import com.cms.service.HibernateUtil;
import java.util.List;
import java.util.Optional;

public class ProvinceRepository {
    public Optional<Province> findById(Long id) {
        return HibernateUtil.executeTransaction(session -> Optional.ofNullable(session.get(Province.class, id)));
    }
    public Optional<Province> findByNameAndCountryId(String name, Long countryId) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM Province p WHERE p.name = :name AND p.country.id = :cId", Province.class)
            .setParameter("name", name)
            .setParameter("cId", countryId)
            .uniqueResultOptional()
        );
    }
    public List<Province> findByCountryId(Long countryId) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM Province p WHERE p.country.id = :cId ORDER BY p.name ASC", Province.class)
            .setParameter("cId", countryId)
            .list()
        );
    }

    public List<Province> searchByName(String keyword) {
        return HibernateUtil.executeTransaction(session ->
            session.createQuery("FROM Province p WHERE LOWER(p.name) LIKE LOWER(:kw) ORDER BY p.name ASC", Province.class)
                   .setParameter("kw", "%" + keyword + "%")
                   .setMaxResults(20)
                   .list()
        );
    }
    public void save(Province province) {
        HibernateUtil.executeVoidTransaction(session -> {
            session.persist(province);
        });
    }
    public java.util.List<Province> findAll() {
        return com.cms.service.HibernateUtil.executeTransaction(session ->
            session.createQuery("FROM Province ORDER BY name ASC", Province.class).list()
        );
    }
}
