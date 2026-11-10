package mss301.carecatalogservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

// Sửa nhiều gói giá cùng lúc: tất cả thành công hoặc không gói nào đổi (một transaction, mỗi gói tự kiểm version).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PricePlanBatchUpdateRequest {

    @NotEmpty(message = "PRICE_PLAN_BATCH_INVALID")
    @Size(max = 50, message = "PRICE_PLAN_BATCH_INVALID")
    List<@Valid @NotNull(message = "PRICE_PLAN_BATCH_INVALID") Item> items;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Item {

        @NotNull(message = "PRICE_PLAN_BATCH_INVALID")
        UUID id;

        @Valid
        @NotNull(message = "PRICE_PLAN_BATCH_INVALID")
        PricePlanRequest plan;
    }
}
