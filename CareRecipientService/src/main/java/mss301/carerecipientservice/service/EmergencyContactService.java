package mss301.carerecipientservice.service;

import mss301.carerecipientservice.dto.request.EmergencyContactRequest;
import mss301.carerecipientservice.dto.response.APIResponse;
import mss301.carerecipientservice.dto.response.EmergencyContactResponse;

import java.util.List;
import java.util.UUID;

public interface EmergencyContactService {
    // Thêm liên hệ khẩn cấp.
    APIResponse<EmergencyContactResponse> create(UUID actorId, UUID recipientId, EmergencyContactRequest request);

    // Danh sách liên hệ khẩn cấp.
    APIResponse<List<EmergencyContactResponse>> list(UUID actorId, UUID recipientId);

    // Chi tiết một liên hệ khẩn cấp.
    APIResponse<EmergencyContactResponse> get(UUID actorId, UUID recipientId, UUID id);

    // Cập nhật liên hệ khẩn cấp.
    APIResponse<EmergencyContactResponse> update(UUID actorId, UUID recipientId, UUID id,
                                                        EmergencyContactRequest request);

    // Xóa liên hệ khẩn cấp.
    APIResponse<Void> delete(UUID actorId, UUID recipientId, UUID id);

    // Đặt liên hệ khẩn cấp chính.
    APIResponse<EmergencyContactResponse> assignPrimary(UUID actorId, UUID recipientId, UUID id);

}
