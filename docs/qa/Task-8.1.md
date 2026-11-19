# QA Report - Task 8.1

**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1.961 pass / 0 fail / 3 skipped (tổng 1.964 test ở 88 lớp, `BUILD SUCCESSFUL`; 3 skipped là test tích hợp mặc định bỏ qua, cùng số với Task 7.4a)

**Phạm vi:** Task 8.1 chỉ sửa tài liệu `docs/SPEC.md` (bổ sung UC-55, BR-56, BR-57, MSG-057 → MSG-060, bảng `branch_info`). Đối chiếu với các dòng con UC, BR / MSG, File sửa, Không đụng, Lưu ý, Xong khi của Task 8.1 trong `task_tracker.md`; thay đổi thực tế lấy từ `git status` và `git diff main` trên nhánh `docs/task-8.1-spec-thong-tin-chi-nhanh`.

## Danh sách lỗi

- [x] 🟡 `docs/SPEC.md` › §5.4, đoạn "Cập nhật (UC-35)" (dòng ~221)
  - *Đã sửa (bước Fix):* thêm vế "trừ người ký bên mình (BR-56)" đúng như gợi ý: "Nháp sửa mọi ô kể cả thời hạn, trừ người ký bên mình (BR-56); **đã ký** ...". Không đụng phần "đã ký chỉ đổi được Người phụ trách" để Task 8.5 vẫn thay được nguyên câu.
  - Vi phạm: dòng "UC" của Task 8.1 ("sửa mô tả liên quan ở `UC-35`"); BR-56; rules §1 (SPEC là nguồn chân lý, chỗ mâu thuẫn buộc agent dừng hỏi).
  - Mô tả: câu "Nháp sửa mọi ô kể cả thời hạn" vẫn giữ nguyên. Trong khi đó BR-56 và gạch đầu dòng mới "Người ký bên mình (BR-56)" ngay bên dưới nói bốn ô người ký bên mình **chỉ hiển thị, không sửa tay** trên form tạo/sửa. Hai câu trong cùng §5.4 nói ngược nhau về bản Nháp. Task 8.4 (khoá 4 ô trên form Sửa) sẽ đọc đúng đoạn này. Task 8.5 có sửa câu này nhưng chỉ nửa sau (phần "đã ký chỉ đổi được Người phụ trách"), không đụng "Nháp sửa mọi ô", nên mâu thuẫn không tự hết.
  - Gợi ý sửa (chỉ thêm vế cuối, không đụng phần "đã ký" để Task 8.5 vẫn áp được):
    ```
    - **Cập nhật (UC-35):** Nháp sửa mọi ô kể cả thời hạn, trừ người ký bên mình (BR-56); **đã ký** chỉ đổi được ...
    ```

## Cần người dùng quyết định (không tính là lỗi của bước Code)

BR-57 được ghi **nguyên văn** theo dòng con của task, nên bước Fix không được tự đổi. Hai điểm dưới đây cần người dùng chốt, nên chốt **trước Task 8.2 / 8.3**:

1. **BR-57 không giới hạn số ký tự của SĐT/fax, nhưng cột trong CSDL chỉ 20 ký tự.** Task 8.2 định `branch_info.phone`, `branch_info.fax`, `contracts.signer_phone`, `contracts.signer_fax` là `varchar(20)`. BR-57 chỉ đếm chữ số (8–15) và cho phép thêm khoảng trắng, dấu chấm, gạch ngang, ngoặc đơn không giới hạn. Ví dụ `(024) 3852 1234 - 1105` có 15 chữ số (đúng BR-57) nhưng dài 22 ký tự: validator của Task 8.3 cho qua, còn MySQL (chế độ strict mặc định) báo "Data too long", người dùng chỉ thấy lỗi lưu chung chung.
   - Đề xuất: thêm vào BR-57 "tối đa 20 ký tự" cho SĐT và fax (khớp cột), bổ sung vào dòng "File test" của Task 8.3 một ca vượt 20 ký tự. Hoặc giữ BR-57 và nới cột ở Task 8.2 lên `varchar(30)`.
   - *Người dùng chốt ở bước Fix: thêm "tối đa 20 ký tự".* Đã ghi: BR-57 trong SPEC và dòng con `BR-57` của Task 8.1 ("nếu nhập thì tối đa 20 ký tự, phải có 8–15 chữ số, ..."); SPEC §5.8 và dòng MSG-059 ghi "sai định dạng hoặc quá 20 ký tự"; §11 mục 12; `task_tracker.md` thêm quyết định 12 và ca `(024) 3852 1234 - 1105` vào "File test" của Task 8.3.
2. **"Chưa khai thông tin chi nhánh" (BR-56, MSG-060) chưa có định nghĩa cụ thể.** Task 8.2 gieo sẵn dòng id = 1 có `director_position` = 'Giám đốc chi nhánh', họ tên NULL. Nếu không nói rõ, Task 8.4 phải tự đoán: "chưa khai" là họ tên trống, hay cả bốn ô trống? Tạo mới lúc đó "để trống" cả bốn ô, hay vẫn chép chức vụ gieo sẵn?
   - Đề xuất: ghi vào dòng "Lưu ý" của Task 8.4 (hoặc thêm vào BR-56): "chưa khai = họ tên GĐCN còn trống; khi đó tạo mới để trống cả bốn ô".
   - *Người dùng chốt ở bước Fix: chưa khai = họ tên GĐCN còn trống; tạo mới để trống cả bốn ô (không chép chức vụ gieo sẵn).* Đã ghi: BR-56 trong SPEC và dòng con `BR-56` của Task 8.1; SPEC §5.4 (gạch đầu dòng "Người ký bên mình"), dòng MSG-060, §11 mục 12; `task_tracker.md` quyết định 10, "Lưu ý" và "File test" của Task 8.4.

## Chi tiết kiểm tra

**D. Phạm vi**
- Đang ở nhánh `docs/task-8.1-spec-thong-tin-chi-nhanh`, đúng dòng "Nhánh". `git log main..HEAD` rỗng (chưa có commit của bước Code).
- `git diff main --stat`: chỉ `docs/SPEC.md` (+26/−6) và `task_tracker.md` (+124/−0). Diff `task_tracker.md` chỉ có dòng thêm (quyết định 9–11 và Giai đoạn 8 lúc lập kế hoạch), Task 8.1 vẫn `[ ]`.
- Không đụng code, `db/`, `README.md`, `PERMISSIONS.md`: đạt.
- Dòng "Lưu ý" (grep mã mới trước khi ghi): đã grep lại cả repo (trừ `build/`, `.git/`). `UC-55`, `BR-56`, `BR-57`, `MSG-057` → `MSG-060` chỉ xuất hiện trong `docs/SPEC.md` và `task_tracker.md`. Đã quét thêm chữ trong `docs/Project_Report_v8.docx` và `docs/Report6_HuongDanSuDung.docx`: không có. Không trùng mã.

**A. Nghiệp vụ: nội dung BR / MSG**
- So bằng script từng ký tự giữa dòng con của task và SPEC: `BR-56`, `BR-57` khớp hoàn toàn; câu trích dẫn của `MSG-057`, `MSG-058`, `MSG-059`, `MSG-060` khớp; loại thông báo ghi đúng (Toast / nội tuyến / nội tuyến trên form hợp đồng), cách ghi loại trong ngoặc theo kiểu `MSG-030` đã có.

**F. Từng mục ở dòng "File sửa"**

| Mục | Kết quả |
|---|---|
| §3.3 thêm "Cập nhật thông tin chi nhánh (UC-55)", chỉ Admin | Đạt. Bảng xếp cột Admin / Kỹ thuật / Sales, dòng mới `X / - / -`, đặt dưới "Quản lý nhân viên". |
| §4 tiêu đề "UC-01 → UC-55" + mục 4.8 | Đạt. |
| §5.4 đoạn "Tạo (UC-33)" | Đạt. Câu được viết lại thay vì thay chữ máy móc: "người ký bên mình do hệ thống tự điền (BR-56)" tách khỏi danh sách "Tùy chọn". Đúng ý task và đọc rõ hơn; chấp nhận. |
| §5.8 "Thông tin chi nhánh (Admin)" | Đạt: một bản ghi, chỉ xem/sửa, BR-57, MSG-057/058/059/015, vai khác → MSG-022. |
| §6 tiêu đề "BR-01 → BR-57" + 2 dòng | Đạt. |
| §7 "30 bảng", nhóm "Tài khoản & tổ chức (8)" thêm `branch_info` | Đạt. |
| §9 bốn dòng MSG | Đạt. |
| §11 quyết định 12 | Đạt: tóm tắt đủ mục 9 và 10 "Quyết định đã chốt" của `task_tracker.md`. |
| Dòng "UC": sửa mô tả UC-33, UC-35, UC-46, UC-54 | UC-33 đạt (đoạn Tạo). UC-46, UC-54 được phủ bởi gạch đầu dòng mới "Người ký bên mình (BR-56)" ở §5.4. **UC-35 chưa đạt**: xem lỗi 🟡 ở trên. |

**Xong khi: số đếm trong tiêu đề khớp nội dung**
- UC: bảng §4 có đủ các dòng UC-01 → UC-55, trong đó UC-40 gạch bỏ nhưng giữ số hiệu như trước. Tiêu đề "UC-01 → UC-55" khớp.
- BR: 57 dòng BR, từ BR-01 tới BR-57, không thiếu số, không trùng.
- Bảng: 8 + 5 + 4 + 7 + 3 + 3 = 30, khớp "30 bảng".
- MSG: 60 dòng, không trùng mã.

**B, C, E**
- B (an toàn đầu vào), C (phân tầng, CSRF, BCrypt...): không áp dụng, task không có code. Về nội dung đặc tả: §5.8 ghi đúng chặn quyền MSG-022 cho Sales và Kỹ thuật.
- E: task không có file test (theo dòng "File test"). Không có file nào trong `test/` bị đổi.

## Ghi chú ngoài phạm vi

- Sau khi 8.1 merge, SPEC §7 ghi 30 bảng và §11 mục 12 nhắc `contracts.signer_phone`/`signer_fax`, nhưng `db/schema.sql` hiện vẫn 29 `CREATE TABLE`, chưa có `branch_info` (thuộc Task 8.2). §7 mở đầu bằng "Nguồn chính thức là `db/schema.sql`", nên hai file lệch nhau cho tới khi Task 8.2 merge. Đúng thứ tự kế hoạch, chỉ nên làm 8.2 ngay sau 8.1.
- `docs/PROMPTS.md` đang untracked, không có trên `main` và không thuộc Task 8.1. Bước Commit 8.1 sẽ coi đây là file lạ và không `git add`. Muốn đưa file này vào repo thì commit riêng.
- `rules.md` §1 ghi "Agent không sửa `docs/SPEC.md`". Task 8.1 do người dùng chỉ định rõ là sửa SPEC, và §1 không thuộc §3 (Ranh giới cấm), nên prompt người dùng được ưu tiên. Không tính lỗi.
- Cảnh báo git "LF will be replaced by CRLF" ở `docs/SPEC.md`: file trong cây làm việc dùng LF, giống bản trên `main`; do `core.autocrlf=true` nên git chỉ báo, nội dung commit không đổi.
