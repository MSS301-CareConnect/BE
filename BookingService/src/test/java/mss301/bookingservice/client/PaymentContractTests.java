package mss301.bookingservice.client;

import mss301.bookingservice.client.dto.ComplaintRefundRequest;
import mss301.bookingservice.client.dto.OutstandingDebtResponse;
import mss301.bookingservice.client.dto.PaymentPageResponse;
import mss301.bookingservice.client.dto.PaymentPolicyResponse;
import mss301.bookingservice.client.dto.RefundCreateRequest;
import mss301.bookingservice.client.dto.RefundQuoteRequest;
import mss301.bookingservice.client.dto.RefundQuoteResponse;
import mss301.bookingservice.client.dto.RemainingInvoiceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test hợp đồng Booking ↔ Payment phía Booking: file JSON trong {@code contracts/payment/} giống hệt bản ở PaymentService
 * (PaymentContractTests bên đó gửi chính các file này vào API thật). Request: DTO client đọc mẫu ở chế độ strict rồi ghi
 * lại phải ra đúng JSON mẫu (cùng tên field, cùng hình dạng). Response: mẫu đọc được vào DTO client.
 */
class PaymentContractTests {

    static final String FIXTURES = "contracts/payment/";

    final ObjectMapper strictMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void requestDtosSerializeExactlyToContractFixtures() throws IOException {
        assertRoundTrip("refund-quote-request.json", RefundQuoteRequest.class);
        assertRoundTrip("refund-create-request.json", RefundCreateRequest.class);
        assertRoundTrip("remaining-invoice-request.json", RemainingInvoiceRequest.class);
        assertRoundTrip("complaint-refund-request.json", ComplaintRefundRequest.class);
    }

    @Test
    void responseFixturesDeserializeIntoClientDtos() throws IOException {
        RefundQuoteResponse quote = strictMapper.readValue(read("refund-quote-response.json"), RefundQuoteResponse.class);
        assertThat(quote.getItems()).hasSize(1);
        assertThat(quote.getItems().getFirst().getRule()).isEqualTo("TIER_72_INF");
        assertThat(quote.getItems().getFirst().getRefundAmount()).isEqualByComparingTo("300000");
        assertThat(quote.getTotalRefund()).isEqualByComparingTo("300000");
        assertThat(quote.getPolicyId()).isNotNull();

        PaymentPageResponse page = strictMapper.readValue(read("payments-page-response.json"), PaymentPageResponse.class);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getItems().getFirst().getStatus()).isEqualTo("SUCCESS");
        assertThat(page.getItems().getFirst().getRefundedAmount()).isEqualByComparingTo("0");
        assertThat(page.getItems().getFirst().getAmount()).isEqualByComparingTo("300000");

        OutstandingDebtResponse outstanding = strictMapper.readValue(read("outstanding-response.json"),
                OutstandingDebtResponse.class);
        assertThat(outstanding.isHasDebt()).isFalse();

        PaymentPolicyResponse policy = strictMapper.readValue(read("policy-response.json"), PaymentPolicyResponse.class);
        assertThat(policy.getDepositPercent()).isEqualByComparingTo("30");
        assertThat(policy.getPolicyId()).isNotNull();
    }

    private <T> void assertRoundTrip(String fixtureName, Class<T> type) throws IOException {
        String json = read(fixtureName);
        T request = strictMapper.readValue(json, type);
        JsonNode serialized = strictMapper.readTree(strictMapper.writeValueAsString(request));
        assertThat(serialized).as(fixtureName).isEqualTo(strictMapper.readTree(json));
    }

    private String read(String name) throws IOException {
        try (InputStream inputStream = new ClassPathResource(FIXTURES + name).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
