package mss301.providerservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Xác thực service-to-service cho /internal/** bằng header {@value #HEADER}. Chỉ đặt Authentication khi secret khớp;
 * sai/thiếu → để chain trả 401 qua SecurityErrorHandler. Không đăng ký làm @Component (Boot sẽ gắn filter cho
 * MỌI request) — chỉ tạo trong {@link InternalSecurityConfig}.
 */
public class InternalSecretFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Secret";
    public static final String INTERNAL_SERVICE_AUTHORITY = "INTERNAL_SERVICE";

    private static final String PRINCIPAL = "internal-service";

    private final byte[] expectedSecret;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    public InternalSecretFilter(String secret) {
        this.expectedSecret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (matches(request.getHeader(HEADER))) {
            PreAuthenticatedAuthenticationToken authentication = new PreAuthenticatedAuthenticationToken(
                    PRINCIPAL, null, AuthorityUtils.createAuthorityList(INTERNAL_SERVICE_AUTHORITY));
            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);
        }
        filterChain.doFilter(request, response);
    }

    // So sánh thời gian hằng; secret cấu hình rỗng thì không bao giờ khớp (fail-closed).
    private boolean matches(String providedSecret) {
        if (expectedSecret.length == 0 || providedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedSecret, providedSecret.getBytes(StandardCharsets.UTF_8));
    }
}
