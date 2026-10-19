package mss301.identityservice.service;

public interface RateLimiter {
    void checkLoginRateLimit(String email, String ip);

    void recordLoginFailure(String email, String ip);

    void resetLoginAttempts(String email, String ip);
}
