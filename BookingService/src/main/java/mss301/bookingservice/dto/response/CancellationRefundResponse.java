package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.enums.RefundReason;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Kèm response hủy (BK-03 / BK-04): yêu cầu hoàn tiền đã xếp gửi PaymentService (bất đồng bộ qua outbox).
// totalRefund chỉ có khi khách hủy kèm previewToken hợp lệ; số thực tế do PaymentService chốt.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CancellationRefundResponse {
    RefundReason reason;

    List<UUID> occurrenceIds;

    BigDecimal totalRefund;
}
