# QA Report - Task 10.9

**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 2.435 pass / 0 fail / 0 error / 3 skipped (92 test suites: 89 unit test suites pass 100%, 3 integration test suites trong `test/poscs/integration/` mặc định bỏ qua theo `rules.md` §0; `BUILD SUCCESSFUL`). Ngoài ra, đã chạy kiểm chứng lệnh JS trong README: `node --test 'test/js/**/*.test.js'` → **31 pass / 0 fail**.

**Phạm vi:** Task 10.9 — Viết lại `README.md` gọn bằng tiếng Việt (9 mục theo Quyết định 18, không ghi tài khoản/mật khẩu mẫu, bỏ danh sách tên lớp và số file từng gói) và chuyển quy ước viết log sang `rules.md` §4. Đối chiếu với `task_tracker.md`, `docs/SPEC.md`, `rules.md` và mã nguồn thực tế (`DBContext`, `EmailUtil`, `LogFiles`, `Logs`, `SystemLogController`, `GuideController`, `AuthenticationFilter`, `CsrfUtil`, `LoginController`, `AuthService`, `DEPLOY.md` §2, `.github/workflows/tests.yml`); thay đổi thực tế lấy từ `git status`, `git log main..HEAD` và `git diff main` trên nhánh `docs/task-10.9-readme-tieng-viet`.

> **Tóm tắt phạm vi chính:** Hai file thuộc mục "File sửa" của Task 10.9 (`README.md` và `rules.md`) đều đạt yêu cầu nội dung, độ chính xác đối chiếu code và tiêu chí "Xong khi" (chỉ có 1 mục 🟢 Minor). Tuy nhiên, nhánh làm việc hiện đang có sửa đổi ngoài phạm vi trong `task_tracker.md` chứa **mật khẩu MySQL `root` dạng bản rõ (`1234`)** sẽ bị đưa vào git ở bước `commit 10.9` nếu không gỡ trước.

## Danh sách lỗi

- [x] 🔴 `task_tracker.md` › Task 10.10 (dòng 760–763)
  - Đã sửa (người dùng đồng ý 24/11/2026, chọn "chỉ gỡ cụm mật khẩu"): dòng 760 đổi `, mật khẩu root là 1234` thành `(nhập mật khẩu root của máy khi mysql hỏi)`; giữ phần đường dẫn đầy đủ tới `mysql.exe`. `git diff main -- task_tracker.md | grep 1234` ra rỗng.
  - Vi phạm: `rules.md` §0 ("Agent KHÔNG hỏi, ghi hay lưu mật khẩu DB"), `rules.md` §3 Về bảo mật ("Không hardcode mật khẩu, khóa, secret"), `docs/PROMPTS.md` Checklist C & D (file bị đổi ngoài dòng "File sửa" của Task 10.9).
  - Mô tả: `git diff main --stat` trên nhánh `docs/task-10.9-readme-tieng-viet` đang có thay đổi chưa commit ở `task_tracker.md` (dòng 760–763, thuộc Task 10.10, mtime `21:43:22` — trước `rules.md` `21:47:15` và `README.md` `21:47:34`). Cụ thể, dòng 760 thêm đoạn `, mật khẩu root là 1234` vào một file được theo dõi bởi Git. Khi chạy bước `commit 10.9` (bắt buộc sửa và `git add task_tracker.md` để đánh `[x]`), mật khẩu DB cục bộ này sẽ bị commit và đẩy lên repo, đi ngược lại Task 7.2a (đã dọn mật khẩu mặc định `"1234"` khỏi repo).
  - Gợi ý sửa: Do `task_tracker.md` nằm ngoài "File sửa" của Task 10.9, theo `rules.md` §3 cần báo trước và xin xác nhận của người dùng để **gỡ cụm `, mật khẩu root là 1234`** ở dòng 760 (có thể giữ phần đường dẫn đầy đủ tới `mysql.exe` cho Task 10.10 nếu người dùng muốn giữ ghi chú này, hoặc tách/hoàn nguyên toàn bộ diff Task 10.10 khỏi nhánh Task 10.9 trước khi commit). Ví dụ dòng 760 sau khi gỡ mật khẩu:
    ```markdown
    - File sửa: không có. **Người dùng tự chạy** trên máy, lúc không dùng app; agent không chạy gì lên `poscs_db` (rules §3). Ba bước, chạy trong Git Bash ở thư mục repo (nhập mật khẩu `root` của máy khi `mysql` hỏi). Máy không có `mysql` trên PATH (PATH chỉ có MySQL Shell 8.0, kiểm 21/11/2026), nên gõ `mysql` trần sẽ báo "command not found"; ba lệnh dưới gọi bằng đường dẫn đầy đủ của MySQL Server 9.3:
    ```

- [x] 🟢 `README.md` › Mục `2. Công nghệ` (dòng 15)
  - Đã sửa: dòng Node thành "Node 22 trở lên: chạy test JavaScript và kịch bản chụp ảnh hướng dẫn (`tools/guide/`); app chạy không cần Node". Kèm theo ghi chú tuỳ chọn ở cuối report: gộp ba chỗ (hai câu mục 1, câu `dist/POSCS.war` lên đoạn Bước 4, hai gạch đầu dòng cuối mục 7), README còn đúng 100 dòng, không bỏ thông tin nào.
  - Vi phạm: Độ chính xác mô tả công cụ trong repo.
  - Mô tả: Dòng 15 ghi `- Node 22 trở lên: chỉ dùng để chạy test JavaScript`, trong khi repo còn dùng Node 22+ cho kịch bản chụp ảnh hướng dẫn `tools/guide/capture.mjs` (đầu file ghi `CÁCH CHẠY (Node 22+, KHÔNG cần npm)` và mục 3 của README dòng 30 cũng liệt kê `tools/`).
  - Gợi ý sửa: Bỏ từ `chỉ` hoặc nêu rõ ngữ cảnh không cần để chạy app, ví dụ:
    ```markdown
    - Node 22 trở lên: chỉ cần khi chạy test JavaScript và kịch bản chụp ảnh hướng dẫn (`tools/guide/`)
    ```

## Chi tiết kiểm tra

### A. Đối chiếu từng mục trong "File sửa" của Task 10.9

| Hạng mục | Kết quả |
|---|---|
| `README.md` — tiếng Việt, ~100 dòng trở xuống, đủ 9 mục đúng thứ tự | Đạt (104 dòng, giảm từ 262 dòng tiếng Anh; đủ đúng 9 mục theo thứ tự: `1. POSCS là gì`, `2. Công nghệ`, `3. Cấu trúc thư mục`, `4. Chạy trên máy`, `5. Đổi cấu trúc CSDL`, `6. Kiểm thử`, `7. Log`, `8. Bảo mật`, `9. Tài liệu khác`). |
| Mục 1 (`POSCS là gì`) | Đạt. 2 câu, đúng tên đơn vị (`Chi nhánh Công ty CP Thiết bị Bưu điện POSTEF`), đủ 3 vai (`Admin`, `Sales`, `Tổng hợp`) và 3 phòng (`Ban giám đốc`, `Phòng kinh doanh`, `Phòng tổng hợp`) khớp `docs/SPEC.md` §1 & §3.1. |
| Mục 2 (`Công nghệ`) | Đạt (1 🟢 Minor ở dòng 15). Đủ Java 21 (`nbproject/project.properties`), Jakarta EE 11 Servlet + JSP (JSTL), Apache Tomcat 11, MySQL 8+ (`utf8mb4`), Apache Ant (`build.xml`), Node 22+. |
| Mục 3 (`Cấu trúc thư mục`) | Đạt. Đúng 6 gói trong `src/java/poscs/` (`controller`, `service`, `validator`, `dao`, `model`, `common`) và 5 thư mục (`web/`, `db/`, `test/`, `tools/`, `docs/`), kiểm tra thực tế khớp cây thư mục; không đếm file, không liệt kê tên lớp; trỏ tới `docs/ARCHITECTURE.md`. |
| Mục 4 (`Chạy trên máy`) | Đạt. Đủ 4 bước, khớp code: (1) 2 lệnh `mysql` chuẩn `utf8mb4` + ghi chú Git Bash; (2) `DB_URL` (`jdbc:mysql://localhost:3306/poscs_db`), `DB_USER` (`root`), `DB_PASSWORD` (bắt buộc, không mặc định), ưu tiên `-D` trước env — khớp `poscs.dao.DBContext`; (3) `MAIL_USERNAME`, `MAIL_PASSWORD` (biến môi trường) không đặt thì in OTP và mật khẩu tạm ra log — khớp `poscs.common.EmailUtil`; (4) `ant dist` với 2 tham số `-D` trỏ `lib-build/jakarta.servlet-api-6.1.jar` và `lib-build/org-netbeans-modules-java-j2seproject-copylibstask.jar` (đã xác nhận tồn tại 2 jar và chạy thử `ant` với 2 tham số `-D` này cho `compile` và `test-single` đều pass), xuất `dist/POSCS.war`, trỏ `DEPLOY.md` §2. |
| Mục 5 (`Đổi cấu trúc CSDL`) | Đạt. Đúng 1 câu, hướng dẫn thêm file vào `db/migrations/` và trỏ tới `db/migrations/README.md`. |
| Mục 6 (`Kiểm thử`) | Đạt. Đủ 3 lệnh (`ant test`, lệnh test tích hợp `-Dtest-sys-prop.DB_*` với CSDL `poscs_it` và placeholder `matkhau_mysql`, lệnh `node --test 'test/js/**/*.test.js'` đã chạy thực tế pass 31/31); nêu rõ quy tắc `_it` tự bỏ qua và xoá sạch mỗi lần chạy (khớp `IntegrationDb.java`), cùng ghi chú thêm 2 tham số `-D` ngoài NetBeans. |
| Mục 7 (`Log`) | Đạt. Nêu `logs/catalina.*`, màn hình Nhật ký hệ thống (`/systemLog`, chỉ Admin — khớp `SystemLogController.java`), `LOG_DIR` (env hoặc `-D` — khớp `LogFiles.java`). Đã lược bỏ chi tiết 512 KB, đuôi `.log`/`.out`/`.txt` đúng yêu cầu. |
| Mục 8 (`Bảo mật`) | Đạt. 5 gạch đầu dòng ngắn, không kèm tên lớp; đối chiếu từng ý đều khớp code: CSRF cho mọi POST kể cả đăng nhập (`AuthenticationFilter.java` dòng 141–162, `CsrfUtil.java`), tạo phiên mới sau đăng nhập (`LoginController.java` dòng 43–47), đăng nhập & quên mật khẩu không lộ username cả thông báo lẫn thời gian (`AuthService.java` dòng 159–167, 278–291, 439–454), OTP sai 5 lần huỷ mã & gửi lại cách tối thiểu 30 giây (`AuthService.java` dòng 55–57, 300–303, 335–352), phân quyền server trỏ `PERMISSIONS.md`. |
| Mục 9 (`Tài liệu khác`) | Đạt. Đủ 7 mục link theo đúng danh sách (`docs/SPEC.md`, `PERMISSIONS.md`, `docs/ARCHITECTURE.md`, `DEPLOY.md`, `rules.md`, `task_tracker.md`, `/guide` + `docs/Report6_HuongDanSuDung.docx`). |
| Các mục yêu cầu lược bỏ | Đạt. Không còn danh sách tên lớp, số file từng gói, giải thích `test-sys-prop`/`vm`/2 bài học JS, chi tiết 512 KB/đuôi file log, hay mục riêng "Roles and permissions". |
| `rules.md` §4 (dòng 99–103) | Đạt. Bổ sung đủ quy ước viết log chuyển từ README cũ: SLF4J (`LoggerFactory.getLogger(...)`), cấm `printStackTrace()`; DAO `LOG.error` kèm thao tác, tham số khoá, exception; Controller `LOG.warn` ở mọi nhánh redirect `?error=..._failed` kèm `Logs.actor(request)` (`username#userId`) và khoá bản ghi; chữ không dấu; cấm log mật khẩu/chuỗi băm/OTP/mật khẩu tạm (ngoại lệ duy nhất: chế độ DEV của `EmailUtil` khi chưa cấu hình SMTP). |

### B, C & D. Bảo mật, Encoding & Phạm vi

- **Encoding UTF-8:** `README.md` (104 dòng, 5.504 byte) và `rules.md` (127 dòng, 11.147 byte) đều hợp lệ UTF-8 strict không BOM, line ending LF khớp bản trên `main`.
- **Quét tài khoản/mật khẩu mẫu trong `README.md`:** Quét mở rộng toàn bộ 10 tài khoản V46 và mật khẩu mẫu (`Poscs@123`, `Admin@123`, `tranquocbao`, `leminhhoang`, `phamthutrang`, `doducanh`, `vuthihang`, `hoangthimai`, `buivannam`, `ngothilan`, `dangvantung`, `1234`) → **rỗng**.
- **Nhánh & Git:** Đang ở đúng nhánh `docs/task-10.9-readme-tieng-viet`, tách từ `127672a` (trùng `main`, sau khi PR #197 Task 10.8 đã merge); `git log main..HEAD` rỗng.
- **Mục "Không đụng":** Không chạm bất kỳ file nào trong danh sách cấm (`DEPLOY.md`, `PERMISSIONS.md`, `db/migrations/README.md`, `tools/testdoc/README.md`, `docs/SPEC.md`, `src/`, `web/`, `test/`, `db/`).
- **Ngoài phạm vi "File sửa":** Phát hiện `task_tracker.md` bị sửa ở dòng 760–763 (Task 10.10) có chứa mật khẩu `1234` → đã ghi nhận mục 🔴 Critical ở trên.

### E & F. Kiểm thử & Tiêu chí "Xong khi"

- **Kiểm thử:** `git diff main -- test/` rỗng (không sửa hay xoá test cũ). Tự chạy `ant test` → **2.435 pass / 0 fail / 0 error / 3 skipped** (`BUILD SUCCESSFUL`). Tự chạy `node --test 'test/js/**/*.test.js'` → **31 pass / 0 fail**.
- **Tiêu chí "Xong khi":**
  1. `README.md` viết bằng tiếng Việt, khoảng 100 dòng trở xuống, đủ 9 mục theo đúng thứ tự → **Đạt** (104 dòng, xấp xỉ mốc ~100 dòng; nếu muốn gọn hẳn ≤ 100 dòng có thể rút nhẹ 4 dòng ở bước Fix).
  2. `grep -n -E "Poscs@123|Admin@123|leminhhoang|hoangthimai" README.md` ra rỗng → **Đạt** (`EXIT=1`, rỗng).
  3. Không còn số file hay danh sách tên lớp của từng gói → **Đạt** (quét regex tên lớp và số file trong `README.md` ra rỗng).
  4. Mọi link trong `README.md` trỏ tới file có thật → **Đạt** (12/12 link Markdown trỏ tới 8 file đều tồn tại trên đĩa; `/systemLog` và `/guide` đều có servlet tương ứng).
  5. `rules.md` §4 có quy ước viết log → **Đạt** (dòng 99–103).

## Ghi chú ngoài phạm vi & Bước Commit

- **Gọn số dòng `README.md` (tuỳ chọn):** Hiện `README.md` dài 104 dòng (đạt chữ "khoảng 100 dòng trở xuống"). Nếu muốn chặt chẽ `≤ 100` dòng khi quay lại bước Fix, có thể gộp dòng 61 (`Lệnh tạo ra dist/POSCS.war...`) lên cùng đoạn với dòng 53 hoặc rút ngắn vài dòng trống mà không mất thông tin nào.
- **Lưu ý bước Commit (`commit 10.9`):** Sau khi người dùng chốt cách xử lý mục 🔴 ở `task_tracker.md` (gỡ mật khẩu `1234` ở dòng 760) và hoàn tất bước `fix 10.9`, nhớ kiểm lại `git diff main -- task_tracker.md` trước khi `git add` để bảo đảm chỉ đưa lên những gì đã được người dùng đồng ý và không có mật khẩu thật trong diff.
