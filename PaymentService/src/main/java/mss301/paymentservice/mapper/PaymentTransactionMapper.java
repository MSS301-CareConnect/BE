package mss301.paymentservice.mapper;

import mss301.paymentservice.entity.PaymentTransaction;
import mss301.paymentservice.enums.TransactionEventType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.UUID;

@Mapper(config = MapStructConfig.class)
public interface PaymentTransactionMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    PaymentTransaction toTransaction(UUID bookingId, UUID paymentId, UUID refundId, UUID invoiceId,
                                     TransactionEventType eventType, BigDecimal amount, String idempotencyKey,
                                     String detail, Boolean signatureValid);
}
