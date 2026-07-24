CREATE TABLE projects (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    global_project_id VARCHAR(64) NOT NULL UNIQUE,
    project_number VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    project_type VARCHAR(64) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(32) NOT NULL,
    origin_system VARCHAR(64) NOT NULL,
    city VARCHAR(128),
    timezone VARCHAR(64),
    owner_user_id BIGINT UNSIGNED NOT NULL,
    team_id BIGINT UNSIGNED,
    organization_id BIGINT UNSIGNED,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    managed_by_user_id BIGINT UNSIGNED NOT NULL,
    activated_at DATETIME(3),
    archived_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_projects_owner FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT fk_projects_team FOREIGN KEY (team_id) REFERENCES teams (id),
    CONSTRAINT fk_projects_organization FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_projects_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_projects_manager FOREIGN KEY (managed_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_projects_type CHECK (project_type IN ('RESIDENTIAL', 'SMALL_COMMERCIAL', 'OTHER')),
    CONSTRAINT chk_projects_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_projects_origin CHECK (origin_system = 'HDR'),
    INDEX idx_projects_owner_status (owner_user_id, status),
    INDEX idx_projects_team_status (team_id, status),
    INDEX idx_projects_organization_status (organization_id, status),
    INDEX idx_projects_manager_status (managed_by_user_id, status)
);

CREATE TABLE project_members (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    member_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    joined_at DATETIME(3) NOT NULL,
    left_at DATETIME(3),
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_project_members_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT chk_project_members_role CHECK (member_role IN ('MANAGER', 'EDITOR', 'VIEWER')),
    CONSTRAINT chk_project_members_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'LEFT')),
    CONSTRAINT chk_project_members_left_at CHECK (
        (status = 'LEFT' AND left_at IS NOT NULL) OR (status <> 'LEFT' AND left_at IS NULL)
    ),
    INDEX idx_project_members_user_status (user_id, status),
    INDEX idx_project_members_project_status (project_id, status)
);

CREATE TABLE project_spaces (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    global_space_id VARCHAR(64) NOT NULL UNIQUE,
    project_id BIGINT UNSIGNED NOT NULL,
    parent_space_id BIGINT UNSIGNED,
    name VARCHAR(255) NOT NULL,
    space_level_type VARCHAR(32) NOT NULL,
    usage_code VARCHAR(64),
    geometry_type VARCHAR(32) NOT NULL,
    length_m DECIMAL(12,3),
    width_m DECIMAL(12,3),
    height_m DECIMAL(12,3),
    floor_area_m2 DECIMAL(14,3),
    volume_m3 DECIMAL(16,3),
    orientation_code VARCHAR(32),
    status VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_by_user_id BIGINT UNSIGNED NOT NULL,
    archived_at DATETIME(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_project_spaces_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_spaces_parent FOREIGN KEY (parent_space_id) REFERENCES project_spaces (id),
    CONSTRAINT fk_project_spaces_creator FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT chk_project_spaces_level CHECK (space_level_type IN ('FLOOR', 'ROOM', 'ZONE')),
    CONSTRAINT chk_project_spaces_geometry CHECK (geometry_type IN ('RECTANGLE', 'UNSPECIFIED')),
    CONSTRAINT chk_project_spaces_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_project_spaces_orientation CHECK (
        orientation_code IS NULL OR orientation_code IN ('N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW',
                                                          'INTERNAL', 'UNKNOWN')
    ),
    CONSTRAINT chk_project_spaces_dimensions CHECK (
        (length_m IS NULL OR length_m > 0)
        AND (width_m IS NULL OR width_m > 0)
        AND (height_m IS NULL OR height_m > 0)
    ),
    CONSTRAINT chk_project_spaces_rectangle CHECK (
        geometry_type <> 'RECTANGLE' OR (length_m IS NOT NULL AND width_m IS NOT NULL)
    ),
    CONSTRAINT chk_project_spaces_derived CHECK (
        (floor_area_m2 IS NULL OR floor_area_m2 > 0) AND (volume_m3 IS NULL OR volume_m3 > 0)
    ),
    INDEX idx_project_spaces_project_status (project_id, status),
    INDEX idx_project_spaces_parent_status (parent_space_id, status)
);

CREATE TABLE external_object_links (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    local_object_type VARCHAR(64) NOT NULL,
    local_object_id BIGINT UNSIGNED NOT NULL,
    local_global_id VARCHAR(64) NOT NULL,
    external_system VARCHAR(64) NOT NULL,
    external_object_type VARCHAR(64) NOT NULL,
    external_object_id VARCHAR(128),
    external_global_id VARCHAR(64),
    relation_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT uk_external_links_local_relation UNIQUE (
        local_object_type, local_global_id, external_system, external_object_type, relation_type
    ),
    CONSTRAINT chk_external_links_local_type CHECK (local_object_type = 'PROJECT'),
    CONSTRAINT chk_external_links_external_id CHECK (
        external_object_id IS NOT NULL OR external_global_id IS NOT NULL
    ),
    CONSTRAINT chk_external_links_relation CHECK (relation_type IN ('SOURCE', 'MIRROR', 'REFERENCE')),
    CONSTRAINT chk_external_links_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    INDEX idx_external_links_external_id (external_system, external_object_type, external_object_id),
    INDEX idx_external_links_external_global_id (external_system, external_object_type, external_global_id)
);
