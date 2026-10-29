package mss301.paymentservice.mapper;

import mss301.paymentservice.dto.response.PaymentPolicyResponse;
import mss301.paymentservice.dto.response.PolicySnapshot;
import mss301.paymentservice.entity.PlatformPolicy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface PaymentPolicyMapper {

    @Mapping(target = "policyId", source = "id")
    PaymentPolicyResponse toResponse(PlatformPolicy policy);

    @Mapping(target = "policyId", source = "id")
    PolicySnapshot toSnapshot(PlatformPolicy policy);
}
