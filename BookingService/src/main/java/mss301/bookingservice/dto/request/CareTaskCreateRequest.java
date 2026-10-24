package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareTaskCreateRequest {
    @NotBlank(message = "INVALID_REQUEST")
    @Size(max = 200, message = "INVALID_REQUEST")
    String title;
}
