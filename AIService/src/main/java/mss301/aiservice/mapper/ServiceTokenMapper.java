package mss301.aiservice.mapper;

import mss301.aiservice.client.dto.ServiceTokenRequest;
import mss301.aiservice.config.AIProperties;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ServiceTokenMapper {
    ServiceTokenRequest toServiceTokenRequest(AIProperties.ServiceClient serviceClient);
}
