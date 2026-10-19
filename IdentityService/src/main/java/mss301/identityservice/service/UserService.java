package mss301.identityservice.service;

import mss301.identityservice.dto.request.ChangePasswordRequest;
import mss301.identityservice.dto.request.ProvisionRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.UpdateProfileRequest;
import mss301.identityservice.dto.request.UserBatchRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.UserStatus;

import java.util.List;
import java.util.UUID;

public interface UserService {
    // Dùng nội bộ bởi AuthenticationService / RegistrationService
    AuthenticatedUser verifyCredentials(String email, String password);

    UserResponse createVerified(RegisterRequest request);

    void resetPassword(String email, String passwordHash);

    void invalidateSessions(UUID userId);

    // Tài khoản của chính mình
    APIResponse<UserResponse> me(UUID actorId);

    APIResponse<UserResponse> update(UUID actorId, UpdateProfileRequest request);

    APIResponse<Void> changePassword(UUID actorId, ChangePasswordRequest request);

    // Tra cứu tài khoản (người dùng & service-to-service qua ApiGateway)
    APIResponse<UserResponse> get(UUID id);

    APIResponse<UserResponse> getByEmail(String email);

    APIResponse<List<UserResponse>> listBatch(UserBatchRequest request);

    // Quản trị tài khoản (theo quyền + phạm vi trung tâm)
    APIResponse<List<UserResponse>> list(UUID actorId, int page, int size);

    APIResponse<UserResponse> provision(UUID actorId, ProvisionRequest request);

    APIResponse<UserResponse> update(UUID actorId, UUID id, UpdateProfileRequest request);

    APIResponse<UserResponse> updateStatus(UUID actorId, UUID id, UserStatus status);

    APIResponse<Void> delete(UUID actorId, UUID id);

    APIResponse<UserResponse> assign(UUID actorId, UUID id, UUID roleId);

    APIResponse<UserResponse> revoke(UUID actorId, UUID id, UUID roleId);
}
