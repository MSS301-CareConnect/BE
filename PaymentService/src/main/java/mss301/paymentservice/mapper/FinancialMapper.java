package mss301.paymentservice.mapper;

import mss301.paymentservice.client.dto.BookingPaymentContextResponse;
import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.enums.BookingFinancialStatus;
import mss301.paymentservice.util.DepositAllocator;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.math.BigDecimal;
import java.util.UUID;

// booking_financials + occurrence_financials (tài chính của booking, tạo khi mở phiên cọc).
@Mapper(config = MapStructConfig.class)
public interface FinancialMapper {

    // context cũng có status / totalAmount / depositPaidAt (phía Booking) → chỉ rõ nguồn của Payment.
    @IgnoreServerManagedFields
    @Mapping(target = "policyId", source = "policySnapshot.policyId")
    @Mapping(target = "policySnapshot", source = "policySnapshot")
    @Mapping(target = "totalAmount", source = "totalAmount")
    @Mapping(target = "depositAmount", source = "depositAmount")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "depositPaymentId", ignore = true)
    @Mapping(target = "depositPaidAt", ignore = true)
    BookingFinancial toBookingFinancial(BookingPaymentContextResponse context, PolicySnapshot policySnapshot,
                                        BigDecimal totalAmount, BigDecimal depositAmount, BookingFinancialStatus status);

    // Mở lại phiên cọc khi giá / chính sách đổi trước lúc trả: chỉ cập nhật phần định giá.
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "policyId", source = "policySnapshot.policyId")
    @Mapping(target = "policySnapshot", source = "policySnapshot")
    @Mapping(target = "totalAmount", source = "totalAmount")
    @Mapping(target = "depositAmount", source = "depositAmount")
    void updatePricing(PolicySnapshot policySnapshot, BigDecimal totalAmount, BigDecimal depositAmount,
                       @MappingTarget BookingFinancial bookingFinancial);

    @IgnoreServerManagedFields
    @Mapping(target = "chargedAmount", constant = "0")
    @Mapping(target = "refundedAmount", constant = "0")
    @Mapping(target = "finalStatus", ignore = true)
    OccurrenceFinancial toOccurrenceFinancial(DepositAllocator.Allocation allocation, UUID bookingId, UUID centerId);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "startAt", source = "startAt")
    @Mapping(target = "priceAmount", source = "priceAmount")
    @Mapping(target = "depositAmount", source = "depositAmount")
    void updateAllocation(DepositAllocator.Allocation allocation, @MappingTarget OccurrenceFinancial occurrenceFinancial);
}
