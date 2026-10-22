package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Bản sao một phần response C2.6 GET /api/v1/payments/me/outstanding — Booking chỉ cần cờ hasDebt.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OutstandingDebtResponse {
    boolean hasDebt;
}
