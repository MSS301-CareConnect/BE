package mss301.providerservice.mapper;

import mss301.providerservice.client.dto.ServiceTokenRequest;
import mss301.providerservice.config.ProviderProperties;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ServiceTokenMapper {

    // Danh tính máy của ProviderService (PROVIDER_SERVICE_CLIENT_ID / SECRET) → request xin token SERVICE ở Identity.
    ServiceTokenRequest toServiceTokenRequest(ProviderProperties.ServiceClient serviceClient);
}
