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

INSERT INTO roles (code, name, description) VALUES
('USER', 'User', 'Default HDR user role.'),
('ADMIN', 'Admin', 'Reserved HDR admin role; not METT Admin.');

INSERT INTO permissions (code, name, description) VALUES
('USER_PROFILE_READ', 'Read user profile', 'Allows reading own user profile.'),
('USER_PROFILE_UPDATE', 'Update user profile', 'Allows updating own user profile.'),
('HDR_ACCESS', 'HDR access', 'Allows access to HDR user capabilities.'),
('ADMIN_ACCESS', 'Admin access', 'Reserved for future HDR admin access.');
