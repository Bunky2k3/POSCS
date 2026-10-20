# QA Report - Task 4.14
**Kết luận:** ĐÃ FIX (6/7 mục đã sửa, 1 mục giữ `- [ ]` do vi phạm rules §3)
**Kết quả `ant test`:** 1539 pass / 0 fail / 3 skipped (tổng 1542 tests; 3 skipped là test tích hợp `_it`, không đụng DB thật)

**Phạm vi review:**
- File code: `src/java/poscs/controller/ContractHandoverController.java`, `src/java/poscs/controller/ContractHandoverHelper.java`, `src/java/poscs/controller/ContractController.java`
- File test: `test/poscs/controller/ContractHandoverControllerTest.java`
- Đối chiếu: UC-48, UC-49 và rules §3, §4 trong `rules.md`, `docs/SPEC.md`.

---

## Danh sách lỗi & Trạng thái sửa

- [x] 🔴 `ContractHandoverController.java` › `handleCompleteHandover()` & `executeComplete()` (dòng ~195-234)
  - Vi phạm: UC-49, rules §3 (Bảo mật / IDOR), §4 (Phân tầng Controller - Service - DAO)
  - Mô tả: Code cũ kiểm tra `if (handoverService != null)` nhưng trường này là null ở runtime, dẫn đến gọi thẳng DAO và tạo lỗ hổng spoofing phòng ban; ngoài ra gán sai `actor.setDepartmentId` từ tham số request.
  - *Dev note đã sửa:* Đã xóa bỏ hoàn toàn `executeComplete` fallback và ủy quyền trực tiếp qua `getHandoverService().completeHandover(contractId, handoverId, departmentId, doneNote, actor)` với `actor` lấy từ `AccessControl.currentUser(request)`. Xử lý trả lỗi `SC_FORBIDDEN` + `MSG_022` khi Service từ chối quyền.

- [x] 🔴 `ContractHandoverController.java` › `handleHandOver()` & `executeHandover()` (dòng ~138-175)
  - Vi phạm: UC-48, rules §4 (Controller không chứa nghiệp vụ, phải gọi qua Service)
  - Mô tả: Code cũ gọi thẳng DAO và tạo đối tượng `User` rỗng thiếu role/cấp bậc.
  - *Dev note đã sửa:* Đã loại bỏ `executeHandover` thừa, ủy quyền toàn bộ qua `getHandoverService().initiateHandover(contractId, departmentIds, note, actor)` với `actor` chuẩn từ session. Ánh xạ các trạng thái lỗi `ERR_FORBIDDEN` (403 MSG-022), `ERR_NOT_FOUND` (404), `ERR_HANDOVER_PENDING`, `ERR_HANDOVER_FROZEN`, `ERR_HANDOVER_FAILED` về đúng tham số URL cho view.

- [x] 🟡 `ContractHandoverController.java` › `handleCompleteHandover()` (dòng ~203-208)
  - Vi phạm: rules §4 (Vi phạm quyền trả `MSG-022`), SPEC mục 3 (dòng 98)
  - Mô tả: Code cũ redirect sang `/error/403.jsp` (file không tồn tại vì file thực tế là `/error403.jsp`), gây lỗi 404.
  - *Dev note đã sửa:* Đã chuyển sang gọi chuẩn `response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);` để servlet container tự động kích hoạt trang `error403.jsp` đã cấu hình trong `web.xml`.

- [x] 🟡 `ContractHandoverController.java` › `getHandoverService()` (dòng ~68-90)
  - Vi phạm: rules §4 (Kiến trúc phân tầng, quản lý Dependency Injection)
  - Mô tả: Không cache instance `handoverService` và tạo `daoAdapter` ẩn danh thừa.
  - *Dev note đã sửa:* Đã cache instance `this.handoverService` theo singleton pattern và tái sử dụng trực tiếp `contractDAO.getHandoverDAO()`.

- [x] 🟡 `ContractHandoverControllerTest.java` › `testCompleteHandover_unauthorizedDepartment_sendErrorForbidden()` & test actor
  - Vi phạm: rules §3, Checklist E
  - Mô tả: Test cũ assert redirect vào file không tồn tại `/error/403.jsp` và mock matcher `any()` che giấu lỗi tạo user rỗng.
  - *Dev note đã sửa:* Đã cập nhật test kiểm tra `response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022)` và bổ sung `argThat` xác minh `User actor` truyền xuống Service có đầy đủ `userId`, `role` chuẩn xác từ session.

- [x] 🟢 `ContractHandoverController.java` › `showHandoverQueue()` (dòng ~127-129)
  - Vi phạm: DRY, rules §4
  - Mô tả: Tính toán lại dữ liệu hàng đợi thay vì tái sử dụng `ContractHandoverService`.
  - *Dev note đã sửa:* Đã chuyển sang gọi `getHandoverService().findPendingHandovers(me, mine)`, `getHandoverService().getQueueOverview(inScope)` và `getHandoverService().findAllDepartments()`.

- [ ] 🟡 `ContractController.java` › `showHandoverQueue()`, `handleHandOver()`, `handleCompleteHandover()` (dòng ~1017-1175)
  - Vi phạm: rules §4 (Tinh gọn Controller và ủy quyền sang Controller chuyên trách)
  - Mô tả: `ContractController.java` hiện vẫn chứa hơn 150 dòng logic bàn giao cũ gọi thẳng DAO (và dòng 1145 vẫn còn lệnh cũ `response.sendRedirect(request.getContextPath() + "/error/403.jsp");`). Vì các form JSP và link menu đều trỏ vào `/contract?action=handovers`, `/contract?action=handOver`, `/contract?action=completeHandover`, nếu `ContractController` không ủy quyền sang `ContractHandoverController`, các request này sẽ không chạy qua `ContractHandoverController` và `ContractHandoverService`.
  - Gợi ý sửa: Bổ sung `handoverController` vào `ContractController` và ủy quyền (như cách đã làm với `listController` và `detailController` ở Task 4.13):
    ```java
    ContractHandoverController getHandoverController() {
        if (handoverController != null) return handoverController;
        return new ContractHandoverController(contractDAO, employeeDAO, null, getPermissionService());
    }

    private void showHandoverQueue(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        getHandoverController().showHandoverQueue(request, response);
    }

    private void handleHandOver(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getHandoverController().handleHandOver(request, response);
    }

    private void handleCompleteHandover(HttpServletRequest request, HttpServletResponse response) throws IOException {
        getHandoverController().handleCompleteHandover(request, response);
    }
    ```
  - *Dev note (KHÔNG sửa, giữ `- [ ]` theo rules §3 và prompt quy trình #3):*
    1. **Vi phạm ranh giới cấm rules §3:** Test suite hiện có `ContractControllerTest.java` (gồm 92 unit tests) chứa các assertion khóa cứng hành vi cũ của `ContractController`:
       - Dòng 1010 (`completeHandover_khongThuocPhongDo_biTuChoi`): khóa cứng `verify(response).sendRedirect(CONTEXT_PATH + "/error/403.jsp");`. Nếu ủy quyền sang `ContractHandoverController`, controller mới sẽ trả HTTP 403 `sendError(SC_FORBIDDEN, MSG_022)` theo chuẩn rules §4 (như chính QA đã yêu cầu ở mục 🟡 trên), dẫn đến test cũ này bị fail. Rules §3 nghiêm cấm: *"Không sửa test để che lỗi. Không sửa assertion, không xóa test case hiện có"*.
       - Dòng 916, 930, 978, 1026: khóa cứng việc gọi trực tiếp trên mock `contractDAO` (`findPendingHandovers`, `handOverToDepartments`, `completeHandover`), trong khi `ContractHandoverController` đi qua `ContractHandoverService` và `ContractHandoverDAO`.
    2. **Đồng nhất với kiến trúc Task 4.12:** Ở Task 4.12, `ContractActionController` (`/contract/action`) cũng được tạo độc lập với đầy đủ nghiệp vụ Service và unit test riêng, hoàn toàn không sửa `ContractController` để bảo toàn 100% tương thích ngược cho 92 test cũ trong `ContractControllerTest`.
    3. **Phạm vi task:** Task 4.14 chỉ quy định xây dựng `ContractHandoverController` (`/contract/handover`) đáp ứng UC-48, UC-49. Việc refactor/tinh gọn `ContractController` cần thực hiện ở một task riêng biệt để cập nhật đồng bộ các assertion của test suite cũ.
