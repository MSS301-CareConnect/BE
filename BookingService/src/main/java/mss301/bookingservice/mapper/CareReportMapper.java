package mss301.bookingservice.mapper;

import mss301.bookingservice.dto.request.CareReportRequest;
import mss301.bookingservice.dto.response.CareReportResponse;
import mss301.bookingservice.dto.response.ReportPhotoResponse;
import mss301.bookingservice.entity.CareReport;
import mss301.bookingservice.entity.CareSession;
import mss301.bookingservice.entity.CareReportMedia;
import mss301.bookingservice.entity.CareReportMediaId;
import mss301.bookingservice.client.dto.DownloadUrlClientResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

@Mapper(config = MapStructConfig.class, imports = CareReportMediaId.class)
public interface CareReportMapper {
    @Mapping(target = "sessionId", source = "report.session.id")
    @Mapping(target = "photos", source = "photos")
    CareReportResponse toResponse(CareReport report, List<ReportPhotoResponse> photos);

    @IgnoreServerManagedFields
    @Mapping(target = "session", source = "session")
    @Mapping(target = "occurrenceId", source = "session.occurrence.id")
    @Mapping(target = "bookingId", source = "session.booking.id")
    @Mapping(target = "caregiverUserId", source = "session.caregiverUserId")
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    CareReport toCareReport(CareReportRequest request, CareSession session);

    @IgnoreServerManagedFields
    @Mapping(target = "session", ignore = true)
    @Mapping(target = "occurrenceId", ignore = true)
    @Mapping(target = "bookingId", ignore = true)
    @Mapping(target = "caregiverUserId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "submittedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void update(CareReportRequest request, @MappingTarget CareReport report);

    @Mapping(target = "id", expression = "java(new CareReportMediaId(reportId, mediaFileId))")
    CareReportMedia toCareReportMedia(UUID reportId, UUID mediaFileId, Integer displayOrder, Instant createdAt);

    @Mapping(target = "mediaFileId", source = "media.id.mediaFileId")
    ReportPhotoResponse toPhotoResponse(CareReportMedia media, DownloadUrlClientResponse download);
}
