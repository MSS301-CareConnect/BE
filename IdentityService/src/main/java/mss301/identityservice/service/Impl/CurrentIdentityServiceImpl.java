package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.entity.User;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.mapper.UserMapper;
import mss301.identityservice.repository.UserRepository;
import mss301.identityservice.service.CurrentIdentityService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CurrentIdentityServiceImpl implements CurrentIdentityService {

    UserRepository userRepository;
    UserMapper userMapper;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getActiveUser(UUID userId, long authzVersion) {
        // Ném AuthenticationException (không phải AppException) vì được gọi từ pipeline của Spring Security → 401.
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new BadCredentialsException("Invalid account"));
        // authzVersion lệch = phiên đã bị thu hồi (logout-all, đổi/reset mật khẩu, khóa, xóa mềm).
        if (user.getStatus() != UserStatus.ACTIVE || user.getAuthzVersion() != authzVersion) {
            throw new BadCredentialsException("Account/session is no longer active");
        }
        return userMapper.toResponse(user);
    }
}
