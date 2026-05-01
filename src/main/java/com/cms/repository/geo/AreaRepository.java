package com.cms.repository.geo;

import com.cms.model.geo.Area;
import com.cms.service.HibernateUtil;
import java.util.List;
import java.util.Optional;

public class AreaRepository {
    public Optional<Area> findById(Long id) {
        return HibernateUtil.executeTransaction(session -> Optional.ofNullable(session.get(Area.class, id)));
    }
    public Optional<Area> findByNameAndCityId(String name, Long cityId) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM Area a WHERE a.name = :name AND a.city.id = :cId", Area.class)
            .setParameter("name", name)
            .setParameter("cId", cityId)
            .uniqueResultOptional()
        );
    }
    public List<Area> findByCityId(Long cityId) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM Area a WHERE a.city.id = :cId ORDER BY a.name ASC", Area.class)
            .setParameter("cId", cityId)
            .list()
        );
    }
    public List<Area> searchByName(String keyword) {
        return HibernateUtil.executeTransaction(session -> 
            session.createQuery("FROM Area a JOIN FETCH a.city c WHERE lower(a.name) LIKE :kw ORDER BY a.name ASC", Area.class)
            .setParameter("kw", "%" + keyword.toLowerCase() + "%")
            .list()
        );
    }
    public void save(Area area) {
        HibernateUtil.executeVoidTransaction(session -> {
            session.persist(area);
        });
    }
    public java.util.List<Area> findAll() {
        return com.cms.service.HibernateUtil.executeTransaction(session ->
            session.createQuery("FROM Area ORDER BY name ASC", Area.class).list()
        );
    }
}
