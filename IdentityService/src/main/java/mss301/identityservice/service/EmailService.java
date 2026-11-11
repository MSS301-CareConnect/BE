package mss301.identityservice.service;

public interface EmailService {
    // Gửi email chứa mã OTP.
    void sendOtp(String email, String otpCode);
}
