package mss301.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Bản sao InternalCaregiverResponse của ProviderService. careServiceIds: null = Provider chưa quản lý kỹ năng,
// [] = không làm được dịch vụ nào. Trạng thái giữ dạng String để Provider thêm giá trị mới không làm hỏng service này.
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderCaregiverResponse {
    public static final String STATUS_VERIFIED = "VERIFIED";
    public static final String STATUS_ACTIVE = "ACTIVE";

    UUID caregiverId;

    UUID userId;

    UUID centerId;

    String centerStatus;

    String fullName;

    String verificationStatus;

    String caregiverStatus;

    List<UUID> careServiceIds;
}
