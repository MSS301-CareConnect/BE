package mss301.providerservice.service;

import mss301.providerservice.dto.response.APIResponse;
import mss301.providerservice.dto.response.ProviderMediaResponse;
import mss301.providerservice.enums.MediaOwnerType;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ProviderMediaService {
    APIResponse<List<ProviderMediaResponse>> upload(UUID actorId, MediaOwnerType ownerType, UUID ownerId,
                                                    List<MultipartFile> files);

    APIResponse<List<ProviderMediaResponse>> list(UUID actorId, MediaOwnerType ownerType, UUID ownerId);

    APIResponse<Void> delete(UUID actorId, MediaOwnerType ownerType, UUID ownerId, UUID mediaFileId);

    /**
     * URL tải (presigned) của tối đa {@code limitPerOwner} ảnh đầu tiên mỗi chủ sở hữu — cho thẻ công khai (SRCH-01/02).
     * Gọi MediaService (mạng) nên chỉ dùng ngoài DB transaction. Best-effort: Media lỗi → bỏ ảnh đó, không làm hỏng
     * danh sách (ảnh là phần trang trí, không phải dữ liệu nghiệp vụ).
     */
    Map<UUID, List<String>> findImageUrls(MediaOwnerType ownerType, Collection<UUID> ownerIds, int limitPerOwner);
}
