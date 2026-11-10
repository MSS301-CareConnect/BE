package mss301.carerecipientservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Bản sao envelope APIResponse của BookingService; bỏ qua field lạ để Booking thêm field không làm hỏng service này.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingApiResponse<T> {
    int code;

    String message;

    T result;
}
