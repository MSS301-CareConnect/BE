package mss301.paymentservice.service;

import mss301.paymentservice.dto.response.APIResponse;
import mss301.paymentservice.dto.response.DepositForfeitureResponse;
import mss301.paymentservice.dto.response.DepositForfeitureSummaryResponse;
import mss301.paymentservice.dto.response.PageResponse;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.enums.RefundReason;
import mss301.paymentservice.enums.RoleCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public interface DepositForfeitureService {

    // Ghi nhận khoản tiền cọc bị giữ của một buổi.
    void record(OccurrenceFinancial occurrenceFinancial, UUID paymentId, BigDecimal amount, RefundReason reason,
                String rule, String idempotencyKey);

    // Danh sách tiền cọc bị giữ theo phạm vi người gọi.
    APIResponse<PageResponse<DepositForfeitureResponse>> list(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                              UUID centerId, Instant from, Instant to, int page,
                                                              int size);

    // Tổng hợp tiền cọc bị giữ theo phạm vi người gọi.
    APIResponse<DepositForfeitureSummaryResponse> getSummary(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                             UUID centerId, Instant from, Instant to);
}
