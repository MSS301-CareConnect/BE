package mss301.aiservice.service.impl;

import mss301.aiservice.client.GroqClient;
import mss301.aiservice.client.dto.GroqMessageRequest;
import mss301.aiservice.client.dto.GroqResult;
import mss301.aiservice.config.AIProperties;
import mss301.aiservice.dto.request.CertificateCheckRequest;
import mss301.aiservice.dto.request.CertificateImageRequest;
import mss301.aiservice.dto.response.CertificateCheckResponse;
import mss301.aiservice.dto.response.CertificateWarningResponse;
import mss301.aiservice.enums.CertificateCheckField;
import mss301.aiservice.enums.CertificateRiskLevel;
import mss301.aiservice.enums.CertificateWarningCode;
import mss301.aiservice.mapper.CertificateCheckMapper;
import mss301.aiservice.mapper.CertificateCheckMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateCheckServiceImplTests {

    // 2026-11-09 10:00 giờ Việt Nam.
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-09T03:00:00Z"), ZoneOffset.UTC);

    final CertificateCheckMapper mapper = new CertificateCheckMapperImpl();
    final CertificateRiskServiceImpl riskService = new CertificateRiskServiceImpl(mapper);
    final FakeGroqClient groqClient = new FakeGroqClient();
    CertificateCheckServiceImpl service;

    @BeforeEach
    void setUp() {
        AIProperties properties = new AIProperties();
        properties.getGroq().setModel("chat-model");
        properties.getGroq().setVisionModel("vision-model");
        service = new CertificateCheckServiceImpl(groqClient, riskService, mapper, properties, JsonMapper.builder().build(),
                CLOCK);
    }

    @Test
    void matchingCertificateIsLowRiskAndUsesVisionModel() {
        groqClient.answer = """
                <think>reading...</think>
                ```json
                {"readable": true, "holderName": "NGUYỄN VĂN AN", "credentialNumber": "Số 12/QĐ-BV",
                 "certificateName": "Chứng chỉ Điều dưỡng cơ bản", "issuedBy": "Bệnh viện Bạch Mai",
                 "issuedDate": "2025-01-15", "expiryDate": "2030-01-15"}
                ```""";

        CertificateCheckResponse response = service.check(request()).getResult();

        assertThat(response.getRiskLevel()).isEqualTo(CertificateRiskLevel.LOW);
        assertThat(response.getWarnings()).isEmpty();
        assertThat(response.getExtracted().getHolderName()).isEqualTo("NGUYỄN VĂN AN");
        assertThat(response.getExtracted().getExpiryDate()).isEqualTo(LocalDate.of(2030, 1, 15));
        assertThat(groqClient.model).isEqualTo("vision-model");
        assertThat(groqClient.temperature).isZero();
    }

    @Test
    void wrongHolderNameIsHighRisk() {
        groqClient.answer = """
                {"readable": true, "holderName": "Trần Thị Bình", "credentialNumber": "12/QĐ-BV",
                 "certificateName": "Điều dưỡng cơ bản", "issuedBy": "Bệnh viện Bạch Mai",
                 "issuedDate": "15/01/2025", "expiryDate": null}""";

        CertificateCheckResponse response = service.check(request()).getResult();

        assertThat(response.getRiskLevel()).isEqualTo(CertificateRiskLevel.HIGH);
        assertThat(response.getWarnings()).extracting(CertificateWarningResponse::getCode, CertificateWarningResponse::getField)
                .contains(org.assertj.core.groups.Tuple.tuple(CertificateWarningCode.MISMATCH, CertificateCheckField.HOLDER_NAME),
                        org.assertj.core.groups.Tuple.tuple(CertificateWarningCode.NOT_FOUND, CertificateCheckField.EXPIRY_DATE));
    }

    @Test
    void expiredCertificateIsHighRisk() {
        groqClient.answer = """
                {"readable": true, "holderName": "Nguyen Van An", "credentialNumber": "12/QD-BV",
                 "certificateName": "Chứng chỉ Điều dưỡng cơ bản", "issuedBy": "Bệnh viện Bạch Mai",
                 "issuedDate": "2025-01-15", "expiryDate": "2026-01-15"}""";

        CertificateCheckResponse response = service.check(request()).getResult();

        assertThat(response.getRiskLevel()).isEqualTo(CertificateRiskLevel.HIGH);
        assertThat(response.getWarnings()).extracting(CertificateWarningResponse::getCode)
                .contains(CertificateWarningCode.EXPIRED, CertificateWarningCode.MISMATCH);
    }

    @Test
    void differentIssuerOnlyIsMediumRisk() {
        groqClient.answer = """
                {"readable": true, "holderName": "An Nguyễn Văn", "credentialNumber": "12QDBV",
                 "certificateName": "Điều dưỡng cơ bản", "issuedBy": "Trường Cao đẳng Y tế Hà Nội",
                 "issuedDate": "2025-01-15", "expiryDate": "2030-01-15"}""";

        CertificateCheckResponse response = service.check(request()).getResult();

        assertThat(response.getRiskLevel()).isEqualTo(CertificateRiskLevel.MEDIUM);
        assertThat(response.getWarnings()).singleElement()
                .satisfies(warning -> {
                    assertThat(warning.getCode()).isEqualTo(CertificateWarningCode.MISMATCH);
                    assertThat(warning.getField()).isEqualTo(CertificateCheckField.ISSUED_BY);
                });
    }

    @Test
    void unreadableOrNonJsonAnswerIsUndetermined() {
        groqClient.answer = "{\"readable\": false}";
        assertThat(service.check(request()).getResult().getRiskLevel()).isEqualTo(CertificateRiskLevel.UNDETERMINED);

        groqClient.answer = "I cannot read this image.";
        CertificateCheckResponse response = service.check(request()).getResult();
        assertThat(response.getRiskLevel()).isEqualTo(CertificateRiskLevel.UNDETERMINED);
        assertThat(response.getWarnings()).extracting(CertificateWarningResponse::getCode)
                .containsExactly(CertificateWarningCode.UNREADABLE);
    }

    @Test
    void noImageOrOnlyPdfIsUndeterminedWithoutCallingGroq() {
        CertificateCheckRequest noImage = request();
        noImage.setImages(List.of());
        assertThat(service.check(noImage).getResult().getWarnings()).extracting(CertificateWarningResponse::getCode)
                .containsExactly(CertificateWarningCode.NO_IMAGE);

        CertificateCheckRequest pdfOnly = request();
        pdfOnly.setImages(List.of(new CertificateImageRequest("https://r2.test/a.pdf", "application/pdf")));
        CertificateCheckResponse response = service.check(pdfOnly).getResult();
        assertThat(response.getRiskLevel()).isEqualTo(CertificateRiskLevel.UNDETERMINED);
        assertThat(response.getWarnings()).extracting(CertificateWarningResponse::getCode)
                .containsExactly(CertificateWarningCode.UNSUPPORTED_FILE);
        assertThat(groqClient.calls).isZero();
    }

    private CertificateCheckRequest request() {
        List<CertificateImageRequest> images = new ArrayList<>();
        images.add(new CertificateImageRequest("https://r2.test/front.jpg", "image/jpeg"));
        images.add(new CertificateImageRequest("https://r2.test/scan.pdf", "application/pdf"));
        return CertificateCheckRequest.builder()
                .holderName("Nguyễn Văn An")
                .certificateName("Chứng chỉ Điều dưỡng cơ bản")
                .issuedBy("Bệnh viện Bạch Mai")
                .credentialNumber("12/QĐ-BV")
                .issuedDate(LocalDate.of(2025, 1, 15))
                .expiryDate(LocalDate.of(2030, 1, 15))
                .images(images)
                .build();
    }

    static class FakeGroqClient implements GroqClient {
        String answer;
        String model;
        double temperature = -1;
        int calls;

        @Override
        public GroqResult generateAnswer(List<GroqMessageRequest> messages) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GroqResult generateAnswer(List<GroqMessageRequest> messages, String model, double temperature) {
            this.model = model;
            this.temperature = temperature;
            calls++;
            assertThat(messages).hasSize(2);
            return GroqResult.builder().answer(answer).model(model).build();
        }
    }
}
