package mss301.mediaservice.service;

import mss301.mediaservice.config.MediaProperties;
import mss301.mediaservice.exception.AppException;
import mss301.mediaservice.exception.ErrorCode;
import mss301.mediaservice.service.Impl.R2StorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTests {

    @Mock
    S3Client s3Client;

    @Mock
    S3Presigner s3Presigner;

    @TempDir
    Path temporaryDirectory;

    R2StorageServiceImpl r2StorageService;

    @BeforeEach
    void setUp() {
        MediaProperties mediaProperties = new MediaProperties();
        mediaProperties.getR2().setBucket("test-bucket");
        r2StorageService = new R2StorageServiceImpl(s3Client, s3Presigner, mediaProperties);
    }

    @Test
    void smallFileCanBeReadAgainForSigningAndRetry() throws Exception {
        assertUploadCanBeReadAgain(69);
    }

    @Test
    void tenMegabyteFileCanBeReadAgainWithoutMarkLimit() throws Exception {
        assertUploadCanBeReadAgain(10 * 1024 * 1024);
    }

    @Test
    void storageFailureDeletesTemporaryUploadAndReturnsServiceError() throws Exception {
        Path source = temporaryDirectory.resolve("failed-upload.png");
        Files.write(source, new byte[]{1, 2, 3});
        AtomicReference<RequestBody> capturedBody = new AtomicReference<>();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            RequestBody requestBody = invocation.getArgument(1);
            capturedBody.set(requestBody);
            readDigest(requestBody);
            throw S3Exception.builder().statusCode(503).message("Storage unavailable").build();
        });

        try (InputStream inputStream = Files.newInputStream(source)) {
            assertThatThrownBy(() -> r2StorageService.upload("test.png", "image/png", 3, inputStream))
                    .isInstanceOf(AppException.class)
                    .extracting(exception -> ((AppException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.UPLOAD_FAILED);
        }

        assertThatThrownBy(() -> capturedBody.get().contentStreamProvider().newStream())
                .hasRootCauseInstanceOf(NoSuchFileException.class);
        assertThat(Files.exists(source)).isTrue();
    }

    @Test
    void unreadableSourceReturnsServiceErrorWithoutCallingStorage() {
        InputStream inputStream = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Source unavailable");
            }
        };

        assertThatThrownBy(() -> r2StorageService.upload("test.png", "image/png", 1, inputStream))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.UPLOAD_FAILED);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    private void assertUploadCanBeReadAgain(int size) throws Exception {
        byte[] content = new byte[size];
        new Random(42).nextBytes(content);
        byte[] expectedDigest = MessageDigest.getInstance("SHA-256").digest(content);
        Path source = temporaryDirectory.resolve("upload-" + size + ".png");
        Files.write(source, content);
        AtomicReference<RequestBody> capturedBody = new AtomicReference<>();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody requestBody = invocation.getArgument(1);
            capturedBody.set(requestBody);
            assertThat(request.bucket()).isEqualTo("test-bucket");
            assertThat(request.contentLength()).isEqualTo(size);
            assertThat(requestBody.optionalContentLength()).contains((long) size);
            assertThat(readDigest(requestBody)).isEqualTo(expectedDigest);
            assertThat(readDigest(requestBody)).isEqualTo(expectedDigest);
            return PutObjectResponse.builder().eTag("test-etag").build();
        });

        try (InputStream inputStream = Files.newInputStream(source)) {
            assertThat(inputStream.markSupported()).isFalse();
            String etag = r2StorageService.upload("test.png", "image/png", size, inputStream);
            assertThat(etag).isEqualTo("test-etag");
        }

        assertThatThrownBy(() -> capturedBody.get().contentStreamProvider().newStream())
                .hasRootCauseInstanceOf(NoSuchFileException.class);
        assertThat(Files.exists(source)).isTrue();
    }

    private byte[] readDigest(RequestBody requestBody) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = requestBody.contentStreamProvider().newStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }
}
