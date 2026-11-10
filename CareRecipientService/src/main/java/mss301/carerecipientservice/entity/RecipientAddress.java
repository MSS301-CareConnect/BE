package mss301.carerecipientservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

// Sổ địa chỉ của người được chăm sóc (2026-11-05): nơi người bệnh có thể ở ngoài địa chỉ hồ sơ (vd nhà con thứ hai) —
// khách chọn lúc đặt lịch hoặc khi đổi địa chỉ của một số buổi. Mọi người cùng quản lý hồ sơ dùng chung.
@Entity
@Table(name = "recipient_addresses")
@AttributeOverride(name = "id", column = @Column(name = "address_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecipientAddress extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    CareRecipient recipient;

    // Tên gợi nhớ: "Nhà con gái", "Nhà anh Minh".
    @Column(name = "label", nullable = false, length = 100)
    String label;

    @Column(name = "address_line", nullable = false)
    String addressLine;

    @Column(name = "ward", length = 100)
    String ward;

    @Column(name = "district", length = 100)
    String district;

    @Column(name = "province", nullable = false, length = 100)
    String province;

    // Hướng dẫn đường đi / ai ở nhà (không bắt buộc).
    @Column(name = "note", length = 500)
    String note;
}
