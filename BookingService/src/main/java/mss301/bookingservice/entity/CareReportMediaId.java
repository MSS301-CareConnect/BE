package mss301.bookingservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareReportMediaId implements Serializable {
    @Column(name = "report_id")
    UUID reportId;
    @Column(name = "media_file_id")
    UUID mediaFileId;
}
