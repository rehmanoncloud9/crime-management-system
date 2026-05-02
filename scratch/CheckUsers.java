package com.cms.scratch;

import com.cms.service.HibernateUtil;
import java.sql.*;
import java.util.Properties;
import java.io.FileInputStream;

public class CheckUsers {
    public static void main(String[] args) {
        System.out.println("=== CMS DATABASE DIAGNOSTIC ===");
        
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream("src/main/resources/db.properties")) {
            props.load(in);
            String url = props.getProperty("db.url");
            String user = props.getProperty("db.username");
            String pass = props.getProperty("db.password");
            
            System.out.println("Connecting to: " + url);
            
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                System.out.println("Connection successful!");
                
                // Check Persons
                System.out.println("\n--- PERSONS ---");
                try (ResultSet rs = conn.createStatement().executeQuery("SELECT id, first_name, last_name FROM persons")) {
                    while (rs.next()) {
                        System.out.printf("ID: %d | Name: %s %s\n", rs.getLong("id"), rs.getString("first_name"), rs.getString("last_name"));
                    }
                } catch (SQLException e) {
                    System.err.println("Error reading persons: " + e.getMessage());
                }
                
                // Check Users
                System.out.println("\n--- USERS ---");
                try (ResultSet rs = conn.createStatement().executeQuery("SELECT id, username, badge_number, role FROM users")) {
                    while (rs.next()) {
                        System.out.printf("ID: %d | User: %s | Badge: %s | Role: %s\n", 
                            rs.getLong("id"), rs.getString("username"), rs.getString("badge_number"), rs.getString("role"));
                    }
                } catch (SQLException e) {
                    System.err.println("Error reading users: " + e.getMessage());
                }
                
                // Check Roles
                System.out.println("\n--- ROLES ---");
                try (ResultSet rs = conn.createStatement().executeQuery("SELECT id, name FROM roles")) {
                    while (rs.next()) {
                        System.out.printf("ID: %d | Role: %s\n", rs.getLong("id"), rs.getString("name"));
                    }
                } catch (SQLException e) {
                    System.err.println("Error reading roles: " + e.getMessage());
                }

                System.out.println("\nDiagnostic complete.");
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
