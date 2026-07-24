DROP ALL OBJECTS;

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    global_user_id VARCHAR(64) NOT NULL UNIQUE,
    identity_source VARCHAR(32) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(50) UNIQUE,
    username VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    last_login_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    display_name VARCHAR(100),
    avatar_file_id VARCHAR(64),
    company_name VARCHAR(255),
    country VARCHAR(64),
    language VARCHAR(32),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_identity_links (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    global_user_id VARCHAR(64) NOT NULL,
    source_system VARCHAR(64) NOT NULL,
    external_user_id VARCHAR(128),
    external_global_user_id VARCHAR(64),
    identity_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (source_system, external_user_id)
);

CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE permissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    FOREIGN KEY (role_id) REFERENCES roles (id),
    FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

CREATE TABLE auth_refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP(3) NOT NULL,
    revoked_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT,
    action VARCHAR(128) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(64),
    source_system VARCHAR(64),
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE organizations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    global_organization_id VARCHAR(64) NOT NULL UNIQUE,
    global_company_id VARCHAR(64) UNIQUE,
    name VARCHAR(255) NOT NULL,
    organization_type VARCHAR(64) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    managed_by_user_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_user_id) REFERENCES users (id),
    FOREIGN KEY (managed_by_user_id) REFERENCES users (id),
    FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE TABLE organization_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    joined_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP(3),
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (organization_id, user_id),
    FOREIGN KEY (organization_id) REFERENCES organizations (id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE TABLE teams (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    global_team_id VARCHAR(64) NOT NULL UNIQUE,
    organization_id BIGINT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    managed_by_user_id BIGINT NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (organization_id) REFERENCES organizations (id),
    FOREIGN KEY (owner_user_id) REFERENCES users (id),
    FOREIGN KEY (managed_by_user_id) REFERENCES users (id),
    FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE TABLE team_members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    joined_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP(3),
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (team_id, user_id),
    FOREIGN KEY (team_id) REFERENCES teams (id),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE TABLE entitlement_definitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    entitlement_type VARCHAR(32) NOT NULL,
    quota_unit VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (entitlement_type IN ('BOOLEAN', 'QUOTA')),
    CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE membership_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_code VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    audience_type VARCHAR(32) NOT NULL,
    tier VARCHAR(32) NOT NULL,
    usage_cycle VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (plan_code, version),
    CHECK (version > 0),
    CHECK (audience_type IN ('PERSONAL', 'TEAM')),
    CHECK (tier IN ('FREE', 'PLUS', 'PRO')),
    CHECK (usage_cycle IN ('MONTHLY')),
    CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED'))
);

CREATE TABLE membership_plan_entitlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    membership_plan_id BIGINT NOT NULL,
    entitlement_definition_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL,
    quota_limit BIGINT,
    is_unlimited BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (membership_plan_id, entitlement_definition_id),
    FOREIGN KEY (membership_plan_id) REFERENCES membership_plans (id),
    FOREIGN KEY (entitlement_definition_id) REFERENCES entitlement_definitions (id),
    CHECK (quota_limit IS NULL OR quota_limit >= 0),
    CHECK (NOT (is_unlimited = TRUE AND quota_limit IS NOT NULL))
);

CREATE TABLE memberships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject_type VARCHAR(32) NOT NULL,
    user_id BIGINT,
    team_id BIGINT,
    membership_plan_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    source VARCHAR(32) NOT NULL,
    current_marker TINYINT,
    started_at TIMESTAMP(3) NOT NULL,
    current_period_start_at TIMESTAMP(3) NOT NULL,
    current_period_end_at TIMESTAMP(3) NOT NULL,
    expires_at TIMESTAMP(3),
    suspended_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    replaced_at TIMESTAMP(3),
    created_by_user_id BIGINT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, current_marker),
    UNIQUE (team_id, current_marker),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (team_id) REFERENCES teams (id),
    FOREIGN KEY (membership_plan_id) REFERENCES membership_plans (id),
    FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CHECK (
        (subject_type = 'USER' AND user_id IS NOT NULL AND team_id IS NULL)
        OR
        (subject_type = 'TEAM' AND team_id IS NOT NULL AND user_id IS NULL)
    ),
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'EXPIRED', 'CANCELLED', 'REPLACED')),
    CHECK (source IN ('SYSTEM_DEFAULT', 'MANUAL', 'PAYMENT', 'ADMIN')),
    CHECK (current_marker IS NULL OR current_marker = 1),
    CHECK (
        status NOT IN ('EXPIRED', 'CANCELLED', 'REPLACED')
        OR current_marker IS NULL
    ),
    CHECK (current_period_end_at > current_period_start_at)
);

CREATE TABLE membership_usage (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    membership_id BIGINT NOT NULL,
    entitlement_definition_id BIGINT NOT NULL,
    cycle_start_at TIMESTAMP(3) NOT NULL,
    cycle_end_at TIMESTAMP(3) NOT NULL,
    quota_limit_snapshot BIGINT,
    is_unlimited BOOLEAN NOT NULL DEFAULT FALSE,
    used_count BIGINT NOT NULL DEFAULT 0,
    reserved_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (membership_id, entitlement_definition_id, cycle_start_at),
    FOREIGN KEY (membership_id) REFERENCES memberships (id),
    FOREIGN KEY (entitlement_definition_id) REFERENCES entitlement_definitions (id),
    CHECK (used_count >= 0 AND reserved_count >= 0),
    CHECK (quota_limit_snapshot IS NULL OR quota_limit_snapshot >= 0),
    CHECK (
        (is_unlimited = TRUE AND quota_limit_snapshot IS NULL)
        OR
        (is_unlimited = FALSE AND quota_limit_snapshot IS NOT NULL)
    ),
    CHECK (
        is_unlimited = TRUE
        OR used_count + reserved_count <= quota_limit_snapshot
    ),
    CHECK (cycle_end_at > cycle_start_at)
);

CREATE TABLE quota_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    membership_id BIGINT NOT NULL,
    membership_usage_id BIGINT NOT NULL,
    entitlement_definition_id BIGINT NOT NULL,
    operation_key VARCHAR(128) NOT NULL UNIQUE,
    change_type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    used_before BIGINT NOT NULL,
    used_after BIGINT NOT NULL,
    reserved_before BIGINT NOT NULL,
    reserved_after BIGINT NOT NULL,
    quota_limit_snapshot BIGINT,
    reservation_key VARCHAR(128),
    related_object_type VARCHAR(64),
    related_object_id VARCHAR(128),
    reason VARCHAR(500),
    actor_user_id BIGINT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (membership_id) REFERENCES memberships (id),
    FOREIGN KEY (membership_usage_id) REFERENCES membership_usage (id),
    FOREIGN KEY (entitlement_definition_id) REFERENCES entitlement_definitions (id),
    FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CHECK (change_type IN (
        'CONSUME', 'RESTORE', 'RESERVE', 'COMMIT', 'RELEASE',
        'GRANT', 'ADJUST', 'EXPIRE', 'REFUND'
    )),
    CHECK (amount > 0),
    CHECK (
        used_before >= 0 AND used_after >= 0
        AND reserved_before >= 0 AND reserved_after >= 0
    )
);

CREATE TABLE usage_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    membership_id BIGINT NOT NULL,
    cycle_start_at TIMESTAMP(3) NOT NULL,
    cycle_end_at TIMESTAMP(3) NOT NULL,
    snapshot_data JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (membership_id, cycle_start_at, cycle_end_at),
    FOREIGN KEY (membership_id) REFERENCES memberships (id),
    CHECK (cycle_end_at > cycle_start_at)
);

INSERT INTO roles (code, name, description) VALUES
('USER', 'User', 'Default HDR user role.'),
('ADMIN', 'Admin', 'Reserved HDR admin role; not METT Admin.');

INSERT INTO permissions (code, name, description) VALUES
('USER_PROFILE_READ', 'Read user profile', 'Allows reading own user profile.'),
('USER_PROFILE_UPDATE', 'Update user profile', 'Allows updating own user profile.'),
('HDR_ACCESS', 'HDR access', 'Allows access to HDR user capabilities.'),
('ADMIN_ACCESS', 'Admin access', 'Reserved for future HDR admin access.');

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
