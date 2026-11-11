package mss301.bookingservice.mapper;

import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.client.dto.RefundCreateRequest;
import mss301.bookingservice.client.dto.RefundQuoteRequest;
import mss301.bookingservice.client.dto.RefundQuoteResponse;
import mss301.bookingservice.dto.response.CancellationPreviewResponse;
import mss301.bookingservice.dto.response.CancellationRefundResponse;
import mss301.bookingservice.client.dto.RemainingInvoiceRequest;
import mss301.bookingservice.enums.BookingStatus;
import mss301.bookingservice.enums.CancelledBy;
import mss301.bookingservice.enums.RefundReason;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.request.ServiceAddressRequest;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.BookingQuoteResponse;
import mss301.bookingservice.dto.response.OccurrenceSlot;
import mss301.bookingservice.dto.response.PaymentContextOccurrenceResponse;
import mss301.bookingservice.dto.response.PaymentHeldOccurrences;
import mss301.bookingservice.dto.response.PaymentContextResponse;
import mss301.bookingservice.dto.response.QuoteLineResponse;
import mss301.bookingservice.dto.response.QuoteOccurrenceResponse;
import mss301.bookingservice.dto.response.ServiceQuote;
import mss301.bookingservice.dto.response.BookingStatusCountResponse;
import mss301.bookingservice.dto.response.BookingStatusCountsResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.CaregiverBookingRequestResponse;
import mss301.bookingservice.dto.response.CaregiverHeldScheduleResponse;
import mss301.bookingservice.dto.response.CustomerProfileResponse;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.CenterBookingPolicy;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.repository.OccurrenceStatsView;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(config = MapStructConfig.class, uses = {OccurrenceMapper.class, BookingDetailMapper.class})
public interface BookingMapper {

    String ADDRESS_SEPARATOR = ", ";

    // Tạo booking từ request + dữ liệu đã xác minh (customer từ token, snapshot từ CareRecipient, caregiver từ
    // Provider, báo giá từ Catalog). Mã, trạng thái, hạn duyệt, múi giờ, idempotency do service quyết định rồi gán.
    @IgnoreServerManagedFields
    @IgnoreServerManagedBookingFields
    // request và snapshot cùng có recipientId; request và caregiver cùng có caregiverId → chỉ rõ nguồn
    @Mapping(target = "recipientId", source = "request.recipientId")
    @Mapping(target = "caregiverId", source = "caregiver.caregiverId")
    @Mapping(target = "caregiverUserId", source = "caregiver.caregiverUserId")
    @Mapping(target = "recipientSnapshot", source = "recipientSnapshot")
    @Mapping(target = "recurrenceFrequency", source = "request.frequency")
    // Ngày buổi đầu / buổi cuối do service tính từ các buổi đã sinh (mọi loại booking); request chỉ có ở RECURRING.
    @Mapping(target = "startDate", source = "startDate")
    @Mapping(target = "endDate", source = "endDate")
    @Mapping(target = "serviceAddress", source = "recipientSnapshot", qualifiedByName = "toServiceAddress")
    Booking toBooking(BookingCreateRequest request, UUID customerId, RecipientSnapshot recipientSnapshot,
                      AssignedCaregiver caregiver, BookingQuote quote, LocalDate startDate, LocalDate endDate,
                      BigDecimal initialPaymentPercent);

    // Báo giá tạm tính (BK-01): tỷ lệ + khoản thanh toán đầu theo center policy (policy mặc định → policyId null).
    @Mapping(target = "occurrenceCount", expression = "java(quote.getOccurrences().size())")
    @Mapping(target = "lines", source = "quote.items")
    @Mapping(target = "occurrences", source = "quote.occurrences")
    @Mapping(target = "totalAmount", source = "quote.totalAmount")
    @Mapping(target = "depositPercent", source = "initialPaymentPercent")
    @Mapping(target = "policyId", source = "policy.id")
    BookingQuoteResponse toQuoteResponse(BookingQuote quote, CenterBookingPolicy policy, BigDecimal initialPaymentPercent,
                                         BigDecimal estimatedDepositAmount);

    @Mapping(target = "pricingUnit", source = "unitType")
    @Mapping(target = "lineTotal", source = "totalPrice")
    QuoteLineResponse toQuoteLineResponse(ServiceQuote serviceQuote);

    QuoteOccurrenceResponse toQuoteOccurrenceResponse(OccurrenceSlot slot);

    // payment-context (C3.2, PaymentService gọi bằng token SERVICE). Ngày cọc thật = confirmedAt.
    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "centerId", source = "booking.centerId")
    @Mapping(target = "status", source = "booking.status")
    @Mapping(target = "depositPaidAt", source = "booking.confirmedAt")
    @Mapping(target = "occurrences", source = "occurrences")
    @Mapping(target = "refundThresholdMinutes", source = "policy.refundThresholdMinutes")
    @Mapping(target = "refundPercentBeforeThreshold", source = "policy.refundPercentBeforeThreshold")
    @Mapping(target = "refundPercentAfterThreshold", source = "policy.refundPercentAfterThreshold")
    @Mapping(target = "lateCancelNoRefundMinutes", source = "policy.lateCancelNoRefundMinutes")
    @Mapping(target = "overduePaymentForfeitPercent", source = "policy.overduePaymentForfeitPercent")
    PaymentContextResponse toPaymentContextResponse(Booking booking, List<BookingOccurrence> occurrences,
                                                    Instant firstStartAt, CenterBookingPolicy policy,
                                                    @Context Set<UUID> occurrenceIdsWithSession,
                                                    @Context PaymentHeldOccurrences heldOccurrences);

    // depositAmount từng buổi do PaymentService ghi khi xác nhận cọc (PAY-08) — chưa có cột ở Booking.
    @Mapping(target = "occurrenceId", source = "id")
    @Mapping(target = "depositAmount", ignore = true)
    @Mapping(target = "hasSession", expression = "java(occurrenceIdsWithSession.contains(occurrence.getId()))")
    @Mapping(target = "settlementHeld", expression = "java(heldOccurrences.contains(occurrence.getId()))")
    PaymentContextOccurrenceResponse toPaymentContextOccurrenceResponse(BookingOccurrence occurrence,
                                                                        @Context Set<UUID> occurrenceIdsWithSession,
                                                                        @Context PaymentHeldOccurrences heldOccurrences);

    // customerProfiles rỗng (màn hình khách) → customer null; trung tâm / admin truyền kết quả batch Identity.
    @Mapping(target = "recipientName", source = "recipientSnapshot.fullName")
    @Mapping(target = "customer", expression = "java(toCustomerProfileResponse(customerProfiles.get(booking.getCustomerId())))")
    @Mapping(target = "totalCount", expression = "java(countTotal(occurrenceStats.get(booking.getId())))")
    @Mapping(target = "completedCount", expression = "java(countCompleted(occurrenceStats.get(booking.getId())))")
    @Mapping(target = "nextOccurrenceAt", expression = "java(occurrenceStats.containsKey(booking.getId()) ? occurrenceStats.get(booking.getId()).getNextOccurrenceAt() : null)")
    @Mapping(target = "deadlineType", expression = "java(mss301.bookingservice.util.BookingDeadlines.typeOf(booking))")
    @Mapping(target = "deadlineAt", expression = "java(mss301.bookingservice.util.BookingDeadlines.dueAt(booking))")
    @Mapping(target = "serviceNames", expression = "java(serviceNames.getOrDefault(booking.getId(), List.of()))")
    BookingSummaryResponse toSummaryResponse(Booking booking, @Context Map<UUID, IdentityUserResponse> customerProfiles,
                                             @Context Map<UUID, OccurrenceStatsView> occurrenceStats,
                                             @Context Map<UUID, List<String>> serviceNames);

    default int countTotal(OccurrenceStatsView stats) {
        return stats == null || stats.getTotalCount() == null ? 0 : stats.getTotalCount().intValue();
    }

    default int countCompleted(OccurrenceStatsView stats) {
        return stats == null || stats.getCompletedCount() == null ? 0 : stats.getCompletedCount().intValue();
    }

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<BookingSummaryResponse> toSummaryResponseList(List<Booking> bookings,
                                                       @Context Map<UUID, IdentityUserResponse> customerProfiles,
                                                       @Context Map<UUID, OccurrenceStatsView> occurrenceStats,
                                                       @Context Map<UUID, List<String>> serviceNames);

    BookingStatusCountResponse toStatusCountResponse(BookingStatus status, long count, Instant nearestDueAt,
                                                     long dueSoonCount, long overdueCount);

    BookingStatusCountsResponse toStatusCountsResponse(List<BookingStatusCountResponse> statuses, long dueSoonMinutes,
                                                       Instant generatedAt);

    // booking và customer cùng có id / status → chỉ rõ nguồn; services / occurrences là tham số trùng tên đích.
    @Mapping(target = "id", source = "booking.id")
    @Mapping(target = "status", source = "booking.status")
    @Mapping(target = "services", source = "services")
    @Mapping(target = "occurrences", source = "occurrences")
    @Mapping(target = "customer", source = "customer")
    @Mapping(target = "refund", ignore = true)
    @Mapping(target = "deadlineType", expression = "java(mss301.bookingservice.util.BookingDeadlines.typeOf(booking))")
    @Mapping(target = "deadlineAt", expression = "java(mss301.bookingservice.util.BookingDeadlines.dueAt(booking))")
    BookingDetailResponse toDetailResponse(Booking booking, List<BookingDetail> services,
                                           List<BookingOccurrence> occurrences, IdentityUserResponse customer,
                                           Instant serverTime);

    CustomerProfileResponse toCustomerProfileResponse(IdentityUserResponse user);

    // CR-3: đơn chờ caregiver nhận — khu vực, tên và tình trạng sức khỏe từ snapshot; không số nhà / SĐT / thuốc chi tiết.
    @Mapping(target = "id", source = "booking.id")
    @Mapping(target = "recipientAge", source = "booking.recipientSnapshot.age")
    @Mapping(target = "recipientGender", source = "booking.recipientSnapshot.gender")
    @Mapping(target = "recipientName", source = "booking.recipientSnapshot.fullName")
    @Mapping(target = "mobilityLevel", source = "booking.recipientSnapshot.mobilityLevel")
    @Mapping(target = "independenceLevel", source = "booking.recipientSnapshot.independenceLevel")
    @Mapping(target = "chronicConditions", source = "booking.recipientSnapshot.chronicConditions")
    @Mapping(target = "allergies", source = "booking.recipientSnapshot.allergies")
    @Mapping(target = "careRequirements", source = "booking.recipientSnapshot.careRequirements")
    @Mapping(target = "dietaryNotes", source = "booking.recipientSnapshot.dietaryNotes")
    @Mapping(target = "behaviorNotes", source = "booking.recipientSnapshot.behaviorNotes")
    @Mapping(target = "medicationCount", expression = "java(booking.getRecipientSnapshot() == null || booking.getRecipientSnapshot().getActiveMedications() == null ? 0 : booking.getRecipientSnapshot().getActiveMedications().size())")
    @Mapping(target = "customerName", source = "customerName")
    @Mapping(target = "ward", source = "booking.recipientSnapshot.ward")
    @Mapping(target = "district", source = "booking.recipientSnapshot.district")
    @Mapping(target = "province", source = "booking.recipientSnapshot.province")
    @Mapping(target = "services", source = "services")
    @Mapping(target = "occurrences", source = "occurrences")
    CaregiverBookingRequestResponse toCaregiverRequestResponse(Booking booking, List<BookingDetail> services,
                                                               List<BookingOccurrence> occurrences, String customerName);

    // Kết quả hủy (BK-03 / BK-04) gắn vào chi tiết booking sau khi dựng.
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "refund", source = "refund")
    void updateRefund(CancellationRefundResponse refund, @MappingTarget BookingDetailResponse response);

    CancellationRefundResponse toCancellationRefundResponse(RefundReason reason, List<UUID> occurrenceIds,
                                                            BigDecimal totalRefund);

    // C2.2: hỏi PaymentService tiền hoàn nếu hủy các buổi. booking cũng có cancelledBy / cancelledAt → chỉ rõ tham số.
    @Mapping(target = "bookingId", source = "booking.id")
    @Mapping(target = "bookingStatus", source = "booking.status")
    @Mapping(target = "cancelledBy", source = "cancelledBy")
    @Mapping(target = "cancelledAt", source = "cancelledAt")
    @Mapping(target = "occurrences", source = "occurrences")
    RefundQuoteRequest toRefundQuoteRequest(Booking booking, List<BookingOccurrence> occurrences, CancelledBy cancelledBy,
                                            RefundReason reason, Instant cancelledAt, Instant firstUpcomingStartAt);

    // Chỉ buổi hủy được (chưa check-in) mới được hỏi giá → không bao giờ có session.
    @Mapping(target = "occurrenceId", source = "id")
    @Mapping(target = "hasSession", constant = "false")
    RefundQuoteRequest.Occurrence toRefundQuoteOccurrence(BookingOccurrence occurrence);

    // BK-03: kết quả Payment + giờ bắt đầu từng buổi (Payment không trả) + token xác nhận.
    @Mapping(target = "items", source = "quote.items")
    @Mapping(target = "totalRefund", source = "quote.totalRefund")
    @Mapping(target = "totalFee", source = "quote.totalFee")
    CancellationPreviewResponse toCancellationPreviewResponse(RefundQuoteResponse quote, String previewToken,
                                                              Instant expiresAt, @Context Map<UUID, Instant> startTimes);

    @Mapping(target = "startAt", expression = "java(startTimes.get(item.getOccurrenceId()))")
    CancellationPreviewResponse.Item toCancellationPreviewItem(RefundQuoteResponse.Item item,
                                                               @Context Map<UUID, Instant> startTimes);

    // Booking chưa đặt cọc: không có tiền để hoàn — preview trả 0, không gọi PaymentService.
    @Mapping(target = "items", source = "occurrences")
    @Mapping(target = "totalRefund", constant = "0")
    @Mapping(target = "totalFee", constant = "0")
    CancellationPreviewResponse toUnpaidPreviewResponse(List<BookingOccurrence> occurrences, String previewToken,
                                                        Instant expiresAt);

    @Mapping(target = "occurrenceId", source = "id")
    @Mapping(target = "paidAmount", constant = "0")
    @Mapping(target = "feeAmount", constant = "0")
    @Mapping(target = "refundAmount", constant = "0")
    @Mapping(target = "rule", constant = "NOT_PAID")
    CancellationPreviewResponse.Item toUnpaidPreviewItem(BookingOccurrence occurrence);

    // C2.3: yêu cầu hoàn tiền gửi PaymentService qua outbox; caregivers = các buổi được hoàn (CR-4).
    RefundCreateRequest toRefundCreateRequest(UUID bookingId, List<UUID> occurrenceIds, RefundReason reason,
                                              CancelledBy cancelledBy, Instant cancelledAt, Instant firstUpcomingStartAt,
                                              UUID requestedBy, String idempotencyKey,
                                              List<BookingOccurrence> caregivers);

    @Mapping(target = "occurrenceId", source = "id")
    RefundCreateRequest.OccurrenceCaregiver toOccurrenceCaregiver(BookingOccurrence occurrence);

    // C2.5: PaymentService tính hóa đơn phần còn lại theo trạng thái cuối của từng buổi (OPS-04).
    RemainingInvoiceRequest toRemainingInvoiceRequest(UUID bookingId, List<BookingOccurrence> occurrences);

    @Mapping(target = "occurrenceId", source = "id")
    @Mapping(target = "finalStatus", source = "status")
    RemainingInvoiceRequest.Occurrence toRemainingInvoiceOccurrence(BookingOccurrence occurrence);

    // Spec §3: lịch đã bị giữ + khoảng bận của caregiver (không kèm booking / khách).
    @Mapping(target = "busySlots", source = "occurrences")
    CaregiverHeldScheduleResponse toHeldScheduleResponse(UUID caregiverId, List<UUID> heldScheduleIds,
                                                         List<BookingOccurrence> occurrences,
                                                         Integer travelBufferMinutes,
                                                         Map<UUID, Integer> scheduleBookingCounts);

    CaregiverHeldScheduleResponse.BusySlot toBusySlot(BookingOccurrence occurrence);

    // Phục vụ GET /api/v1/bookings/caregiver-access (CareRecipientService gọi bằng token SERVICE).
    CaregiverAccessResponse toCaregiverAccessResponse(UUID caregiverUserId, UUID recipientId, boolean allowed);

    // Địa chỉ chăm sóc = địa chỉ trong snapshot lúc đặt (bỏ phần trống).
    @Named("toServiceAddress")
    default String toServiceAddress(RecipientSnapshot recipientSnapshot) {
        if (recipientSnapshot == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : Arrays.asList(recipientSnapshot.getAddressLine(), recipientSnapshot.getWard(),
                recipientSnapshot.getDistrict(), recipientSnapshot.getProvince())) {
            if (part != null && !part.isBlank()) {
                parts.add(part.strip());
            }
        }
        return String.join(ADDRESS_SEPARATOR, parts);
    }

    // CR-2: snapshot mới (object khác để Hibernate thấy jsonb đổi) — chỉ phần địa chỉ lấy từ request; tọa độ cũ không còn
    // đúng nên bỏ.
    @Mapping(target = "addressLine", source = "request.addressLine")
    @Mapping(target = "ward", source = "request.ward")
    @Mapping(target = "district", source = "request.district")
    @Mapping(target = "province", source = "request.province")
    @Mapping(target = "latitude", ignore = true)
    @Mapping(target = "longitude", ignore = true)
    RecipientSnapshot toAddressSnapshot(RecipientSnapshot snapshot, ServiceAddressRequest request);

    // Thứ trong tuần: request/response dùng List<Integer> (1..7), DB lưu smallint[] tăng dần không trùng; rỗng → NULL.
    default Short[] toDayNumbers(List<Integer> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return null;
        }
        return daysOfWeek.stream()
                .filter(Objects::nonNull)
                .sorted()
                .distinct()
                .map(Integer::shortValue)
                .toArray(Short[]::new);
    }

    default List<Integer> toDayList(Short[] daysOfWeek) {
        if (daysOfWeek == null) {
            return null;
        }
        return Arrays.stream(daysOfWeek)
                .map(Short::intValue)
                .collect(Collectors.toList());
    }
}
