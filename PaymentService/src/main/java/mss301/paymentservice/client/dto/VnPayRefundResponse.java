package mss301.paymentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Phần response API refund VNPay mà Payment cần đọc.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VnPayRefundResponse {
    @JsonProperty("vnp_ResponseId")
    String responseId;

    @JsonProperty("vnp_ResponseCode")
    String responseCode;

    @JsonProperty("vnp_Message")
    String message;

    @JsonProperty("vnp_TransactionNo")
    String transactionNo;
}
