# POSCS - Đặc tả Yêu cầu & Thiết kế Hệ thống (SPEC v2)

> **Single Source of Truth của dự án POSCS.**
> Chắt lọc từ `docs/Project_Report_v8.docx` (Phần I, III, IV). Số hiệu **UC / BR / MSG / NFR / FE** giữ nguyên theo báo cáo để tra cứu ngược.
> Các file `PERMISSIONS.md`, `README.md`, `db/schema.sql` **chưa được đối chiếu** trong bản này.

## 0. Quy ước dành cho agent

- Mọi logic nghiệp vụ phải bám tài liệu này. **Không tự thêm tính năng** ngoài đặc tả.
- Mục đánh dấu **`[CẦN XÁC NHẬN]`** là chỗ tài liệu mơ hồ hoặc mâu thuẫn. Khi task chạm vào mục đó: **DỪNG và hỏi người dùng**, không tự đoán.
- UC-36 → UC-54 trong báo cáo chỉ có mô tả ngắn (không có luồng chi tiết). Chi tiết nào chưa được nêu thì hỏi, không tự bịa.
- Tham chiếu chéo: mỗi UC ghi các BR/MSG liên quan; mã thông báo dùng đúng nội dung ở mục 9.

---

## 1. Giới thiệu & Phạm vi

- **Tên hệ thống:** POSCS - phần mềm quản lý khách hàng doanh nghiệp và hợp đồng cho Chi nhánh Công ty CP Thiết bị Bưu điện POSTEF.
- **Mục tiêu:** quản trị quan hệ khách hàng B2B, vòng đời hợp đồng mua/bán, tiếp nhận & xử lý phiếu hỗ trợ kỹ thuật, theo dõi kỳ thanh toán/doanh thu, thông báo nhắc hạn.
- **Chức năng chính (FE):**
  - FE-01 Xác thực · FE-02 Quản lý nhân viên · FE-03 Trang tổng quan · FE-04 Khách hàng/NCC
  - FE-05 Sản phẩm · FE-06 Hợp đồng · FE-07 Phiếu hỗ trợ kỹ thuật · FE-08 Thông báo · FE-09 Nhật ký hệ thống

### Ngoài phạm vi (KHÔNG làm)
- Không tích hợp tổng đài hỗ trợ tự động.
- Không quản lý kho hàng, tồn kho thực tế, logistics.
- Không tích hợp cổng thanh toán trực tuyến; không xuất hóa đơn tài chính, không kế toán/sổ sách đầy đủ (chỉ theo dõi kỳ thanh toán: số tiền, hạn thu, đã thu).
- Không quản lý tài chính doanh nghiệp thuần túy, chấm công, tính lương.
- Đã gỡ (29/09/2026, theo yêu cầu khách hàng): **nhập hợp đồng từ PDF** và **xuất từng hợp đồng ra PDF** (giữ số hiệu UC-40).
- **Không làm (đã chốt):** BR-44 (danh sách thiết bị trong phiếu hỗ trợ). Phiếu gắn với **hợp đồng**; thông tin thiết bị/lỗi ghi vào trường **Mô tả/ghi chú** của phiếu. Không xây màn hình hay CRUD cho `technicalrequestdevices`.
- Chưa triển khai: BR-50 (giờ bắt đầu/kết thúc), BR-51 (màu sắc hiển thị tùy chỉnh).

---

## 2. Công nghệ & Kiến trúc

- **Nền tảng:** Java 21 (javac source/target = 21), Jakarta EE (Servlet, JSP, JSTL), Tomcat 11.0, MySQL 9.3, build bằng Apache Ant 1.10 (NetBeans IDE tùy chọn).
- **Truy cập dữ liệu:** JDBC + commons-dbutils, bồn kết nối **HikariCP** (do `DBContext` quản lý).
- **Email:** gửi qua máy chủ SMTP ngoài (`EmailUtil`). Nếu chưa cấu hình SMTP: chạy chế độ phát triển, **không gửi thật**, ghi thông tin ra nhật ký máy chủ.
- **Bảo mật:** mật khẩu băm **BCrypt**; chống **CSRF** (`CsrfUtil`); `AuthenticationFilter`, `AccessControl` kiểm soát theo vai trò.
- **Phân tầng (bắt buộc giữ):**

| Gói | Vai trò |
|---|---|
| `view` | Trang JSP + CSS/JS/ảnh trong `web/`. Chỉ hiển thị, **không chứa logic nghiệp vụ**. |
| `controller` | 11 servlet: Authentication, Customer, Contract, Product, TechnicalSupportTicket, Employee, Dashboard, Notification, Address, UploadFile, SystemLog. Kiểm quyền, validate đầu vào, gọi xuống dao. |
| `dao` | 10 lớp DAO. Mọi thao tác ghi nhiều bảng chạy **trong một giao dịch**. |
| `model` | 21 lớp POJO ánh xạ bảng DB. |
| `service` (`poscs.service`) | Tầng nghiệp vụ và giao dịch, tách khỏi controller; có Base Service và Result/Response wrapper. Đã có `AuthService`, `CustomerService`, `CustomerEvaluationService`. |
| `validator` (`poscs.validator`) | Kiểm tra đầu vào theo BR. Đã có `AuthValidator`, `CustomerValidator`. |

> Danh sách 11 servlet ở trên theo báo cáo v8; các controller đang được tách dần (ví dụ `AuthenticationController` đã tách thành `LoginController`, `LogoutController`, `PasswordResetController`, `ProfileController`).
| `common` | `AuthenticationFilter`, `AccessControl`, `CsrfUtil`, `EmailUtil`, `ExcelUtil`, `PdfUtil`, `FileStorage`, `LogFiles`, `Logs`, `TextRules`, bộ hẹn giờ sinh thông báo (`NotificationScheduler`). |

---

## 3. Vai trò & Phân quyền (RBAC)

### 3.1 Ba tác nhân
1. **Admin:** toàn quyền hệ thống; quản lý nhân viên, phân công địa bàn, xem nhật ký hệ thống, chữa sai sót hợp đồng đã ký.
2. **Sales:** quản lý khách hàng, hợp đồng. Quyền **ghi** phụ thuộc việc Sales có cấp trên hay không (xem 3.2).
3. **Kỹ thuật:** quản lý sản phẩm; xử lý phiếu hỗ trợ kỹ thuật (với phiếu được giao).

> **Đã chốt:** hệ thống **không có vai trò CS (Customer Support)**. Chỉ có đúng 3 vai trò trên. Không tạo role, quyền hay màn hình riêng cho CS.

### 3.2 Quản lý vùng vs Sales có cấp trên (cơ chế cốt lõi)
- **Người quản lý trực tiếp (cấp trên)** của nhân viên hiện được thiết lập **trực tiếp trong DB**; biểu mẫu nhân viên **không có ô này**.
- **"Sales (quản lý vùng)"** = Sales **không có cấp trên**. Chỉ vai này (và Admin) được thực hiện các thao tác ghi trên Khách hàng và Hợp đồng (các dòng đánh dấu `(*)` bên dưới).
- **Sales có cấp trên:** chỉ **xem**; muốn tạo mới/lập phụ lục thì **gửi yêu cầu thay đổi (UC-50)**; người quản lý trực tiếp của người gửi (hoặc Admin) duyệt/từ chối (UC-51).
- **Phạm vi dữ liệu của Sales:** mặc định chỉ thấy hợp đồng do mình hoặc cấp dưới phụ trách, hoặc thuộc **tỉnh mình được giao**. Sales đã được giao tỉnh chỉ sửa được đối tác trong phạm vi "Của tôi" (do mình/cấp dưới đứng tên, hoặc ở tỉnh mình phụ trách; nhà cung cấp thuần phải do mình/cấp dưới đứng tên).
- **Địa bàn (tỉnh/thành):** mỗi tỉnh chỉ có **một** người phụ trách; tỉnh đã có người thì bị khóa khi gán cho người khác.

### 3.3 Ma trận quyền theo màn hình (nguồn: báo cáo mục 3.1.4.2)

| Chức năng | Admin | Kỹ thuật | Sales |
|---|:-:|:-:|:-:|
| Đăng nhập / Đăng xuất / Quên MK / OTP / Đổi MK | X | X | X |
| Xem bảng điều khiển; Xem & Cập nhật hồ sơ cá nhân | X | X | X |
| Xem danh sách & chi tiết Khách hàng/NCC | X | X | X |
| Xuất danh sách Khách hàng/NCC | X | X | X |
| Tạo / Cập nhật / Xóa Khách hàng/NCC; Đánh giá khách hàng | X | - | X (*) |
| Xem danh sách & chi tiết Sản phẩm | X | X | X |
| Tạo / Cập nhật / Xóa Sản phẩm | X | X | - |
| Xem danh sách, chi tiết, nhật ký trạng thái Hợp đồng; Xuất danh sách HĐ | X | X | X |
| Tạo, Cập nhật, Huỷ bản ghi, Ký HĐ; Tạo phụ lục; Liên kết Bán↔Mua; Khởi tạo bàn giao; Quản lý tài liệu; Quản lý kỳ thanh toán; Thêm/gỡ SP trong HĐ | X | - | X (*) |
| Chữa sai sót nhập liệu HĐ đã ký (UC-54) | X | - | - |
| Xem danh sách, chi tiết, tìm kiếm Phiếu hỗ trợ; Xuất phiếu | X | X | X |
| **Tạo** Phiếu hỗ trợ | X | - | X |
| **Cập nhật** Phiếu hỗ trợ | X | X (chỉ phiếu được giao) | X |
| **Xóa** Phiếu hỗ trợ | X | - | X |
| Quản lý nhân viên (xem DS, chi tiết, tạo, sửa, khóa/mở khóa, gửi TK, đổi vai trò) | X | - | - |
| Xem DS thông báo; Đánh dấu đã đọc | X | X | X |
| Xem nhật ký hệ thống (UC-43) | X | - | - |

**Ghi chú:**
- `(*)` = chỉ **Sales không có cấp trên (quản lý vùng)**. Riêng huỷ hợp đồng: bản **Nháp** Admin và quản lý vùng đều được; **đã ký** chỉ Admin.
- UC-49 (xác nhận bàn giao): Admin hoặc nhân viên thuộc đúng phòng ban nhận bàn giao. UC-50: nhân viên có cấp trên. UC-51: quản lý trực tiếp của người gửi hoặc Admin. Ba UC này không nằm trong ma trận trên.
- Kỹ thuật vào chức năng ghi của Khách hàng/Hợp đồng, hoặc Sales có cấp trên: trả trang "Không có quyền truy cập" (**MSG-022**).

---

## 4. Danh sách Use Case (UC-01 → UC-54)

### 4.1 Xác thực & tài khoản (Admin, Sales, Kỹ thuật)
| UC | Tên | Ghi chú |
|---|---|---|
| UC-01 | Đăng nhập | Username + mật khẩu. BR-01, BR-02. Sau đăng nhập về trang tổng quan theo vai trò. |
| UC-02 | Đăng xuất | Hủy phiên, về trang đăng nhập. |
| UC-03 | Đổi mật khẩu | BR-06, 07, 08. |
| UC-04 | Quên mật khẩu | Nhập **username**; OTP gửi tới **email cá nhân** đã lưu. BR-03, BR-04. |
| UC-05 | Xác thực OTP | 6 ô nhập, đồng hồ 5 phút. BR-04, BR-05. |
| UC-06 | Xem hồ sơ cá nhân | Chỉ đọc. |
| UC-07 | Cập nhật hồ sơ cá nhân | BR-09, 10, 11. Lần đăng nhập đầu nếu thiếu thông tin: hiện dải nhắc bổ sung. |

### 4.2 Khách hàng / Nhà cung cấp
| UC | Tên | Tác nhân |
|---|---|---|
| UC-08 | Xem danh sách KH/NCC | Admin, Kỹ thuật, Sales |
| UC-09 | Xem chi tiết KH/NCC | Admin, Kỹ thuật, Sales |
| UC-30 | Thêm KH/NCC | Admin, Sales (quản lý vùng) |
| UC-31 | Cập nhật KH/NCC | Admin, Sales (quản lý vùng) |
| UC-32 | Xóa KH/NCC (xóa mềm) | Admin, Sales (quản lý vùng) |
| UC-37 | Xuất danh sách KH ra Excel | Admin, Kỹ thuật, Sales |
| UC-38 | Đánh giá khách hàng (sự kiện vòng đời + mức quan hệ: Tốt / Cần theo dõi / Xấu / Có nguy cơ rời bỏ; ghi cùng một giao dịch) | Admin, Sales (quản lý vùng) |

### 4.3 Sản phẩm
| UC | Tên | Tác nhân |
|---|---|---|
| UC-12 | Xem danh sách sản phẩm (card grid) | Admin, Kỹ thuật, Sales |
| UC-13 | Xem chi tiết sản phẩm | Admin, Kỹ thuật, Sales |
| UC-18 | Cập nhật sản phẩm | Admin, Kỹ thuật |
| UC-19 | Thêm sản phẩm | Admin, Kỹ thuật |
| UC-20 | Xóa sản phẩm (xóa mềm) | Admin, Kỹ thuật |

### 4.4 Hợp đồng
| UC | Tên | Tác nhân |
|---|---|---|
| UC-10 | Xem danh sách hợp đồng | Admin, Kỹ thuật, Sales |
| UC-11 | Xem chi tiết hợp đồng | Admin, Kỹ thuật, Sales |
| UC-33 | Tạo hợp đồng | Admin, Sales (quản lý vùng) |
| UC-34 | Huỷ bản ghi hợp đồng (xóa mềm) | Admin, Sales (quản lý vùng) nếu Nháp; chỉ Admin nếu đã ký |
| UC-35 | Cập nhật hợp đồng | Admin, Sales (quản lý vùng) |
| UC-39 | Thêm / gỡ sản phẩm trong hợp đồng (kèm số lượng, đơn vị, ghi chú) | Admin, Sales (quản lý vùng) |
| UC-41 | Xuất danh sách hợp đồng ra Excel (cùng bộ lọc, không phân trang, sắp theo tỉnh/thành) | Admin, Kỹ thuật, Sales |
| UC-44 | Ký hợp đồng (Nháp → Đã ký; cần đủ ngày hiệu lực & kết thúc) | Admin, Sales (quản lý vùng) |
| UC-45 | Xem nhật ký thay đổi trạng thái hợp đồng | Admin, Sales, Kỹ thuật |
| UC-46 | Tạo phụ lục hợp đồng (điều chỉnh giá trị cho HĐ gốc **đã ký**, chưa phải phụ lục) | Admin, Sales (quản lý vùng) |
| UC-47 | Liên kết hợp đồng Bán ↔ Mua (và gỡ liên kết) | Admin, Sales (quản lý vùng) |
| UC-48 | Khởi tạo bàn giao hợp đồng cho một/nhiều phòng ban | Admin, Sales (quản lý vùng) |
| UC-49 | Xác nhận xử lý bàn giao | Admin, nhân viên phòng nhận |
| UC-50 | Gửi yêu cầu thay đổi (tạo mới/lập phụ lục) | Nhân viên có cấp trên |
| UC-51 | Duyệt / từ chối yêu cầu thay đổi | Admin, quản lý trực tiếp của người gửi |
| UC-52 | Quản lý tài liệu đính kèm (chỉ gắn **đường dẫn**, không upload; hủy mềm kèm lý do bắt buộc) | Admin, Sales (quản lý vùng) |
| UC-53 | Quản lý kỳ thanh toán (lập kỳ, ghi nhận đã thu, xóa kỳ lập nhầm) | Admin, Sales (quản lý vùng) |
| UC-54 | Chữa sai sót nhập liệu HĐ đã ký (chưa thanh lý/chấm dứt; bắt buộc lý do; ghi nhật ký loại sự kiện riêng; **ngày ký không sửa được**) | Admin |
| ~~UC-40~~ | ~~Nhập HĐ từ PDF~~ (đã gỡ) | - |

### 4.5 Phiếu hỗ trợ kỹ thuật
| UC | Tên | Tác nhân |
|---|---|---|
| UC-16 | Xem danh sách phiếu | Admin, Sales, Kỹ thuật |
| UC-17 | Xem chi tiết phiếu | Admin, Sales, Kỹ thuật |
| UC-21 | Tạo phiếu | Admin, Sales |
| UC-22 | Cập nhật phiếu | Admin, Sales, Kỹ thuật (Kỹ thuật: chỉ phiếu được giao) |
| UC-23 | Xóa phiếu (xóa mềm, ghi lý do, lưu vết) | Admin, Sales |
| UC-42 | Xuất phiếu ra Excel hoặc PDF | Admin, Kỹ thuật, Sales |

### 4.6 Thông báo, tổng quan, nhật ký
| UC | Tên | Tác nhân |
|---|---|---|
| UC-14 | Xem danh sách thông báo (chỉ của chính mình) | Admin, Sales, Kỹ thuật |
| UC-15 | Đánh dấu thông báo đã đọc | Admin, Sales, Kỹ thuật |
| UC-36 | Xem trang tổng quan (số KH, KH mới trong tháng, tình trạng HĐ, doanh thu tháng, tình trạng phiếu, HĐ sắp hết hạn) | Admin, Kỹ thuật, Sales |
| UC-43 | Xem nhật ký hệ thống (đọc tệp log trên trình duyệt, lọc từ khóa, tải 1 tệp) | Admin |
| Hệ thống | `NotificationScheduler` (xem 5.7): bộ hẹn giờ chạy nền, tự sinh thông báo cho HĐ sắp hết hạn và phiếu sắp/đã quá hạn SLA | - |

### 4.7 Quản lý nhân viên (chỉ Admin)
| UC | Tên |
|---|---|
| UC-24 | Xem danh sách nhân viên (dạng thẻ, tìm theo họ tên đầy đủ/username/SĐT; lọc vai trò, trạng thái) |
| UC-25 | Xem chi tiết nhân viên (Thông tin công việc, Thông tin cá nhân, Địa chỉ) |
| UC-26 | Thêm nhân viên (hệ thống **tự sinh username và mật khẩu tạm**, chưa gửi email) |
| UC-27 | Cập nhật nhân viên (kể cả vai trò, phòng ban, địa bàn) |
| UC-28 | Gửi thông tin tài khoản qua email (mỗi lần gửi cấp mật khẩu tạm **mới**) |
| UC-29 | Khóa / Mở khóa tài khoản |

---

## 5. Đặc tả các luồng quan trọng

### 5.1 Xác thực
- **UC-01:** chỉ nhận username (BR-02). Sai quá 5 lần liên tiếp **theo IP** → khóa IP 15 phút (BR-01, MSG-008). Tài khoản khóa/ngừng hoạt động → MSG-004. Sai thông tin → MSG-003. Để trống → MSG-002.
- **UC-04/05 (OTP):** luôn chuyển sang trang xác thực OTP kể cả khi username không tồn tại hoặc tài khoản chưa có email cá nhân (không gửi mã, **không tiết lộ** username tồn tại, MSG-005). OTP 6 số, hiệu lực 5 phút, dùng 1 lần. Sai: MSG-006. Hết hạn: MSG-007. Trống: MSG-009.
- **UC-03:** sai MK hiện tại MSG-011; MK mới trùng cũ MSG-012; xác nhận không khớp MSG-013; thành công MSG-010 rồi về trang xem hồ sơ.
- **UC-07:** bắt buộc Họ, Tên, Số CCCD/CMND (thiếu → "Vui lòng nhập đầy đủ Họ, Tên và Số CCCD/CMND."); địa chỉ phải chọn Tỉnh/Thành phố và Xã/Phường. SĐT sai MSG-015, email sai MSG-016, ngày sinh sai MSG-034.
- Phiên tự hết hạn sau 30 phút không thao tác; cookie phiên đặt cờ HttpOnly (NFR-20).

### 5.2 Khách hàng / Nhà cung cấp
- **Hai trang Thêm** (Khách hàng mua / Nhà cung cấp) dùng chung biểu mẫu; mỗi trang tạo **đúng một vai**. Công ty vừa mua vừa bán: thêm vai thứ hai ở trang Sửa (ô "Đồng thời là ...").
- **Mã tự sinh** `KH-xxxx` / `NCC-xxx` (BR-52); người dùng không nhập tay. Mã và mã số thuế chỉ hiển thị khi sửa.
- **Bắt buộc (BR-31):** Tên, Mã số thuế, Loại, Nhóm, Người phụ trách chính, SĐT, Email, địa chỉ (Xã/Phường + địa chỉ chi tiết). Email & SĐT của KH phải **duy nhất** (BR-27). Ngày tham gia không được ở tương lai (BR-32, MSG-019).
- **Người phụ trách chính:** Sales được giao tỉnh khi thêm KH → khóa ở chính mình; Admin/Sales chưa giao tỉnh → theo phân công địa bàn của tỉnh (điền sẵn và khóa nếu tỉnh đã có người); Admin thêm NCC → chọn tay. Khi sửa, người phụ trách chính chỉ đổi được nếu người sửa là Admin.
- **UC-32 Xóa:** hộp thoại xác nhận (MSG-038) → **xóa mềm** (`is_deleted = 1`, chuyển Inactive). **Chặn** nếu KH còn **hợp đồng đang hiệu lực** (MSG-040). Không tìm thấy bản ghi → MSG-021.
- Danh sách: bảng, cột mặc định theo BR-13; tìm kiếm/bộ lọc giữ nguyên khi phân trang (kể cả ký tự đặc biệt như `&`, `#`).

### 5.3 Sản phẩm
- Bắt buộc: **Tên sản phẩm** và **Danh mục** (BR-54). Mã sản phẩm **tự sinh**, duy nhất nhờ ràng buộc UNIQUE ở DB, **không đổi được sau khi tạo**; nếu đụng trùng khi lưu → tự sinh lại, thử tối đa **5 lần** không báo lỗi (BR-55, MSG-056).
- Ảnh sản phẩm tùy chọn, chỉ nhận JPG/PNG/GIF/WEBP (BR-48). Catalogue là tệp đính kèm riêng. Danh mục phải còn dùng được (không phải danh mục cha/đã xóa).
- Chi tiết gồm: thông tin (mã, danh mục, mô tả, ngày tạo), hình ảnh, catalogue, hợp đồng sử dụng (BR-19).
- **UC-20 Xóa:** hộp thoại xác nhận (BR-49, MSG-054); **chặn** nếu sản phẩm nằm trong hợp đồng còn hiệu lực; xóa mềm.

### 5.4 Hợp đồng
- **Tạo (UC-33):** bắt buộc Mã hợp đồng (nhập tay theo bản giấy), Tiêu đề, Loại hợp đồng, Khách hàng/NCC (đúng vai), Người phụ trách (BR-35). Tùy chọn: giá trị hợp đồng, người ký & chức vụ hai bên, số ủy quyền, nơi ký. **Biểu mẫu không có ô ngày và hàng hóa.** Lưu ở tiến độ **Nháp**, ghi nhật ký "Khởi tạo". Mã trùng → báo "Mã hợp đồng này đã có hợp đồng khác dùng...". **Chiều Bán/Mua** lấy theo mục đang đứng và **không đổi được**.
- **Ngày ký:** KHÔNG phải trường nhập tay; hệ thống đóng dấu ngày hiện tại khi Ký (UC-44). Ràng buộc Ngày ký ≤ Ngày hiệu lực ≤ Ngày kết thúc (BR-36). Chỉ điền một mốc hoặc hiệu lực sau kết thúc → MSG-045.
- **Tiến độ (vòng đời):** `Nháp → Đã ký → Đã thanh lý | Chấm dứt sớm`.
- **Trạng thái tự tính** (BR-17) từ ngày hiệu lực/kết thúc: *Chưa hiệu lực* (chưa tới ngày hiệu lực, hoặc thiếu ngày) / *Đang hiệu lực* / *Sắp hết hạn* (còn ≤ 30 ngày) / *Đã hết hạn*. Khác với "Tiến độ".
- **Cập nhật (UC-35):** Nháp sửa mọi ô kể cả thời hạn; **đã ký** chỉ đổi được **Người phụ trách**; **Đã thanh lý/Chấm dứt sớm** không sửa. Sửa sai nhập liệu hợp đồng đã ký đi qua UC-54; sửa đổi thật đi qua phụ lục. Phụ lục còn Nháp: khoản giảm trừ lớn hơn giá trị còn lại của HĐ gốc bị từ chối.
- **Huỷ bản ghi (UC-34, BR-37):** là xóa mềm do **nhập nhầm** (không phải hủy HĐ ngoài đời). **Bắt buộc lý do** (hộp thoại MSG-043). Nháp: Admin hoặc quản lý vùng. Đã ký trở đi: **chỉ Admin**. **Chặn** nếu HĐ đã có phụ lục. Kiểm quyền, đánh dấu huỷ và ghi nhật ký trong **một giao dịch**.
- **Danh sách (UC-10):** hiển thị theo từng chiều (bán/mua); cột theo BR-16; bốn ô trạng thái đồng thời là bộ lọc; tìm theo mã, tiêu đề, tên đối tác; lọc theo loại, tỉnh/thành (chỉ HĐ bán), kỳ theo ngày ký, tiến độ, chỉ HĐ gốc, đang bàn giao. Mặc định mọi vai trò chỉ thấy HĐ **còn hiệu lực trong tháng hiện tại**; có chuyển "Của tôi / Toàn chi nhánh" và "Mọi thời điểm". Bộ lọc giữ khi phân trang và khi xuất Excel.
- **Chi tiết (UC-11):** thanh vòng đời, hàng hóa, phụ lục, kỳ thanh toán, bàn giao, liên kết bán-mua, tài liệu, nhật ký. Người có quyền quản lý thấy thêm nút "Quản lý hợp đồng" (sang UC-35).
- Giá trị hợp đồng và số tiền từng kỳ thanh toán lưu & hiển thị trực tiếp trên hồ sơ HĐ (BR-18).

### 5.5 Phiếu hỗ trợ kỹ thuật
- **Mã ticket** tự sinh duy nhất (BR-38).
- **Bắt buộc khi tạo (BR-39):** Khách hàng, Loại phiếu, Mức độ ưu tiên, Kênh tiếp nhận, Mô tả sự cố, Kỹ thuật viên được chỉ định. **Trạng thái và Ngày tạo do hệ thống gán** (Mới tiếp nhận, thời điểm hiện tại).
- **Trạng thái (BR-40):** chỉ 3 giá trị `Mới tiếp nhận → Đang xử lý → Đã đóng`. Lần đầu sang *Đã đóng*: hệ thống ghi thời điểm hoàn tất. Mọi lần đổi trạng thái ghi vào lịch sử. Chuyển sai quy trình → MSG-051.
- **Ưu tiên (BR-41):** Thấp / Bình thường / Cao / Khẩn cấp. **SLA (BR-42):** mỗi phiếu có hạn xử lý; quá hạn = vượt mốc mà chưa *Đã đóng*. **Cờ bảo hành (BR-43):** bắt buộc, Có/Không, mặc định **Có**.
- **Danh sách (UC-16):** bảng, cột theo BR-45 (Mã, Loại, Khách hàng, Ưu tiên, Trạng thái, Người xử lý, Ngày tạo, Thao tác; mã hợp đồng chỉ ở chi tiết và Excel). Dải KPI: Mới tiếp nhận / Đang xử lý / Đã đóng / Khẩn cấp, đếm theo kỳ & người xử lý đang chọn. Tìm theo mã, mô tả, tên KH; lọc trạng thái, ưu tiên, kỳ (theo ngày tạo), người xử lý (Tất cả / Phiếu của tôi / một KTV). **Kỹ thuật viên mở lần đầu mặc định "Phiếu của tôi".**
- **Chi tiết (UC-17, BR-46):** Thông tin chung, Người tạo phiếu, Nhân viên kỹ thuật xử lý, Lịch sử xử lý.
- **Xóa (UC-23, BR-47):** hộp thoại xác nhận (MSG-048); **không xóa phiếu Đang xử lý** (chỉ xóa *Mới tiếp nhận* hoặc *Đã đóng*); xóa mềm.
- **Cập nhật nhiều bảng** (phiếu + lịch sử trạng thái) trong **một giao dịch** (NFR-19).
- **Thiết bị/lỗi (BR-44, đã chốt không làm):** phiếu liên kết với hợp đồng (nếu có); thiết bị gặp sự cố và mô tả lỗi ghi vào trường Mô tả/ghi chú. **Bỏ qua** phần "Thiết bị liên quan (tên/serial/lỗi)" xuất hiện trong mô tả UC-17, UC-21, UC-22 của báo cáo gốc.

### 5.6 Nhân viên (Admin)
- **Bắt buộc (BR-28):** Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email cá nhân hợp lệ. Giới tính, ngày sinh, CCCD, SĐT, địa chỉ có thể trống (nhân viên tự bổ sung lần đăng nhập đầu); nếu có nhập phải đúng định dạng (BR-09, 29, 30).
- Ô **Vai trò không có Admin**; không cho thăng nhân viên lên Admin. **SĐT và CCCD phải duy nhất** (BR-27, MSG-033); email cá nhân không ràng buộc duy nhất.
- Tỉnh đã có người phụ trách bị khóa; lưu hồ sơ xong mới lưu địa bàn (nếu địa bàn lỗi: hồ sơ đã lưu, báo rõ địa bàn chưa lưu).
- **UC-28:** sinh mật khẩu tạm mới → gửi email → **chỉ khi gửi thành công mới băm BCrypt và ghi đè mật khẩu cũ**. Chưa có email cá nhân → dừng, không đổi mật khẩu.
- **UC-29:** có hộp thoại xác nhận (BR-26, MSG-030); **không tự khóa tài khoản đang đăng nhập của mình**. Khóa → ẩn khỏi danh sách đang hoạt động (BR-25); phiên đang mở bị ngắt ở thao tác kế tiếp.
- Truy cập phân hệ không phải Admin → MSG-022, không trả dữ liệu.

### 5.7 Thông báo
- Chỉ xem thông báo của chính mình; sắp xếp mới nhất lên đầu, hiển thị số chưa đọc (BR-20).
- **Thông báo không do người dùng tạo tay.** `NotificationScheduler` chạy nền, **chu kỳ 60 phút**, tự sinh thông báo cho đúng hai trường hợp:
  1. **Hợp đồng sắp hết hạn** → gửi cho **người phụ trách hợp đồng**. "Sắp hết hạn" theo BR-17 (còn ≤ 30 ngày tới ngày kết thúc).
  2. **Phiếu hỗ trợ sắp / đã quá hạn SLA** → gửi cho **kỹ thuật viên được phân công**. "Sắp quá hạn" = còn ≤ **24 giờ** tới hạn SLA (người dùng chốt; báo cáo không nêu).
  - Mỗi cặp (đối tượng, người nhận) chỉ sinh **một** thông báo; chạy lại nhiều lần **không tạo bản ghi trùng**.
  - Không có thông báo nhắc hạn thanh toán. Báo cáo chỉ nêu hai trường hợp trên.
- Mở danh sách **KHÔNG** tự đánh dấu đã đọc; phải bấm từng thông báo hoặc "Đánh dấu tất cả đã đọc" (BR-21). Mã thông báo không thuộc người dùng → bỏ qua, không đổi dữ liệu.

---

## 6. Quy tắc nghiệp vụ (BR-01 → BR-55)

| Mã | Quy tắc |
|---|---|
| BR-01 | Sau 5 lần đăng nhập thất bại liên tiếp **theo IP** (không theo username) → khóa IP 15 phút. |
| BR-02 | Đăng nhập chỉ bằng username; không đăng nhập bằng email. |
| BR-03 | Quên mật khẩu chỉ nhập username; OTP chỉ gửi khi username tồn tại và tài khoản có email cá nhân. |
| BR-04 | OTP đúng 6 chữ số, gửi qua email, hiệu lực tối đa 5 phút. |
| BR-05 | Mỗi OTP chỉ dùng một lần. |
| BR-06 | Mật khẩu hiện tại nhập vào phải khớp mật khẩu đang lưu. |
| BR-07 | Mật khẩu mới phải khác mật khẩu cũ. |
| BR-08 | Mật khẩu xác nhận phải khớp mật khẩu mới. |
| BR-09 | SĐT đúng định dạng số điện thoại Việt Nam. |
| BR-10 | Email (hồ sơ) đúng định dạng chuẩn. |
| BR-11 | Trường bắt buộc của hồ sơ không được trống khi lưu. |
| BR-12 | Danh sách phải phân trang, kích thước mặc định cấu hình sẵn (10 hoặc 20; NFR-15 quy định mặc định **10**). |
| BR-13 | Cột danh sách KH: STT, Khách hàng (tên + email), Loại KH, SĐT, Địa bàn (tỉnh/thành), Phụ trách chính, Thao tác. |
| BR-14 | Danh sách trống phải hiện trạng thái trống, không để màn hình trắng. |
| BR-15 | Dashboard lọc theo Năm và Kỳ (quý/tháng/năm) chọn từ dropdown; năm không hợp lệ/trống → coi như không lọc (hiện toàn bộ). |
| BR-16 | Cột danh sách HĐ: STT, Hợp đồng (tiêu đề + tỉnh/thành · KH), Loại, Thời hạn (ngày ký → kết thúc), Trạng thái, Tiến độ, Phụ trách, Thao tác. |
| BR-17 | Trạng thái HĐ tự tính: Chưa hiệu lực / Đang hiệu lực / Sắp hết hạn (≤ 30 ngày) / Đã hết hạn (xem 5.4). |
| BR-18 | Doanh thu hiển thị VND, có dấu phân cách hàng nghìn. Giá trị HĐ và số tiền kỳ thanh toán cũng lưu/hiển thị trên hồ sơ HĐ. |
| BR-19 | Chi tiết sản phẩm gồm: Thông tin (mã, danh mục, mô tả, ngày tạo), Hình ảnh, Catalogue, Hợp đồng sử dụng. |
| BR-20 | Trung tâm thông báo hiện số chưa đọc, mới nhất lên đầu. |
| BR-21 | Mở danh sách thông báo KHÔNG tự đánh dấu đã đọc. |
| BR-22 | Danh sách nhân viên dạng thẻ: Tên, Mã NV, Phòng ban/Vai trò, Username, Trạng thái. |
| BR-23 | Danh sách nhân viên trống → hiện trạng thái trống. |
| BR-24 | Chi tiết nhân viên gồm: Thông tin công việc, Thông tin cá nhân, Địa chỉ. |
| BR-25 | Trạng thái nhân viên: Active / Inactive. Inactive → ẩn khỏi danh sách đang hoạt động. |
| BR-26 | Ngừng hoạt động nhân viên phải qua hộp thoại xác nhận. |
| BR-27 | Email & SĐT khách hàng duy nhất toàn hệ thống. Nhân viên: chỉ SĐT và CCCD/CMND duy nhất. |
| BR-28 | Bắt buộc khi tạo/sửa nhân viên: Họ, Tên, Phòng ban, Vai trò, Ngày vào làm, Email cá nhân hợp lệ. |
| BR-29 | Ngày sinh hợp lệ, trong quá khứ, định dạng dd/MM/yyyy. |
| BR-30 | Giới tính: Nam / Nữ / Khác. |
| BR-31 | Bắt buộc khi tạo/sửa KH/NCC: Tên, MST, Loại, Nhóm, Người phụ trách chính, SĐT, Email, địa chỉ (Xã/Phường + chi tiết). |
| BR-32 | Ngày tham gia của KH không ở tương lai. |
| BR-33 | SĐT và email liên hệ của KH phải hợp lệ. |
| BR-34 | Xóa KH: có hộp thoại xác nhận, **xóa mềm** (Inactive); cấm xóa nếu KH có HĐ còn hiệu lực. |
| BR-35 | Bắt buộc khi tạo HĐ: Mã HĐ, Tiêu đề, Khách hàng, Loại HĐ, Người phụ trách. Ngày ký không nhập tay; hệ thống đóng dấu khi bấm Ký. |
| BR-36 | Ngày ký ≤ Ngày hiệu lực ≤ Ngày kết thúc. |
| BR-37 | Huỷ HĐ (nhập nhầm): bắt buộc lý do. Nháp: người có quyền quản lý HĐ đều huỷ được. Từ Đã ký: chỉ Admin; chặn nếu có phụ lục. |
| BR-38 | Phiếu hỗ trợ tự sinh Mã ticket duy nhất. |
| BR-39 | Bắt buộc của phiếu: Khách hàng, Loại phiếu, Mức ưu tiên, Kênh tiếp nhận, Mô tả sự cố, KTV được chỉ định. Trạng thái và Ngày tạo do hệ thống gán. |
| BR-40 | Trạng thái phiếu: Mới tiếp nhận → Đang xử lý → Đã đóng; mặc định Mới tiếp nhận; lần đầu Đã đóng ghi thời điểm hoàn tất; mọi lần đổi ghi lịch sử. |
| BR-41 | Ưu tiên: Thấp / Bình thường / Cao / Khẩn cấp. |
| BR-42 | Mỗi phiếu có hạn SLA; quá hạn khi vượt mốc mà chưa Đã đóng. |
| BR-43 | Cờ bảo hành bắt buộc, Có/Không, mặc định Có. |
| BR-44 | **Không làm (đã chốt):** dòng thiết bị (Tên thiết bị, Serial, Lỗi/Ghi chú) gắn theo phiếu. Phiếu gắn với hợp đồng; lỗi ghi ở Mô tả/ghi chú. |
| BR-45 | Cột danh sách phiếu: Mã, Loại, KH, Ưu tiên, Trạng thái, Người xử lý, Ngày tạo, Thao tác (mã HĐ chỉ ở chi tiết và Excel). |
| BR-46 | Chi tiết phiếu gồm: Thông tin chung, Người tạo phiếu, KTV xử lý, Lịch sử xử lý. |
| BR-47 | Xóa phiếu: hộp thoại xác nhận; không xóa phiếu Đang xử lý. |
| BR-48 | Ảnh sản phẩm tùy chọn; nếu có thì lưu và liên kết với sản phẩm. |
| BR-49 | Xóa sản phẩm bắt buộc có hộp thoại xác nhận. |
| BR-50 | **Chưa triển khai:** trường Giờ bắt đầu/Giờ kết thúc. |
| BR-51 | **Chưa triển khai:** trường Màu sắc hiển thị tùy chỉnh. |
| BR-52 | Mã KH/NCC tự sinh theo hai dãy `KH-xxxx` và `NCC-xxx`; không cho nhập tay. |
| BR-53 | Danh sách sản phẩm dạng card grid: Hình ảnh, Thẻ danh mục, Mã, Tên, Ngày cập nhật gần nhất. |
| BR-54 | Bắt buộc khi tạo SP: Tên và Danh mục. Mã do hệ thống sinh. |
| BR-55 | Mã SP duy nhất nhờ UNIQUE ở DB (không kiểm trùng ở tầng ứng dụng); trùng thì tự sinh lại. Mã không đổi sau khi tạo. |

---

## 7. Mô hình dữ liệu (22 bảng)

Nguồn chính thức là `db/schema.sql`; bảng dưới chỉ để định hướng.

| Nhóm | Bảng |
|---|---|
| Tài khoản & tổ chức | `users`, `roles`, `departments`, `provinces`, `districts`, `addresses`, `user_provinces` (phân công địa bàn; có trong DB, chưa có trong báo cáo, tên chính xác theo `schema.sql`) |
| Khách hàng | `enterprises`, `enterprisecontacts`, `customer_lifecycle_events` (có `relationship_rating`), `customer_evaluation_rules` |
| Sản phẩm | `productcategories` (cây đa tầng), `products`, `productimages`, `productcatalogues` |
| Hợp đồng | `contracts`, `contractproducts`, `contract_payments` (nguồn duy nhất tính doanh thu dashboard & đánh giá KH) |
| Phiếu hỗ trợ | `technicalrequests`, `technicalrequestdevices` (có bảng nhưng **không dùng**, theo quyết định bỏ BR-44), `technicalrequesthistory` |
| Khác | `notifications`, `schema_migrations` (mỗi file trong `db/migrations/` khi áp dụng ghi một dòng) |

Lưu ý: dùng **xóa mềm** (`is_deleted`) cho KH, sản phẩm, hợp đồng, phiếu, tài liệu hợp đồng, danh mục.

---

## 8. Yêu cầu phi chức năng

| Mã | Yêu cầu |
|---|---|
| NFR-01 | Chạy trên Chrome, Edge, Firefox, không cài thêm phần mềm. |
| NFR-02 | Gửi email qua máy chủ SMTP ngoài (OTP, thông tin tài khoản nhân viên). |
| NFR-03 | Kết nối MySQL qua JDBC với bồn kết nối dùng chung. |
| NFR-04 → 07 | UI thân thiện, phản hồi ngay khi dữ liệu sai, tên chức năng rõ nghĩa, liên kết hướng dẫn nổi bật. |
| NFR-08 | Ngày hiển thị `dd/MM/yyyy` hoặc `dd-MM-yyyy`. |
| NFR-09 | UTF-8 toàn bộ. |
| NFR-10 | RBAC: mỗi vai trò một quyền hạn cụ thể. |
| NFR-11 | Mật khẩu băm trước khi lưu. |
| NFR-12 | Dữ liệu định danh/liên hệ truyền và lưu an toàn (HTTPS). |
| NFR-13 → 14 | Tuân thủ SOLID, Clean Code; review mã 1-2 lần/tuần. |
| NFR-15 | Mọi màn hình danh sách phân trang, mặc định 10 bản ghi. |
| NFR-16 | Bồn kết nối tối đa 10, tối thiểu 2 chờ sẵn; chờ cấp kết nối ≤ 10 giây. |
| NFR-17 | Nhật ký hệ thống chỉ đọc tối đa 512 KB cuối của tệp mỗi lần. |
| NFR-18 | Sẵn sàng tối thiểu 95% trong giờ hành chính. |
| NFR-19 | Thao tác ghi nhiều bảng phải trong một giao dịch, lỗi thì hoàn tác toàn bộ. |
| NFR-20 | Phiên hết hạn sau 30 phút không thao tác; cookie phiên HttpOnly. |

---

## 9. Thông báo hệ thống (MSG)

Loại: **Nội tuyến** (inline), **Toast**, **Hộp thoại**. "(chuyển trang)" = không hiện thông báo riêng, hệ thống điều hướng như mô tả.

| Mã | Khi nào | Nội dung |
|---|---|---|
| MSG-001 | Đăng nhập thành công | "Đăng nhập thành công!" |
| MSG-002 | Trống username/mật khẩu (đăng nhập) hoặc username (quên MK) | "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu." / "Vui lòng nhập tên đăng nhập." |
| MSG-003 | Sai thông tin đăng nhập | "Sai tên đăng nhập hoặc mật khẩu." |
| MSG-004 | Tài khoản khóa/ngừng hoạt động | "Tài khoản này đã bị khóa hoặc ngừng hoạt động. Vui lòng liên hệ quản trị viên để được hỗ trợ." |
| MSG-005 | Quên MK với username không tồn tại / chưa có email cá nhân | Không thông báo riêng; vẫn sang trang OTP (không tiết lộ username). |
| MSG-006 | OTP sai | "Mã OTP bạn nhập không chính xác." |
| MSG-007 | OTP hết hạn | "Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới." |
| MSG-008 | Sai quá 5 lần liên tiếp | "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau ít phút." |
| MSG-009 | Trống ô OTP | "Vui lòng nhập mã xác minh." |
| MSG-010 | Đổi MK thành công | "Mật khẩu của bạn đã được thay đổi thành công." |
| MSG-011 | Sai MK hiện tại | "Mật khẩu hiện tại bạn nhập không chính xác." |
| MSG-012 | MK mới trùng MK cũ | "Mật khẩu mới phải khác mật khẩu hiện tại." |
| MSG-013 | MK xác nhận không khớp | "Mật khẩu xác nhận không trùng khớp với mật khẩu mới." |
| MSG-014 | Lưu hồ sơ cá nhân thành công | "Cập nhật thông tin cá nhân thành công." |
| MSG-015 | SĐT sai định dạng | "Số điện thoại không hợp lệ." |
| MSG-016 | Email sai định dạng | "Địa chỉ email không hợp lệ." |
| MSG-017 | DS khách hàng trống | "Không có khách hàng để hiển thị." |
| MSG-018 | Lỗi tải dữ liệu (500, mất kết nối DB) | "Có lỗi khi tải dữ liệu. Vui lòng thử lại." |
| MSG-019 | Ngày không hợp lệ (ngày tham gia KH ở tương lai; ngày tạo phiếu sai) | "Ngày nhập vào không hợp lệ." |
| MSG-020 | DS hợp đồng trống | "Không có hợp đồng để hiển thị." |
| MSG-021 | Truy cập KH/HĐ/NV/SP đã bị xóa | "Không tìm thấy [khách hàng / nhà cung cấp / hợp đồng] này. Có thể bản ghi đã bị xoá hoặc huỷ, hoặc đường dẫn không đúng." (dải cảnh báo vàng trên trang danh sách) |
| MSG-022 | Vượt thẩm quyền | "Bạn không có quyền truy cập nội dung này." |
| MSG-023 | DS sản phẩm trống | "Không có sản phẩm để hiển thị." |
| MSG-024 | ID sản phẩm không tồn tại | "Không tìm thấy sản phẩm. Vui lòng quay lại danh sách." |
| MSG-025 | Không có thông báo | "Không có thông báo nào." |
| MSG-026 | Xuất báo cáo xong | "Đã tạo báo cáo." |
| MSG-027 | Xuất báo cáo không có dữ liệu | "Không có dữ liệu cho khoảng thời gian đã chọn." |
| MSG-028 | DS nhân viên trống | "Không có nhân viên để hiển thị." |
| MSG-029 | Sửa nhân viên thành công | (chuyển trang) tới chi tiết nhân viên. |
| MSG-030 | Xác nhận khóa/mở khóa TK | "Bạn có chắc chắn muốn khóa tài khoản của [họ tên]? Nhân viên sẽ không thể đăng nhập vào hệ thống cho tới khi được mở khóa..." (hộp thoại trong trang) |
| MSG-031 | Khóa/mở khóa thành công | (chuyển trang) chi tiết hiện trạng thái mới. |
| MSG-032 | Tạo nhân viên thành công | (chuyển trang) tới chi tiết nhân viên vừa tạo. |
| MSG-033 | Trùng SĐT/CCCD (BR-27) | "Số điện thoại này đã được sử dụng bởi tài khoản khác." / "Số CCCD/CMND này đã được sử dụng bởi tài khoản khác." |
| MSG-034 | Ngày sinh tương lai/không hợp lệ | "Ngày sinh phải là một ngày hợp lệ trong quá khứ." |
| MSG-035 | Giới tính ngoài danh mục | "Giá trị giới tính không hợp lệ." |
| MSG-036 | Thêm KH thành công | (chuyển trang) tới chi tiết KH vừa tạo. |
| MSG-037 | Sửa KH thành công | (chuyển trang) tới chi tiết KH vừa sửa. |
| MSG-038 | Xác nhận xóa KH | "Bạn có chắc chắn muốn xóa khách hàng [tên]? Hành động này không thể hoàn tác." |
| MSG-039 | Xóa mềm KH thành công | (chuyển trang) về danh sách đang đứng (Khách hàng mua / Nhà cung cấp). |
| MSG-040 | Chặn xóa KH có HĐ hiệu lực | "Không thể xóa vì khách hàng đang có hợp đồng hiệu lực." |
| MSG-041 | Tạo HĐ thành công | (chuyển trang) tới chi tiết HĐ vừa tạo. |
| MSG-042 | Sửa HĐ thành công | (chuyển trang) tới chi tiết HĐ vừa sửa. |
| MSG-043 | Hộp thoại "Huỷ bản ghi" HĐ | "Bản ghi này sẽ biến khỏi danh sách. Đây là thao tác sửa nhập liệu sai, không phải huỷ hợp đồng ngoài đời." + ô "Lý do" bắt buộc. |
| MSG-044 | Huỷ bản ghi HĐ thành công | (chuyển trang) về danh sách hợp đồng. |
| MSG-045 | Mốc thời hạn HĐ sai (BR-36) | "Điền cả hai mốc, và ngày hiệu lực phải trước hoặc bằng ngày kết thúc." |
| MSG-046 | Tạo phiếu thành công | "Tạo phiếu hỗ trợ thành công." |
| MSG-047 | Cập nhật phiếu thành công | "Cập nhật phiếu hỗ trợ thành công." |
| MSG-048 | Hộp thoại xóa phiếu | "Bạn có chắc chắn muốn xóa phiếu hỗ trợ [mã phiếu]? Hành động này không thể hoàn tác." (rút gọn) |
| MSG-049 | Xóa phiếu thành công | (chuyển trang) về danh sách phiếu. |
| MSG-050 | DS phiếu trống | "Không có phiếu để hiển thị." |
| MSG-051 | Chuyển trạng thái phiếu sai quy trình | "Trạng thái chuyển không hợp lệ." |
| MSG-052 | Tạo SP thành công | (chuyển trang) tới chi tiết SP vừa tạo. |
| MSG-053 | Sửa SP thành công | (chuyển trang) tới chi tiết SP vừa sửa. |
| MSG-054 | Xác nhận xóa SP | "Bạn có chắc chắn muốn xóa sản phẩm [tên]? Hành động này không thể hoàn tác." |
| MSG-055 | Xóa SP thành công | (chuyển trang) về danh sách sản phẩm. |
| MSG-056 | Mã SP tự sinh bị trùng khi lưu | Không báo; tự sinh mã mới thử lại tối đa 5 lần (BR-55). |

**Thông báo lỗi ngoài danh mục MSG** (đúng nguyên văn trong báo cáo):
- "Thông tin khách hàng (nhà cung cấp) chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc."
- "Thông tin hợp đồng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc."
- "Mã hợp đồng này đã có hợp đồng khác dùng. Kiểm lại số trên bản giấy hoặc nhập mã khác."
- "Không lưu được hợp đồng. Vui lòng thử lại." / "Không lưu được thay đổi. Vui lòng thử lại."
- "Không huỷ được bản ghi này -- vui lòng thử lại." / "Phải có lý do thì mới huỷ được bản ghi hợp đồng."
- "Không xoá được khách hàng. Vui lòng thử lại." / "Không xoá được sản phẩm. Vui lòng thử lại." / "Không xoá được phiếu hỗ trợ. Vui lòng thử lại."
- "Không xoá được phiếu này vì phiếu đang có người xử lý (trạng thái Đang xử lý). Chỉ xoá được phiếu Mới tiếp nhận hoặc Đã đóng."
- "Bạn chỉ xem được khách hàng này: khách do người khác phụ trách."
- "Không thể tự khóa tài khoản đang đăng nhập của chính bạn."
- "Vui lòng nhập đầy đủ và đúng định dạng các trường bắt buộc." (nhân viên) · "Vui lòng nhập đầy đủ các trường bắt buộc." (phiếu)
- "Không thể tạo nhân viên. Vui lòng thử lại." / "Không thể cập nhật nhân viên. Vui lòng thử lại."
- "Có tỉnh trong ô Địa bàn phụ trách vừa được giao cho người khác. Chọn lại địa bàn rồi tạo lại."
- "Đã gửi thông tin tài khoản (mật khẩu tạm mới) tới email cá nhân của nhân viên." · "Không thể gửi thông tin tài khoản. Vui lòng thử lại."
- "Tên sản phẩm không được để trống." · "Vui lòng chọn danh mục sản phẩm." · "Ảnh sản phẩm chỉ nhận file JPG, PNG, GIF hoặc WEBP..."

---

## 10. Quy ước đặt tên & mức độ triển khai hiện tại

- Mã tự sinh: `KH-xxxx`, `NCC-xxx`, Mã ticket, Mã sản phẩm: **người dùng không nhập tay**.
- Mã hợp đồng: **nhập tay theo bản giấy**, phải duy nhất.
- Nhật ký hợp đồng ghi các sự kiện: Khởi tạo, Sửa thông tin, Huỷ bản ghi, đổi trạng thái, chữa sai sót (loại sự kiện riêng).

---

## 11. Quyết định đã chốt & điểm còn mở

### Đã chốt
1. **Không có vai trò CS.** Chỉ Admin, Sales, Kỹ thuật.
2. **BR-44 không làm.** Phiếu gắn với hợp đồng; lỗi/thiết bị ghi ở Mô tả/ghi chú. Bỏ qua phần thiết bị trong mô tả UC-17/21/22.
3. **Mã NCC là `NCC-xxx`** (3 chữ số), mã KH là `KH-xxxx`.
4. Trạng thái phiếu dùng **Đã đóng** (theo BR-40), không dùng "Đã giải quyết" như mô tả UC-16.
5. Trạng thái và ngày tạo của phiếu do **hệ thống gán** (theo BR-39), không phải trường nhập như mô tả UC-21.
6. `NotificationScheduler` chu kỳ 60 phút, 2 loại thông báo tự sinh (xem 5.7). **Phiếu: báo trước 24 giờ** so với hạn SLA.
7. Cách lấy IP cho BR-01: không cần thay đổi, giữ cách hiện tại.
8. Hai gói `poscs.service` và `poscs.validator` đã được xác nhận và đã tồn tại trong code (mục 2).

### Còn mở `[CẦN XÁC NHẬN]`
1. **Ngưỡng thông báo "hợp đồng sắp hết hạn":** báo cáo không nói riêng; tạm dùng BR-17 (≤ 30 ngày). Đổi nếu người dùng chỉ định khác.
2. **Luồng chi tiết** UC-36 → UC-54: báo cáo chỉ có mô tả ngắn.
3. Đối chiếu `PERMISSIONS.md`, `README.md`, `db/schema.sql` với tài liệu này.
