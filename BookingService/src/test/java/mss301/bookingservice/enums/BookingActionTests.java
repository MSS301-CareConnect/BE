package mss301.bookingservice.enums;

import mss301.bookingservice.exception.AppException;
import mss301.bookingservice.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * State machine (docs/2026-10-09-PLAN_BOOKING.md §4): kiểm MỌI cặp (hành động × trạng thái) — hợp lệ ra đúng trạng thái
 * đích, còn lại ném INVALID_BOOKING_TRANSITION.
 */
class BookingActionTests {

    static final Map<BookingAction, Set<BookingStatus>> EXPECTED_SOURCES = new EnumMap<>(BookingAction.class);
    static final Map<BookingAction, BookingStatus> EXPECTED_TARGETS = new EnumMap<>(BookingAction.class);

    static {
        // Spec §11.1 — bảng chuyển trạng thái đầy đủ của booking.
        EXPECTED_SOURCES.put(BookingAction.ACCEPT, EnumSet.of(BookingStatus.PENDING_CENTER_CONFIRMATION));
        EXPECTED_TARGETS.put(BookingAction.ACCEPT, BookingStatus.AWAITING_INITIAL_PAYMENT);
        EXPECTED_SOURCES.put(BookingAction.ACCEPT_WITHOUT_CAREGIVER, EnumSet.of(BookingStatus.PENDING_CENTER_CONFIRMATION));
        EXPECTED_TARGETS.put(BookingAction.ACCEPT_WITHOUT_CAREGIVER, BookingStatus.PENDING_CAREGIVER_ASSIGNMENT);
        EXPECTED_SOURCES.put(BookingAction.ASSIGN_CAREGIVER, EnumSet.of(BookingStatus.PENDING_CAREGIVER_ASSIGNMENT));
        EXPECTED_TARGETS.put(BookingAction.ASSIGN_CAREGIVER, BookingStatus.AWAITING_INITIAL_PAYMENT);
        EXPECTED_SOURCES.put(BookingAction.REJECT, EnumSet.of(BookingStatus.PENDING_CENTER_CONFIRMATION,
                BookingStatus.PENDING_CAREGIVER_ASSIGNMENT));
        EXPECTED_TARGETS.put(BookingAction.REJECT, BookingStatus.REJECTED);
        EXPECTED_SOURCES.put(BookingAction.PAY_INITIAL_PAYMENT, EnumSet.of(BookingStatus.AWAITING_INITIAL_PAYMENT));
        EXPECTED_TARGETS.put(BookingAction.PAY_INITIAL_PAYMENT, BookingStatus.CONFIRMED);
        EXPECTED_SOURCES.put(BookingAction.CANCEL_BY_CUSTOMER, EnumSet.of(BookingStatus.PENDING_CENTER_CONFIRMATION,
                BookingStatus.PENDING_CAREGIVER_ASSIGNMENT, BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED));
        EXPECTED_TARGETS.put(BookingAction.CANCEL_BY_CUSTOMER, BookingStatus.CANCELLED);
        EXPECTED_SOURCES.put(BookingAction.CANCEL_BY_CENTER,
                EnumSet.of(BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS));
        EXPECTED_TARGETS.put(BookingAction.CANCEL_BY_CENTER, BookingStatus.CANCELLED);
        EXPECTED_SOURCES.put(BookingAction.CANCEL_BY_SYSTEM, EnumSet.of(BookingStatus.PENDING_CENTER_CONFIRMATION,
                BookingStatus.PENDING_CAREGIVER_ASSIGNMENT, BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED,
                BookingStatus.IN_PROGRESS));
        EXPECTED_TARGETS.put(BookingAction.CANCEL_BY_SYSTEM, BookingStatus.CANCELLED);
        EXPECTED_SOURCES.put(BookingAction.EXPIRE, EnumSet.of(BookingStatus.PENDING_CENTER_CONFIRMATION,
                BookingStatus.PENDING_CAREGIVER_ASSIGNMENT, BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED));
        EXPECTED_TARGETS.put(BookingAction.EXPIRE, BookingStatus.EXPIRED);
        EXPECTED_SOURCES.put(BookingAction.START, EnumSet.of(BookingStatus.CONFIRMED));
        EXPECTED_TARGETS.put(BookingAction.START, BookingStatus.IN_PROGRESS);
        EXPECTED_SOURCES.put(BookingAction.FINALIZE, EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS));
        EXPECTED_TARGETS.put(BookingAction.FINALIZE, BookingStatus.COMPLETED);
        EXPECTED_SOURCES.put(BookingAction.FINALIZE_CANCELLED, EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS));
        EXPECTED_TARGETS.put(BookingAction.FINALIZE_CANCELLED, BookingStatus.CANCELLED);
    }

    @Test
    void everyActionAndStatusPairBehavesAsDesigned() {
        assertThat(EXPECTED_SOURCES.keySet()).containsExactlyInAnyOrder(BookingAction.values());
        int checkedPairs = 0;
        for (BookingAction action : BookingAction.values()) {
            for (BookingStatus status : BookingStatus.values()) {
                checkedPairs++;
                if (EXPECTED_SOURCES.get(action).contains(status)) {
                    assertThat(action.isAllowedFrom(status)).as("%s from %s", action, status).isTrue();
                    assertThat(action.apply(status)).as("%s from %s", action, status).isEqualTo(EXPECTED_TARGETS.get(action));
                } else {
                    assertThat(action.isAllowedFrom(status)).as("%s from %s", action, status).isFalse();
                    assertThatThrownBy(() -> action.apply(status))
                            .as("%s from %s", action, status)
                            .isInstanceOf(AppException.class)
                            .extracting(exception -> ((AppException) exception).getErrorCode())
                            .isEqualTo(ErrorCode.INVALID_BOOKING_TRANSITION);
                }
            }
        }
        assertThat(checkedPairs).isEqualTo(BookingAction.values().length * BookingStatus.values().length);
    }

    @Test
    void terminalStatusesHaveNoOutgoingTransition() {
        for (BookingStatus status : BookingStatus.values()) {
            boolean hasOutgoing = false;
            for (BookingAction action : BookingAction.values()) {
                hasOutgoing |= action.isAllowedFrom(status);
            }
            assertThat(hasOutgoing).as("%s", status).isEqualTo(!status.isTerminal());
        }
    }

    @Test
    void scheduleHoldingStatusesAreExactlyTheActiveOnes() {
        assertThat(BookingStatus.SCHEDULE_HOLDING).containsExactlyInAnyOrder(BookingStatus.PENDING_CENTER_CONFIRMATION,
                BookingStatus.PENDING_CAREGIVER_ASSIGNMENT, BookingStatus.AWAITING_INITIAL_PAYMENT, BookingStatus.CONFIRMED,
                BookingStatus.IN_PROGRESS);
        assertThat(BookingStatus.CAREGIVER_RECIPIENT_ACCESS).containsExactlyInAnyOrder(BookingStatus.CONFIRMED,
                BookingStatus.IN_PROGRESS);
    }

    @Test
    void occurrenceTransitions() {
        // Spec §11.2: chưa có caregiver → giữ lịch → lên lịch chính thức khi trả khoản đầu.
        assertThat(OccurrenceStatus.PENDING_ASSIGNMENT.canTransitionTo(OccurrenceStatus.RESERVED)).isTrue();
        assertThat(OccurrenceStatus.PENDING_ASSIGNMENT.canTransitionTo(OccurrenceStatus.SCHEDULED)).isFalse();
        assertThat(OccurrenceStatus.RESERVED.canTransitionTo(OccurrenceStatus.SCHEDULED)).isTrue();
        assertThat(OccurrenceStatus.RESERVED.canTransitionTo(OccurrenceStatus.EXPIRED)).isTrue();
        assertThat(OccurrenceStatus.RESERVED.canTransitionTo(OccurrenceStatus.IN_PROGRESS)).isFalse();
        assertThat(OccurrenceStatus.SCHEDULED.canTransitionTo(OccurrenceStatus.CANCELLED)).isTrue();
        assertThat(OccurrenceStatus.SCHEDULED.canTransitionTo(OccurrenceStatus.IN_PROGRESS)).isTrue();
        assertThat(OccurrenceStatus.SCHEDULED.canTransitionTo(OccurrenceStatus.CAREGIVER_MISSED)).isTrue();
        assertThat(OccurrenceStatus.SCHEDULED.canTransitionTo(OccurrenceStatus.COMPLETED)).isFalse();
        // M1: booking quá hạn trả phần còn lại → buổi đã lên lịch hết hiệu lực.
        assertThat(OccurrenceStatus.SCHEDULED.canTransitionTo(OccurrenceStatus.EXPIRED)).isTrue();
        assertThat(OccurrenceStatus.ACKNOWLEDGED.canTransitionTo(OccurrenceStatus.EXPIRED)).isTrue();
        assertThat(OccurrenceStatus.IN_PROGRESS.canTransitionTo(OccurrenceStatus.EXPIRED)).isFalse();
        assertThat(OccurrenceStatus.IN_PROGRESS.canTransitionTo(OccurrenceStatus.COMPLETED)).isTrue();
        assertThat(OccurrenceStatus.IN_PROGRESS.canTransitionTo(OccurrenceStatus.CANCELLED)).isFalse();
        for (OccurrenceStatus terminal : EnumSet.of(OccurrenceStatus.COMPLETED, OccurrenceStatus.CANCELLED,
                OccurrenceStatus.EXPIRED, OccurrenceStatus.CUSTOMER_NO_SHOW, OccurrenceStatus.CAREGIVER_MISSED)) {
            for (OccurrenceStatus target : OccurrenceStatus.values()) {
                assertThat(terminal.canTransitionTo(target)).isFalse();
            }
        }
        // Spec §6: các trạng thái chiếm lịch caregiver.
        assertThat(OccurrenceStatus.CAREGIVER_HOLDING).containsExactlyInAnyOrder(OccurrenceStatus.RESERVED,
                OccurrenceStatus.SCHEDULED, OccurrenceStatus.ACKNOWLEDGED, OccurrenceStatus.IN_PROGRESS);
    }
}
