# QA Report - Task 5.2
**Kết luận:** ĐÃ SỬA (Sẵn sàng nghiệm thu)
**Kết quả `ant test`:** 1814 pass / 0 fail / 3 skipped (tổng 1817; 3 skipped là test tích hợp `_it`, không chạy DB thật theo rules §0). `TicketServiceTest`: 28/28 pass.

**Phạm vi review:**
- File code: `src/java/poscs/service/TicketService.java` (file mới, chưa commit; DAO/Validator/PermissionService không bị sửa)
- File test: `test/poscs/service/TicketServiceTest.java`
- Đối chiếu SPEC: UC-16, UC-17, UC-21, UC-22, UC-23 và BR-38..43, BR-47 (kèm MSG-022, MSG-051, NFR-19).

## Đã kiểm tra và đạt
- BR-38: mã `TK-xxxx` sinh qua DAO, ghi đè mã người dùng truyền vào, có test retry khi trùng.
- BR-40: luồng Mới tiếp nhận → Đang xử lý → Đã đóng, sai luồng trả MSG-051. `resolvedAt` chỉ ghi lần đầu Đã đóng. Phiếu và lịch sử ghi trong một giao dịch (DAO `insert`/`update`), có test rollback.
- BR-47: Đang xử lý bị chặn xóa; xóa mềm qua `is_deleted`.
- RBAC: qua `PermissionService`, vi phạm trả MSG-022. Kỹ thuật chỉ sửa phiếu được giao và chỉ sửa trường kỹ thuật.
- Hàm đều ≤ 40 dòng, không `TODO`/placeholder, không hardcode secret, không dùng `javax.*`.

## Danh sách lỗi

- [x] 🔴 `TicketService.java` › `applySystemCreateDefaults()` (dòng ~324-326)
  - Vi phạm: BR-39 / SPEC §5.5 ("Ngày tạo do hệ thống gán", không phải trường nhập)
  - Mô tả: `createdDate` chỉ được gán khi `null`. Người gọi truyền ngày quá khứ thì phiếu lưu ngày đó. Validator chỉ chặn ngày tương lai. Test (`TicketServiceTest` dòng ~303) chỉ kiểm tra ngày tương lai, không có test ngày quá khứ.
  - Gợi ý sửa:
    ```java
    ticket.setCreatedDate(Date.valueOf(LocalDate.now())); // BR-39: luôn do hệ thống gán
    ```
    Bổ sung test: truyền `createdDate` quá khứ, kỳ vọng kết quả là hôm nay. Nếu muốn giữ chặn ngày tương lai (MSG-019) thì validate trước khi ghi đè.
  - *Dev note đã sửa:* Đã sửa `applySystemCreateDefaults()` luôn gán `ticket.setCreatedDate(Date.valueOf(LocalDate.now()))` (sau bước `validateCreate` nên ngày tương lai vẫn bị chặn với MSG-019, còn ngày quá khứ hoặc `null` luôn được hệ thống ghi đè thành hôm nay). Đã bổ sung test truyền `createdDate` quá khứ trong `createTicket_assignsSystemStatusNewAndTodayCreatedDate()`.

- [x] 🟡 `TicketService.java` › `createTicket(TechnicalRequest, User)` (dòng ~274)
  - Vi phạm: BR-43 (mặc định Có)
  - Mô tả: overload 2 tham số truyền `ticket.isWarranty()`. `TechnicalRequest.warranty` là `boolean` nên không bao giờ `null` và mặc định `false`. Gọi overload này thì phiếu lưu "Không", sai mặc định. Test chỉ kiểm tra overload 3 tham số với `null`.
  - Gợi ý sửa: overload 2 tham số phải truyền `null` để dùng mặc định Có, hoặc đổi cách biểu diễn ngoại lệ. Không đổi chữ ký public khi chưa được yêu cầu. Thêm test cho overload 2 tham số với ticket mới tạo.
  - *Dev note đã sửa:* Đã sửa overload 2 tham số `createTicket(TechnicalRequest, User)` truyền `null` xuống `createTicket(ticket, null, actor)` để `ticketValidator.resolveWarranty(null)` luôn gán mặc định `true` (Có) theo BR-43. Đã bổ sung test gọi overload 2 tham số với POJO `new TechnicalRequest()` mới khởi tạo trong `createTicket_warrantyDefaultsToTrueWhenNullAndRespectsExplicitBoolean()`.

- [x] 🟡 `TicketService.java` › `createTicket()` / `validateUpdateRequest()` (dòng ~298, ~449)
  - Vi phạm: BR-39 (KTV được chỉ định)
  - Mô tả: chỉ kiểm tra `assignedTechnicianId > 0`. Service không kiểm tra user đó tồn tại, đang hoạt động và có vai trò Kỹ thuật. Gán nhầm Admin/Sales/user đã khóa vẫn qua. Sai khóa ngoại chỉ trả `create_failed` chung chung. `findActiveTechnicians()` đã có sẵn nhưng không dùng để kiểm tra.
  - Gợi ý sửa:
    ```java
    if (!employeeDAO.isActiveTechnician(ticket.getAssignedTechnicianId())) { /* fail ERR_INVALID */ }
    ```
    (hoặc dùng `employeeDAO.findActiveByRole` kèm kiểm tra id) và thêm test tương ứng.
  - *Dev note đã sửa:* Đã thêm `isAssignedTechnicianValid(int technicianId, Integer keepTechnicianId)` đối chiếu qua `employeeDAO.findById` đảm bảo người xử lý tồn tại, đang hoạt động (`!isDeleted()`) và có đúng vai trò `"Kỹ thuật"` (cho phép giữ nguyên KTV cũ khi sửa phiếu cũ nếu người đó đã đổi vai trò, tương tự `keepUserId` ở Controller). Đã bổ sung test `createAndUpdateTicket_validateAssignedTechnicianMustBeActiveTechnician()`.

- [ ] 🟡 `TicketService.java` › `updateTicket()` + `TechnicalSupportTicketDAO.update()` (dòng ~356-375)
  - Vi phạm: BR-40 (MSG-051), NFR-19
  - Mô tả: luồng chuyển trạng thái được kiểm tra với bản `existing` đọc trước, không phải trạng thái đã khóa `FOR UPDATE` trong DAO. Hai người cùng sửa: A đọc "Mới tiếp nhận", B đã chuyển sang "Đã đóng", A gửi → "Đang xử lý" vẫn qua validate và DB ghi lùi Đã đóng → Đang xử lý. `resolvedAt` cũng tính theo `existing` cũ. Chưa có test cho tình huống này.
  - Gợi ý sửa: kiểm tra `isValidStatusTransition(previousStatus, newStatus)` ngay trong transaction ở DAO (hoặc thêm `AND status = ?` vào `UPDATE`), trả kết quả "xung đột" để service map sang MSG-051. Việc sửa DAO phải được người dùng đồng ý vì ngoài file của task.
  - *Dev note không sửa:* Giữ nguyên `- [ ]` vì câu lệnh `SELECT ... FOR UPDATE` và `UPDATE` nằm trong `TechnicalSupportTicketDAO.java` — thuộc file ngoài phạm vi Task 5.2 (`rules.md` §3 nghiêm cấm sửa file ngoài danh sách của task khi chưa có sự đồng ý của người dùng).

- [ ] 🟡 `TicketService.java` › `deleteTicket()` (dòng ~486-489)
  - Vi phạm: BR-47 (TOCTOU), UC-23 ("ghi lý do, lưu vết" ở bảng UC SPEC dòng 165)
  - Mô tả: (1) `canDelete()` rồi `softDelete()` là hai câu lệnh rời nhau, câu `UPDATE` không có điều kiện `status <> 'Đang xử lý'`. Phiếu chuyển sang Đang xử lý giữa hai lệnh vẫn bị xóa. (2) Service không nhận và không ghi lý do xóa, không lưu vết người xóa. SPEC §5.5 chỉ nói "hộp thoại xác nhận" nên hai chỗ này mâu thuẫn nhau.
  - Gợi ý sửa: (1) `UPDATE technicalrequests SET is_deleted=1 WHERE ticket_id=? AND status<>'Đang xử lý' AND is_deleted=0`. (2) Người dùng cần chốt SPEC có yêu cầu lý do/lưu vết hay không (rules §1: SPEC mơ hồ thì dừng và hỏi). Không tự thêm tính năng.
  - *Dev note không sửa:* Giữ nguyên `- [ ]` vì: (1) câu SQL `softDelete` nằm trong `TechnicalSupportTicketDAO.java` (ngoài phạm vi file của Task 5.2 theo `rules.md` §3); (2) theo `db/schema.sql` (bảng `technicalrequests` chỉ có cột `is_deleted`, không có cột `deleted_by`/`delete_reason`), `docs/SPEC.md` §5.5 (`BR-47`, `MSG-048` chỉ có hộp thoại xác nhận) và code/JSP hiện hữu (`TechnicalSupportTicketController.handleDelete`, `listTicket.jsp`, `viewdetailTicket.jsp`), thao tác xóa phiếu không có trường nhập hay cột lưu lý do xóa (`rules.md` §1 & §3 cấm tự thêm cột DB hoặc tự thêm tính năng).

- [ ] 🟡 `TicketService.java` › `createTicket()` (dòng ~303-309)
  - Vi phạm: BR-42 (mỗi phiếu có hạn SLA)
  - Mô tả: tạo phiếu không gán và không bắt buộc `slaDeadline`. Mặc định cột là `NULL`, và `isSlaOverdue()` trả `false` khi `null`, nên phiếu không SLA không bao giờ quá hạn. SPEC không nói rõ hạn SLA do ai nhập hay tự tính theo mức ưu tiên. Test chỉ kiểm tra `isSlaOverdue`/`isSlaDueSoon`, không kiểm tra `createTicket` với SLA trống hoặc SLA quá khứ.
  - Gợi ý sửa: người dùng cần chốt: bắt buộc nhập, hay tự tính theo ưu tiên (ví dụ Khẩn cấp + X giờ). Sau đó thêm validate hoặc gán mặc định và test.
  - *Dev note không sửa:* Giữ nguyên `- [ ]` đối với việc bắt buộc/tự tính `slaDeadline` vì theo `docs/SPEC.md` `BR-39` (6 trường bắt buộc khi tạo phiếu không gồm `slaDeadline`), `db/schema.sql` (`sla_deadline datetime DEFAULT NULL`), `TechnicalSupportTicketController.java` (dòng 461: *"hạn SLA không bắt buộc"*), và `TicketValidator` (Task 5.1 đã APPROVED), hạn SLA là trường tùy chọn. Đã bổ sung test `createTicket` với SLA trống, SLA quá khứ và SLA tương lai trong `TicketServiceTest`.

- [x] 🟡 `TicketServiceTest.java` (toàn file)
  - Vi phạm: rules §4 (mỗi nhánh mới có test)
  - Mô tả: thiếu test cho các điểm trên (ngày tạo quá khứ, overload warranty, KTV không hợp lệ, race trạng thái, xóa với lý do) và thiếu kiểm tra thông báo cho nhánh `ERR_NOT_BUYER`/`ERR_CONTRACT_MISMATCH` ở phía `getErrors()`. Tại dòng ~774/784 `assertNotNull(ticketDAO.findById(id))` chỉ kiểm tra phiếu còn tồn tại, có kèm `assertEquals` thông báo nên chấp nhận được.
  - Gợi ý sửa: thêm các test tương ứng sau khi sửa code.
  - *Dev note đã sửa:* Đã bổ sung đầy đủ unit test cho ngày tạo quá khứ, overload 2 tham số của `createTicket` (mặc định bảo hành Có), kiểm tra KTV không hợp lệ (không tồn tại, sai vai trò Sales/Admin, đã khóa), kiểm tra `getFieldErrors()` cho `ERR_NOT_BUYER` (`MSG_NOT_BUYER`) và `ERR_CONTRACT_MISMATCH` (`MSG_CONTRACT_MISMATCH`), kiểm tra tạo phiếu với SLA trống/quá khứ/tương lai, và kiểm tra Kỹ thuật cập nhật phiếu cũ có trường chung ngoài danh mục.

- [x] 🟢 `TicketService.java` › hằng số `MAX_CODE_GEN_ATTEMPTS` (dòng 31)
  - Vi phạm: rules §4 (code thừa)
  - Mô tả: hằng số không được dùng; việc thử lại đã nằm ở DAO (`MAX_CODE_GEN_ATTEMPTS = 5` private).
  - Gợi ý sửa: xóa hằng số, hoặc dùng làm nguồn chung.
  - *Dev note đã sửa:* Đã xóa hằng số `MAX_CODE_GEN_ATTEMPTS` không sử dụng khỏi `TicketService.java`.

- [ ] 🟢 `TicketService.java` (toàn file, 532 dòng)
  - Vi phạm: rules §4 (file > 250 dòng nên xem xét tách)
  - Mô tả: file lẫn nhiều nhóm việc (SLA, danh sách/KPI, CRUD). Nên tách `TicketSlaService` hoặc tách helper merge khi có dịp refactor. Không bắt buộc ở task này.
  - *Dev note không sửa:* Giữ nguyên một file `TicketService.java` theo đúng danh sách file của Task 5.2 (`rules.md` §3). Mọi phương thức bên trong đều ≤ 40 dòng.

- [x] 🟢 `TicketService.java` › `updateTicket()` (dòng ~369)
  - Vi phạm: rules §4 (hành vi cũ)
  - Mô tả: `validateMandatoryFields` chạy trên cả bản merge của Kỹ thuật, nên phiếu cũ có dữ liệu không hợp lệ (ví dụ ưu tiên ngoài danh mục) sẽ khiến Kỹ thuật không cập nhật được dù chỉ sửa trường kỹ thuật. Cần quyết định có chấp nhận hay không.
  - *Dev note đã sửa:* Đã tách `validateTechnicianFields()` khi `fullAccess == false` để Kỹ thuật viên chỉ bị kiểm tra các trường kỹ thuật mà họ được phép sửa (`status` và `causeCategory`), không bị chặn bởi dữ liệu lịch sử trên các trường quản trị chung. Đã bổ sung test `updateTicket_technicianCanUpdateLegacyTicketWithOutOfCatalogGeneralFields()`.

## Thống kê
🔴 1 · 🟡 6 · 🟢 3
