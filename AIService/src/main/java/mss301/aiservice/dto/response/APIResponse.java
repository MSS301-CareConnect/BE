package mss301.aiservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
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
        return APIResponse.<T>builder()
                .code(200)
                .message(message)
                .result(result)
                .build();
    }

    public static <T> APIResponse<T> success(String message) {
        return success(message, null);
    }
}
