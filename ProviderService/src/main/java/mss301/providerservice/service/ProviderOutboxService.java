package mss301.providerservice.service;

import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.CaregiverProfile;
import java.util.List;
import java.util.UUID;

public interface ProviderOutboxService {
    void enqueue(CareCenter center, String event, String reason);

    // H-11: caregiver ACTIVE → INACTIVE / SUSPENDED / ARCHIVED. Không xếp hàng theo sự kiện trung tâm.
    void enqueueCaregiverDeactivated(CaregiverProfile caregiver, String reason);
    List<UUID> findDueIds();
    void dispatch(UUID id);
}
