package mss301.aiservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import mss301.aiservice.enums.MessageRole;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessage extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "chat_message_id", nullable = false, updatable = false)
    UUID chatMessageId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_role", nullable = false, length = 20)
    MessageRole messageRole;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    String content;

    @Column(name = "sequence_number", nullable = false)
    Integer sequenceNumber;

    @Column(name = "model_name", length = 120)
    String modelName;

    @Column(name = "prompt_tokens")
    Integer promptTokens;

    @Column(name = "completion_tokens")
    Integer completionTokens;
}
