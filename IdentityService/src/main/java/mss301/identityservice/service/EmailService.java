package mss301.identityservice.service;

public interface EmailService {
    void sendOtp(String email, String otpCode);
}
