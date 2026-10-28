package mss301.providerservice.service;

import mss301.providerservice.entity.CareCenter;
import java.util.List;
import java.util.UUID;

public interface ProviderOutboxService {
    void enqueue(CareCenter center, String event, String reason);
    List<UUID> findDueIds();
    void dispatch(UUID id);
}
