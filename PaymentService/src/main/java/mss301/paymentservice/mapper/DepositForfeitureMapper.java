package mss301.paymentservice.mapper;

import mss301.paymentservice.dto.response.DepositForfeitureResponse;
import mss301.paymentservice.dto.response.DepositForfeitureSummaryResponse;
import mss301.paymentservice.entity.DepositForfeiture;
import mss301.paymentservice.entity.OccurrenceFinancial;
import mss301.paymentservice.enums.ForfeitureBeneficiary;
import mss301.paymentservice.enums.RefundReason;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface DepositForfeitureMapper {

    // Bên hưởng do service quyết định (theo caregiver của buổi) rồi truyền vào; caregiver lấy từ occurrence_financials.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "bookingId", source = "occurrenceFinancial.bookingId")
    @Mapping(target = "centerId", source = "occurrenceFinancial.centerId")
    @Mapping(target = "occurrenceId", source = "occurrenceFinancial.occurrenceId")
    @Mapping(target = "occurrenceStartAt", source = "occurrenceFinancial.startAt")
    @Mapping(target = "caregiverId", source = "occurrenceFinancial.caregiverId")
    @Mapping(target = "caregiverUserId", source = "occurrenceFinancial.caregiverUserId")
    DepositForfeiture toForfeiture(OccurrenceFinancial occurrenceFinancial, UUID paymentId, BigDecimal amount,
                                   ForfeitureBeneficiary beneficiary, RefundReason reason, String rule,
                                   String idempotencyKey);

    DepositForfeitureResponse toResponse(DepositForfeiture forfeiture);

    List<DepositForfeitureResponse> toResponseList(List<DepositForfeiture> forfeitures);

    DepositForfeitureSummaryResponse toSummaryResponse(BigDecimal centerAmount, BigDecimal caregiverAmount,
                                                       BigDecimal totalAmount);
}
