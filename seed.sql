-- CMS v2.0 Seed Data
USE cms_db;

-- Default Admin User (Password: Admin@123)
-- jBCrypt hashed: $2a$12$R.S4qJ7j1oVz2Fk9uGf9EOY9pY6uA8wY0kG7R8S9T0U1V2W3X4Y5Z (Example hash, using Admin@123)
INSERT INTO users (badge_number, username, password_hash, full_name, officer_rank, role, status)
VALUES ('ADMIN001', 'admin', '$2a$12$kcH4Z7G4gQMzr9nUb08lfeBQmAtpGw9sAK/sppCmq3pHWe5RtBtIS', 'System Administrator', 'Admin', 'ADMINISTRATOR', 'ACTIVE');

-- Sample Crime Types
INSERT INTO crime_types (name, code) VALUES ('Violent Crimes', 'VC');
INSERT INTO crime_types (name, code, parent_id) VALUES ('Assault', 'VC-AS', 1);
INSERT INTO crime_types (name, code, parent_id) VALUES ('Aggravated Assault', 'VC-AS-AG', 2);
INSERT INTO crime_types (name, code) VALUES ('Property Crimes', 'PC');
INSERT INTO crime_types (name, code, parent_id) VALUES ('Burglary', 'PC-BU', 4);
INSERT INTO crime_types (name, code, parent_id) VALUES ('Theft', 'PC-TH', 4);

-- Sample Officers
INSERT INTO users (badge_number, username, password_hash, full_name, officer_rank, role, status, precinct)
VALUES ('OFF001', 'officer1', '$2a$12$kcH4Z7G4gQMzr9nUb08lfeBQmAtpGw9sAK/sppCmq3pHWe5RtBtIS', 'Officer James Carter', 'Sergeant', 'OFFICER', 'ACTIVE', 'North Precinct');

INSERT INTO users (badge_number, username, password_hash, full_name, officer_rank, role, status, precinct)
VALUES ('DET001', 'detective1', '$2a$12$kcH4Z7G4gQMzr9nUb08lfeBQmAtpGw9sAK/sppCmq3pHWe5RtBtIS', 'Detective Sarah Connor', 'Senior Detective', 'DETECTIVE', 'ACTIVE', 'Central Bureau');
