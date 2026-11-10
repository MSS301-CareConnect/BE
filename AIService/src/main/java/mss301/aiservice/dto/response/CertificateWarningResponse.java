package mss301.aiservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.enums.CertificateCheckField;
import mss301.aiservice.enums.CertificateWarningCode;

// Một cảnh báo: mã + trường liên quan (null với NO_IMAGE / UNSUPPORTED_FILE / UNREADABLE) + hai giá trị đem so.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CertificateWarningResponse {
    CertificateWarningCode code;
    CertificateCheckField field;
    String declaredValue;
    String extractedValue;
}
