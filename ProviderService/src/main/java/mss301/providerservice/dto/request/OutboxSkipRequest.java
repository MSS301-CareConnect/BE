package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// H-08: admin đã xử lý tay việc này (khóa tài khoản / hủy booking) → bỏ qua dòng FAILED, ghi lý do.
@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OutboxSkipRequest {
    @NotBlank
    @Size(max = 400)
    String note;
}
