package mss301.paymentservice.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VnPayIpnResponse {
    @JsonProperty("RspCode")
    String responseCode;

    @JsonProperty("Message")
    String message;
}
