package mss301.mediaservice;

import mss301.mediaservice.entity.MediaFile;
import mss301.mediaservice.enums.MediaStatus;
import mss301.mediaservice.enums.MediaPurpose;
import mss301.mediaservice.enums.FileType;
import mss301.mediaservice.repository.MediaFileRepository;
import mss301.mediaservice.service.R2StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockMultipartFile;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;

@SpringBootTest(properties = {"media.jwt.issuer=https://identity.test", "media.jwt.jwks-uri=https://identity.test/.well-known/jwks.json"})
@AutoConfigureMockMvc
class MediaApiSecurityTests {
    @Autowired
    MockMvc mockMvc;
    @Autowired
    MediaFileRepository mediaFileRepository;
    @MockitoBean
    R2StorageService r2StorageService;

    @Test
    void onlyOwningServiceCanReadOrDelete() throws Exception {
        MediaFile file = mediaFileRepository.save(MediaFile.builder().bucketName("test").objectKey(UUID.randomUUID().toString())
                .originalFileName("image.png").contentType("image/png").fileType(FileType.IMAGE).fileSize(8L)
                .mediaPurpose(MediaPurpose.CARE_REPORT_IMAGE).mediaStatus(MediaStatus.ACTIVE).sourceService("provider").build());
        mockMvc.perform(get("/api/v1/media/" + file.getId()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/media/" + file.getId()).with(service("booking"))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/media/" + file.getId()).with(service("booking"))).andExpect(status().isNotFound());
        verify(r2StorageService, never()).delete(any());
        mockMvc.perform(get("/api/v1/media/" + file.getId()).with(service("provider"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/media/" + file.getId() + "/download-url").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadOwnershipComesFromClientClaim() throws Exception {
        when(r2StorageService.upload(any(), any(), anyLong(), any())).thenReturn("etag");
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        mockMvc.perform(multipart("/api/v1/media").file(new MockMultipartFile("file", "image.png", "image/png", png))
                .param("purpose", "CARE_REPORT_IMAGE").param("sourceService", "booking").with(service("provider")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.sourceService").value("provider"));
    }

    private RequestPostProcessor service(String clientId) {
        return jwt().jwt(jwt -> jwt.subject(UUID.randomUUID().toString()).claim("client_id", clientId))
                .authorities(new SimpleGrantedAuthority("ROLE_SERVICE"));
    }
}
