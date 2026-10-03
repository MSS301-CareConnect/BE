package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.request.UserBatchRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.InternalUserResponse;
import mss301.identityservice.entity.User;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.mapper.UserMapper;
import mss301.identityservice.repository.UserRepository;
import mss301.identityservice.service.InternalUserService;
import mss301.identityservice.util.EmailNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalUserServiceImpl implements InternalUserService {

    UserRepository userRepository;
    UserMapper userMapper;

    // Tài khoản LOCKED vẫn trả về kèm status — service gọi tự quyết định; chỉ tài khoản đã xóa mềm coi như không tồn tại.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<InternalUserResponse> get(UUID id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        InternalUserResponse internalUserResponse = userMapper.toInternalResponse(user);
        return APIResponse.success("Lấy thông tin tài khoản thành công", internalUserResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public APIResponse<InternalUserResponse> getByEmail(String email) {
        String normalizedEmail = EmailNormalizer.normalize(email);
        if (normalizedEmail == null || normalizedEmail.isEmpty()) {
            throw new AppException(ErrorCode.EMAIL_REQUIRED);
        }
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        InternalUserResponse internalUserResponse = userMapper.toInternalResponse(user);
        return APIResponse.success("Lấy thông tin tài khoản thành công", internalUserResponse);
    }

    // Id không tồn tại / đã xóa mềm bị bỏ qua (không lỗi) — service gọi tự xử lý phần thiếu.
    @Override
    @Transactional(readOnly = true)
    public APIResponse<List<InternalUserResponse>> list(UserBatchRequest request) {
        Set<UUID> userIds = new LinkedHashSet<>(request.getUserIds());
        List<User> users = userRepository.findAllByIdInAndDeletedAtIsNull(userIds);
        List<InternalUserResponse> internalUserResponses = userMapper.toInternalResponseList(users);
        return APIResponse.success("Lấy danh sách tài khoản thành công", internalUserResponses);
    }
}
