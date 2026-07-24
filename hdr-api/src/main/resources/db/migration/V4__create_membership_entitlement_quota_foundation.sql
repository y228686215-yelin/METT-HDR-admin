CREATE TABLE entitlement_definitions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    entitlement_type VARCHAR(32) NOT NULL,
    quota_unit VARCHAR(64) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_entitlement_definitions_code (code),
    KEY idx_entitlement_definitions_type_status (entitlement_type, status),
    CONSTRAINT chk_entitlement_definitions_type
        CHECK (entitlement_type IN ('BOOLEAN', 'QUOTA')),
    CONSTRAINT chk_entitlement_definitions_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE membership_plans (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    plan_code VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    audience_type VARCHAR(32) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    usage_cycle VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_plans_code_version (plan_code, version),
    KEY idx_membership_plans_audience_status (audience_type, status),
    KEY idx_membership_plans_default (audience_type, is_default, status),
    CONSTRAINT chk_membership_plans_version CHECK (version > 0),
    CONSTRAINT chk_membership_plans_audience
        CHECK (audience_type IN ('PERSONAL', 'TEAM')),
    CONSTRAINT chk_membership_plans_tier
        CHECK (tier IN ('FREE', 'PLUS', 'PRO')),
    CONSTRAINT chk_membership_plans_cycle
        CHECK (usage_cycle IN ('MONTHLY')),
    CONSTRAINT chk_membership_plans_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE membership_plan_entitlements (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    membership_plan_id BIGINT UNSIGNED NOT NULL,
    entitlement_definition_id BIGINT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL,
    quota_limit BIGINT NULL,
    is_unlimited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_plan_entitlements_plan_definition
        (membership_plan_id, entitlement_definition_id),
    KEY idx_membership_plan_entitlements_definition (entitlement_definition_id),
    CONSTRAINT fk_membership_plan_entitlements_plan
        FOREIGN KEY (membership_plan_id) REFERENCES membership_plans (id),
    CONSTRAINT fk_membership_plan_entitlements_definition
        FOREIGN KEY (entitlement_definition_id) REFERENCES entitlement_definitions (id),
    CONSTRAINT chk_membership_plan_entitlements_limit
        CHECK (quota_limit IS NULL OR quota_limit >= 0),
    CONSTRAINT chk_membership_plan_entitlements_unlimited
        CHECK (NOT (is_unlimited = TRUE AND quota_limit IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE memberships (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    subject_type VARCHAR(32) NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    team_id BIGINT UNSIGNED NULL,
    membership_plan_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    current_marker TINYINT NULL,
    started_at DATETIME(3) NOT NULL,
    current_period_start_at DATETIME(3) NOT NULL,
    current_period_end_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NULL,
    suspended_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    replaced_at DATETIME(3) NULL,
    created_by_user_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_memberships_current_user (user_id, current_marker),
    UNIQUE KEY uk_memberships_current_team (team_id, current_marker),
    KEY idx_memberships_plan (membership_plan_id),
    KEY idx_memberships_user_history (user_id, created_at),
    KEY idx_memberships_team_history (team_id, created_at),
    KEY idx_memberships_status_period (status, current_period_end_at),
    CONSTRAINT fk_memberships_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_memberships_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_memberships_plan FOREIGN KEY (membership_plan_id) REFERENCES membership_plans (id),
    CONSTRAINT fk_memberships_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_memberships_subject
        CHECK (
            (subject_type = 'USER' AND user_id IS NOT NULL AND team_id IS NULL)
            OR
            (subject_type = 'TEAM' AND team_id IS NOT NULL AND user_id IS NULL)
        ),
    CONSTRAINT chk_memberships_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELLED', 'REPLACED')),
    CONSTRAINT chk_memberships_source
        CHECK (source IN ('SYSTEM_DEFAULT', 'MANUAL', 'PAYMENT', 'ADMIN')),
    CONSTRAINT chk_memberships_current_marker
        CHECK (current_marker IS NULL OR current_marker = 1),
    CONSTRAINT chk_memberships_historical_marker
        CHECK (
            status NOT IN ('EXPIRED', 'CANCELLED', 'REPLACED')
            OR current_marker IS NULL
        ),
    CONSTRAINT chk_memberships_period
        CHECK (current_period_end_at > current_period_start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE membership_usage (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    membership_id BIGINT UNSIGNED NOT NULL,
    entitlement_definition_id BIGINT UNSIGNED NOT NULL,
    cycle_start_at DATETIME(3) NOT NULL,
    cycle_end_at DATETIME(3) NOT NULL,
    quota_limit_snapshot BIGINT NULL,
    is_unlimited BOOLEAN NOT NULL DEFAULT FALSE,
    used_count BIGINT NOT NULL DEFAULT 0,
    reserved_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_usage_cycle
        (membership_id, entitlement_definition_id, cycle_start_at),
    KEY idx_membership_usage_entitlement (entitlement_definition_id),
    KEY idx_membership_usage_cycle_end (cycle_end_at),
    CONSTRAINT fk_membership_usage_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id),
    CONSTRAINT fk_membership_usage_definition
        FOREIGN KEY (entitlement_definition_id) REFERENCES entitlement_definitions (id),
    CONSTRAINT chk_membership_usage_counts
        CHECK (used_count >= 0 AND reserved_count >= 0),
    CONSTRAINT chk_membership_usage_limit
        CHECK (quota_limit_snapshot IS NULL OR quota_limit_snapshot >= 0),
    CONSTRAINT chk_membership_usage_limit_mode
        CHECK (
            (is_unlimited = TRUE AND quota_limit_snapshot IS NULL)
            OR
            (is_unlimited = FALSE AND quota_limit_snapshot IS NOT NULL)
        ),
    CONSTRAINT chk_membership_usage_capacity
        CHECK (
            is_unlimited = TRUE
            OR used_count + reserved_count <= quota_limit_snapshot
        ),
    CONSTRAINT chk_membership_usage_cycle
        CHECK (cycle_end_at > cycle_start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE quota_transactions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    membership_id BIGINT UNSIGNED NOT NULL,
    membership_usage_id BIGINT UNSIGNED NOT NULL,
    entitlement_definition_id BIGINT UNSIGNED NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    used_before BIGINT NOT NULL,
    used_after BIGINT NOT NULL,
    reserved_before BIGINT NOT NULL,
    reserved_after BIGINT NOT NULL,
    quota_limit_snapshot BIGINT NULL,
    reservation_key VARCHAR(128) NULL,
    related_object_type VARCHAR(64) NULL,
    related_object_id VARCHAR(128) NULL,
    reason VARCHAR(500) NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_quota_transactions_operation_key (operation_key),
    KEY idx_quota_transactions_membership_created (membership_id, created_at),
    KEY idx_quota_transactions_usage_created (membership_usage_id, created_at),
    KEY idx_quota_transactions_reservation
        (membership_id, entitlement_definition_id, reservation_key),
    CONSTRAINT fk_quota_transactions_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id),
    CONSTRAINT fk_quota_transactions_usage
        FOREIGN KEY (membership_usage_id) REFERENCES membership_usage (id),
    CONSTRAINT fk_quota_transactions_definition
        FOREIGN KEY (entitlement_definition_id) REFERENCES entitlement_definitions (id),
    CONSTRAINT fk_quota_transactions_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT chk_quota_transactions_type
        CHECK (change_type IN (
            'CONSUME', 'RESTORE', 'RESERVE', 'COMMIT', 'RELEASE',
            'GRANT', 'ADJUST', 'EXPIRE', 'REFUND'
        )),
    CONSTRAINT chk_quota_transactions_amount CHECK (amount > 0),
    CONSTRAINT chk_quota_transactions_counts
        CHECK (
            used_before >= 0 AND used_after >= 0
            AND reserved_before >= 0 AND reserved_after >= 0
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE usage_snapshots (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    membership_id BIGINT UNSIGNED NOT NULL,
    cycle_start_at DATETIME(3) NOT NULL,
    cycle_end_at DATETIME(3) NOT NULL,
    snapshot_data JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_usage_snapshots_membership_cycle
        (membership_id, cycle_start_at, cycle_end_at),
    CONSTRAINT fk_usage_snapshots_membership
        FOREIGN KEY (membership_id) REFERENCES memberships (id),
    CONSTRAINT chk_usage_snapshots_cycle
        CHECK (cycle_end_at > cycle_start_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO entitlement_definitions (
    code, name, description, entitlement_type, quota_unit, status
) VALUES
('HDR_ACCESS', 'HDR Access', 'Allows access to HDR application capabilities.', 'BOOLEAN', NULL, 'ACTIVE'),
('TEAM_WORKSPACE_ACCESS', 'Team Workspace Access', 'Allows membership-backed team workspace access.', 'BOOLEAN', NULL, 'ACTIVE'),
('PROJECT_COUNT', 'Project Count', 'Reserved project-count entitlement category.', 'QUOTA', 'COUNT', 'ACTIVE'),
('SPACE_COUNT', 'Space Count', 'Reserved space-count entitlement category.', 'QUOTA', 'COUNT', 'ACTIVE'),
('ANALYSIS_RUN_COUNT', 'Analysis Run Count', 'Reserved analysis-run entitlement category.', 'QUOTA', 'COUNT', 'ACTIVE'),
('REPORT_EXPORT_COUNT', 'Report Export Count', 'Reserved report-export entitlement category.', 'QUOTA', 'COUNT', 'ACTIVE'),
('PRODUCT_COMPARE_COUNT', 'Product Compare Count', 'Reserved product-compare entitlement category.', 'QUOTA', 'COUNT', 'ACTIVE'),
('PRODUCT_UPLOAD_COUNT', 'Product Upload Count', 'Reserved product-upload entitlement category.', 'QUOTA', 'COUNT', 'ACTIVE'),
('TEAM_SEAT_COUNT', 'Team Seat Count', 'Reserved team-seat entitlement category.', 'QUOTA', 'COUNT', 'ACTIVE'),
('EXPERT_CONSULTATION_COUNT', 'Expert Consultation Count', 'Reserved consultation entitlement category.', 'QUOTA', 'COUNT', 'ACTIVE');

INSERT INTO membership_plans (
    plan_code, version, name, audience_type, tier, usage_cycle, status, is_default
) VALUES
('PERSONAL_FREE', 1, 'Personal Free', 'PERSONAL', 'FREE', 'MONTHLY', 'ACTIVE', TRUE),
('PERSONAL_PLUS', 1, 'Personal Plus', 'PERSONAL', 'PLUS', 'MONTHLY', 'ACTIVE', FALSE),
('PERSONAL_PRO', 1, 'Personal Pro', 'PERSONAL', 'PRO', 'MONTHLY', 'ACTIVE', FALSE),
('TEAM_PLUS', 1, 'Team Plus', 'TEAM', 'PLUS', 'MONTHLY', 'ACTIVE', FALSE),
('TEAM_PRO', 1, 'Team Pro', 'TEAM', 'PRO', 'MONTHLY', 'ACTIVE', FALSE);

INSERT INTO membership_plan_entitlements (
    membership_plan_id, entitlement_definition_id, enabled, quota_limit, is_unlimited
)
SELECT p.id, d.id, TRUE, NULL, FALSE
FROM membership_plans p
JOIN entitlement_definitions d ON d.code = 'HDR_ACCESS'
WHERE p.plan_code IN ('PERSONAL_FREE', 'PERSONAL_PLUS', 'PERSONAL_PRO')
  AND p.version = 1;

INSERT INTO membership_plan_entitlements (
    membership_plan_id, entitlement_definition_id, enabled, quota_limit, is_unlimited
)
SELECT p.id, d.id, TRUE, NULL, FALSE
FROM membership_plans p
JOIN entitlement_definitions d ON d.code = 'TEAM_WORKSPACE_ACCESS'
WHERE p.plan_code IN ('TEAM_PLUS', 'TEAM_PRO')
  AND p.version = 1;

INSERT INTO memberships (
    subject_type, user_id, team_id, membership_plan_id, status, source,
    current_marker, started_at, current_period_start_at, current_period_end_at
)
SELECT
    'USER', u.id, NULL, p.id, 'ACTIVE', 'SYSTEM_DEFAULT',
    1, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3),
    DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL 1 MONTH)
FROM users u
JOIN membership_plans p
  ON p.plan_code = 'PERSONAL_FREE'
 AND p.version = 1
 AND p.status = 'ACTIVE'
WHERE NOT EXISTS (
    SELECT 1
    FROM memberships m
    WHERE m.user_id = u.id
      AND m.current_marker = 1
);

INSERT INTO audit_logs (
    actor_user_id, action, resource_type, resource_id, source_system
)
SELECT
    NULL, 'MEMBERSHIP_DEFAULT_PROVISION', 'USER', u.global_user_id, 'HDR'
FROM memberships m
JOIN users u ON u.id = m.user_id
WHERE m.subject_type = 'USER'
  AND m.source = 'SYSTEM_DEFAULT'
  AND m.current_marker = 1;
