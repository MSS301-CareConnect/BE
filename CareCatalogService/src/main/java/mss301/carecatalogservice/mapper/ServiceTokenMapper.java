package mss301.carecatalogservice.mapper;

import mss301.carecatalogservice.client.dto.ServiceTokenRequest;
import mss301.carecatalogservice.config.CareCatalogProperties;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ServiceTokenMapper {

    ServiceTokenRequest toServiceTokenRequest(CareCatalogProperties.ServiceClient serviceClient);
}
