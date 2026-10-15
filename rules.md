# Project Rules & Guidelines - POSCS

> Quy chuẩn chi tiết: [.gemini/rules.md](.gemini/rules.md). **Nếu hai file mâu thuẫn, file này được ưu tiên.**
> Môi trường: Windows / PowerShell, Java + Jakarta EE (Servlet, JSP, JSTL), MySQL, build bằng Apache Ant (NetBeans project).

## 0. Lệnh của dự án

Ant **không nằm trong PATH**. Luôn gọi bằng đường dẫn đầy đủ (mỗi lệnh phải tự chạy được, không dựa vào biến đã đặt trước):

- Biên dịch: `& "C:\Program Files\NetBeans-25\netbeans\extide\ant\bin\ant.bat" compile`
- Kiểm tra JSP (**chạy sau MỖI lần sửa file `.jsp`**): `& "C:\Program Files\NetBeans-25\netbeans\extide\ant\bin\ant.bat" compile-jsps`
- Chạy toàn bộ test: `& "C:\Program Files\NetBeans-25\netbeans\extide\ant\bin\ant.bat" test`
- Chạy một lớp test: `& "C:\Program Files\NetBeans-25\netbeans\extide\ant\bin\ant.bat" test-single -Dtest.includes=poscs/service/AuthServiceTest.java`
- Đóng gói WAR: `& "C:\Program Files\NetBeans-25\netbeans\extide\ant\bin\ant.bat" dist`

**Test tích hợp (`test/poscs/integration/`):**
- `ant test` mặc định **bỏ qua (skipped)** các test này, không đụng DB. "Pass" ở đây chưa kiểm tra DB thật.
- Chúng chỉ chạy trên CSDL tên kết thúc bằng `_it` và **xóa sạch dữ liệu của DB đó**. Chỉ người dùng được bật (xem `README.md`).
- Agent **KHÔNG** tự chạy test tích hợp, **KHÔNG** tự tạo DB, **KHÔNG** hỏi, ghi hay lưu mật khẩu DB. Task cần test tích hợp mà chưa có DB/mật khẩu: dừng và báo người dùng.

## 1. Nguồn chân lý (Single Source of Truth)

- Mọi logic nghiệp vụ đối chiếu `docs/SPEC.md`. Số hiệu UC / BR / MSG / NFR trong SPEC là chuẩn tham chiếu.
- **Không tự suy diễn, không tự thêm tính năng** ngoài SPEC.
- Mục đánh dấu `[CẦN XÁC NHẬN]` trong SPEC, hoặc SPEC mơ hồ/thiếu/mâu thuẫn: **DỪNG và hỏi người dùng**, không tự đoán.
- **Agent không sửa `docs/SPEC.md`.** Phát hiện sai lệch giữa SPEC và code thì liệt kê cho người dùng. Cấu trúc code mới ghi vào `docs/ARCHITECTURE.md`.
- Các quyết định đã chốt (không làm lại, không đề xuất lại):
  - **Không có vai trò CS.** Chỉ 3 vai trò: Admin, Sales, Kỹ thuật.
  - **Không làm BR-44** (thiết bị trong phiếu). Phiếu gắn với hợp đồng; thiết bị/lỗi ghi ở Mô tả/ghi chú. Không tạo `DeviceDAO`, không làm giao diện thiết bị.
  - Không làm BR-50, BR-51, và không làm nhập/xuất PDF từng hợp đồng (UC-40 đã gỡ).
  - Mã khách hàng `KH-xxxx`, mã nhà cung cấp `NCC-xxx`.

## 2. Quy trình thực thi

1. Đọc `task_tracker.md`, chọn task `[ ]` **đầu tiên theo thứ tự** mà không bị chặn bởi một quyết định còn mở.
2. Chỉ làm **ĐÚNG 1 task** mỗi lượt. Không gộp, không làm trước task sau.
3. Đọc các mục SPEC được task gắn nhãn (UC, BR) trước khi viết code.
4. Viết code và test, sau đó chạy **toàn bộ** `ant test` (và `compile-jsps` nếu có sửa JSP). Chỉ khi **tất cả test hiện có pass** mới được đánh `[x]` trong `task_tracker.md`, kèm ghi chú ngắn các file đã sửa.
5. Cuối lượt báo cáo ngắn: đã làm gì, file đã đổi, kết quả test, điểm chưa chắc chắn.

## 3. Ranh giới cấm (bắt buộc tuân thủ)

**Về code**
- Không dùng placeholder trong code viết ra: `// ... giữ nguyên code cũ`, `// rest of the code`, `// TODO`. Khi sửa file, chỉ sửa đúng đoạn cần thiết nhưng đoạn đó phải đầy đủ và chạy được. Khi tạo file mới hoặc in code ra, phải in đầy đủ.
- **Không sửa test để che lỗi.** Test fail thì sửa logic ở mã nguồn. Không sửa assertion, không xóa test case hiện có.
- **Không đổi chữ ký** (tên hàm, kiểu trả về, tham số) của public API/Service nếu SPEC hoặc task không yêu cầu rõ.
- Task refactor: **không đổi hành vi**. Nếu chưa có test khóa hành vi hiện tại thì viết trước khi tách.

**Về an toàn hệ thống (hỏi trước, chờ đồng ý mới làm)**
- Trước khi **xóa hoặc sửa bất cứ thứ gì ngoài phạm vi file của task**, phải báo trước: xóa/sửa gì, vì sao, ảnh hưởng ra sao, rồi chờ người dùng đồng ý.
- Luôn hỏi trước khi chạy: `ant clean` (xóa `build/`), `run`, `debug`, `profile`, deploy/undeploy, `rm -rf`, `git reset --hard`, `git push --force`.
- Không đổi cấu hình hệ thống, biến môi trường, hay PATH của máy.
- Không thêm/xóa/sửa bảng hoặc cột DB, không chạy file `.sql` lên DB nếu task không yêu cầu rõ. Tên bảng, cột lấy từ `db/schema.sql`, không tự bịa.

**Về bảo mật**
- Không hardcode mật khẩu, khóa, secret. Không commit `.env` hay file chứa thông tin kết nối thật.
- Mật khẩu luôn băm BCrypt. Chống CSRF với mọi form thay đổi dữ liệu.

## 4. Tiêu chuẩn code & kiến trúc

- Phân tầng, mỗi tầng một việc:

| Gói | Vai trò |
|---|---|
| `view` (JSP) | Chỉ hiển thị, **không chứa logic nghiệp vụ** |
| `controller` | Kiểm quyền, nhận và kiểm đầu vào, gọi `service`; không chứa nghiệp vụ |
| `service` (`poscs.service`) | Nghiệp vụ và giao dịch |
| `validator` (`poscs.validator`) | Kiểm tra đầu vào theo BR |
| `dao` | Truy cập DB; mọi thao tác ghi nhiều bảng chạy **trong một giao dịch** |
| `model`, `common` | POJO và tiện ích |

- Công nghệ: Java 21, Jakarta EE 11. **Luôn dùng `jakarta.*`** (`jakarta.servlet.*`, `jakarta.mail.*`), **không bao giờ dùng `javax.*`**. Build bằng **Ant, không dùng Maven**.
- Truy vấn DB **chỉ dùng `PreparedStatement`**, không nối chuỗi SQL.
- Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ.
- Mọi logic mới hoặc nhánh mới (`if/else`, trường hợp biên) có test tương ứng trong `test/`.
- Phân quyền chỉ qua `PermissionService`/`AccessControl`, không rải `if (role == ...)` trong controller. Phân biệt **Sales quản lý vùng** (không có cấp trên, được ghi) với **Sales có cấp trên** (chỉ xem, gửi yêu cầu thay đổi). Vi phạm quyền: trả `MSG-022`.
- Xóa dữ liệu là **xóa mềm** (`is_deleted`) cho khách hàng, sản phẩm, hợp đồng, phiếu, tài liệu hợp đồng.
- Văn bản hiển thị dùng đúng nội dung `MSG-xxx` trong SPEC. Mọi file UTF-8 (tiếng Việt).

## 5. Xử lý lỗi (Error Handling Loop)

- Test hoặc build fail: tối đa tự sửa **2 lần**, sửa ở **logic nguồn**.
- Vẫn fail sau 2 lần: **dừng, giữ nguyên trạng thái**, in log lỗi chi tiết kèm giả thuyết nguyên nhân để người dùng can thiệp. Không đánh `[x]`.
- Không "vá" bằng cách tắt test, bỏ qua lỗi (`@Ignore`, `try/catch` nuốt lỗi) hay nới điều kiện assertion.
