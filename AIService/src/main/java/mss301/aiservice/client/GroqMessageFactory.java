package mss301.aiservice.client;

import mss301.aiservice.client.dto.ChatMediaUpload;
import mss301.aiservice.client.dto.GroqContentPart;
import mss301.aiservice.client.dto.GroqImageUrl;
import mss301.aiservice.client.dto.GroqMessageRequest;
import mss301.aiservice.config.ChatPrompts;
import mss301.aiservice.entity.ChatMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class GroqMessageFactory {
    public List<GroqMessageRequest> buildMessages(List<ChatMessage> history, String prompt, List<ChatMediaUpload> uploadedImages) {
        List<GroqMessageRequest> messages = new ArrayList<>();

        messages.add(GroqMessageRequest.text("system", ChatPrompts.SYSTEM_PROMPT));
        addHistory(messages, history);
        addCurrentMessage(messages, prompt, uploadedImages);
        return messages;
    }

    private void addHistory(List<GroqMessageRequest> messages, List<ChatMessage> history) {
        if (history == null) {
            return;
        }

        for (ChatMessage message : history) {
            String role = message.getMessageRole()
                            .name()
                            .toLowerCase(Locale.ROOT);

            messages.add(GroqMessageRequest.text(role, message.getContent()));
        }
    }

    private void addCurrentMessage(List<GroqMessageRequest> messages, String prompt, List<ChatMediaUpload> uploadedImages) {
        if (uploadedImages == null || uploadedImages.isEmpty()) {
            messages.add(GroqMessageRequest.text("user", prompt));
            return;
        }

        List<GroqContentPart> contentParts = new ArrayList<>();

        contentParts.add(GroqContentPart.builder()
                        .type("text")
                        .text(prompt)
                        .build());

        for (ChatMediaUpload image : uploadedImages) {
            GroqImageUrl imageUrl = GroqImageUrl.builder()
                            .url(image.getDownloadUrl())
                            .build();

            contentParts.add(GroqContentPart.builder()
                            .type("image_url")
                            .imageUrl(imageUrl)
                            .build());
        }

        messages.add(GroqMessageRequest.builder()
                        .role("user")
                        .content(contentParts)
                        .build());
    }
}
