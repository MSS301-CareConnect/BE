package mss301.providerservice.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Một cảnh báo của lần kiểm tra AI (chỉ ADMIN thấy). code: NO_IMAGE, UNSUPPORTED_FILE, UNREADABLE, MISMATCH, NOT_FOUND,
 * EXPIRED; field: HOLDER_NAME, CREDENTIAL_NUMBER, CERTIFICATE_NAME, ISSUED_BY, ISSUED_DATE, EXPIRY_DATE (null với ba mã
 * đầu). Giữ dạng chuỗi để mã mới của AIService không làm hỏng dữ liệu đã lưu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CertificateAiWarningResponse {
    String code;
    String field;
    String declaredValue;
    String extractedValue;
}
