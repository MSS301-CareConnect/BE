package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SessionNoteRequest {
    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 2000, message = "INVALID_REQUEST")
    String content;
    @Valid
    SessionVitals vitals;
}
