package mss301.carerecipientservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecipientAddressRequest {
    @NotBlank(message = "ADDRESS_LABEL_REQUIRED")
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String label;

    @NotBlank(message = "ADDRESS_LINE_REQUIRED")
    @Size(max = 255, message = "TEXT_TOO_LONG")
    String addressLine;

    @Size(max = 100, message = "TEXT_TOO_LONG")
    String ward;

    @Size(max = 100, message = "TEXT_TOO_LONG")
    String district;

    @NotBlank(message = "ADDRESS_PROVINCE_REQUIRED")
    @Size(max = 100, message = "TEXT_TOO_LONG")
    String province;

    @Size(max = 500, message = "TEXT_TOO_LONG")
    String note;
}
