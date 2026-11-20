# QA Report - Task 8.6

**Kết luận:** ĐẠT (0 🔴 Critical, 0 🟡 Major, 0 🟢 Minor)
**Kết quả `ant test`:** 2.136 pass / 0 fail / 0 error / 3 skipped (tổng 2.139 test ở 92 lớp, `BUILD SUCCESSFUL`; 3 skipped là test tích hợp mặc định bỏ qua)
**Kết quả `ant compile-jsps`:** `BUILD SUCCESSFUL`

**Phạm vi:** Task 8.6 cập nhật hướng dẫn sử dụng (hai trang JSP trong ứng dụng, kịch bản/ảnh chụp màn hình và bản Word `Report6_HuongDanSuDung.docx`) cho Thông tin chi nhánh, người ký bên mình và người phụ trách hợp đồng. Đối chiếu với `task_tracker.md` (UC-55, UC-33, UC-35; BR-35, BR-56, BR-57, MSG-057 → MSG-060), `docs/SPEC.md` (§5.4, §5.8, §6, §9), mã nguồn/JSP của các Task 8.3–8.5 (`branchInfo.jsp`, `BranchInfoValidator`, `BranchInfoService`, `BranchInfoController`, `addnewcontract.jsp`, `updatecontract.jsp`, `viewcontractdetail.jsp`) và `rules.md` (§3–§4, §7); kiểm tra trên nhánh `docs/task-8.6-guide-branch-signer-owner`.

## Danh sách lỗi

Không phát hiện lỗi nào.

## Chi tiết kiểm tra

**A. Phạm vi & Git**
- Nhánh hiện tại: `docs/task-8.6-guide-branch-signer-owner`, khớp dòng "Nhánh" của Task 8.6.
- Các file thuộc task (10 file, khớp phạm vi "File sửa" và các ảnh chụp lại theo kịch bản `contract.mjs`):
  - `web/jsp/guide/contract.jsp`
  - `web/jsp/guide/employee.jsp`
  - `tools/guide/shots/contract.mjs`
  - `tools/guide/word/export_word.py`
  - `docs/Report6_HuongDanSuDung.docx`
  - `web/guide/contract/04-tao-hop-dong.png`
  - `web/guide/contract/07-quan-ly.png`
  - `web/guide/contract/11-da-ky.png`
  - `web/guide/contract/12-chua-sai-sot.png`
  - `web/guide/contract/19-lap-phu-luc.png`
- Phần "Không đụng": không thay đổi bất kỳ file Java (`src/`, `test/`) hay `docs/SPEC.md`. Đạt.
- Mã hóa & cú pháp: cả 4 file văn bản đều UTF-8 thuần (không BOM); `node --check tools/guide/shots/contract.mjs` và kiểm tra AST Python `tools/guide/word/export_word.py` đều hợp lệ. Đạt.

**B. Đối chiếu nội dung hướng dẫn với SPEC & giao diện thực tế**
- `web/jsp/guide/contract.jsp`:
  - Bảng tiến độ (mục 1.1): đã sửa dòng *Nháp* (trừ người phụ trách và người ký bên mình) và *Đã ký* (form thông tin khoá hết, bỏ câu "Chỉ còn đổi người phụ trách").
  - Bảng quyền (mục 1.3): bổ sung dòng *Chọn người phụ trách khi tạo; thay người phụ trách đã bị khoá tài khoản* (chỉ Admin).
  - Mục 3 (Tạo hợp đồng): mô tả đúng `BR-35` (Sales tạo là chính mình dạng chỉ đọc, Admin tạo chọn Sales đang hoạt động, khoá sau khi tạo, trỏ mục 5.2) và `BR-56` (4 ô *Người ký bên mình*, *Chức vụ*, *Điện thoại*, *Fax* tự điền chỉ đọc từ Thông tin chi nhánh; chỉ nhập phần bên đối tác). Bổ sung hộp cảnh báo trích nguyên văn `MSG-060` (*Chưa có thông tin Giám đốc chi nhánh. Admin khai tại trang Thông tin chi nhánh.*) khớp `addnewcontract.jsp`, kèm hướng dẫn trỏ sang Nhân viên mục 9. Mục 3.1 bổ sung nguyên nhân người phụ trách Admin chọn đã bị khoá tài khoản.
  - Mục 4 (Chi tiết hợp đồng): bổ sung phần người ký bên mình kèm điện thoại, fax nếu có, khớp `viewcontractdetail.jsp`.
  - Mục 5 (Quản lý hợp đồng): bước 1 mô tả đúng bản Nháp không sửa người phụ trách và 4 ô người ký bên mình, mỗi lần bấm *Lưu thay đổi* chép lại GĐCN mới nhất. Bổ sung mục 5.2 *Thay người phụ trách đã nghỉ việc* (`#thay-nguoi-phu-trach`) khớp `BR-35` và nguyên văn chú thích *"Người phụ trách đã bị khoá tài khoản, chọn người thay."* trong `updatecontract.jsp`.
  - Mục 6.1 & 6.2 (Hợp đồng đã ký & Chữa sai sót): bỏ mục người phụ trách còn sửa được và nút *Lưu thay đổi*; đánh lại bước 1–3 khớp Hình 11 mới; cập nhật tham chiếu *(số 3 ở Hình 11)*; nêu rõ nút *Lưu thay đổi* ẩn cho tới khi Admin bật chữa sai sót hoặc thay người phụ trách đã bị khoá, và chữa sai sót không sửa được ngày ký, người phụ trách, bốn ô người ký bên mình.
  - Mục 10 (Phụ lục): bổ sung người phụ trách theo hợp đồng gốc và người ký bên mình tự điền từ Thông tin chi nhánh; bước 3 nhắc ô *Người phụ trách* bên cạnh cũng không đổi được.
  - Không còn câu chữ cũ nào nói người phụ trách "còn đổi được" hay "ô duy nhất còn sửa được". Đạt.
- `web/jsp/guide/employee.jsp`:
  - Mục 1: bổ sung giới thiệu mục **Thông tin chi nhánh** ngay dưới Nhân viên trên thanh bên trái, liên kết tới `#thong-tin-chi-nhanh` (mục 9).
  - Mục 7 (Khoá tài khoản): cập nhật bước 4 nêu rõ hợp đồng chỉ đổi được người phụ trách sau khi đã khoá tài khoản (trỏ hướng dẫn Hợp đồng, mục 5.2).
  - Thêm mục 9 **Thông tin chi nhánh** (`#thong-tin-chi-nhanh`): chỉ Admin, một bản ghi duy nhất không có nút tạo/xoá; đúng 4 nhãn ô (*Họ tên Giám đốc chi nhánh*, *Chức vụ*, *Điện thoại*, *Fax*), ràng buộc `BR-57` chính xác (bắt buộc ≤ 100 ký tự; SĐT/Fax không bắt buộc, ≤ 20 ký tự, 8–15 chữ số, cho phép `+` đầu chuỗi, khoảng trắng, dấu chấm, gạch ngang, ngoặc đơn); dòng *Cập nhật lần cuối bởi … lúc …*; thông báo lưu xong `MSG-057` (*Đã lưu thông tin chi nhánh.*); ghi chú thời điểm chép vào hợp đồng và hợp đồng đã ký giữ nguyên; bảng 5 thông báo lỗi khớp nguyên văn `BranchInfoValidator` và `BranchInfoService` (`MSG-058`, `MSG-015`, `MSG-059`, `MSG_SAVE_FAILED`, `MSG-018`).
  - Đẩy mục *Thông báo lỗi thường gặp* sang mục 10, bổ sung ngữ cảnh trang Thông tin chi nhánh vào dòng `MSG-022`. Đạt.

**C. Kịch bản chụp ảnh & Ảnh chụp màn hình (`tools/guide/shots/contract.mjs`, `web/guide/contract/*.png`)**
- `contract.mjs`: ghi chú rõ điều kiện tiên quyết đã khai GĐCN ở `/branch`; cập nhật selector mark 4 ở `04-tao-hop-dong.png` sang ô chỉ đọc *Người phụ trách* (thay vì `#owner`), giữ `n: 6` ở tiêu đề *Thông tin ký kết*; bỏ mark `#owner` và nút *Lưu thay đổi* ở `11-da-ky.png`, đánh lại số 1–3.
- Kiểm tra trực quan cả 5 ảnh PNG (`04-tao-hop-dong.png`, `07-quan-ly.png`, `11-da-ky.png`, `12-chua-sai-sot.png`, `19-lap-phu-luc.png`): hình sắc nét, hiển thị đúng giao diện sau Task 8.4 và 8.5 (4 ô người ký bên mình chỉ đọc đã điền GĐCN, ô Người phụ trách chỉ đọc, không hiện dải vàng `MSG-060`, hợp đồng đã ký giữ nguyên người ký cũ và ẩn nút *Lưu thay đổi*), số khoanh đỏ khớp 100% với từng mục `ol.guide-steps` trong `contract.jsp`. Đạt.

**D. Xuất bản Word (`tools/guide/word/export_word.py`, `docs/Report6_HuongDanSuDung.docx`)**
- `export_word.py`: thêm 1 dòng cuối `CHANGES` ngày `20/11/2026`, loại `T`, tóm tắt đầy đủ thay đổi kèm `(Task 8.6)`.
- Kiểm tra tệp `docs/Report6_HuongDanSuDung.docx`:
  - `docProps/core.xml`: `lastModifiedBy` = `tools/guide/word/export_word.py` (đúng quy chuẩn Task 7.4a / 8.6).
  - `word/document.xml`: chứa đầy đủ nội dung mới (dòng lịch sử `Task 8.6`, mục *Thông tin chi nhánh*, *Thay người phụ trách đã nghỉ việc*, các câu `MSG-057` → `MSG-060`, *Form thông tin khoá hết*) và không còn câu cũ (*Chỉ còn đổi người phụ trách*, *ô duy nhất còn sửa được*). Đạt.

## Ghi chú ngoài phạm vi

- `task_tracker.md` đang có thay đổi sẵn ở working tree và `docs/PROMPTS.md`, `task_tracker_deploy.md` đang untracked từ trước. Ở bước Commit Task 8.6, chỉ `git add` đúng 10 file thuộc Task 8.6 và `docs/qa/Task-8.6.md` (không dùng `git add .`).
