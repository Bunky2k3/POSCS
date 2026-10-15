# QA Report - Task 3.3
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1127 pass / 0 fail / 3 skipped (test tích hợp DB `_it` bị bỏ qua mặc định). `BUILD SUCCESSFUL`.

Phạm vi review:
- File code: `src/java/poscs/controller/ProductController.java`, `src/java/poscs/service/ProductService.java`
- File test: `test/poscs/controller/ProductControllerTest.java`
- Đối chiếu: UC-12, UC-13, UC-18, UC-19, UC-20; BR-12, BR-14, BR-19, BR-49, BR-53; SPEC mục 4.3, 5.3, 6, 9.

**Đã kiểm tra đạt yêu cầu:**
- **Phân tầng Presentation thuần túy (rules §4):** `ProductController` đã loại bỏ hoàn toàn việc truy cập trực tiếp `ProductDAO` trong các luồng xử lý HTTP request, chuyển giao toàn bộ tính toán cây danh mục (root categories, children map, subtree rollup count, accordion open path, category options), kiểm tra tính hợp lệ BR-54 / BR-48, xử lý upload/xóa file ảnh & catalogue, và thao tác xóa mềm sang `ProductService`.
- **UC-12, BR-12, BR-14, BR-53 (Danh sách sản phẩm):** Thiết lập đầy đủ các request attributes cho card grid và cây phân cấp danh mục (`productList`, `categoryList`, `rootCategories`, `childrenByParent`, `expandedCategoryIds`, `categoryCounts`, `grandTotal`, `currentPage`, `totalPages`, `totalCount`, `keyword`, `keywordParam`, `categoryFilter`). Kích thước phân trang cố định 10 bản ghi (`PAGE_SIZE = 10`), chuẩn hóa số trang không hợp lệ (số âm, chuỗi chữ) về 1. Danh sách trống hiển thị đúng MSG-023.
- **UC-13, BR-19 (Chi tiết sản phẩm):** Lấy chi tiết qua `ProductService.getProductDetail(id)`, nạp đầy đủ thông tin sản phẩm, danh mục, hình ảnh, catalogue và danh sách hợp đồng liên quan (`contractList`). Xử lý an toàn khi ID sai định dạng, <= 0 hoặc không tồn tại (chuyển hướng kèm `error=notfound`).
- **UC-18, UC-19, UC-20 (Thêm / Sửa / Xóa sản phẩm):** Gọi qua `ProductService.createProduct()`, `updateProduct()`, `deleteProduct()`. Nhận diện và chuyển hướng chính xác các mã lỗi chuẩn hóa (`invalid`, `invalid_category`, `invalid_image_type`, `invalid_catalogue_type`, `create_failed`, `update_failed`, `delete_failed`, `has_active_contracts`, `notfound`, `files_not_saved`).
- **An toàn & Bảo mật:** Kiểm tra phân quyền `AccessControl.requireFullAccess` trên mọi thao tác ghi và form tạo/sửa; CSRF token được bảo vệ trên form POST qua `AuthenticationFilter`; bóc tách tham số chống lỗi định dạng (`parseIntOrNull`, `parseIntOrDefault`, `emptyToNull`).

---

## Danh sách lỗi

- [x] 🟡 `ProductController.java` › `handleUpdate()` (dòng ~274–323)
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ").
  - Mô tả: Phương thức `handleUpdate()` dài **50 dòng** (> 40 dòng) do vừa bóc tách các trường dữ liệu của `Product`, vừa xử lý tham số file, vừa gọi `ProductService` và phân nhánh điều hướng kết quả.
  - Gợi ý sửa: Tách phần bóc tách dữ liệu `Product` từ request ra hàm phụ trợ riêng:
    ```java
    private Product extractProductForUpdate(HttpServletRequest request, Integer id) {
        Product p = new Product();
        p.setProductId(id);
        p.setProductName(emptyToNull(request.getParameter("productName")));
        p.setDescription(emptyToNull(request.getParameter("description")));
        Integer categoryId = parseIntOrNull(request.getParameter("categoryId"));
        if (categoryId != null) {
            p.setCategoryId(categoryId);
        }
        return p;
    }
    ```
    Giúp rút ngắn `handleUpdate()` xuống dưới 35 dòng, đáp ứng chuẩn rules §4.

---

- [x] 🟡 `ProductControllerTest.java` › Thiếu unit test kiểm tra phân quyền (RBAC) cho thao tác Sửa và Xóa (`handleUpdate`, `handleDelete`) (dòng ~400–600)
  - Vi phạm: rules §4 ("Mọi logic mới hoặc nhánh mới (if/else, trường hợp biên) có test tương ứng trong test/"), UC-18, UC-20, SPEC mục 4.3 (Admin, Kỹ thuật có quyền; Sales chỉ xem, không có quyền ghi trên Sản phẩm).
  - Mô tả: Cả `handleUpdate` và `handleDelete` đều có chốt chặn kiểm quyền `AccessControl.requireFullAccess(request, response, AccessControl.Resource.PRODUCT)`. Tuy nhiên, test suite hiện tại chỉ có test case kiểm tra chặn quyền cho `create` (`create_withoutFullAccess_returns403AndNeverInserts`) và trang form (`createForm_withoutFullAccess...`, `editForm_withoutFullAccess...`), hoàn toàn thiếu test case kịch bản phủ định (negative test) khi người dùng không có quyền (ví dụ vai trò Sales) gửi POST tới `action=update` hoặc `action=delete`.
  - Gợi ý sửa: Bổ sung 2 unit test case kiểm tra chặn quyền trả về 403 Forbidden:
    ```java
    @Test
    public void update_withoutFullAccess_returns403AndNeverUpdates() throws Exception {
        loginWithoutProductAccess();
        when(request.getParameter("action")).thenReturn("update");
        when(request.getParameter("productId")).thenReturn("7");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(productDAO, never()).update(any());
    }

    @Test
    public void delete_withoutFullAccess_returns403AndNeverDeletes() throws Exception {
        loginWithoutProductAccess();
        when(request.getParameter("action")).thenReturn("delete");
        when(request.getParameter("id")).thenReturn("7");

        controller.doPost(request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
        verify(productDAO, never()).softDelete(anyInt());
    }
    ```

---

- [x] 🟢 `ProductControllerTest.java` › `showList` chưa kiểm tra forward view `LIST_VIEW` (`/jsp/technical/listProduct.jsp`) (dòng ~102–211)
  - Vi phạm: Quy chuẩn Unit test (Checklist E: assertion chưa bao quát việc điều hướng view hiển thị).
  - Mô tả: Trong `setUp()`, `request.getRequestDispatcher(anyString())` được stub chung. Mặc dù các test case của `showList` kiểm tra rất chi tiết các request attribute (cây danh mục, số lượng cộng dồn, phân trang, từ khóa), không có test nào thực hiện `verify(request).getRequestDispatcher("/jsp/technical/listProduct.jsp")` và `verify(dispatcher).forward(request, response)`.
  - Gợi ý sửa: Bổ sung assertion kiểm tra dispatcher forward tới `LIST_VIEW` trong `testShowList_EmptyList_BR14_BR53` hoặc tạo riêng test case xác nhận view routing cho `showList`.

---

- [ ] 🟢 `ProductController.java` › Kích thước file `ProductController.java` (dòng 1–385)
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ").
  - Mô tả: `ProductController.java` có tổng cộng 385 dòng (> 250 dòng). Dù đã giảm đáng kể từ 616 dòng trước refactor, controller hiện tại vẫn tích hợp cả 5 use cases (list, detail, create, edit, delete).
  - Gợi ý sửa: Cấu trúc hiện tại đã rõ ràng theo từng nhóm Presentation action. Khi mở rộng module sau này, có thể xem xét tách riêng `ProductListController`, `ProductDetailController` và `ProductActionController` tương tự mô hình của module Hợp đồng / Khách hàng.
  - *Ghi chú Dev:* Giữ nguyên controller thống nhất theo đúng mục tiêu của Task 3.3 ("Tinh gọn ProductController chỉ còn lớp Presentation, gọi qua ProductService"). Controller đã được làm sạch hoàn toàn khỏi nghiệp vụ và truy cập DAO, tất cả các phương thức đều $\le 30$ dòng. Khi module Sản phẩm phát triển thêm tính năng (nhập/xuất hàng loạt, lịch sử giá...) sẽ tiến hành tách tương tự Module Khách hàng.
