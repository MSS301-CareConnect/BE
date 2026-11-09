package mss301.aiservice.service;

import jakarta.servlet.http.HttpServletRequest;

public interface GuestUsageService {
    int consumePrompt(HttpServletRequest request);

    void restorePrompt(HttpServletRequest request);

    int getRemainingPrompts(HttpServletRequest request);
}
