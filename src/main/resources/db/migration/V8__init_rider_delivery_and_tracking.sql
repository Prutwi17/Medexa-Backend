-- Phase 7: Rider KYC, Delivery Zones, Automatic Rider Assignment, Delivery Lifecycle, OTP Verification & Earnings

ALTER TABLE pharmacies ADD COLUMN latitude DECIMAL(9, 6) NULL;
ALTER TABLE pharmacies ADD COLUMN longitude DECIMAL(9, 6) NULL;

ALTER TABLE customer_addresses ADD COLUMN latitude DECIMAL(9, 6) NULL;
ALTER TABLE customer_addresses ADD COLUMN longitude DECIMAL(9, 6) NULL;

ALTER TABLE riders ADD COLUMN identity_document_type VARCHAR(50) NULL;
ALTER TABLE riders ADD COLUMN identity_document_number VARCHAR(100) NULL;
ALTER TABLE riders ADD COLUMN identity_document_url VARCHAR(500) NULL;
ALTER TABLE riders ADD COLUMN driving_license_expiry DATE NULL;
ALTER TABLE riders ADD COLUMN driving_license_url VARCHAR(500) NULL;
ALTER TABLE riders ADD COLUMN vehicle_model VARCHAR(120) NULL;
ALTER TABLE riders ADD COLUMN current_latitude DECIMAL(9, 6) NULL;
ALTER TABLE riders ADD COLUMN current_longitude DECIMAL(9, 6) NULL;
ALTER TABLE riders ADD COLUMN location_updated_at TIMESTAMP(6) NULL;
ALTER TABLE riders ADD COLUMN max_concurrent_deliveries INT NOT NULL DEFAULT 2;
ALTER TABLE riders ADD COLUMN base_fee_per_delivery DECIMAL(10, 2) NOT NULL DEFAULT 40.00;

CREATE TABLE IF NOT EXISTS rider_delivery_zones (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rider_id BIGINT NOT NULL,
    zone_name VARCHAR(120) NOT NULL,
    city VARCHAR(100) NOT NULL,
    postal_code VARCHAR(20) NOT NULL,
    priority_order INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_rider_delivery_zones PRIMARY KEY (id),
    CONSTRAINT uk_rider_zone_postal UNIQUE (rider_id, postal_code),
    CONSTRAINT fk_rider_zones_rider FOREIGN KEY (rider_id) REFERENCES riders (id) ON DELETE CASCADE,
    INDEX idx_rider_zones_postal_active (postal_code, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_reference VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    pharmacy_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    assigned_rider_id BIGINT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'WAITING_FOR_RIDER',
    pickup_postal_code VARCHAR(20) NOT NULL,
    drop_postal_code VARCHAR(20) NOT NULL,
    distance_km DECIMAL(8, 2) NOT NULL DEFAULT 0.00,
    assignment_attempts INT NOT NULL DEFAULT 0,
    assigned_at TIMESTAMP(6) NULL,
    assignment_expires_at TIMESTAMP(6) NULL,
    accepted_at TIMESTAMP(6) NULL,
    picked_up_at TIMESTAMP(6) NULL,
    out_for_delivery_at TIMESTAMP(6) NULL,
    delivered_at TIMESTAMP(6) NULL,
    failed_at TIMESTAMP(6) NULL,
    otp_hash VARCHAR(128) NULL,
    customer_otp_code VARCHAR(10) NULL,
    otp_expires_at TIMESTAMP(6) NULL,
    otp_attempts INT NOT NULL DEFAULT 0,
    max_otp_attempts INT NOT NULL DEFAULT 3,
    otp_verified_at TIMESTAMP(6) NULL,
    proof_type VARCHAR(40) NULL,
    delivery_proof_url VARCHAR(500) NULL,
    recipient_confirmed_name VARCHAR(150) NULL,
    delivery_notes VARCHAR(500) NULL,
    failure_reason VARCHAR(500) NULL,
    rider_lat DECIMAL(9, 6) NULL,
    rider_lng DECIMAL(9, 6) NULL,
    rider_location_updated_at TIMESTAMP(6) NULL,
    rider_earning_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_deliveries PRIMARY KEY (id),
    CONSTRAINT uk_deliveries_reference UNIQUE (delivery_reference),
    CONSTRAINT uk_deliveries_order UNIQUE (order_id),
    CONSTRAINT fk_deliveries_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_deliveries_pharmacy FOREIGN KEY (pharmacy_id) REFERENCES pharmacies (id) ON DELETE CASCADE,
    CONSTRAINT fk_deliveries_customer FOREIGN KEY (customer_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_deliveries_rider FOREIGN KEY (assigned_rider_id) REFERENCES riders (id) ON DELETE SET NULL,
    INDEX idx_deliveries_status_exp (status, assignment_expires_at),
    INDEX idx_deliveries_rider_status (assigned_rider_id, status),
    INDEX idx_deliveries_customer (customer_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS delivery_assignment_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_id BIGINT NOT NULL,
    rider_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    distance_km DECIMAL(8, 2) NOT NULL DEFAULT 0.00,
    ranking_score DECIMAL(10, 4) NOT NULL DEFAULT 0.0000,
    offered_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NULL,
    responded_at TIMESTAMP(6) NULL,
    rejection_reason VARCHAR(500) NULL,
    CONSTRAINT pk_delivery_attempts PRIMARY KEY (id),
    CONSTRAINT fk_delivery_attempts_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_attempts_rider FOREIGN KEY (rider_id) REFERENCES riders (id) ON DELETE CASCADE,
    INDEX idx_delivery_attempts_delivery (delivery_id, attempt_number),
    INDEX idx_delivery_attempts_rider (rider_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS delivery_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    delivery_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(40) NULL,
    new_status VARCHAR(40) NOT NULL,
    actor_user_id BIGINT NULL,
    actor_role VARCHAR(30) NOT NULL,
    actor_name VARCHAR(150) NOT NULL,
    notes VARCHAR(500) NULL,
    latitude DECIMAL(9, 6) NULL,
    longitude DECIMAL(9, 6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_delivery_events PRIMARY KEY (id),
    CONSTRAINT fk_delivery_events_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id) ON DELETE CASCADE,
    INDEX idx_delivery_events_time (delivery_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rider_earnings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    earning_reference VARCHAR(64) NOT NULL,
    rider_id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    base_fee DECIMAL(10, 2) NOT NULL,
    distance_bonus DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    incentive_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    total_earning DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'ACCRUED',
    earned_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_rider_earnings PRIMARY KEY (id),
    CONSTRAINT uk_rider_earnings_ref UNIQUE (earning_reference),
    CONSTRAINT uk_rider_earnings_delivery UNIQUE (delivery_id),
    CONSTRAINT uk_rider_earnings_order UNIQUE (order_id),
    CONSTRAINT fk_rider_earnings_rider FOREIGN KEY (rider_id) REFERENCES riders (id) ON DELETE CASCADE,
    CONSTRAINT fk_rider_earnings_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id) ON DELETE CASCADE,
    CONSTRAINT fk_rider_earnings_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    INDEX idx_rider_earnings_rider_time (rider_id, earned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
