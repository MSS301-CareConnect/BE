package mss301.aiservice.service;

import jakarta.servlet.http.HttpServletRequest;

public interface GuestUsageService {
    // Trừ một lượt chat trong ngày của khách, trả về số lượt còn lại.
    int consumePrompt(HttpServletRequest request);

    // Hoàn lại lượt chat của khách khi gọi AI thất bại.
    void restorePrompt(HttpServletRequest request);

    // Số lượt chat còn lại trong ngày của khách.
    int getRemainingPrompts(HttpServletRequest request);
}
