package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Caregiver đã có buổi còn hoạt động trùng ít nhất một khoảng được hỏi (C5.1).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BusyCaregiverResponse {
    List<UUID> busyCaregiverIds;

    // Giống PageResponse.of: wrapper một danh sách id, không phải entity ↔ DTO nên không đi qua MapStruct.
    public static BusyCaregiverResponse of(List<UUID> busyCaregiverIds) {
        BusyCaregiverResponse response = new BusyCaregiverResponse();
        response.setBusyCaregiverIds(busyCaregiverIds);
        return response;
    }
}
