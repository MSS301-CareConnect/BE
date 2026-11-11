package mss301.bookingservice.repository.projection;

// Số đánh giá theo từng mức sao (1–5) — biểu đồ phân bố đánh giá.
public interface RatingCountView {
    Short getRating();
    Long getCount();
}
