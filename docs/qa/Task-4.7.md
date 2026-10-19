# QA Report - Task 4.7
**Kết luận:** ĐÃ SỬA
**Kết quả `ant test`:** 1287/0/3 (1287 pass / 0 fail / 3 skipped tích hợp theo rules §0)

## Danh sách lỗi

- [x] 🔴 `ContractHandoverDAO.java` › `handOverToDepartments(int, List<Integer>, String, int)` & `insertHandoverRow(...)` (dòng ~125–134, ~199–243)
  - Vi phạm: UC-48 ("Chặn giao lại khi phòng đó đang còn giữ hợp đồng chưa xử lý"), Checklist B (An toàn đầu vào với phần tử trong `departmentIds`) & Checklist C (`rules.md` §4: hàm ≤ 40 dòng — hàm hiện dài **45 dòng**).
  - Mô tả:
    1. **Lỗ hổng chặn giao trùng phòng trong cùng lô (`departmentIds` chứa ID trùng lặp):** Vòng lặp kiểm tra `hasPendingHandover` (dòng 217–221) chạy tách biệt trước vòng lặp `insertHandoverRow` (dòng 227–231), và bảng `contract_handovers` trong `db/schema.sql` không có ràng buộc UNIQUE chặn 2 dòng `done_at IS NULL`. Nếu truyền `departmentIds` có phần tử trùng (ví dụ `List.of(1, 1)` từ request gửi lặp `departmentId=1&departmentId=1`), vòng lặp đầu kiểm tra CSDL khi chưa có bản ghi nào nên cả 2 lần đều trả `false`, sau đó vòng lặp thứ hai sẽ `INSERT` **2 chặng mở cùng lúc cho cùng 1 phòng ban** trong cùng một transaction, phá vỡ quy tắc UC-48.
    2. **Chưa kiểm tra phần tử `null` hoặc `<= 0` trong `departmentIds`:** Nếu `departmentIds` chứa phần tử `null` (ví dụ `Arrays.asList(1, null)`), lời gọi `hasPendingHandover(conn, contractId, departmentId)` tự động unbox sang `int` và ném `NullPointerException` ngay giữa transaction đang mở thay vì trả về `HANDOVER_FAILED`. Nếu chứa `departmentId <= 0` (ví dụ `-1` / `WAITING_ANY_DEPARTMENT` hoặc `0`), hàm vẫn mở kết nối DB và chạy tới câu lệnh `INSERT` gây lỗi khóa ngoại thay vì từ chối sớm.
    3. **Bỏ qua kết quả `executeUpdate()` trong `insertHandoverRow` (dòng 132):** Không kiểm tra `ps.executeUpdate() > 0` trước khi ghi lịch sử `contract_history`.
    4. **Độ dài hàm vượt ngưỡng:** `handOverToDepartments` dài 45 dòng (dòng 199–243), vượt giới hạn 40 dòng tại `rules.md` §4.
  - Gợi ý sửa:
    - Tách hàm kiểm tra/khử trùng lặp hoặc chặn danh sách `departmentIds` không hợp lệ trước khi mở kết nối DB (từ chối trả `HANDOVER_FAILED` nếu có phần tử `null`, `<= 0`, hoặc trùng lặp phòng ban trong cùng danh sách; hoặc dùng `LinkedHashSet` / helper validate để rút gọn thân hàm `handOverToDepartments` xuống ≤ 40 dòng).
    ```java
    private boolean hasInvalidOrDuplicateDepartments(List<Integer> departmentIds) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Integer id : departmentIds) {
            if (id == null || id <= 0 || !seen.add(id)) {
                return true;
            }
        }
        return false;
    }
    ```
    - Đổi `insertHandoverRow` trả về `boolean` (`return ps.executeUpdate() > 0;`) và nếu `!insertHandoverRow(...)` thì trả về `HANDOVER_FAILED` (để `finally` tự rollback).
  - *Đã sửa (Dev):* Đã bổ sung `hasInvalidOrDuplicateDepartments` từ chối phần tử `null`, `<= 0` hoặc trùng lặp trong `departmentIds`, kiểm tra `insertHandoverRow(...) > 0`, rút gọn thân hàm `handOverToDepartments` xuống 38 dòng (≤ 40 dòng).

- [x] 🟡 `ContractHandoverDAOTest.java` › `testHandOverToDepartments_*` & `testCompleteHandover_*` (dòng ~104–120, ~188–273, ~406–450)
  - Vi phạm: Checklist E (Unit test: thiếu kịch bản biên/ngoại lệ cho `departmentIds` và assertion chưa kiểm tra tham số SQL / side-effect rollback).
  - Mô tả:
    1. Chưa có test cho trường hợp `departmentIds` chứa phần tử `null`, `departmentId <= 0`, hoặc chứa 2 ID phòng ban trùng nhau (`List.of(DEPT_ACCOUNTING, DEPT_ACCOUNTING)`), cũng như trường hợp giao nhiều phòng mà phòng thứ 2 đang pending (`List.of(DEPT_ACCOUNTING, DEPT_PROJECT)` với phòng 2 trả `true` ở `hasPendingHandover` -> phải rollback toàn bộ, không gọi `psInsert`).
    2. Trong `testHandOverToDepartments_Success_MultipleDepartments_AtomicTransaction` (dòng 260), mới chỉ `verify(psInsert, times(2)).executeUpdate()` mà chưa kiểm tra các tham số được bind vào `psInsert` (`setInt(1, CONTRACT_ID)`, `setInt(2, DEPT_ACCOUNTING)`, `setInt(2, DEPT_PROJECT)`, `setInt(3, ACTOR_ID)`, `setString(4, "Soạn xong nhờ kiểm")`).
    3. Trong `testCompleteHandover_NotFoundOrAlreadyDone` (dòng 406–423) và `testCompleteHandover_UpdateFails` (dòng 426–450), chưa verify `historyDAO.insertHistory(...)` không được gọi và `conn.commit()` không được gọi khi thao tác thất bại.
  - Gợi ý sửa:
    ```java
    // Bổ sung trong testHandOverToDepartments_InvalidArguments:
    assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
            dao.handOverToDepartments(CONTRACT_ID, java.util.Arrays.asList(DEPT_ACCOUNTING, null), "ghi chu", ACTOR_ID));
    assertEquals(ContractHandoverDAO.HANDOVER_FAILED,
            dao.handOverToDepartments(CONTRACT_ID, List.of(0), "ghi chu", ACTOR_ID));
    assertEquals(ContractHandoverDAO.HANDOVER_ALREADY_PENDING, // hoặc HANDOVER_FAILED tùy thiết kế khử/chặn trùng
            dao.handOverToDepartments(CONTRACT_ID, List.of(DEPT_ACCOUNTING, DEPT_ACCOUNTING), "ghi chu", ACTOR_ID));

    // Bổ sung verify tham số psInsert trong testHandOverToDepartments_Success_MultipleDepartments_AtomicTransaction:
    verify(psInsert, times(2)).setInt(1, CONTRACT_ID);
    verify(psInsert).setInt(2, DEPT_ACCOUNTING);
    verify(psInsert).setInt(2, DEPT_PROJECT);
    verify(psInsert, times(2)).setInt(3, ACTOR_ID);
    verify(psInsert, times(2)).setString(4, "Soạn xong nhờ kiểm");
    ```
  - *Đã sửa (Dev):* Đã bổ sung toàn bộ test case kiểm tra `null`, `<= 0`, trùng ID trong `departmentIds`; test rollback và không insert khi phòng thứ 2 pending; test rollback khi insert thất bại; verify chi tiết các tham số SQL bind vào `psInsert`; verify không gọi `insertHistory` và không commit khi `completeHandover` thất bại.

- [x] 🟢 `ContractDAO.java` › `mapHandoverRow(ResultSet)` (dòng ~1360–1362)
  - Vi phạm: `rules.md` §4 (Clean Code, không để lại hàm `private` chết sau khi tách DAO).
  - Mô tả: Phương thức `private static ContractHandover mapHandoverRow(ResultSet rs)` trong `ContractDAO.java` (dòng 1360–1362) không còn bất kỳ nơi nào trong `ContractDAO` gọi tới sau khi `findHandoversOf` và `findPendingHandovers` đã ủy quyền sang `ContractHandoverDAO`.
  - Gợi ý sửa: Xóa hàm `private static ContractHandover mapHandoverRow(ResultSet rs)` không còn sử dụng trong `ContractDAO.java`.
  - *Đã sửa (Dev):* Đã xóa phương thức `private static ContractHandover mapHandoverRow(ResultSet rs)` thừa trong `ContractDAO.java`.
