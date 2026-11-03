package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

// CR-2: địa chỉ chăm sóc mới cho các buổi chưa bắt đầu của booking (thay toàn bộ phần địa chỉ trong snapshot).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceAddressRequest {
    @NotBlank(message = "ADDRESS_LINE_REQUIRED")
    @Size(max = 255, message = "TEXT_TOO_LONG")
    String addressLine;

    @Size(max = 100, message = "TEXT_TOO_LONG")
    String ward;

    @Size(max = 100, message = "TEXT_TOO_LONG")
    String district;

    @NotBlank(message = "PROVINCE_REQUIRED")
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String province;

    // Ghi chú cho trung tâm / caregiver (vd "người bệnh đang ở nhà con gái"); không bắt buộc.
    @Size(max = 500, message = "TEXT_TOO_LONG")
    String note;
}
