package mss301.paymentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class APIResponse<T> {
    private int code;
    private String message;
    private T result;

    public static <T> APIResponse<T> success(String message, T result) {
        return new APIResponse<>(200, message, result);
    }

    public static APIResponse<Void> success(String message) {
        return new APIResponse<>(200, message, null);
    }

}
