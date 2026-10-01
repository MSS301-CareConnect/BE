package mss301.identityservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.identityservice.config.IdentityProperties;
import mss301.identityservice.exception.AppException;
import mss301.identityservice.exception.ErrorCode;
import mss301.identityservice.service.EmailService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class EmailServiceImpl implements EmailService {

    static String SEND_EMAIL_PATH = "/smtp/email";
    static String API_KEY_HEADER = "api-key";
    static String OTP_SUBJECT = "CareConnect — Mã xác thực";
    static int API_KEY_VISIBLE_SUFFIX = 4;
    static int MAX_LOGGED_RESPONSE_LENGTH = 500;

    IdentityProperties identityProperties;
    RestClient brevoRestClient;

    @Override
    public void sendOtp(String email, String otpCode) {
        IdentityProperties.Brevo brevo = identityProperties.getBrevo();
        if (isBlank(brevo.getApiKey()) || isBlank(brevo.getSenderEmail())) {
            log.error("Brevo API key or sender email is not configured");
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", brevo.getSenderEmail(), "name", brevo.getSenderName()),
                "to", List.of(Map.of("email", email)),
                "subject", OTP_SUBJECT,
                "textContent", "Mã xác thực của bạn: " + otpCode + ". Không chia sẻ mã này với người khác.");
        try {
            brevoRestClient.post()
                    .uri(SEND_EMAIL_PATH)
                    .header(API_KEY_HEADER, brevo.getApiKey())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Body lỗi của Brevo chỉ có {code, message} (không echo request) nên log được để biết lý do thật
            // (vd IP chưa được authorise, key bị thu hồi). Không log e.getMessage()/request vì có OTP.
            log.error("Brevo email delivery failed: status={}, apiKey={}, response={}",
                    e.getStatusCode().value(), maskApiKey(brevo.getApiKey()), truncate(e.getResponseBodyAsString()));
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED);
        } catch (RestClientException e) {
            // Lỗi mạng/timeout: exception có thể chứa request — chỉ log loại lỗi.
            log.error("Brevo email delivery failed ({})", e.getClass().getSimpleName());
            throw new AppException(ErrorCode.EMAIL_DELIVERY_FAILED);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // Chỉ để đối chiếu đúng key đã nạp (độ dài + 4 ký tự cuối), không bao giờ log nguyên key.
    private String maskApiKey(String apiKey) {
        if (apiKey.length() <= API_KEY_VISIBLE_SUFFIX) {
            return "***";
        }
        return "***" + apiKey.substring(apiKey.length() - API_KEY_VISIBLE_SUFFIX) + " (len=" + apiKey.length() + ")";
    }

    private String truncate(String value) {
        if (value.length() <= MAX_LOGGED_RESPONSE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOGGED_RESPONSE_LENGTH) + "...";
    }
}
