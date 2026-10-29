package mss301.paymentservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.paymentservice.enums.TransactionEventType;
import mss301.paymentservice.mapper.PaymentTransactionMapper;
import mss301.paymentservice.repository.PaymentTransactionRepository;
import mss301.paymentservice.service.PaymentTransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentTransactionServiceImpl implements PaymentTransactionService {
    static int MAX_DETAIL_LENGTH = 1000;

    PaymentTransactionRepository paymentTransactionRepository;
    PaymentTransactionMapper paymentTransactionMapper;

    // MANDATORY: dòng nhật ký commit / rollback cùng biến động tiền nó mô tả — không bao giờ có nhật ký "mồ côi".
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID bookingId, UUID paymentId, UUID refundId, UUID invoiceId, TransactionEventType eventType,
                       BigDecimal amount, String idempotencyKey, String detail, Boolean signatureValid) {
        String safeDetail = detail != null && detail.length() > MAX_DETAIL_LENGTH
                ? detail.substring(0, MAX_DETAIL_LENGTH)
                : detail;
        paymentTransactionRepository.save(paymentTransactionMapper.toTransaction(bookingId, paymentId, refundId,
                invoiceId, eventType, amount, idempotencyKey, safeDetail, signatureValid));
    }
}
