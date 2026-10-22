package mss301.bookingservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// BK-03: khách xem trước số tiền hoàn nếu hủy. occurrenceIds rỗng = mọi buổi chưa diễn ra.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CancellationPreviewRequest {
    @Size(max = 500, message = "TOO_MANY_OCCURRENCES")
    List<UUID> occurrenceIds;
}
