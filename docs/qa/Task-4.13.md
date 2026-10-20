# QA Report - Task 4.13
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1509 pass / 0 fail / 3 skipped (tổng 1512; 3 skipped là test tích hợp `_it`, không đụng DB thật)

**Phạm vi review:**
- File code:
  - `src/java/poscs/controller/ContractListController.java`
  - `src/java/poscs/controller/ContractDetailController.java`
  - `src/java/poscs/controller/ContractFilterHelper.java`
  - `src/java/poscs/controller/ContractController.java`
  - `src/java/poscs/controller/ContractControllerHelper.java`
- File test:
  - `test/poscs/controller/ContractListControllerTest.java`
  - `test/poscs/controller/ContractDetailControllerTest.java`
- Đối chiếu: UC-10, UC-11, UC-45 và BR-12, BR-14, BR-16, BR-18; rules §3, §4; SPEC mục 1, 3, 5.4, 6.

**Đánh giá tổng quan:**
- **Phân tầng & Tách controller (rules §4):** Đã tách thành công `ContractListController` (UC-10: danh sách, phân trang, lọc) và `ContractDetailController` (UC-11, UC-45: chi tiết hợp đồng, nhật ký, workspace) từ `ContractController`. Kích thước các file tuân thủ nghiêm ngặt chuẩn kiến trúc rules §4 (`ContractListController.java`: 245 dòng ≤ 250; `ContractDetailController.java`: 66 dòng ≤ 250; `ContractFilterHelper.java`: 220 dòng ≤ 250; `ContractControllerHelper.java`: 249 dòng ≤ 250; mọi method đều ≤ 35 dòng ≤ 40).
- **Tuân thủ nghiệp vụ (UC/BR):**
  - **BR-12 & BR-14:** Phân trang với `PAGE_SIZE = 10`, xử lý trang đầu cuối, hiển thị trạng thái trống chuẩn hóa qua `contractList` rỗng cho JSP.
  - **BR-16:** Cung cấp đầy đủ trường dữ liệu theo quy định cột cho JSP. Xử lý chiều Bán/Mua độc lập, bỏ lọc theo tỉnh đối với HĐ Mua.
  - **BR-18 & UC-11:** Hiển thị và bóc tách đầy đủ giá trị hợp đồng, lịch thanh toán, công nợ, đối chiếu giá trị cụm hợp đồng (`paymentMismatch`), chênh lệch hợp đồng Bán-Mua liên kết.
  - **UC-45:** Nhật ký thay đổi trạng thái hợp đồng được nạp và gán đầy đủ ra `contractHistory`.
  - Giữ nguyên toàn bộ bộ lọc trên thanh URL và phân trang (QueryStrings, FilterState, FilterChip).
- **Tính tương thích ngược:** `ContractController` chuyển giao `showList`, `showDetail` và `listByEnterpriseAsJson` sạch sẽ, đồng thời giữ các hàm static delegation và class `FilterChip` để không phá vỡ bất kỳ test hoặc code cũ nào. Toàn bộ 1509 unit tests đều PASS.
- **Vấn đề tồn tại:** Cả `ContractListController` và `ContractDetailController` đều chưa gọi `PermissionService.canViewContracts(currentUser)` để thực thi fail-closed RBAC theo chuẩn rules §4 (trả `MSG-022` / HTTP 403 khi người dùng không hợp lệ hoặc không có quyền), và các test suite mới chưa có test kịch bản phủ định cho phân quyền.

---

## Danh sách lỗi

- [x] 🔴 `ContractListController.java` › `showList()` (dòng ~81) & `ContractDetailController.java` › `doGet()` (dòng ~49)
  - Vi phạm: rules §4 ("Phân quyền chỉ qua `PermissionService`/`AccessControl`... Vi phạm quyền: trả `MSG-022`"), UC-10, UC-11, Checklist C.
  - Mô tả:
    1. Cả `ContractListController` và `ContractDetailController` đều nhận và khởi tạo `PermissionService` (hỗ trợ DI qua constructor và setter), nhưng phương thức xử lý request lại **không hề gọi** `getPermissionService().canViewContracts(currentUser)`.
    2. Nếu người dùng chưa đăng nhập hợp lệ (trong trường hợp truy cập trực tiếp servlet), tài khoản đã bị xóa mềm/vô hiệu hóa trong DB (`is_deleted = 1`), hoặc vai trò không thuộc danh mục được xem hợp đồng (Admin, Sales, Kỹ thuật), controller vẫn tiếp tục truy vấn DB và render trang JSP thay vì từ chối ngay lập tức bằng mã lỗi HTTP 403 Forbidden cùng thông báo `PermissionService.MSG_022`.
  - Gợi ý sửa:
    Trong `ContractListController.java` (`showList`):
    ```java
    User currentUser = AccessControl.currentUser(request);
    if (!getPermissionService().canViewContracts(currentUser)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        return;
    }
    ```
    Trong `ContractDetailController.java` (`doGet`):
    ```java
    User currentUser = AccessControl.currentUser(request);
    if (!getPermissionService().canViewContracts(currentUser)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
        return;
    }
    ```
    *Lưu ý khi tích hợp:* Đảm bảo các test case cũ của `ContractControllerTest` (nếu chạy qua Facade) được cung cấp stub `employeeDAO.findById()` hoặc tiêm `PermissionService` phù hợp để cơ chế fail-closed hoạt động thông suốt.

---

- [x] 🟡 `ContractListControllerTest.java` & `ContractDetailControllerTest.java` › Thiếu unit test kịch bản phủ định cho phân quyền (RBAC negative test)
  - Vi phạm: rules §4 ("Mọi logic mới hoặc nhánh mới (if/else, trường hợp biên) có test tương ứng trong test/"), Checklist E.
  - Mô tả: Cả hai file test suite mới đều chỉ kiểm thử luồng người dùng hợp lệ (`loginAs("Sales")`), hoàn toàn thiếu test case kiểm tra khi người dùng không có quyền xem hợp đồng (`canViewContracts == false`) thì hệ thống phải chặn và trả về HTTP 403 với `PermissionService.MSG_022`.
  - Gợi ý sửa: Bổ sung các test cases tương ứng:
    Trong `ContractListControllerTest.java`:
    ```java
    @Test
    public void list_userCannotViewContracts_returns403WithMsg022() throws Exception {
        when(permissionService.canViewContracts(any())).thenReturn(false);
        controller.showList(request, response);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }
    ```
    Trong `ContractDetailControllerTest.java`:
    ```java
    @Test
    public void view_userCannotViewContracts_returns403WithMsg022() throws Exception {
        when(permissionService.canViewContracts(any())).thenReturn(false);
        controller.doGet(request, response);
        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, PermissionService.MSG_022);
    }
    ```

---

- [x] 🟢 `ContractListControllerTest.java` › Bổ sung test kịch bản phòng thủ tham số phân trang
  - Vi phạm: Checklist B, Checklist E (kiểm tra trường hợp biên).
  - Mô tả: Phương thức `showList` có xử lý `Math.max(1, ContractControllerHelper.parseIntOrDefault(request.getParameter("page"), 1))` để chuẩn hoá tham số trang âm hoặc chuỗi không phải số về 1. Tuy nhiên, test suite hiện tại mới chỉ kiểm tra `page = null` và `page = "2"`, chưa có test case khẳng định `page = "-5"` hoặc `page = "abc"` vẫn được chuẩn hoá an toàn về trang 1.
  - Gợi ý sửa: Thêm test case:
    ```java
    @Test
    public void list_invalidOrNegativePage_defaultsToPageOne() throws Exception {
        when(request.getParameter("page")).thenReturn("-5");
        controller.showList(request, response);
        verify(request).setAttribute("currentPage", 1);
    }
    ```

---

- [x] 🟢 `ContractListController.java` › `doGet()` (dòng ~74)
  - Vi phạm: Tính nhất quán cấu hình route `@WebServlet`.
  - Mô tả: `ContractListController` khai báo url-pattern là `@WebServlet(name = "ContractListController", urlPatterns = {"/contract/list"})`, nhưng bên trong `doGet()` lại có kiểm tra `if ("/contract/byEnterprise".equals(request.getServletPath()))`. Nhánh này thực tế không bao giờ được kích hoạt trong web container thật thông qua servlet này mà chỉ được gọi gián tiếp từ `ContractController` hoặc khi mock unit test.
  - Gợi ý sửa: Giữ nguyên để phục vụ Facade delegation hiện tại, nhưng khi tiến hành refactor triệt để `ContractController` ở các task kế tiếp, nên chuẩn hóa ánh xạ URL hoặc tách route API JSON chuyên biệt để loại bỏ nhánh điều kiện không đạt tới (dead branch) trên URL chính.
