package com.cms.repository.geo;

import com.cms.model.geo.City;
import com.cms.service.HibernateUtil;
import java.util.List;
import java.util.Optional;

public class CityRepository {
    public Optional<City> findById(Long id) {
        return HibernateUtil.executeTransaction(session -> Optional.ofNullable(session.get(City.class, id)));
    }
    public Optional<City> findByNameAndDistrictId(String name, Long districtId) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM City c WHERE c.name = :name AND c.district.id = :dId", City.class)
            .setParameter("name", name)
            .setParameter("dId", districtId)
            .uniqueResultOptional()
        );
    }
    public List<City> findByDistrictId(Long districtId) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM City c WHERE c.district.id = :dId ORDER BY c.name ASC", City.class)
            .setParameter("dId", districtId)
            .list()
        );
    }
    public List<City> searchByName(String keyword) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM City c JOIN FETCH c.district d WHERE lower(c.name) LIKE :kw ORDER BY c.name ASC", City.class)
            .setParameter("kw", "%" + keyword.toLowerCase() + "%")
            .list()
        );
    }
    public void save(City city) {
        HibernateUtil.executeVoidTransaction(session -> {
            session.persist(city);
        });
    }
    public java.util.List<City> findAll() {
        return com.cms.service.HibernateUtil.executeTransaction(session ->
            session.createQuery("FROM City ORDER BY name ASC", City.class).list()
        );
    }
}
