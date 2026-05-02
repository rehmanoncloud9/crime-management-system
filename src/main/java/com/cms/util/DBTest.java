package com.cms.util;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBTest {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/cms_db?useSSL=false&allowPublicKeyRetrieval=true";
        String user = "root";
        String[] passwords = {"potassium", ""};

        System.out.println("=== MySQL Connection Test ===");
        for (String pass : passwords) {
            System.out.println("Testing password: [" + pass + "]");
            try (Connection conn = DriverManager.getConnection(url, user, pass)) {
                System.out.println("SUCCESS! Connection established with password: [" + pass + "]");
                return;
            } catch (Exception e) {
                System.out.println("FAILED: " + e.getMessage());
            }
        }
        System.out.println("All attempts failed. Please verify your MySQL root password.");
    }
}
