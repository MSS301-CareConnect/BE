package mss301.identityservice.service;

import mss301.identityservice.dto.request.UserBatchRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.InternalUserResponse;

import java.util.List;
import java.util.UUID;

// Tra cứu tài khoản cho service khác qua /internal/** (không có người dùng cuối, xác thực bằng internal secret).
public interface InternalUserService {
    APIResponse<InternalUserResponse> get(UUID id);

    APIResponse<InternalUserResponse> getByEmail(String email);

    APIResponse<List<InternalUserResponse>> list(UserBatchRequest request);
}
