package mss301.carerecipientservice.mapper;

import mss301.carerecipientservice.client.dto.ServiceTokenRequest;
import mss301.carerecipientservice.config.CareRecipientProperties;
import org.mapstruct.Mapper;

@Mapper(config = MapStructConfig.class)
public interface ServiceTokenMapper {

    // Danh tính máy của CareRecipientService (CARE_RECIPIENT_SERVICE_CLIENT_ID / SECRET) → request xin token SERVICE ở Identity.
    ServiceTokenRequest toServiceTokenRequest(CareRecipientProperties.ServiceClient serviceClient);
}
