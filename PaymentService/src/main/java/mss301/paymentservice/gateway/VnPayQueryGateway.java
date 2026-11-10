package mss301.paymentservice.gateway;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.client.dto.VnPayQueryResponse;
import mss301.paymentservice.config.PaymentProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * API truy vấn giao dịch VNPay (vnp_Command=querydr, PAY-07). Chữ ký HMAC-SHA512 trên
 * vnp_RequestId|vnp_Version|vnp_Command|vnp_TmnCode|vnp_TxnRef|vnp_TransactionDate|vnp_CreateDate|vnp_IpAddr|vnp_OrderInfo.
 * vnp_TransactionDate = vnp_CreateDate của lệnh pay (= createdAt của phiên). Gọi mạng — ngoài transaction.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class VnPayQueryGateway {
    static String COMMAND = "querydr";
    static String OK_CODE = "00";
    static String PAID_STATUS = "00";
    // 01: giao dịch chưa hoàn tất, 02: lỗi — đều là "chưa thu tiền".
    static String NOT_COMPLETED_STATUS = "01";
    static String FAILED_STATUS = "02";
    static String SEPARATOR = "|";

    RestClient vnPayApiRestClient;
    PaymentProperties paymentProperties;
    Clock clock;

    public QueryResult query(String transactionReference, Instant transactionDate) {
        PaymentProperties.Vnpay vnpay = paymentProperties.getVnpay();
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = VnPayGateway.VNPAY_DATE_FORMAT.format(clock.instant().atZone(VnPayGateway.VNPAY_ZONE));
        String paidDate = VnPayGateway.VNPAY_DATE_FORMAT.format(transactionDate.atZone(VnPayGateway.VNPAY_ZONE));
        String orderInfo = "Truy van " + transactionReference;
        String data = String.join(SEPARATOR, requestId, VnPayGateway.VERSION, COMMAND, vnpay.getTmnCode(),
                transactionReference, paidDate, createDate, vnpay.getServerIp(), orderInfo);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", VnPayGateway.VERSION);
        body.put("vnp_Command", COMMAND);
        body.put("vnp_TmnCode", vnpay.getTmnCode());
        body.put("vnp_TxnRef", transactionReference);
        body.put("vnp_OrderInfo", orderInfo);
        body.put("vnp_TransactionDate", paidDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", vnpay.getServerIp());
        body.put("vnp_SecureHash", VnPaySigner.hmacSha512(vnpay.getHashSecret(), data));
        VnPayQueryResponse response;
        try {
            response = vnPayApiRestClient.post()
                    .uri(vnpay.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(VnPayQueryResponse.class);
        } catch (RestClientException e) {
            log.warn("VNPay querydr {} failed ({})", transactionReference, e.getClass().getSimpleName());
            return new QueryResult(QueryResult.Status.UNKNOWN, null, null, null);
        }
        if (response == null || !OK_CODE.equals(response.getResponseCode())) {
            return new QueryResult(QueryResult.Status.UNKNOWN, null, null,
                    response == null ? null : response.getResponseCode());
        }
        String transactionStatus = response.getTransactionStatus();
        if (PAID_STATUS.equals(transactionStatus)) {
            return new QueryResult(QueryResult.Status.PAID, response.getTransactionNo(), response.getAmount(),
                    response.getResponseCode());
        }
        if (NOT_COMPLETED_STATUS.equals(transactionStatus) || FAILED_STATUS.equals(transactionStatus)) {
            return new QueryResult(QueryResult.Status.NOT_PAID, null, null, response.getResponseCode());
        }
        return new QueryResult(QueryResult.Status.UNKNOWN, null, null, response.getResponseCode());
    }
}
