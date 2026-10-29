package mss301.paymentservice.mapper;

import mss301.paymentservice.dto.response.OutstandingDebtResponse;
import mss301.paymentservice.dto.response.RemainingInvoiceResponse;
import mss301.paymentservice.dto.response.RemainingInvoiceResultResponse;
import mss301.paymentservice.entity.BookingFinancial;
import mss301.paymentservice.entity.RemainingInvoice;
import mss301.paymentservice.enums.InvoiceStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface RemainingInvoiceMapper {

    String NOT_REQUIRED = "NOT_REQUIRED";

    // financial cũng có status → chỉ rõ trạng thái hóa đơn.
    @IgnoreServerManagedFields
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", source = "status")
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "waivedBy", ignore = true)
    @Mapping(target = "waiveReason", ignore = true)
    RemainingInvoice toInvoice(BookingFinancial financial, BigDecimal amount, Instant dueAt, InvoiceStatus status);

    RemainingInvoiceResponse toResponse(RemainingInvoice invoice);

    List<RemainingInvoiceResponse> toResponseList(List<RemainingInvoice> invoices);

    @Mapping(target = "invoiceId", source = "id")
    RemainingInvoiceResultResponse toResultResponse(RemainingInvoice invoice);

    // Không phải thu thêm (đã đủ bằng cọc, hoặc booking chưa từng cọc).
    @Mapping(target = "invoiceId", ignore = true)
    @Mapping(target = "amount", constant = "0")
    @Mapping(target = "status", constant = NOT_REQUIRED)
    @Mapping(target = "dueAt", ignore = true)
    RemainingInvoiceResultResponse toNotRequiredResponse(UUID bookingId);

    OutstandingDebtResponse toOutstandingResponse(boolean hasDebt, List<RemainingInvoice> overdueInvoices,
                                                  List<RemainingInvoice> pendingInvoices);
}
