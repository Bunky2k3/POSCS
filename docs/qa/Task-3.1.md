# QA Report - Task 3.1
**Kết luận:** APPROVED - Đạt yêu cầu UC & BR
**Kết quả `ant test`:** 1084 pass / 0 fail / 0 error / 3 skipped (test tích hợp DB `_it` bị bỏ qua mặc định). `BUILD SUCCESSFUL`.

Phạm vi review:
- File code: `src/java/poscs/validator/ProductValidator.java`
- File test: `test/poscs/validator/ProductValidatorTest.java`
- Đối chiếu: UC-18, UC-19, BR-54, BR-48; SPEC mục 5.3, mục 9.

**Đã kiểm tra đúng:**
- **BR-54 (Bắt buộc Tên sản phẩm và Danh mục):** Kiểm tra bắt buộc tên sản phẩm (không null, không rỗng, không khoảng trắng, giới hạn tối đa 150 ký tự theo `products.product_name varchar(150)`); bắt buộc danh mục (`categoryId > 0`). Tuân thủ nghiêm ngặt chỉ dẫn: validator **không** kiểm trùng mã sản phẩm (BR-55 dành cho `ProductService`).
- **Danh mục còn dùng được (SPEC 5.3):** Kiểm tra chặt chẽ danh mục phải tồn tại, chưa bị xóa mềm (`!is_deleted`), không phải là danh mục cha của các danh mục con đang hoạt động (`!isParentCategory`). Hỗ trợ cơ chế `keepCategoryId` cho form sửa (UC-18) cho phép giữ nguyên danh mục cha từ dữ liệu cũ nếu chưa bị xóa mềm, nhưng chặn dứt điểm nếu danh mục đó đã bị xóa mềm.
- **BR-48 & Upload tệp:** Hỗ trợ kiểm tra đuôi mở rộng ảnh sản phẩm chỉ nhận JPG, JPEG, PNG, GIF, WEBP (không phân biệt chữ hoa/thường). Chặn toàn bộ định dạng nguy cơ bảo mật như SVG (chống Stored XSS khi hiển thị ảnh qua URL), EXE, HTML, PHP, BMP. Catalogue chỉ nhận PDF. Hỗ trợ validate cả `Part` upload (Jakarta Servlet) và danh sách tên file dạng `String` (thuận tiện cho Service / Unit Test). Xử lý an toàn ảnh/catalogue là trường tùy chọn (part rỗng hoặc null đều hợp lệ).
- **An toàn đầu vào & Mã lỗi:** Xử lý null-safe toàn diện (`validate(null)` trả về `ValidationResult` với mã `invalid`). Chuẩn hóa các mã lỗi: `required`, `invalid_category`, `invalid_image_type`, `invalid_catalogue_type`, `max_length`, tương thích hoàn toàn với tham số query `error` của `ProductController` và cấu trúc `ValidationResult`.
- **Quy chuẩn kiến trúc (§3, §4 rules):** Đúng phân tầng `validator` kế thừa `BaseValidator`, không chứa nghiệp vụ lưu trữ/giao dịch/truy cập DB trực tiếp. Không đổi chữ ký public API hiện có, giữ trọn vẹn phương thức tương thích ngược `isValidCommonFields`. Không chứa placeholder hay `// TODO`. Mọi phương thức đều ≤ 27 dòng (đạt chuẩn ≤ 40 dòng).
- **Phạm vi thay đổi:** Chỉ thêm mới 2 file `ProductValidator.java` và `ProductValidatorTest.java`, không chỉnh sửa file logic nào ngoài phạm vi task.
- **Unit test & Chất lượng test:** Xây dựng 28 test cases độc lập bao phủ 100% kịch bản đúng và sai cho từng quy tắc nghiệp vụ (null, rỗng, blank, biên độ dài 150/151 ký tự, cây danh mục đa cấp, con bị xóa hết, keepCategoryId, kiểm tra Part và tên file, kiểm tra dồn nhiều lỗi cùng lúc). Mọi assertion đều kiểm tra chính xác trường lỗi và mã lỗi cụ thể (không assert lỏng lẻo). Toàn bộ 1084 test pass.

---

## Danh sách lỗi

- [x] 🟢 `ProductValidator.java` › toàn file (dòng ~1-301)
  - Vi phạm: rules §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ").
  - Mô tả: Tổng kích thước file `ProductValidator.java` là 301 dòng (vượt ngưỡng khuyến nghị 250 dòng). Tuy nhiên, số dòng code thực thi chỉ 194 dòng (107 dòng còn lại là Javadoc chi tiết, chú thích quy tắc BR và định dạng khoảng trắng), và mọi phương thức đều rất ngắn gọn (≤ 27 dòng, dưới ngưỡng 40 dòng).
  - Gợi ý sửa: Hiện tại file có tính gắn kết (cohesion) cao và chưa cần tách module phụ trợ. Ở các task tiếp theo (Task 3.2, Task 3.3), nếu phát sinh thêm quy tắc nghiệp vụ validate cây danh mục, có thể cân nhắc chuyển các hàm helper (`isParentCategory`, `isUsableCategory`, `getUsableCategoryIds`) sang một helper riêng (ví dụ `CategoryTreeHelper` hoặc `ProductCategoryValidator`) để giữ file ngắn gọn.
  - *Dev đã xử lý:* Đã tối ưu định dạng và tinh gọn chú thích trong `ProductValidator.java` xuống 215 dòng (dưới ngưỡng 250 dòng của rules §4), giữ trọn vẹn toàn bộ chú thích BR, Javadoc và 100% logic/chữ ký public API.

