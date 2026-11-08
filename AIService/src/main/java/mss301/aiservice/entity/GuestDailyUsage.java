package mss301.aiservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "guest_daily_usage")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GuestDailyUsage extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "guest_daily_usage_id", nullable = false, updatable = false)
    UUID guestDailyUsageId;

    @Column(name = "visitor_hash", nullable = false, length = 64, updatable = false)
    String visitorHash;

    @Column(name = "usage_date", nullable = false, updatable = false)
    LocalDate usageDate;

    @Builder.Default
    @Column(name = "prompt_count", nullable = false)
    Integer promptCount = 0;
}
