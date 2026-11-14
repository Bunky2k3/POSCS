# QA Report - Task 6.5
**Kết luận:** APPROVED - Đạt yêu cầu UC & BR
**Kết quả `ant test`:** 2024 pass / 0 fail / 3 skipped (tổng 2027 tests; 3 skipped là test tích hợp `_it`, không chạy DB thật theo rules §0; BUILD SUCCESSFUL)

**Phạm vi review:**
- File code: `src/java/poscs/controller/SystemLogController.java`, `src/java/poscs/common/LogFiles.java`
- File test: `test/poscs/controller/SystemLogControllerTest.java`, `test/poscs/common/LogFilesTest.java`
- Đối chiếu SPEC: UC-43 và NFR-17 trong `docs/SPEC.md`; quy chuẩn kiến trúc và bảo mật trong `rules.md`, `.gemini/rules.md`.

## Đã kiểm tra và đạt
- **Nghiệp vụ UC-43 & Phân quyền Admin:**
  - Màn hình xem và tải nhật ký hệ thống được bảo vệ tập trung bằng `AccessControl.requireAdmin(request, response)` ngay đầu `doGet`, áp dụng đồng nhất cho cả xem log trên trình duyệt và tải file (`action=download`).
  - Mọi trường hợp truy cập không hợp lệ (chưa đăng nhập / session null, vai trò Sales, Kỹ thuật, người dùng không có role hoặc bị khóa/xóa) đều bị chặn ngay lập tức với HTTP 403 Forbidden và chuyển hướng hiển thị thông điệp MSG-022 ("Không có quyền truy cập").
- **Yêu cầu kỹ thuật NFR-17 (Đọc tối đa 512 KB cuối tệp):**
  - Hằng số `LogFiles.MAX_TAIL_BYTES = 512 * 1024` quy định trần đọc 512 KB. Phương thức `LogFiles.tail(file, maxLines)` sử dụng `RandomAccessFile.seek()` từ vị trí `Math.max(0, length - MAX_TAIL_BYTES)`, chỉ cấp phát và đọc đúng mảng byte trong phạm vi 512 KB cuối của tệp, ngăn ngừa hoàn toàn nguy cơ cạn kiệt RAM / DoS khi file log catalina lên tới hàng trăm MB.
  - Unit test `tail_hugeFile_doesNotReadLinesBeyond512KbLimit_NFR17` kiểm chứng với tệp giả lập ~1.08 MB (60.000 dòng), kết quả chỉ đọc các dòng thuộc 512 KB cuối, không bao giờ đọc vượt trần.
- **An toàn đầu vào & Phòng chống lỗ hổng:**
  - Phòng chống Path Traversal: Tham số `file` được kiểm duyệt chặt chẽ qua regex `[A-Za-z0-9._-]{1,120}` (chặn ký tự `/`, `\`, `..`), đồng thời bắt buộc phải đối chiếu khớp với danh sách tệp log quét được từ thư mục thực tế (`LogFiles.resolve`). Mọi yêu cầu file không tồn tại hoặc sai định dạng đều chuyển hướng an toàn về `/systemLog?error=notfound`.
  - Tham số `lines` được kẹp biên an toàn trong đoạn [1, 2000] (`DEFAULT_LINES = 200`, `MAX_LINES = 2000`), không để người dùng kéo quá nhiều dòng gây đơ trình duyệt.
  - Tham số `keyword` được chuẩn hóa, trim khoảng trắng về null và lọc không phân biệt hoa thường (`Locale.ROOT`).
  - Phòng chống XSS: Giao diện `systemLog.jsp` sử dụng `fn:escapeXml(line)` cho từng dòng log và `<c:out>` cho mọi biến context, vô hiệu hóa hoàn toàn nguy cơ chèn mã độc thông qua nội dung log.
  - Tải file an toàn: Thiết lập MIME type `application/octet-stream`, `Content-Disposition: attachment; filename="..."` và `X-Content-Type-Options: nosniff` để trình duyệt không tự ý thực thi nội dung log như script/HTML.
- **Quy chuẩn kiến trúc (§3, §4 rules):**
  - Đúng phân tầng: Controller chỉ điều phối và kiểm soát quyền/đầu vào, ủy quyền đọc tệp cho `LogFiles`, hiển thị qua JSP.
  - Không có logic nghiệp vụ trong JSP.
  - Mọi phương thức đều ≤ 38 dòng (đáp ứng tiêu chuẩn ≤ 40 dòng).
  - Độ dài tệp `SystemLogController.java` (150 dòng) và `LogFiles.java` (197 dòng) đều ≤ 250 dòng.
  - Không có placeholder, không có `// TODO`, không đổi chữ ký public API.
- **Unit test bao phủ:**
  - Bổ sung đầy đủ kịch bản cho cả luồng thành công và ngoại lệ (vai trò không hợp lệ, session hết hạn, từ khóa không khớp, từ khóa khoảng trắng, thư mục log rỗng, tên file rác, file quá khổ NFR-17).
  - `SystemLogControllerTest` (22 tests pass), `LogFilesTest` (16 tests pass). Toàn bộ 2027 tests của hệ thống pass 100% (2024 pass, 0 fail, 3 skipped).

## Danh sách lỗi
Không có lỗi nào.
