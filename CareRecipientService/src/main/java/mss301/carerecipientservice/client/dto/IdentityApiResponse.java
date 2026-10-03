package mss301.carerecipientservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Bản sao envelope APIResponse của IdentityService; bỏ qua field lạ để Identity thêm field không làm hỏng service này.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentityApiResponse<T> {
    int code;

    String message;

    T result;
}
