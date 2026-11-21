# QA Report - Task 10.8

**Kết luận:** APPROVED - Đạt yêu cầu phạm vi & quy chuẩn dọn tài liệu (Task 10.8)
**Kết quả `ant test`:** 2.432 pass / 0 fail / 0 error / 3 skipped (92 test suites: 89 unit test suites pass 100%, 3 integration test suites trong `test/poscs/integration/` mặc định bỏ qua theo `rules.md` §0; `BUILD SUCCESSFUL`).

**Phạm vi:** Task 10.8 — Dọn tài liệu: xoá 4 file lỗi thời trong `docs/` (`kichbandemoposcs.html`, `kichbandemoposcs.pdf`, `POSCS_TaiKhoanTest.sql`, `POSERD.png`), cập nhật `docs/ARCHITECTURE.md` theo cơ cấu 3 vai mới và code đã merge tới Task 10.6, bỏ bản chép trùng ở `.gemini/rules.md`, bỏ trỏ `.gemini/rules.md` ở `GEMINI.md` và `task_tracker.md` dòng 4, đưa 3 file quy trình (`docs/PROMPTS.md`, `task_tracker_deploy.md`, `docs/HuongDan_DoiEmailGui.md`) vào git không sửa nội dung. Đối chiếu với `task_tracker.md`, `docs/SPEC.md`, `rules.md` và mã nguồn thực tế trong `src/java/poscs/`; thay đổi thực tế lấy từ `git status`, `git log main..HEAD` và `git diff main` trên nhánh `chore/task-10.8-don-tai-lieu`.

## Danh sách lỗi

Không phát hiện lỗi (**0 🔴 Critical, 0 🟡 Major, 0 🟢 Minor**).

**APPROVED - Đạt yêu cầu UC & BR.** Đã rà soát toàn bộ 11 file thay đổi (4 xoá, 4 sửa, 3 đưa vào git), đối chiếu từng tầng và danh sách lớp trong `docs/ARCHITECTURE.md` với mã nguồn thực tế (39 controller/helper, 22 service, 10 validator, 16 DAO, 28 model, 18 common), kiểm chứng `rules.md` không mất quy tắc nào so với `.gemini/rules.md` cũ, quét thông tin nhạy cảm trên cả 3 file mới đưa vào git và tự chạy đủ 4 kiểm tra "Xong khi" cùng toàn bộ `ant test` — tất cả đều đạt.

## Chi tiết kiểm tra

### A. Đối chiếu từng mục trong "File sửa" của Task 10.8

| Hạng mục | Kết quả |
|---|---|
| Xoá 4 file lỗi thời bằng `git rm` (`docs/kichbandemoposcs.html`, `docs/kichbandemoposcs.pdf`, `docs/POSCS_TaiKhoanTest.sql`, `docs/POSERD.png`) | Đạt. Cả 4 file đã được `git rm` (staged deleted) và không còn tồn tại trên đĩa; không còn tham chiếu nào ngoài `task_tracker.md` và `docs/qa/`. |
| `docs/ARCHITECTURE.md` — đổi `.gemini/rules.md` (§4) và 3 vai trò | Đạt. Dòng 3 và dòng 58 đã đổi `.gemini/rules.md` (§4) → `rules.md` (§4). Dòng 147 và dòng 241 đã đổi `3 vai trò (Admin, Sales, Kỹ thuật)` → `3 vai trò (Admin, Sales, Tổng hợp)`. |
| `docs/ARCHITECTURE.md` — rà mục 2 theo code đã merge tới Task 10.6 | Đạt. Dòng 156 đã cập nhật mô tả Contract Service: `"giấy tờ của hợp đồng (ô tích theo danh mục, link không bắt buộc), bàn giao cho phòng Tổng hợp"` (thay `"tài liệu đính kèm, bàn giao liên phòng"`). Dòng 160 đã bổ sung loại thông báo thứ 3 cho `NotificationService`: `"hợp đồng đã giao hàng quá 15 ngày chưa ký"` (khớp `BR-62` và `NotificationService.java`). Dòng 157 (`TicketService`) vốn ghi `"phân công xử lý"`, không ghi "Kỹ thuật viên" nên không sai. Đối chiếu toàn bộ mục 2 với `src/java/poscs/` xác nhận khớp 100% số lượng, tên lớp và `@WebServlet` (39 `controller`, 22 `service`, 10 `validator`, 16 `dao`, 28 `model`, 18 `common`). Không viết lại cả file, không thêm mục mới. |
| `.gemini/rules.md` | Đạt. Đã thay toàn bộ 122 dòng chép trùng bằng 5 dòng trỏ về `rules.md` ở thư mục gốc. Đã đối chiếu `git diff main:.gemini/rules.md rules.md` (rỗng) → xác nhận không thất thoát bất kỳ quy tắc nào. |
| `GEMINI.md` | Đạt. Chỉ bỏ đúng dòng 7 (`4. .gemini/rules.md: quy chuẩn chi tiết bổ sung.`), giữ nguyên 3 nguồn ưu tiên còn lại và phần `Core Tech Rules`. |
| `task_tracker.md` dòng 4 | Đạt. Dòng 4 đã đổi `.gemini/rules.md` → `rules.md`. Task 10.8 vẫn giữ `[ ]` (chưa đánh dấu hoàn thành trước bước Commit). |
| Đưa 3 file vào git không sửa nội dung (`docs/PROMPTS.md`, `task_tracker_deploy.md`, `docs/HuongDan_DoiEmailGui.md`) | Đạt. Cả 3 file đã được `git add` (staged new file), không có diff unstaged (`git diff -- <3 file>` rỗng). |

### B & C. An toàn thông tin, encoding & quy chuẩn (§3–§4)

- **Kiểm tra thông tin nhạy cảm trên 3 file mới đưa vào git (mục "Lưu ý"):** Đã đọc đầy đủ `docs/PROMPTS.md` (149 dòng), `task_tracker_deploy.md` (214 dòng), `docs/HuongDan_DoiEmailGui.md` (104 dòng) và quét regex email/mật khẩu/tên miền/IP/đường dẫn máy cá nhân:
  - Không có địa chỉ email thật, mật khẩu thật, tên miền thật hay IP thật.
  - Chỉ có các chuỗi công khai/mẫu hợp lệ: `smtp.gmail.com:587`, `https://myaccount.google.com/{security,apppasswords}`, `localhost`, dải mạng riêng Docker `172.16.0.0/12`, ví dụ định dạng `abcd efgh ijkl mnop`, placeholder `<Gmail mới>` / `<tên miền>`, mật khẩu tài khoản demo `Admin@123`/`Poscs@123` (đã công khai trong `db/schema.sql`) và ghi chú bỏ mặc định cũ `1234` của `DEPLOY.md`.
- **Kiểm tra file lạ trong `docs/` (mục "Lưu ý"):** Hiện có đúng 11 mục trong `docs/` (`qa/`, `ARCHITECTURE.md`, `HuongDan_DoiEmailGui.md`, `POSCS_IntegrationTest_Blackbox.xlsx`, `POSCS_SystemTest.xlsx`, `POSCS_UnitTest_Function.xlsx`, `POSCS_UserAcceptanceTest.xlsx`, `Project_Report_v8.docx`, `PROMPTS.md`, `Report6_HuongDanSuDung.docx`, `SPEC.md`). Không có file lạ nào khác.
- **Encoding UTF-8:** Cả 7 file văn bản còn lại/sửa/thêm trong phạm vi đều hợp lệ UTF-8 strict (không BOM), giữ nguyên tiếng Việt đầy đủ dấu.

### D, E & F. Phạm vi, Kiểm thử & Tiêu chí "Xong khi"

- **Nhánh & Git:** Đang ở đúng nhánh `chore/task-10.8-don-tai-lieu`, tách từ `c319d0e` (trùng `main`, sau khi PR #196 Task 10.7 đã merge); `git log main..HEAD` rỗng (chưa có commit bước Code).
- **Phạm vi & Không đụng:**
  - `git diff main --name-status` có đúng 11 file, 100% thuộc mục "File sửa" của Task 10.8.
  - Không chạm bất kỳ file nào trong mục "Không đụng": `docs/qa/*.md`, 4 file `docs/POSCS_*Test.xlsx`, `tools/testdoc/`, `docs/Project_Report_v8.docx`, `docs/Report6_HuongDanSuDung.docx`, `docs/SPEC.md`, `PERMISSIONS.md`, `README.md`, `DEPLOY.md`, `tools/guide/`, `web/guide/`, `web/jsp/guide/`, `src/`, `test/`, `web/`, `db/`.
- **Kiểm thử:** `git diff main -- test/` rỗng (không sửa hay xoá test cũ); tự chạy `ant test` → **2.432 pass / 0 fail / 0 error / 3 skipped** (`BUILD SUCCESSFUL`).
- **Tiêu chí "Xong khi":**
  1. `git grep -n -E "kichbandemoposcs|POSCS_TaiKhoanTest|POSERD" -- . ':!task_tracker.md' ':!docs/qa'` → **rỗng** (Đạt).
  2. `git grep -n "\.gemini/rules" -- . ':!task_tracker.md' ':!docs/qa'` → **rỗng** (Đạt).
  3. `grep -n "Kỹ thuật" docs/ARCHITECTURE.md` → **rỗng** (Đạt; tìm không phân biệt hoa thường chỉ còn đúng 1 lần ở dòng 157 `"phiếu hỗ trợ kỹ thuật"` viết thường).
  4. `git ls-files docs/PROMPTS.md task_tracker_deploy.md docs/HuongDan_DoiEmailGui.md` → **ra đủ 3 file** (Đạt).

## Ghi chú ngoài phạm vi & Bước Commit

- **Phần diff kế hoạch có sẵn trong `task_tracker.md`:** Ngoài dòng 4 (`.gemini/rules.md` → `rules.md`) do Task 10.8 sửa, `git diff main -- task_tracker.md` đang chứa thêm phần cập nhật kế hoạch ngày 21/11/2026 từ trước bước commit Task 10.7 (đã ghi nhận tại `docs/qa/Task-10.7.md` dòng 99): dòng 9 trỏ sang `task_tracker_deploy.md`, Quyết định 17–18, ghi chú đầu Giai đoạn 9–10 và nội dung Task 10.8, 10.9, Giai đoạn 12. Khi chạy `commit 10.8` và đánh `[x]` cho Task 10.8, việc commit `task_tracker.md` sẽ đồng thời đưa các mục kế hoạch này (bao gồm định nghĩa của chính Task 10.8 và liên kết sang `task_tracker_deploy.md`) lên `main`.
- **Quan sát nhẹ về `docs/ARCHITECTURE.md` (không tính là lỗi):** Dòng 156 ghi tóm lược `"kỳ thanh toán"` (chưa liệt kê chi tiết nộp hồ sơ và số thực thu như đã mở ngoặc cho phần giấy tờ) và dòng 242 (§3) vẫn ghi `"tài liệu hợp đồng"` (khớp tên bảng `contract_documents` và `rules.md` §4 dòng 97). Cả hai câu đều không sai so với code nên giữ nguyên là phù hợp chỉ dẫn *"chỉ sửa câu sai so với code, không viết lại cả file"*.
- **Bước Commit:** Nhớ `git add` đủ 4 file sửa (`.gemini/rules.md`, `GEMINI.md`, `docs/ARCHITECTURE.md`, `task_tracker.md` sau khi đánh `[x]`), `docs/qa/Task-10.8.md`, giữ nguyên 4 file đã `git rm` và 3 file mới đã `git add`.
