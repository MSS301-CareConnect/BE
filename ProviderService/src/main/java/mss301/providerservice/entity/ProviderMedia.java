package mss301.providerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.MediaOwnerType;
import mss301.providerservice.enums.MediaFileType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "provider_media",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_provider_media_file",
                        columnNames = "media_file_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_provider_media_owner",
                        columnList = "owner_type, owner_id"
                )
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProviderMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "provider_media_id", nullable = false)
    UUID providerMediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 30)
    MediaOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    UUID ownerId;

    @Column(name = "media_file_id", nullable = false, unique = true)
    UUID mediaFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_file_type", nullable = false, length = 30)
    MediaFileType mediaFileType;

    @Column(name = "display_order", nullable = false)
    Integer displayOrder;

    @Column(name = "created_by", nullable = false, updatable = false)
    UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (displayOrder == null) {
            displayOrder = 0;
        }

        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
