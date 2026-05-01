package com.cms.repository.geo;

import com.cms.model.geo.District;
import com.cms.service.HibernateUtil;
import java.util.List;
import java.util.Optional;

public class DistrictRepository {
    public Optional<District> findById(Long id) {
        return HibernateUtil.executeTransaction(session -> Optional.ofNullable(session.get(District.class, id)));
    }
    public Optional<District> findByNameAndProvinceId(String name, Long provinceId) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM District d WHERE d.name = :name AND d.province.id = :pId", District.class)
            .setParameter("name", name)
            .setParameter("pId", provinceId)
            .uniqueResultOptional()
        );
    }
    public List<District> findByProvinceId(Long provinceId) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM District d WHERE d.province.id = :pId ORDER BY d.name ASC", District.class)
            .setParameter("pId", provinceId)
            .list()
        );
    }
    public List<District> searchByName(String keyword) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM District d JOIN FETCH d.province p WHERE lower(d.name) LIKE :kw ORDER BY d.name ASC", District.class)
            .setParameter("kw", "%" + keyword.toLowerCase() + "%")
            .list()
        );
    }
    public void save(District district) {
        HibernateUtil.executeVoidTransaction(session -> {
            session.persist(district);
        });
    }
    public java.util.List<District> findAll() {
        return com.cms.service.HibernateUtil.executeTransaction(session ->
            session.createQuery("FROM District ORDER BY name ASC", District.class).list()
        );
    }
}
