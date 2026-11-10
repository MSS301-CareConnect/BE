package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Bản sao envelope APIResponse của service được gọi; bỏ qua field lạ để bên kia thêm field không làm hỏng service này.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteApiResponse<T> {
    int code;

    String message;

    T result;
}
