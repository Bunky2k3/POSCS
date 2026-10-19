# QA Report - Task 4.4
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1208/0/3 (1208 pass / 0 fail / 3 skipped tích hợp theo rules §0)

## Danh sách lỗi

- [x] 🟡 `ContractPaymentDAO.java` › `findById(Connection, int)`, `findById(int)`, `findByContractId(int)` & các hàm overload/helper `public` thừa (dòng ~77–177, ~182, ~245, ~303, ~359, ~486–505)
  - Vi phạm: `rules.md` §1, §3 ("Chỉ tách, không thêm tính năng"), §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ" — file hiện dài **533 dòng**) & NFR-19.
  - Mô tả:
    1. Trong `ContractDAO` gốc hoàn toàn không có `findById(int)`, `findById(Connection, int)`, `findByContractId(int)` và không có bất kỳ nơi nào trong codebase gọi các hàm này. Việc tự thêm hàm mới ngoài thiết kế gốc vi phạm nguyên tắc "Chỉ tách, không thêm tính năng" của Giai đoạn 4 và làm file phình lên 533 dòng (vượt xa ngưỡng 250 dòng tại `rules.md` §4).
    2. Các hàm `insertPayment(Connection, ...)`, `markPaymentPaid(Connection, ...)`, `deletePayment(Connection, ...)`, `findPaymentsByContractId(Connection, ...)`, `sumScheduledPaymentsForCluster(Connection, ...)` cùng các helper nội bộ `lockPayment`, `isFrozen`, `lockProgressStatus` được mở `public` nhưng không có caller bên ngoài nào sử dụng; hơn nữa các hàm ghi nhận `Connection` trả `boolean` thay vì quản lý transaction trọn gói dễ dẫn đến lỗi quên rollback nếu bị gọi rời sau này.
  - Gợi ý sửa: Xóa `findById(Connection, int)`, `findById(int)`, `findByContractId(int)`; gộp các overload nhận `Connection` vào hàm chính (hoặc để `private` như `lockPayment`, `isFrozen`, `lockProgressStatus` trong `ContractDAO` gốc) để giữ đúng hành vi trước khi tách và rút gọn file về quanh mốc ~250 dòng.

- [x] 🟡 `ContractPaymentDAO.java` › `sumInvoiceAmountByMonth(int, int, List<Integer>, List<Integer>)` (dòng ~454–481)
  - Vi phạm: Checklist B (An toàn đầu vào).
  - Mô tả: Khác với `sumInvoiceAmountInPeriod` có kiểm tra `if (period == null) return BigDecimal.ZERO;` trước khi mở kết nối CSDL, hàm `sumInvoiceAmountByMonth` không kiểm tra miền giá trị của `year` và `month` (ví dụ `year <= 0 || month < 1 || month > 12`), dẫn đến vẫn lấy `Connection` từ `DBContext` và thực thi truy vấn SQL khi đầu vào không hợp lệ.
  - Gợi ý sửa:
    ```java
    if (year <= 0 || month < 1 || month > 12) {
        return BigDecimal.ZERO;
    }
    ```

- [x] 🟡 `ContractDAO.java` › `ContractDAO()` & `ContractDAO(ContractPaymentDAO)` (dòng ~99–114)
  - Vi phạm: `rules.md` §4 (Clean Code & tính nhất quán Dependency Injection).
  - Mô tả: Constructor mặc định `ContractDAO()` gọi `this(new ContractHistoryDAO(), new ContractPaymentDAO())`, tạo ra hai instance `ContractHistoryDAO` riêng biệt (một trong `ContractDAO.historyDAO` và một khác nằm trong `ContractPaymentDAO`). Tương tự, `ContractDAO(ContractPaymentDAO paymentDAO)` cũng khởi tạo `new ContractHistoryDAO()` mới thay vì dùng chung `paymentDAO.getHistoryDAO()`.
  - Gợi ý sửa:
    ```java
    public ContractDAO() {
        this(new ContractHistoryDAO());
    }

    public ContractDAO(ContractPaymentDAO paymentDAO) {
        this(paymentDAO != null ? paymentDAO.getHistoryDAO() : new ContractHistoryDAO(), paymentDAO);
    }
    ```

- [x] 🟡 `ContractPaymentDAOTest.java` › `sumInvoiceAmountInPeriod_scenarios()`, `sumInvoiceAmountByMonth_scenarios()`, `insertPayment_contractFrozen_returnsFalse()`, `deletePayment_contractFrozen_returnsFalse()` (dòng ~310, ~550, ~692, ~723)
  - Vi phạm: Checklist E (Assertion quá lỏng, thiếu kiểm tra tham số SQL bind và rollback giao dịch).
  - Mô tả:
    1. Trong `sumInvoiceAmountByMonth_scenarios` (dòng 723–746), khi gọi các overload lọc theo `provinceId` và `(provinceIds, ownerIds)`, test chỉ kiểm tra kết quả trả về từ `ResultSet` giả lập mà không kiểm tra câu SQL có chứa điều kiện `YEAR(p.paid_date) = ? AND MONTH(p.paid_date) = ?`, `c.direction = 'Bán'`, `c.is_deleted = 0`, mệnh đề scope `c.owner_id` / `d.province_id`, và không `verify(ps).setInt(...)` cho các tham số `ownerIds`, `provinceIds`. Tương tự ở `sumInvoiceAmountInPeriod_scenarios` (dòng 703), truyền `provinceId = 5` nhưng chưa `verify(ps).setInt(3, 5)`.
    2. Trong `insertPayment_contractFrozen_returnsFalse` (dòng 310) và `deletePayment_contractFrozen_returnsFalse` (dòng 550), test mới gọi hàm nhận `Connection` nên chưa xác nhận `conn.rollback()` được gọi và `conn.commit()` không được gọi khi hợp đồng ở trạng thái `Đã thanh lý` hoặc `Chấm dứt sớm`.
  - Gợi ý sửa:
    ```java
    // Trong sumInvoiceAmountInPeriod_scenarios:
    assertTrue(sql.contains("d.province_id IN (?)"));
    verify(ps).setInt(3, 5);

    // Trong insertPayment_contractFrozen_returnsFalse (gọi qua insertPayment(int, ContractPayment, int)):
    assertFalse(dao.insertPayment(CONTRACT_ID, p, ACTOR_ID));
    verify(conn, never()).commit();
    verify(conn).rollback();
    verify(conn).setAutoCommit(true);
    ```

- [x] 🟢 `ContractDAO.java` › `DETAIL_DATE_FORMAT`, `formatDate(Date)` (dòng ~125, ~2609)
  - Vi phạm: `rules.md` §4 (Clean Code, dọn mã trùng lặp sau khi tách).
  - Mô tả: Trong khi `ContractDAO.formatMoney` và `ContractDAO.truncate` đã ủy quyền sang DAO con, `ContractDAO` vẫn giữ lại hằng `DETAIL_DATE_FORMAT` (dòng 125) và logic `formatDate(Date)` (dòng 2609) trùng lặp với `ContractPaymentDAO.formatDate(Date)`.
  - Gợi ý sửa: Xóa `DETAIL_DATE_FORMAT` trong `ContractDAO.java` và sửa `formatDate(Date date)` gọi `return ContractPaymentDAO.formatDate(date);`.
