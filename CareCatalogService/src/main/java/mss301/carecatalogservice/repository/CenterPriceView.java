package mss301.carecatalogservice.repository;

import java.math.BigDecimal;
import java.util.UUID;

// Projection một gói giá đang bán kèm trung tâm của nó (PricePlanRepository.findSellablePrices).
public interface CenterPriceView {
    UUID getCenterId();

    BigDecimal getBasePrice();
}
