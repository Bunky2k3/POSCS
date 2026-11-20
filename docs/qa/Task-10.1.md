# QA Report - Task 10.1

**Kết luận:** APPROVED
**Kết quả `ant test`:** 2.214 pass / 0 fail / 0 error / 3 skipped (92 lớp test: 89 lớp unit test pass 100%, 3 lớp integration test trong `test/poscs/integration/` mặc định bỏ qua; `BUILD SUCCESSFUL`, cùng số với Task 9.5)

**Phạm vi:** Task 10.1 chỉ cập nhật đặc tả nghiệp vụ trong `docs/SPEC.md` cho Giai đoạn 10 (vòng đời hợp đồng theo chi nhánh, mã hợp đồng để trống tới lúc Ký, giấy tờ dạng ô tích, kỳ thanh toán có loại kỳ / nộp hồ sơ / hạn thu / số thực thu / sửa kỳ, bước giao hàng và nhắc ký sau 15 ngày). Đối chiếu với Quyết định 14–15 và các dòng con UC, BR / MSG / NFR, File sửa, Không đụng, Lưu ý, Xong khi của Task 10.1 trong `task_tracker.md`; thay đổi thực tế lấy từ `git status` và `git diff main` trên nhánh `docs/task-10.1-spec-vong-doi-giay-to-ky-thanh-toan`.

## Danh sách lỗi

Không phát hiện lỗi 🔴 Critical hoặc 🟡 Major (**0 🔴 Critical, 0 🟡 Major, 2 🟢 Minor**). Đạt yêu cầu UC & BR của Task 10.1; hai điểm 🟢 Minor dưới đây là thiếu sót diễn đạt nhỏ khi tóm lược lại giữa các mục trong `docs/SPEC.md`, không làm sai nghiệp vụ.

- [x] 🟢 `docs/SPEC.md` › §6, dòng `BR-60` (dòng ~344)
  - Vi phạm: nhất quán nội bộ giữa §6 `BR-60` với §5.4 (dòng 233), §11 mục 15 (dòng 514) và Quyết định 15 (chốt 20/11/2026) trong `task_tracker.md`.
  - Mô tả: Câu `"Đã nộp hồ sơ": ghi ngày nộp (không sau hôm nay, MSG-064); kỳ có số ngày thì hạn thu = ngày nộp + số ngày, không có thì nhập hạn tay.` trong `BR-60` chưa ghi rõ `(không bắt buộc)` cho việc nhập hạn tay, dù câu liền sau có nhắc `"hoặc đã có ngày nộp hồ sơ dù chưa có hạn thu → Chờ thu"` và §5.4 / §11 đã ghi rõ `(không bắt buộc, chốt 20/11/2026)`.
  - Gợi ý sửa: Bổ sung `(không bắt buộc)` ngay tại câu nhập hạn tay trong `BR-60`:
    ```
    "Đã nộp hồ sơ": ghi ngày nộp (không sau hôm nay, MSG-064); kỳ có số ngày thì hạn thu = ngày nộp + số ngày, không có thì nhập hạn tay (không bắt buộc).
    ```

- [x] 🟢 `docs/SPEC.md` › §5.4, đoạn "Giấy tờ (UC-52, BR-59)" (dòng ~230)
  - Vi phạm: nhất quán nội bộ giữa §5.4 với `BR-59` (dòng 343) và ma trận quyền §3.2–§3.3 (dòng 69, 85).
  - Mô tả: (1) Đoạn liệt kê các ô nhập khi bấm ô trống / "Thêm tờ" viết `"để nhập ngày trên giấy, số giấy, đường dẫn, ghi chú"` nhưng bỏ sót **tên giấy tờ (bắt buộc khi chọn loại "Khác")** đã quy định ở `BR-59`. (2) Câu cuối viết `"Admin, Sales quản lý vùng thêm/huỷ; Tổng hợp chỉ xem."` chưa nhắc **Sales có cấp trên** (cũng chỉ xem theo §3.2 và cột `X (*)` ở §3.3).
  - Gợi ý sửa: Bổ sung hai chi tiết nhỏ trong dòng 230:
    ```
    ... để nhập ngày trên giấy, số giấy, tên giấy tờ (bắt buộc với loại "Khác"), đường dẫn, ghi chú. ... Admin, Sales quản lý vùng thêm/huỷ; Sales có cấp trên và Tổng hợp chỉ xem.
    ```

## Chi tiết kiểm tra

### A. Đối chiếu nghiệp vụ (UC, BR, MSG)

- **Kiểm tra từng ký tự 5 MSG mới (`MSG-061` → `MSG-065`, §9 dòng 463–467):**
  - `MSG-061`: `"Hợp đồng chưa có mã. Vui lòng nhập mã hợp đồng trước khi ký."` (nội tuyến) — khớp 100%.
  - `MSG-062`: `"Vui lòng nhập ngày trên giấy tờ."` (nội tuyến) — khớp 100%.
  - `MSG-063`: `"Ngày trên giấy tờ không được sau hôm nay."` (nội tuyến) — khớp 100%.
  - `MSG-064`: `"Ngày nộp hồ sơ không được sau hôm nay."` (nội tuyến) — khớp 100%.
  - `MSG-065`: `"Hợp đồng [tên hợp đồng] đã giao hàng [n] ngày nhưng chưa ký."` (thông báo) — khớp 100%.
  - Dòng chú thích đầu §9 (dòng 399) bổ sung thêm loại **Thông báo** khớp với `MSG-065`.
- **Kiểm tra đánh số & trùng mã trên toàn repo (kể cả `docs/Project_Report_v8.docx` và `docs/Report6_HuongDanSuDung.docx`):**
  - Đánh số nối tiếp sau `BR-57` → `BR-58`..`BR-62`; sau `MSG-060` → `MSG-061`..`MSG-065`. Không có bất kỳ trùng mã nào ở code, tài liệu hay `.docx`.
- **Kiểm tra `BR-18`, `BR-35` và `BR-58` → `BR-62` (§6):**
  - `BR-18` (dòng 302): bổ sung đúng quy tắc doanh thu cộng **số tiền thực thu** theo **ngày thu**, chưa tách riêng tạm ứng.
  - `BR-35` (dòng 319): bỏ `Mã HĐ` khỏi bắt buộc khi tạo, trỏ sang `BR-58`, giữ nguyên toàn bộ quy tắc Người phụ trách của Task 8.5.
  - `BR-58` (dòng 342): mã nhập tay, không bắt buộc ở Nháp, bắt buộc và duy nhất trước khi Ký (`MSG-061`), nhiều bản Nháp trống mã không coi là trùng, hiển thị `"Chưa có số"`.
  - `BR-59` (dòng 343): khớp 100% danh mục 15 loại giấy tờ (`Phiếu GQNCKH` → `Khác`), ngày trên giấy bắt buộc không sau hôm nay (`MSG-062`, `MSG-063`), số giấy, link không bắt buộc, loại `"Khác"` bắt nhập tên, một loại nhiều tờ, phân biệt `Biên bản giao nhận hàng hoá` với `Biên bản nghiệm thu bàn giao`, huỷ mềm bắt lý do, ghi nhật ký, làm được ở mọi tiến độ kể cả sau thanh lý.
  - `BR-60` (dòng 344): đủ loại kỳ (`Tạm ứng` / `Thanh toán`), điều kiện, số ngày, hạn thu để trống khi lập, `"Đã nộp hồ sơ"` (`MSG-064`), `"Đã thu"` với số thực thu mặc định bằng số tiền kỳ (> 0, khác không bắt lý do), đủ 4 tình trạng suy ra theo đúng thứ tự kèm quyết định 20/11/2026 (`có hạn thu, hoặc đã có ngày nộp hồ sơ dù chưa có hạn thu → Chờ thu`), điều kiện lập/sửa/xoá và sau thanh lý, ô `%`, không thu hồi dần tạm ứng. (Xem điểm 🟢 Minor về cụm `(không bắt buộc)`.)
  - `BR-61` (dòng 345): đúng 2 bước suy ra chỉ cho hợp đồng Nháp từ giấy tờ chưa huỷ (`Chuẩn bị hàng`, `Đã giao – chưa ký`), `Ngày giao` = ngày sớm nhất của các biên bản giao nhận chưa huỷ, ký xong mất bước giao hàng.
  - `BR-62` (dòng 346): nhắc người phụ trách một lần khi hợp đồng `"Đã giao – chưa ký"` có `ngày giao ≤ hôm nay − 15 ngày` (`MSG-065`).

### D & F. Phạm vi, Không đụng, Từng mục "File sửa" & Tiêu chí "Xong khi"

- **Nhánh & git:** Đang ở đúng nhánh `docs/task-10.1-spec-vong-doi-giay-to-ky-thanh-toan` (HEAD `8a5444f` trùng `main`, chưa có commit bước Code). Task 8.5 đã merge vào `main` (`2f3a66a`), đạt điều kiện dòng "Lưu ý".
- **File thay đổi (`git diff main --numstat`):** chỉ `docs/SPEC.md` (+58/−28, UTF-8 hợp lệ không BOM) và `task_tracker.md` (+221/−1: phần bổ sung kế hoạch Giai đoạn 10 và ghi chú quyết định chốt 20/11/2026; Task 10.1 vẫn `[ ]`).
- **Không đụng:** `git diff main -- src web db test` hoàn toàn rỗng (không đụng code, `db/`, test).

| Mục trong "File sửa" / "UC" | Kết quả |
|---|---|
| §3.1 & §3.3 (quyền giấy tờ & kỳ thanh toán) | Đạt. Đổi "Quản lý tài liệu" → "Quản lý giấy tờ (UC-52)"; dòng kỳ thanh toán thêm sửa kỳ và ghi nhận **đã nộp hồ sơ** (`X / X (*) / -`); dòng **đã thu** thêm "kèm số tiền thực thu" (`X / X (*) / X`); §3.1 cập nhật mô tả vai Tổng hợp nhất quán. |
| §4.4 (`UC-10`, `UC-11`, `UC-33`, `UC-44`, `UC-52`, `UC-53`) & §4.6 (`UC-36`, `NotificationScheduler`) | Đạt. Đủ cả 6 UC yêu cầu ở §4.4 (`UC-52` đổi tên thành "Quản lý giấy tờ của hợp đồng"), đồng bộ thêm `UC-36` và dòng `NotificationScheduler` ở §4.6. |
| §5.4 (Vòng đời, Tạo, Ký, Tiến độ, Cập nhật, Danh sách, Chi tiết, Giấy tờ, Kỳ thanh toán) | Đạt (1 🟢 Minor nhỏ ở đoạn Giấy tờ). Không còn mâu thuẫn với `UC-35` hay `BR-58`. |
| §5.7 (Thông báo) | Đạt. Đổi "hai trường hợp" → "ba trường hợp", thêm mục 3 nhắc ký (`BR-62`, `ref_type` riêng khác loại 1, gửi người phụ trách hiện tại, không gửi Admin, bấm mở chi tiết HĐ, nhắc 1 lần), đổi chống trùng sang "cho mỗi loại", đánh số `CHANGE_REQUEST` thành mục 4. |
| §6 (Bảng BR) | Đạt (1 🟢 Minor nhỏ ở `BR-60`). Tiêu đề `BR-01 → BR-62` khớp đúng 62 dòng liên tục, không nhảy số, không trùng. |
| §7 (Mô hình dữ liệu) | Đạt. Tiêu đề `30 bảng` khớp 8 + 5 + 4 + 7 + 3 + 3 = 30 bảng; đổi mô tả `contract_documents` và dòng xoá mềm sang "giấy tờ"; ghi đủ cột mới/nullable của `contracts.contract_code`, `contract_documents` (`doc_date`, `doc_no` tối đa 50, `file_url` NULL), `contract_payments` (`payment_type`, `condition_note` tối đa 255, `payment_days`, `submitted_date`, `paid_amount`, `due_date` NULL), khớp Task 10.2–10.4. |
| §9 (Bảng MSG) & §10 (Quy ước mã HĐ) | Đạt. 65 dòng `MSG-001` → `MSG-065` liên tục; §10 cập nhật đồng bộ với `BR-58`. |
| §11 (Quyết định đã chốt) | Đạt. Cập nhật Quyết định 7 (ngưỡng 15 ngày), thêm Quyết định 14 và 15 tóm tắt đủ mục 14 và 15 (kể cả các điểm chốt 20/11/2026) trong `task_tracker.md`. |
| Xong khi: không còn chỗ nào nói tài liệu "chỉ gắn đường dẫn" là bắt buộc | Đạt. Đã quét toàn bộ `docs/SPEC.md` theo từ khoá `tài liệu`, `chỉ gắn`, `đường dẫn`: mọi chỗ liên quan `UC-52` đều ghi rõ đường dẫn **không bắt buộc** (chỉ giữ nguyên tắc không upload file nếu có dán đường dẫn ở dòng 230). |

### B, C, E

- **B (An toàn đầu vào) & C (Phân tầng, CSRF, BCrypt...):** Không áp dụng trực tiếp vì task chỉ sửa tài liệu. Về nội dung đặc tả: phân quyền §3.3 / §5.4 nhất quán với `MSG-022`, giữ cơ chế xoá mềm bắt buộc lý do cho `contract_documents`, giữ ràng buộc đường dẫn `http://`/`https://` khi có nhập.
- **E (Unit test):** Task không có file test mới (`git diff main -- test` rỗng); đã tự chạy toàn bộ `ant test` và toàn bộ 2.214 unit tests pass 100%.

## Ghi chú ngoài phạm vi (và lưu ý cho Task 10.2 → 10.5)

- **Lưu ý cho Task 10.2 (`UC-54` & `BR-58`):** `BR-58` chỉ cho phép bỏ trống mã hợp đồng khi còn **Nháp**. Khi bỏ bắt buộc `contractCode` ở bản Nháp, luồng **Chữa sai sót nhập liệu HĐ đã ký (UC-54)** vẫn phải chặn không cho xoá trống mã hợp đồng đã ký.
- **Lưu ý cho Task 10.4 (`BR-60`):**
  - `ContractPayment.getDaysLateOrEarly()` hiện trừ trực tiếp `dueDate.getTime()` chưa kiểm `dueDate == null`; cần xử lý null-safe khi `due_date` cho phép NULL.
  - Khi viết `ContractPaymentService`, cần xử lý nhất quán các trường hợp biên: (i) `payment_days` nếu có nhập phải hợp lệ (không âm / dương), (ii) chặn `"Đã nộp hồ sơ"` với kỳ đã `"Đã thu"` (`paid_date != null`), (iii) ngày nộp hồ sơ khi để trống (mặc định hôm nay giống `paidDate` hay báo lỗi bắt buộc).
- **Lưu ý cho Task 10.5 (`BR-62` & §5.7):**
  - Cụm văn nói `"quá 15 ngày"` (`> 15`) và công thức `"(tức ngày giao ≤ hôm nay − 15 ngày)"` (`≥ 15`) hơi vênh mặt chữ; khi cài đặt và viết test ở Task 10.5, bám đúng công thức biên đã khoá `ngày giao ≤ hôm nay − 15 ngày` (đủ 15 ngày → tạo thông báo, 14 ngày → không tạo, khớp dòng "File test" của Task 10.5).
  - Trong §5.7 dòng 264–265 có sẵn từ trước `ref_type = "CONTRACT"` / `"TICKET"` trong khi `NotificationService.java` dùng `"contract_expiring"` / `"ticket_sla"` (lệch cũ ngoài phạm vi Task 10.1); dòng 266 mới thêm đã tránh mâu thuẫn bằng cách ghi `ref_type riêng, khác loại 1`.
- **Lệch tạm giữa SPEC và CSDL/code:** Tương tự Task 8.1 và 9.1, sau Task 10.1 thì `docs/SPEC.md` §7 đã mô tả các cột mới của Giai đoạn 10 trước khi `db/schema.sql` và code được cập nhật ở Task 10.2 → 10.5.
- **File ngoài phạm vi trong working tree:** `docs/PROMPTS.md` và `task_tracker_deploy.md` đang untracked; bước Commit không `git add` hai file này.
- **Khác:** `docs/SPEC.md` §1 (dòng 27) vẫn còn cụm ngoặc ngắn cũ `(chỉ theo dõi kỳ thanh toán: số tiền, hạn thu, đã thu)` (nằm ngoài dòng "File sửa" của Task 10.1). `rules.md` §1 ghi "Agent không sửa `docs/SPEC.md`", nhưng Task 10.1 do người dùng chỉ định rõ sửa SPEC nên prompt người dùng được ưu tiên theo đầu file `rules.md`.
