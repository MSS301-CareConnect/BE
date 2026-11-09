package mss301.aiservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.List;

/**
 * Yêu cầu kiểm tra một chứng chỉ (chỉ ProviderService gọi, token SERVICE): thông tin caregiver đã khai báo và các file
 * của chứng chỉ. AI chỉ đọc ảnh; việc đối chiếu và tính mức rủi ro do AIService làm bằng code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateCheckRequest {
    @NotBlank(message = "CERTIFICATE_HOLDER_NAME_REQUIRED")
    @Size(max = 255, message = "INVALID_REQUEST")
    String holderName;

    @NotBlank(message = "CERTIFICATE_NAME_REQUIRED")
    @Size(max = 255, message = "INVALID_REQUEST")
    String certificateName;

    @Size(max = 255, message = "INVALID_REQUEST")
    String issuedBy;

    @Size(max = 255, message = "INVALID_REQUEST")
    String credentialNumber;

    LocalDate issuedDate;

    LocalDate expiryDate;

    @Valid
    @NotNull(message = "INVALID_REQUEST")
    @Size(max = 10, message = "TOO_MANY_CERTIFICATE_IMAGES")
    List<CertificateImageRequest> images;
}
