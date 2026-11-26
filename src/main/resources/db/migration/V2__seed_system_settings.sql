-- Phase 1: Initial platform system settings (non-sensitive operational defaults)

INSERT INTO system_settings (setting_key, setting_value, value_type, description, is_public)
VALUES
    ('platform.name', 'Medexa', 'STRING', 'Platform display name', TRUE),
    ('platform.currency', 'INR', 'STRING', 'Default marketplace settlement currency', TRUE),
    ('platform.support_email', 'support@medexa.in', 'STRING', 'Customer and partner support email', TRUE),
    ('checkout.max_items_per_session', '50', 'INTEGER', 'Maximum distinct line items permitted in one checkout session', FALSE),
    ('delivery.otp_expiry_minutes', '15', 'INTEGER', 'Validity duration in minutes for customer delivery confirmation OTP', FALSE),
    ('prescription.max_upload_mb', '10', 'INTEGER', 'Maximum prescription document size in megabytes', TRUE)
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    is_public = VALUES(is_public);
