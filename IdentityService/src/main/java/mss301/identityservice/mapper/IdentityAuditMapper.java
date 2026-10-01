package mss301.identityservice.mapper;

import mss301.identityservice.entity.IdentityAudit;
import mss301.identityservice.enums.AuditAction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IdentityAuditMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    IdentityAudit toAudit(UUID actorId, UUID targetId, UUID centerId, AuditAction action, String detail);
}
