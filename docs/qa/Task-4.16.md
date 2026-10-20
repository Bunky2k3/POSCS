# QA Report - Task 4.16
**Kết luận:** ĐÃ SỬA (PASS)
**Kết quả `ant test`:** 1644 pass / 0 fail / 3 skipped (tổng 1647 tests; 3 skipped là test tích hợp `_it`, không đụng DB thật)

**Phạm vi review:**
- File code: `src/java/poscs/controller/ContractDocumentController.java`, `src/java/poscs/service/ContractDocumentService.java`, `src/java/poscs/controller/ContractController.java`
- File test: `test/poscs/controller/ContractDocumentControllerTest.java`, `test/poscs/service/ContractDocumentServiceTest.java`, `test/poscs/controller/ContractControllerTest.java`
- Đối chiếu: UC-52 trong `docs/SPEC.md`; quy chuẩn kiến trúc và bảo mật trong `rules.md`, `.gemini/rules.md`.

**Đánh giá tổng quan:**
Task 4.16 đã hoàn thành tốt các mục tiêu chính: tách `ContractDocumentController` và `ContractDocumentService` xử lý nghiệp vụ tài liệu đính kèm hợp đồng (UC-52) với phân quyền RBAC qua `PermissionService` (Admin, Sales quản lý vùng), kiểm tra an toàn URL qua `TextRules.isSafeHttpUrl`, xóa mềm bắt buộc lý do và ghi nhận `contract_history` trong cùng một transaction ở tầng DAO. Toàn bộ các điểm lưu ý về bảo mật (IDOR), lazy initialization caching và clean code đã được khắc phục hoàn chỉnh kèm unit tests bao phủ 100%.

---

## Danh sách lỗi

- [x] 🟡 `ContractDocumentService.java` › `voidDocument()` (dòng ~76-100)
  - Vi phạm: UC-52, Checklist A & B, NFR-10 (Bảo mật truy cập & toàn vẹn dữ liệu - IDOR / Cross-contract document tampering)
  - Mô tả: Trong `voidDocument(int contractId, int documentId, String reason, User actor)`, service kiểm tra `contractDAO.findById(contractId)` để xác nhận hợp đồng tồn tại, nhưng sau đó KHÔNG kiểm tra xem `documentId` có thực sự thuộc về `contractId` hay không trước khi gọi `documentDAO.voidDocument(documentId, ...)`. Do `ContractDocumentDAO.voidDocument` chỉ nhận `documentId` mà không ràng buộc `contractId`, một người dùng có quyền quản lý hợp đồng A có thể gửi request huỷ với `contractId = [ID hợp đồng A]` nhưng truyền `documentId = [ID tài liệu của hợp đồng B]`. Khi đó tài liệu của hợp đồng B sẽ bị xoá mềm và ghi log vào hợp đồng B. (Tương tự như cách `ContractPaymentService.java` dòng 146 xử lý bằng `findPaymentInContract(contractId, paymentId)` để chặn huỷ chéo).
  - Gợi ý sửa:
    Kiểm tra sự tồn tại của `documentId` trong danh sách tài liệu của `contractId` trước khi thực hiện xoá mềm:
    ```java
    List<ContractDocument> docs = documentDAO.findDocumentsOf(contractId);
    boolean belongsToContract = docs != null && docs.stream().anyMatch(d -> d.getDocumentId() == documentId);
    if (!belongsToContract) {
        return ServiceResult.fail(ERR_NOT_FOUND);
    }
    ```
    Đồng thời bổ sung unit test case trong `ContractDocumentServiceTest.java` kiểm tra kịch bản `documentId` không thuộc về `contractId` trả về `ERR_NOT_FOUND`.

- [x] 🟢 `ContractController.java` › `getDocumentController()` (dòng ~187-192)
  - Vi phạm: rules §4 (Kiến trúc phân tầng & Quản lý Dependency)
  - Mô tả: Trong `ContractController.java`, phương thức `getDocumentController()` kiểm tra `if (documentController != null) return documentController;`, nhưng ở nhánh dưới lại `return new ContractDocumentController(...)` mà không gán ngược lại vào `this.documentController`. Điều này khiến mỗi request `addDocument` hoặc `voidDocument` đi qua `ContractController` đều tạo ra một instance mới của `ContractDocumentController` thay vì tái sử dụng instance đã được khởi tạo theo cơ chế lazy initialization.
  - Gợi ý sửa:
    ```java
    ContractDocumentController getDocumentController() {
        if (documentController == null) {
            this.documentController = new ContractDocumentController(contractDAO, employeeDAO, null, getPermissionService());
        }
        return documentController;
    }
    ```
    Đồng thời bổ sung assertion kiểm tra instance caching trong `ContractControllerTest.getDocumentController_lazyInitAndSetDocumentController()`.

- [x] 🟢 `ContractController.java` (dòng 34)
  - Vi phạm: rules §4 (Code cleanliness / Clean Code)
  - Mô tả: Import `poscs.model.ContractDocument` tại dòng 34 không còn được sử dụng ở bất kỳ đâu trong `ContractController.java` sau khi logic tài liệu được chuyển sang `ContractDocumentController`.
  - Gợi ý sửa:
    Xoá dòng `import poscs.model.ContractDocument;` khỏi `ContractController.java`.
