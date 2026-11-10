package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FeedbackRequest {
    @NotNull(message = "INVALID_REQUEST")
    UUID bookingId;
    @NotNull(message = "INVALID_REQUEST")
    UUID caregiverId;
    @NotNull(message = "RATING_OUT_OF_RANGE")
    @Min(value = 1, message = "RATING_OUT_OF_RANGE")
    @Max(value = 5, message = "RATING_OUT_OF_RANGE")
    Integer rating;
    @Size(max = 2000, message = "INVALID_REQUEST")
    String comment;
}
