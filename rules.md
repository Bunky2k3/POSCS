# Project Rules & Guidelines - POSCS

> Đây là file quy chuẩn chính của dự án. Nếu prompt của người dùng mâu thuẫn với file này, **prompt của người dùng được ưu tiên**, trừ các mục ở §3 (Ranh giới cấm).
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
  - Ngưỡng thông báo hợp đồng sắp hết hạn: **30 ngày** (BR-17); phiếu: **24 giờ** trước hạn SLA.
  - **UC-50, UC-51** (Change Request): giữ nguyên `ChangeRequestDAO` và `ChangeRequestController` đã có độc lập, không tách lại.
  - **UC-54** (chữa sai sót HĐ đã ký): tích hợp vào `ContractService` (Task 4.10) và `ContractActionController` (Task 4.12), không tạo controller riêng.
  - Luồng chi tiết **UC-36 → UC-54**: lấy hành vi code và JSP hiện có làm chuẩn, chỉ tách nhỏ theo rules §3, không thêm tính năng.

## 2. Quy trình thực thi

Mỗi task đi qua 4 bước, **mỗi bước một lượt chat và dừng chờ người dùng**:

| Bước | Vai trò | Kết quả | Được làm |
|---|---|---|---|
| 1. Code | Dev | Code + test, `ant test` pass | Sửa code/test trong phạm vi task |
| 2. Review | QA (chat mới) | `docs/qa/Task-X.X.md` | **Chỉ đọc code**, chỉ ghi file report |
| 3. Fix | Dev | Code sửa theo report, report được tick | Sửa code/test, cập nhật report |
| 4. Commit | Dev | `[x]` trong tracker, commit, push | Git, `task_tracker.md` |

Chi tiết bước Code:

1. Làm đúng task người dùng chỉ định. Nếu người dùng không chỉ định, chọn task `[ ]` **đầu tiên theo thứ tự** trong `task_tracker.md` mà không bị chặn bởi một quyết định còn mở.
2. Chỉ làm **ĐÚNG 1 task** mỗi lượt. Không gộp, không làm trước task sau.
3. Đọc các mục SPEC được task gắn nhãn (UC, BR), `db/schema.sql` (nếu có truy cập DB) và code liên quan **trước khi viết code**.
4. Viết code và test, sau đó chạy **toàn bộ** `ant test` (và `compile-jsps` nếu có sửa JSP).
5. Khi tất cả test pass: **DỪNG và báo cáo**. **Không đánh `[x]`, không commit** cho đến khi người dùng yêu cầu ở bước Commit.
6. Báo cáo cuối lượt ngắn gọn: đã làm gì, file đã đổi, kết quả test (số pass/fail/skipped), điểm chưa chắc chắn.

## 3. Ranh giới cấm (bắt buộc tuân thủ, prompt không ghi đè được)

**Về code**
- Không dùng placeholder trong code viết ra: `// ... giữ nguyên code cũ`, `// rest of the code`, `// TODO`. Khi sửa file, chỉ sửa đúng đoạn cần thiết nhưng đoạn đó phải đầy đủ và chạy được. Khi tạo file mới hoặc in code ra, phải in đầy đủ.
- **Không sửa test để che lỗi.** Test fail thì sửa logic ở mã nguồn. Không sửa assertion, không xóa test case hiện có.
- **Không đổi chữ ký** (tên hàm, kiểu trả về, tham số) của public API/Service nếu SPEC hoặc task không yêu cầu rõ.
- Task refactor: **không đổi hành vi**. Nếu chưa có test khóa hành vi hiện tại thì viết trước khi tách.
- **Không thêm thư viện/dependency** (file `.jar`, thay đổi `lib/`, `nbproject/project.properties`) nếu SPEC không ghi rõ. Cần thư viện mới: dừng và hỏi.

**Về file**
- Mọi file UTF-8 (tiếng Việt).
- Tạo/sửa file bằng **công cụ chỉnh sửa file của agent**. Không ghi file bằng redirect của PowerShell (`>`, `>>`, `Out-File`, `Set-Content` không chỉ định encoding) vì dễ ra UTF-16 và hỏng tiếng Việt.

**Về an toàn hệ thống (hỏi trước, chờ đồng ý mới làm)**
- Trước khi **xóa hoặc sửa bất cứ thứ gì ngoài phạm vi file của task**, phải báo trước: xóa/sửa gì, vì sao, ảnh hưởng ra sao, rồi chờ người dùng đồng ý.
- Luôn hỏi trước khi chạy: `ant clean` (xóa `build/`), `run`, `debug`, `profile`, deploy/undeploy, `rm -rf` / `Remove-Item -Recurse`, `git reset --hard`, `git push --force`, `git rebase`.
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

- Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ.
- Mọi logic mới hoặc nhánh mới (`if/else`, trường hợp biên) có test tương ứng trong `test/`.
- Comment cho logic nghiệp vụ không hiển nhiên, ghi kèm mã BR liên quan (ví dụ `// BR-09: ...`). Không comment từng dòng hiển nhiên.
- Phân quyền chỉ qua `PermissionService`/`AccessControl`, không rải `if (role == ...)` trong controller. Phân biệt **Sales quản lý vùng** (không có cấp trên, được ghi) với **Sales có cấp trên** (chỉ xem, gửi yêu cầu thay đổi). Vi phạm quyền: trả `MSG-022`.
- Xóa dữ liệu là **xóa mềm** (`is_deleted`) cho khách hàng, sản phẩm, hợp đồng, phiếu, tài liệu hợp đồng.
- Văn bản hiển thị dùng đúng nội dung `MSG-xxx` trong SPEC.

## 5. Xử lý lỗi (Error Handling Loop)

- Test hoặc build fail: tối đa tự sửa **2 lần**, sửa ở **logic nguồn**.
- Vẫn fail sau 2 lần: **dừng, giữ nguyên trạng thái**, in log lỗi chi tiết kèm giả thuyết nguyên nhân để người dùng can thiệp. Không đánh `[x]`.
- Không "vá" bằng cách tắt test, bỏ qua lỗi (`@Ignore`, `try/catch` nuốt lỗi) hay nới điều kiện assertion.

## 6. Git

- **Không commit, không push** khi người dùng chưa yêu cầu.
- Trước khi commit: chạy lại `ant test` toàn bộ, chạy `git status`, **liệt kê từng file sẽ commit** và chỉ `git add` đúng các file đó. Không dùng `git add .` / `git add -A`.
- Không commit: `.env`, file cấu hình kết nối thật, `build/`, `dist/`, file log, file tạm của IDE.
- Commit message: `<type>(<module>): <mô tả ngắn tiếng Việt> (Task X.X)`
  - `type`: `feat` (tính năng), `fix` (sửa lỗi), `test` (chỉ thêm test), `refactor`, `docs`, `chore`.
  - Ví dụ: `feat(customer): thêm CustomerValidator kiểm tra SĐT, email, mã KH (Task 2.1)`
- Push lên branch hiện tại. Nếu branch hiện tại là `main`/`master`: **dừng và hỏi** trước khi push.
- Push bị từ chối (remote có commit mới): dừng và báo, không tự `pull --rebase` hay `push --force`.

## 7. Báo cáo QA

- Lưu tại `docs/qa/Task-X.X.md` (một file mỗi task), **không xóa** sau khi fix để giữ lịch sử review.
- Mỗi lỗi có mức độ: 🔴 Critical (sai BR/bảo mật, bắt buộc sửa) / 🟡 Major (thiếu test, sai quy chuẩn §3–§4) / 🟢 Minor (đặt tên, style).
- Bước Fix đánh `- [x]` cho mục đã sửa. Mục Dev không đồng ý thì giữ `- [ ]` và ghi lý do ngay dưới mục đó.
- File report được commit cùng code của task.
