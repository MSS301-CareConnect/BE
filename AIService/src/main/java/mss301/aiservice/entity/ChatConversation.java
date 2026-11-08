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
import mss301.aiservice.enums.ConversationStatus;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "chat_conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatConversation extends BaseEntity {
    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "conversation_id", nullable = false, updatable = false)
    UUID conversationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    UUID userId;

    @Column(name = "title", nullable = false, length = 120)
    String title;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "conversation_status", nullable = false, length = 20)
    ConversationStatus conversationStatus = ConversationStatus.ACTIVE;
}
