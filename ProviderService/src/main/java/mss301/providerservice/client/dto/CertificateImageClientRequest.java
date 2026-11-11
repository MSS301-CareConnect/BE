package mss301.providerservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Một file chứng chỉ: URL tải có hạn từ MediaService + loại file (AIService chỉ đọc JPEG / PNG / WebP).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateImageClientRequest {
    String url;

    String mimeType;
}
