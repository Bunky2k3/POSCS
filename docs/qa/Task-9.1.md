# QA Report - Task 9.1

**Kết luận:** ĐẠT
**Kết quả `ant test`:** 2.068 pass / 0 fail / 0 error / 3 skipped (tổng 2.071 test ở 92 lớp, `BUILD SUCCESSFUL`; 3 skipped là test tích hợp mặc định bỏ qua, cùng số với Task 8.4)

**Phạm vi:** Task 9.1 chỉ sửa tài liệu `docs/SPEC.md` theo cơ cấu chi nhánh mới (3 vai trò Admin / Sales / Tổng hợp, 3 phòng ban Ban giám đốc / Kinh doanh / Tổng hợp). Đối chiếu với các dòng con UC, BR / MSG / NFR, File sửa, Không đụng, Lưu ý, Xong khi của Task 9.1 trong `task_tracker.md`; thay đổi thực tế lấy từ `git status` và `git diff main` trên nhánh `docs/task-9.1-spec-co-cau-chi-nhanh`.

## Danh sách lỗi

Không phát hiện lỗi (0 🔴 Critical, 0 🟡 Major, 0 🟢 Minor).

## Chi tiết kiểm tra

**D. Phạm vi**
- Đang ở nhánh `docs/task-9.1-spec-co-cau-chi-nhanh`, đúng dòng "Nhánh". `git log main..HEAD` rỗng (chưa có commit của bước Code).
- `git diff main --stat`: chỉ `docs/SPEC.md` (+48/−44) và `task_tracker.md` (+253/−11). Diff `task_tracker.md` là phần bổ sung kế hoạch (quyết định 13–15, Giai đoạn 9–10 và chỉnh nhỏ mô tả Task 8.5), Task 9.1 vẫn `[ ]`.
- Không đụng code, `db/`, `PERMISSIONS.md`: đạt.

**A & F. Đối chiếu từng mục ở dòng "UC", "BR / MSG / NFR" và "File sửa"**

| Mục | Kết quả |
|---|---|
| §3.1 Ba tác nhân + Phòng ban + khung "Đã chốt" | Đạt. Đủ 3 vai Admin / Sales / Tổng hợp (Sales thêm quản lý sản phẩm & tạo/xử lý phiếu hỗ trợ; Tổng hợp xem KH/HĐ/SP, không vào phiếu hỗ trợ, xác nhận bàn giao UC-49, ghi nhận kỳ đã thu UC-53 không lập/xoá kỳ); liệt kê 3 phòng Ban giám đốc / Kinh doanh / Tổng hợp; khung "Đã chốt" đổi sang 3 vai mới, ghi ngày đổi 19/11/2026. |
| §3.3 Ma trận quyền theo màn hình & Ghi chú | Đạt. Header `Admin \| Sales \| Tổng hợp`; Sản phẩm (Tạo/Sửa/Xoá) chuyển sang Sales; tách riêng dòng "Lập / xoá kỳ thanh toán" (`X / X (*) / -`) và "Ghi nhận kỳ thanh toán **đã thu** (UC-53)" (`X / X (*) / X`); toàn bộ nhóm Phiếu hỗ trợ là `X / X / -`; ghi chú UC-49 (phòng Tổng hợp) và MSG-022 cập nhật nhất quán. |
| §4.1, §4.2, §4.6 (các UC chung) | Đạt. Đổi tác nhân xem/chung từ `Kỹ thuật` sang `Tổng hợp` (`Admin, Sales, Tổng hợp`). |
| §4.3 Sản phẩm (`UC-12`, `UC-13`, `UC-18` → `UC-20`) | Đạt. `UC-12`, `UC-13`: `Admin, Sales, Tổng hợp`; `UC-18` → `UC-20`: `Admin, Sales`. |
| §4.4 Hợp đồng (`UC-10`, `UC-11`, `UC-41`, `UC-45`, `UC-48`, `UC-49`, `UC-53`) | Đạt. `UC-10`, `UC-11`, `UC-41`, `UC-45`: `Admin, Sales, Tổng hợp`; `UC-48`, `UC-49` ghi rõ phòng nhận duy nhất là phòng Tổng hợp; `UC-53` bổ sung `Tổng hợp: chỉ ghi nhận đã thu (không lập, không xoá kỳ)`. |
| §4.5 Phiếu hỗ trợ kỹ thuật (`UC-16`, `UC-17`, `UC-21` → `UC-23`, `UC-42`) | Đạt. Toàn bộ là `Admin, Sales`; bỏ vai Kỹ thuật và ràng buộc "chỉ phiếu được giao" ở `UC-22`. |
| §5.4 Hợp đồng (Bàn giao) | Đạt. Thêm mục Bàn giao (UC-48, UC-49): phòng Tổng hợp là phòng nhận duy nhất (gộp Kế toán và Dự án cũ), một hợp đồng không có hai chặng cùng đang mở ở một phòng. |
| §5.5 Phiếu hỗ trợ kỹ thuật | Đạt. Đổi "Kỹ thuật viên" thành "Người xử lý (Sales)" ở tạo phiếu (BR-39), danh sách (UC-16) và chi tiết (UC-17, BR-46); mặc định mở lần đầu là **"Tất cả"** (khớp hành vi hiện tại của Sales trong `TicketService.resolveAssigneeFilter`). |
| §5.7 Thông báo & §5.8 Thông tin chi nhánh | Đạt. §5.7 mục 2 đổi "kỹ thuật viên được phân công" → "người xử lý được phân công (Sales)"; §5.8 đổi "Sales, Kỹ thuật truy cập → MSG-022" → "Sales, Tổng hợp truy cập → MSG-022". |
| §6 Quy tắc nghiệp vụ (`BR-39`, `BR-46`) | Đạt. `BR-39` đổi "KTV được chỉ định" → "Người xử lý (chọn trong các Sales đang hoạt động)"; `BR-46` đổi "KTV xử lý" → "Người xử lý". |
| §7 Mô hình dữ liệu | Đạt. Bổ sung ghi chú `roles` có 3 vai **Admin, Sales, Tổng hợp**; `departments` có 3 phòng **Ban giám đốc, Kinh doanh, Tổng hợp**. |
| §11 Quyết định 1 | Đạt. Đổi thành 3 vai mới, 3 phòng mới, ghi ngày đổi 19/11/2026 và đầy đủ lý do cơ cấu chi nhánh thật (khớp Quyết định 13 trong `task_tracker.md`). |

**Kiểm tra dòng "Lưu ý" (`grep` từ khóa cũ trong `docs/SPEC.md`)**
- Không còn bất kỳ chuỗi `KTV` nào trong `docs/SPEC.md`.
- Các dòng còn chứa `Kỹ thuật` / `kỹ thuật` (7 dòng):
  - Dòng 19, 22, 59, 160, 232: tên phân hệ/nghiệp vụ "phiếu hỗ trợ kỹ thuật" / "FE-07 Phiếu hỗ trợ kỹ thuật" (hợp lệ).
  - Dòng 64 (§3.1 khung "Đã chốt"): giải thích lịch sử "Vai **Kỹ thuật** cũ đã bỏ: ..." (hợp lệ).
  - Dòng 473 (§11 quyết định 1): giải thích lịch sử "(đổi 19/11/2026; trước đó là Admin, Sales, Kỹ thuật) ... **không có phòng Kỹ thuật**. Vì vậy bỏ vai Kỹ thuật, ..." (hợp lệ).
- Các dòng còn chứa `Kế toán` / `Dự án` (3 dòng):
  - Dòng 27: "không kế toán/sổ sách đầy đủ" (nghiệp vụ ngoài phạm vi, hợp lệ).
  - Dòng 229, 473: giải thích lịch sử gộp hai phòng Kế toán và Dự án cũ thành phòng Tổng hợp (hợp lệ).
- Kết luận tiêu chí "Xong khi": SPEC không còn giao việc nào cho vai Kỹ thuật; ma trận §3.3 có cột Tổng hợp. Đạt.

**B, C, E**
- B (an toàn đầu vào), C (phân tầng, CSRF, BCrypt...): không áp dụng, task không có code.
- E: task không có file test (theo dòng "File test"). Không có file nào trong `test/` bị đổi; toàn bộ `ant test` pass.

## Ghi chú ngoài phạm vi

- `docs/PROMPTS.md` và `task_tracker_deploy.md` đang untracked và không thuộc Task 9.1; bước Commit không `git add` hai file này.
- `rules.md` §1 (dòng 28) vẫn đang ghi "Chỉ 3 vai trò: Admin, Sales, Kỹ thuật" (nằm ngoài phạm vi "File sửa" của Task 9.1); có thể đồng bộ riêng khi hoàn tất Giai đoạn 9.
- `docs/SPEC.md` đã phản ánh 3 vai trò / 3 phòng ban mới trước khi code và DB được cập nhật ở Task 9.2 → 9.4 (tương tự thứ tự Task 8.1 → 8.4), vì vậy cần triển khai tiếp 9.2 → 9.4 để code/`PERMISSIONS.md`/`db/schema.sql` khớp SPEC.
- `docs/SPEC.md` §3.3 vẫn giữ chú thích "(nguồn: báo cáo mục 3.1.4.2)" ở tiêu đề dù ma trận đã cập nhật theo cơ cấu mới; không ảnh hưởng nghiệp vụ.
