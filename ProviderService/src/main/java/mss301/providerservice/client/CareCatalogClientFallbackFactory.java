package mss301.providerservice.client;

import lombok.extern.slf4j.Slf4j;
import mss301.providerservice.client.dto.CenterPriceSummaryClientResponse;
import mss301.providerservice.client.dto.CenterServiceClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import mss301.providerservice.exception.AppException;
import mss301.providerservice.exception.ErrorCode;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class CareCatalogClientFallbackFactory implements FallbackFactory<CareCatalogClient> {

    @Override
    public CareCatalogClient create(Throwable cause) {
        return new CareCatalogClient() {
            @Override
            public RemoteApiResponse<CenterServiceClientResponse> getCenterService(UUID id) {
                throw resolveFailure(cause);
            }

            @Override
            public RemoteApiResponse<List<CenterPriceSummaryClientResponse>> getPriceSummary(UUID careServiceId,
                                                                                            BigDecimal minPrice,
                                                                                            BigDecimal maxPrice) {
                throw resolveFailure(cause);
            }
        };
    }

    // Lỗi đã dịch (404 → 4208, token → 4700) giữ nguyên; lỗi mạng / timeout / mạch mở → 503.
    private AppException resolveFailure(Throwable cause) {
        if (cause instanceof AppException appException) {
            return appException;
        }
        log.warn("CareCatalogService unavailable ({})", cause.getClass().getSimpleName());
        return new AppException(ErrorCode.CATALOG_UNAVAILABLE);
    }
}
