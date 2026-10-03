package mss301.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.apigateway.exception.ErrorCode;
import mss301.apigateway.security.SecurityErrorHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chặn hoàn toàn API nội bộ (/internal/**) từ bên ngoài: mọi path có segment "internal" (kiểm cả dạng thô lẫn đã
 * decode, không phân biệt hoa thường) → 404, trước cả xác thực, không bao giờ tới service. Route của gateway cũng
 * không có prefix nào trỏ vào /internal.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InternalPathBlockFilter extends OncePerRequestFilter {

    static Pattern INTERNAL_SEGMENT = Pattern.compile("(^|/)internal(/|;|$)");

    SecurityErrorHandler securityErrorHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isInternalPath(request)) {
            log.warn("Blocked external request to internal path");
            securityErrorHandler.writeError(response, ErrorCode.PATH_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isInternalPath(HttpServletRequest request) {
        String rawPath = request.getRequestURI().toLowerCase(Locale.ROOT);
        String decodedPath;
        try {
            decodedPath = URLDecoder.decode(rawPath.replace("+", "%2B"), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            // Mã hóa hỏng → không đoán được đích thật, chặn luôn.
            return true;
        }
        return INTERNAL_SEGMENT.matcher(rawPath).find() || INTERNAL_SEGMENT.matcher(decodedPath).find();
    }
}
