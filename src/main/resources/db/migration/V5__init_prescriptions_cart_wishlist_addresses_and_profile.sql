-- Phase 4: Medicine classification rules, prescriptions & audit logs, customer profile, delivery addresses, cart, wishlist, and recently viewed

ALTER TABLE medicines
    ADD COLUMN classification_code VARCHAR(40) NOT NULL DEFAULT 'OTC';

UPDATE medicines
SET classification_code = CASE
    WHEN prescription_required = TRUE THEN 'SCHEDULE_H'
    ELSE 'OTC'
END;

CREATE TABLE IF NOT EXISTS medicine_classification_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    classification_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    prescription_required BOOLEAN NOT NULL DEFAULT FALSE,
    default_rx_validity_days INT NOT NULL DEFAULT 180,
    max_units_per_order INT NOT NULL DEFAULT 30,
    auto_substitution_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    requires_pharmacist_license_check BOOLEAN NOT NULL DEFAULT TRUE,
    warning_text VARCHAR(500) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_medicine_classification_rules PRIMARY KEY (id),
    CONSTRAINT uk_classification_code UNIQUE (classification_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO medicine_classification_rules (
    classification_code,
    display_name,
    prescription_required,
    default_rx_validity_days,
    max_units_per_order,
    auto_substitution_allowed,
    requires_pharmacist_license_check,
    warning_text,
    is_active
) VALUES
    ('OTC', 'Over-the-Counter (OTC)', FALSE, 365, 50, FALSE, FALSE, 'Read label instructions carefully before use.', TRUE),
    ('SCHEDULE_H', 'Schedule H Prescription Medicine', TRUE, 180, 30, FALSE, TRUE, 'Schedule H drug: cannot be dispensed or substituted without a verified medical practitioner prescription.', TRUE),
    ('SCHEDULE_H1', 'Schedule H1 Restricted Antibiotic / Psychotropic', TRUE, 30, 15, FALSE, TRUE, 'Schedule H1 restricted medicine: strict pharmacist verification and identity audit required; no substitution.', TRUE),
    ('SCHEDULE_X', 'Schedule X Controlled Substance', TRUE, 14, 10, FALSE, TRUE, 'Schedule X controlled medicine: short validity prescription required and dispensed strictly as prescribed.', TRUE)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    prescription_required = VALUES(prescription_required),
    auto_substitution_allowed = VALUES(auto_substitution_allowed),
    warning_text = VALUES(warning_text);

CREATE TABLE IF NOT EXISTS customer_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    date_of_birth DATE NULL,
    gender VARCHAR(30) NULL,
    blood_group VARCHAR(15) NULL,
    allergies VARCHAR(500) NULL,
    chronic_conditions VARCHAR(500) NULL,
    emergency_contact_name VARCHAR(150) NULL,
    emergency_contact_phone VARCHAR(30) NULL,
    preferred_language VARCHAR(40) NOT NULL DEFAULT 'English',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_profiles PRIMARY KEY (id),
    CONSTRAINT uk_customer_profiles_user UNIQUE (user_id),
    CONSTRAINT fk_customer_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS customer_addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    label VARCHAR(60) NOT NULL,
    recipient_name VARCHAR(150) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    address_line1 VARCHAR(250) NOT NULL,
    address_line2 VARCHAR(250) NULL,
    landmark VARCHAR(150) NULL,
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_addresses PRIMARY KEY (id),
    CONSTRAINT fk_customer_addresses_user FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_customer_addresses_customer (customer_id, is_default),
    INDEX idx_customer_addresses_postal (postal_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prescriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    pharmacy_id BIGINT NULL,
    reference_code VARCHAR(64) NOT NULL,
    patient_name VARCHAR(150) NOT NULL,
    patient_age INT NULL,
    doctor_name VARCHAR(150) NOT NULL,
    doctor_registration_number VARCHAR(120) NULL,
    clinic_name VARCHAR(200) NULL,
    issued_date DATE NOT NULL,
    valid_until DATE NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    notes VARCHAR(1000) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    rejection_reason VARCHAR(500) NULL,
    review_notes VARCHAR(500) NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_by_pharmacy_id BIGINT NULL,
    pharmacist_name VARCHAR(150) NULL,
    pharmacist_registration_number VARCHAR(120) NULL,
    reviewed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_prescriptions PRIMARY KEY (id),
    CONSTRAINT uk_prescriptions_ref UNIQUE (reference_code),
    CONSTRAINT fk_prescriptions_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_prescriptions_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies (id) ON DELETE SET NULL,
    CONSTRAINT fk_prescriptions_reviewed_pharmacy FOREIGN KEY (reviewed_by_pharmacy_id) REFERENCES pharmacies (id) ON DELETE SET NULL,
    INDEX idx_prescriptions_customer (customer_id, status, created_at),
    INDEX idx_prescriptions_pharmacy_status (pharmacy_id, status, created_at),
    INDEX idx_prescriptions_valid_until (status, valid_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prescription_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    actor_role VARCHAR(30) NOT NULL,
    actor_name VARCHAR(150) NOT NULL,
    action VARCHAR(60) NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NULL,
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_prescription_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_rx_audit_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE CASCADE,
    INDEX idx_rx_audit_prescription_time (prescription_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS prescription_order_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    prescription_id BIGINT NOT NULL,
    order_id BIGINT NULL,
    order_reference VARCHAR(80) NULL,
    medicine_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    pharmacy_id BIGINT NOT NULL,
    verification_status VARCHAR(40) NOT NULL,
    verified_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_prescription_order_links PRIMARY KEY (id),
    CONSTRAINT fk_rx_order_link_rx FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE CASCADE,
    CONSTRAINT fk_rx_order_link_med FOREIGN KEY (medicine_id) REFERENCES medicines (id) ON DELETE CASCADE,
    CONSTRAINT fk_rx_order_link_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_rx_order_link_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies (id) ON DELETE CASCADE,
    INDEX idx_rx_order_links_rx (prescription_id),
    INDEX idx_rx_order_links_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    medicine_id BIGINT NOT NULL,
    pharmacy_id BIGINT NOT NULL,
    prescription_id BIGINT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT uk_cart_customer_medicine UNIQUE (customer_id, medicine_id),
    CONSTRAINT fk_cart_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_prescription FOREIGN KEY (prescription_id) REFERENCES prescriptions (id) ON DELETE SET NULL,
    INDEX idx_cart_customer_pharmacy (customer_id, pharmacy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS wishlist_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    medicine_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_wishlist_items PRIMARY KEY (id),
    CONSTRAINT uk_wishlist_customer_medicine UNIQUE (customer_id, medicine_id),
    CONSTRAINT fk_wishlist_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wishlist_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id) ON DELETE CASCADE,
    INDEX idx_wishlist_customer (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS recently_viewed_medicines (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_id BIGINT NOT NULL,
    medicine_id BIGINT NOT NULL,
    viewed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_recently_viewed_medicines PRIMARY KEY (id),
    CONSTRAINT uk_recently_viewed UNIQUE (customer_id, medicine_id),
    CONSTRAINT fk_recent_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_recent_medicine FOREIGN KEY (medicine_id) REFERENCES medicines (id) ON DELETE CASCADE,
    INDEX idx_recent_customer_time (customer_id, viewed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
