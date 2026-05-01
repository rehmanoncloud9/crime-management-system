package com.cms.service;

import com.cms.model.Notification;
import com.cms.model.enums.NotificationPriority;
import com.cms.model.enums.NotificationType;
import com.cms.repository.NotificationRepository;

import java.util.List;

public class NotificationService {
    private final NotificationRepository notificationRepository;

    private static NotificationService instance;

    public NotificationService() {
        this.notificationRepository = new NotificationRepository();
    }

    public static NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    public void createNotification(String title, String description, NotificationType type, NotificationPriority priority) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setDescription(description);
        notification.setType(type);
        notification.setPriority(priority);
        notificationRepository.save(notification);
    }

    public List<Notification> getLatestNotifications() {
        return notificationRepository.getLatestNotifications(20);
    }

    public void markAsRead(Notification notification) {
        notification.setRead(true);
        notificationRepository.save(notification);
    }
    
    public long getUnreadCount() {
        return notificationRepository.getUnreadCount();
    }
}
