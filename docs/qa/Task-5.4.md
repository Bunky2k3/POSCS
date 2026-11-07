# QA Report - Task 5.4
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1864 pass / 0 fail / 3 skipped (tổng 1867; 3 skipped là test tích hợp `_it`, không chạy DB thật theo rules §0)

**Phạm vi review:**
- File code:
  - `src/java/poscs/controller/TicketActionController.java`
  - `src/java/poscs/controller/TicketActionHelper.java`
  - `src/java/poscs/controller/TechnicalSupportTicketController.java`
  - `src/java/poscs/service/TicketService.java` (phát hiện bị chỉnh sửa)
- File test:
  - `test/poscs/controller/TicketActionControllerTest.java`
- Đối chiếu SPEC: UC-21, UC-22, UC-23 và BR-38, BR-39, BR-40, BR-41, BR-42, BR-43, BR-47 (kèm ràng buộc rules §3, §4).

---

## Danh sách lỗi

- [x] 🔴 `TicketService.java` › `isValidTechnician()`, `deleteTicket()`, `isMock()` (dòng ~488-491, ~526-532, ~584-596)
  - Vi phạm: rules §3 (Ranh giới cấm: cấm sửa logic production để lách lỗi test mock), rules §2 (Phạm vi task: tự ý sửa `TicketService.java` vốn thuộc Task 5.2 đã đóng).
  - Mô tả: Để unit test trong `TicketActionControllerTest` pass mà không cần stub `employeeDAO.findById()` hay `ticketDAO.findById()`, code nghiệp vụ production đã bị chèn cơ chế reflection gọi Mockito (`Class.forName("org.mockito.Mockito")`) và kiểm tra `isMock(employeeDAO)` / `isMock(ticketDAO)` nhằm bypass các kiểm tra hợp lệ ở `TicketService`. Đây là sai phạm kiến trúc và bảo mật rất nghiêm trọng:
    1. Làm rò rỉ framework test vào logic nghiệp vụ production.
    2. Bỏ qua kiểm tra vai trò kỹ thuật viên và dữ liệu phiếu hợp lệ trong runtime khi đối tượng là mock.
    3. Trực tiếp vi phạm ranh giới cấm của dự án.
  - Gợi ý sửa:
    1. Revert hoàn toàn mọi thay đổi trong `src/java/poscs/service/TicketService.java` về trạng thái chuẩn của Task 5.2 (xóa bỏ hàm `isMock` và các nhánh kiểm tra mock).
    2. Cập nhật stubbing chuẩn xác trong `test/poscs/controller/TicketActionControllerTest.java` (stub `employeeDAO.findById(50)` trả về User có role Kỹ thuật và `ticketDAO.findById(...)` trả về phiếu hợp lệ).

- [x] 🟡 `TicketActionController.java` (toàn bộ file, dòng 1-287)
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ.")
  - Mô tả: File `TicketActionController.java` hiện có 287 dòng, vượt ngưỡng tiêu chuẩn 250 dòng của dự án. File chứa nhiều getter/setter DAO dư thừa (`getTicketDAO`, `setTicketDAO`, `getCustomerDAO`, ...) không được servlet container hay caller nào sử dụng.
  - Gợi ý sửa: Lược bỏ các getter/setter DAO không cần thiết, hoặc di chuyển các phương thức phụ trợ như `setDropdownAttributes`, `handleCreateError`, `handleUpdateError`, `handleDeleteError` sang `TicketActionHelper.java` để đưa controller về dưới 250 dòng.

- [x] 🟡 `TicketActionController.java` › `showEditForm()` (dòng ~193-195)
  - Vi phạm: UC-22 / rules §4 (Tương thích view JSP)
  - Mô tả: Khi hiển thị form sửa phiếu, controller gán `request.setAttribute("lockGeneral", !fullAccess)` nhưng KHÔNG gán `request.setAttribute("canManage", fullAccess)`. File JSP `web/jsp/customersupport/updateTicket.jsp` (dòng 25) có chỉ thị `<c:set var="lockGeneral" value="${not canManage}"/>` ở page scope. Khi truy cập trực tiếp qua servlet `TicketActionController` (`/ticket/action?action=edit&id=...`), `canManage` bị rỗng/null khiến `${not canManage}` luôn đánh giá là `true`, ghi đè và khóa toàn bộ các trường chung ngay cả đối với Admin và Sales.
  - Gợi ý sửa:
    ```java
    request.setAttribute("ticket", ticket);
    request.setAttribute("canManage", fullAccess);
    request.setAttribute("lockGeneral", !fullAccess);
    ```

- [x] 🟢 `TicketActionController.java` › `handleDeleteError()` (dòng ~275-278)
  - Vi phạm: UC-23 / BR-47 (Trải nghiệm luồng ngoại lệ)
  - Mô tả: Khi xóa phiếu không tồn tại trong DB (`TicketService.ERR_NOT_FOUND`), hàm `handleDeleteError` chuyển hướng về `/ticket?action=view&id=" + id + "&error=cannot_delete`. Việc chuyển hướng về trang xem chi tiết của một phiếu không tồn tại sẽ kích hoạt lỗi `notfound` tiếp theo khi nạp chi tiết, gây lỗi thông báo và dư thừa redirect. Lẽ ra nên điều hướng thẳng về danh sách kèm thông báo `notfound`.
  - Gợi ý sửa:
    ```java
    if (TicketService.ERR_NOT_FOUND.equals(err)) {
        response.sendRedirect(request.getContextPath() + "/ticket?error=notfound");
        return;
    }
    if (TicketService.ERR_CANNOT_DELETE.equals(err)) {
        response.sendRedirect(request.getContextPath() + "/ticket?action=view&id=" + id + "&error=cannot_delete");
        return;
    }
    ```

- [x] 🟢 `TicketActionControllerTest.java` (dòng 1-512)
  - Vi phạm: Checklist E, rules §4 ("Mọi logic mới hoặc nhánh mới có test tương ứng trong test/")
  - Mô tả: Bộ unit test của Task 5.4 còn thiếu một số nhánh biên quan trọng:
    1. Chưa có test cho trường hợp người dùng chưa đăng nhập (`session.getAttribute("currentUser") == null`), cần xác nhận trả về HTTP 403 `MSG-022` ở cả 5 action (`new`, `edit`, `create`, `update`, `delete`).
    2. Chưa có test xác nhận cờ bảo hành mặc định `Có` (`isWarranty = true` theo BR-43) khi form tạo phiếu gửi lên không có tham số `isWarranty`.
    3. Chưa có test xóa phiếu khi ID phiếu không tồn tại.
  - Gợi ý sửa: Bổ sung các test cases tương ứng trong `TicketActionControllerTest.java`:
    ```java
    @Test
    public void actions_unauthenticated_returns403() throws Exception {
        when(session.getAttribute("currentUser")).thenReturn(null);
        when(request.getParameter("action")).thenReturn("new");
        controller.doGet(request, response);
        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), eq(PermissionService.MSG_022));
    }

    @Test
    public void handleCreate_missingWarrantyParam_defaultsToWarrantyTrue() throws Exception {
        when(request.getParameter("action")).thenReturn("create");
        stubValidCreateFields();
        when(request.getParameter("isWarranty")).thenReturn(null);
        when(ticketDAO.generateNextTicketCode()).thenReturn("TK-0001");
        when(ticketDAO.insert(any())).thenReturn(10);
        User tech = new User();
        tech.setUserId(50);
        tech.setRole(new Role(3, "Kỹ thuật"));
        when(employeeDAO.findById(50)).thenReturn(tech);
        controller.doPost(request, response);
        verify(ticketDAO).insert(argThat(TechnicalRequest::isWarranty));
    }
    ```
