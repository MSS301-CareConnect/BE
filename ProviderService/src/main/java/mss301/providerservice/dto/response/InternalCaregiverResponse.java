package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CaregiverStatus;
import mss301.providerservice.enums.CenterStatus;
import mss301.providerservice.enums.VerificationStatus;

import java.util.List;
import java.util.UUID;

// Dữ liệu caregiver cho service khác qua /internal/caregivers/** (Booking phân công caregiver).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InternalCaregiverResponse {
    UUID caregiverId;

    // UUID tài khoản CAREGIVER bên IdentityService (= sub trong JWT của caregiver)
    UUID userId;

    UUID centerId;

    CenterStatus centerStatus;

    String fullName;

    VerificationStatus verificationStatus;

    CaregiverStatus caregiverStatus;

    // null = ProviderService CHƯA quản lý kỹ năng (khác danh sách rỗng = không làm được dịch vụ nào).
    // TODO(caregiver_services): đọc từ bảng kỹ năng khi có.
    List<UUID> careServiceIds;
}
