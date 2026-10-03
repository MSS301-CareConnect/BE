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

    // Quyền của người gọi trên hồ sơ (null với API admin)
    RecipientAccessLevel myAccessLevel;

    List<MedicationResponse> medications;

    List<EmergencyContactResponse> emergencyContacts;
}
