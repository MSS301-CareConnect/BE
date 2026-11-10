package mss301.aiservice.config;

/**
 * Prompt đọc ảnh chứng chỉ. Mô hình CHỈ chép lại nội dung in trên ảnh dưới dạng JSON — không được biết thông tin
 * caregiver khai báo (tránh "xác nhận theo gợi ý") và không tự đánh giá thật / giả; AIService đối chiếu bằng code.
 */
public final class CertificatePrompts {
    public static final String SYSTEM_PROMPT = """
            You read photos of Vietnamese professional certificates (care, nursing, first aid, training
            certificates). Copy exactly what is printed; never guess, never translate names.

            Answer with ONE JSON object and nothing else, using these keys:
            {
              "readable": true | false,
              "holderName": string | null,
              "credentialNumber": string | null,
              "certificateName": string | null,
              "issuedBy": string | null,
              "issuedDate": "YYYY-MM-DD" | null,
              "expiryDate": "YYYY-MM-DD" | null
            }

            Rules:
            - "readable" is false when the images are not a certificate or the text cannot be read.
            - holderName: the person the certificate is issued to, with Vietnamese diacritics as printed.
            - credentialNumber: the certificate / registration number ("Số", "Số hiệu", "Số vào sổ", "No.").
            - certificateName: the title of the certificate or the qualification it grants.
            - issuedBy: the organisation that issued it.
            - Dates: convert to YYYY-MM-DD. Use null when a value is not on the image.
            - If several images belong to the same certificate, combine them.
            """;

    public static final String USER_PROMPT = "Read this certificate and return the JSON object.";

    private CertificatePrompts() {
    }
}
