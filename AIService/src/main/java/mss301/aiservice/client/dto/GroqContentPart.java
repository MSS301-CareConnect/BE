package mss301.aiservice.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroqContentPart {
    String type;
    String text;

    @JsonProperty("image_url")
    GroqImageUrl imageUrl;

    public static GroqContentPart text(String text) {
        return GroqContentPart.builder()
                .type("text")
                .text(text)
                .build();
    }

    public static GroqContentPart image(String imageUrl) {
        return GroqContentPart.builder()
                .type("image_url")
                .imageUrl(GroqImageUrl.builder().url(imageUrl).build())
                .build();
    }
}
