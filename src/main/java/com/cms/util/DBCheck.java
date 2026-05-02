package com.cms.util;

import java.sql.*;
import java.util.Properties;
import java.io.FileInputStream;

public class DBCheck {
    public static void main(String[] args) {
        System.out.println("=== CMS DATABASE DIAGNOSTIC ===");
        
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("src/main/resources/db.properties")) {
            props.load(in);
            String url = props.getProperty("db.url");
            String user = props.getProperty("db.username");
            String pass = props.getProperty("db.password", "potassium");
            
            System.out.println("Connecting to: " + url + " as " + user);
            
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                System.out.println("Connection successful!");
                
                // Check Users
                System.out.println("\n--- USERS ---");
                try (ResultSet rs = conn.createStatement().executeQuery("SELECT id, username, badge_number, role FROM users")) {
                    boolean found = false;
                    while (rs.next()) {
                        found = true;
                        System.out.printf("ID: %d | User: %s | Badge: %s | Role: %s\n", 
                            rs.getLong("id"), rs.getString("username"), rs.getString("badge_number"), rs.getString("role"));
                    }
                    if (!found) System.out.println("No users found in database.");
                } catch (SQLException e) {
                    System.err.println("Error reading users: " + e.getMessage());
                }
                
                System.out.println("\nDiagnostic complete.");
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
