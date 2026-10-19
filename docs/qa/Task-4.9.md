# QA Report - Task 4.9
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1358/0/3 (1358 test, 0 fail/error, 3 skipped là test tích hợp bỏ qua theo rules §0)

**Phạm vi review:** `src/java/poscs/dao/ContractDAO.java`, `test/poscs/dao/ContractDAOTest.java` (chỉ 2 file này bị sửa theo `git status`). Đối chiếu UC-10, UC-11, UC-33, UC-34, UC-35, UC-39, UC-41, UC-44, UC-46 và BR-12, BR-16, BR-17, BR-18, BR-35..37.

**Đã xác nhận đạt:**
- Refactor tách hàm (`insert`, `update`, `correct`, `voidRecord`, `changeProgressStatus`, `countStatusSummary`, `appendFilters`, `mapRow`) giữ nguyên SQL, thứ tự ghi và ranh giới transaction. Mọi chỗ ghi nhiều bảng (contracts + contract_history) vẫn nằm trong một giao dịch có `finishTransaction`.
- Không đổi chữ ký public. Xóa mềm `is_deleted` giữ nguyên. Toàn bộ SQL dùng `PreparedStatement`, không hardcode secret, không có `TODO`/placeholder.
- BR-17 (ngưỡng 30 ngày) giờ ủy quyền sang `ContractValidator.computeStatus`. Các test insert/findById hiện có phủ đủ biên (đúng 30 ngày, hôm nay hết hạn, thiếu ngày).
- BR-35, BR-36, BR-37 (bắt buộc trường, ràng buộc ngày, quyền hủy bản ghi) là phần của `ContractValidator` và `ContractService` (Task 4.10). DAO chỉ chặn đúng phần của nó: lý do trống khi `voidRecord`/`correct`, phụ lục thì không hủy được, hợp đồng đã đóng băng thì không sửa. Các nhánh này đều có test cả đúng lẫn sai.

## Danh sách lỗi

- [x] 🟡 `ContractDAO.java` › `executeUpdateTransaction()` (dòng ~1177-1232) và `executeProgressTransition()` (dòng ~1642-1692)
  - Vi phạm: rules §4 (hàm ≤ 40 dòng; hiện lần lượt 56 và 51 dòng)
  - Mô tả: refactor tách hàm nhưng hai hàm mới vẫn vượt ngưỡng, phần lớn do khối comment dài nằm giữa logic.
  - Gợi ý sửa: tách nhánh kiểm tra trước khi ghi và nhánh ghi nhật ký thành hàm riêng, ví dụ:
    ```java
    // executeUpdateTransaction
    private boolean canApplyUpdate(Connection conn, Contract before, Contract written, boolean termsLocked) throws SQLException { ... }
    private void logUpdateHistory(Connection conn, Contract before, Contract written, int id, int actorId) throws SQLException { ... }
    // executeProgressTransition
    private String progressUpdateSql(String toStatus) { ... }
    private void logSignedAmendmentValue(Connection conn, Contract current, int actorId) throws SQLException { ... }
    ```

- [x] 🟡 `ContractDAO.java` › `appendSearchAndAttributeFilters()` (dòng ~2195, 46 dòng), `appendScopeAndStructuralFilters()` (dòng ~2153, 41 dòng), `mapRow()` (dòng ~2258, 45 dòng)
  - Vi phạm: rules §4 (hàm ≤ 40 dòng). UC-10 (lọc/tìm kiếm), BR-16 (cột danh sách).
  - Mô tả: `appendFilters` được tách làm hai nhưng cả hai hàm con vẫn > 40 dòng. `mapRow` cũng được sửa trong task (tách enterprise/owner) nhưng vẫn 45 dòng.
  - Gợi ý sửa: tách riêng phần keyword (`appendKeywordFilter`) và phần tỉnh/kỳ/chiều (`appendProvincePeriodFilters`). Với `mapRow`, tách phần gán `pending_departments`/`pending_handover_days` thành `mapHandoverSummary(rs, c)`.

- [x] 🟡 `ContractDAO.java` › `findAll(...)` (dòng ~398-425) và `countAll(...)`
  - Vi phạm: BR-12 (phân trang), checklist B (an toàn đầu vào)
  - Mô tả: không chặn `pageSize <= 0`. `pageSize` bằng 0 hoặc âm đi thẳng vào `LIMIT ?`, MySQL báo lỗi cú pháp, DAO nuốt lỗi và trả danh sách rỗng mà không ai biết. `page < 1` được `Math.max(0, ...)` xử lý nhưng không có test. Không có test nào cho các biên này.
  - Gợi ý sửa:
    ```java
    int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE; // BR-12: mặc định 10
    params.add(size);
    params.add(Math.max(0, (Math.max(page, 1) - 1) * size));
    ```
    kèm test `findAll_pageSizeZeroOrNegative_usesDefaultPageSize`, `findAll_pageBelowOne_startsAtOffsetZero`.

- [x] 🟡 `ContractDAOTest.java` › khối "Task 4.9" (cuối file)
  - Vi phạm: checklist E, rules §4 (mỗi nhánh mới có test tương ứng)
  - Mô tả: các hàm mới tách (`mapEnterpriseFromRow`, `mapOwnerFromRow`, `waitingHandoverClause`, `bindStatusSummaryParams`) có nhánh trả `null` và nhánh `WAITING_ANY_DEPARTMENT`/phòng cụ thể. Test thêm vào chưa phủ rõ các nhánh này: không có test `findAll` với enterprise null, province null, owner null. `insertProducts` với phần tử `null` cũng chưa có test.
  - Gợi ý sửa: thêm test mapRow với `enterprise_name = null` (kỳ vọng `getEnterprise() == null`), `province_name = null` (kỳ vọng `getAddress() == null`), `owner_last_name = null`. Thêm test `countStatusSummary` với `WAITING_ANY_DEPARTMENT`, kiểm tra không bind thêm tham số.

- [x] 🟡 `ContractDAO.java` › `insertProducts()` (dòng ~1032)
  - Vi phạm: checklist B (xử lý null), UC-35 (hàng hóa)
  - Mô tả: mới chặn `items == null`. Danh sách chứa phần tử `null` sẽ gây NPE trong `executeInsertProductBatch` giữa transaction đang mở, thay vì trả `false` sớm. Kiểm tra `quantity <= 0` là việc của validator/service, nhưng DAO vẫn phải an toàn với `null`.
  - Gợi ý sửa:
    ```java
    if (items.stream().anyMatch(java.util.Objects::isNull)) {
        return false;
    }
    ```
    kèm test `insertProducts_listWithNullElement_returnsFalseWithoutTouchingDatabase`.

- [x] 🟢 `ContractDAO.java` › import `poscs.validator.ContractValidator` (dòng ~20) và `computeStatus()` (dòng ~2254)
  - Vi phạm: rules §4 (phân tầng `dao` / `validator`); style
  - Mô tả: tầng `dao` giờ phụ thuộc ngược lên `validator`, và import chèn giữa nhóm `poscs.common` và `poscs.model`. Hành vi đúng nhưng lệch phân tầng. Cân nhắc đưa hàm tính trạng thái vào `poscs.common`, hoặc ghi quyết định này vào `docs/ARCHITECTURE.md`.
  - Gợi ý sửa: đặt import theo thứ tự alphabet (`poscs.model` trước `poscs.validator`).

- [ ] 🟢 `ContractDAO.java` (toàn file, ~2447 dòng)
  - Vi phạm: rules §4 (file > 250 dòng); đối chiếu mô tả Task 4.9 ("thu gọn `ContractDAO`")
  - Mô tả: sau task này file vẫn ~2450 dòng (git diff: +711/-458). Còn khoảng 25 method public chỉ ủy quyền sang `ContractHistoryDAO`, `ContractPaymentDAO`, `ContractDocumentDAO`, `ContractHandoverDAO`, `ContractLinkDAO`. Giữ lại là đúng vì rules cấm đổi chữ ký public, nhưng cần người dùng xác nhận "thu gọn" trong tracker nghĩa là gì (tách hàm, hay xóa các method ủy quyền sau khi caller được chuyển). Không coi là lỗi nghiệp vụ.
  - **Lý do không xóa method ủy quyền:** `rules.md` §3 quy định cứng *"Không đổi chữ ký của public API/Service nếu SPEC hoặc task không yêu cầu rõ"* và yêu cầu giữ tương thích ngược. Hiện tại `ContractController`, `DashboardController` và các test hiện hữu trong `ContractDAOTest` vẫn gọi trực tiếp ~25 method ủy quyền này (việc chuyển caller sang Service/DAO con nằm ở các Task 4.10–4.18). Do đó giữ nguyên các method ủy quyền để đảm bảo toàn bộ codebase biên dịch và chạy test không bị gãy.
