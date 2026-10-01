package mss301.identityservice.service;

import mss301.identityservice.dto.request.RolePermissionsRequest;
import mss301.identityservice.dto.request.RoleRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.PermissionResponse;
import mss301.identityservice.dto.response.RoleResponse;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    APIResponse<List<RoleResponse>> list(UUID actorId);

    APIResponse<RoleResponse> create(UUID actorId, RoleRequest request);

    APIResponse<RoleResponse> updatePermissions(UUID actorId, UUID id, RolePermissionsRequest request);

    APIResponse<List<PermissionResponse>> listPermissions(UUID actorId);
}
