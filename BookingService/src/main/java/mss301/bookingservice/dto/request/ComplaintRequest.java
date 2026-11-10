package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import mss301.bookingservice.enums.ComplaintTargetType;
import mss301.bookingservice.enums.ComplaintCategory;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ComplaintRequest {
    @NotNull(message = "INVALID_REQUEST")
    UUID bookingId;
    UUID occurrenceId;
    @NotNull(message = "INVALID_REQUEST")
    ComplaintTargetType targetType;
    @NotNull(message = "INVALID_REQUEST")
    ComplaintCategory category;
    @NotBlank(message = "COMPLAINT_CONTENT_REQUIRED")
    @Size(max = 200, message = "INVALID_REQUEST")
    String title;
    @NotBlank(message = "COMPLAINT_CONTENT_REQUIRED")
    @Size(max = 4000, message = "INVALID_REQUEST")
    String content;
}
