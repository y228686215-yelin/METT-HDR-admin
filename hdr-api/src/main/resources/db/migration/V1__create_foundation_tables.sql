CREATE TABLE system_configs (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    config_key VARCHAR(128) NOT NULL,
    config_value JSON NULL,
    config_type VARCHAR(64) NOT NULL DEFAULT 'string',
    description VARCHAR(512) NULL,
    is_sensitive TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_configs_key (config_key),
    KEY idx_system_configs_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE enum_dictionaries (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dict_type VARCHAR(128) NOT NULL,
    dict_code VARCHAR(128) NOT NULL,
    dict_label VARCHAR(255) NOT NULL,
    description VARCHAR(512) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_enum_dictionaries_type_code (dict_type, dict_code),
    KEY idx_enum_dictionaries_type (dict_type),
    KEY idx_enum_dictionaries_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
