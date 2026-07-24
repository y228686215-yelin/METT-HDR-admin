package com.mett.hdr.permission.service;

import com.mett.hdr.permission.repository.PermissionRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public void assignDefaultUserRole(Long userId) {
        permissionRepository.assignRole(userId, "USER");
    }

    public List<String> rolesForUser(Long userId) {
        return permissionRepository.findRoleCodesByUserId(userId);
    }
}
