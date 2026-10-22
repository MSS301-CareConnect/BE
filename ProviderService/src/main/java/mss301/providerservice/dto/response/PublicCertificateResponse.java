package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

// Chứng chỉ đã được ADMIN xác minh, hiển thị cho khách — không có số hiệu văn bằng / người duyệt.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PublicCertificateResponse {
    String certificateName;
    String issuedBy;
    LocalDate issuedDate;
    LocalDate expiryDate;
}
