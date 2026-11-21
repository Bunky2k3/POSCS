# Hướng dẫn đổi địa chỉ email gửi thư của POSCS

POSCS gửi hai loại thư: **mã OTP** (khi bấm "Quên mật khẩu?") và **thông tin tài khoản** cho nhân viên (nút "Gửi tài khoản" ở trang Nhân viên). Cả hai đi qua Gmail SMTP bằng một tài khoản Gmail chung.

Địa chỉ gửi **không nằm trong code**. `src/java/poscs/common/EmailUtil.java` đọc nó từ biến môi trường của máy chạy Tomcat:

| Biến | Ý nghĩa | Khi không đặt |
|---|---|---|
| `MAIL_USERNAME` | Địa chỉ Gmail dùng để gửi, cũng là người gửi hiện trong thư | Không gửi thư thật, nội dung thư in ra log (chế độ DEV) |
| `MAIL_PASSWORD` | **Mật khẩu ứng dụng** 16 ký tự của địa chỉ trên (không phải mật khẩu đăng nhập Gmail) | Như trên |
| `MAIL_FROM` | Người gửi hiển thị, chỉ đặt khi muốn khác `MAIL_USERNAME` | Lấy bằng `MAIL_USERNAME` |
| `MAIL_SMTP_HOST`, `MAIL_SMTP_PORT` | Máy chủ gửi thư | `smtp.gmail.com`, `587` |

Muốn đổi người gửi thì chỉ cần đổi `MAIL_USERNAME` và `MAIL_PASSWORD`, rồi khởi động lại Tomcat. **Không sửa code, không build lại.**

Xem máy đang dùng địa chỉ nào (PowerShell):

```powershell
[Environment]::GetEnvironmentVariable('MAIL_USERNAME','Machine')
```

---

## Bước 1. Lấy mật khẩu ứng dụng cho Gmail mới

Gmail không cho ứng dụng đăng nhập SMTP bằng mật khẩu thường. Phải tạo một **mật khẩu ứng dụng** (App password) riêng cho POSCS.

1. Đăng nhập tài khoản Gmail mới trên trình duyệt.
2. Bật **Xác minh 2 bước** nếu chưa bật: vào <https://myaccount.google.com/security>, mục "Cách bạn đăng nhập vào Google", chọn **Xác minh 2 bước** rồi làm theo hướng dẫn (cần số điện thoại). Chưa bật bước này thì không tạo được mật khẩu ứng dụng.
3. Mở <https://myaccount.google.com/apppasswords>. Google có thể hỏi lại mật khẩu.
4. Ô **Tên ứng dụng**: gõ tên dễ nhận ra, ví dụ `POSCS may dev` (máy chủ thật thì `POSCS VPS`). Bấm **Tạo**.
5. Google hiện một mã **16 chữ cái**, chia 4 nhóm, dạng `abcd efgh ijkl mnop`. **Mã chỉ hiện một lần**: chép lại ngay, **bỏ hết dấu cách** (còn 16 chữ liền), rồi bấm **Xong**.
6. Giữ mã như mật khẩu: không dán vào file trong repo, không gửi qua chat, không chụp màn hình gửi người khác.

Không thấy trang mật khẩu ứng dụng ("Không có chế độ cài đặt bạn đang tìm") thường là do:
- chưa bật Xác minh 2 bước;
- Xác minh 2 bước chỉ dùng khoá bảo mật, hoặc tài khoản bật Chương trình Bảo vệ nâng cao;
- tài khoản trường hoặc công ty (Google Workspace) mà quản trị viên đã tắt tính năng này. Trường hợp này nên dùng một Gmail cá nhân.

## Bước 2. Đặt biến môi trường trên máy Windows

Dùng giao diện của Windows để mật khẩu không bị lưu trong lịch sử lệnh.

1. Bấm Start, gõ `environment`, chọn **Edit the system environment variables** (Chỉnh sửa biến môi trường hệ thống).
2. Bấm nút **Environment Variables...**
3. Ở khung dưới **System variables** (không phải khung "User variables" phía trên):
   - Chọn `MAIL_USERNAME` → **Edit** → gõ địa chỉ Gmail mới → OK. Chưa có biến này thì bấm **New** và tạo.
   - Chọn `MAIL_PASSWORD` → **Edit** → dán mã 16 chữ ở Bước 1 → OK.
4. Bấm OK ở hai hộp thoại còn lại để lưu.

Lưu ý:
- Không tạo cùng tên biến ở khung **User variables**: biến User đè biến System, Tomcat sẽ đọc nhầm giá trị đó.
- Không đặt `MAIL_FROM`. Gmail chỉ cho gửi dưới tên chính tài khoản đó (hoặc bí danh đã xác minh trong Gmail); để trống thì người gửi tự bằng `MAIL_USERNAME`.
- Sửa ở khung System variables cần quyền quản trị. Nút Edit bị mờ thì mở lại hộp thoại bằng tài khoản Windows có quyền admin.

## Bước 3. Khởi động lại Tomcat

Biến môi trường mới chỉ có tác dụng với chương trình mở **sau** khi lưu. Tomcat đang chạy vẫn giữ địa chỉ cũ.

Cách chắc nhất: **khởi động lại máy**, rồi mở NetBeans và chạy POSCS như mọi khi.

Không muốn khởi động lại máy thì:
1. Tắt hẳn NetBeans.
2. Nếu hay mở NetBeans bằng **Flow Launcher**: thoát luôn Flow Launcher (chuột phải biểu tượng ở khay hệ thống → Exit). Flow Launcher giữ biến môi trường cũ từ lúc nó khởi động và truyền sang NetBeans.
3. Tomcat của máy này không dừng theo NetBeans. Tắt tiến trình đang giữ cổng 8080 (PowerShell):
   ```powershell
   Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess }
   ```
4. Mở NetBeans **từ Start menu**, chạy lại POSCS.

## Bước 4. Kiểm tra

1. Mở `http://localhost:8080/POSCS/`, bấm **Quên mật khẩu?**.
2. Nhập username của một tài khoản có **email cá nhân là hộp thư bạn đọc được**, bấm gửi.
3. Mở hộp thư đó (xem cả thư mục Spam). Thư OTP phải tới, và **người gửi là địa chỉ Gmail mới**.
4. Không cần đặt lại mật khẩu: bỏ trang nhập OTP, mã tự hết hạn, mật khẩu tài khoản giữ nguyên.

Đừng thử bằng nút **Gửi tài khoản** ở trang Nhân viên: nút đó cấp mật khẩu mới cho nhân viên, mật khẩu cũ mất luôn.

Không thấy thư thì xem log Tomcat (cửa sổ Output của NetBeans):

| Dòng log | Nguyên nhân | Cách sửa |
|---|---|---|
| `[DEV MODE] Chua cau hinh SMTP` | Tomcat không nhận được biến | Làm lại Bước 3; kiểm tra đã đặt ở **System variables** |
| `Loi gui email otp` kèm `535` hoặc `Username and Password not accepted` | Mã ở Bước 1 sai, còn dấu cách, hoặc đã bị thu hồi | Tạo mã mới ở Bước 1, đặt lại `MAIL_PASSWORD` |
| `Loi gui email otp` kèm lỗi kết nối, hết thời gian chờ | Mạng hoặc tường lửa chặn cổng 587 | Thử mạng khác |
| Không có dòng lỗi, thư vẫn gửi từ địa chỉ cũ | Tomcat còn chạy bằng biến cũ | Làm lại Bước 3 |

Trang Quên mật khẩu không báo lỗi gửi thư: thư được gửi ngầm sau khi trang đã trả về, nên lỗi chỉ có trong log.

## Sau khi đổi xong

- Thu hồi mật khẩu ứng dụng của Gmail cũ nếu không dùng nữa: đăng nhập Gmail cũ, mở <https://myaccount.google.com/apppasswords>, bấm biểu tượng thùng rác cạnh `POSCS ...`.
- Đổi mật khẩu đăng nhập của Gmail mới thì Google **tự thu hồi mọi mật khẩu ứng dụng** của tài khoản đó. POSCS sẽ ngừng gửi thư (chỉ báo trong log). Khi đó làm lại Bước 1, Bước 2, Bước 3.
- Gmail giới hạn khoảng 500 thư mỗi ngày cho một tài khoản. Mức này đủ cho OTP và thư tài khoản của chi nhánh.

## Khi POSCS đã chạy trên VPS bằng Docker (Giai đoạn 11)

Áp dụng sau khi Task 11.1 trong `task_tracker_deploy.md` đã xong. Biến không đặt trong Windows mà nằm trong file `deploy/.env` trên VPS.

1. Tạo mật khẩu ứng dụng riêng cho VPS (Bước 1, tên `POSCS VPS`). Đừng dùng chung mã với máy dev: thu hồi mã của máy này không làm hỏng máy kia.
2. Sửa `deploy/.env`: `MAIL_USERNAME=<Gmail mới>`, `MAIL_PASSWORD=<mã 16 chữ, không dấu cách>`. Để trống `MAIL_FROM`.
3. Trong thư mục `deploy/`, chạy `docker compose up -d tomcat`. Docker thấy biến đổi thì tự tạo lại container Tomcat.
4. Kiểm tra như Bước 4 trên tên miền thật. Log xem bằng `docker compose logs tomcat`.
