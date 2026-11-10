package mss301.bookingservice.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Chấp nhận booking; caregiverId (Provider) tùy chọn — có thì phân công trước khi chuyển trạng thái.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AcceptRequest {
    UUID caregiverId;
}
