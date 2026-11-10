package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

// Bản sao một phần PolicySnapshotResponse của PaymentService (C2.1) — Booking chỉ cần id + % cọc.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentPolicyResponse {
    UUID policyId;

    // Phần trăm (vd 30 = 30%).
    BigDecimal depositPercent;
}
