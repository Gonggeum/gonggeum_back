-- ERD v1.3: identity tables plus stored_files for the users/file cycle.
CREATE TABLE users (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    login_id VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    email VARCHAR(254) COLLATE utf8mb4_0900_as_cs NOT NULL,
    password_hash VARCHAR(255) NULL,
    display_name VARCHAR(20) NOT NULL,
    profile_file_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    withdrawn_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_login_id UNIQUE (login_id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'LOCKED', 'WITHDRAWN'))
) ENGINE=InnoDB;

CREATE TABLE stored_files (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    uploaded_by_user_id BIGINT UNSIGNED NULL,
    storage_provider VARCHAR(20) COLLATE utf8mb4_0900_as_cs NOT NULL,
    bucket_name VARCHAR(100) COLLATE utf8mb4_0900_as_cs NOT NULL,
    object_key VARCHAR(500) COLLATE utf8mb4_0900_as_cs NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_original_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    parent_file_id BIGINT UNSIGNED NULL,
    representation VARCHAR(30) NOT NULL,
    masking_status VARCHAR(20) NOT NULL,
    encryption_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_stored_files_object UNIQUE (storage_provider, bucket_name, object_key),
    INDEX ix_stored_files_original (source_original_sha256),
    INDEX ix_stored_files_cleanup (status, expires_at),
    CONSTRAINT ck_stored_files_representation CHECK (representation IN ('PROCESSING_ORIGINAL','MASKED_DISPLAY','THUMBNAIL','PROFILE','TEAM_IMAGE','EXPORT')),
    CONSTRAINT ck_stored_files_mask CHECK (masking_status IN ('NOT_REQUIRED','PENDING','READY','FAILED')),
    CONSTRAINT ck_stored_files_status CHECK (status IN ('ACTIVE','DELETE_PENDING','DELETE_FAILED','DELETED'))
) ENGINE=InnoDB;

CREATE TABLE terms (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    term_type VARCHAR(30) NOT NULL,
    version_name VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NOT NULL,
    is_required BOOLEAN NOT NULL,
    published_at DATETIME(6) NOT NULL,
    retired_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_terms_version UNIQUE (term_type, version_name),
    INDEX ix_terms_effective (term_type, published_at, retired_at),
    CONSTRAINT ck_terms_type CHECK (term_type IN ('SERVICE','PRIVACY','MARKETING')),
    CONSTRAINT ck_terms_dates CHECK (retired_at IS NULL OR retired_at > published_at),
    CONSTRAINT ck_terms_required CHECK (is_required IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE term_consents (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    term_id BIGINT UNSIGNED NOT NULL,
    is_agreed BOOLEAN NOT NULL,
    agreed_at DATETIME(6) NOT NULL,
    withdrawn_at DATETIME(6) NULL,
    ip_address VARBINARY(16) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_term_consents_user_term UNIQUE (user_id, term_id),
    CONSTRAINT ck_term_consents_agreed CHECK (is_agreed IN (0,1))
) ENGINE=InnoDB;

CREATE TABLE user_sessions (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    refresh_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    device_name VARCHAR(200) NULL,
    user_agent VARCHAR(500) NULL,
    ip_address VARBINARY(16) NULL,
    issued_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    revoke_reason VARCHAR(50) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_sessions_refresh_hash UNIQUE (refresh_token_hash),
    INDEX ix_sessions_user_active (user_id, revoked_at, expires_at),
    INDEX ix_sessions_expiry (expires_at),
    CONSTRAINT ck_sessions_dates CHECK (expires_at > issued_at)
) ENGINE=InnoDB;

CREATE TABLE social_accounts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) COLLATE utf8mb4_0900_as_cs NOT NULL,
    provider_email VARCHAR(254) NULL,
    linked_at DATETIME(6) NOT NULL,
    last_login_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_social_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uk_social_user_provider UNIQUE (user_id, provider),
    CONSTRAINT ck_social_provider CHECK (provider IN ('GOOGLE','KAKAO','APPLE'))
) ENGINE=InnoDB;

CREATE TABLE verification_tokens (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    target_email VARCHAR(254) NULL,
    purpose VARCHAR(30) NOT NULL,
    challenge_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_token_id BIGINT UNSIGNED NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    hash_key_version VARCHAR(30) NULL,
    context_json JSON NULL,
    attempt_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    resend_available_at DATETIME(6) NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_verification_challenge UNIQUE (challenge_id),
    CONSTRAINT uk_verification_parent UNIQUE (parent_token_id),
    INDEX ix_verification_email (target_email),
    INDEX ix_verification_expiry (expires_at),
    CONSTRAINT ck_verification_purpose CHECK (purpose IN ('RESET_CODE','RESET_GRANT','LOGIN_2FA','REAUTH','EMAIL_VERIFY')),
    CONSTRAINT ck_verification_parent_purpose CHECK ((purpose = 'RESET_GRANT' AND parent_token_id IS NOT NULL) OR (purpose <> 'RESET_GRANT' AND parent_token_id IS NULL)),
    CONSTRAINT ck_verification_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB;

CREATE TABLE user_security_settings (
    user_id BIGINT UNSIGNED NOT NULL,
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    totp_secret_ciphertext VARBINARY(512) NULL,
    totp_enabled_at DATETIME(6) NULL,
    totp_failed_count TINYINT UNSIGNED NOT NULL DEFAULT 0,
    totp_locked_until DATETIME(6) NULL,
    last_accepted_totp_step BIGINT NULL,
    auto_lock_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_lock_minutes SMALLINT UNSIGNED NULL DEFAULT 15,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_security_totp CHECK (totp_enabled IN (0,1) AND (totp_enabled = 0 OR (totp_secret_ciphertext IS NOT NULL AND totp_enabled_at IS NOT NULL))),
    CONSTRAINT ck_security_autolock CHECK (auto_lock_enabled IN (0,1) AND (auto_lock_minutes IS NULL OR auto_lock_minutes IN (10,15,30,60)) AND (auto_lock_enabled = 0 OR auto_lock_minutes IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE totp_recovery_codes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_recovery_hash UNIQUE (code_hash)
) ENGINE=InnoDB;

CREATE TABLE login_histories (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    login_identifier_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    session_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    success BOOLEAN NOT NULL,
    failure_reason VARCHAR(50) NULL,
    ip_address VARBINARY(16) NULL,
    user_agent VARCHAR(500) NULL,
    occurred_at DATETIME(6) NOT NULL,
    auth_stage VARCHAR(20) NOT NULL,
    login_attempt_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_login_user_time (user_id, occurred_at),
    INDEX ix_login_attempt (login_attempt_id),
    INDEX ix_login_time (occurred_at),
    CONSTRAINT ck_login_success CHECK (success IN (0,1)),
    CONSTRAINT ck_login_stage CHECK (auth_stage IN ('PASSWORD','SOCIAL','OTP','RECOVERY','COMPLETE')),
    CONSTRAINT ck_login_complete CHECK (success = 0 OR auth_stage = 'COMPLETE')
) ENGINE=InnoDB;

CREATE TABLE notification_settings (
    user_id BIGINT UNSIGNED NOT NULL,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    team_invite_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    expense_created_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    budget_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    role_changed_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    dnd_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    dnd_start_time TIME NULL,
    dnd_end_time TIME NULL,
    timezone VARCHAR(40) NOT NULL DEFAULT 'Asia/Seoul',
    updated_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_notification_inapp CHECK (in_app_enabled = 1),
    CONSTRAINT ck_notification_flags CHECK (push_enabled IN (0,1) AND team_invite_enabled IN (0,1) AND expense_created_enabled IN (0,1) AND budget_alert_enabled IN (0,1) AND role_changed_enabled IN (0,1) AND dnd_enabled IN (0,1)),
    CONSTRAINT ck_notification_dnd CHECK (dnd_enabled = 0 OR (dnd_start_time IS NOT NULL AND dnd_end_time IS NOT NULL AND CHAR_LENGTH(timezone) > 0))
) ENGINE=InnoDB;

ALTER TABLE users ADD CONSTRAINT fk_users_profile FOREIGN KEY (profile_file_id) REFERENCES stored_files(id) ON DELETE RESTRICT;
ALTER TABLE stored_files
    ADD CONSTRAINT fk_files_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_files_parent FOREIGN KEY (parent_file_id) REFERENCES stored_files(id) ON DELETE SET NULL;
ALTER TABLE term_consents
    ADD CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_consents_term FOREIGN KEY (term_id) REFERENCES terms(id) ON DELETE RESTRICT;
ALTER TABLE user_sessions ADD CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE social_accounts ADD CONSTRAINT fk_social_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE verification_tokens
    ADD CONSTRAINT fk_verification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_verification_parent FOREIGN KEY (parent_token_id) REFERENCES verification_tokens(id) ON DELETE RESTRICT;
ALTER TABLE user_security_settings ADD CONSTRAINT fk_security_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE totp_recovery_codes ADD CONSTRAINT fk_recovery_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
ALTER TABLE login_histories
    ADD CONSTRAINT fk_login_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_login_session FOREIGN KEY (session_id) REFERENCES user_sessions(id) ON DELETE SET NULL;
ALTER TABLE notification_settings ADD CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;
