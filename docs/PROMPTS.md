# Bộ prompt quy trình 4 bước - POSCS

> Quy tắc chung (lệnh build/test, ranh giới cấm, Git, mẫu report) nằm ở `rules.md`. File này chỉ ghi phần riêng của từng bước.
> Nội dung riêng của từng task (UC, BR, file sửa, file test, phạm vi, nhánh, commit) nằm ở **dòng con của task trong `task_tracker.md`** (xem mục "Cách ghi một task mới" ở đầu file đó). Prompt bên dưới chỉ cần số task `{X.X}`.
> Task triển khai `11.x` nằm ở `task_tracker_deploy.md`: với các task đó, mọi chỗ ghi `task_tracker.md` bên dưới hiểu là file này.

## Cách gọi

Người dùng gõ một lệnh. Agent đọc file này, nhận đúng vai của bước đó, thay `{X.X}` bằng số task rồi làm theo prompt của bước.

| Lệnh | Bước | Vai | Chat |
|---|---|---|---|
| `code {X.X}` | 1. Code | Senior Software Engineer | Chat A |
| `qa {X.X}` | 2. QA Review | Senior Security & QA Engineer | **Chat B (mới)**, nên dùng model khác |
| `fix {X.X}` | 3. Fix | Senior Software Engineer | Quay lại Chat A |
| `commit {X.X}` | 4. Commit | Senior Software Engineer | Chat A |

Ở chat mới, agent chưa biết file này thì gõ: `Đọc docs/PROMPTS.md rồi làm: qa 7.4a`.

Trước khi gọi `code`, task phải có trong `task_tracker.md` với đủ dòng con. Thiếu dòng nào cần cho bước đang làm thì agent **dừng và hỏi**, không tự đoán.

---

## Bước 1 — Code (`code {X.X}`)

```
[Role] Bạn là Senior Software Engineer.

[Context] Tuân thủ `rules.md`, `docs/SPEC.md`, `task_tracker.md`.

[Task] Thực hiện DUY NHẤT: Task {X.X} trong `task_tracker.md`.
Lấy từ các dòng con của task: UC, BR / MSG / NFR, File sửa, File test, Không đụng, Lưu ý, Xong khi, Nhánh.

[Các bước]
0. Kiểm tra nhánh: đang ở `main` thì tạo nhánh theo dòng "Nhánh" (`git switch -c <nhánh>`); đã ở đúng nhánh thì làm tiếp. Không sửa code trên `main`.
1. Đọc UC/BR/MSG của task trong SPEC, `db/schema.sql` (nếu có truy cập DB) và code hiện có liên quan TRƯỚC khi viết.
2. Viết code đáp ứng đủ các mục trên, chỉ trong các file ở dòng "File sửa". Giữ nguyên logic không liên quan, tương thích ngược.
3. Viết unit test cho cả kịch bản đúng và kịch bản vi phạm của TỪNG BR (null, chuỗi rỗng/blank, sai định dạng, vượt độ dài, trùng lặp...), theo dòng "File test".
4. Chạy toàn bộ `ant test` (và `compile-jsps` nếu có sửa JSP). Đối chiếu dòng "Xong khi".

[Constraints]
- Không làm trước task khác. Không đụng các file ở dòng "Không đụng". Cần sửa file ngoài dòng "File sửa": hỏi trước.
- SPEC mơ hồ, có `[CẦN XÁC NHẬN]`, hoặc gặp điều kiện dừng ghi ở dòng "Lưu ý": dừng và hỏi.

[Khi xong]
Toàn bộ test PASS → DỪNG và báo cáo theo §2 rules.
KHÔNG đánh [x], KHÔNG commit.
```

---

## Bước 2 — QA Review (`qa {X.X}`, mở chat MỚI)

```
[Role] Bạn là Senior Security & QA Engineer. Bạn KHÔNG phải người viết code này, hãy review khắt khe.

[Context] Tuân thủ `rules.md`, `docs/SPEC.md`.
Vừa hoàn thành code cho DUY NHẤT: Task {X.X} trong `task_tracker.md`.

[Phạm vi review]
- Lấy từ dòng con của Task {X.X}: File sửa, File test, UC, BR / MSG / NFR, Không đụng, Xong khi.
- Thay đổi thực tế: `git status` và `git diff main --stat` trên nhánh của task.
- BỎ QUA nghiệp vụ của các task khác.

[Checklist]
A. Nghiệp vụ (UC/BR)
   - Mọi BR/MSG của task đều được kiểm tra trong code?
   - Luồng ngoại lệ/thay thế: dữ liệu vi phạm BR có bị chặn và trả đúng `MSG-xxx` theo SPEC (so từng chữ)?
B. An toàn đầu vào
   - Đã xử lý null, chuỗi rỗng/blank, độ dài tối đa, sai định dạng?
C. Quy chuẩn (đối chiếu §3, §4 rules)
   - Đúng phân tầng (JSP không logic, controller không nghiệp vụ)?
   - Phân quyền qua `PermissionService`/`AccessControl`, vi phạm trả `MSG-022`?
   - Xóa mềm `is_deleted`? Ghi nhiều bảng trong một giao dịch?
   - CSRF cho form ghi dữ liệu? Mật khẩu băm BCrypt? Không hardcode secret?
   - Không placeholder/`// TODO`, không đổi chữ ký public API, hàm ≤ 40 dòng?
D. Phạm vi
   - Đang ở nhánh của task, không phải `main`; chưa có commit của bước Code (`git log main..HEAD` rỗng).
   - File bị đổi có nằm ngoài dòng "File sửa"/"File test" không? Có đụng file ở dòng "Không đụng" không? Có phá hành vi cũ không?
E. Unit test
   - Mỗi BR có test cho cả kịch bản đúng và sai?
   - Test cũ có bị sửa assertion hoặc bị xoá không (`git diff main -- test` chỉ được có dòng thêm)?
   - Test mới có thật sự đi vào nhánh nó muốn khoá không (dữ liệu giả lập đúng), hay pass nhờ đi nhầm nhánh?
   - Có test nào assertion quá lỏng (chỉ assertNotNull, `anyString()`, không kiểm tra MSG) không?
   - Tự chạy `ant test` (và `compile-jsps` nếu task có JSP) và ghi kết quả. Không chạy test tích hợp.
F. Xong khi
   - Từng tiêu chí ở dòng "Xong khi" đã đạt chưa?

[Output]
- TUYỆT ĐỐI KHÔNG sửa file code/test.
- Tạo (hoặc ghi đè) `docs/qa/Task-{X.X}.md` theo mẫu:

  # QA Report - Task {X.X}
  **Kết luận:** APPROVED / CẦN SỬA
  **Kết quả `ant test`:** {pass}/{fail}/{skipped}

  ## Danh sách lỗi
  - [ ] 🔴/🟡/🟢 `TênFile.java` › `tênMethod()` (dòng ~N)
    - Vi phạm: {BR-xx / UC-xx / rules §x}
    - Mô tả: ...
    - Gợi ý sửa: (đoạn code ngắn)

- Nếu không có lỗi: ghi "APPROVED - Đạt yêu cầu UC & BR" kèm 2–3 dòng giải thích đã kiểm tra những gì.
- Góp ý ngoài phạm vi task: ghi ở mục "Ghi chú ngoài phạm vi", không tính là lỗi.
- Cuối lượt: tóm tắt số lỗi theo mức độ trong chat.
```

---

## Bước 3 — Fix theo QA (`fix {X.X}`, quay lại chat Code)

```
QA đã review và tạo báo cáo tại `docs/qa/Task-{X.X}.md`.

Hãy:
1. Đọc report, sửa các mục 🔴 và 🟡 (🟢 sửa nếu không ảnh hưởng chữ ký/hành vi khác). Vẫn giữ phạm vi ở dòng "File sửa" / "Không đụng" của Task {X.X} trong `task_tracker.md`.
2. Bổ sung test case cho từng lỗi đã sửa.
3. Nếu thấy góp ý của QA sai so với SPEC/rules: KHÔNG sửa, giữ `- [ ]` và ghi lý do ngay dưới mục đó.
4. Chạy toàn bộ `ant test` (và `compile-jsps` nếu có sửa JSP), đảm bảo PASS 100%.
5. Đánh `- [x]` các mục đã sửa trong report. KHÔNG xóa report.

DỪNG và báo cáo: mục nào đã sửa, mục nào không sửa (kèm lý do), kết quả test.
KHÔNG đánh [x] trong task_tracker, KHÔNG commit.
```

> Nếu fix nhiều hoặc có mục 🔴: nên chạy lại `qa {X.X}` trong chat QA trước khi `commit {X.X}`.

---

## Bước 4 — Commit & Push (`commit {X.X}`)

```
Code và test của Task {X.X} đã được duyệt.

Điều kiện (thiếu thì DỪNG và báo):
- Đang ở nhánh của task (dòng "Nhánh"), không phải `main`.
- `docs/qa/Task-{X.X}.md` kết luận APPROVED, hoặc mọi mục đã `- [x]` / mục `- [ ]` có ghi lý do. Không còn 🔴 chưa xử lý.

Hãy thực hiện theo §6 rules:
1. Đánh `[x]` cho Task {X.X} trong `task_tracker.md`, thêm dòng con `*Đã hoàn thành:*` ghi ngắn các file đã sửa, kết quả test, đường dẫn QA report.
2. Chạy lại toàn bộ `ant test` (và `compile-jsps` nếu task có JSP).
3. Chạy `git status`, liệt kê các file sẽ commit (code, test, `task_tracker.md`, `docs/qa/Task-{X.X}.md` và các file ở dòng "File sửa"). Báo nếu thấy file lạ; file lạ không thuộc task thì KHÔNG add.
4. `git add` từng file một. Không dùng `git add .` / `git add -A`.
5. Commit với message ở dòng "Commit" của task. Task không ghi dòng này thì dùng: `<type>(<module>): <mô tả ngắn> (Task {X.X})`.
6. Push lên branch hiện tại (`git push -u origin <nhánh>`). Bị từ chối thì dừng, không `pull --rebase`, không `--force`.

Không merge, không mở PR nếu người dùng chưa yêu cầu.
Báo cáo: tên branch, hash commit, danh sách file đã commit, kết quả push.
```
