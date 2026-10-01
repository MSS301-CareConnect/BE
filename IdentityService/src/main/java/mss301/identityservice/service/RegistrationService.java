package mss301.identityservice.service;

import mss301.identityservice.dto.request.EmailRequest;
import mss301.identityservice.dto.request.RegisterRequest;
import mss301.identityservice.dto.request.ResetPasswordRequest;
import mss301.identityservice.dto.request.VerifyOtpRequest;
import mss301.identityservice.dto.response.APIResponse;
import mss301.identityservice.dto.response.OtpResponse;
import mss301.identityservice.dto.response.UserResponse;

public interface RegistrationService {
    // Đăng ký CUSTOMER 2 bước: gửi OTP → xác thực → tạo tài khoản
    APIResponse<OtpResponse> register(RegisterRequest request);

    APIResponse<OtpResponse> resend(EmailRequest request);

    APIResponse<UserResponse> verify(VerifyOtpRequest request);

    // Quên mật khẩu: gửi OTP → đặt lại bằng OTP (consume ngay trong bước đặt lại)
    APIResponse<Void> forgotPassword(EmailRequest request);

    APIResponse<Void> resetPassword(ResetPasswordRequest request);
}
