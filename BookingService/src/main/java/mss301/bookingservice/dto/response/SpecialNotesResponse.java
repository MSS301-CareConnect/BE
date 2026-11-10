package mss301.bookingservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

// Ghi chú đặc biệt caregiver cần đọc trước ca (OPS-01) — trích từ snapshot người bệnh lúc đặt, cùng điều kiện xem snapshot.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SpecialNotesResponse {
    String allergies;

    String chronicConditions;

    String dietaryNotes;

    String behaviorNotes;

    String careRequirements;

    List<RecipientSnapshot.MedicationItem> medications;
}
