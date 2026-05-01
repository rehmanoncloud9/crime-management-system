-- Crime Management System (CMS) Database Schema
-- Version: 3.0 (normalized, FK-safe, production-oriented)
-- MySQL 8+

CREATE DATABASE IF NOT EXISTS cms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE cms_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =========================
-- GEO LOOKUP HIERARCHY
-- =========================
CREATE TABLE countries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    code CHAR(3) UNIQUE
) ENGINE=InnoDB;

CREATE TABLE provinces (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    country_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    code VARCHAR(20),
    CONSTRAINT fk_province_country
        FOREIGN KEY (country_id) REFERENCES countries(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    UNIQUE KEY uk_province_country_name (country_id, name),
    INDEX idx_province_country (country_id)
) ENGINE=InnoDB;

CREATE TABLE districts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    province_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    code VARCHAR(20),
    CONSTRAINT fk_district_province
        FOREIGN KEY (province_id) REFERENCES provinces(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    UNIQUE KEY uk_district_province_name (province_id, name),
    INDEX idx_district_province (province_id)
) ENGINE=InnoDB;

CREATE TABLE cities (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    district_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    postal_code VARCHAR(20),
    CONSTRAINT fk_city_district
        FOREIGN KEY (district_id) REFERENCES districts(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    UNIQUE KEY uk_city_district_name (district_id, name),
    INDEX idx_city_district (district_id)
) ENGINE=InnoDB;

CREATE TABLE areas (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    city_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    area_type VARCHAR(30),
    latitude DECIMAL(10, 7),
    longitude DECIMAL(10, 7),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_area_city
        FOREIGN KEY (city_id) REFERENCES cities(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    UNIQUE KEY uk_area_city_name (city_id, name),
    INDEX idx_area_city (city_id),
    INDEX idx_area_geo (latitude, longitude)
) ENGINE=InnoDB;

-- =========================
-- SECURITY / USERS
-- =========================
CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    badge_number VARCHAR(50) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    person_id BIGINT UNSIGNED NOT NULL UNIQUE,
    officer_rank VARCHAR(100),
    role VARCHAR(30) NOT NULL,
    department VARCHAR(150),
    precinct VARCHAR(100),
    date_joined DATE,
    profile_photo_path VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME,
    last_active DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by BIGINT UNSIGNED,
    CONSTRAINT fk_user_created_by
        FOREIGN KEY (created_by) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_user_person
        FOREIGN KEY (person_id) REFERENCES persons(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CHECK (failed_login_attempts >= 0)
) ENGINE=InnoDB;

CREATE TABLE civilians (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT UNSIGNED NOT NULL UNIQUE,
    occupation VARCHAR(100),
    employer VARCHAR(150),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_civilian_person FOREIGN KEY (person_id) REFERENCES persons(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE roles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE permissions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
    role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE login_sessions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    login_at DATETIME NOT NULL,
    logout_at DATETIME,
    workstation_id VARCHAR(100),
    ip_address VARCHAR(45),
    session_status VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_session_user (user_id),
    INDEX idx_session_status (session_status),
    INDEX idx_session_login_at (login_at)
) ENGINE=InnoDB;

-- =========================
-- MASTER DATA
-- =========================
CREATE TABLE crime_types (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    parent_id INT UNSIGNED,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE,
    description VARCHAR(255),
    category VARCHAR(100),
    examples TEXT,
    investigation_tips TEXT,
    legal_reference VARCHAR(200),
    CONSTRAINT fk_crime_type_parent
        FOREIGN KEY (parent_id) REFERENCES crime_types(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_crime_type_parent (parent_id)
) ENGINE=InnoDB;

-- =========================
-- CORE CASE PIPELINE
-- =========================
CREATE TABLE crime_incidents (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    incident_number VARCHAR(50) NOT NULL UNIQUE,
    crime_type_id INT UNSIGNED NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    occurred_at DATETIME NOT NULL,
    reported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reporter_name VARCHAR(200),
    reporter_contact VARCHAR(100),
    location_address VARCHAR(255),
    district_id BIGINT UNSIGNED,
    city_id BIGINT UNSIGNED,
    area_id BIGINT UNSIGNED,
    precinct VARCHAR(100),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    reporting_officer_id BIGINT UNSIGNED,
    reporter_person_id BIGINT UNSIGNED,
    severity_level VARCHAR(30) DEFAULT 'MEDIUM',
    status VARCHAR(50) NOT NULL DEFAULT 'REPORTED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_crime_type
        FOREIGN KEY (crime_type_id) REFERENCES crime_types(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_incident_district
        FOREIGN KEY (district_id) REFERENCES districts(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_incident_city
        FOREIGN KEY (city_id) REFERENCES cities(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_incident_area
        FOREIGN KEY (area_id) REFERENCES areas(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_incident_officer
        FOREIGN KEY (reporting_officer_id) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_incident_reporter_person
        FOREIGN KEY (reporter_person_id) REFERENCES persons(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_incident_crime_type (crime_type_id),
    INDEX idx_incident_officer (reporting_officer_id),
    INDEX idx_incident_status (status),
    INDEX idx_incident_occurred (occurred_at),
    FULLTEXT INDEX ft_incident_search (title, description, location_address),
    CHECK (latitude IS NULL OR (latitude BETWEEN -90.0 AND 90.0)),
    CHECK (longitude IS NULL OR (longitude BETWEEN -180.0 AND 180.0)),
    CHECK (status IN ('REPORTED','VERIFIED','CONVERTED','OPEN','UNDER_INVESTIGATION','ARRESTED','CHARGED','IN_TRIAL','CLOSED_CONVICTED','CLOSED_ACQUITTED','CLOSED_UNSOLVED','CLOSED','ARCHIVED'))
) ENGINE=InnoDB;

CREATE TABLE incident_amendments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT UNSIGNED NOT NULL,
    amended_by BIGINT UNSIGNED NOT NULL,
    amendment_reason TEXT NOT NULL,
    changed_fields JSON NOT NULL,
    amended_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_amendment_incident
        FOREIGN KEY (incident_id) REFERENCES crime_incidents(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_incident_amendment_user
        FOREIGN KEY (amended_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_incident_amendment_incident (incident_id),
    INDEX idx_incident_amendment_user (amended_by)
) ENGINE=InnoDB;

CREATE TABLE case_files (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    case_number VARCHAR(50) NOT NULL UNIQUE,
    incident_id BIGINT UNSIGNED NOT NULL UNIQUE,
    primary_investigator_id BIGINT UNSIGNED,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    opened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME,
    closure_reason TEXT,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_case_incident
        FOREIGN KEY (incident_id) REFERENCES crime_incidents(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_case_primary_investigator
        FOREIGN KEY (primary_investigator_id) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_case_primary_investigator (primary_investigator_id),
    INDEX idx_case_priority (priority),
    INDEX idx_case_status (status),
    INDEX idx_case_status_deleted (status, deleted_at),
    CHECK (closed_at IS NULL OR closed_at >= opened_at),
    CHECK (priority IN ('LOW','MEDIUM','HIGH')),
    CHECK (status IN ('OPEN','UNDER_INVESTIGATION','ARRESTED','CHARGED','IN_TRIAL','CLOSED_CONVICTED','CLOSED_ACQUITTED','CLOSED_UNSOLVED','REOPENED'))
) ENGINE=InnoDB;

-- Officer assignment M:N (requested relationship)
CREATE TABLE case_officers (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT UNSIGNED NOT NULL,
    officer_id BIGINT UNSIGNED NOT NULL,
    role_in_case VARCHAR(40) NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT UNSIGNED,
    CONSTRAINT fk_case_officer_case
        FOREIGN KEY (case_id) REFERENCES case_files(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_case_officer_user
        FOREIGN KEY (officer_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_case_officer_assigned_by
        FOREIGN KEY (assigned_by) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    UNIQUE KEY uk_case_officer_unique_role (case_id, officer_id, role_in_case),
    INDEX idx_case_officer_case (case_id),
    INDEX idx_case_officer_user (officer_id)
) ENGINE=InnoDB;

-- =========================
-- PERSONS / CRIMINAL DOMAIN
-- =========================
CREATE TABLE persons (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(20),
    nationality_country_id BIGINT UNSIGNED,
    national_id VARCHAR(20) UNIQUE,
    height_cm SMALLINT,
    weight_kg SMALLINT,
    eye_color VARCHAR(50),
    hair_color VARCHAR(50),
    `build` VARCHAR(50),
    district_id BIGINT UNSIGNED,
    city_id BIGINT UNSIGNED,
    area_id BIGINT UNSIGNED,
    address TEXT,
    is_identified BOOLEAN NOT NULL DEFAULT TRUE,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    photo MEDIUMBLOB,
    is_high_risk BOOLEAN DEFAULT FALSE,
    gang_affiliation VARCHAR(150),
    risk_score VARCHAR(20) DEFAULT 'LOW' COMMENT 'Derived attribute cached for performance',
    risk_score_updated_at DATETIME,
    has_active_warrant BOOLEAN DEFAULT FALSE,
    age INT GENERATED ALWAYS AS (TIMESTAMPDIFF(YEAR, date_of_birth, CURDATE())) VIRTUAL,
    person_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL,
    CONSTRAINT fk_person_country
        FOREIGN KEY (nationality_country_id) REFERENCES countries(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_person_district
        FOREIGN KEY (district_id) REFERENCES districts(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_person_city
        FOREIGN KEY (city_id) REFERENCES cities(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_person_area
        FOREIGN KEY (area_id) REFERENCES areas(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_person_status (person_status),
    INDEX idx_person_national_id (national_id),
    INDEX idx_person_names (first_name, last_name),
    FULLTEXT INDEX ft_person_search (first_name, last_name, address),
    CHECK (height_cm IS NULL OR height_cm > 0),
    CHECK (weight_kg IS NULL OR weight_kg > 0)
) ENGINE=InnoDB;

CREATE TABLE person_marks (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT UNSIGNED NOT NULL,
    description VARCHAR(255) NOT NULL,
    body_location VARCHAR(100),
    CONSTRAINT fk_pmark_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE person_aliases (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT UNSIGNED NOT NULL,
    alias VARCHAR(150) NOT NULL,
    CONSTRAINT fk_palias_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE medical_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT UNSIGNED NOT NULL UNIQUE,
    blood_group VARCHAR(20),
    dna_profile VARCHAR(255),
    fingerprint_id VARCHAR(100),
    known_diseases TEXT,
    injuries TEXT,
    medical_notes TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_medical_person
        FOREIGN KEY (person_id) REFERENCES persons(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- M:N case-person relationships (explicit role)
CREATE TABLE case_persons (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT UNSIGNED NOT NULL,
    person_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(20) NOT NULL,
    notes TEXT,
    added_by BIGINT UNSIGNED,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_case_person_case
        FOREIGN KEY (case_id) REFERENCES case_files(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_case_person_person
        FOREIGN KEY (person_id) REFERENCES persons(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_case_person_added_by
        FOREIGN KEY (added_by) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    UNIQUE KEY uk_case_person_role (case_id, person_id, role),
    INDEX idx_case_person_case (case_id),
    INDEX idx_case_person_person (person_id),
    INDEX idx_case_person_role (role)
) ENGINE=InnoDB;

CREATE TABLE case_notes (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT UNSIGNED NOT NULL,
    author_id BIGINT UNSIGNED NOT NULL,
    note_text TEXT NOT NULL,
    is_confidential BOOLEAN DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cn_case FOREIGN KEY (case_id) REFERENCES case_files(id) ON DELETE CASCADE,
    CONSTRAINT fk_cn_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Compatibility with existing JPA join tables
CREATE TABLE case_suspects (
    case_id BIGINT UNSIGNED NOT NULL,
    person_id BIGINT UNSIGNED NOT NULL,
    motive TEXT,
    threat_level VARCHAR(20) DEFAULT 'LOW',
    added_by BIGINT UNSIGNED,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (case_id, person_id),
    CONSTRAINT fk_case_suspect_case FOREIGN KEY (case_id) REFERENCES case_files(id) ON DELETE CASCADE,
    CONSTRAINT fk_case_suspect_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_case_suspect_added_by FOREIGN KEY (added_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE case_victims (
    case_id BIGINT UNSIGNED NOT NULL,
    person_id BIGINT UNSIGNED NOT NULL,
    statement TEXT,
    injury_description TEXT,
    added_by BIGINT UNSIGNED,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (case_id, person_id),
    CONSTRAINT fk_case_victim_case FOREIGN KEY (case_id) REFERENCES case_files(id) ON DELETE CASCADE,
    CONSTRAINT fk_case_victim_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_case_victim_added_by FOREIGN KEY (added_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE case_witnesses (
    case_id BIGINT UNSIGNED NOT NULL,
    person_id BIGINT UNSIGNED NOT NULL,
    statement TEXT,
    reliability_rating INT CHECK (reliability_rating BETWEEN 1 AND 5),
    added_by BIGINT UNSIGNED,
    added_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (case_id, person_id),
    CONSTRAINT fk_case_witness_case FOREIGN KEY (case_id) REFERENCES case_files(id) ON DELETE CASCADE,
    CONSTRAINT fk_case_witness_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_case_witness_added_by FOREIGN KEY (added_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE person_associates (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT UNSIGNED NOT NULL,
    associate_id BIGINT UNSIGNED NOT NULL,
    relationship_type VARCHAR(100),
    notes TEXT,
    CONSTRAINT fk_person_assoc_person
        FOREIGN KEY (person_id) REFERENCES persons(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_person_assoc_associate
        FOREIGN KEY (associate_id) REFERENCES persons(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    UNIQUE KEY uk_person_associate (person_id, associate_id),
    CHECK (person_id <> associate_id)
) ENGINE=InnoDB;

-- =========================
-- EVIDENCE / WARRANT / ARREST
-- =========================
CREATE TABLE evidence (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    evidence_number VARCHAR(50) NOT NULL UNIQUE,
    case_id BIGINT UNSIGNED NOT NULL,
    suspect_id BIGINT UNSIGNED,
    type VARCHAR(30) NOT NULL,
    description TEXT NOT NULL,
    collected_at DATETIME NOT NULL,
    collected_by BIGINT UNSIGNED NOT NULL,
    collection_location VARCHAR(500) NOT NULL,
    current_storage_location VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'COLLECTED',
    file_path VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_evidence_case
        FOREIGN KEY (case_id) REFERENCES case_files(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_evidence_suspect
        FOREIGN KEY (suspect_id) REFERENCES persons(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_evidence_collected_by
        FOREIGN KEY (collected_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_evidence_case (case_id),
    INDEX idx_evidence_suspect (suspect_id),
    INDEX idx_evidence_status (status),
    CHECK (status IN ('COLLECTED','ANALYZED','VERIFIED','IN_STORAGE','IN_LAB','TRANSFERRED','IN_COURT','RETURNED','DESTROYED','IN_TRANSFER','STORED')),
    CHECK (type IN ('PHYSICAL','DIGITAL','DOCUMENTARY','FORENSIC','BIOLOGICAL'))
) ENGINE=InnoDB;

CREATE TABLE court_case_evidence (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    court_case_id BIGINT UNSIGNED NOT NULL,
    evidence_id BIGINT UNSIGNED NOT NULL,
    presented_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    CONSTRAINT fk_cce_case FOREIGN KEY (court_case_id) REFERENCES court_cases(id) ON DELETE CASCADE,
    CONSTRAINT fk_cce_evidence FOREIGN KEY (evidence_id) REFERENCES evidence(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE evidence_custody_log (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    evidence_id BIGINT UNSIGNED NOT NULL,
    action VARCHAR(200) NOT NULL,
    action_by BIGINT UNSIGNED NOT NULL,
    from_location VARCHAR(500),
    to_location VARCHAR(500),
    notes TEXT,
    action_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_custody_evidence
        FOREIGN KEY (evidence_id) REFERENCES evidence(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_custody_action_by
        FOREIGN KEY (action_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_custody_evidence (evidence_id),
    INDEX idx_custody_action_at (action_at)
) ENGINE=InnoDB;

CREATE TABLE warrant_types (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
) ENGINE=InnoDB;

CREATE TABLE warrants (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    warrant_number VARCHAR(50) NOT NULL UNIQUE,
    case_id BIGINT UNSIGNED,
    suspect_id BIGINT UNSIGNED NOT NULL,
    issued_by VARCHAR(255) NOT NULL,
    issuing_court_id BIGINT UNSIGNED,
    issued_at DATE NOT NULL,
    expires_at DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_warrant_case FOREIGN KEY (case_id) REFERENCES case_files(id) ON DELETE SET NULL,
    CONSTRAINT fk_warrant_suspect FOREIGN KEY (suspect_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_warrant_court FOREIGN KEY (issuing_court_id) REFERENCES courts(id) ON DELETE SET NULL,
    CHECK (expires_at IS NULL OR expires_at >= issued_at)
) ENGINE=InnoDB;

CREATE TABLE warrant_charges (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    warrant_id BIGINT UNSIGNED NOT NULL,
    description VARCHAR(500) NOT NULL,
    legal_section VARCHAR(100),
    CONSTRAINT fk_wcharge_warrant FOREIGN KEY (warrant_id) REFERENCES warrants(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE arrest_records (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT UNSIGNED NOT NULL,
    arresting_officer_id BIGINT UNSIGNED NOT NULL,
    case_id BIGINT UNSIGNED,
    warrant_id BIGINT UNSIGNED,
    arrested_at DATETIME NOT NULL,
    arrest_location VARCHAR(500),
    custody_location VARCHAR(500),
    bail_amount DECIMAL(12, 2),
    bail_status VARCHAR(20) DEFAULT 'NOT_APPLICABLE',
    arrest_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    booking_reference VARCHAR(100) UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_arrest_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE,
    CONSTRAINT fk_arrest_officer FOREIGN KEY (arresting_officer_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_arrest_case FOREIGN KEY (case_id) REFERENCES case_files(id) ON DELETE SET NULL,
    CONSTRAINT fk_arrest_warrant FOREIGN KEY (warrant_id) REFERENCES warrants(id) ON DELETE SET NULL,
    CHECK (bail_status IN ('NOT_APPLICABLE','PENDING','GRANTED','DENIED','FORFEITED')),
    CHECK (arrest_status IN ('INITIATED','COMPLETED','CANCELLED'))
) ENGINE=InnoDB;

CREATE TABLE arrest_charges (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    arrest_record_id BIGINT UNSIGNED NOT NULL,
    description VARCHAR(500) NOT NULL,
    legal_section VARCHAR(100),
    CONSTRAINT fk_acharge_arrest FOREIGN KEY (arrest_record_id) REFERENCES arrest_records(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =========================
-- COURT
-- =========================
CREATE TABLE courts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    type VARCHAR(100),
    district_id BIGINT UNSIGNED,
    city_id BIGINT UNSIGNED,
    address VARCHAR(300),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_court_district
        FOREIGN KEY (district_id) REFERENCES districts(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_court_city
        FOREIGN KEY (city_id) REFERENCES cities(id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE court_cases (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT UNSIGNED NOT NULL UNIQUE,
    court_id BIGINT UNSIGNED,
    court_case_number VARCHAR(50) UNIQUE,
    judge_name VARCHAR(100),
    prosecutor_id BIGINT UNSIGNED,
    filed_at DATE NOT NULL,
    verdict TEXT,
    verdict_date DATE,
    sentence_details TEXT,
    arrest_id BIGINT UNSIGNED,
    defendant_id BIGINT UNSIGNED,
    status VARCHAR(50) NOT NULL DEFAULT 'FILED',
    CONSTRAINT fk_court_case_file FOREIGN KEY (case_id) REFERENCES case_files(id) ON DELETE CASCADE,
    CONSTRAINT fk_court_case_court FOREIGN KEY (court_id) REFERENCES courts(id) ON DELETE SET NULL,
    CONSTRAINT fk_court_prosecutor FOREIGN KEY (prosecutor_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_court_case_arrest FOREIGN KEY (arrest_id) REFERENCES arrest_records(id) ON DELETE SET NULL,
    CONSTRAINT fk_court_case_defendant FOREIGN KEY (defendant_id) REFERENCES persons(id) ON DELETE SET NULL,
    INDEX idx_court_id (court_id),
    INDEX idx_court_status (status)
) ENGINE=InnoDB;

CREATE TABLE appeals (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    court_case_id BIGINT UNSIGNED NOT NULL,
    appeal_number VARCHAR(50) NOT NULL UNIQUE,
    filed_by BIGINT UNSIGNED,
    filed_at DATE NOT NULL,
    grounds TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'FILED',
    outcome TEXT,
    decided_at DATE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appeal_court_case FOREIGN KEY (court_case_id) REFERENCES court_cases(id) ON DELETE CASCADE,
    CONSTRAINT fk_appeal_filed_by FOREIGN KEY (filed_by) REFERENCES users(id) ON DELETE SET NULL,
    CHECK (status IN ('FILED','UNDER_REVIEW','HEARING','GRANTED','DENIED','WITHDRAWN'))
) ENGINE=InnoDB;

CREATE TABLE hearing_types (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT
) ENGINE=InnoDB;


CREATE TABLE court_hearings (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    court_case_id BIGINT UNSIGNED NOT NULL,
    hearing_date DATETIME NOT NULL,
    outcome VARCHAR(255),
    next_hearing_date DATETIME,
    recorded_by BIGINT UNSIGNED NOT NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hearing_status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    CONSTRAINT fk_hearing_court_case
        FOREIGN KEY (court_case_id) REFERENCES court_cases(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_hearing_recorded_by
        FOREIGN KEY (recorded_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_hearing_case (court_case_id),
    INDEX idx_hearing_date (hearing_date),
    CHECK (hearing_status IN ('SCHEDULED','COMPLETED','ADJOURNED'))
) ENGINE=InnoDB;

CREATE TABLE charge_sheets (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT UNSIGNED NOT NULL UNIQUE,
    court_case_id BIGINT UNSIGNED,
    sheet_number VARCHAR(50) NOT NULL UNIQUE,
    filed_by BIGINT UNSIGNED NOT NULL,
    filed_on DATE NOT NULL,
    summary TEXT NOT NULL,
    legal_sections TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_charge_sheet_case
        FOREIGN KEY (case_id) REFERENCES case_files(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_charge_sheet_court_case
        FOREIGN KEY (court_case_id) REFERENCES court_cases(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_charge_sheet_filed_by
        FOREIGN KEY (filed_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CHECK (status IN ('DRAFT','FILED','SUBMITTED'))
) ENGINE=InnoDB;

CREATE TABLE media_files (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT UNSIGNED NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_uri VARCHAR(1000) NOT NULL,
    mime_type VARCHAR(100),
    file_size_bytes BIGINT,
    uploaded_by BIGINT UNSIGNED,
    uploaded_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_media_user FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- =========================
-- ANALYTICS / AUDIT / AI
-- =========================
CREATE TABLE modus_operandi (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT UNSIGNED NOT NULL,
    crime_type_id INT UNSIGNED,
    method_description TEXT,
    typical_time_of_day VARCHAR(20),
    typical_location_type VARCHAR(200),
    target_type VARCHAR(200),
    tools_used TEXT,
    tags TEXT,
    noted_by BIGINT UNSIGNED NOT NULL,
    noted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mo_person
        FOREIGN KEY (person_id) REFERENCES persons(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_mo_crime_type
        FOREIGN KEY (crime_type_id) REFERENCES crime_types(id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_mo_noted_by
        FOREIGN KEY (noted_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    FULLTEXT INDEX ft_mo_search (method_description, tools_used, target_type)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    user_name VARCHAR(200) NOT NULL,
    action VARCHAR(30) NOT NULL,
    entity_type VARCHAR(100),
    entity_id BIGINT UNSIGNED,
    description TEXT,
    old_value JSON,
    new_value JSON,
    ip_address VARCHAR(45),
    workstation_id VARCHAR(100),
    `timestamp` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_timestamp (`timestamp`)
) ENGINE=InnoDB;

CREATE TABLE notifications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    type VARCHAR(30) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_notification_read (is_read),
    INDEX idx_notification_timestamp (timestamp)
) ENGINE=InnoDB;

CREATE TABLE chat_sessions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    start_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_activity DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE chat_messages (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT UNSIGNED NOT NULL,
    sender VARCHAR(20) NOT NULL, -- 'USER' or 'AI'
    content TEXT,
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_session FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB;


CREATE TABLE ai_analysis_results (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    analysis_type VARCHAR(40) NOT NULL,
    subject_type VARCHAR(100),
    subject_id BIGINT UNSIGNED,
    result_json JSON NOT NULL,
    confidence DECIMAL(5,4),
    model_version VARCHAR(50),
    computed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_subject (subject_type, subject_id),
    INDEX idx_ai_analysis_type (analysis_type),
    INDEX idx_ai_computed_at (computed_at)
) ENGINE=InnoDB;

-- =========================
-- WORKFLOW ENFORCEMENT TRIGGERS
-- =========================
DELIMITER $$

CREATE TRIGGER trg_arrest_requires_suspect_link
BEFORE INSERT ON arrest_records
FOR EACH ROW
BEGIN
    IF NEW.case_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Arrest must belong to a case';
    END IF;

    -- Updated: check case_suspects instead of case_persons
    IF NOT EXISTS (
        SELECT 1 FROM case_suspects cs
        WHERE cs.case_id = NEW.case_id
          AND cs.person_id = NEW.person_id
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Arrest suspect must be linked as SUSPECT in the case';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM case_files c
        WHERE c.id = NEW.case_id AND c.status = 'UNDER_INVESTIGATION'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Arrest allowed only for UNDER_INVESTIGATION cases';
    END IF;
END$$

CREATE TRIGGER trg_case_close_validation
BEFORE UPDATE ON case_files
FOR EACH ROW
BEGIN
    IF NEW.status IN ('CLOSED_CONVICTED','CLOSED_ACQUITTED','CLOSED_UNSOLVED') THEN
        IF NEW.status = 'CLOSED_UNSOLVED' AND (NEW.closure_reason IS NULL OR TRIM(NEW.closure_reason) = '') THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Unsolved case requires explicit closure_reason';
        END IF;

        IF NEW.status IN ('CLOSED_CONVICTED','CLOSED_ACQUITTED') AND NOT EXISTS (
            SELECT 1
            FROM court_hearings h
            JOIN court_cases cc ON cc.id = h.court_case_id
            WHERE cc.case_id = NEW.id AND h.hearing_status = 'COMPLETED'
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Conviction/acquittal requires completed hearing';
        END IF;
    END IF;
END$$

CREATE TRIGGER trg_charge_sheet_prereq
BEFORE INSERT ON charge_sheets
FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM evidence e WHERE e.case_id = NEW.case_id) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Charge sheet requires at least one evidence record';
    END IF;

    -- Updated: check case_suspects instead of case_persons
    IF NOT EXISTS (
        SELECT 1 FROM case_suspects cs WHERE cs.case_id = NEW.case_id
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Charge sheet requires at least one suspect';
    END IF;
END$$

DELIMITER ;

-- ========================================================================
-- DATABASE ENHANCEMENTS FOR COURSE PROJECT
-- Demonstrates: Normalization, Data Integrity, Indexing, Views, Procedures
-- ========================================================================

-- 1. NORMALIZATION: 1NF - Replace comma-separated related_case_ids with M:M table
-- ========================================================================

-- This table replaces the denormalized TEXT field on case_files
CREATE TABLE IF NOT EXISTS related_cases (
    case_id BIGINT UNSIGNED NOT NULL,
    related_id BIGINT UNSIGNED NOT NULL,
    relation_type VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (case_id, related_id),
    CONSTRAINT fk_related_case_primary
        FOREIGN KEY (case_id) REFERENCES case_files(id) ON DELETE CASCADE,
    CONSTRAINT fk_related_case_secondary
        FOREIGN KEY (related_id) REFERENCES case_files(id) ON DELETE CASCADE,
    CONSTRAINT chk_case_not_self
        CHECK (case_id <> related_id),
    CONSTRAINT chk_case_ordering
        CHECK (case_id < related_id),
    
    INDEX idx_related_case_id (case_id),
    INDEX idx_related_id (related_id),
    INDEX idx_relation_type (relation_type)
) ENGINE=InnoDB;

-- ========================================================================
-- 2. DATA INTEGRITY: CHECK Constraints matching Java enum values
-- ========================================================================

-- Ensure warrant status is restricted to valid domain
ALTER TABLE warrants 
ADD CONSTRAINT chk_warrant_status CHECK (status IN (
    'ISSUED',
    'EXECUTED', 
    'EXPIRED',
    'CANCELLED',
    'REVOKED'
));

-- Ensure court case status matches CourtStatus enum
ALTER TABLE court_cases 
ADD CONSTRAINT chk_court_case_status CHECK (status IN (
    'FILED',
    'PENDING_HEARING',
    'ONGOING_TRIAL',
    'CONVICTED',
    'ACQUITTED',
    'APPEALED',
    'CLOSED'
));

-- Ensure person gender is restricted to valid values
ALTER TABLE persons 
ADD CONSTRAINT chk_person_gender CHECK (gender IN (
    'MALE',
    'FEMALE',
    'OTHER',
    'UNKNOWN'
));

-- Ensure user role matches Role enum (9 values)
ALTER TABLE users 
ADD CONSTRAINT chk_user_role CHECK (role IN (
    'ADMINISTRATOR',
    'SUPERVISOR',
    'OFFICER',
    'DETECTIVE',
    'ANALYST',
    'RECORDS_CLERK',
    'PROSECUTOR',
    'AUDITOR',
    'MANAGEMENT'
));

-- Evidence collected_at must not be in the future (trigger-based)
-- Evidence status must match EvidenceStatus enum
ALTER TABLE evidence
ADD CONSTRAINT chk_evidence_status CHECK (status IN (
    'COLLECTED',
    'IN_TRANSFER',
    'STORED',
    'DESTROYED',
    'LOGGED'
));

-- ========================================================================
-- 3. BUSINESS RULE ENFORCEMENT: Trigger for Evidence Date Validation
-- ========================================================================

DELIMITER $$

DROP TRIGGER IF EXISTS trg_evidence_collected_not_future$$

CREATE TRIGGER trg_evidence_collected_not_future
BEFORE INSERT ON evidence
FOR EACH ROW
BEGIN
    IF NEW.collected_at > NOW() THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Evidence collected_at cannot be in the future';
    END IF;
END$$

-- Prevent arrest on expired warrant (critical business logic)
DROP TRIGGER IF EXISTS trg_warrant_not_expired$$

CREATE TRIGGER trg_warrant_not_expired
BEFORE INSERT ON arrest_records
FOR EACH ROW
BEGIN
    IF NEW.warrant_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM warrants 
        WHERE id = NEW.warrant_id AND expires_at < CURDATE()
    ) THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Cannot arrest on expired warrant';
    END IF;
END$$

DELIMITER ;

-- ========================================================================
-- 4. PERFORMANCE INDEXES (non-unique indexes for fast lookups)
-- ========================================================================

-- Crime incident lookups by report date
ALTER TABLE crime_incidents ADD INDEX idx_incident_reported_at (reported_at);

-- Warrant lookups by expiry (for queries on valid warrants)
ALTER TABLE warrants ADD INDEX idx_warrant_expires (expires_at);

-- Court case lookups by status (for active cases queries)
ALTER TABLE court_cases ADD INDEX idx_court_case_status (status);

-- ========================================================================
-- 5. ANALYTICAL VIEWS (demonstrate DB design concepts)
-- ========================================================================

-- View: Active cases with investigation progress
DROP VIEW IF EXISTS v_active_cases;
CREATE VIEW v_active_cases AS
SELECT 
    cf.id,
    cf.case_number,
    ci.title AS incident_title,
    ci.occurred_at,
    CONCAT(p.first_name, ' ', p.last_name) AS investigator,
    cf.priority,
    cf.status,
    COUNT(DISTINCT e.id) AS evidence_count,
    COUNT(DISTINCT cs.person_id) AS suspect_count,
    COUNT(DISTINCT cv.person_id) AS victim_count,
    cf.opened_at,
    DATEDIFF(CURDATE(), DATE(cf.opened_at)) AS days_open
FROM case_files cf
JOIN crime_incidents ci ON ci.id = cf.incident_id
LEFT JOIN users u ON u.id = cf.primary_investigator_id
LEFT JOIN persons p ON p.id = u.person_id
LEFT JOIN evidence e ON e.case_id = cf.id
LEFT JOIN case_suspects cs ON cs.case_id = cf.id
LEFT JOIN case_victims cv ON cv.case_id = cf.id
WHERE cf.status NOT IN ('CLOSED', 'CLOSED_CONVICTED', 'CLOSED_ACQUITTED', 'CLOSED_UNSOLVED')
GROUP BY cf.id, cf.case_number, ci.title, ci.occurred_at, p.first_name, p.last_name, cf.priority, cf.status, cf.opened_at;

-- View: Criminal profile with arrest history
DROP VIEW IF EXISTS v_criminal_profile;
CREATE VIEW v_criminal_profile AS
SELECT 
    p.id,
    CONCAT(p.first_name, ' ', p.last_name) AS full_name,
    p.national_id,
    p.risk_score,
    p.has_active_warrant,
    COUNT(DISTINCT cs.case_id) AS total_suspect_cases,
    COUNT(DISTINCT ar.id) AS total_arrests,
    MAX(ar.arrested_at) AS last_arrested,
    MIN(p.date_of_birth) AS age_calculation_dob,
    p.gang_affiliation
FROM persons p
LEFT JOIN case_suspects cs ON cs.person_id = p.id
LEFT JOIN arrest_records ar ON ar.person_id = p.id
GROUP BY p.id, p.first_name, p.last_name, p.national_id, p.risk_score, p.has_active_warrant, p.gang_affiliation;

-- View: Case closure analysis
DROP VIEW IF EXISTS v_closed_cases_summary;
CREATE VIEW v_closed_cases_summary AS
SELECT 
    cf.id,
    cf.case_number,
    ci.title,
    cf.status AS closure_status,
    cf.closed_at,
    DATEDIFF(DATE(cf.closed_at), DATE(cf.opened_at)) AS investigation_days,
    cf.closure_reason,
    CONCAT(p.first_name, ' ', p.last_name) AS investigator,
    COUNT(DISTINCT e.id) AS evidence_collected,
    COUNT(DISTINCT CASE WHEN cc.status IN ('CONVICTED', 'ACQUITTED') THEN cc.id END) AS court_decisions
FROM case_files cf
JOIN crime_incidents ci ON ci.id = cf.incident_id
LEFT JOIN users u ON u.id = cf.primary_investigator_id
LEFT JOIN persons p ON p.id = u.person_id
LEFT JOIN evidence e ON e.case_id = cf.id
LEFT JOIN court_cases cc ON cc.case_id = cf.id
WHERE cf.status IN ('CLOSED', 'CLOSED_CONVICTED', 'CLOSED_ACQUITTED', 'CLOSED_UNSOLVED')
GROUP BY cf.id, cf.case_number, ci.title, cf.status, cf.closed_at, cf.closure_reason, p.first_name, p.last_name;

-- View: Evidence chain of custody
DROP VIEW IF EXISTS v_evidence_custody_chain;
CREATE VIEW v_evidence_custody_chain AS
SELECT 
    e.id,
    e.evidence_number,
    e.type,
    cf.case_number,
    e.collection_location,
    e.current_storage_location,
    CONCAT(p.first_name, ' ', p.last_name) AS collected_by,
    e.collected_at,
    e.status,
    e.file_path
FROM evidence e
JOIN case_files cf ON cf.id = e.case_id
JOIN users u ON u.id = e.collected_by
JOIN persons p ON p.id = u.person_id
ORDER BY e.collected_at DESC;

-- ========================================================================
-- 6. STORED PROCEDURE: Case closure with audit trail
-- ========================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_close_case$$

CREATE PROCEDURE sp_close_case(
    IN p_case_id BIGINT UNSIGNED,
    IN p_status VARCHAR(50),
    IN p_reason TEXT,
    IN p_user_id BIGINT UNSIGNED
)
READS SQL DATA
MODIFIES SQL DATA
DETERMINISTIC
COMMENT 'Closes a case with proper audit trail and validation'
BEGIN
    DECLARE v_case_exists INT DEFAULT 0;
    DECLARE v_user_exists INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    -- Validate case exists
    SELECT COUNT(*) INTO v_case_exists FROM case_files WHERE id = p_case_id;
    IF v_case_exists = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Case ID not found';
    END IF;
    
    -- Validate user exists
    SELECT COUNT(*) INTO v_user_exists FROM users WHERE id = p_user_id;
    IF v_user_exists = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'User ID not found';
    END IF;
    
    -- Validate closure status
    IF p_status NOT IN ('CLOSED', 'CLOSED_CONVICTED', 'CLOSED_ACQUITTED', 'CLOSED_UNSOLVED') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid closure status';
    END IF;
    
    START TRANSACTION;
    
    -- Update case status
    UPDATE case_files 
    SET status = p_status, 
        closed_at = NOW(), 
        closure_reason = p_reason
    WHERE id = p_case_id;
    
    -- Log to audit trail
    INSERT INTO audit_logs(user_id, user_name, action, entity_type, entity_id, description, `timestamp`)
    SELECT p_user_id, CONCAT(p.first_name, ' ', p.last_name), 'CLOSE_CASE', 'case_files', p_case_id, 
           CONCAT('Case closed with status: ', p_status, '. Reason: ', p_reason), NOW()
    FROM users u 
    JOIN persons p ON p.id = u.person_id
    WHERE u.id = p_user_id;
    
    COMMIT;
END$$

DELIMITER ;

-- ========================================================================
-- 7. STORED PROCEDURE: Generate criminal profile report
-- ========================================================================

DELIMITER $$

DROP PROCEDURE IF EXISTS sp_criminal_profile_report$$

CREATE PROCEDURE sp_criminal_profile_report(
    IN p_person_id BIGINT UNSIGNED
)
READS SQL DATA
COMMENT 'Retrieves comprehensive criminal profile for a person'
BEGIN
    SELECT * FROM v_criminal_profile WHERE id = p_person_id;
END$$

DELIMITER ;

-- ========================================================================
-- DOCUMENTATION: Known Denormalizations (design decisions)
-- ========================================================================

-- =========================
-- ERD SPECIALISATION HIERARCHIES
-- =========================

-- ── Specialisation 1: Overlapping (○), Partial — PERSONS → USERS / CIVILIANS ──
-- Joined-table strategy: every User/Civilian HAS-A Person base record.
-- Overlapping: a person can be BOTH a User AND a Civilian.
-- Partial: not every Person must be a User or Civilian.
-- (Implemented via fk_user_person and fk_civilian_person in table definitions)



-- ── Specialisation 2: Disjoint (⊕), Partial — PERSONS → CASE_SUSPECTS / CASE_VICTIMS / CASE_WITNESSES ──
-- These are WEAK ENTITIES (not bare join tables). They have descriptive attributes beyond the composite PK.
-- Disjoint: a person can hold at most ONE role per case.
-- Partial: not every Person needs to appear in any of these tables.
-- (Implemented via descriptive columns in weak entity table definitions)

-- Drop legacy case_persons table (replaced by three ERD-defined weak entity tables)
DROP TABLE IF EXISTS case_persons;


-- ── Disjoint (⊕) Constraint Triggers ──
-- A person CANNOT hold more than one role in the same case.

DELIMITER $$

CREATE TRIGGER trg_disjoint_victim
BEFORE INSERT ON case_victims
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM case_witnesses WHERE case_id = NEW.case_id AND person_id = NEW.person_id)
    OR EXISTS (SELECT 1 FROM case_suspects WHERE case_id = NEW.case_id AND person_id = NEW.person_id)
    THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ERD Disjoint Violation: Person already has a role in this case.';
    END IF;
END$$

CREATE TRIGGER trg_disjoint_witness
BEFORE INSERT ON case_witnesses
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM case_victims WHERE case_id = NEW.case_id AND person_id = NEW.person_id)
    OR EXISTS (SELECT 1 FROM case_suspects WHERE case_id = NEW.case_id AND person_id = NEW.person_id)
    THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ERD Disjoint Violation: Person already has a role in this case.';
    END IF;
END$$

CREATE TRIGGER trg_disjoint_suspect
BEFORE INSERT ON case_suspects
FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM case_victims WHERE case_id = NEW.case_id AND person_id = NEW.person_id)
    OR EXISTS (SELECT 1 FROM case_witnesses WHERE case_id = NEW.case_id AND person_id = NEW.person_id)
    THEN
        SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'ERD Disjoint Violation: Person already has a role in this case.';
    END IF;
END$$

DELIMITER ;

/*
DESIGN DECISIONS - Known Denormalizations:

1. persons.aliases (TEXT)
   - Stores multiple aliases as newline-separated values
   - Reason: UI simplicity, rare queries on alias_name
   - Could extract to table: person_aliases(person_id, alias_name)
   - For this course project: acceptable denormalization

2. persons.gang_affiliation (VARCHAR 255)
   - Stores primary gang affiliation as single value
   - Reason: Law enforcement priority is single primary gang
   - Could extract: person_gangs(person_id, gang_id) + gangs table
   - Current design: sufficient for case requirements

3. persons.risk_score (VARCHAR 20)
   - DERIVED ATTRIBUTE per ERD. Stored for performance: computed by AI/ML pipeline.
   - Cached here, refreshed periodically. Justified denormalization.

NORMALIZATION IMPROVEMENTS IMPLEMENTED:

✓ case_files.related_case_ids → related_cases M:M table (1NF)
✓ All enum fields protected with CHECK constraints
✓ Foreign key cascade rules enforced
✓ Triggers prevent business logic violations
✓ Indexes on frequently-queried columns
✓ Views provide normalized data presentation layer
✓ Stored procedures encapsulate complex operations

ERD SPECIALISATION HIERARCHIES IMPLEMENTED:

✓ Specialisation 1 — Overlapping (○), Partial: PERSONS → USERS / CIVILIANS
✓ Specialisation 2 — Weak Entities: CASE_ROLES (Disjoint ⊕, Partial)

✓ Normalization — Multivalued Attributes (1NF):
    - persons.distinguishing_marks → person_marks table
    - arrest_records.charges → arrest_charges table
    - warrants.charges → warrant_charges table

✓ Normalization — Strong Entities:
    - promoted court_name to dedicated COURTS table linked via court_id.

  Strategy: Joined-table (table-per-subclass) via person_id FK
  users.person_id UNIQUE FK → persons(id), civilians.person_id UNIQUE FK → persons(id)

✓ Specialisation 2 — Disjoint (⊕), Partial: PERSONS → CASE_SUSPECTS / CASE_VICTIMS / CASE_WITNESSES
  Composite PK (case_id, person_id). Weak entities with descriptive attributes.
  Disjoint constraint enforced via triggers trg_disjoint_victim/witness/suspect.
  Legacy case_persons table dropped.
*/

-- End of Database Enhancements
SET FOREIGN_KEY_CHECKS = 1;
