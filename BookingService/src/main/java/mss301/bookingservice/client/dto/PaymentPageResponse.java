package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentPageResponse {
    @JsonAlias("content")
    List<PaymentSummaryResponse> items;
    int totalPages;
}
