# QA Report - Task 4.3
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1192/0/3 (1192 pass / 0 fail / 3 skipped tích hợp theo rules §0)

## Danh sách lỗi

- [x] 🟡 `ContractHistoryDAO.java` › `insertHistory(int, ...)`, `insertStatusChange(int, ...)`, `insertHistory(Connection, ContractHistory)`, `insertHistory(ContractHistory)` (dòng ~136–232)
  - Vi phạm: `rules.md` §1, §3 ("Chỉ tách, không thêm tính năng"), §4 & NFR-19 ("mọi thao tác ghi nhiều bảng chạy trong một giao dịch"; file > 250 dòng).
  - Mô tả:
    1. Bảng `contract_history` là bảng lưu vết kiểm toán đi kèm thao tác thay đổi hợp đồng. Như thiết kế gốc tại `ContractDAO` (dòng ~2195), thao tác ghi lịch sử **cố ý không tự mở connection riêng** và phải ném `SQLException` để transaction của bên gọi rollback khi ghi lịch sử thất bại. Việc tự thêm các hàm ghi độc lập tự mở connection (`DBContext.getConnection()`) và nuốt `SQLException` (trả `false`/`-1`) không có bất kỳ nơi nào trong codebase sử dụng, tạo rủi ro các task sau gọi nhầm ngoài transaction (vi phạm NFR-19), đồng thời làm file phình lên 320 dòng (vượt ngưỡng 250 dòng của rules §4).
    2. Hàm `insertHistory(Connection conn, ContractHistory history)` (dòng ~182) nhận `Connection` trong transaction nhưng khi tham số không hợp lệ hoặc `affected == 0` lại trả `-1` thay vì ném `SQLException`, không đồng nhất với `insertHistory(Connection, int, ...)` và `insertStatusChange(Connection, ...)`.
  - Gợi ý sửa: Lược bỏ các hàm ghi tự mở connection không được gọi (`insertHistory(int, ...)`, `insertStatusChange(int, ...)`, `insertHistory(ContractHistory)`, `insertHistory(Connection, ContractHistory)`) để giữ đúng ranh giới "chỉ tách, không đổi hành vi" và đảm bảo mọi thao tác ghi nhật ký đều bắt buộc truyền `Connection` của transaction đang mở; khi đó file cũng gọn về ~170 dòng (≤ 250 dòng).

- [x] 🟡 `ContractHistoryDAO.java` › `eventTypeOf(String)` & `insertStatusChange(Connection, ...)` (dòng ~55, ~106)
  - Vi phạm: Checklist B (An toàn đầu vào) & `rules.md` §3–§4.
  - Mô tả:
    1. Trong `ContractDAO` gốc, `eventTypeOf` là `private` và chỉ được gọi sau khi `ALLOWED_TRANSITIONS` đã khóa chặt `toStatus`. Khi đưa ra `public static` ở `ContractHistoryDAO`, mọi giá trị `toStatus` bất kỳ (kể cả `null`, chuỗi rỗng, hoặc trạng thái sai) đều mặc định trả về `ContractHistory.EVENT_TERMINATED` (`"Chấm dứt sớm"`).
    2. `insertStatusChange(Connection, ...)` không kiểm tra `fromStatus` và `toStatus` null/rỗng; nếu một trong hai là `null`, câu lệnh `fromStatus + " → " + toStatus` (dòng 117) sẽ tạo chuỗi `"null → ..."` và lưu thẳng vào cột `detail` trong DB.
  - Gợi ý sửa:
    ```java
    public static String eventTypeOf(String toStatus) {
        if (ContractDAO.PROGRESS_SIGNED.equals(toStatus)) {
            return ContractHistory.EVENT_SIGNED;
        }
        if (ContractDAO.PROGRESS_LIQUIDATED.equals(toStatus)) {
            return ContractHistory.EVENT_LIQUIDATED;
        }
        if (ContractDAO.PROGRESS_TERMINATED.equals(toStatus)) {
            return ContractHistory.EVENT_TERMINATED;
        }
        throw new IllegalArgumentException("Invalid target progress status: " + toStatus);
    }
    ```
    Và trong `insertStatusChange(Connection, ...)`, kiểm tra `fromStatus == null || fromStatus.trim().isEmpty() || toStatus == null || toStatus.trim().isEmpty()` thì ném `SQLException`.

- [x] 🟡 `ContractHistoryDAO.java` › `truncate(String, int)` & `insertHistory(Connection, ...)` (dòng ~42, ~77)
  - Vi phạm: Checklist B (An toàn đầu vào) & `db/schema.sql` (`event_type varchar(30) NOT NULL`, `detail varchar(500) NOT NULL`).
  - Mô tả:
    1. `truncate(String text, int max)` (dòng 46): khi `max <= 0` và `text` không rỗng, `text.substring(0, max - 1)` sẽ ném `StringIndexOutOfBoundsException`.
    2. `insertHistory(Connection, int, String, String, int, String)` (dòng 77) chỉ kiểm tra `conn == null`, chưa chặn `contractId <= 0`, `changedBy <= 0`, hoặc `eventType == null || eventType.trim().isEmpty()`.
  - Gợi ý sửa:
    ```java
    public static String truncate(String text, int max) {
        if (text == null || max <= 0) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        return max == 1 ? "…" : text.substring(0, max - 1) + "…";
    }
    ```
    Trong `insertHistory(Connection, ...)`, kiểm tra:
    ```java
    if (conn == null || contractId <= 0 || changedBy <= 0
            || eventType == null || eventType.trim().isEmpty()) {
        throw new SQLException("Invalid contract history parameters");
    }
    ```

- [x] 🟡 `ContractHistoryDAOTest.java` › `findHistoryByContractId_success_mapsAllFieldsAndUser()` & `contractDAO_delegatesHistoryOperations()` (dòng ~336, ~463)
  - Vi phạm: Checklist E (Assertion lỏng, thiếu kiểm tra câu lệnh SQL/tham số bind và ủy quyền ghi) & UC-45.
  - Mô tả:
    1. `findHistoryByContractId_success_mapsAllFieldsAndUser` chỉ kiểm tra việc đọc `ResultSet` giả lập mà chưa `verify(ps).setInt(1, CONTRACT_ID)` và chưa kiểm tra câu SQL thực thi có `WHERE h.contract_id = ?`, `JOIN users u ON u.user_id = h.changed_by` và thứ tự `ORDER BY h.changed_at DESC, h.history_id DESC` (đúng yêu cầu UC-45: mới nhất trước, kèm họ tên người thực hiện).
    2. `contractDAO_delegatesHistoryOperations` mới kiểm tra ủy quyền hàm đọc `findHistoryByContractId`, chưa kiểm tra `ContractDAO` ủy quyền thao tác ghi (`insertHistory` / `insertStatusChange`) sang instance `historyDAO` được inject.
  - Gợi ý sửa:
    ```java
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(conn).prepareStatement(sqlCaptor.capture());
    assertTrue(sqlCaptor.getValue().contains("ORDER BY h.changed_at DESC, h.history_id DESC"));
    verify(ps).setInt(1, CONTRACT_ID);
    ```
    Và bổ sung kiểm tra trong `contractDAO_delegatesHistoryOperations` (hoặc test riêng) xác nhận khi gọi một hàm ghi của `ContractDAO` (như `voidRecord`), `customMock.insertHistory(...)` được gọi.

- [x] 🟢 `ContractDAO.java` › `DETAIL_MAX_LENGTH`, `eventTypeOf()`, `truncate()` (dòng ~111, ~2187, ~2607)
  - Vi phạm: `rules.md` §4 (Clean Code, dọn mã thừa sau khi tách).
  - Mô tả: Sau khi tách sang `ContractHistoryDAO`, trong `ContractDAO.java` vẫn còn hằng `private static final int DETAIL_MAX_LENGTH = 500;` (dòng 111) và hàm `private static String eventTypeOf(String toStatus)` (dòng 2187) không còn được gọi ở bất kỳ đâu trong `ContractDAO`; hàm `private static String truncate(String text, int max)` (dòng 2607) bị lặp lại logic của `ContractHistoryDAO.truncate`.
  - Gợi ý sửa: Xóa `DETAIL_MAX_LENGTH` và `eventTypeOf` trong `ContractDAO.java`; sửa `ContractDAO.truncate` ủy quyền sang `ContractHistoryDAO.truncate(text, max)`.

## Ghi chú về phạm vi (Checklist D)
- Trong working tree hiện có các thay đổi chưa commit của `rules.md`, `.gemini/rules.md`, `test/poscs/dao/ContractDAOTest.java`, `docs/qa/Task-4.2.md`. Khi commit Task 4.3 (sau khi hoàn tất Task 4.2), lưu ý chỉ `git add` đúng các file thuộc phạm vi Task 4.3 (`src/java/poscs/dao/ContractHistoryDAO.java`, `src/java/poscs/dao/ContractDAO.java`, `test/poscs/dao/ContractHistoryDAOTest.java`, `docs/qa/Task-4.3.md`, `task_tracker.md`) theo `rules.md` §6.
