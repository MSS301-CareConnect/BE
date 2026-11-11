package mss301.aiservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.aiservice.client.GroqClient;
import mss301.aiservice.client.dto.GroqContentPart;
import mss301.aiservice.client.dto.GroqMessageRequest;
import mss301.aiservice.client.dto.GroqResult;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.config.CertificatePrompts;
import mss301.aiservice.dto.request.CertificateCheckRequest;
import mss301.aiservice.dto.request.CertificateImageRequest;
import mss301.aiservice.dto.response.APIResponse;
import mss301.aiservice.dto.response.CertificateCheckResponse;
import mss301.aiservice.dto.response.CertificateExtractedResponse;
import mss301.aiservice.dto.response.CertificateWarningResponse;
import mss301.aiservice.enums.CertificateRiskLevel;
import mss301.aiservice.enums.CertificateWarningCode;
import mss301.aiservice.mapper.CertificateCheckMapper;
import mss301.aiservice.service.CertificateCheckService;
import mss301.aiservice.service.CertificateRiskService;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CertificateCheckServiceImpl implements CertificateCheckService {
    static Set<String> READABLE_IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    static Pattern THINK_BLOCK = Pattern.compile("(?s)<think>.*?</think>");
    static Pattern JSON_OBJECT = Pattern.compile("(?s)\\{.*}");
    static DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ROOT);

    GroqClient groqClient;
    CertificateRiskService certificateRiskService;
    CertificateCheckMapper certificateCheckMapper;
    AIProperties aiProperties;
    ObjectMapper objectMapper;
    Clock clock;

    // Gửi ảnh chứng chỉ cho AI đọc, so với thông tin khai báo và trả mức rủi ro.
    @Override
    public APIResponse<CertificateCheckResponse> check(CertificateCheckRequest request) {
        List<CertificateImageRequest> images = request.getImages() == null ? List.of() : request.getImages();
        List<String> readableUrls = images.stream()
                .filter(image -> isReadableImage(image.getMimeType()))
                .map(CertificateImageRequest::getUrl)
                .limit(aiProperties.getCertificate().getMaxImages())
                .toList();

        if (readableUrls.isEmpty()) {
            CertificateWarningCode code = images.isEmpty()
                    ? CertificateWarningCode.NO_IMAGE
                    : CertificateWarningCode.UNSUPPORTED_FILE;
            return undetermined(code, null);
        }

        String model = visionModel();
        GroqResult groqResult = groqClient.generateAnswer(buildMessages(readableUrls), model,
                aiProperties.getCertificate().getTemperature());
        JsonNode reading = parseReading(groqResult.getAnswer());

        if (reading == null || !reading.path("readable").asBoolean(false)) {
            return undetermined(CertificateWarningCode.UNREADABLE, groqResult.getModel());
        }

        CertificateExtractedResponse extracted = certificateCheckMapper.toExtractedResponse(
                text(reading, "holderName"),
                text(reading, "credentialNumber"),
                text(reading, "certificateName"),
                text(reading, "issuedBy"),
                date(reading, "issuedDate"),
                date(reading, "expiryDate"));

        LocalDate today = LocalDate.now(clock.withZone(aiProperties.getCertificate().getTimezone()));
        List<CertificateWarningResponse> warnings = certificateRiskService.compare(request, extracted, today);
        CertificateRiskLevel riskLevel = certificateRiskService.toRiskLevel(warnings);

        CertificateCheckResponse response = certificateCheckMapper.toResponse(riskLevel, warnings, extracted,
                groqResult.getModel(), clock.instant());

        return APIResponse.success("Kiểm tra chứng chỉ thành công", response);
    }

    // Trả kết quả "không xác định" kèm cảnh báo khi không đọc được chứng chỉ.
    private APIResponse<CertificateCheckResponse> undetermined(CertificateWarningCode code, String model) {
        List<CertificateWarningResponse> warnings = new ArrayList<>();
        warnings.add(certificateCheckMapper.toWarningResponse(code, null, null, null));
        Instant checkedAt = clock.instant();

        CertificateCheckResponse response = certificateCheckMapper.toResponse(CertificateRiskLevel.UNDETERMINED,
                warnings, null, model, checkedAt);

        return APIResponse.success("Kiểm tra chứng chỉ thành công", response);
    }

    // Dựng message gửi AI gồm prompt và các ảnh chứng chỉ.
    private List<GroqMessageRequest> buildMessages(List<String> imageUrls) {
        List<GroqContentPart> contentParts = new ArrayList<>();
        contentParts.add(GroqContentPart.text(CertificatePrompts.USER_PROMPT));

        for (String imageUrl : imageUrls) {
            contentParts.add(GroqContentPart.image(imageUrl));
        }

        return List.of(
                GroqMessageRequest.text("system", CertificatePrompts.SYSTEM_PROMPT),
                GroqMessageRequest.builder().role("user").content(contentParts).build());
    }

    // Chọn model đọc ảnh; chưa cấu hình thì dùng model mặc định.
    private String visionModel() {
        String visionModel = aiProperties.getGroq().getVisionModel();
        return visionModel == null || visionModel.isBlank() ? aiProperties.getGroq().getModel() : visionModel;
    }

    // Mô hình có thể bọc JSON trong ```json``` hoặc kèm <think>…</think> — lấy khối {...} đầu tiên.
    private JsonNode parseReading(String answer) {
        if (answer == null) {
            return null;
        }

        String withoutThinking = THINK_BLOCK.matcher(answer).replaceAll("");
        Matcher matcher = JSON_OBJECT.matcher(withoutThinking);

        if (!matcher.find()) {
            log.warn("Certificate reading had no JSON object");
            return null;
        }

        try {
            JsonNode node = objectMapper.readTree(matcher.group());
            return node != null && node.isObject() ? node : null;
        } catch (JacksonException exception) {
            log.warn("Certificate reading was not valid JSON ({})", exception.getClass().getSimpleName());
            return null;
        }
    }

    // Đọc một trường chuỗi từ JSON AI trả về, rỗng thì null.
    private String text(JsonNode reading, String field) {
        JsonNode value = reading.get(field);

        if (value == null || value.isNull() || !value.isValueNode()) {
            return null;
        }

        String text = value.asString().trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    // YYYY-MM-DD như prompt yêu cầu; chấp nhận thêm dd/MM/yyyy nếu mô hình giữ nguyên cách ghi trên ảnh.
    private LocalDate date(JsonNode reading, String field) {
        String value = text(reading, field);

        if (value == null) {
            return null;
        }

        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(value.replace('-', '/').replace('.', '/'), DAY_MONTH_YEAR);
            } catch (DateTimeParseException alsoIgnored) {
                return null;
            }
        }
    }

    // Kiểm tra loại ảnh AI đọc được.
    private boolean isReadableImage(String mimeType) {
        return mimeType != null && READABLE_IMAGE_TYPES.contains(mimeType.toLowerCase(Locale.ROOT));
    }
}
