# Sinh tài liệu test theo mẫu SEP490

`gen_unittest_function.py` sinh **Report 5.1 - Unit Test (Function)** từ chính
mã nguồn test và kết quả chạy test, thay vì gõ tay 385 test case vào Excel.

## Cách chạy

```
ant test                                        # bắt buộc chạy trước
python tools/testdoc/gen_unittest_function.py
```

Kết quả mặc định: `%USERPROFILE%\Documents\POSCS_UnitTest_Function.xlsx`

File .xlsx là tài liệu nộp nên **để ngoài repo**. Muốn ghi chỗ khác:

```
python tools/testdoc/gen_unittest_function.py -o "D:\BaoCao\UnitTest.xlsx"
```

Script đọc `build/test/results/*.xml` để lấy trạng thái Passed/Failed/Untested
**thật**. Chưa chạy `ant test` thì script dừng - tài liệu không bao giờ ghi
"Passed" cho test chưa từng chạy. Chạy lại `ant test` rồi sinh lại mỗi khi sửa
code hoặc thêm test.

Trên máy chưa cài Ant vào PATH (xem `.github/workflows/tests.yml`):

```
"C:\Program Files\NetBeans-25\netbeans\extide\ant\bin\ant.bat" \
  -Dj2ee.platform.classpath="%CD%\lib-build\jakarta.servlet-api-6.1.jar" \
  -Dlibs.CopyLibs.classpath="%CD%\lib-build\org-netbeans-modules-java-j2seproject-copylibstask.jar" \
  test
```

## Cách script hiểu test

Tên method test trong repo theo quy ước `<method>_<điều kiện>_<kỳ vọng>`:

```
insert_noRowAffected_rollsBackAndReturnsMinusOne
       └── dòng Condition      └── dòng Confirm
```

Mỗi `@Test` thành một cột UTCID; mỗi điều kiện/kỳ vọng khác nhau thành một
dòng, đánh dấu `O` ở giao điểm. Cuối mỗi sheet có bảng đối chiếu
`UTCID -> tên method JUnit` để truy ngược về code.

Test đặt tên chỉ có hai đoạn (`url_javascriptSchemeRejected`) được tách tại từ
mở đầu phần kỳ vọng (`OUTCOME_WORDS`).

## Tiếng Việt

Toàn bộ nhãn, tiêu đề cột và sheet Hướng dẫn đã là tiếng Việt. Phần nội dung
(điều kiện / kỳ vọng của từng UTCID) được dịch qua `vi_glossary.json` — từ
điển ánh xạ chuỗi sinh từ tên method test sang tiếng Việt.

Thêm test mới mà chưa có bản dịch thì script vẫn chạy, giữ nguyên chuỗi tiếng
Anh và in ra cuối danh sách những chuỗi cần bổ sung, đã định dạng sẵn để dán
thẳng vào `vi_glossary.json`.

Tên lớp, tên hàm và tên sheet giữ nguyên tiếng Anh vì là định danh trong mã
nguồn.

## Hai chỗ cần người rà lại

1. **Cột `Type (N/A/B)`** là suy đoán từ tên test (danh sách từ khoá trong
   `BOUNDARY_WORDS` / `ABNORMAL_WORDS` / `REJECTION_WORDS`). Phân bố hiện tại
   N=145 / A=225 / B=15 - số ca biên thấp vì bộ test vốn ít ca biên, không
   phải do script đếm sai. Rà lại trước khi nộp.
2. **`ALIASES` và `WHOLE_CLASS`** ánh xạ tiền tố tên test sang method
   production. Controller là servlet điều phối theo `action` nên tiền tố test
   là tên action, phải khai báo tay. Thêm controller/handler mới thì bổ sung
   vào hai bảng này, nếu không sheet sẽ mang tên action thay vì tên handler.

## Phạm vi

Chỉ gồm unit test. Ba lớp trong `poscs.integration` bị loại ra
(`EXCLUDED_TEST_CLASSES`) vì thuộc Report 5.2.

---

# Report 5.2 - Integration Test (Blackbox)

`gen_integration_blackbox.py` dựng tài liệu kiểm thử tích hợp hộp đen.

```
python tools/testdoc/gen_integration_blackbox.py
```

Kết quả mặc định: `%USERPROFILE%\Documents\POSCS_IntegrationTest_Blackbox.xlsx`

Khác tài liệu Unit Test, đây là kiểm thử **chạy tay trên giao diện** nên không
sinh được từ mã nguồn. Nội dung test case nằm ở `blackbox/*.json`, script chỉ
lo trình bày ra đúng mẫu. Sửa test case thì sửa file JSON rồi chạy lại.

## Cột kết quả để trống có chủ đích

Cột "Kết quả thực tế" và "Trạng thái" đặt là **Chưa chạy** cho mọi test case,
vì chưa có ai thực sự bấm thử. Sau mỗi vòng chạy tay, người test điền tay vào
file .xlsx (đừng sinh lại file, sẽ mất kết quả đã điền). Nếu cần sinh lại thì
chép cột kết quả sang file mới.

## Thêm chức năng mới

Thêm một phần tử vào mảng `functions` của file JSON tương ứng:

```json
{
  "name": "Tên chức năng tiếng Việt",
  "sheet": "TenSheet",            // <= 31 ký tự, không trùng
  "prefix": "TC_XXX",             // mã test case thành TC_XXX_001, _002...
  "screen": "/POSCS/...",
  "description": "...",
  "precondition": "...",
  "cases": [
    {"d": "mô tả", "s": ["bước 1", "bước 2"], "e": "kết quả mong đợi",
     "t": "dữ liệu kiểm thử", "p": "tiền điều kiện riêng", "o": "hậu điều kiện",
     "n": "ghi chú"}
  ]
}
```

Bắt buộc: `d`, `s`, `e`. Script tự kiểm tên sheet quá dài, trùng tên sheet,
trùng mã test case và thiếu trường bắt buộc — có lỗi thì dừng, không ghi file.

Thứ tự module trong tài liệu do `SPEC_ORDER` trong script quy định.

## Chạy thật một phần test case

`run_blackbox.py` tự động hoá các test case kiểm được bằng request/phản hồi:
đăng nhập, kiểm tra dữ liệu đầu vào, phân quyền 403, endpoint JSON, chống path
traversal. Những ca phải nhìn giao diện (bố cục, dropdown, hộp thoại xác nhận,
nội dung file Excel/PDF) vẫn phải chạy tay.

Dựng môi trường một lần:

```bash
mysql -h127.0.0.1 -uroot -p -e "CREATE DATABASE poscs_bbtest CHARACTER SET utf8mb4"
mysql -h127.0.0.1 -uroot -p poscs_bbtest < db/schema.sql
```

`db/schema.sql` có sẵn khách hàng/hợp đồng/sản phẩm/phiếu mẫu nhưng **bảng
users rỗng**, phải thêm tài khoản cho đủ 4 vai trò (mật khẩu băm bằng jbcrypt
trong `lib/`). Các id 1, 15, 16, 17 đang bị dữ liệu mẫu tham chiếu nên tài khoản
phải mang đúng các id đó.

Triển khai WAR lên Tomcat với biến môi trường `DB_URL` trỏ vào `poscs_bbtest`,
rồi:

```bash
python tools/testdoc/run_blackbox.py http://localhost:8099/POSCS
python tools/testdoc/gen_integration_blackbox.py
```

Bước 1 ghi `blackbox_results.json`, bước 2 nối kết quả vào cột "Kết quả thực
tế"/"Trạng thái" của vòng 1. Test case không có trong file kết quả vẫn giữ
trạng thái "Chưa chạy".

Lưu ý khi viết thêm ca tự động:

- POST tới `/customer`, `/product`, `/contract` phải gửi **multipart** — các
  controller này khai báo `@MultipartConfig` và gọi `request.getPart(...)`,
  gửi urlencoded sẽ thành HTTP 500.
- Mọi POST cần tham số `csrfToken` lấy từ một trang có render ô ẩn đó
  (`/changePassword.jsp` hợp với mọi vai trò).
- Tên tham số id khác nhau giữa các handler: `customerId`, `contractId`,
  `productId`, `ticketId`, `userId` khi cập nhật nhưng đều là `id` khi xoá.
  Dùng sai tên thì request dừng ở nhánh "không tìm thấy" **trước** bước kiểm
  quyền, và ca kiểm phân quyền sẽ đạt vì lý do sai.
- Luôn có một ca đối chứng đường đi đúng cho mỗi module. Không có nó thì một
  payload sai toàn tập vẫn làm mọi ca "thiếu trường X" đều đạt.

## Ba lượt chạy tự động

| Script | Kiểm cái gì | Thời gian |
|---|---|---|
| `run_blackbox.py` | Mã HTTP và tham số redirect: đăng nhập, kiểm tra dữ liệu vào, phân quyền 403, endpoint JSON, path traversal | ~30 giây |
| `run_blackbox_ui.py` | Nội dung thật: HTML trả về (số dòng, thông báo), file .xls/.pdf tải xuống, tải file lên, mã OTP đọc từ log | ~3 phút |
| `run_blackbox_rest.py` | Nhập hợp đồng từ PDF, các ca biên về ngày, và những ca phải **chờ theo đồng hồ thật** | ~22 phút |

Chạy theo đúng thứ tự trên; mỗi lượt gộp kết quả vào cùng `blackbox_results.json`.

```bash
mysql -h127.0.0.1 -uroot -p poscs_bbtest < tools/testdoc/blackbox/fixtures.sql
python tools/testdoc/run_blackbox.py
python tools/testdoc/run_blackbox_ui.py --log <đường dẫn catalina stdout>
python tools/testdoc/run_blackbox_rest.py --log <đường dẫn catalina stdout>
python tools/testdoc/gen_integration_blackbox.py
```

`--log` trỏ tới file hứng stdout/stderr của Tomcat. `EmailUtil` chạy DEV MODE khi
chưa cấu hình SMTP nên in mã OTP và mật khẩu tạm ra đó — nhờ vậy chạy được trọn
luồng quên mật khẩu và gửi thông tin tài khoản mà không cần hộp thư thật.

### Bốn thứ bắt buộc phải làm đúng thứ tự

1. **Nạp lại `blackbox/fixtures.sql` trước mỗi lượt.** Bộ test có ca đổi mật
   khẩu thật và khoá tài khoản thật; không nạp lại thì lượt sau đăng nhập
   không được và mọi ca phía sau trượt oan.
2. **Khởi động lại Tomcat trước lượt 3.** Hạn mức 10 yêu cầu OTP/IP trong 15
   phút nằm trong bộ nhớ máy chủ; lượt trước dùng hết thì nhánh OTP không chạy
   được.
3. **`test_otp_quota` phải sau `test_otp_timing`** — nó cố tình làm cạn hạn mức.
4. **`test_login_lockout` là phần cuối cùng** — nó khoá IP 15 phút, chạy sớm thì
   chặn mọi ca đăng nhập sau đó.

Chạy kèm `--skip-slow` để bỏ hai đoạn chờ dài (OTP hết hạn 5 phút, khoá IP 15
phút); ba ca tương ứng sẽ thành "N/A" kèm lý do.

### Cạm bẫy khi viết thêm ca tự động

- POST tới `/customer`, `/product`, `/contract` phải gửi **multipart** — các
  controller này khai báo `@MultipartConfig` và gọi `request.getPart(...)`;
  gửi urlencoded sẽ thành HTTP 500.
- Mọi POST cần `csrfToken` lấy từ trang có render ô ẩn đó (`/changePassword.jsp`
  hợp với mọi vai trò đã đăng nhập).
- Tên tham số không nhất quán giữa các handler: cập nhật dùng `customerId`,
  `contractId`, `productId`, `ticketId`, `userId`; xoá thì tất cả dùng `id`;
  xác thực OTP dùng `otpCode`; gỡ sản phẩm khỏi hợp đồng dùng
  `contractProductId`. Dùng sai tên thì request dừng ở nhánh "không tìm thấy"
  **trước** bước kiểm quyền, và ca kiểm phân quyền sẽ đạt vì lý do sai.
- Ô "Xếp hạng quan hệ" gửi **tên hằng enum** (`GOOD`/`NEEDS_REVIEW`/`BAD`/
  `AT_RISK`), không phải chuỗi tiếng Việt.
- Quên mật khẩu tra theo **email công ty hoặc tên đăng nhập**, không phải email
  cá nhân.
- Danh sách Nhân viên và Sản phẩm dựng bằng lưới thẻ chứ không phải `<table>`;
  đếm `<tr>` sẽ luôn ra 0. Lưới rỗng vẫn có một phần tử con `.empty-state`.
- Khi kiểm "kết quả lọc không lẫn giá trị khác", chỉ đọc chữ trong khối kết
  quả: đọc cả trang sẽ dính tên mọi trạng thái trong `<option>` của bộ lọc.
- Nhập hợp đồng từ PDF: ô chọn file tên là `file`; các ô `/Ch` chỉ nhận đúng
  giá trị trong `/Opt` của mẫu; khách hàng chưa có trong hệ thống thì phải điền
  đủ Tỉnh/Thành + Xã/Phường + Địa chỉ chi tiết. Handler **forward** lại trang
  nhập cả khi thành công lẫn khi lỗi, phải đọc chữ trên trang chứ đừng nhìn mã
  HTTP.
- Luôn có ca đối chứng đường đi đúng cho mỗi module. Không có nó thì một payload
  sai toàn tập vẫn làm mọi ca "thiếu trường X" đều đạt.
