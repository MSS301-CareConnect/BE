package mss301.mediaservice.service;

import mss301.mediaservice.config.MediaProperties;
import mss301.mediaservice.dto.response.APIResponse;
import mss301.mediaservice.dto.response.MediaResponse;
import mss301.mediaservice.entity.MediaFile;
import mss301.mediaservice.enums.MediaPurpose;
import mss301.mediaservice.enums.MediaStatus;
import mss301.mediaservice.exception.AppException;
import mss301.mediaservice.exception.ErrorCode;
import mss301.mediaservice.mapper.MediaMapper;
import mss301.mediaservice.repository.MediaFileRepository;
import mss301.mediaservice.service.Impl.MediaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTests {

    @Mock
    MediaFileRepository mediaFileRepository;

    @Mock
    MediaMapper mediaMapper;

    @Mock
    R2StorageService r2StorageService;

    @Mock
    TransactionTemplate transactionTemplate;

    @Mock
    TransactionStatus transactionStatus;

    MediaService mediaService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.getR2().setBucket("careconnect-media-test");
        mediaProperties.getR2().setDownloadUrlTtlSeconds(300);

        lenient().when(transactionTemplate.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(transactionStatus);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        mediaService = new MediaServiceImpl(
                mediaFileRepository,
                mediaMapper,
                r2StorageService,
                mediaProperties,
                transactionTemplate);
    }

    @Test
    void uploadStoresObjectAndMetadata() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", png);
        UUID uploaderId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();

        when(r2StorageService.upload(any(), any(), anyLong(), any())).thenReturn("etag-test");
        when(mediaFileRepository.save(any(MediaFile.class))).thenAnswer(invocation -> {
            MediaFile mediaFile = invocation.getArgument(0);
            mediaFile.setId(mediaId);
            mediaFile.setCreatedAt(Instant.now());
            return mediaFile;
        });
        when(mediaMapper.toResponse(any(MediaFile.class))).thenAnswer(invocation -> {
            MediaFile mediaFile = invocation.getArgument(0);
            return MediaResponse.builder()
                    .mediaFileId(mediaFile.getId())
                    .mediaPurpose(mediaFile.getMediaPurpose())
                    .mediaStatus(mediaFile.getMediaStatus())
                    .build();
        });

        APIResponse<MediaResponse> result = mediaService.upload(
                file,
                MediaPurpose.CAREGIVER_AVATAR,
                uploaderId,
                "PROVIDER_SERVICE",
                "CAREGIVER_PROFILE",
                UUID.randomUUID());

        assertThat(result.getResult().getMediaFileId()).isEqualTo(mediaId);
        assertThat(result.getResult().getMediaStatus()).isEqualTo(MediaStatus.ACTIVE);
        verify(r2StorageService).upload(any(), any(), anyLong(), any());
        verify(mediaFileRepository).save(any(MediaFile.class));
    }

    @Test
    void uploadRejectsContentThatDoesNotMatchDeclaredType() {
        MockMultipartFile fakePng = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());

        assertThatThrownBy(() -> mediaService.upload(
                fakePng,
                MediaPurpose.CAREGIVER_AVATAR,
                UUID.randomUUID(),
                "PROVIDER_SERVICE",
                null,
                null))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.FILE_CONTENT_MISMATCH);

        verify(r2StorageService, never()).upload(any(), any(), anyLong(), any());
    }

    @Test
    void deleteMarksMetadataAndDeletesR2Object() {
        UUID mediaId = UUID.randomUUID();
        MediaFile mediaFile = MediaFile.builder()
                .objectKey("caregiver-avatar/2026/10/file.png")
                .mediaStatus(MediaStatus.ACTIVE)
                .build();
        mediaFile.setId(mediaId);

        when(mediaFileRepository.findById(mediaId)).thenReturn(Optional.of(mediaFile));
        when(mediaFileRepository.save(mediaFile)).thenReturn(mediaFile);

        mediaService.delete(mediaId);

        assertThat(mediaFile.getMediaStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(mediaFile.getDeletedAt()).isNotNull();
        verify(r2StorageService).delete(mediaFile.getObjectKey());
    }
}
