package mss301.providerservice.mapper;

import mss301.providerservice.dto.request.WorkShiftRequest;
import mss301.providerservice.dto.response.WorkShiftResponse;
import mss301.providerservice.entity.CareCenter;
import mss301.providerservice.entity.WorkShift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class)
public interface WorkShiftMapper {

    // approvedCount / pendingCount do service đếm rồi truyền vào; shiftStatus là giá trị đã lưu — service ghi đè bằng
    // trạng thái hiệu lực (WorkShiftStates) trước khi trả về.
    @Mapping(target = "centerId", source = "workShift.center.id")
    @Mapping(target = "centerName", source = "workShift.center.centerName")
    WorkShiftResponse toResponse(WorkShift workShift, long approvedCount, long pendingCount);

    // center do service tra cứu + kiểm phạm vi rồi truyền vào; audit do service gán.
    @IgnoreServerManagedFields
    @IgnoreServerManagedWorkShiftFields
    WorkShift toWorkShift(WorkShiftRequest request, CareCenter center);

    // PUT toàn phần: null ghi đè. Ca không chuyển sang trung tâm khác.
    @IgnoreServerManagedFields
    @IgnoreServerManagedWorkShiftFields
    @Mapping(target = "center", ignore = true)
    void update(WorkShiftRequest request, @MappingTarget WorkShift workShift);
}
