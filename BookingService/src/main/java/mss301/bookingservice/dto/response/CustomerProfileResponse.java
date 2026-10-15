package mss301.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

// Thông tin khách hàng từ IdentityService (batch) cho màn hình trung tâm / admin.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerProfileResponse {
    UUID id;

    String fullName;

    String phoneNumber;

    String email;
}
