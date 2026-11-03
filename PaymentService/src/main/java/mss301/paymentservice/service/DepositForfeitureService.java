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

/**
 * CR-4: sổ cọc bị giữ. Ghi luôn trong transaction của lệnh hoàn tiền tạo ra nó; đọc theo phạm vi role — CAREGIVER phần
 * mình hưởng, CENTER_MANAGER trung tâm trong token, ADMIN mọi trung tâm (hoặc một trung tâm).
 */
public interface DepositForfeitureService {

    void record(OccurrenceFinancial occurrenceFinancial, UUID paymentId, BigDecimal amount, RefundReason reason,
                String rule, String idempotencyKey);

    APIResponse<PageResponse<DepositForfeitureResponse>> list(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                              UUID centerId, Instant from, Instant to, int page,
                                                              int size);

    APIResponse<DepositForfeitureSummaryResponse> getSummary(UUID actorId, Set<RoleCode> roles, UUID claimCenterId,
                                                             UUID centerId, Instant from, Instant to);
}
