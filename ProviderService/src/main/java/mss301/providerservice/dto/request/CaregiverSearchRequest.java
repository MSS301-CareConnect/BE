package mss301.providerservice.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.CaregiverSearchSort;
import mss301.providerservice.enums.Gender;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Query của GET /api/v1/caregivers/search (SRCH-01), bind bằng {@code @ModelAttribute}. Khung giờ: một cặp
 * {@code slotStart}/{@code slotEnd}, và/hoặc {@code slots} lặp lại dạng {@code <startISO>/<endISO>} (tối đa 60).
 * sort/page/size null → mặc định do service quyết định (RATING_DESC, 0, 20).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CaregiverSearchRequest {

    @NotNull(message = "SEARCH_CENTER_REQUIRED")
    UUID centerId;

    @NotEmpty(message = "CARE_SERVICE_IDS_REQUIRED")
    @Size(max = 10, message = "CARE_SERVICE_IDS_REQUIRED")
    List<UUID> careServiceIds;

    Gender gender;

    @Min(value = 0, message = "MIN_EXPERIENCE_INVALID")
    Integer minExperienceYears;

    @DecimalMin(value = "0", message = "MIN_RATING_INVALID")
    @DecimalMax(value = "5", message = "MIN_RATING_INVALID")
    BigDecimal minRating;

    @Size(max = 255, message = "INVALID_REQUEST")
    String keyword;

    Instant slotStart;

    Instant slotEnd;

    @Size(max = 60, message = "SLOTS_INVALID")
    List<String> slots;

    CaregiverSearchSort sort;

    @Min(value = 0, message = "INVALID_PAGE")
    Integer page;

    @Min(value = 1, message = "INVALID_PAGE")
    @Max(value = 50, message = "INVALID_PAGE")
    Integer size;
}
