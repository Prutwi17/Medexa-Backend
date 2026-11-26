-- Phase 2: Authentication, security tokens, login history, and onboarding profiles

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(190) NOT NULL,
    phone_number VARCHAR(30) NULL,
    password_hash VARCHAR(255) NULL,
    role VARCHAR(30) NOT NULL,
    account_status VARCHAR(40) NOT NULL,
    auth_provider VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    google_sub VARCHAR(128) NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_google_sub UNIQUE (google_sub),
    INDEX idx_users_role_status (role, account_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    token_family_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    revocation_reason VARCHAR(100) NULL,
    replaced_by_token_hash VARCHAR(64) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_family (token_family_id),
    INDEX idx_refresh_tokens_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS login_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    email_attempted VARCHAR(190) NOT NULL,
    auth_method VARCHAR(30) NOT NULL,
    outcome VARCHAR(40) NOT NULL,
    failure_reason VARCHAR(200) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    correlation_id VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_login_history PRIMARY KEY (id),
    CONSTRAINT fk_login_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_login_history_email_time (email_attempted, created_at),
    INDEX idx_login_history_user_time (user_id, created_at),
    INDEX idx_login_history_ip_time (ip_address, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_email_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_email_verification_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_email_verification_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    requested_ip VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_password_reset_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pharmacies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    pharmacy_name VARCHAR(200) NOT NULL,
    slug VARCHAR(220) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    address_line VARCHAR(300) NOT NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    verification_status VARCHAR(40) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    rejection_reason VARCHAR(500) NULL,
    is_accepting_orders BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP(6) NULL,
    verified_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_pharmacies PRIMARY KEY (id),
    CONSTRAINT uk_pharmacies_user UNIQUE (user_id),
    CONSTRAINT uk_pharmacies_slug UNIQUE (slug),
    CONSTRAINT fk_pharmacies_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_pharmacies_status_city (verification_status, city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pharmacy_licenses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pharmacy_id BIGINT NOT NULL,
    license_number VARCHAR(120) NOT NULL,
    issuing_authority VARCHAR(180) NOT NULL,
    pharmacist_name VARCHAR(150) NOT NULL,
    pharmacist_registration_number VARCHAR(120) NOT NULL,
    expiry_date DATE NOT NULL,
    document_url VARCHAR(500) NULL,
    verification_status VARCHAR(40) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP(6) NULL,
    review_notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_pharmacy_licenses PRIMARY KEY (id),
    CONSTRAINT uk_pharmacy_licenses_number UNIQUE (license_number),
    CONSTRAINT fk_pharmacy_licenses_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies (id) ON DELETE CASCADE,
    INDEX idx_pharmacy_licenses_status (verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS riders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    phone_number VARCHAR(30) NOT NULL,
    vehicle_type VARCHAR(40) NOT NULL,
    vehicle_registration_number VARCHAR(60) NOT NULL,
    driving_license_number VARCHAR(100) NOT NULL,
    service_city VARCHAR(100) NOT NULL,
    verification_status VARCHAR(40) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    availability_status VARCHAR(30) NOT NULL DEFAULT 'OFFLINE',
    rejection_reason VARCHAR(500) NULL,
    verified_at TIMESTAMP(6) NULL,
    verified_by BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_riders PRIMARY KEY (id),
    CONSTRAINT uk_riders_user UNIQUE (user_id),
    CONSTRAINT uk_riders_license UNIQUE (driving_license_number),
    CONSTRAINT fk_riders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_riders_status_city (verification_status, service_city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
