package mss301.aiservice.service.impl;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ReaderException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.aiservice.dto.request.CertificateVerificationRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.CertificateVerificationResponse;
import mss301.aiservice.exception.AppException;
import mss301.aiservice.exception.ErrorCode;
import mss301.aiservice.service.CertificateVerificationService;
import org.springframework.stereotype.Service;

import mss301.aiservice.config.AIProperties;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CertificateVerificationServiceImpl implements CertificateVerificationService {
    static ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    RestClient groqRestClient;
    AIProperties aiProperties;
    JsonMapper jsonMapper;

    @Override
    public APIResponse<CertificateVerificationResponse> verify(CertificateVerificationRequest request) {

        String extractedText = extractTextWithQwen(request.getImageUrls());
        String qrContent = readQrCodeFromImages(request.getImageUrls());

        boolean caregiverNameMatched = containsText(extractedText, request.getCaregiverFullName());

        boolean certificateNameMatched = containsText(extractedText, request.getCertificateName());
        boolean issuedByMatched = containsText(extractedText, request.getIssuedBy());
        boolean credentialNumberMatched = containsOptionalText(extractedText, request.getCredentialNumber());
        boolean issuedDateMatched = containsDate(extractedText, request.getIssuedDate());
        boolean expiryDateMatched = containsDate(extractedText, request.getExpiryDate());

        boolean expired = request.getExpiryDate() != null
                        && request.getExpiryDate().isBefore(LocalDate.now(VIETNAM_ZONE));

        List<String> warnings = createWarnings(
                extractedText,
                qrContent,
                caregiverNameMatched,
                certificateNameMatched,
                issuedByMatched,
                credentialNumberMatched,
                issuedDateMatched,
                expiryDateMatched,
                expired
        );

        String riskLevel = calculateRiskLevel(
                extractedText,
                caregiverNameMatched,
                certificateNameMatched,
                issuedByMatched,
                credentialNumberMatched,
                issuedDateMatched,
                expiryDateMatched,
                expired
        );

        CertificateVerificationResponse response = CertificateVerificationResponse.builder()
                        .certificateId(request.getCertificateId())
                        .extractedText(extractedText)
                        .qrContent(qrContent)
                        .caregiverNameMatched(caregiverNameMatched)
                        .certificateNameMatched(certificateNameMatched)
                        .issuedByMatched(issuedByMatched)
                        .credentialNumberMatched(credentialNumberMatched)
                        .issuedDateMatched(issuedDateMatched)
                        .expiryDateMatched(expiryDateMatched)
                        .expired(expired)
                        .riskLevel(riskLevel)
                        .warnings(warnings)
                        .checkedAt(Instant.now())
                        .build();

        return APIResponse.success("Kiểm tra chứng chỉ hoàn tất", response);
    }

    private String extractTextWithQwen(List<String> imageUrls) {

        try {
            List<Map<String, Object>> messageContent = new ArrayList<>();

            messageContent.add(
                    Map.of(
                            "type", "text",
                            "text", buildCertificateOcrPrompt()
                    )
            );

            for (String imageUrl : imageUrls) {
                messageContent.add(
                        Map.of(
                                "type", "image_url",
                                "image_url",
                                Map.of("url", imageUrl)
                        )
                );
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", aiProperties.getGroq().getModel());
            requestBody.put("messages", List.of(Map.of("role", "user", "content", messageContent)));
            requestBody.put("reasoning_effort", "none");
            requestBody.put("temperature", 0);
            requestBody.put("max_completion_tokens", 800);

            requestBody.put("response_format", Map.of("type", "json_object"));
            JsonNode groqResponse = groqRestClient.post()
                            .uri("/chat/completions")
                            .body(requestBody)
                            .retrieve()
                            .body(JsonNode.class);

            String qwenContent = getQwenContent(groqResponse);
            String cleanJson = removeMarkdownCodeBlock(qwenContent);

            JsonNode result = jsonMapper.readTree(cleanJson);

            if (!result.has("extractedText")) {
                throw new AppException(ErrorCode.VISION_SERVICE_UNAVAILABLE);
            }

            return result.path("extractedText")
                    .asText()
                    .trim();

        } catch (AppException exception) {
            throw exception;

        } catch (Exception exception) {
            log.error("Qwen certificate OCR failed", exception);
            throw new AppException(ErrorCode.VISION_SERVICE_UNAVAILABLE);
        }
    }

    private String getQwenContent(JsonNode groqResponse) {

        if (groqResponse == null) {
            throw new AppException(ErrorCode.VISION_SERVICE_UNAVAILABLE);
        }

        String content = groqResponse
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText();

        if (content == null || content.isBlank()) {
            throw new AppException(ErrorCode.VISION_SERVICE_UNAVAILABLE);
        }

        return content;
    }

    private String removeMarkdownCodeBlock(String content) {

        String result = content.trim();

        if (!result.startsWith("```")) {
            return result;
        }

        int firstLineEnd = result.indexOf('\n');
        int lastCodeBlock = result.lastIndexOf("```");

        if (firstLineEnd < 0 || lastCodeBlock <= firstLineEnd) {
            return result;
        }

        return result.substring(firstLineEnd + 1, lastCodeBlock).trim();
    }

    private String buildCertificateOcrPrompt() {
        return """
                Bạn là hệ thống OCR đọc chứng chỉ
                của người chăm sóc.

                Hãy đọc toàn bộ chữ xuất hiện trong
                tất cả ảnh được gửi kèm.

                Yêu cầu:
                - Giữ nguyên tiếng Việt và dấu tiếng Việt.
                - Giữ nguyên họ tên.
                - Giữ nguyên tên chứng chỉ.
                - Giữ nguyên đơn vị cấp.
                - Giữ nguyên mã chứng chỉ.
                - Giữ nguyên ngày cấp và ngày hết hạn.
                - Không tóm tắt.
                - Không tự đoán chữ không nhìn thấy.
                - Nếu có nhiều ảnh, nối nội dung theo
                  đúng thứ tự ảnh.
                - Chỉ trả JSON.
                - Không giải thích.
                - Không sử dụng markdown.

                Định dạng bắt buộc:
                {
                  "extractedText":
                    "toàn bộ nội dung đọc được"
                }
                """;
    }

    private String readQrCodeFromImages(List<String> imageUrls) {

        for (String imageUrl : imageUrls) {
            byte[] imageBytes = download(imageUrl);

            String qrContent = readQrCode(imageBytes);

            if (qrContent != null && !qrContent.isBlank()) {
                return qrContent;
            }
        }

        return null;
    }

    private byte[] download(String downloadUrl) {
        try {
            URLConnection connection = URI.create(downloadUrl).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(15_000);

            try (InputStream inputStream = connection.getInputStream()) {
                return inputStream.readAllBytes();
            }

        } catch (Exception exception) {
            log.error("Cannot download certificate image", exception);
            throw new AppException(ErrorCode.CERTIFICATE_IMAGE_INVALID);
        }
    }

    private String readQrCode(byte[] imageBytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            if (image == null) {
                return null;
            }

            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
            return new QRCodeReader()
                    .decode(bitmap)
                    .getText();

        } catch (ReaderException exception) {
            return null;

        } catch (Exception exception) {
            throw new AppException(ErrorCode.CERTIFICATE_IMAGE_INVALID);
        }
    }

    private boolean containsText(String extractedText, String expectedValue) {
        if (expectedValue == null || expectedValue.isBlank()) {
            return false;
        }

        return normalize(extractedText).contains(normalize(expectedValue));
    }

    private boolean containsOptionalText(String extractedText, String expectedValue) {

        if (expectedValue == null || expectedValue.isBlank()) {
            return true;
        }

        return containsText(extractedText, expectedValue);
    }

    private boolean containsDate(
            String extractedText,
            LocalDate expectedDate) {

        if (expectedDate == null) {
            return true;
        }

        List<DateTimeFormatter> formatters =
                List.of(
                        DateTimeFormatter.ofPattern(
                                "dd/MM/yyyy"
                        ),
                        DateTimeFormatter.ofPattern(
                                "dd-MM-yyyy"
                        ),
                        DateTimeFormatter.ofPattern(
                                "dd.MM.yyyy"
                        ),
                        DateTimeFormatter.ISO_LOCAL_DATE
                );

        return formatters.stream()
                .map(expectedDate::format)
                .anyMatch(extractedText::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }

    private List<String> createWarnings(
            String text,
            String qrContent,
            boolean caregiverNameMatched,
            boolean certificateNameMatched,
            boolean issuedByMatched,
            boolean credentialNumberMatched,
            boolean issuedDateMatched,
            boolean expiryDateMatched,
            boolean expired) {

        List<String> warnings = new ArrayList<>();

        if (text.isBlank()) {
            warnings.add("Không đọc được nội dung trong ảnh");
        }

        if (!caregiverNameMatched) {
            warnings.add("Tên caregiver không trùng khớp");
        }

        if (!certificateNameMatched) {
            warnings.add("Tên chứng chỉ không trùng khớp");
        }

        if (!issuedByMatched) {
            warnings.add("Đơn vị cấp chứng chỉ không trùng khớp");
        }

        if (!credentialNumberMatched) {
            warnings.add("Mã chứng chỉ không trùng khớp");
        }

        if (!issuedDateMatched) {
            warnings.add("Ngày cấp không trùng khớp");
        }

        if (!expiryDateMatched) {
            warnings.add("Ngày hết hạn không trùng khớp");
        }

        if (expired) {
            warnings.add("Chứng chỉ đã hết hạn");
        }

        if (qrContent == null) {
            warnings.add("Không tìm thấy hoặc không đọc được mã QR");
        }

        return warnings;
    }

    private String calculateRiskLevel(String text, boolean caregiverNameMatched, boolean certificateNameMatched,
                                      boolean issuedByMatched, boolean credentialNumberMatched, boolean issuedDateMatched,
                                      boolean expiryDateMatched, boolean expired) {

        if (text.isBlank()) {
            return "UNDETERMINED";
        }

        if (!caregiverNameMatched || !credentialNumberMatched || expired) {
            return "HIGH";
        }

        if (!certificateNameMatched || !issuedByMatched || !issuedDateMatched || !expiryDateMatched) {
            return "MEDIUM";
        }

        return "LOW";
    }
}
