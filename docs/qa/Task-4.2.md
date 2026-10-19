# QA Report - Task 4.2
**Kết luận:** CẦN SỬA (không có lỗi 🔴; 4 lỗi 🟡 về độ phủ/assertion lỏng, 3 lỗi 🟢)
**Kết quả `ant test`:** 1158 pass / 0 fail / 3 skipped (tổng 1161 run, BUILD SUCCESSFUL; 3 skipped là test tích hợp theo rules §0)

**Phạm vi đã xem:** `git diff` của `test/poscs/dao/ContractDAOTest.java` (+586 dòng, khối "Task 4.2", ~20 test mới) đối chiếu `ContractDAO.java` (không đổi, đúng yêu cầu task "chỉ viết test khóa hành vi"). UC-10/11/33/34/35/44, BR-16/17/35/37 trong SPEC (dòng 213–219, 272–293).

## Danh sách lỗi

- [x] 🟡 `ContractDAOTest.java` › `update_signedContract_ignoresLockedFieldChangesInHistoryWhenOwnerUnchanged()` (dòng ~2008)
  - Vi phạm: UC-35 / BR-35 (HĐ đã ký chỉ đổi Người phụ trách); rules §3 (test phải khóa hành vi thật).
  - Mô tả: Tên test nói "bỏ qua thay đổi trường đã khóa" nhưng chỉ kiểm `historyPs` không ghi và `commit()`. Không kiểm câu `UPDATE` thực sự **không** chứa `title`/trường khóa. Nếu sau khi tách DAO có người cho ghi `title` của HĐ đã ký thì test vẫn xanh.
  - Gợi ý sửa:
    ```java
    ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
    verify(conn, atLeastOnce()).prepareStatement(sql.capture());
    assertTrue(sql.getAllValues().stream().filter(q -> q.startsWith("UPDATE contracts"))
            .noneMatch(q -> q.contains("title")));
    ```
    (và thêm ca đối chứng: đổi `owner_id` → có ghi lịch sử).

- [x] 🟡 `ContractDAOTest.java` › nhóm `voidRecord_*` (dòng ~2053–2125)
  - Vi phạm: BR-37 ("kiểm quyền, đánh dấu huỷ và ghi nhật ký trong **một giao dịch**"), rules §4 (xóa mềm, ghi nhiều bảng một giao dịch).
  - Mô tả: Chưa test nào khóa (a) câu lệnh là `UPDATE contracts SET is_deleted = 1` chứ không phải `DELETE`; (b) nhật ký ghi **trước** `commit()`; (c) ghi nhật ký lỗi → `rollback()`, trả `false` (so sánh với `insertProducts_historyFails_rollsBackTheProductRowsToo`). Test cũ `voidRecord_storesTheReasonInNoteNotInDetail` chỉ kiểm `setString(5,...)` và `commit`.
  - Gợi ý sửa: thêm test `voidRecord_historyFails_rollsBackAndReturnsFalse` (`historyPs.executeUpdate()` ném `SQLException` → `verify(conn).rollback(); verify(conn, never()).commit();`) và assert SQL chứa `is_deleted = 1`, không chứa `DELETE FROM contracts`; dùng `InOrder(contractPs, historyPs, conn)`.

- [x] 🟡 `ContractDAOTest.java` › `insert_*` (khối "Tạo ở Nháp", dòng ~1866)
  - Vi phạm: BR-35 ("Ngày ký không nhập tay; hệ thống đóng dấu khi bấm Ký"), UC-33.
  - Mô tả: `insert_alwaysForcesDraftProgressStatusAndLogsCreatedHistory` khóa `progress_status = Nháp` nhưng không có test khóa việc `insert()` **không** lưu `signing_date` do người gọi truyền vào (helper `contract()` luôn set `signingDate = hôm nay`, vậy nên hành vi hiện tại là gì chưa được khóa). Nếu DAO hiện đang ghi `signing_date` từ đối tượng, test cần khóa đúng hành vi đó (hoặc báo sai lệch SPEC cho người dùng); nếu bỏ qua thì ghi chú rõ trong test.
  - Gợi ý sửa: `verify(contractPs, never()).setDate(anyInt(), eq(c.getSigningDate()))` hoặc `setNull` ở vị trí cột tương ứng, kèm comment `// BR-35`.

- [x] 🟡 `ContractDAOTest.java` › nhóm "Bộ lọc mặc định" (dòng ~1590–1745)
  - Vi phạm: UC-10 ("mặc định chỉ thấy HĐ còn hiệu lực trong **tháng hiện tại**; chuyển 'Của tôi / Toàn chi nhánh' và 'Mọi thời điểm'"), task 4.2.
  - Mô tả: Các test dùng `Period.parse("2026","m10")` cố định, tức chỉ khóa việc DAO **nhận** `ListScope`; chưa thấy test khóa việc mặc định = tháng hiện tại (`ListScope.activeIn(Period tháng hiện tại)`) và ca "Mọi thời điểm" (`ListScope.all()` → SQL **không** có mệnh đề `effective_date`/`end_date`). Ca `ListScope.all()` chỉ xuất hiện gián tiếp ở test cũ về tỉnh.
  - Gợi ý sửa: thêm test `findAll_withAllScope_hasNoOwnerNorActiveWindowClause` (`assertFalse(sql.contains("c.effective_date"))`, `assertFalse(sql.contains("c.owner_id IN"))`) và, nếu mặc định nằm ở `ListScope`/controller, một test khóa mặc định tháng hiện tại ở đó (nếu đã có test sẵn thì ghi tham chiếu vào report khi fix).

- [x] 🟢 `ContractDAOTest.java` › `insert_alwaysForcesDraft...()` (dòng ~1880)
  - Vi phạm: rules §4 (test dễ vỡ).
  - Mô tả: dùng số cứng `setString(11, ...)` trong khi file đã có hằng `STATUS_PARAM_INDEX = 10` cho cột liền kề. Thêm cột sẽ phải sửa nhiều chỗ.
  - Gợi ý sửa: khai báo `PROGRESS_PARAM_INDEX = STATUS_PARAM_INDEX + 1` cùng chỗ với hằng cũ.

- [x] 🟢 `ContractDAOTest.java` › các test BR-17 dùng `LocalDate.now()` (dòng ~1762–1800)
  - Vi phạm: rules §4 (độ ổn định test).
  - Mô tả: test và DAO mỗi bên gọi `LocalDate.now()`; chạy đúng lúc qua nửa đêm có thể lệch một ngày (các ca biên 30 ngày, "hết hạn hôm nay"). Rủi ro nhỏ.
  - Gợi ý sửa: ghi chú rủi ro trong Javadoc hoặc tính biên bằng `now ± 1` quanh ngưỡng khi có thể.

- [ ] 🟢 `ContractDAOTest.java` (toàn file, 2133 dòng)
  - Vi phạm: rules §4 (file > 250 dòng nên tách).
  - Mô tả: Task này làm file phình thêm ~590 dòng; theo kế hoạch tách DAO (Task 4.3+) nên tách test theo module khi tách DAO, không cần làm ở task này.
  - Gợi ý sửa: tách `ContractDAOFilterTest` / `ContractDAOProgressTest` ở task tách tương ứng.
  - Lý do không sửa ở Task 4.2: Như ghi chú trong chính mục QA và lộ trình ở `task_tracker.md`, Task 4.2 chỉ bổ sung test khóa hành vi vào `ContractDAOTest.java` trước khi tách; việc tách test theo từng DAO con (`ContractHistoryDAOTest`, `ContractPaymentDAOTest`, `ContractDocumentDAOTest`, `ContractLinkDAOTest`, `ContractHandoverDAOTest`, ...) sẽ được thực hiện đồng bộ ở các Task 4.3 → 4.9.

## Ghi chú về phạm vi (D)
- `git status` còn `M rules.md` và `M .gemini/rules.md` — **ngoài phạm vi Task 4.2**. Không được `git add` hai file này vào commit của task (rules §6: chỉ add từng file của task).
- Không sửa `src/` (đúng yêu cầu), không đổi chữ ký public, không placeholder/`TODO`.

## Điểm đã kiểm tra tốt
- BR-17: có đủ ca Chưa hiệu lực / Đang hiệu lực / Sắp hết hạn (biên 30 ngày, hôm nay) / Đã hết hạn / thiếu 1 hoặc 2 ngày; `findById` và `findByEnterpriseId` tính lại từ ngày chứ không tin cột `status` cũ.
- BR-16/UC-10: kiểm SQL và thứ tự bind tham số cho "Của tôi" (owner OR tỉnh), cửa sổ hiệu lực cho phép NULL, kết hợp mọi bộ lọc, đồng bộ `findAll`/`countAll`/`countStatusSummary`.
- BR-35/UC-44: tạo luôn ở Nháp + nhật ký "Khởi tạo"; ký phải đủ cả hai ngày (có ca thiếu một ngày), không ký lại/quay Nháp/nhảy Nháp→Thanh lý; HĐ đã đóng băng không sửa được.
- BR-37 phần lý do trống/null, HĐ không tồn tại, HĐ còn phụ lục (test cũ dòng ~1027), điều chỉnh giá trị khi huỷ phụ lục đã ký / không điều chỉnh khi phụ lục nháp.
- Lưu ý: `MSG-043`/`MSG-022` và kiểm quyền Admin/quản lý vùng thuộc `ContractService`/controller (Task 4.10/4.12); DAO chỉ trả `boolean` nên không đánh giá ở task này.
