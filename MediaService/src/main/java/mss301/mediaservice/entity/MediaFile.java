package mss301.mediaservice.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import mss301.mediaservice.enums.FileType;
import mss301.mediaservice.enums.MediaPurpose;
import mss301.mediaservice.enums.MediaStatus;

import java.util.UUID;

@Entity
@Table(name = "media_files")
@AttributeOverride(name = "id", column = @Column(name = "media_file_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MediaFile extends BaseEntity {

    @Column(name = "bucket_name", nullable = false, length = 100)
    String bucketName;

    @Column(name = "object_key", nullable = false, length = 1024, unique = true)
    String objectKey;

    @Column(name = "original_file_name", nullable = false)
    String originalFileName;

    @Column(name = "content_type", nullable = false, length = 100)
    String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 20)
    FileType fileType;

    @Column(name = "file_size", nullable = false)
    Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_purpose", nullable = false, length = 50)
    MediaPurpose mediaPurpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_status", nullable = false, length = 30)
    MediaStatus mediaStatus;

    @Column(name = "uploaded_by")
    UUID uploadedBy;

    @Column(name = "source_service", nullable = false, length = 50)
    String sourceService;

    @Column(name = "reference_type", length = 80)
    String referenceType;

    @Column(name = "reference_id")
    UUID referenceId;

    @Column(name = "etag")
    String etag;
}
