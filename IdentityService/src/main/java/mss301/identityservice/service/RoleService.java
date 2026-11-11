package mss301.identityservice.service;

import mss301.identityservice.dto.request.RolePermissionsRequest;
import mss301.identityservice.dto.request.RoleRequest;
import mss301.identityservice.dto.request.RoleUpdateRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.PermissionResponse;
import mss301.identityservice.dto.response.RoleResponse;

import java.util.List;
import java.util.UUID;

public interface RoleService {
    // Danh sách role.
    APIResponse<List<RoleResponse>> list(UUID actorId);

    // Tạo role tùy chỉnh.
    APIResponse<RoleResponse> create(UUID actorId, RoleRequest request);

    // Cập nhật role tùy chỉnh.
    APIResponse<RoleResponse> update(UUID actorId, UUID id, RoleUpdateRequest request);

    // Xóa role tùy chỉnh.
    APIResponse<Void> delete(UUID actorId, UUID id);

    // Gán lại permission cho role.
    APIResponse<RoleResponse> updatePermissions(UUID actorId, UUID id, RolePermissionsRequest request);

    // Danh sách permission.
    APIResponse<List<PermissionResponse>> listPermissions(UUID actorId);
}
