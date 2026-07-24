CREATE TABLE membership_plan_offers (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    global_offer_id VARCHAR(64) NOT NULL,
    offer_code VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    membership_plan_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    purchase_mode VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount_minor BIGINT NOT NULL,
    membership_duration_months INT NOT NULL,
    valid_from DATETIME(3) NULL,
    valid_until DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_plan_offers_global (global_offer_id),
    UNIQUE KEY uk_membership_plan_offers_code_version (offer_code, version),
    KEY idx_membership_plan_offers_availability (status, valid_from, valid_until),
    KEY idx_membership_plan_offers_plan (membership_plan_id),
    CONSTRAINT fk_membership_plan_offers_plan
        FOREIGN KEY (membership_plan_id) REFERENCES membership_plans (id),
    CONSTRAINT chk_membership_plan_offers_version CHECK (version > 0),
    CONSTRAINT chk_membership_plan_offers_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT chk_membership_plan_offers_mode
        CHECK (purchase_mode = 'ONE_TIME_TERM'),
    CONSTRAINT chk_membership_plan_offers_currency
        CHECK (
            CHAR_LENGTH(currency) = 3
            AND currency = UPPER(currency)
        ),
    CONSTRAINT chk_membership_plan_offers_amount CHECK (amount_minor >= 0),
    CONSTRAINT chk_membership_plan_offers_duration
        CHECK (membership_duration_months > 0),
    CONSTRAINT chk_membership_plan_offers_validity
        CHECK (
            valid_from IS NULL OR valid_until IS NULL
            OR valid_until > valid_from
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_orders (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    global_order_id VARCHAR(64) NOT NULL,
    order_number VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    team_id BIGINT UNSIGNED NULL,
    purchaser_user_id BIGINT UNSIGNED NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    fulfillment_status VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    total_amount_minor BIGINT NOT NULL,
    request_idempotency_key VARCHAR(128) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    paid_at DATETIME(3) NULL,
    fulfilled_at DATETIME(3) NULL,
    cancelled_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_orders_global (global_order_id),
    UNIQUE KEY uk_payment_orders_number (order_number),
    UNIQUE KEY uk_payment_orders_purchaser_idempotency
        (purchaser_user_id, request_idempotency_key),
    KEY idx_payment_orders_purchaser_created (purchaser_user_id, created_at),
    KEY idx_payment_orders_team_created (team_id, created_at),
    KEY idx_payment_orders_status_expiry (order_status, expires_at),
    CONSTRAINT fk_payment_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_payment_orders_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_payment_orders_purchaser
        FOREIGN KEY (purchaser_user_id) REFERENCES users (id),
    CONSTRAINT chk_payment_orders_subject
        CHECK (
            (subject_type = 'USER' AND user_id IS NOT NULL AND team_id IS NULL)
            OR
            (subject_type = 'TEAM' AND team_id IS NOT NULL AND user_id IS NULL)
        ),
    CONSTRAINT chk_payment_orders_order_status
        CHECK (order_status IN (
            'CREATED', 'PAYMENT_PENDING', 'PAID', 'FULFILLED',
            'CANCELLED', 'EXPIRED', 'PAYMENT_FAILED',
            'PAYMENT_REVIEW_REQUIRED'
        )),
    CONSTRAINT chk_payment_orders_payment_status
        CHECK (payment_status IN (
            'UNPAID', 'PROCESSING', 'PAID', 'FAILED', 'REVIEW_REQUIRED'
        )),
    CONSTRAINT chk_payment_orders_fulfillment_status
        CHECK (fulfillment_status IN (
            'NOT_STARTED', 'PROCESSING', 'FULFILLED', 'FAILED'
        )),
    CONSTRAINT chk_payment_orders_currency
        CHECK (
            CHAR_LENGTH(currency) = 3
            AND currency = UPPER(currency)
        ),
    CONSTRAINT chk_payment_orders_amount CHECK (total_amount_minor >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_order_items (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT UNSIGNED NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    membership_plan_offer_id BIGINT UNSIGNED NOT NULL,
    membership_plan_id BIGINT UNSIGNED NOT NULL,
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
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_items_order (payment_order_id),
    KEY idx_payment_order_items_offer (membership_plan_offer_id),
    KEY idx_payment_order_items_plan (membership_plan_id),
    CONSTRAINT fk_payment_order_items_order
        FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    CONSTRAINT fk_payment_order_items_offer
        FOREIGN KEY (membership_plan_offer_id) REFERENCES membership_plan_offers (id),
    CONSTRAINT fk_payment_order_items_plan
        FOREIGN KEY (membership_plan_id) REFERENCES membership_plans (id),
    CONSTRAINT chk_payment_order_items_type
        CHECK (item_type = 'MEMBERSHIP_PLAN'),
    CONSTRAINT chk_payment_order_items_quantity CHECK (quantity = 1),
    CONSTRAINT chk_payment_order_items_currency
        CHECK (
            CHAR_LENGTH(currency) = 3
            AND currency = UPPER(currency)
        ),
    CONSTRAINT chk_payment_order_items_amounts
        CHECK (
            unit_amount_minor >= 0
            AND total_amount_minor = unit_amount_minor * quantity
        ),
    CONSTRAINT chk_payment_order_items_versions
        CHECK (plan_version_snapshot > 0 AND offer_version_snapshot > 0),
    CONSTRAINT chk_payment_order_items_tier
        CHECK (tier_snapshot IN ('PLUS', 'PRO')),
    CONSTRAINT chk_payment_order_items_duration
        CHECK (membership_duration_months > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_attempts (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    global_payment_attempt_id VARCHAR(64) NOT NULL,
    payment_order_id BIGINT UNSIGNED NOT NULL,
    provider_code VARCHAR(64) NOT NULL,
    provider_attempt_id VARCHAR(128) NULL,
    provider_transaction_id VARCHAR(128) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    requested_amount_minor BIGINT NOT NULL,
    requested_currency VARCHAR(3) NOT NULL,
    next_action_type VARCHAR(32) NULL,
    provider_reference VARCHAR(255) NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    expires_at DATETIME(3) NULL,
    succeeded_at DATETIME(3) NULL,
    failed_at DATETIME(3) NULL,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_attempts_global (global_payment_attempt_id),
    UNIQUE KEY uk_payment_attempts_order_idempotency
        (payment_order_id, idempotency_key),
    UNIQUE KEY uk_payment_attempts_provider_attempt
        (provider_code, provider_attempt_id),
    UNIQUE KEY uk_payment_attempts_provider_transaction
        (provider_code, provider_transaction_id),
    KEY idx_payment_attempts_order_created (payment_order_id, created_at),
    CONSTRAINT fk_payment_attempts_order
        FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    CONSTRAINT fk_payment_attempts_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_payment_attempts_status
        CHECK (status IN (
            'CREATED', 'ACTION_REQUIRED', 'PROCESSING', 'SUCCEEDED',
            'FAILED', 'CANCELLED', 'EXPIRED'
        )),
    CONSTRAINT chk_payment_attempts_amount CHECK (requested_amount_minor >= 0),
    CONSTRAINT chk_payment_attempts_currency
        CHECK (
            CHAR_LENGTH(requested_currency) = 3
            AND requested_currency = UPPER(requested_currency)
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_events (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_code VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    payment_order_id BIGINT UNSIGNED NULL,
    payment_attempt_id BIGINT UNSIGNED NULL,
    provider_transaction_id VARCHAR(128) NULL,
    sanitized_event_data JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    received_at DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_events_provider_event
        (provider_code, provider_event_id),
    KEY idx_payment_events_order_created (payment_order_id, created_at),
    KEY idx_payment_events_attempt_created (payment_attempt_id, created_at),
    KEY idx_payment_events_transaction
        (provider_code, provider_transaction_id),
    CONSTRAINT fk_payment_events_order
        FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    CONSTRAINT fk_payment_events_attempt
        FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts (id),
    CONSTRAINT chk_payment_events_status
        CHECK (processing_status IN (
            'RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED', 'REVIEW_REQUIRED'
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_order_fulfillments (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT UNSIGNED NOT NULL,
    fulfillment_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    operation_key VARCHAR(128) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    fulfilled_membership_id BIGINT UNSIGNED NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    started_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_order_fulfillments_order (payment_order_id),
    UNIQUE KEY uk_payment_order_fulfillments_operation (operation_key),
    CONSTRAINT fk_payment_order_fulfillments_order
        FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    CONSTRAINT fk_payment_order_fulfillments_membership
        FOREIGN KEY (fulfilled_membership_id) REFERENCES memberships (id),
    CONSTRAINT chk_payment_order_fulfillments_type
        CHECK (fulfillment_type = 'MEMBERSHIP_ACTIVATION'),
    CONSTRAINT chk_payment_order_fulfillments_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'FULFILLED', 'FAILED')),
    CONSTRAINT chk_payment_order_fulfillments_attempts
        CHECK (attempt_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_order_state_transitions (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT UNSIGNED NOT NULL,
    state_type VARCHAR(32) NOT NULL,
    from_state VARCHAR(32) NULL,
    to_state VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NULL,
    actor_user_id BIGINT UNSIGNED NULL,
    payment_event_id BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_payment_order_transitions_order_created
        (payment_order_id, created_at),
    KEY idx_payment_order_transitions_event (payment_event_id),
    CONSTRAINT fk_payment_order_transitions_order
        FOREIGN KEY (payment_order_id) REFERENCES payment_orders (id),
    CONSTRAINT fk_payment_order_transitions_actor
        FOREIGN KEY (actor_user_id) REFERENCES users (id),
    CONSTRAINT fk_payment_order_transitions_event
        FOREIGN KEY (payment_event_id) REFERENCES payment_events (id),
    CONSTRAINT chk_payment_order_transitions_type
        CHECK (state_type IN ('ORDER', 'PAYMENT', 'FULFILLMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
