package mss301.providerservice.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import mss301.providerservice.client.dto.DownloadUrlClientResponse;
import mss301.providerservice.client.dto.IdentityUserResponse;
import mss301.providerservice.client.dto.MediaClientResponse;
import mss301.providerservice.enums.MediaFileType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityUserResponseContractTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        // Matches ProviderService application.yaml: spring.jackson.deserialization.fail-on-unknown-properties: true
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    @Test
    @DisplayName("IdentityUserResponse deserializes cleanly when upstream IdentityService UserResponse has extra fields")
    void identityUserResponse_deserializesCleanlyWithUnknownProperties() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID centerId = UUID.randomUUID();

        // JSON payload containing fields returned by IdentityService UserResponse:
        // centerId, permissions, createdAt, and an extra forward-compatibility field.
        String json = """
                {
                    "id": "%s",
                    "email": "caregiver@careconnect.vn",
                    "fullName": "Nguyen Van A",
                    "phoneNumber": "0901234567",
                    "status": "ACTIVE",
                    "roles": ["CAREGIVER"],
                    "centerId": "%s",
                    "permissions": ["READ_PROFILE", "UPDATE_SCHEDULE"],
                    "createdAt": "2026-10-18T10:15:30Z",
                    "unexpectedNewFeatureField": "future_data"
                }
                """.formatted(userId, centerId);

        IdentityUserResponse response = objectMapper.readValue(json, IdentityUserResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getEmail()).isEqualTo("caregiver@careconnect.vn");
        assertThat(response.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(response.getPhoneNumber()).isEqualTo("0901234567");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getRoles()).containsExactly("CAREGIVER");
    }

    @Test
    @DisplayName("MediaClientResponse deserializes cleanly when upstream MediaService MediaResponse has extra fields")
    void mediaClientResponse_deserializesCleanlyWithUnknownProperties() throws Exception {
        UUID mediaId = UUID.randomUUID();
        UUID uploadedBy = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();

        // JSON payload containing fields returned by MediaService MediaResponse:
        // mediaPurpose, mediaStatus, uploadedBy, sourceService, referenceType, referenceId, createdAt
        String json = """
                {
                    "mediaFileId": "%s",
                    "originalFileName": "nurse_license.pdf",
                    "contentType": "application/pdf",
                    "fileType": "DOCUMENT",
                    "fileSize": 2048576,
                    "mediaPurpose": "CAREGIVER_CERTIFICATE",
                    "mediaStatus": "ACTIVE",
                    "uploadedBy": "%s",
                    "sourceService": "ProviderService",
                    "referenceType": "CERTIFICATE",
                    "referenceId": "%s",
                    "createdAt": "2026-10-18T12:00:00Z"
                }
                """.formatted(mediaId, uploadedBy, referenceId);

        MediaClientResponse response = objectMapper.readValue(json, MediaClientResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getMediaFileId()).isEqualTo(mediaId);
        assertThat(response.getOriginalFileName()).isEqualTo("nurse_license.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getFileType()).isEqualTo(MediaFileType.DOCUMENT);
        assertThat(response.getFileSize()).isEqualTo(2048576L);
    }

    @Test
    @DisplayName("DownloadUrlClientResponse deserializes cleanly with unexpected extra properties")
    void downloadUrlClientResponse_deserializesCleanlyWithUnknownProperties() throws Exception {
        UUID mediaId = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-10-18T14:30:00Z");

        String json = """
                {
                    "mediaFileId": "%s",
                    "downloadUrl": "https://storage.careconnect.vn/certs/%s.pdf",
                    "expiresAt": "%s",
                    "cdnHost": "cdn.careconnect.vn",
                    "securityToken": "sec_token_999"
                }
                """.formatted(mediaId, mediaId, expiresAt);

        DownloadUrlClientResponse response = objectMapper.readValue(json, DownloadUrlClientResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.getMediaFileId()).isEqualTo(mediaId);
        assertThat(response.getDownloadUrl()).isEqualTo("https://storage.careconnect.vn/certs/" + mediaId + ".pdf");
        assertThat(response.getExpiresAt()).isEqualTo(expiresAt);
    }

    public static class StrictTestDto {
        public String name;
    }

    @Test
    @DisplayName("Sanity check: Unannotated DTO fails under FAIL_ON_UNKNOWN_PROPERTIES=true")
    void sanityCheck_unannotatedDtoFailsOnUnknownProperties() {
        String json = """
                {
                    "name": "CareConnect",
                    "unknownField": "boom"
                }
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, StrictTestDto.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
