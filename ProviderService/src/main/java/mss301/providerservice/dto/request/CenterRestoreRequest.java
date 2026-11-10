package mss301.providerservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// D31: khôi phục trung tâm đã lưu trữ về INACTIVE — bắt buộc lý do để còn dấu vết.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CenterRestoreRequest {

    @NotBlank(message = "CENTER_STATUS_REASON_REQUIRED")
    @Size(max = 500, message = "CENTER_STATUS_REASON_TOO_LONG")
    String reason;

    @NotNull(message = "CENTER_VERSION_REQUIRED")
    Long version;
}
