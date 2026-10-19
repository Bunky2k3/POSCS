# QA Report - Task 4.6
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1255/0/3 (1255 pass / 0 fail / 3 skipped tích hợp theo rules §0)

## Danh sách lỗi

- [x] 🟡 `ContractLinkDAO.java` › `unlinkPair(int, int, int)`, `findLinkById(int)`, `isLinked(int, int)`, `countLinksOf(int)` & `linkContracts(int, int, String, String, int)` (dòng ~163–276, ~281–300, ~306–346, ~351–425, ~459–500)
  - Vi phạm: `rules.md` §1 ("Luồng chi tiết UC-36 → UC-54: lấy hành vi code và JSP hiện có làm chuẩn, chỉ tách nhỏ theo rules §3, không thêm tính năng"), `task_tracker.md` Giai đoạn 4 ("Chỉ **tách**, không thêm tính năng"), `rules.md` §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ" — file hiện dài **502 dòng**) & NFR-16.
  - Mô tả:
    1. Trong `ContractDAO` gốc chỉ có 5 phương thức phục vụ UC-47: `linkContracts(int, int, String, int)`, `canLink(Contract, Contract)`, `unlinkContracts(int, int)`, `findLinksOf(int)` (kèm helper `mapLinkRow`), và `sumLinkedBuyValue(int)`. Việc tự thêm `unlinkPair`, `findLinkById`, `isLinked`, `countLinksOf` và overload 5 tham số `linkContracts(..., relationType, ...)` không có bất kỳ nơi nào trong toàn bộ codebase (`src/`, `web/`) gọi tới, vi phạm nguyên tắc "Chỉ tách, không thêm tính năng" của Giai đoạn 4 và làm file phình lên **502 dòng** (gấp đôi ngưỡng 250 dòng tại `rules.md` §4).
    2. Riêng hàm tự thêm `unlinkPair` (dòng 286–294) mở một `Connection` trong khối `try-with-resources`, rồi ngay bên trong khối đó lại gọi `unlinkContracts(linkId, actorId)` (vốn mở thêm một `Connection` thứ hai từ `DBContext`). Giữ 2 kết nối lồng nhau trên cùng 1 luồng tạo nguy cơ cạn kiệt pool (deadlock bể kết nối tối đa 10 kết nối theo NFR-16).
    3. Có 4 hàm vượt ngưỡng 40 dòng (`rules.md` §4): `linkContracts` (55 dòng, dòng 163–217), `unlinkContracts` (55 dòng, dòng 222–276), `findLinksOf` (41 dòng, dòng 306–346), `mapLinkRow` (42 dòng, dòng 459–500).
  - Gợi ý sửa:
    - Xóa các hàm tự thêm không có caller (`unlinkPair`, `findLinkById`, `isLinked`, `countLinksOf`, và gộp overload `linkContracts` về đúng chữ ký 4 tham số `linkContracts(int sellContractId, int buyContractId, String note, int actorId)` như `ContractDAO` gốc, để DB tự gán `DEFAULT 'Đầu vào phục vụ đầu ra'` hoặc truyền hằng mặc định).
    - Tách các bước SQL trong `linkContracts` và `unlinkContracts` thành helper `private` (như `insertLinkRow`, `lockLinkForUnlink`, `deleteLinkRow` tương tự cấu trúc của `ContractDocumentDAO` ở Task 4.5) để mọi hàm đều ≤ 40 dòng và tổng độ dài file rút về ~250 dòng.

- [x] 🟡 `ContractLinkDAOTest.java` › `testFindLinksOf_SuccessMapping()`, `testSumLinkedBuyValue_Scenarios()`, `testLinkContracts_SellOrBuyNotFoundOrInvalidDirection()` (dòng ~179–218, ~592–667, ~806–844)
  - Vi phạm: Checklist E (Unit test: thiếu kiểm tra câu lệnh SQL được thực thi và thiếu kịch bản nhánh `parent_contract_id != null` trong `lockContractForLink`).
  - Mô tả:
    1. Trong `testFindLinksOf_SuccessMapping` (dòng 628) và `testSumLinkedBuyValue_Scenarios` (dòng 816), test dùng `when(conn.prepareStatement(anyString())).thenReturn(ps)` mà không kiểm tra câu lệnh SQL được tạo có chứa đúng các điều kiện cốt lõi của UC-47: lọc `o.is_deleted = 0` / `b.is_deleted = 0`, loại trừ phụ lục nháp (`a.progress_status <> 'Nháp'`), đếm `shared_count`, và thứ tự `ORDER BY l.created_at, l.link_id`.
    2. Trong `lockContractForLink` (`ContractLinkDAO.java` dòng 142–143), nhánh `rs.wasNull() == false` khi đọc `parent_contract_id` chưa được kiểm thử trong `linkContracts`: mọi test của `linkContracts` đều truyền `"parent_contract_id", null` trong `ResultSet` giả lập, chưa có kịch bản từ chối khi một trong hai hợp đồng trong DB là phụ lục (`parent_contract_id` khác null) hoặc khi hợp đồng bán tồn tại nhưng hợp đồng mua không tồn tại.
  - Gợi ý sửa:
    ```java
    // Trong testFindLinksOf_SuccessMapping:
    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(conn).prepareStatement(sqlCaptor.capture());
    assertTrue(sqlCaptor.getValue().contains("WHERE (l.sell_contract_id = ? OR l.buy_contract_id = ?) AND o.is_deleted = 0"));
    assertTrue(sqlCaptor.getValue().contains("ORDER BY l.created_at, l.link_id"));

    // Trong testSumLinkedBuyValue_Scenarios:
    ArgumentCaptor<String> sumSqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(conn).prepareStatement(sumSqlCaptor.capture());
    assertTrue(sumSqlCaptor.getValue().contains("WHERE l.sell_contract_id = ? AND b.is_deleted = 0"));

    // Bổ sung kịch bản phụ lục (parent_contract_id = 100) trong testLinkContracts:
    ResultSet rsBuyAmendment = resultSetOf(List.of(
            row("contract_id", BUY_ID, "contract_code", "PL-01", "direction", "Mua", "parent_contract_id", 100)
    ));
    ```

- [x] 🟢 `ContractLinkDAO.java` & `ContractDAO.java` › Tái sử dụng tiện ích chung và dọn mã trùng lặp (dòng ~72–99, ~490–498 `ContractLinkDAO.java`; dòng ~105–134, ~1622, ~2395 `ContractDAO.java`)
  - Vi phạm: `rules.md` §3 ("Không đổi chữ ký của public API") & §4 (Clean Code, tránh trùng lặp mã nguồn giữa các DAO cùng gói).
  - Mô tả:
    1. `ContractLinkDAO` viết lại `trimToNull` và `truncate` (dòng 72–85) và tự dựng `User` để lấy họ tên (dòng 490–498) trong khi `ContractDocumentDAO.trimToNull`, `ContractDocumentDAO.truncate`, `ContractDocumentDAO.fullName` đã có sẵn `public static` trong cùng gói `poscs.dao`.
    2. `ContractDAO.isDuplicateKeyError(SQLException, String)` (dòng 2395) vẫn giữ logic riêng thay vì ủy quyền sang `ContractLinkDAO.isDuplicateKeyError(ex, keyColumn)`.
    3. `ContractDAO.mapLinkRow(ResultSet)` (dòng 1622) vốn là `private` trong `ContractDAO` gốc nhưng bị đổi thành `public static` trên `ContractDAO` dù không có caller bên ngoài nào gọi qua `ContractDAO`.
    4. Các constructor `ContractDAO(ContractHistoryDAO)`, `ContractDAO(ContractHistoryDAO, ContractPaymentDAO)`, `ContractDAO(ContractHistoryDAO, ContractPaymentDAO, ContractDocumentDAO)` (dòng 105–127) tự `new` các DAO phụ thuộc trước khi vào constructor 4 tham số; nếu truyền `historyDAO = null` sẽ tạo ra nhiều instance `ContractHistoryDAO` khác nhau thay vì truyền `null` để constructor 4 tham số khởi tạo tập trung từ `this.historyDAO`.
  - Gợi ý sửa:
    ```java
    // Trong ContractLinkDAO.mapLinkRow:
    link.setCreatedByName(ContractDocumentDAO.fullName(rs, "last_name", "middle_name", "first_name"));

    // Trong ContractDAO:
    public ContractDAO(ContractHistoryDAO historyDAO) {
        this(historyDAO, null, null, null);
    }

    private boolean isDuplicateKeyError(SQLException ex, String keyColumn) {
        return ContractLinkDAO.isDuplicateKeyError(ex, keyColumn);
    }
    ```
