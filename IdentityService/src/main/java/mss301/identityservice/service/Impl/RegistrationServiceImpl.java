package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.config.RedisKeys;
import mss301.identityservice.dto.request.EmailRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.ResetPasswordRequest;
import mss301.identityservice.dto.request.VerifyOtpRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.OtpResponse;
import mss301.identityservice.dto.response.UserResponse;
import mss301.identityservice.entity.User;
import mss301.identityservice.enums.UserStatus;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.mapper.RegistrationMapper;
import mss301.identityservice.repository.UserRepository;
import mss301.identityservice.service.AccountService;
import mss301.identityservice.service.EmailService;
import mss301.identityservice.service.IdentityStateService;
import mss301.identityservice.service.RegistrationService;
import mss301.identityservice.util.EmailNormalizer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RegistrationServiceImpl implements RegistrationService {

    // Purpose tách namespace OTP đăng ký và quên mật khẩu trên Redis.
    static String REGISTER_PURPOSE = "register";
    static String RESET_PURPOSE = "reset";

    // Field dữ liệu đăng ký chờ xác thực — trùng tên property của RegisterRequest để RegistrationMapper map lại.
    static String F_EMAIL = "email";
    static String F_PASSWORD = "password";
    static String F_FULL_NAME = "fullName";
    static String F_PHONE_NUMBER = "phoneNumber";

    static int OTP_BOUND = 1_000_000;
    static String OTP_FORMAT = "%06d";

    static SecureRandom SECURE_RANDOM = new SecureRandom();

    IdentityStateService identityStateService;
    IdentityProperties identityProperties;
    PasswordEncoder passwordEncoder;
    EmailService emailService;
    AccountService accountService;
    RegistrationMapper registrationMapper;
    UserRepository userRepository;

    @Override
    public APIResponse<OtpResponse> register(RegisterRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.EMAIL_EXISTED);
        }
        // Chưa tạo tài khoản: chỉ lưu dữ liệu chờ (password đã BCrypt) cho tới khi email được xác thực.
        Map<String, String> pendingData = new HashMap<>();
        pendingData.put(F_EMAIL, email);
        pendingData.put(F_PASSWORD, passwordEncoder.encode(request.getPassword()));
        pendingData.put(F_FULL_NAME, request.getFullName().strip());
        if (request.getPhoneNumber() != null) {
            pendingData.put(F_PHONE_NUMBER, request.getPhoneNumber());
        }
        sendOtp(REGISTER_PURPOSE, email, pendingData);
        return toOtpResponse(email);
    }

    @Override
    public APIResponse<OtpResponse> resend(EmailRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        Map<String, String> pendingData = identityStateService.getOtp(REGISTER_PURPOSE, email);
        if (!pendingData.containsKey(F_PASSWORD)) {
            throw new AppException(ErrorCode.REGISTRATION_SESSION_EXPIRED);
        }
        sendOtp(REGISTER_PURPOSE, email, pendingData);
        return toOtpResponse(email);
    }

    @Override
    public APIResponse<UserResponse> verify(VerifyOtpRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());

        Map<String, String> pendingData = consumeOtp(REGISTER_PURPOSE, email, request.getOtpCode());
        RegisterRequest verifiedRequest = registrationMapper.toRequest(pendingData);

        UserResponse userResponse = accountService.createVerified(verifiedRequest);
        return APIResponse.success("Đăng ký thành công, vui lòng đăng nhập", userResponse);
    }

    @Override
    public APIResponse<Void> forgotPassword(EmailRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        Optional<User> user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (user.isPresent() && user.get().getStatus() == UserStatus.ACTIVE) {
            try {
                sendOtp(RESET_PURPOSE, email, new HashMap<>());
            } catch (AppException e) {
                // Còn cooldown thì im lặng: phản hồi luôn trung tính, không lộ email có tồn tại hay không.
                if (e.getErrorCode() != ErrorCode.OTP_RESEND_TOO_SOON) {
                    throw e;
                }
            }
        }
        return APIResponse.success("Nếu tài khoản hợp lệ, mã xác thực sẽ được gửi đến email");
    }

    @Override
    public APIResponse<Void> resetPassword(ResetPasswordRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        consumeOtp(RESET_PURPOSE, email, request.getOtpCode());
        // OTP đã consume trước khi ghi DB (không có transaction chung DB–Redis): DB lỗi thì người dùng xin OTP mới.
        accountService.resetPassword(email, passwordEncoder.encode(request.getNewPassword()));
        return APIResponse.success("Đặt mật khẩu thành công, vui lòng đăng nhập");
    }

    private void sendOtp(String purpose, String email, Map<String, String> data) {
        String otpCode = String.format(Locale.ROOT, OTP_FORMAT, SECURE_RANDOM.nextInt(OTP_BOUND));
        data.put(RedisKeys.OTP_HASH_FIELD, passwordEncoder.encode(otpCode));
        data.put(RedisKeys.OTP_ATTEMPTS_FIELD, "0");
        if (!identityStateService.beginOtp(purpose, email, data)) {
            throw new AppException(ErrorCode.OTP_RESEND_TOO_SOON);
        }
        try {
            emailService.sendOtp(email, otpCode);
        } catch (AppException e) {
            // Gửi lỗi → xóa OTP + cooldown để người dùng thử lại ngay.
            identityStateService.clearOtp(purpose, email);
            throw e;
        }
    }

    // Mã đúng chỉ dùng được một lần: request nào claim được hash (nguyên tử) mới đi tiếp.
    private Map<String, String> consumeOtp(String purpose, String email, String otpCode) {
        Map<String, String> data = identityStateService.getOtp(purpose, email);
        String otpHash = data.get(RedisKeys.OTP_HASH_FIELD);
        if (otpHash == null) {
            throw new AppException(ErrorCode.OTP_NOT_FOUND);
        }
        if (!passwordEncoder.matches(otpCode, otpHash)) {
            int attempts = identityStateService.failOtp(purpose, email, otpHash);
            throw new AppException(attempts >= identityProperties.getOtpMaxAttempts()
                    ? ErrorCode.OTP_ATTEMPTS_EXCEEDED
                    : ErrorCode.INVALID_OTP_OR_EMAIL);
        }
        if (!identityStateService.consumeOtp(purpose, email, otpHash)) {
            throw new AppException(ErrorCode.OTP_ALREADY_USED);
        }
        return data;
    }

    private APIResponse<OtpResponse> toOtpResponse(String email) {
        OtpResponse otpResponse = registrationMapper.toOtpResponse(email,
                identityProperties.getOtpTtl().toSeconds(), identityProperties.getOtpCooldown().toSeconds());
        return APIResponse.success("Đã gửi mã xác thực", otpResponse);
    }
}
