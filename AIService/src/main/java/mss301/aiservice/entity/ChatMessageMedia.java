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

import java.util.UUID;

@Entity
@Table(name = "chat_message_media")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageMedia extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "chat_message_media_id", nullable = false, updatable = false)
    UUID chatMessageMediaId;

    @Column(name = "chat_message_id", nullable = false, updatable = false)
    UUID chatMessageId;

    @Column(name = "media_file_id", nullable = false, updatable = false)
    UUID mediaFileId;

    @Column(name = "original_file_name", nullable = false, length = 255)
    String originalFileName;

    @Column(name = "mime_type", nullable = false, length = 100)
    String mimeType;

    @Column(name = "sort_order", nullable = false)
    Short sortOrder;
}
