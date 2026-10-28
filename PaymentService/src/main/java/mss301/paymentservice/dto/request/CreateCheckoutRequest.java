package mss301.paymentservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.PaymentProvider;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCheckoutRequest {
    @NotNull
    PaymentProvider provider;
}
