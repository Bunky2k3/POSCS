# QA Report - Task 4.1
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1129 pass / 0 fail / 3 skipped

## Đánh giá tổng quan
Đã kiểm tra đầy đủ theo checklist (A. Nghiệp vụ UC-33/35/44 & BR-17/35/36, B. An toàn đầu vào, C. Quy chuẩn §3–§4, D. Phạm vi, E. Unit test):
- **Điểm đạt:** `ContractValidator` kế thừa `BaseValidator`, kiểm tra đầy đủ các trường bắt buộc theo BR-35 (Mã HĐ, Tiêu đề, KH/NCC đúng vai chéo Bán↔Khách mua / Mua↔Nhà cung cấp, Loại HĐ, Người phụ trách), chặn nhập tay/sửa `signingDate`, khóa chiều `direction`, tính trạng thái tự động theo BR-17 (`Chưa hiệu lực`, `Đang hiệu lực`, `Sắp hết hạn` ≤ 30 ngày, `Đã hết hạn`), kiểm tra `Ngày ký ≤ hiệu lực ≤ kết thúc` kèm đúng nội dung `MSG-045` (BR-36), và chặn khoản giảm trừ phụ lục vượt giá trị còn lại của HĐ gốc (UC-35). Toàn bộ phương thức đều ≤ 36 dòng, không chứa placeholder hay truy cập DB.
- **Điểm cần khắc phục:** Còn một số lỗ hổng kiểm tra đầu vào ở luồng cập nhật/tạo hợp đồng, kích thước file vượt ngưỡng quy chuẩn và 1 assertion lỏng trong Unit Test (chi tiết bên dưới).

## Danh sách lỗi

- [x] 🔴 `ContractValidator.java` › `validateUpdate()` (dòng ~502–508)
  - Vi phạm: BR-35 / UC-35 (Ngày ký không thể thay đổi sau khi đã ký).
  - Mô tả: Điều kiện kiểm tra ngày ký bị sửa ở dòng 505 viết `else if (existing.getSigningDate() != null && c.getSigningDate() != null && !existing.getSigningDate().equals(c.getSigningDate()))`. Nếu hợp đồng `existing` đã có `signingDate != null` nhưng đối tượng cập nhật `c` truyền vào có `c.getSigningDate() == null` (cố tình xóa trắng ngày ký), điều kiện này bị bỏ qua hoàn toàn và `validateUpdate` trả về `valid = true`, làm mất ngày ký của hợp đồng đã ký.
  - Gợi ý sửa:
    ```java
    if (existing.getSigningDate() == null && c.getSigningDate() != null) {
        result.addError(FIELD_SIGNING_DATE, MSG_SIGNING_DATE_SYSTEM_GENERATED);
    } else if (existing.getSigningDate() != null
            && !existing.getSigningDate().equals(c.getSigningDate())) {
        result.addError(FIELD_SIGNING_DATE, MSG_SIGNING_DATE_IMMUTABLE);
    }
    ```

- [x] 🟡 `ContractValidator.java` › `validateCreate()` & `validateUpdate()` (dòng ~388–391, 449–451, 497–500, 525–527)
  - Vi phạm: BR-35 / UC-33 / UC-35 & Mục B (An toàn đầu vào).
  - Mô tả: Có 3 điểm kiểm tra đầu vào chưa chặt chẽ:
    1. Phương thức `isValidContractType(contractType, direction)` đã được viết (dòng 174) để kiểm tra loại hợp đồng có thuộc danh mục của chiều Bán (`SELL_CONTRACT_TYPES`) hay Mua (`BUY_CONTRACT_TYPES`) không, nhưng **không được gọi** trong `validateRequiredFields` / `validateCreate` / `validateUpdate`. Hệ quả: hợp đồng chiều `Bán` truyền `contractType = "Mua thiết bị"` (hoặc chuỗi bất kỳ `"ABC"`) vẫn pass `validateCreate` và `validateUpdate`.
    2. Trong `validateCreate` (dòng 449) và `validateUpdate` (dòng 525), điều kiện `if (enterpriseRoles != null ...)` cho phép bỏ qua kiểm tra đối tác đúng vai (BR-35) khi caller truyền `enterpriseRoles = null` hoặc `Collections.emptySet()`. Cần fail-closed (nếu `c.getEnterpriseId() > 0 && !isBlank(c.getDirection())` thì bắt buộc gọi `validateCounterpartyRole`, vì `isCounterpartyMatchingDirection` đã xử lý `enterpriseRoles == null` trả về `false`).
    3. Trong `validateUpdate` (dòng 497), khi hợp đồng đang ở trạng thái `PROGRESS_SIGNED`, nếu `c.setDirection(null)` hoặc `""` trong khi `existing.getDirection()` là `"Bán"`, nhánh `if (c.getDirection() != null ...)` bị bỏ qua và vì `PROGRESS_SIGNED` return sớm ở dòng 518 (trước `validateRequiredFields`), việc xóa trắng `direction` không bị bắt lỗi.
  - Gợi ý sửa:
    ```java
    // 1. Trong validateRequiredFields (sau khi kiểm tra direction & contractType):
    if (!result.getErrors().containsKey(FIELD_CONTRACT_TYPE)
            && !result.getErrors().containsKey(FIELD_DIRECTION)
            && !isValidContractType(c.getContractType(), c.getDirection())) {
        result.addError(FIELD_CONTRACT_TYPE, MSG_TYPE_REQUIRED);
    }
    // 2. Trong validateCreate / validateUpdate: luôn gọi khi đã có enterpriseId và direction hợp lệ:
    if (c.getEnterpriseId() > 0 && !result.getErrors().containsKey(FIELD_DIRECTION)) {
        validateCounterpartyRole(result, c.getDirection(), enterpriseRoles);
    }
    // 3. Trong validateUpdate (dòng 497):
    if (existing.getDirection() != null
            && (isBlank(c.getDirection()) || !c.getDirection().trim().equalsIgnoreCase(existing.getDirection().trim()))) {
        result.addError(FIELD_DIRECTION, MSG_DIRECTION_IMMUTABLE);
    }
    ```

- [x] 🟡 `ContractValidator.java` › toàn file (dòng ~1–616)
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ").
  - Mô tả: File `ContractValidator.java` dài **616 dòng**, vượt xa ngưỡng 250 dòng theo quy chuẩn §4 (tương tự lỗi đã nêu ở `Task-3.1`). Ngoài ra các hằng `ERR_*` ở dòng 93–103 (`ERR_REQUIRED`, `ERR_INVALID`, ...) được khai báo nhưng không được sử dụng ở bất kỳ đâu trong code hay test, làm tăng số dòng dư thừa.
  - Gợi ý sửa: Thu gọn Javadoc/comment phân mục trùng lặp, lược bỏ các hằng `ERR_*` không sử dụng hoặc tách các hằng thông báo/danh mục loại hợp đồng sang lớp phụ trợ (hoặc tinh gọn file về quanh ngưỡng quy chuẩn).

- [x] 🟡 `ContractValidatorTest.java` › `testValidateDates_signingAfterEnd_violation()` (dòng ~419–427)
  - Vi phạm: Checklist E ("Có test nào assertion quá lỏng (chỉ assertNotNull, không kiểm tra MSG) không?") & BR-36.
  - Mô tả: Tại dòng 426, test chỉ dùng `assertNotNull(vr.getError(ContractValidator.FIELD_SIGNING_DATE))` thay vì `assertEquals` mã thông báo cụ thể. Hơn nữa, trong kịch bản `signing = 2027-01-01`, `effective = 2026-01-01`, `end = 2026-12-31`, do `ValidationResult.addError` dùng `putIfAbsent`, lỗi thực tế được lưu vào `FIELD_SIGNING_DATE` là `MSG_SIGNING_AFTER_EFFECTIVE` (dòng 256) chứ nhánh `MSG_SIGNING_AFTER_END` (dòng 260) bị ghi đè/bỏ qua và chưa bao giờ được kiểm chứng bằng `assertEquals`. Ngoài ra chưa có test cho trường hợp cập nhật phụ lục còn Nháp có giá trị âm (khoản giảm trừ) hợp lệ.
  - Gợi ý sửa:
    ```java
    // Kiểm tra đúng thông báo cụ thể thay vì assertNotNull:
    assertEquals(ContractValidator.MSG_SIGNING_AFTER_EFFECTIVE,
            vr.getError(ContractValidator.FIELD_SIGNING_DATE));

    // Và test nhánh signing > end khi effective == null:
    ValidationResult vrEndOnly = new ValidationResult();
    assertFalse(validator.validateDates(vrEndOnly,
            LocalDate.of(2027, 1, 1), null, LocalDate.of(2026, 12, 31)));
    assertEquals(ContractValidator.MSG_SIGNING_AFTER_END,
            vrEndOnly.getError(ContractValidator.FIELD_SIGNING_DATE));
    ```
