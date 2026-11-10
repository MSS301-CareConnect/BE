package mss301.carerecipientservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.RecipientAccessLevel;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CareRecipientDetailResponse {
    CareRecipientResponse profile;

    // Quyền của người gọi trên hồ sơ (null khi ADMIN / CAREGIVER xem)
    RecipientAccessLevel myAccessLevel;

    List<MedicationResponse> medications;

    List<EmergencyContactResponse> emergencyContacts;

    // Chỉ có khi CAREGIVER được phân công xem: khi đó profile / medications / myAccessLevel luôn null (bị bỏ khỏi JSON);
    // emergencyContacts có mọi liên hệ khẩn cấp để caregiver gọi khi cần.
    RecipientSnapshotResponse careSnapshot;

    // Chỉ có khi CAREGIVER được phân công xem: tiền sử bệnh, thói quen ngủ, chiều cao / cân nặng.
    CaregiverCareDetailsResponse careDetails;
}
