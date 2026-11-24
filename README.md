# POSCS

## 1. POSCS là gì

POSCS là ứng dụng web giúp Chi nhánh miền Bắc của Công ty CP Thiết bị Bưu điện POSTEF theo dõi khách hàng doanh nghiệp và hợp đồng. Có ba vai: **Admin** (Ban giám đốc, toàn quyền, quản lý nhân viên), **Sales** (Phòng kinh doanh: khách hàng, hợp đồng, sản phẩm) và **Tổng hợp** (Phòng tổng hợp: xem dữ liệu, nhận bàn giao hợp đồng, ghi nhận kỳ thanh toán đã thu).

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

## 4. Kiểm thử

```bash
ant test
ant -Dtest-sys-prop.DB_URL=jdbc:mysql://localhost:3306/poscs_it \
    -Dtest-sys-prop.DB_USER=root \
    -Dtest-sys-prop.DB_PASSWORD=matkhau_mysql \
    test
node --test 'test/js/**/*.test.js'
```

- Lệnh 1 chạy toàn bộ test Java. Lệnh 2 chạy thêm test tích hợp trên MySQL thật: tạo sẵn CSDL rỗng `poscs_it` (bảng mã `utf8mb4`; test tự nạp `db/schema.sql`) và thay `matkhau_mysql` bằng mật khẩu của bạn. Lệnh 3 chạy test JavaScript.
- Không trỏ tới CSDL có tên đuôi `_it` thì test tích hợp tự bỏ qua; CSDL `_it` bị xoá sạch dữ liệu mỗi lần chạy.
- Ngoài NetBeans, thêm vào mỗi lệnh `ant` hai tham số `-D` ghi ở [DEPLOY.md](DEPLOY.md) §2.

## 5. Log

- App ghi log vào log của Tomcat: `logs/catalina.*`.
- Admin xem và tải log ngay trong app ở màn hình Nhật ký hệ thống (`/systemLog`). Màn hình này đọc thư mục `logs/` của Tomcat; log nằm chỗ khác thì đặt `LOG_DIR` (biến môi trường hoặc `-D`).

## 6. Bảo mật

- Mọi request `POST`, kể cả đăng nhập, phải kèm mã CSRF đúng của phiên; sai thì bị từ chối.
- Đăng nhập xong thì tạo phiên mới, không dùng lại mã phiên cũ.
- Đăng nhập và quên mật khẩu trả lời như nhau dù username có thật hay không, cả câu báo lẫn thời gian phản hồi.
- Nhập sai mã OTP 5 lần thì mã bị huỷ, phải xin mã mới; gửi lại mã phải cách lần trước ít nhất 30 giây.
- Phân quyền kiểm ở server theo ma trận trong [PERMISSIONS.md](PERMISSIONS.md).

## 7. Tài liệu khác

- [docs/SPEC.md](docs/SPEC.md): đặc tả yêu cầu (UC, BR, MSG)
- [PERMISSIONS.md](PERMISSIONS.md): ma trận phân quyền
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): kiến trúc
- [DEPLOY.md](DEPLOY.md): dựng CSDL, build và triển khai
- [rules.md](rules.md): quy chuẩn viết code của dự án
- [task_tracker.md](task_tracker.md): danh sách task
- Hướng dẫn sử dụng: trang `/guide` trong app, bản Word [docs/Report6_HuongDanSuDung.docx](docs/Report6_HuongDanSuDung.docx)
