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

CREATE TABLE membership_plan_offers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    global_offer_id VARCHAR(64) NOT NULL UNIQUE,
    offer_code VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    membership_plan_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    purchase_mode VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL,
    membership_duration_months INT NOT NULL,
    valid_from TIMESTAMP(3),
    valid_until TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (offer_code, version),
    FOREIGN KEY (membership_plan_id) REFERENCES membership_plans (id),
    CHECK (version > 0),
    CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CHECK (purchase_mode = 'ONE_TIME_TERM'),
    CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency)),
    CHECK (amount_minor >= 0),
    CHECK (membership_duration_months > 0),
    CHECK (
        valid_from IS NULL OR valid_until IS NULL
        OR valid_until > valid_from
    )
);

CREATE TABLE payment_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    global_order_id VARCHAR(64) NOT NULL UNIQUE,
    order_number VARCHAR(64) NOT NULL UNIQUE,
    subject_type VARCHAR(32) NOT NULL,
    user_id BIGINT,
    team_id BIGINT,
    purchaser_user_id BIGINT NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    fulfillment_status VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_amount_minor BIGINT NOT NULL,
    request_idempotency_key VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    paid_at TIMESTAMP(3),
    fulfilled_at TIMESTAMP(3),
    cancelled_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (purchaser_user_id, request_idempotency_key),
    FOREIGN KEY (user_id) REFERENCES users (id),
    FOREIGN KEY (team_id) REFERENCES teams (id),
    FOREIGN KEY (purchaser_user_id) REFERENCES users (id),
    CHECK (
        (subject_type = 'USER' AND user_id IS NOT NULL AND team_id IS NULL)
        OR
        (subject_type = 'TEAM' AND team_id IS NOT NULL AND user_id IS NULL)
    ),
    CHECK (order_status IN (
        'CREATED', 'PAYMENT_PENDING', 'PAID', 'FULFILLED',
        'CANCELLED', 'EXPIRED', 'PAYMENT_FAILED',
        'PAYMENT_REVIEW_REQUIRED'
    )),
    CHECK (payment_status IN (
        'UNPAID', 'PROCESSING', 'PAID', 'FAILED', 'REVIEW_REQUIRED'
    )),
    CHECK (fulfillment_status IN (
        'NOT_STARTED', 'PROCESSING', 'FULFILLED', 'FAILED'
    )),
    CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency)),
    CHECK (total_amount_minor >= 0)
);

CREATE TABLE payment_order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_order_id BIGINT NOT NULL UNIQUE,
    item_type VARCHAR(32) NOT NULL,
    membership_plan_offer_id BIGINT NOT NULL,
    membership_plan_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    unit_amount_minor BIGINT NOT NULL,
    total_amount_minor BIGINT NOT NULL,
    plan_code_snapshot VARCHAR(64) NOT NULL,
    plan_version_snapshot INT NOT NULL,
    tier_snapshot VARCHAR(32) NOT NULL,
    offer_code_snapshot VARCHAR(64) NOT NULL,
    offer_version_snapshot INT NOT NULL,
    membership_duration_months INT NOT NULL,
    item_snapshot JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    FOREIGN KEY (membership_plan_offer_id) REFERENCES membership_plan_offers (id),
    FOREIGN KEY (membership_plan_id) REFERENCES membership_plans (id),
    CHECK (item_type = 'MEMBERSHIP_PLAN'),
    CHECK (quantity = 1),
    CHECK (CHAR_LENGTH(currency) = 3 AND currency = UPPER(currency)),
    CHECK (
        unit_amount_minor >= 0
        AND total_amount_minor = unit_amount_minor * quantity
    ),
    CHECK (plan_version_snapshot > 0 AND offer_version_snapshot > 0),
    CHECK (tier_snapshot IN ('PLUS', 'PRO')),
    CHECK (membership_duration_months > 0)
);

CREATE TABLE payment_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    global_payment_attempt_id VARCHAR(64) NOT NULL UNIQUE,
    payment_order_id BIGINT NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    provider_attempt_id VARCHAR(128),
    provider_transaction_id VARCHAR(128),
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_amount_minor BIGINT NOT NULL,
    requested_currency VARCHAR(3) NOT NULL,
    next_action_type VARCHAR(32),
    provider_reference VARCHAR(255),
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    expires_at TIMESTAMP(3),
    succeeded_at TIMESTAMP(3),
    failed_at TIMESTAMP(3),
    created_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (payment_order_id, idempotency_key),
    UNIQUE (provider_code, provider_attempt_id),
    UNIQUE (provider_code, provider_transaction_id),
    FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CHECK (status IN (
        'CREATED', 'ACTION_REQUIRED', 'PROCESSING', 'SUCCEEDED',
        'FAILED', 'CANCELLED', 'EXPIRED'
    )),
    CHECK (requested_amount_minor >= 0),
    CHECK (
        CHAR_LENGTH(requested_currency) = 3
        AND requested_currency = UPPER(requested_currency)
    )
);

CREATE TABLE payment_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_code VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    payment_order_id BIGINT,
    payment_attempt_id BIGINT,
    provider_transaction_id VARCHAR(128),
    sanitized_event_data JSON,
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    received_at TIMESTAMP(3) NOT NULL,
    processed_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider_code, provider_event_id),
    FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts (id),
    CHECK (processing_status IN (
        'RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED', 'REVIEW_REQUIRED'
    ))
);

CREATE TABLE payment_order_fulfillments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_order_id BIGINT NOT NULL UNIQUE,
    fulfillment_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    operation_key VARCHAR(128) NOT NULL UNIQUE,
    attempt_count INT NOT NULL DEFAULT 0,
    fulfilled_membership_id BIGINT,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(500),
    started_at TIMESTAMP(3),
    completed_at TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    FOREIGN KEY (fulfilled_membership_id) REFERENCES memberships (id),
    CHECK (fulfillment_type = 'MEMBERSHIP_ACTIVATION'),
    CHECK (status IN ('PENDING', 'PROCESSING', 'FULFILLED', 'FAILED')),
    CHECK (attempt_count >= 0)
);

CREATE TABLE payment_order_state_transitions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_order_id BIGINT NOT NULL,
    state_type VARCHAR(32) NOT NULL,
    from_state VARCHAR(32),
    to_state VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    actor_user_id BIGINT,
    payment_event_id BIGINT,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    FOREIGN KEY (actor_user_id) REFERENCES users (id),
    FOREIGN KEY (payment_event_id) REFERENCES payment_events (id),
    CHECK (state_type IN ('ORDER', 'PAYMENT', 'FULFILLMENT'))
);
