package mss301.providerservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import mss301.providerservice.enums.RegistrationStatus;

import java.util.List;
import java.util.UUID;

/** Duyệt / từ chối nhiều đăng ký của cùng một ca trong một transaction (tất cả hoặc không). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegistrationBulkReviewRequest {

    @NotNull(message = "INVALID_REQUEST")
    RegistrationStatus status;

    @Size(max = 500, message = "INVALID_REQUEST")
    String reason;

    @NotEmpty(message = "REGISTRATION_BULK_INVALID")
    @Size(max = 50, message = "REGISTRATION_BULK_INVALID")
    List<@Valid @NotNull(message = "REGISTRATION_BULK_INVALID") Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Item {
        @NotNull(message = "REGISTRATION_BULK_INVALID")
        UUID registrationId;

        @NotNull(message = "CONCURRENT_UPDATE")
        Long version;
    }
}
