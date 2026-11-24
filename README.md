# POSCS

## 1. POSCS là gì

POSCS là ứng dụng web giúp Chi nhánh Công ty CP Thiết bị Bưu điện POSTEF theo dõi khách hàng doanh nghiệp, hợp đồng và phiếu hỗ trợ kỹ thuật. Có ba vai: **Admin** (Ban giám đốc, toàn quyền, quản lý nhân viên), **Sales** (Phòng kinh doanh: khách hàng, hợp đồng, sản phẩm, phiếu hỗ trợ) và **Tổng hợp** (Phòng tổng hợp: xem dữ liệu, nhận bàn giao hợp đồng, ghi nhận kỳ thanh toán đã thu).

## 2. Công nghệ

- Java 21
- Jakarta EE 11: Servlet + JSP (JSTL)
- Apache Tomcat 11
- MySQL 8 trở lên, bảng mã `utf8mb4`
- Apache Ant (dự án NetBeans, `build.xml`)
- Node 22 trở lên: chạy test JavaScript và kịch bản chụp ảnh hướng dẫn (`tools/guide/`); app chạy không cần Node

## 3. Cấu trúc thư mục

```
src/java/poscs/
  controller/   servlet: nhận request, kiểm quyền, kiểm đầu vào, gọi service
  service/      nghiệp vụ, giao dịch, phân quyền
  validator/    kiểm tra đầu vào theo BR
  dao/          đọc, ghi MySQL
  model/        lớp dữ liệu (POJO)
  common/       tiện ích dùng chung: filter đăng nhập và CSRF, gửi thư, đọc log...
web/            JSP, CSS, JS, ảnh; cấu hình ở web/WEB-INF/web.xml
db/             schema.sql (dựng CSDL từ đầu) và migrations/ (thay đổi dần)
test/           test JUnit (test/poscs/) và test JavaScript (test/js/)
tools/          kịch bản chụp ảnh và xuất hướng dẫn sử dụng, sinh tài liệu test
docs/           đặc tả, kiến trúc, báo cáo, báo cáo QA
```

Phân tầng và luồng xử lý chi tiết: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## 4. Chạy trên máy

**Bước 1. Tạo CSDL và nạp cấu trúc.** Chạy trong Git Bash ở thư mục repo (PowerShell không có `<`). `schema.sql` xoá rồi tạo lại mọi bảng, kèm dữ liệu mẫu, nên chỉ chạy trên CSDL mới.

```bash
mysql -u root -p -e "CREATE DATABASE poscs_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql --default-character-set=utf8mb4 -u root -p poscs_db < db/schema.sql
```

**Bước 2. Đặt kết nối CSDL.** Biến môi trường, hoặc tham số `-D` của JVM (được ưu tiên hơn):

- `DB_URL`: mặc định `jdbc:mysql://localhost:3306/poscs_db`
- `DB_USER`: mặc định `root`
- `DB_PASSWORD`: bắt buộc, không có mặc định

**Bước 3. Thư.** `MAIL_USERNAME` và `MAIL_PASSWORD` (biến môi trường) không đặt thì app không gửi thư mà in mã OTP và mật khẩu tạm ra log, đủ để thử trên máy. Gửi thư thật và các biến `MAIL_*` khác: xem [DEPLOY.md](DEPLOY.md).

**Bước 4. Build và deploy.** Trong NetBeans: Clean and Build. Ngoài NetBeans, `ant dist` cần thêm hai tham số `-D` (giải thích ở [DEPLOY.md](DEPLOY.md) §2). Cả hai cách tạo ra `dist/POSCS.war`; deploy file này lên Tomcat 11.

```bash
ant -Dj2ee.platform.classpath=lib-build/jakarta.servlet-api-6.1.jar \
    -Dlibs.CopyLibs.classpath=lib-build/org-netbeans-modules-java-j2seproject-copylibstask.jar \
    dist
```

## 5. Đổi cấu trúc CSDL

Không sửa tay CSDL đang chạy: thêm một file vào `db/migrations/`, cách đặt tên và các bước xem [db/migrations/README.md](db/migrations/README.md).

## 6. Kiểm thử

```bash
ant test
ant -Dtest-sys-prop.DB_URL=jdbc:mysql://localhost:3306/poscs_it \
    -Dtest-sys-prop.DB_USER=root \
    -Dtest-sys-prop.DB_PASSWORD=matkhau_mysql \
    test
node --test 'test/js/**/*.test.js'
```

- Lệnh 1 chạy toàn bộ test Java. Lệnh 2 chạy thêm test tích hợp trên MySQL thật: tạo sẵn CSDL rỗng `poscs_it` (như lệnh `CREATE DATABASE` ở mục 4, test tự nạp `db/schema.sql`) và thay `matkhau_mysql` bằng mật khẩu của bạn. Lệnh 3 chạy test JavaScript.
- Không trỏ tới CSDL có tên đuôi `_it` thì test tích hợp tự bỏ qua; CSDL `_it` bị xoá sạch dữ liệu mỗi lần chạy.
- Ngoài NetBeans, thêm vào mỗi lệnh `ant` hai tham số `-D` như lệnh `ant dist` ở mục 4.

## 7. Log

- App ghi log vào log của Tomcat: `logs/catalina.*`.
- Admin xem và tải log ngay trong app ở màn hình Nhật ký hệ thống (`/systemLog`). Màn hình này đọc thư mục `logs/` của Tomcat; log nằm chỗ khác thì đặt `LOG_DIR` (biến môi trường hoặc `-D`).

## 8. Bảo mật

- Mọi request `POST`, kể cả đăng nhập, phải kèm mã CSRF đúng của phiên; sai thì bị từ chối.
- Đăng nhập xong thì tạo phiên mới, không dùng lại mã phiên cũ.
- Đăng nhập và quên mật khẩu trả lời như nhau dù username có thật hay không, cả câu báo lẫn thời gian phản hồi.
- Nhập sai mã OTP 5 lần thì mã bị huỷ, phải xin mã mới; gửi lại mã phải cách lần trước ít nhất 30 giây.
- Phân quyền kiểm ở server theo ma trận trong [PERMISSIONS.md](PERMISSIONS.md).

## 9. Tài liệu khác

- [docs/SPEC.md](docs/SPEC.md): đặc tả yêu cầu (UC, BR, MSG)
- [PERMISSIONS.md](PERMISSIONS.md): ma trận phân quyền
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): kiến trúc
- [DEPLOY.md](DEPLOY.md): triển khai lên máy chủ
- [rules.md](rules.md): quy chuẩn viết code của dự án
- [task_tracker.md](task_tracker.md): danh sách task
- Hướng dẫn sử dụng: trang `/guide` trong app, bản Word [docs/Report6_HuongDanSuDung.docx](docs/Report6_HuongDanSuDung.docx)
