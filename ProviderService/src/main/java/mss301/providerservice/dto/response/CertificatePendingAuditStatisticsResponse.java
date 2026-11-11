package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

// Số chứng chỉ chờ admin duyệt (CENTER_VERIFIED) theo kết quả AI — thẻ đếm của hàng chờ duyệt chứng chỉ.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificatePendingAuditStatisticsResponse {
    long total;

    long high;

    long medium;

    long low;

    long undetermined;

    // QUEUED + PROCESSING: AI chưa trả kết quả.
    long checking;

    // AI lỗi hết lượt thử — admin chạy lại hoặc duyệt thủ công.
    long failed;
}
