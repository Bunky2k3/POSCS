# Sinh tài liệu test theo mẫu SEP490

`gen_unittest_function.py` sinh **Report 5.1 - Unit Test (Function)** từ chính
mã nguồn test và kết quả chạy test, thay vì gõ tay 385 test case vào Excel.

## Cách chạy

Ba bước, **đúng thứ tự này**: `ant test` xoá sạch `build/test/results` trước
khi chạy, nên bản junit của Node phải sinh SAU, nếu không phần JavaScript sẽ
mang nhãn "Chưa chạy".

```
ant test                                        # bắt buộc chạy trước

node --test --test-reporter=junit      --test-reporter-destination=build/test/results/TEST-js.xml      "test/js/**/*.test.js"                     # phần JavaScript

python tools/testdoc/gen_unittest_function.py
```

Thiếu Node thì phần Java vẫn sinh bình thường, phần JavaScript được liệt kê
nhưng đánh dấu "Chưa chạy" kèm cảnh báo - thà thiếu còn hơn ghi Đạt cho thứ
chưa từng chạy.

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

## Test JavaScript

Mã nguồn `web/js/appshell.js` không có lớp nào, nên nhóm sheet lấy tên file làm
"lớp": `appshell_<hàm>`. Tiêu đề test trong `test/js/*.test.js` viết theo quy
ước

```
test('<hàm>: <điều kiện> → <kỳ vọng>', ...)
```

đúng ba phần mà ma trận UTCID cần, chỉ khác là viết bằng tiếng Việt cho đọc
được thẳng trong terminal. Thiếu dấu `→` thì cả phần mô tả bị coi là điều kiện
và script cảnh báo ở cuối - kỳ vọng để trống cho người viết tự điền, chứ không
đoán bừa một câu vào tài liệu nộp.

Bộ xuất junit của Node escape hai lần (`"` ra thành `&amp;quot;`), script tự gỡ
trước khi đối chiếu tên - không gỡ thì mọi tiêu đề có dấu nháy kép đều bị ghi
nhầm là "Chưa chạy".

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

## Chạy thật các test case

Bốn script tự động hoá những ca kiểm được bằng request / phản hồi và bằng cách
đọc thẳng CSDL kiểm thử. Ca nào chỉ kiểm được bằng mắt (hộp thoại xác nhận,
lọc bằng JavaScript phía trình duyệt) thì script **không ghi gì** — ca đó giữ
"Chưa chạy" cho người test chạy tay; dòng in ra mang nhãn `TAY`.

### Dựng môi trường một lần

```bash
mysql -h127.0.0.1 -uroot -p -e "CREATE DATABASE poscs_bbtest CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
mysql -h127.0.0.1 -uroot -p --default-character-set=utf8mb4 poscs_bbtest -e "source db/schema.sql"
mysql -h127.0.0.1 -uroot -p --default-character-set=utf8mb4 poscs_bbtest -e "source tools/testdoc/blackbox/fixtures.sql"
```

`db/schema.sql` gieo sẵn 12 nhân viên (sales2..6, kythuat2..5, cskh2..4 — mật
khẩu chung `Poscs@123`, có sẵn địa bàn), khách hàng / hợp đồng / sản phẩm /
phiếu mẫu; **không có Admin**. `fixtures.sql` thêm `admin`, `sale01`, `tech01`,
`tech02`, `locked01`, `doimk01` (xem đầu file) — không cố định `user_id` nữa,
dữ liệu demo tra người dùng theo username.

Triển khai bản build lên Tomcat riêng (cổng 8099 là mặc định của các script)
với `DB_URL=jdbc:mysql://127.0.0.1:3306/poscs_bbtest`, `UPLOAD_DIR` trỏ vào một
thư mục trống, và **bỏ `MAIL_USERNAME` / `MAIL_PASSWORD`** để `EmailUtil` chạy
DEV MODE (in mã OTP và mật khẩu tạm ra stdout thay vì gửi thư thật).

`testdb.py` là chỗ duy nhất các script chạm CSDL (tra id, dựng trigger giả lỗi,
gieo cấp trên); cấu hình qua `POSCS_TEST_DB` (mặc định `poscs_bbtest`),
`MYSQL_EXE`, `DB_USER` / `DB_PASSWORD`. Nó **từ chối chạy trên `poscs_db`**.

### Bốn lượt hộp đen

| Script | Kiểm cái gì | Thời gian |
|---|---|---|
| `run_blackbox.py` | Mã HTTP và tham số redirect: đăng nhập, kiểm tra dữ liệu vào, phân quyền 403, endpoint JSON, path traversal; form / thời hạn / tài liệu hợp đồng | ~5 giây |
| `run_blackbox_ui.py` | Nội dung thật: cột bảng, thông báo trên trang đích, file .xls/.pdf, tải file lên, OTP / mật khẩu tạm đọc từ log, đối chiếu CSDL sau mỗi thao tác ghi | ~15 giây |
| `run_blackbox_branches.py` | Các ca thêm theo PR #149–#167: phạm vi Sales, trang nhà cung cấp, luật Người hỗ trợ, nhánh ghi CSDL hỏng (trigger), notfound giữ đúng danh sách, từ khoá có `&` `#`, lọc Người xử lý, dải nhắc hồ sơ | ~10 giây |
| `run_blackbox_rest.py` | Các ca biên về ngày, và những ca phải **chờ theo đồng hồ thật** (OTP hết hạn, khoá IP 15 phút) | ~22 phút |

**`run_blackbox.py` GHI ĐÈ** `blackbox_results.json`; ba lượt sau gộp thêm vào.
Chạy lại cả bộ thì bắt đầu từ lượt này, không thì kết quả cũ của ca đã đổi
nghĩa vẫn nằm lại trong file.

```bash
python tools/testdoc/run_blackbox.py http://localhost:8099/POSCS
python tools/testdoc/run_blackbox_ui.py http://localhost:8099/POSCS --log <catalina stdout>
python tools/testdoc/run_blackbox_branches.py http://localhost:8099/POSCS --log <catalina stdout>
UPLOAD_DIR=<thư mục upload của Tomcat> python tools/testdoc/run_blackbox_rest.py http://localhost:8099/POSCS --log <catalina stdout>
python tools/testdoc/gen_integration_blackbox.py
```

### Bốn thứ bắt buộc phải làm đúng thứ tự

1. **Nạp lại `blackbox/fixtures.sql` trước khi bắt đầu.** Bộ test đổi mật khẩu
   thật (`tech02`, `doimk01`) và khoá tài khoản thật; lượt 3, lượt 4 và
   `run_systemtest.py` tự nạp lại lúc bắt đầu.
2. **Khởi động lại Tomcat trước khi chạy cả bộ.** Hạn mức 10 yêu cầu OTP/IP và
   bộ đếm 5 lần đăng nhập sai nằm trong bộ nhớ máy chủ; lượt trước dùng hết thì
   nhánh OTP / đăng nhập trượt oan.
3. **`test_otp_quota` phải sau `test_otp_timing`** — nó cố tình làm cạn hạn mức.
4. **`run_blackbox_rest.py` là lượt cuối** — `test_login_lockout` khoá IP 15 phút.

Chạy kèm `--skip-slow` để bỏ hai đoạn chờ dài (OTP hết hạn 5 phút, khoá IP 15
phút); ba ca tương ứng sẽ thành "N/A" kèm lý do.

### Cạm bẫy khi viết thêm ca tự động

- POST tới `/customer`, `/product` phải gửi **multipart** (`@MultipartConfig`,
  gọi `getPart`); gửi urlencoded thành HTTP 500. `/contract` thì **ngược lại**:
  từ V34 không còn multipart, gửi multipart tới đó là mất `csrfToken` và nhận 403.
- Mọi POST cần `csrfToken` lấy từ trang có render ô ẩn đó (`/changePassword.jsp`
  hợp với mọi vai trò đã đăng nhập).
- Tên tham số không nhất quán giữa các handler: cập nhật dùng `customerId`,
  `contractId`, `productId`, `ticketId`, `userId`; xoá thì tất cả dùng `id`;
  xác thực OTP dùng `otpCode`; gỡ hàng hoá dùng `contractProductId`; huỷ bản ghi
  hợp đồng cần `voidReason`. Dùng sai tên thì request dừng ở nhánh "không tìm
  thấy" **trước** bước kiểm quyền, và ca kiểm phân quyền sẽ đạt vì lý do sai.
- Sửa khách hàng phải gửi ô `roles` (một hoặc hai lần) — thiếu thì bị `invalid`.
- Ô "Xếp hạng quan hệ" gửi **tên hằng enum** (`GOOD`/`NEEDS_REVIEW`/`BAD`/
  `AT_RISK`), không phải chuỗi tiếng Việt.
- Quên mật khẩu tra theo **tên đăng nhập** (V36); OTP gửi tới **email cá nhân**
  trong hồ sơ — lọc log theo đúng địa chỉ đó.
- Đăng nhập bằng tài khoản không tồn tại cũng tính một lần sai vào hạn mức 5
  lần / IP. Sau mỗi cụm ca sai mật khẩu, đăng nhập đúng một lần để xoá bộ đếm.
- Danh sách Nhân viên và Sản phẩm dựng bằng lưới thẻ chứ không phải `<table>`;
  thẻ sản phẩm chưa có ảnh hiện biểu tượng thay cho `<img>`. Danh sách phiếu
  của kỹ thuật viên mặc định chỉ "Phiếu của tôi" — kiểm toàn bộ thì gửi
  `assignee=all`.
- Kiểm câu thông báo thì **mở trang đích** (có trang đá tiếp sang trang khác),
  và đọc chữ trong đúng khối kết quả / khối cảnh báo, không đọc cả trang.
- Hồ sơ cá nhân chỉ nhận **số di động** (đầu 3/5/7/8/9); form nhân viên của
  Admin thì nhận cả số bàn.
- Luôn có ca đối chứng đường đi đúng cho mỗi module, và **đừng ghi Đạt vô điều
  kiện** (`expect(..., True)`): không kiểm được thì dùng `manual()` để ca giữ
  "Chưa chạy".

### Ba ca cần môi trường riêng

Không chạy được trong lượt tự động thường; dựng thêm rồi chạy tay:

- **TC_EMPSEND_003** (gửi mail lỗi thì không đổi mật khẩu): khởi động Tomcat
  với `MAIL_USERNAME`/`MAIL_PASSWORD` bất kỳ và `MAIL_SMTP_HOST=127.0.0.1`,
  `MAIL_SMTP_PORT=1`. `EmailUtil` bỏ DEV MODE, kết nối SMTP bị từ chối ngay nên
  không phải chờ timeout.
- **TC_DASH_008** (Dashboard trên CSDL rỗng): tạo CSDL thứ hai, nạp
  `db/schema.sql`, `TRUNCATE` các bảng nghiệp vụ (giữ users/roles/departments/
  provinces/districts), nạp `fixtures.sql`, rồi trỏ `DB_URL` sang đó.
- **TC_NOTI_009** (tác vụ nền sinh thông báo hợp đồng sắp hết hạn): tạo hợp
  đồng kết thúc trong 30 ngày, xoá sạch `notifications` có
  `ref_type='contract_expiring'`, khởi động lại Tomcat rồi chờ ~70 giây
  (`NotificationScheduler` chạy lần đầu sau 1 phút, sau đó mỗi 60 phút). Khởi
  động lại lần nữa để xác nhận không sinh bản ghi trùng.

### Ca cần giả lập lỗi CSDL

Các nhánh `delete_failed`, `evaluate_failed`, `roles_not_saved`,
`toggle_failed`, `province_not_saved`, `files_not_saved`, `create_failed`
chỉ chạy khi câu lệnh ghi xuống CSDL hỏng giữa chừng — bấm trên giao diện
không tạo ra được. Cách dựng: trên **CSDL kiểm thử** (không bao giờ trên
`poscs_db`), tạo một trigger `SIGNAL` chặn đúng câu lệnh đó, chạy ca, rồi xoá
trigger ngay.

```sql
DELIMITER //
-- Xoá mềm hỏng: đổi tên bảng theo ca (enterprises / products / technicalrequests)
CREATE TRIGGER tt_gia_lap_loi BEFORE UPDATE ON enterprises FOR EACH ROW
BEGIN
  IF NEW.is_deleted <> OLD.is_deleted THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gia lap loi';
  END IF;
END//
DELIMITER ;
-- ... chạy ca trên giao diện ...
DROP TRIGGER tt_gia_lap_loi;
```

| Ca | Trigger |
|---|---|
| TC_CUSDEL_007 | `BEFORE UPDATE ON enterprises`, điều kiện `is_deleted` đổi |
| TC_PRDDEL_006 | như trên, bảng `products` |
| TC_TKDEL_006 | như trên, bảng `technicalrequests` |
| TC_EMPBAN_007 | như trên, bảng `users` |
| TC_CUSEVAL_008 | `BEFORE INSERT ON customer_lifecycle_events` (không cần điều kiện) |
| TC_CUSADD_024, TC_CUSEDIT_016 | `BEFORE INSERT ON enterprise_roles` |
| TC_EMPADD_018, TC_EMPEDIT_010 | `BEFORE INSERT ON user_provinces` |
| TC_PRDADD_012 | `BEFORE INSERT ON productimages` |
| TC_PRDEDIT_010 | `BEFORE DELETE ON productimages` |
| TC_PRDEDIT_011 | cả hai trigger `productimages` trên |
| TC_CTRADD_017 | `BEFORE INSERT ON contracts` |

Mỗi trigger dùng một tên riêng nếu cần hai cái cùng lúc (TC_PRDEDIT_011).
Quên xoá trigger thì mọi ca sau đó trên cùng bảng đều hỏng oan.

### Lưu ý sau lần cập nhật 30/09/2026

Test case đã được viết lại cho khớp code `main` @ 5309da6 (PR #149–#167, vai
CSKH gộp vào Sales, hợp đồng không còn ô ngày ở form tạo / mã HD-xxxx / link
PDF, sản phẩm không có đơn giá). Ca nào đổi nội dung thì kết quả cũ đã gỡ về
"Chưa chạy"; ca mới đều "Chưa chạy".

Script chạy tự động (`run_blackbox*.py`, `run_systemtest.py`) và
`blackbox/fixtures.sql` đã cập nhật theo cùng đợt (vai CSKH bỏ, TC_LOGIN dồn
số, lượt 4 `run_blackbox_branches.py` cho các ca mới). Trong đợt đó cũng bỏ
mọi chỗ ghi "Đạt" vô điều kiện: ca không kiểm được tự động giờ giữ "Chưa chạy".

### Lưu ý sau lần cập nhật 01/10/2026

Trang Sửa tách thành Sửa khách hàng / Sửa nhà cung cấp (PR #172) và Sales không
đổi được người phụ trách chính ở trang Sửa (PR #173). TC_CUSEDIT_016 đổi bước
(ô "Đồng thời là ..." thay hai ô tick) nên kết quả cũ đã gỡ; TC_CUSEDIT_017–028
là ca mới, "Chưa chạy", chưa có trong `run_blackbox*.py`. TC_CUSEDIT_013/014 giữ
kết quả vì vẫn đúng với luật mới. POST `action=update` giờ kèm `kind` của trang
(thiếu thì coi là trang khách hàng): vai của trang luôn được giữ.

---

# Report 5.3 - System Test

Kiểm các **luồng nghiệp vụ end-to-end** — thứ chỉ hỏng khi ghép các chức năng
lại với nhau, khác Report 5.2 vốn kiểm từng chức năng độc lập.

```bash
mysql -h127.0.0.1 -uroot -p poscs_bbtest < tools/testdoc/blackbox/fixtures.sql
python tools/testdoc/run_systemtest.py --log <đường dẫn catalina stdout>
python tools/testdoc/gen_system_test.py
```

Kết quả mặc định: `%USERPROFILE%\Documents\POSCS_SystemTest.xlsx`

Nội dung ở `systemtest/*.json`, kết quả chạy ở `systemtest_results.json`.
Mẫu 5.3 có **ba vòng chạy**; script chỉ điền vòng 1.

## Các bước trong một luồng phụ thuộc nhau

Tài khoản tạo ở bước 1 được dùng để đăng nhập ở bước 4; hợp đồng tạo ở luồng
khách hàng được dùng lại ở luồng phiếu hỗ trợ và luồng sản phẩm. Hỏng một bước
thì các bước sau mất chỗ dựa, nên mỗi luồng tự dừng và ghi "N/A" kèm lý do cho
phần còn lại thay vì báo trượt hàng loạt (`skip_rest`).

## Hai ca cần khởi động lại máy chủ

`NotificationScheduler` chạy lần đầu 1 phút sau khi khởi động rồi lặp mỗi 60
phút, nên không chạy được trong cùng một lượt:

```bash
mysql ... -e "DELETE FROM notifications WHERE ref_type='contract_expiring'"
# khởi động lại Tomcat, chờ ~70 giây
python tools/testdoc/run_systemtest.py --part scheduler  --log <...>
# khởi động lại lần nữa, chờ ~70 giây
python tools/testdoc/run_systemtest.py --part scheduler2 --log <...>
```

## Cạm bẫy riêng của lượt này

- **Sửa hợp đồng về quá khứ phải lùi cả ngày ký.** `isValid()` đòi ngày ký ≤
  ngày hiệu lực ≤ ngày kết thúc; giữ ngày ký hôm nay mà lùi ngày hiệu lực thì
  bản cập nhật bị từ chối và trạng thái không đổi — rất dễ tưởng nhầm là lỗi
  tính trạng thái hợp đồng.
- **Lịch sử đổi trạng thái phiếu là `<ul class="history-list">`**, không phải
  `<table>`; đếm `<tr>` sẽ luôn ra 0.
- **Ô thống kê trên Dashboard phải đọc theo `.kpi-label` / `.kpi-value`.** Bắt
  số theo chữ "khách hàng" trong toàn trang sẽ dính menu bên trái.
- **Đừng so sánh chuỗi tiếng Việt qua tham số `-e` của mysql** — vướng cả mã
  hoá lẫn collation. Dùng cột khác tương đương (`resolved_at IS NULL` thay cho
  `status <> 'Đã đóng'`).
- **Từ khoá tìm kiếm phải là chuỗi con thật sự của dữ liệu.** Tạo phiếu mô tả
  "SLA qua 160255" rồi tìm "SLA 160255" sẽ không ra dòng nào.
- Hai lần tạo dữ liệu trong cùng lượt không được trùng số điện thoại hay mã số
  thuế — các cột đó UNIQUE.

---

# Report 5.4 - User Acceptance Test

```bash
python tools/testdoc/gen_uat.py
```

Kết quả mặc định: `%USERPROFILE%\Documents\POSCS_UserAcceptanceTest.xlsx`

Đây là bản **khách hàng ký nghiệm thu**, nên script không tự ý đánh dấu chấp
nhận. Mỗi mục trong `uat_checklist.json` khai một danh sách mã test case làm
căn cứ; ô "Đạt" chỉ được tích khi **tất cả** mã đó đều Đạt trong kết quả chạy
thật của Report 5.2 và 5.3. Mục nào có test case trượt thì bị tích vào ô "Chưa
đạt"; mục không khai căn cứ (tốc độ, bố cục, tài liệu bàn giao) để trống cả hai
ô cho bên nghiệm thu tự đánh giá.

Vì vậy phải chạy xong 5.2 và 5.3 trước khi sinh file này, nếu không mọi mục đều
ra "chưa đối chiếu được".

Cột "Căn cứ" là phần thêm so với mẫu gốc, để người ký lần ngược về đúng test
case đã chạy. Không cần thì xoá cột H.

Hai mã căn cứ đặc biệt không đến từ hai lượt trên mà kiểm tay rồi ghi thẳng vào
`systemtest_results.json`:

- `MAN_BCRYPT` — truy vấn CSDL xác nhận mọi bản ghi `users` lưu mật khẩu dạng
  bcrypt và bảng không có cột nào chứa bản rõ.
- `MAN_CSRF` — gửi POST xoá khách hàng bằng phiên hợp lệ nhưng thiếu token và
  với token giả mạo, cả hai phải trả 403.

Chạy lại hai kiểm tra này khi cần làm mới bằng chứng.
