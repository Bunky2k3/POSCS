# QA Report - Task 12.1

**Kết luận:** APPROVED - Đạt yêu cầu UC & BR
**Kết quả `ant test`:** 2.437 pass / 0 fail / 0 error / 3 skipped (92 test suites: 89 unit test suites pass 100%, tăng +5 test ở `PasswordResetControllerTest` từ 1 lên 6; 3 integration test suites trong `test/poscs/integration/` mặc định bỏ qua theo `rules.md` §0; `BUILD SUCCESSFUL`). Ngoài ra đã chạy `node --test 'test/js/**/*.test.js'` → **31 pass / 0 fail** và `node --check tools/guide/shots/general.mjs` → **0 lỗi**.
**Kiểm tra JSP:** `ant compile-jsps` trả `BUILD SUCCESSFUL` (nhưng trên máy này `compile.jsps=false` bỏ qua bước dịch); đã biên dịch kiểm chứng trực tiếp bằng `org.apache.jasper.JspC` của Tomcat 11.0 cho cả 4 JSP sửa cùng `verifyOtp.jsp` (`forgotPassword.jsp`, `login.jsp`, `changePassword.jsp`, `verifyOtp.jsp`, `jsp/guide/general.jsp`) → `Generation completed with [0] errors` (exit code 0) và xác minh mã sinh ra cho hai nhánh `<c:when>` mới (`expired`, `too_many_attempts`).

**Phạm vi:** Task 12.1 — Quên mật khẩu: sửa `PasswordResetController.handleVerifyOtp` đọc đúng tham số `otpCode` của `verifyOtp.jsp`; bổ sung câu báo lỗi còn thiếu theo SPEC (`MSG-007` ở `forgotPassword.jsp`, `MSG-008` ở `changePassword.jsp`); đổi *"Phòng Nhân sự / IT"* sang *"Admin"* ở `login.jsp` và `forgotPassword.jsp`; bỏ đoạn lách tạm ở `tools/guide/shots/general.mjs`, chụp lại ảnh hướng dẫn liên quan và cập nhật `web/jsp/guide/general.jsp`; bổ sung unit test ở `PasswordResetControllerTest` (`UC-03`, `UC-04`, `UC-05`; `BR-01`, `BR-04`, `BR-05`; `MSG-007`, `MSG-008`). Đối chiếu với `docs/SPEC.md`, `rules.md`, mã nguồn `AuthService` / `AuthValidator` / `AuthenticationFilter` / `verifyOtp.jsp` / `resetPassword.jsp` và các dòng con của Task 12.1 trong `task_tracker.md`; thay đổi thực tế lấy từ `git status`, `git log main..HEAD` và `git diff main` trên nhánh `fix/task-12.1-otp-quen-mat-khau`.

## Danh sách lỗi

Không phát hiện lỗi (**0 🔴 Critical, 0 🟡 Major, 0 🟢 Minor**).

**APPROVED - Đạt yêu cầu UC & BR.** Đã rà soát đầy đủ toàn bộ file mã nguồn, JSP, test, kịch bản chụp ảnh và 4 ảnh chụp hướng dẫn (`01`, `04`, `05`, `06`), đối chiếu từng ký tự hai thông báo `MSG-007` và `MSG-008` với `docs/SPEC.md` §9, dịch kiểm chứng 5 JSP bằng Jasper của Tomcat 11, tự chạy toàn bộ `ant test` (2.437 pass, 0 fail) cùng `node --test` và đối chiếu đủ 3 tiêu chí "Xong khi" — tất cả đều đạt, không chạm bất kỳ file nào ở mục "Không đụng".

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ (UC-03, UC-04, UC-05; BR-01, BR-04, BR-05; MSG-007, MSG-008)

| Hạng mục | Kết quả |
|---|---|
| `src/java/poscs/controller/PasswordResetController.java` › `handleVerifyOtp()` (dòng 136) | Đạt. Đọc `request.getParameter("otpCode")` khớp chính xác ô ẩn `<input type="hidden" name="otpCode" id="otpCode">` tại `web/verifyOtp.jsp` dòng 234 và JS ghép 6 ô ở dòng 327. Ủy quyền trọn vẹn cho `AuthService.verifyOtp`: đúng mã → `otpVerified=true`, xoá `resetOtp` (`BR-05`) và redirect `/resetPassword.jsp`; sai hoặc thiếu `otpCode` → `/verifyOtp.jsp?error=invalid_otp`; quá 5 phút (`BR-04`) → xoá mã và redirect `/forgotPassword.jsp?error=expired`; sai quá 5 lần → `/forgotPassword.jsp?error=too_many_attempts`; hết phiên → `/forgotPassword.jsp?error=session_expired`. |
| `web/forgotPassword.jsp` (dòng 246, 278, 283) | Đạt. Bổ sung `<c:when test="${param.error == 'expired'}">Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới.</c:when>` khớp từng ký tự với `MSG-007` (`docs/SPEC.md` dòng 409); toàn bộ 6 mã lỗi có thể chuyển về trang này đều có nhánh hiển thị riêng. Dòng 283 đổi *"Phòng Nhân sự / IT"* thành `Vui lòng liên hệ <strong>Admin</strong> để được hỗ trợ.` (kèm comment dòng 278). |
| `web/login.jsp` (dòng 279, 284) | Đạt. Dòng 284 đổi *"Phòng Nhân sự / IT"* thành `Vui lòng liên hệ <strong>Admin</strong> để được cấp tài khoản.` (kèm comment dòng 279). Các mã lỗi hiện có giữ nguyên và khớp `MSG-002`, `MSG-003`, `MSG-004`, `MSG-008`. |
| `web/changePassword.jsp` (dòng 308–309) | Đạt. Bổ sung `<c:when test="${param.error == 'too_many_attempts'}">Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau ít phút.</c:when>` kèm ghi chú `BR-01`, khớp từng ký tự với `MSG-008` (`docs/SPEC.md` dòng 410) và đồng bộ với `AuthService.changePassword` (dòng 238–240, 250) cũng như `login.jsp` dòng 234. |
| `tools/guide/shots/general.mjs` & ảnh `web/guide/general/01, 04, 05, 06` | Đạt. Đã gỡ toàn bộ 10 dòng `// TẠM (Task 10.7)` chèn ô ẩn `otp` ở cảnh `06-dat-lai-mat-khau.png`; kịch bản đi thẳng qua `fillOtp` + `#submitBtn` (dùng `otpCode` thật). Kiểm tra thời điểm ghi file xác nhận cả 4 ảnh `01`, `04`, `05`, `06` đều được chụp lại (`00:06:29`–`00:06:41` ngày 25/11/2026); đã xem trực quan cả 4 ảnh: `01-dang-nhap.png` và `04-quen-mat-khau.png` hiển thị rõ *"Vui lòng liên hệ **Admin**..."*, `05-nhap-otp.png` và `06-dat-lai-mat-khau.png` khớp luồng thật sang màn hình đặt lại mật khẩu cho `leminhhoang` (trùng từng byte với bản `main` nên `git status` chỉ ghi nhận 2 ảnh `01` và `04` đổi nội dung). |
| `web/jsp/guide/general.jsp` (mục 1, 3, 8) | Đạt. Mục 8 (dòng 254) đã cập nhật thông báo khi bị chặn máy sang đúng `MSG-008` (*"Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau ít phút."*). Mục 1 (dòng 29, 42–44) vốn đã ghi *"liên hệ Admin"* và khớp giao diện mới; mục 3 khớp luồng và bảng lỗi trên giao diện. |

### B & C. Bảo mật, an toàn đầu vào & quy chuẩn (§3–§4)

- **An toàn đầu vào & bảo mật:** `otpCode` đi qua `AuthValidator.trimToNull` và so sánh hằng thời gian `MessageDigest.isEqual` trong `AuthService.verifyOtp` (an toàn với `null`, rỗng/blank, sai định dạng, mã mồi `decoy`). Hai nhánh `<c:when>` mới trong `forgotPassword.jsp` và `changePassword.jsp` chỉ in chuỗi tĩnh, không in trực tiếp `param.error` nên không có nguy cơ XSS. Toàn bộ form POST đều giữ nguyên `<input type="hidden" name="csrfToken" value="${csrfToken}">` được `AuthenticationFilter` kiểm tra. Đã quét `git diff main` không có mật khẩu, khóa hay secret hardcode.
- **Quy chuẩn (§3–§4):** Đúng phân tầng (`view` thuần hiển thị, `controller` ủy quyền `AuthService`); không có `javax.*`, không có placeholder/`// TODO`; không đổi chữ ký public API; `handleVerifyOtp()` dài 25 dòng (≤ 40), `PasswordResetController.java` dài 208 dòng (≤ 250). Cả 7/7 file text đã sửa hợp lệ UTF-8 strict không BOM và giữ nguyên quy ước xuống dòng của file gốc trên `main`.

### D, E & F. Phạm vi, Kiểm thử & Tiêu chí "Xong khi"

- **Nhánh & Git:** Đang ở đúng nhánh `fix/task-12.1-otp-quen-mat-khau`, tách từ `6aa12a5` (`main`); `git log main..HEAD` rỗng (chưa commit bước Code); Task 12.1 trong `task_tracker.md` vẫn giữ `[ ]`.
- **Phạm vi & Không đụng:** `git diff main -- src/java/poscs/service/AuthService.java src/java/poscs/common/EmailUtil.java web/verifyOtp.jsp` hoàn toàn rỗng — không đụng bất kỳ file nào ở dòng "Không đụng". Các file sửa thuộc Task 12.1 khớp dòng "File sửa", "File test" và quy tắc chụp lại ảnh của Giai đoạn 12 (`01-dang-nhap.png`, `04-quen-mat-khau.png`). Riêng `task_tracker.md` có sửa đổi kế hoạch ngoài phạm vi Task 12.1 (xem mục "Ghi chú ngoài phạm vi & Bước Commit" bên dưới).
- **Kiểm thử (`test/poscs/controller/PasswordResetControllerTest.java`):**
  - `git diff main -- test/` chỉ có dòng thêm (`+143 / -0`), giữ nguyên test cũ `testInstantiate()`.
  - Đủ 3 kịch bản dòng "File test" (`verifyOtp_dungMaTrongOtpCode_sangTrangDatLaiMatKhau`, `verifyOtp_saiMa_baoInvalidOtpVaTinhMotLuot`, `verifyOtp_thieuOtpCode_baoLoiKhongCoiLaDung` — cố tình đặt đúng mã ở tham số cũ `otp` và bỏ trống `otpCode` để khoá hồi quy lỗi) cùng 2 ca bổ sung kiểm luồng lỗi mới (`verifyOtp_maHetHan_veTrangQuenMatKhauVoiLoiExpired`, `changePassword_mayBiChan_chuyenMaTooManyAttemptsChoTrangDoiMatKhau`).
  - Các ca OTP chạy qua `AuthService` thật, assertion chặt (kiểm URL redirect chính xác, `SESSION_OTP_VERIFIED`, xoá `SESSION_RESET_OTP` theo `BR-05`, số lần thử sai, không `invalidate()` session khi bị chặn máy).
  - Tự chạy `ant test`: **2.437 pass / 0 fail / 0 error / 3 skipped** (`BUILD SUCCESSFUL`). Tự chạy `node --test 'test/js/**/*.test.js'`: **31 pass / 0 fail**.
- **Tiêu chí "Xong khi":**
  1. Gõ đúng mã OTP ở `verifyOtp.jsp` sang được trang đặt lại mật khẩu mà không lách gì → **Đạt** (khoá bởi unit test qua `AuthService` thật và kiểm chứng qua chụp lại `06-dat-lai-mat-khau.png` bằng `general.mjs` đã bỏ đoạn lách).
  2. `grep -n "TẠM" tools/guide/shots/general.mjs` không còn kết quả → **Đạt** (`git grep -n "TẠM" -- tools/guide/shots/general.mjs` ra rỗng, exit 1).
  3. `grep -rn "Nhân sự / IT" web` không còn kết quả → **Đạt** (`git grep -n "Nhân sự / IT" -- web` và kiểm mở rộng `Phòng Nhân sự|HR/IT` đều ra rỗng, exit 1).

## Ghi chú ngoài phạm vi & Bước Commit

- **Diff kế hoạch có sẵn trong `task_tracker.md` (ngoài phạm vi Task 12.1):** `task_tracker.md` đang có sẵn thay đổi chưa commit (`+144 / -3`, cập nhật lúc `00:16` ngày 25/11/2026 — sau thời điểm sửa code và chụp ảnh Task 12.1), gồm cập nhật Quyết định 14–15, thêm Quyết định 19, đánh `[x]` Task 10.10, thêm Giai đoạn 14 (Task 14.1 → 14.6) và dòng ghi chú ở Giai đoạn 13 (Task 12.1 vẫn giữ `[ ]`; đã quét không chứa mật khẩu/secret). Trước khi `git add task_tracker.md` ở bước `commit 12.1`, cần xác nhận với người dùng xem có gộp luôn phần kế hoạch đó vào commit Task 12.1 hay tách riêng.
- **Quan sát nhỏ (không tính là lỗi):**
  - `test/poscs/controller/PasswordResetControllerTest.java` dòng 125 ghi `// BR-05: mã quá 5 phút -> forgotPassword.jsp hiện MSG-007.` (theo `docs/SPEC.md` §6, hiệu lực tối đa 5 phút là `BR-04`, còn `BR-05` là mỗi OTP chỉ dùng một lần — cách ghi kế thừa từ `AuthService.java` dòng 329 khi xoá mã hết hạn).
  - `web/jsp/guide/general.jsp` mục 3.2 (dòng 110) mô tả thông báo hết hạn phía trình duyệt trên `verifyOtp.jsp` (`Mã OTP đã hết hạn…`), vẫn đúng với thao tác thông thường; nếu mã hết hạn phía máy chủ lúc vừa bấm Xác nhận, trang chuyển về bước 1 (`forgotPassword.jsp?error=expired`) kèm `MSG-007` (*"Mã xác thực đã hết hạn. Vui lòng yêu cầu mã mới."*).
  - `docs/Report6_HuongDanSuDung.docx` không thuộc "File sửa" của Task 12.1 nên vẫn nhúng bản cũ của ảnh `01`, `04` và dòng mục 8; sẽ được đồng bộ khi xuất lại bản Word ở Task 14.6.
- **Danh sách file cho bước `commit 12.1`** (`git add` từng file một): `src/java/poscs/controller/PasswordResetController.java`, `test/poscs/controller/PasswordResetControllerTest.java`, `web/forgotPassword.jsp`, `web/login.jsp`, `web/changePassword.jsp`, `tools/guide/shots/general.mjs`, `web/jsp/guide/general.jsp`, `web/guide/general/01-dang-nhap.png`, `web/guide/general/04-quen-mat-khau.png`, `docs/qa/Task-12.1.md`, và `task_tracker.md` (sau khi đánh `[x]` Task 12.1 và xác nhận phần diff kế hoạch với người dùng).
