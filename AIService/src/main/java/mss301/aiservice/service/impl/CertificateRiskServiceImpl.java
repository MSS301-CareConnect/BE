package mss301.aiservice.service.impl;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import mss301.aiservice.dto.request.CertificateCheckRequest;
import mss301.aiservice.dto.response.CertificateExtractedResponse;
import mss301.aiservice.dto.response.CertificateWarningResponse;
import mss301.aiservice.enums.CertificateCheckField;
import mss301.aiservice.enums.CertificateRiskLevel;
import mss301.aiservice.enums.CertificateWarningCode;
import mss301.aiservice.mapper.CertificateCheckMapper;
import mss301.aiservice.service.CertificateRiskService;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Quy tắc mức rủi ro (đã chốt với chủ dự án 2026-11-09):
 * - UNDETERMINED: không có ảnh, chỉ có file không đọc được, hoặc AI không đọc được ảnh.
 * - HIGH: tên caregiver hoặc mã chứng chỉ KHÁC trên ảnh, hoặc chứng chỉ đã hết hạn.
 * - MEDIUM: tên chứng chỉ / đơn vị cấp / ngày cấp / ngày hết hạn khác, hoặc một trường đã khai không thấy trên ảnh.
 * - LOW: không có cảnh báo nào.
 * Chỉ so các trường caregiver đã khai; so sánh bỏ dấu, bỏ hoa thường, bỏ ký tự phân cách.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CertificateRiskServiceImpl implements CertificateRiskService {
    // Tỉ lệ từ chung tối thiểu để coi tên chứng chỉ / đơn vị cấp là cùng một (OCR hay rút gọn, thêm bớt chữ).
    static double TEXT_SIMILARITY_THRESHOLD = 0.6;
    static Set<CertificateWarningCode> UNDETERMINED_CODES = Set.of(CertificateWarningCode.NO_IMAGE,
            CertificateWarningCode.UNSUPPORTED_FILE, CertificateWarningCode.UNREADABLE);
    static Set<CertificateCheckField> PRIMARY_FIELDS = Set.of(CertificateCheckField.HOLDER_NAME,
            CertificateCheckField.CREDENTIAL_NUMBER);

    // Nhãn thường in trước số chứng chỉ (sau khi bỏ dấu, viết thường).
    static Pattern NUMBER_LABEL = Pattern.compile("^(so hieu|so vao so|so chung chi|ma so|so|no|number)\\s+");

    CertificateCheckMapper certificateCheckMapper;

    @Override
    public List<CertificateWarningResponse> compare(CertificateCheckRequest declared,
                                                    CertificateExtractedResponse extracted, LocalDate today) {
        List<CertificateWarningResponse> warnings = new ArrayList<>();

        compareText(warnings, CertificateCheckField.HOLDER_NAME, declared.getHolderName(), extracted.getHolderName(),
                sameWords(declared.getHolderName(), extracted.getHolderName()));
        compareText(warnings, CertificateCheckField.CREDENTIAL_NUMBER, declared.getCredentialNumber(),
                extracted.getCredentialNumber(),
                compact(declared.getCredentialNumber()).equals(compact(extracted.getCredentialNumber())));
        compareText(warnings, CertificateCheckField.CERTIFICATE_NAME, declared.getCertificateName(),
                extracted.getCertificateName(), similar(declared.getCertificateName(), extracted.getCertificateName()));
        compareText(warnings, CertificateCheckField.ISSUED_BY, declared.getIssuedBy(), extracted.getIssuedBy(),
                similar(declared.getIssuedBy(), extracted.getIssuedBy()));
        compareDate(warnings, CertificateCheckField.ISSUED_DATE, declared.getIssuedDate(), extracted.getIssuedDate());
        compareDate(warnings, CertificateCheckField.EXPIRY_DATE, declared.getExpiryDate(), extracted.getExpiryDate());

        // Hạn ghi trên ảnh là căn cứ; ảnh không ghi hạn thì dùng hạn caregiver khai.
        LocalDate expiryDate = extracted.getExpiryDate() != null ? extracted.getExpiryDate() : declared.getExpiryDate();

        if (expiryDate != null && expiryDate.isBefore(today)) {
            warnings.add(certificateCheckMapper.toWarningResponse(CertificateWarningCode.EXPIRED,
                    CertificateCheckField.EXPIRY_DATE, toText(declared.getExpiryDate()),
                    toText(extracted.getExpiryDate())));
        }

        return warnings;
    }

    @Override
    public CertificateRiskLevel toRiskLevel(List<CertificateWarningResponse> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return CertificateRiskLevel.LOW;
        }

        boolean undetermined = warnings.stream().anyMatch(warning -> UNDETERMINED_CODES.contains(warning.getCode()));

        if (undetermined) {
            return CertificateRiskLevel.UNDETERMINED;
        }

        boolean high = warnings.stream().anyMatch(warning -> warning.getCode() == CertificateWarningCode.EXPIRED
                || (warning.getCode() == CertificateWarningCode.MISMATCH && PRIMARY_FIELDS.contains(warning.getField())));

        return high ? CertificateRiskLevel.HIGH : CertificateRiskLevel.MEDIUM;
    }

    private void compareText(List<CertificateWarningResponse> warnings, CertificateCheckField field,
                             String declaredValue, String extractedValue, boolean matches) {
        if (isBlank(declaredValue)) {
            return;
        }

        if (isBlank(extractedValue)) {
            warnings.add(certificateCheckMapper.toWarningResponse(CertificateWarningCode.NOT_FOUND, field,
                    declaredValue.trim(), null));
            return;
        }

        if (!matches) {
            warnings.add(certificateCheckMapper.toWarningResponse(CertificateWarningCode.MISMATCH, field,
                    declaredValue.trim(), extractedValue.trim()));
        }
    }

    private void compareDate(List<CertificateWarningResponse> warnings, CertificateCheckField field,
                             LocalDate declaredValue, LocalDate extractedValue) {
        if (declaredValue == null) {
            return;
        }

        if (extractedValue == null) {
            warnings.add(certificateCheckMapper.toWarningResponse(CertificateWarningCode.NOT_FOUND, field,
                    toText(declaredValue), null));
            return;
        }

        if (!declaredValue.equals(extractedValue)) {
            warnings.add(certificateCheckMapper.toWarningResponse(CertificateWarningCode.MISMATCH, field,
                    toText(declaredValue), toText(extractedValue)));
        }
    }

    // Họ tên: cùng tập từ sau khi bỏ dấu (chấp nhận viết hoa toàn bộ, đảo họ / tên).
    private boolean sameWords(String first, String second) {
        return words(first).equals(words(second));
    }

    // Tên chứng chỉ / đơn vị cấp: chuỗi này chứa chuỗi kia, hoặc đủ nhiều từ chung.
    private boolean similar(String first, String second) {
        String normalizedFirst = normalize(first);
        String normalizedSecond = normalize(second);

        if (normalizedFirst.isEmpty() || normalizedSecond.isEmpty()) {
            return normalizedFirst.equals(normalizedSecond);
        }

        if (normalizedFirst.contains(normalizedSecond) || normalizedSecond.contains(normalizedFirst)) {
            return true;
        }

        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);
        Set<String> common = new HashSet<>(firstWords);
        common.retainAll(secondWords);
        int smaller = Math.min(firstWords.size(), secondWords.size());

        return smaller > 0 && (double) common.size() / smaller >= TEXT_SIMILARITY_THRESHOLD;
    }

    private Set<String> words(String value) {
        String normalized = normalize(value);
        return normalized.isEmpty() ? Set.of() : new HashSet<>(Arrays.asList(normalized.split(" ")));
    }

    // Mã chứng chỉ: bỏ nhãn đứng trước ("Số", "Số hiệu", "No."), chỉ giữ chữ và số, viết hoa ("Số 12/QĐ-ABC" ≡ "12QDABC").
    private String compact(String value) {
        return NUMBER_LABEL.matcher(normalize(value)).replaceFirst("").replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        String withoutMarks = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');

        return withoutMarks.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String toText(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
