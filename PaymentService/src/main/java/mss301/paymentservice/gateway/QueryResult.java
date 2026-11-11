package mss301.paymentservice.gateway;

public record QueryResult(Status status, String transactionNo, String amountMinor, String responseCode) {

    public enum Status {
        PAID,
        NOT_PAID,
        UNKNOWN
    }
}
