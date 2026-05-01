package com.cms.repository;

import com.cms.model.Notification;
import com.cms.service.HibernateUtil;

import java.util.List;

public class NotificationRepository {

    public void save(Notification notification) {
        HibernateUtil.executeVoidTransaction(session -> session.merge(notification));
    }

    public List<Notification> getLatestNotifications(int limit) {
        return HibernateUtil.executeTransaction(session ->
            session.createQuery(
                "FROM Notification n ORDER BY n.priority ASC, n.timestamp DESC", Notification.class)
                .setMaxResults(limit)
                .list()
        );
    }

    public long getUnreadCount() {
        return HibernateUtil.executeTransaction(session ->
            session.createQuery(
                "SELECT COUNT(n) FROM Notification n WHERE n.read = false", Long.class)
                .uniqueResult()
        );
    }
}
