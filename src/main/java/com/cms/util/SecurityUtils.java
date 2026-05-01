package com.cms.util;

import com.cms.model.User;
import com.cms.model.enums.Role;
import com.cms.service.SessionManager;
import java.util.Set;
import java.util.Arrays;

/**
 * Utility class for implementing Role-Based Access Control (RBAC).
 * Addresses C-10: Missing Authorization Checks.
 */
public class SecurityUtils {

    /**
     * Verifies that the current user has one of the required roles.
     * Throws SecurityException if unauthorized.
     * 
     * @param allowedRoles roles that are permitted to perform the action
     */
    public static void requireRole(Role... allowedRoles) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        
        if (currentUser == null) {
            throw new SecurityException("Authentication Required: No active session found.");
        }

        Set<Role> roles = new java.util.HashSet<>(Arrays.asList(allowedRoles));
        if (!roles.contains(currentUser.getRole())) {
            throw new SecurityException("Access Denied: User role '" + currentUser.getRole() + 
                "' does not have permission for this operation. Required: " + roles);
        }
    }

    /**
     * Checks if the current user has the required role without throwing an exception.
     */
    public static boolean hasRole(Role... allowedRoles) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) return false;
        
        Set<Role> roles = new java.util.HashSet<>(Arrays.asList(allowedRoles));
        return roles.contains(currentUser.getRole());
    }
}
