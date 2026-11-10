package mss301.bookingservice.mapper;

import org.mapstruct.Builder;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Cấu hình MapStruct dùng chung — MỌI mapper của service khai báo {@code @Mapper(config = MapStructConfig.class)}.
 * - ERROR khi còn thuộc tính đích chưa map: thêm field mới mà quên map/ignore là lỗi compile, không âm thầm null.
 * - Tắt builder Lombok: builder không set được field kế thừa từ BaseEntity → dùng constructor rỗng + setter.
 * - Inject mapper con ({@code uses}) qua constructor, không field {@code @Autowired}.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        builder = @Builder(disableBuilder = true))
public interface MapStructConfig {
}
