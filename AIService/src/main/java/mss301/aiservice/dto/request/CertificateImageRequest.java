package mss301.aiservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Một file của chứng chỉ: URL tải có hạn (ProviderService lấy từ MediaService) và loại file.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateImageRequest {
    @NotBlank(message = "CERTIFICATE_IMAGE_URL_REQUIRED")
    @Size(max = 4000, message = "INVALID_REQUEST")
    String url;

    @Size(max = 100, message = "INVALID_REQUEST")
    String mimeType;
}
