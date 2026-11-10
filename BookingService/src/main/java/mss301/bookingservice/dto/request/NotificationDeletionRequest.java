package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Xóa nhiều thông báo của chính người dùng (chọn trên trang thông báo); id của người khác bị bỏ qua.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationDeletionRequest {
    @NotEmpty(message = "INVALID_REQUEST")
    @Size(max = 100, message = "INVALID_REQUEST")
    List<@NotNull(message = "INVALID_REQUEST") UUID> ids;
}
