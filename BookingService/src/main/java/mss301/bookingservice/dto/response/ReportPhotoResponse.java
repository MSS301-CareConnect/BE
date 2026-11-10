package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.UUID;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReportPhotoResponse {
    UUID mediaFileId;
    Integer displayOrder;
    String downloadUrl;
    Instant expiresAt;
}
