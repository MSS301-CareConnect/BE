package mss301.identityservice.service;

public interface RateLimiter {
    // Chặn đăng nhập khi sai quá số lần cho phép.
    void checkLoginRateLimit(String email, String ip);

    // Ghi nhận một lần đăng nhập sai.
    void recordLoginFailure(String email, String ip);

    // Xóa bộ đếm đăng nhập sai.
    void resetLoginAttempts(String email, String ip);

    // H-13: đếm theo (clientId, IP) — người ngoài sai secret chỉ khóa chính IP của họ, không khóa client của service.
    void checkServiceTokenRateLimit(String clientId, String ip);

    // Ghi nhận một lần xin token SERVICE thất bại.
    void recordServiceTokenFailure(String clientId, String ip);

    // Xóa bộ đếm xin token SERVICE sai.
    void resetServiceTokenAttempts(String clientId, String ip);
}
