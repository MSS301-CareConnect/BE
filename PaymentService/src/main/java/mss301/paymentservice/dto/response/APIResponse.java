package mss301.paymentservice.dto.response;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIResponse<T> {
    int code;

    String message;

    T result;

    public static <T> APIResponse<T> success(String message, T result) {
        return new APIResponse<>(200, message, result);
    }

    public static APIResponse<Void> success(String message) {
        return new APIResponse<>(200, message, null);
    }

}
