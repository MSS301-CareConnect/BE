package mss301.paymentservice.mapper;

import mss301.paymentservice.dto.response.*;
import mss301.paymentservice.entity.*;
import org.mapstruct.*;

import java.math.BigDecimal;

@Mapper(config = MapStructConfig.class, imports = BigDecimal.class)
public interface PaymentMapper {
    @Mapping(target = "refundedAmount", expression = "java(BigDecimal.ZERO)")
    PaymentResponse toResponse(Payment payment);

    PaymentAttemptResponse toResponse(PaymentAttempt attempt);
}
