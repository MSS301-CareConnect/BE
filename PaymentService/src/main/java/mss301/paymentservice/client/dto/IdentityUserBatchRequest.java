package mss301.paymentservice.client.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IdentityUserBatchRequest {
    // Identity nhận tối đa 100 id / lần — bằng cỡ trang lớn nhất của danh sách rút tiền.
    public static final int MAX_SIZE = 100;

    List<UUID> userIds;
}
