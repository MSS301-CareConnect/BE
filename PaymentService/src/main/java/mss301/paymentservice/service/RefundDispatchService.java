package mss301.paymentservice.service;

import java.util.List;
import java.util.UUID;

// Worker hoàn tiền (rule #25): mỗi refund một transaction ngắn, gọi cổng ngoài transaction.
public interface RefundDispatchService {
    List<UUID> findDueIds();

    void process(UUID refundId);
}
