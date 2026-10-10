# POSCS - Đặc tả Yêu cầu & Thiết kế Hệ thống (System Specification)

> **Tài liệu Single Source of Truth của dự án POSCS**  
> Trích xuất và chuẩn hóa từ `docs/Project_Report_v8.docx` (Phần III: SRS & Phần IV: SDS), `PERMISSIONS.md`, `README.md`, và `db/schema.sql`.

---

## 1. Giới thiệu & Phạm vi Hệ thống

- **Tên hệ thống:** POSCS (Phần mềm Quản lý Khách hàng Doanh nghiệp và Hợp đồng cho Công ty CP Thiết bị Bưu điện POSTEF).
- **Mục tiêu:** Quản trị tập trung quan hệ khách hàng B2B, quản lý vòng đời hợp đồng mua/bán, tiếp nhận và xử lý yêu cầu hỗ trợ kỹ thuật, theo dõi doanh thu và công việc phòng ban.
- **Công nghệ nền tảng:** Java 21, Jakarta EE 11 (Servlet, JSP, JSTL), MySQL 8.x/9.x, HikariCP, Apache Ant (NetBeans project).

---

## 2. Các Tác nhân & Ma trận Phân quyền (RBAC Matrix)

Hệ thống có 4 vai trò chính:
1. **Admin (Quản trị viên):** Toàn quyền cấu hình hệ thống, quản lý nhân viên, phân công địa bàn, xem toàn bộ dữ liệu, xem log kiểm toán, chữa sai sót nhập liệu hợp đồng đã ký.
2. **Sales (Nhân viên Kinh doanh / Trưởng phòng KD):** Quản lý khách hàng, lập hợp đồng (bán/mua), lập phụ lục, theo dõi thanh toán, chuyển giao bàn giao hợp đồng.
3. **Kỹ thuật (Nhân viên / Trưởng phòng Kỹ thuật):** Quản lý danh mục sản phẩm/catalogue, tiếp nhận và xử lý phiếu hỗ trợ kỹ thuật, xác nhận bàn giao kỹ thuật.
4. **CS / Hỗ trợ khách hàng (Customer Support):** Tiếp nhận yêu cầu hỗ trợ từ khách hàng, tạo phiếu ticket, theo dõi trạng thái phản hồi.

### Ma trận Quyền hạn theo Chức năng:
| Phân hệ / Chức năng | Admin | Sales | Kỹ thuật | CS |
| :--- | :---: | :---: | :---: | :---: |
| Đăng nhập, Đổi MK, Quên MK, Hồ sơ cá nhân | X | X | X | X |
| Xem Trang tổng quan (Dashboard) | X | X | X | X |
| Quản lý Nhân viên & Gán địa bàn | X | - | - | - |
| Xem danh sách & Chi tiết Khách hàng | X | X | X | X |
| Thêm / Sửa / Xóa Khách hàng | X | X (*) | - | X (*) |
| Xuất Excel Khách hàng | X | X | X | X |
| Đánh giá khách hàng theo vòng đời | X | X (*) | - | - |
| Quản lý Sản phẩm (Thêm, Sửa, Xóa, Ảnh, Catalogue) | X | - | X | - |
| Xem danh mục Sản phẩm | X | X | X | X |
| Quản lý Hợp đồng (Tạo, Sửa, Ký, Hủy, Bàn giao, Kỳ TT) | X | X (*) | - | - |
| Xem danh sách & Chi tiết Hợp đồng | X | X | X | X |
| Xuất Excel Hợp đồng | X | X | X | X |
| Quản lý Phiếu hỗ trợ kỹ thuật (Tạo, Sửa, Đổi trạng thái) | X | X | X | X |
| Xóa Phiếu hỗ trợ | X | X (*) | - | - |
| Xử lý / Cập nhật kỹ thuật Ticket | X | - | X | - |
| Quản lý Thiết bị trong Ticket (BR-44) | X | - | X | X |
| Quản lý Thông báo & Nhật ký hệ thống | X | X (TB) | X (TB) | X (TB) |
| Phê duyệt Yêu cầu thay đổi (Change Request) | X (Manager) | - | - | - |

*(*) Phụ thuộc vào phân công địa bàn và quyền sở hữu bản ghi.*

---

## 3. Danh sách 35 Use Cases (UC-01 đến UC-35)

### 3.1 Xác thực & Tài khoản
- **UC-01:** Đăng nhập hệ thống (username + password, giới hạn 5 lần/IP khóa 15p).
- **UC-02:** Đăng xuất.
- **UC-03:** Đổi mật khẩu (mật khẩu cũ hợp lệ, mật khẩu mới khác cũ).
- **UC-04:** Quên mật khẩu (nhập username, gửi OTP về email).
- **UC-05:** Xác thực OTP cho quên mật khẩu (OTP 6 số, hiệu lực 5 phút, dùng 1 lần).
- **UC-06:** Xem chi tiết hồ sơ cá nhân.
- **UC-07:** Cập nhật hồ sơ cá nhân (họ tên, email, số điện thoại VN, địa chỉ).

### 3.2 Khách hàng & Đối tác (Enterprises)
- **UC-08:** Xem danh sách khách hàng / nhà cung cấp (lọc theo loại, địa bàn, phân trang).
- **UC-09:** Xem chi tiết thông tin khách hàng / nhà cung cấp (thông tin 360°, người liên hệ, hợp đồng, ticket).
- **UC-30:** Thêm khách hàng / nhà cung cấp (tự sinh mã `KH-xxxx` hoặc `NCC-xxxx`).
- **UC-31:** Cập nhật thông tin khách hàng / nhà cung cấp.
- **UC-32:** Xóa khách hàng / nhà cung cấp (chỉ xóa khi không có ràng buộc hợp đồng/ticket).

### 3.3 Sản phẩm & Danh mục (Products)
- **UC-12:** Xem danh sách sản phẩm (dạng thẻ Card Grid, tìm kiếm, lọc danh mục).
- **UC-13:** Xem chi tiết thông tin sản phẩm (mô tả, danh mục, hình ảnh, catalogue, hợp đồng sử dụng).
- **UC-18:** Cập nhật sản phẩm.
- **UC-19:** Thêm sản phẩm mới (tự sinh mã sản phẩm duy nhất).
- **UC-20:** Xóa sản phẩm (hộp thoại xác nhận, không xóa khi đã nằm trong hợp đồng).

### 3.4 Hợp đồng (Contracts)
- **UC-10:** Xem danh sách hợp đồng (phân trang, lọc theo loại Mua/Bán, trạng thái, tiến độ).
- **UC-11:** Xem chi tiết hợp đồng (sản phẩm, kỳ thanh toán, chặng bàn giao, tài liệu, liên kết bán-mua, lịch sử).
- **UC-33:** Tạo hợp đồng mới (hợp đồng bán/mua, không nhận ngày ký trước).
- **UC-34:** Hủy bản ghi hợp đồng (chỉ khi hợp đồng ở trạng thái dự thảo/chưa ký).
- **UC-35:** Cập nhật hợp đồng (thông tin chung, sản phẩm, điều khoản).
- *(Mở rộng)*: Ký hợp đồng, Lập phụ lục (`parent_contract_id`), Liên kết hợp đồng bán-mua, Quản lý bàn giao giữa các phòng ban, Quản lý kỳ thanh toán, Quản lý tài liệu đính kèm, Xuất Excel.

### 3.5 Phiếu Hỗ trợ Kỹ thuật (Technical Support Tickets)
- **UC-16:** Xem danh sách phiếu hỗ trợ kỹ thuật (bảng dữ liệu, tìm kiếm, lọc mức độ, trạng thái).
- **UC-17:** Xem chi tiết phiếu hỗ trợ kỹ thuật (thông tin chung, người tạo, KTV xử lý, SLA, lịch sử trạng thái).
- **UC-21:** Tạo phiếu hỗ trợ kỹ thuật (mặc định Mới tiếp nhận, cờ bảo hành mặc định Có).
- **UC-22:** Cập nhật phiếu hỗ trợ kỹ thuật (chuyển trạng thái: Mới tiếp nhận → Đang xử lý → Đã đóng; ghi log lịch sử).
- **UC-23:** Xóa phiếu hỗ trợ kỹ thuật (xác nhận, không được xóa phiếu Đang xử lý).
- *(Mở rộng - BR-44)*: Quản lý danh sách thiết bị gặp sự cố (`technicalrequestdevices`).

### 3.6 Thông báo & Nhật ký
- **UC-14:** Xem danh sách thông báo.
- **UC-15:** Đánh dấu thông báo đã đọc.
- **Hệ thống:** Bộ lập lịch quét nhắc hạn hợp đồng / thanh toán / ticket (`NotificationScheduler`), Xem log hệ thống (`SystemLogController`).

### 3.7 Quản lý Nhân viên (Employees)
- **UC-24:** Xem danh sách nhân viên.
- **UC-25:** Xem chi tiết nhân viên (hồ sơ, địa bàn phụ trách, phòng ban, vai trò).
- **UC-26:** Thêm nhân viên mới.
- **UC-27:** Cập nhật thông tin & vai trò nhân viên.
- **UC-28:** Gửi thông tin tài khoản qua email.
- **UC-29:** Khóa / Mở khóa tài khoản nhân viên.

---

## 4. Bảng Quy tắc Nghiệp vụ Trọng yếu (Business Rules)

- **BR-01:** Sau 5 lần đăng nhập thất bại từ 1 IP, khóa IP trong 15 phút.
- **BR-02:** Chỉ đăng nhập bằng username.
- **BR-03 - BR-05:** OTP 6 số gửi qua email, hiệu lực 5 phút, dùng 1 lần duy nhất.
- **BR-06 - BR-08:** Đổi mật khẩu: kiểm tra mật khẩu hiện tại, mật khẩu mới khác cũ, xác nhận trùng khớp.
- **BR-09 - BR-11:** Số điện thoại định dạng VN (10 chữ số, đầu số hợp lệ); email hợp lệ; không để trống trường bắt buộc.
- **BR-12 - BR-14:** Phân trang bảng dữ liệu (mặc định 10 hoặc 20 dòng/trang); hiển thị empty state khi dữ liệu trống.
- **BR-15:** Dashboard lọc theo Năm và Kỳ (quý/tháng/năm).
- **BR-16 - BR-17:** Trạng thái hợp đồng tự động tính toán theo ngày hiệu lực & ngày kết thúc: *Chưa hiệu lực / Đang hiệu lực / Sắp hết hạn (≤ 30 ngày) / Đã hết hạn*.
- **BR-18:** Doanh thu và tiền tệ định dạng VND có dấu phân cách hàng nghìn.
- **BR-39 - BR-43:** Ticket có 3 trạng thái (*Mới tiếp nhận → Đang xử lý → Đã đóng*), 4 mức ưu tiên (*Thấp / Bình thường / Cao / Khẩn cấp*), theo dõi hạn SLA, cờ bảo hành mặc định *Có*.
- **BR-44:** *(TÍNH NĂNG CÒN THIẾU)* Quản lý thiết bị trong ticket (`technicalrequestdevices`: Tên thiết bị, Serial, Ghi chú lỗi).
- **BR-52:** Mã khách hàng tự sinh `KH-xxxx`, nhà cung cấp `NCC-xxxx`.
- **BR-53 - BR-55:** Sản phẩm hiển thị dạng Card Grid; mã sản phẩm tự sinh duy nhất, không cho sửa sau khi tạo.
