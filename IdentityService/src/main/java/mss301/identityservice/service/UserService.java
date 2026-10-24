package mss301.identityservice.service;

import mss301.identityservice.dto.request.ChangePasswordRequest;
import mss301.identityservice.dto.request.ProvisionRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.UpdateProfileRequest;
import mss301.identityservice.dto.request.UserBatchRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.UserStatisticsResponse;
import mss301.identityservice.dto.response.AuthenticatedUser;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.enums.RoleCode;
import mss301.identityservice.dto.request.UserStatusRequest;
import mss301.identityservice.dto.response.PageResponse;
import mss301.identityservice.dto.response.AffectedUsersResponse;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

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
    APIResponse<PageResponse<UserResponse>> list(UUID actorId, RoleCode role, UserStatus status, UUID centerId,
                                               String keyword, int page, int size, String sort);

    APIResponse<AffectedUsersResponse> updateCenterStatus(UUID actorId, UUID centerId, UserStatusRequest request);

    APIResponse<UserResponse> provision(UUID actorId, ProvisionRequest request);

    APIResponse<UserResponse> update(UUID actorId, UUID id, UpdateProfileRequest request);

    APIResponse<UserResponse> updateStatus(UUID actorId, UUID id, UserStatus status, String reason);

    APIResponse<Void> delete(UUID actorId, UUID id);

    APIResponse<UserResponse> assign(UUID actorId, UUID id, UUID roleId);

    APIResponse<UserResponse> revoke(UUID actorId, UUID id, UUID roleId);
    APIResponse<UserStatisticsResponse> statistics(LocalDate from, LocalDate to);
}
