package mss301.paymentservice.gateway;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import mss301.paymentservice.client.dto.VnPayRefundResponse;
import mss301.paymentservice.config.PaymentProperties;
import mss301.paymentservice.util.VndMoney;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Adapter API hoàn tiền VNPay (merchant_webapi, vnp_Command=refund, PAY-10). Chỉ dùng khi PAYMENT_REFUND_MODE=VNPAY.
 * Chữ ký: HMAC-SHA512 trên vnp_RequestId|vnp_Version|vnp_Command|vnp_TmnCode|vnp_TransactionType|vnp_TxnRef|vnp_Amount|
 * vnp_TransactionNo|vnp_TransactionDate|vnp_CreateBy|vnp_CreateDate|vnp_IpAddr|vnp_OrderInfo. Chưa kiểm trên sandbox
 * (Đợt 1b). Gọi mạng — caller phải gọi ngoài transaction.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class VnPayRefundGateway {
    static String COMMAND = "refund";
    static String FULL_REFUND = "02";
    static String PARTIAL_REFUND = "03";
    static String SUCCESS_CODE = "00";
    // 94: yêu cầu trùng đang được VNPay xử lý — thử lại sau.
    static String IN_PROGRESS_CODE = "94";
    static String SEPARATOR = "|";

    RestClient vnPayApiRestClient;
    PaymentProperties paymentProperties;
    Clock clock;

    public RefundResult refund(RefundCommand command) {
        PaymentProperties.Vnpay vnpay = paymentProperties.getVnpay();
        if (!isPresent(vnpay.getTmnCode()) || !isPresent(vnpay.getHashSecret()) || !isPresent(vnpay.getApiUrl())) {
            return RefundResult.rejected(null, command.requestId(), "VNPay refund API not configured");
        }
        Map<String, String> body = buildRequest(command, vnpay);
        VnPayRefundResponse response;
        try {
            response = vnPayApiRestClient.post()
                    .uri(vnpay.getApiUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(VnPayRefundResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("VNPay refund {} HTTP {}", command.refundId(), status);
            if (status >= 500 || status == 408 || status == 429) {
                return RefundResult.retryable(null, command.requestId(), "VNPay HTTP " + status);
            }
            return RefundResult.rejected(null, command.requestId(), "VNPay HTTP " + status);
        } catch (ResourceAccessException e) {
            log.warn("VNPay refund {} timed out / unreachable", command.refundId());
            return RefundResult.retryable(null, command.requestId(), "VNPay unreachable: " + e.getClass().getSimpleName());
        } catch (RestClientException e) {
            log.warn("VNPay refund {} unreadable response ({})", command.refundId(), e.getClass().getSimpleName());
            return RefundResult.retryable(null, command.requestId(), "VNPay response unreadable");
        }
        String code = response == null ? null : response.getResponseCode();
        if (SUCCESS_CODE.equals(code)) {
            return RefundResult.succeeded(code, command.requestId());
        }
        if (code == null || IN_PROGRESS_CODE.equals(code)) {
            return RefundResult.retryable(code, command.requestId(), "VNPay refund pending (code " + code + ")");
        }
        return RefundResult.rejected(code, command.requestId(), "VNPay refund rejected (code " + code + ")");
    }

    private Map<String, String> buildRequest(RefundCommand command, PaymentProperties.Vnpay vnpay) {
        String transactionType = command.fullRefund() ? FULL_REFUND : PARTIAL_REFUND;
        String amount = VndMoney.toVnPayAmount(command.amount());
        String transactionDate = VnPayGateway.VNPAY_DATE_FORMAT.format(command.transactionDate().atZone(VnPayGateway.VNPAY_ZONE));
        String createDate = VnPayGateway.VNPAY_DATE_FORMAT.format(clock.instant().atZone(VnPayGateway.VNPAY_ZONE));
        String transactionNo = command.transactionNo() == null ? "" : command.transactionNo();
        String data = String.join(SEPARATOR, command.requestId(), VnPayGateway.VERSION, COMMAND, vnpay.getTmnCode(),
                transactionType, command.transactionReference(), amount, transactionNo, transactionDate,
                command.createdBy(), createDate, vnpay.getServerIp(), command.orderInfo());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", command.requestId());
        body.put("vnp_Version", VnPayGateway.VERSION);
        body.put("vnp_Command", COMMAND);
        body.put("vnp_TmnCode", vnpay.getTmnCode());
        body.put("vnp_TransactionType", transactionType);
        body.put("vnp_TxnRef", command.transactionReference());
        body.put("vnp_Amount", amount);
        body.put("vnp_TransactionNo", transactionNo);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateBy", command.createdBy());
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", vnpay.getServerIp());
        body.put("vnp_OrderInfo", command.orderInfo());
        body.put("vnp_SecureHash", VnPaySigner.hmacSha512(vnpay.getHashSecret(), data));
        return body;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
