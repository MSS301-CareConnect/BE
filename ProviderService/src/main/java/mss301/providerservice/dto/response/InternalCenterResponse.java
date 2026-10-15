package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CenterStatus;

import java.util.UUID;

// Dữ liệu trung tâm cho service khác qua /internal/centers/** (Booking kiểm trung tâm đang hoạt động).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalCenterResponse {
    UUID centerId;

    String centerName;

    CenterStatus centerStatus;

    UUID managerUserId;
}
