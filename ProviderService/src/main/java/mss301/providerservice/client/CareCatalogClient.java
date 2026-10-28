package mss301.providerservice.client;

import mss301.providerservice.client.dto.CenterPriceSummaryClientResponse;
import mss301.providerservice.client.dto.CenterServiceClientResponse;
import mss301.providerservice.client.dto.RemoteApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// Gọi CareCatalogService qua ApiGateway bằng token SERVICE (ServiceTokenInterceptor).
@FeignClient(name = "care-catalog", url = "${provider.care-catalog.url}",
        configuration = CareCatalogClientConfig.class, fallbackFactory = CareCatalogClientFallbackFactory.class)
public interface CareCatalogClient {

    // Token SERVICE: Catalog trả 404 (3200) khi offering không bán được (ngừng / item ngừng / không có gói active).
    @GetMapping("/api/v1/center-services/{id}")
    RemoteApiResponse<CenterServiceClientResponse> getCenterService(@PathVariable("id") UUID id);

    // C9.2 (token SERVICE): giá thấp nhất mỗi trung tâm còn bán dịch vụ thỏa bộ lọc; tham số null bị bỏ khỏi query.
    @GetMapping("/api/v1/center-services/price-summary")
    RemoteApiResponse<List<CenterPriceSummaryClientResponse>> getPriceSummary(
            @RequestParam(value = "careServiceId", required = false) UUID careServiceId,
            @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
            @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice);
}
