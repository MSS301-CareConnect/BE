package mss301.identityservice.service;

public interface RateLimiter {
    void checkLoginRateLimit(String email, String ip);

    void recordLoginFailure(String email, String ip);

    void resetLoginAttempts(String email, String ip);

    // H-13: đếm theo (clientId, IP) — người ngoài sai secret chỉ khóa chính IP của họ, không khóa client của service.
    void checkServiceTokenRateLimit(String clientId, String ip);

    void recordServiceTokenFailure(String clientId, String ip);

    void resetServiceTokenAttempts(String clientId, String ip);
}
