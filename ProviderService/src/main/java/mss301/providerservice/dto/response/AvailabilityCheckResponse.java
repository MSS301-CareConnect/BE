package mss301.providerservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

// C5.2: available = không có vi phạm nào; mỗi khung vi phạm xuất hiện một lần với lý do ưu tiên TIME_OFF.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AvailabilityCheckResponse {
    boolean available;
    List<AvailabilityViolationResponse> violations;
}
