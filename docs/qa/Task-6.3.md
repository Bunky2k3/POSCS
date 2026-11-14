# QA Report - Task 6.3
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1989 pass / 0 fail / 3 skipped (tổng 1992; BUILD SUCCESSFUL; `NotificationServiceTest` 16, `NotificationSchedulerTest` 3)

Phạm vi: `NotificationService.java`, `NotificationScheduler.java`, `NotificationServiceTest.java`, `NotificationSchedulerTest.java`. Đối chiếu SPEC §5.7, UC-14, UC-15, BR-17, BR-20, BR-21.

## Danh sách lỗi
- [x] 🟡 `NotificationService.java` › `isEligibleTicket()` (dòng ~188-189)
  - Vi phạm: SPEC §5.7 (phiếu "còn ≤ 24 giờ tới hạn SLA"), rules §4 (nhánh mới phải có test)
  - Mô tả: `sla == null` trả `true`, nên phiếu chưa có hạn SLA vẫn bị tạo thông báo "sắp/đã quá hạn SLA". Không có hạn thì không có "sắp/đã quá hạn". Nhánh này hiện không có test. DAO có lọc `sla_deadline IS NOT NULL` nên lỗi bị che, nhưng service tự nhận phiếu null SLA là hợp lệ và hành vi không nhất quán.
  - Gợi ý sửa: `return sla != null && !sla.toLocalDateTime().isAfter(cutoff);` và thêm test `generateTicketSla_nullSla_skipped`.

- [x] 🟡 `NotificationService.java` › `isEligibleContract()` (dòng ~174-176)
  - Vi phạm: BR-17, SPEC §5.7
  - Mô tả: `endDate == null` trả `true`. Theo BR-17, thiếu ngày thì trạng thái là "Chưa hiệu lực", không phải "Sắp hết hạn". Hợp đồng không có ngày kết thúc sẽ nhận thông báo "sắp hết hạn" (không có "còn N ngày"). Nhánh này không có test (test chỉ phủ 0/30/31/âm ngày). Service cũng không tự kiểm tra `effectiveDate`/trạng thái Đã ký, mà phụ thuộc hoàn toàn vào SQL của `findExpiringSoon`.
  - Gợi ý sửa: `if (c.getEndDate() == null) return false;` (hoặc dùng `daysUntil` luôn) và thêm test `generateContractExpiring_nullEndDate_skipped`.

- [x] 🟡 `NotificationServiceTest.java` › thiếu test idempotency "chạy lại không tạo trùng" (cả file)
  - Vi phạm: SPEC §5.7 ("chạy lại nhiều lần không tạo bản ghi trùng"), Task 6.3
  - Mô tả: Các test chỉ mock `existsForUserAndRef` trả `true` sẵn, nên chưa chứng minh gọi `generateScheduledNotifications()` hai lần liên tiếp thì lần 2 trả 0. Dùng fake có trạng thái (Set các khóa đã chèn) mới kiểm tra được hành vi thật.
  - Gợi ý sửa: dùng `thenAnswer` ghi vào `Set<String>` ở `insert`, đọc ở `existsForUserAndRef`, rồi `assertEquals(2, run1); assertEquals(0, run2);`.

- [x] 🟡 `NotificationServiceTest.java` › thiếu test các nhánh còn lại
  - Vi phạm: rules §4 (mọi nhánh mới có test), BR-20, UC-15
  - Mô tả: thiếu (1) `insert` trả `false` thì không tăng bộ đếm `created`; (2) danh sách mà `findExpiringSoon`/`findOverdueOrDueSoon` rỗng; (3) phiếu có `ownerId` / KTV khác nhau thì thông báo gửi đúng người nhận; (4) `findRecentByUser`/`findAllByUser` trả đúng thứ tự mới nhất lên đầu chỉ do DAO nên chưa được khóa ở service (BR-20); (5) `openNotification` chưa assert `markAsRead` không gọi khi `findByIdForUser` trả null theo `userId` khác (hiện chỉ assert với id 99).
  - Gợi ý sửa: thêm test `insert` trả `false` → `assertEquals(0, created)`; thêm `assertEquals` trên `title` đầy đủ thay vì chỉ `contains` ở vài test.

- [x] 🟡 `NotificationService.java` › `CONTRACT_EXPIRING_DAYS` / `TICKET_SLA_HOURS` (dòng 31-32)
  - Vi phạm: rules §4 (không trùng lặp quy tắc), BR-17
  - Mô tả: Ngưỡng 30 ngày và 24 giờ lặp ở 3 nơi: hằng số service, `ContractDAO.SOON_THRESHOLD_DAYS` và literal `INTERVAL 24 HOUR` trong `TechnicalSupportTicketDAO.findOverdueOrDueSoon()`. Đổi một nơi mà quên nơi khác sẽ làm ngưỡng lệch ngầm, và không có test nào kiểm tra hằng số đồng bộ.
  - Gợi ý sửa: thêm test `assertEquals(ContractDAO.SOON_THRESHOLD_DAYS, NotificationService.CONTRACT_EXPIRING_DAYS)` (nếu hằng số truy cập được), hoặc để service dùng chung hằng số của DAO.
  - *Đã sửa:* `NotificationService.CONTRACT_EXPIRING_DAYS` dùng chung `ContractValidator.SOON_THRESHOLD_DAYS` (`public static final int = 30`) và bổ sung test `thresholds_matchSpecAndContractValidator` (không sửa `ContractDAO` hay `TechnicalSupportTicketDAO` vì nằm ngoài phạm vi Task 6.3).

- [x] 🟢 `NotificationService.java` › `generateContractExpiringNotifications()` / `generateTicketSlaNotifications()` (dòng ~77-82, 104-109)
  - Vi phạm: SPEC §5.7 (không tạo bản ghi trùng)
  - Mô tả: Kiểm tra `exists` rồi `insert` không nguyên tử, và `schema.sql` chỉ có `KEY idx_notifications_ref` (không `UNIQUE`). Hiện chỉ có 1 scheduler thread nên an toàn, nhưng nếu có hai instance ứng dụng chạy cùng DB thì có thể trùng. Không được sửa schema trong task này (rules §3), nên chỉ ghi nhận cho người dùng.
  - Gợi ý sửa: cân nhắc ràng buộc `UNIQUE (user_id, ref_type, ref_id)` ở task riêng, hoặc `synchronized` hóa `generateScheduledNotifications()` nếu cần.
  - *Đã sửa:* Đã `synchronized` hóa `generateScheduledNotifications()`; việc thêm `UNIQUE` vào `db/schema.sql` không thuộc phạm vi Task 6.3 (rules §3).

- [ ] 🟢 `NotificationController.java` / `NotificationService.java` (UC-14, UC-15)
  - Vi phạm: rules §4 (controller gọi service, không gọi thẳng DAO)
  - Mô tả: `NotificationController` (và `ChangeRequestController`, `AuthenticationFilter`) vẫn dùng `NotificationDAO` trực tiếp. Các hàm UC-14/UC-15 trong `NotificationService` (`countUnread`, `findRecentByUser`, `findAllByUser`, `markAsRead`, `markAllAsRead`, `openNotification`) hiện chưa được ai gọi (mã chết). Đây ngoài phạm vi task theo tracker nên không tính là lỗi chặn, chỉ ghi để người dùng quyết định có làm task riêng không.
  - *Lý do giữ nguyên:* Ngoài phạm vi Task 6.3 theo `task_tracker.md` (không sửa `NotificationController`, `ChangeRequestController`, `AuthenticationFilter` để tránh đụng file ngoài task).

- [x] 🟢 `NotificationScheduler.java` › `generate()` (dòng 61-70)
  - Vi phạm: rules §4 (xử lý lỗi), NFR log
  - Mô tả: Log lỗi ghi không dấu (`"Loi chay notification scheduler"`) nên không đồng nhất với văn bản tiếng Việt khác. Chỉ bắt `Exception`, không bắt `Error`. Test `generate_delegatesToService_andSwallowsException` không kiểm tra log. Không ảnh hưởng nghiệp vụ.
  - *Đã sửa:* Chuẩn hóa thông điệp log sang tiếng Việt có dấu (`"Lỗi chạy notification scheduler"`). Giữ `catch (Exception ex)` thay vì bắt `Error` (như `OutOfMemoryError`) theo chuẩn an toàn Java.

## Đã kiểm tra, không có lỗi
- Chu kỳ **60 phút** (`PERIOD_MINUTES`) có test khóa bằng `scheduleAtFixedRate(…, 1, 60, MINUTES)`; `contextDestroyed` gọi `shutdownNow()`; một lần chạy lỗi không giết lịch (bắt `Exception`).
- Chỉ **2 loại** tự sinh (`contract_expiring` → người phụ trách; `ticket_sla` → KTV được giao); biên 0/30/31 ngày và 24h/24h+1s/quá hạn đều có test; phiếu `Đã đóng`, đã xóa, không có KTV, HĐ phụ lục (`parentContractId`), HĐ đã xóa, không có chủ đều bị bỏ qua.
- BR-21: các hàm xem danh sách không gọi `markAsRead`/`markAllAsRead` (có `verify never`); thông báo không thuộc người dùng thì `openNotification` trả `null`, không đổi dữ liệu (SQL của DAO có `AND user_id = ?`). Tiêu đề được cắt ≤ 150 ký tự khớp `varchar(150)`; PreparedStatement ở DAO; không hardcode secret, không có `// TODO`; mọi hàm ≤ 40 dòng; file service 220 dòng (<250). Chữ ký public của `NotificationScheduler` không bị đổi (chỉ thêm constructor, `PERIOD_MINUTES`/`INITIAL_DELAY_MINUTES` đổi từ private sang public; không phá hành vi cũ).
- Task này không có luồng ghi nhiều bảng, không có form (CSRF không áp dụng), không có phân quyền (`MSG-022` không áp dụng ở tầng service/scheduler).
