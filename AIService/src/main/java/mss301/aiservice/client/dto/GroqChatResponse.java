package mss301.aiservice.client.dto;

import lombok.Builder;
import lombok.Data;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GroqChatResponse {
    String id;

    String model;

    List<GroqChoiceResponse> choices;

    GroqUsageResponse usage;
}
