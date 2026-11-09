package mss301.aiservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Hạn mức chat của người đang hỏi: khách thấy số lượt còn lại trong ngày, người đăng nhập thấy giới hạn ảnh.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatQuotaResponse {
    boolean guest;
    Integer dailyPromptLimit;
    Integer remainingPrompts;
    int maxPromptLength;
    boolean imagesAllowed;
    int maxImages;
    int maxImageSizeMb;
    int maxTotalImageSizeMb;
}
