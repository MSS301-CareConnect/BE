package mss301.carerecipientservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.carerecipientservice.enums.MedicationRoute;

import java.time.LocalDate;
import java.util.List;

// PUT thay thế toàn bộ (field null = xóa giá trị), trừ "active": chỉ dùng khi tạo (mặc định true); đổi qua PATCH /active.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MedicationRequest {
    @NotBlank(message = "MEDICATION_NAME_REQUIRED")
    @Size(max = 255, message = "TEXT_TOO_LONG")
    String medicationName;

    @Size(max = 255, message = "TEXT_TOO_LONG")
    String dosage;

    @Size(max = 255, message = "TEXT_TOO_LONG")
    String frequency;

    @Size(max = 24, message = "TOO_MANY_ITEMS")
    List<@NotNull(message = "INVALID_TIME_OF_DAY") @Pattern(regexp = "([01][0-9]|2[0-3]):[0-5][0-9]", message = "INVALID_TIME_OF_DAY") String> timeOfDay;

    MedicationRoute route;

    @NotNull(message = "START_DATE_REQUIRED")
    LocalDate startDate;

    LocalDate endDate;

    @Size(max = 255, message = "TEXT_TOO_LONG")
    String prescribedBy;

    @Size(max = 5000, message = "TEXT_TOO_LONG")
    String usageNotes;

    Boolean active;
}
