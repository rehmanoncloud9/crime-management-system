package com.cms.service;

import com.cms.model.LoginSession;
import com.cms.model.User;

public class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    private volatile LoginSession currentSession;

    private SessionManager() {}

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public synchronized LoginSession getCurrentSession() {
        return currentSession;
    }

    public synchronized void setCurrentSession(LoginSession session) {
        this.currentSession = session;
    }

    public synchronized User getCurrentUser() {
        return (currentSession != null) ? currentSession.getUser() : null;
    }

    public synchronized void logout() {
        if (currentSession != null) {
            new AuthService().logout(currentSession);
            currentSession = null;
        }
    }

    public synchronized boolean isLoggedIn() {
        return currentSession != null;
    }
}