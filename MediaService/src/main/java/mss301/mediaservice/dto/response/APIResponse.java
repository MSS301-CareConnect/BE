package mss301.mediaservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIResponse<T> {
    int code;
    String message;
    T result;

    public static <T> APIResponse<T> success(String message, T result) {
        APIResponse<T> response = new APIResponse<>();
        response.setCode(200);
        response.setMessage(message);
        response.setResult(result);
        return response;
    }

    public static <T> APIResponse<T> success(String message) {
        return success(message, null);
    }
}
