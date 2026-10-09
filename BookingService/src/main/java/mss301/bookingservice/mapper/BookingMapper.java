package mss301.bookingservice.mapper;

import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.dto.request.BookingCreateRequest;
import mss301.bookingservice.dto.response.AssignedCaregiver;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingQuote;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.CaregiverAccessResponse;
import mss301.bookingservice.dto.response.CustomerProfileResponse;
import mss301.bookingservice.dto.response.RecipientSnapshot;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import org.mapstruct.Context;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Mapper(config = MapStructConfig.class, uses = {OccurrenceMapper.class, BookingDetailMapper.class})
public interface BookingMapper {

    String ADDRESS_SEPARATOR = ", ";

    // Tạo booking từ request + dữ liệu đã xác minh (customer từ token, snapshot từ CareRecipient, caregiver từ
    // Provider, báo giá từ Catalog). Mã, trạng thái, hạn duyệt, múi giờ, idempotency do service quyết định rồi gán.
    @IgnoreServerManagedFields
    @IgnoreServerManagedBookingFields
    // request và snapshot cùng có recipientId; request và caregiver cùng có caregiverId → chỉ rõ nguồn
    @Mapping(target = "recipientId", source = "request.recipientId")
    @Mapping(target = "caregiverId", source = "caregiver.caregiverId")
    @Mapping(target = "caregiverUserId", source = "caregiver.caregiverUserId")
    @Mapping(target = "recipientSnapshot", source = "recipientSnapshot")
    // Lịch một lần không gửi endDate → cùng ngày với startDate (cột NOT NULL)
    @Mapping(target = "endDate", source = "request.endDate", defaultExpression = "java(request.getStartDate())")
    @Mapping(target = "serviceAddress", source = "recipientSnapshot", qualifiedByName = "toServiceAddress")
    Booking toBooking(BookingCreateRequest request, UUID customerId, RecipientSnapshot recipientSnapshot,
                      AssignedCaregiver caregiver, BookingQuote quote);

    // customerProfiles rỗng (màn hình khách) → customer null; trung tâm / admin truyền kết quả batch Identity.
    @Mapping(target = "recipientName", source = "recipientSnapshot.fullName")
    @Mapping(target = "customer", expression = "java(toCustomerProfileResponse(customerProfiles.get(booking.getCustomerId())))")
    BookingSummaryResponse toSummaryResponse(Booking booking, @Context Map<UUID, IdentityUserResponse> customerProfiles);

    @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    List<BookingSummaryResponse> toSummaryResponseList(List<Booking> bookings,
                                                       @Context Map<UUID, IdentityUserResponse> customerProfiles);

    // booking và customer cùng có id / status → chỉ rõ nguồn; services / occurrences là tham số trùng tên đích.
    @Mapping(target = "id", source = "booking.id")
    @Mapping(target = "status", source = "booking.status")
    @Mapping(target = "services", source = "services")
    @Mapping(target = "occurrences", source = "occurrences")
    @Mapping(target = "customer", source = "customer")
    BookingDetailResponse toDetailResponse(Booking booking, List<BookingDetail> services,
                                           List<BookingOccurrence> occurrences, IdentityUserResponse customer);

    CustomerProfileResponse toCustomerProfileResponse(IdentityUserResponse user);

    // Phục vụ API nội bộ /internal/bookings/caregiver-access (CareRecipientService gọi).
    CaregiverAccessResponse toCaregiverAccessResponse(UUID caregiverUserId, UUID recipientId, boolean allowed);

    // Địa chỉ chăm sóc = địa chỉ trong snapshot lúc đặt (bỏ phần trống).
    @Named("toServiceAddress")
    default String toServiceAddress(RecipientSnapshot recipientSnapshot) {
        if (recipientSnapshot == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (String part : Arrays.asList(recipientSnapshot.getAddressLine(), recipientSnapshot.getWard(),
                recipientSnapshot.getDistrict(), recipientSnapshot.getProvince())) {
            if (part != null && !part.isBlank()) {
                parts.add(part.strip());
            }
        }
        return String.join(ADDRESS_SEPARATOR, parts);
    }

    // Thứ trong tuần: request/response dùng List<Integer> (1..7), DB lưu smallint[] tăng dần không trùng; rỗng → NULL.
    default Short[] toDayNumbers(List<Integer> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return null;
        }
        return daysOfWeek.stream()
                .filter(Objects::nonNull)
                .sorted()
                .distinct()
                .map(Integer::shortValue)
                .toArray(Short[]::new);
    }

    default List<Integer> toDayList(Short[] daysOfWeek) {
        if (daysOfWeek == null) {
            return null;
        }
        return Arrays.stream(daysOfWeek)
                .map(Short::intValue)
                .collect(Collectors.toList());
    }
}
