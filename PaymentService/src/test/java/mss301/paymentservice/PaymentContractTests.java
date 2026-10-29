package mss301.paymentservice;

import mss301.paymentservice.support.PaymentIntegrationTest;
import mss301.paymentservice.support.PaymentTestServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test hợp đồng phía Payment: file JSON trong {@code contracts/payment/} giống hệt bản ở BookingService. Request mẫu
 * của Booking gửi tới Payment phải được nhận (fail-on-unknown-properties bật → tên field lệch là 400); response thật
 * của Payment phải có đủ field mà mẫu (Booking đọc) có.
 */
class PaymentContractTests extends PaymentIntegrationTest {

    static final String FIXTURES = "contracts/payment/";

    @Test
    void bookingRequestsAreAcceptedAndResponsesHaveEveryContractField() throws Exception {
        JsonNode quoteRequest = fixture("refund-quote-request.json");
        UUID bookingId = UUID.fromString(quoteRequest.get("bookingId").asString());
        UUID occurrenceId = UUID.fromString(quoteRequest.get("occurrences").get(0).get("occurrenceId").asString());
        UUID customerId = UUID.randomUUID();
        // Booking đã cọc với đúng id trong mẫu để mọi endpoint ghi trả 200 thật (kể cả hoàn khiếu nại).
        Instant startAt = Instant.parse(quoteRequest.get("occurrences").get(0).get("startAt").asString());
        SERVER.putBooking(bookingId, customerId, UUID.randomUUID(), "AWAITING_DEPOSIT", BASE.plusSeconds(3600),
                List.of(PaymentTestServer.occurrence(occurrenceId, 1, startAt, "SCHEDULED", 1_000_000L)));
        payDeposit(new BookingSetup(bookingId, customerId, null, List.of(occurrenceId), List.of(startAt)));

        JsonNode quote = postOk("/api/v1/refund-quotes", "refund-quote-request.json", null);
        assertHasFields(quote, fixture("refund-quote-response.json"), "refund-quote");

        // Thứ tự để mọi lệnh đều hợp lệ: hóa đơn → khiếu nại (giảm hóa đơn) → hoàn buổi (còn đủ tiền cọc).
        postOk("/api/v1/remaining-invoices", "remaining-invoice-request.json", "REMAINING_INVOICE:" + bookingId);
        postOk("/api/v1/refunds/complaints", "complaint-refund-request.json", "COMPLAINT_REFUND:contract");
        postOk("/api/v1/refunds", "refund-create-request.json", "REFUND:CENTER_CANCEL:" + occurrenceId);

        JsonNode page = getOk(get("/api/v1/payments").header("Authorization", service())
                .param("bookingId", bookingId.toString()).param("status", "SUCCESS")
                .param("page", "0").param("size", "100"));
        assertHasFields(page, fixture("payments-page-response.json"), "payments-page");

        JsonNode outstanding = getOk(get("/api/v1/payments/me/outstanding").header("Authorization", customer(customerId)));
        assertHasFields(outstanding, fixture("outstanding-response.json"), "outstanding");

        JsonNode policy = getOk(get("/api/v1/payment-policies/effective").header("Authorization", service())
                .param("centerId", UUID.randomUUID().toString()).param("at", BASE.toString()));
        assertHasFields(policy, fixture("policy-response.json"), "policy");
    }

    private JsonNode postOk(String path, String fixtureName, String idempotencyKey) throws Exception {
        MockHttpServletRequestBuilder request = post(path).header("Authorization", service())
                .contentType(MediaType.APPLICATION_JSON)
                .content(read(fixtureName));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return body(mockMvc.perform(request).andExpect(status().isOk())).get("result");
    }

    private JsonNode getOk(MockHttpServletRequestBuilder request) throws Exception {
        return body(mockMvc.perform(request).andExpect(status().isOk())).get("result");
    }

    // Mọi field (đệ quy, phần tử đầu của mảng) có trong mẫu phải có trong response thật.
    private void assertHasFields(JsonNode actual, JsonNode expected, String path) {
        List<String> missing = new ArrayList<>();
        collectMissing(actual, expected, path, missing);
        assertThat(missing).as("Field thiếu so với hợp đồng").isEmpty();
    }

    private void collectMissing(JsonNode actual, JsonNode expected, String path, List<String> missing) {
        if (expected.isObject()) {
            for (String field : expected.propertyNames()) {
                if (actual == null || !actual.has(field)) {
                    missing.add(path + "." + field);
                    continue;
                }
                collectMissing(actual.get(field), expected.get(field), path + "." + field, missing);
            }
        } else if (expected.isArray() && !expected.isEmpty()) {
            if (actual == null || !actual.isArray() || actual.isEmpty()) {
                missing.add(path + "[0]");
                return;
            }
            collectMissing(actual.get(0), expected.get(0), path + "[0]", missing);
        }
    }

    private JsonNode fixture(String name) throws IOException {
        return objectMapper.readTree(read(name));
    }

    private String read(String name) throws IOException {
        try (InputStream inputStream = new ClassPathResource(FIXTURES + name).getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
