package mss301.carerecipientservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;

import java.util.List;

// Trang kết quả kèm tổng số để FE phân trang (IdentityService chỉ trả List; service này cần tổng số).
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResponse<T> {
    List<T> items;

    int page;

    int size;

    long totalElements;

    int totalPages;

    // Giống APIResponse.success: wrapper chung, không phải entity ↔ DTO nên không đi qua MapStruct.
    public static <T> PageResponse<T> of(Page<?> page, List<T> items) {
        PageResponse<T> response = new PageResponse<>();
        response.setItems(items);
        response.setPage(page.getNumber());
        response.setSize(page.getSize());
        response.setTotalElements(page.getTotalElements());
        response.setTotalPages(page.getTotalPages());
        return response;
    }
}
