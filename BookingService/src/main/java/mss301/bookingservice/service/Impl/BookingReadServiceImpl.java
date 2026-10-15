package mss301.bookingservice.service.Impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.bookingservice.client.dto.IdentityUserResponse;
import mss301.bookingservice.dto.response.BookingDetailResponse;
import mss301.bookingservice.dto.response.BookingSummaryResponse;
import mss301.bookingservice.dto.response.PageResponse;
import mss301.bookingservice.entity.Booking;
import mss301.bookingservice.entity.BookingDetail;
import mss301.bookingservice.entity.BookingOccurrence;
import mss301.bookingservice.mapper.BookingMapper;
import mss301.bookingservice.repository.BookingDetailRepository;
import mss301.bookingservice.repository.BookingOccurrenceRepository;
import mss301.bookingservice.service.BookingReadService;
import mss301.bookingservice.service.IdentityUserService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BookingReadServiceImpl implements BookingReadService {

    BookingDetailRepository bookingDetailRepository;
    BookingOccurrenceRepository bookingOccurrenceRepository;
    BookingMapper bookingMapper;
    IdentityUserService identityUserService;

    @Override
    public BookingDetailResponse toDetailResponse(Booking booking, boolean includeCustomer) {
        List<BookingDetail> details = bookingDetailRepository.findAllByBooking(booking.getId());
        List<BookingOccurrence> occurrences = bookingOccurrenceRepository.findAllByBooking(booking.getId());
        IdentityUserResponse customer = includeCustomer
                ? identityUserService.getProfiles(List.of(booking.getCustomerId())).get(booking.getCustomerId())
                : null;
        return bookingMapper.toDetailResponse(booking, details, occurrences, customer);
    }

    @Override
    public PageResponse<BookingSummaryResponse> toPageResponse(Page<Booking> bookings, boolean includeCustomer) {
        Map<UUID, IdentityUserResponse> customerProfiles = includeCustomer
                ? identityUserService.getProfiles(bookings.map(Booking::getCustomerId).getContent())
                : Map.of();
        List<BookingSummaryResponse> items = bookingMapper.toSummaryResponseList(bookings.getContent(), customerProfiles);
        return PageResponse.of(bookings, items);
    }
}
