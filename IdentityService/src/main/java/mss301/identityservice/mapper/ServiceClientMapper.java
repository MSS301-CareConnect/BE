package mss301.identityservice.mapper;

import mss301.identityservice.entity.ServiceClient;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ServiceClientMapper {

    // secretHash là BCrypt do người vận hành cấp qua env; active do ServiceClientInitializer quyết định.
    @IgnoreServerManagedFields
    ServiceClient toServiceClient(String clientId, String secretHash, String description, boolean active);
}
