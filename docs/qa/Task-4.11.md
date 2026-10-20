# QA Report - Task 4.11
**Kết luận:** APPROVED (ĐÃ FIX)
**Kết quả `ant test`:** 1430 pass / 0 fail / 3 skipped (tổng 1433; 3 skipped là test tích hợp `_it`, không đụng DB thật)

**Phạm vi review:**
- File code: `src/java/poscs/service/ContractHandoverService.java`, `src/java/poscs/service/ContractPaymentService.java`
- File test: `test/poscs/service/ContractHandoverServiceTest.java`, `test/poscs/service/ContractPaymentServiceTest.java`
- Đối chiếu: UC-48, UC-49, UC-53 và BR-18, NFR-19 trong `docs/SPEC.md`.

**Đánh giá tổng quan:**
APPROVED - Đạt yêu cầu UC & BR. Code tuân thủ nghiêm ngặt chuẩn kiến trúc phân tầng (kế thừa `BaseService`, trả `ServiceResult`, mọi hàm ≤ 24 dòng, file ≤ 241 dòng, không có TODO/placeholder). Phân quyền chuẩn xác qua `PermissionService` (vi phạm trả `MSG-022`), quản lý ranh giới giao dịch và kiểm toán `contract_history` đầy đủ. 35 unit tests mới bao phủ toàn diện cả kịch bản đúng và các trường hợp biên/lỗi với assertion chặt chẽ.

## Danh sách lỗi & Khuyến nghị

- [x] 🟡 `ContractHandoverService.java` › `completeHandover()` (dòng ~130-153)
  - Vi phạm: UC-49, Checklist B/C (Bảo mật & Kiểm tra quyền theo đối tượng)
  - Mô tả: Service kiểm tra quyền dựa trên tham số `departmentId` do client truyền vào (`permissionService.canConfirmHandover(actor, departmentId)`). Tuy nhiên `handoverDAO.completeHandover(handoverId, ...)` tự khóa dòng theo `handoverId` từ DB mà không xác thực xem `departmentId` thực tế của bản ghi chặng bàn giao có khớp với phòng ban của actor hay không (nếu actor là nhân viên phòng ban). Nếu một nhân viên thuộc phòng ban A truyền `departmentId = [id phòng A]` nhưng gửi kèm `handoverId` của phòng ban B, kiểm tra quyền sẽ vượt qua và hoàn tất chặng bàn giao của phòng ban B.
  - Gợi ý sửa: Tại Controller (Task 4.14) hoặc mở rộng kiểm tra trong Service: tra cứu thông tin chặng bàn giao qua danh sách `findHandoversOf(contractId)` để đảm bảo `handover.getDepartmentId() == actor.getDepartmentId()`, hoặc truyền thêm `expectedDepartmentId` xuống DAO xác thực trong mệnh đề WHERE của `updateDoneStatus`.
  - *Dev note đã sửa:* Đã thêm hàm `findHandoverTarget(contractId, handoverId)` tra cứu đối tượng chặng bàn giao thực tế từ DB; đối chiếu `targetHandover.getDepartmentId() == departmentId` (nếu lệch trả `ERR_INVALID`) và kiểm tra quyền `permissionService.canConfirmHandover(actor, actualDeptId)` trực tiếp trên phòng ban của bản ghi. Đã bổ sung unit test `testCompleteHandover_QAFeedback_DepartmentMismatchAndSpoofing_Fails()`.

- [x] 🟢 `ContractHandoverService.java` › `completeHandover()` (dòng ~138-143)
  - Vi phạm: Checklist B (Tính toàn vẹn tham chiếu chéo)
  - Mô tả: Khi caller truyền `contractId > 0`, service chỉ kiểm tra hợp đồng tồn tại và chưa xóa mềm, nhưng không xác minh `handoverId` có thực sự thuộc về `contractId` đó hay không.
  - Gợi ý sửa: Khi gọi overload có `contractId`, có thể xác nhận chặng bàn giao nằm trong `findHandoversOf(contractId)` trước khi gọi hoàn tất.
  - *Dev note đã sửa:* Đã tích hợp kiểm tra chặng bàn giao nằm trong `findHandoversOf(contractId)` khi `contractId > 0`; nếu không tìm thấy trả `ERR_NOT_FOUND`. Đã bổ sung unit test `testCompleteHandover_QAFeedback_HandoverNotBelongingToContract_FailsNotFound()`.

- [x] 🟢 `ContractPaymentService.java` › `deletePayment()` (dòng ~131-150)
  - Vi phạm: UC-53 (Nghiệp vụ xóa kỳ thanh toán lập nhầm)
  - Mô tả: Service và DAO hiện tại cho phép xóa kỳ thanh toán kể cả khi kỳ này đã được ghi nhận đã thu tiền (`paid_date != null`), miễn là hợp đồng chưa đóng băng. Kỳ đã thu tiền nếu bị xóa có thể ảnh hưởng đến báo cáo doanh thu đã thu.
  - Gợi ý sửa: Bổ sung kiểm tra nếu kỳ thanh toán đã có `paid_date != null` thì từ chối xóa hoặc chỉ cho phép Admin thực hiện, trả lỗi nghiệp vụ rõ ràng `ERR_PAYMENT_ALREADY_PAID`.
  - *Dev note đã sửa:* Đã thêm hằng số `ERR_PAYMENT_ALREADY_PAID = "payment_already_paid"`. Khi thực hiện `deletePayment`, service tra cứu kỳ thanh toán trong hợp đồng qua `findPaymentInContract(contractId, paymentId)`; nếu kỳ thanh toán đã có `paid_date != null` thì từ chối xóa và trả mã lỗi `ERR_PAYMENT_ALREADY_PAID`. Đã bổ sung unit test `testDeletePayment_QAFeedback_PaymentAlreadyPaid_Fails()`.
