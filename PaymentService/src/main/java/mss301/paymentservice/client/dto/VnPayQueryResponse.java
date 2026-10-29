package mss301.paymentservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

// Phần response querydr VNPay mà Payment cần đọc.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VnPayQueryResponse {
    @JsonProperty("vnp_ResponseCode")
    String responseCode;

    @JsonProperty("vnp_TransactionStatus")
    String transactionStatus;

    @JsonProperty("vnp_TransactionNo")
    String transactionNo;

    @JsonProperty("vnp_Amount")
    String amount;
}
