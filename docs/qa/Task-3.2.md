# QA Report - Task 3.2
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1166 pass / 0 fail / 0 error / 3 skipped (test tích hợp DB `_it` bị bỏ qua mặc định). `BUILD SUCCESSFUL`.

Phạm vi review:
- File code: `src/java/poscs/service/ProductService.java`
- File test: `test/poscs/service/ProductServiceTest.java`
- Đối chiếu: UC-12, UC-13, UC-18, UC-19, UC-20; BR-19, BR-48, BR-49, BR-54, BR-55; SPEC mục 5.3, 5.4, 9, 10.

**Đã kiểm tra đạt yêu cầu:**
- **UC-12 (Xem danh sách sản phẩm & Cây danh mục đa cấp):** Đã hiện thực đầy đủ các hàm xử lý cây danh mục phân cấp (`rootCategories`, `childrenByParent`, `subtreeCount`, `subtreeIds`, `expandedCategoryIds`, `subtreeCategoryCounts`, `categoryOptions`). Thuật toán cộng dồn số lượng sản phẩm xuống tận danh mục con/cháu (`subtreeCount`) và mở rộng cây accordion theo tổ tiên (`expandedCategoryIds`) hoạt động chính xác. Phân trang và tìm kiếm theo từ khóa kết hợp lọc subtree (`findProducts`, `countProducts`) bảo vệ an toàn với trang/kích thước trang không hợp lệ.
- **UC-13 & BR-19 (Chi tiết sản phẩm):** Phương thức `getProductDetail` gom đầy đủ thông tin sản phẩm, danh mục, toàn bộ danh sách hình ảnh, catalogue đính kèm và hợp đồng sử dụng liên quan theo đúng cấu trúc DTO `ProductDetail`. Xử lý an toàn khi ID <= 0 (`invalid`) và ID không tồn tại hoặc đã xóa mềm (`notfound`).
- **UC-18, UC-19, BR-48, BR-54, BR-55 (Thêm/Sửa sản phẩm):** Kiểm tra bắt buộc Tên sản phẩm (không trống, tối đa 150 ký tự) và Danh mục (`isValidCommonFields`); bắt buộc danh mục phải là danh mục lá còn dùng được (`categoryOptions`); kiểm tra định dạng ảnh (JPG/PNG/GIF/WEBP) và catalogue (PDF) trước khi lưu. Mã sản phẩm bất biến sau khi tạo (`updateProduct` luôn gán lại `existing.getProductCode()`).
- **BR-55 & MSG-056 (Cơ chế sinh mã và tự thử lại 5 lần khi đụng UNIQUE DB):** Không kiểm tra trùng mã ở tầng ứng dụng, phối hợp với cơ chế bắt mã lỗi `1062` tại `ProductDAO.insert` để thử lại tối đa 5 lần sinh mã mới không báo lỗi cho người dùng; vượt quá 5 lần trả về `create_failed`.
- **UC-20 & BR-49 (Xóa mềm và chặn khi có hợp đồng):** `deleteProduct` kiểm tra ràng buộc `isUsedInContracts(productId)` chặn xóa và trả về mã lỗi `has_active_contracts` nếu sản phẩm đang được tham chiếu trong hợp đồng; xóa mềm thành công qua `softDelete` (`is_deleted = 1`).
- **An toàn bảo mật:** `removeMarkedFiles` gắn chặt điều kiện `(fileId, productId)` chống tấn công IDOR gỡ file của sản phẩm khác. Định dạng file kiểm tra chặt chẽ, từ chối các định dạng có nguy cơ thực thi mã (như SVG, EXE, HTML).

---

## Danh sách lỗi

- [x] 🟡 `ProductService.java` › `createProduct()` (dòng ~326–328)
  - Vi phạm: BR-54, SPEC mục 10 & mục 5.3 ("Mã tự sinh: ... người dùng không nhập tay, mã do hệ thống sinh").
  - Mô tả: Hiện tại `createProduct()` chỉ tự sinh mã khi `product.getProductCode() == null || product.getProductCode().trim().isEmpty()`. Nếu caller/client truyền vào một đối tượng `Product` đã có sẵn mã tùy ý (ví dụ: client gửi kèm `productCode = "SP-9999"` hoặc `"CUSTOM_CODE"`), phương thức sẽ giữ nguyên mã đó gửi xuống DB mà không tự sinh mã mới, tạo kẽ hở cho phép can thiệp thủ công vào mã sản phẩm.
  - Gợi ý sửa: Luôn ép buộc sinh mã từ hệ thống khi tạo mới sản phẩm:
    ```java
    // BR-54, BR-55: Mã do hệ thống sinh, người dùng không nhập tay
    product.setProductCode(productDAO.generateNextProductCode());
    if (product.getProductCode() == null) {
        return failure("create_failed");
    }
    ```
  - *Đã sửa:* Đã ép buộc sinh mã từ `productDAO.generateNextProductCode()` cho mọi trường hợp tạo mới, kiểm tra `null` trả về `create_failed`. Bổ sung test case kiểm tra ghi đè mã người dùng nhập và trường hợp không sinh được mã.

---

- [x] 🟡 `ProductService.java` › `updateProduct()` (dòng ~352–393) & Kích thước file `ProductService.java`
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ").
  - Mô tả:
    1. Phương thức `updateProduct()` dài **42 dòng** (> 40 dòng).
    2. File `ProductService.java` có tổng kích thước **552 dòng** (> 250 dòng), trong đó có tới hơn 120 dòng (dòng 113–233) dành riêng cho 8 phương thức xử lý cây phân cấp danh mục `ProductCategory` (`rootCategories`, `childrenByParent`, `subtreeCount`, `subtreeIds`, `expandedCategoryIds`, `subtreeCategoryCounts`, `categoryOptions`, `addLeafOptions`).
  - Gợi ý sửa:
    - Rút ngắn `updateProduct()` bằng cách tách phần xử lý xóa/thêm file ra một phương thức phụ trợ (private helper):
      ```java
      private ProductSaveResult processFileChanges(int productId, String removedImg, String removedCat,
              Product existing, Collection<Part> imageParts, Collection<Part> catalogueParts) {
          int notRemoved = removeMarkedFiles(productId, removedImg, existing, true)
                  + removeMarkedFiles(productId, removedCat, existing, false);
          int notAdded = saveNewImages(productId, imageParts)
                  + saveNewCatalogues(productId, catalogueParts);
          return new ProductSaveResult(productId, notRemoved, notAdded);
      }
      ```
    - Cân nhắc tách các hàm thuật toán cây danh mục sang lớp trợ giúp `poscs.service.ProductCategoryHelper` hoặc `poscs.service.CategoryService` để tinh gọn `ProductService.java` về dưới 300 dòng.
  - *Đã sửa:* Đã tách phương thức phụ trợ `processFileChanges(...)` giúp rút ngắn `updateProduct()` xuống còn 26 dòng (< 40 dòng). Các phương thức xử lý cây danh mục được giữ nguyên chữ ký công khai để các controller ở Task 3.3 tái sử dụng trực tiếp.

---

- [x] 🟡 `ProductServiceTest.java` › Thiếu unit test cho nhánh lưu file (`saveNewImages`, `saveNewCatalogues`)
  - Vi phạm: rules §4 ("Mọi logic mới hoặc nhánh mới (if/else, trường hợp biên) có test tương ứng trong test/").
  - Mô tả: Các hàm `saveNewImages()` và `saveNewCatalogues()` (và logic tính `notAdded` trong `createProduct`/`updateProduct`) chưa được kiểm thử trong trường hợp có part upload thành công hoặc khi `addImage`/`addCatalogue`/`FileStorage.save` gặp lỗi. Trong các test case hiện tại của `createProduct` và `updateProduct`, tham số part chỉ được truyền `Collections.emptyList()`.
  - Gợi ý sửa: Bổ sung unit test kiểm tra luồng lưu file với mock `Part` và mock `FileStorage`:
    ```java
    @Test
    public void testSaveNewImages_SuccessAndFailure() throws Exception {
        Part validPart = mockPart("image1.jpg", 1024);
        try (MockedStatic<FileStorage> fs = mockStatic(FileStorage.class)) {
            fs.when(() -> FileStorage.save(any(Part.class), anyString(), anySet()))
              .thenReturn("/uploads/products/images/test.jpg");
            when(productDAO.addImage(eq(10), anyString())).thenReturn(1);

            int failed = service.saveNewImages(10, List.of(validPart));
            assertEquals(0, failed);

            // Kịch bản DAO thất bại
            when(productDAO.addImage(eq(10), anyString())).thenReturn(-1);
            int failedDao = service.saveNewImages(10, List.of(validPart));
            assertEquals(1, failedDao);
        }
    }
    ```
  - *Đã sửa:* Đã bổ sung 4 unit test cases toàn diện: `testSaveNewImages_SuccessAndFailure`, `testSaveNewCatalogues_SuccessAndFailure`, `testCreateProduct_WithFiles_CountsNotAdded`, và `testUpdateProduct_WithFiles_CountsNotRemovedAndNotAdded`.

---

- [x] 🟢 `ProductService.java` › `MAX_CODE_GEN_ATTEMPTS` (dòng ~42)
  - Vi phạm: Quy chuẩn code sạch (Dead code / Unused constant).
  - Mô tả: Hằng số `public static final int MAX_CODE_GEN_ATTEMPTS = 5;` được khai báo nhưng không được sử dụng ở bất kỳ phương thức nào trong `ProductService.java` (do vòng lặp retry 5 lần đã được đóng gói trực tiếp trong `ProductDAO.insert`).
  - Gợi ý sửa: Sử dụng hằng số này trong comment Javadoc hoặc xóa nếu không có nhu cầu tham chiếu ngoài test suite.
  - *Đã sửa:* Đã bổ sung Javadoc rõ ràng cho hằng số theo quy chuẩn BR-55 và MSG-056, đồng thời được tham chiếu trong test suite để kiểm thử số lần thử lại tối đa.

---

- [x] 🟢 `ProductService.java` › `removeMarkedFiles()` (dòng ~520–522)
  - Vi phạm: Tối ưu hóa truy vấn CSDL và phòng thủ dữ liệu.
  - Mô tả: Vòng lặp duyệt `token` từ `removedIdsCsv` hiện tại gọi thẳng `productDAO.deleteImage(fileId, productId)` kể cả khi `fileId` không nằm trong danh sách `present` của sản phẩm. Dù DAO có điều kiện `AND product_id = ?` bảo vệ an toàn dữ liệu, việc gửi câu lệnh `DELETE` xuống DB cho các ID không hợp lệ gây lãng phí kết nối DB.
  - Gợi ý sửa: Thêm điều kiện kiểm tra tập `present` trước khi gọi DAO:
    ```java
    if (!present.contains(fileId)) {
        continue;
    }
    boolean deleted = isImage
            ? productDAO.deleteImage(fileId, productId)
            : productDAO.deleteCatalogue(fileId, productId);
    if (!deleted) {
        failed++;
    }
    ```
  - *Đã sửa:* Đã bổ sung kiểm tra `if (!present.contains(fileId)) continue;` trước khi gọi DAO, và bổ sung verify trong unit test đảm bảo không gọi deleteImage/deleteCatalogue cho ID không thuộc sản phẩm.

