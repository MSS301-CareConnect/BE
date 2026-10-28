package mss301.paymentservice.mapper;

import org.mapstruct.*;
import org.mapstruct.MapperConfig;


@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        builder = @Builder(disableBuilder = true))
public interface MapStructConfig {
}
