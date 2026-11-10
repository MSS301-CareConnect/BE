package mss301.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.dto.request.SessionVitals;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Table(name = "care_session_notes")
@AttributeOverride(name = "id", column = @Column(name = "note_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CareSessionNote extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    CareSession session;
    @Column(name = "content", nullable = false, length = 2000)
    String content;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "vitals")
    SessionVitals vitals;
    @Column(name = "created_by", nullable = false)
    UUID createdBy;
}
