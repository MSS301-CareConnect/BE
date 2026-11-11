package mss301.carecatalogservice;

import mss301.carecatalogservice.client.MediaClient;
import mss301.carecatalogservice.client.dto.DownloadUrlClientResponse;
import mss301.carecatalogservice.client.dto.MediaClientResponse;
import mss301.carecatalogservice.client.dto.RemoteApiResponse;
import mss301.carecatalogservice.exception.AppException;
import mss301.carecatalogservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogImageApiTests {

    static byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:catalog-image;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("care-catalog.jwt.issuer", () -> "https://identity.test");
        registry.add("care-catalog.jwt.jwks-uri", () -> "http://127.0.0.1:1/.well-known/jwks.json");
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MediaClient mediaClient;

    @Test
    void adminUploadsImageAndGetsStableCatalogUrl() throws Exception {
        UUID mediaFileId = UUID.randomUUID();
        MediaClientResponse media = MediaClientResponse.builder()
                .mediaFileId(mediaFileId).contentType("image/png").fileSize((long) PNG_BYTES.length).build();
        when(mediaClient.upload(any(), eq("CATALOG_ITEM_IMAGE"), any(), eq("CATALOG_ITEM")))
                .thenReturn(RemoteApiResponse.<MediaClientResponse>builder().code(200).result(media).build());

        mockMvc.perform(multipart("/api/v1/catalog-items/images").file(pngFile()).with(user("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.mediaFileId").value(mediaFileId.toString()))
                .andExpect(jsonPath("$.result.imageUrl").value("/api/v1/catalog-items/images/" + mediaFileId));
    }

    @Test
    void onlyAdminCanUploadImages() throws Exception {
        mockMvc.perform(multipart("/api/v1/catalog-items/images").file(pngFile()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/catalog-items/images").file(pngFile()).with(user("CENTER_MANAGER")))
                .andExpect(status().isForbidden());
        verify(mediaClient, never()).upload(any(), any(), any(), any());
    }

    @Test
    void uploadRejectsMissingWrongTypeAndOversizedFiles() throws Exception {
        mockMvc.perform(multipart("/api/v1/catalog-items/images").with(user("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CATALOG_IMAGE_REQUIRED.getCode()));
        MockMultipartFile pdf = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/v1/catalog-items/images").file(pdf).with(user("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CATALOG_IMAGE_INVALID.getCode()));
        MockMultipartFile large = new MockMultipartFile("file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        mockMvc.perform(multipart("/api/v1/catalog-items/images").file(large).with(user("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.CATALOG_IMAGE_TOO_LARGE.getCode()));
        verify(mediaClient, never()).upload(any(), any(), any(), any());
    }

    @Test
    void imageRedirectIsPublicAndCachedShorterThanTheDownloadUrl() throws Exception {
        UUID mediaFileId = UUID.randomUUID();
        DownloadUrlClientResponse download = DownloadUrlClientResponse.builder()
                .mediaFileId(mediaFileId)
                .downloadUrl("https://r2.example/catalog-item-image/a.png?sig=1")
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        when(mediaClient.createDownloadUrl(mediaFileId))
                .thenReturn(RemoteApiResponse.<DownloadUrlClientResponse>builder().code(200).result(download).build());

        mockMvc.perform(get("/api/v1/catalog-items/images/" + mediaFileId))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, download.getDownloadUrl()))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=5")));
    }

    @Test
    void unknownImageIsNotFound() throws Exception {
        UUID mediaFileId = UUID.randomUUID();
        when(mediaClient.createDownloadUrl(mediaFileId)).thenThrow(new AppException(ErrorCode.CATALOG_IMAGE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/catalog-items/images/" + mediaFileId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.CATALOG_IMAGE_NOT_FOUND.getCode()));
    }

    static MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "cover.png", "image/png", PNG_BYTES);
    }

    static JwtRequestPostProcessor user(String role) {
        return jwt()
                .jwt(token -> token.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
