# QA Report - Task 8.2

**Kết luận:** ĐẠT (không có lỗi cần sửa)
**Kết quả `ant test`:** 1.975 pass / 0 fail / 0 error / 3 skipped (tổng 1.978 test ở 89 lớp, `BUILD SUCCESSFUL`; `BranchInfoDAOTest` 14/14 pass; 3 skipped là test tích hợp mặc định bỏ qua)

**Phạm vi:** Task 8.2 tạo bảng `branch_info`, thêm `signer_phone`/`signer_fax` vào `contracts`, kèm model `BranchInfo`, cập nhật `Contract`, DAO `BranchInfoDAO` và unit test `BranchInfoDAOTest`. Đối chiếu với `task_tracker.md` (UC-55, UC-33, UC-35; BR-56, BR-57) và `docs/SPEC.md` (§5.8, §6, §7); kiểm tra trên nhánh `feat/task-8.2-branch-info-schema`.

## Danh sách lỗi

Không phát hiện lỗi 🔴 Critical, 🟡 Major hay 🟢 Minor.

## Lưu ý triển khai cho người dùng

> [!IMPORTANT]
> **Trước khi kéo/merge code Task 8.4 về checkout chính**, người dùng cần chạy migration `db/migrations/V39__add_branch_info_and_contract_signer_contact__ndat2003.sql` lên CSDL `poscs_db`. Nếu chạy code Task 8.4 (đọc/ghi `signer_phone`, `signer_fax`) trên CSDL cũ chưa có V39, mọi thao tác đọc/lưu hợp đồng sẽ lỗi `Unknown column`. (Agent không tự chạy file `.sql` lên DB theo `rules.md` §3.)

## Chi tiết kiểm tra

**A. Phạm vi & Git**
- Nhánh hiện tại: `feat/task-8.2-branch-info-schema`, khớp dòng "Nhánh" của Task 8.2.
- Các file thuộc task:
  - `db/migrations/V39__add_branch_info_and_contract_signer_contact__ndat2003.sql` (mới)
  - `db/schema.sql` (sửa: +30 dòng)
  - `src/java/poscs/model/BranchInfo.java` (mới, 41 dòng)
  - `src/java/poscs/model/Contract.java` (sửa: +13 dòng)
  - `src/java/poscs/dao/BranchInfoDAO.java` (mới, 98 dòng)
  - `test/poscs/dao/BranchInfoDAOTest.java` (mới, 265 dòng)
- Phần "Không đụng": không chạm `ContractDAO`, controller, JSP hay hai cột `signer_name`, `signer_position`. Đạt.
- Mã hóa UTF-8: cả 6 file đều là UTF-8 thuần (không UTF-16, không BOM), tiếng Việt hiển thị chuẩn. Đạt.

**B. Đối chiếu V39 và `db/schema.sql` (BR-56, BR-57, SPEC §7)**

| Hạng mục | V39 | `db/schema.sql` | Kết quả |
|---|---|---|---|
| Ghi `schema_migrations` | `V39__add_branch_info_and_contract_signer_contact__ndat2003` (khớp tên file) | (bảng rỗng như các bản trước) | Đạt |
| Bảng `branch_info` | `branch_info_id tinyint NOT NULL PK`, `director_name varchar(100) NULL`, `director_position varchar(100) NULL`, `phone varchar(20) NULL`, `fax varchar(20) NULL`, `updated_by int NULL` FK → `users(user_id)`, `updated_at timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`, `utf8mb4_unicode_ci` | Khớp từng cột, kiểu, collation, PK, KEY và FK; đặt cạnh nhóm tổ chức (ngay sau `users`) | Đạt |
| Dòng gieo `branch_info` | `(1, 'Giám đốc chi nhánh')`, các cột khác `NULL` | Khớp nguyên văn | Đạt |
| Hai cột mới trên `contracts` | `signer_phone varchar(20) NULL AFTER signer_position`, `signer_fax varchar(20) NULL AFTER signer_phone` | Đặt ngay sau `signer_position`, khớp kiểu và collation | Đạt |
| Số lượng bảng trong `schema.sql` | — | 30 lệnh `CREATE TABLE` (khớp `docs/SPEC.md` §7 "30 bảng") | Đạt |
| Comment đầu file V39 | Giải thích lý do, quyết định, thứ tự triển khai theo kiểu V27 | Comment gọn kèm `Xem V39` | Đạt |
| Tương thích `IntegrationDb.readStatements` | — | Comment `--` riêng dòng, câu lệnh kết thúc `;`, `branch_info` đứng sau `users` | Đạt (kiểm tĩnh) |

**C. Model & DAO (rules §3–§4)**
- `BranchInfo.java`: đủ 6 trường (`directorName`, `directorPosition`, `phone`, `fax`, `updatedBy` kiểu `Integer`, `updatedAt` kiểu `Timestamp`) kèm getter/setter. Đạt.
- `Contract.java`: thêm `signerPhone`, `signerFax` kèm getter/setter đặt ngay cạnh `signerName`/`signerPosition`, không đổi chữ ký hay hành vi cũ. Đạt.
- `BranchInfoDAO.java`:
  - Độ dài 98 dòng (≤ 250), mọi phương thức ≤ 22 dòng (≤ 40).
  - Dùng `PreparedStatement` + `try-with-resources`, không có `javax.*`, ghi log lỗi qua SLF4J như các DAO khác.
  - `get()`: truy vấn dòng `branch_info_id = 1`, xử lý `rs.wasNull()` cho `updated_by` (tránh biến `NULL` thành `0`), trả `null` khi không có dòng hoặc `SQLException`. Đạt.
  - `update(BranchInfo info, int actorId)`: chặn sớm khi `info == null` hoặc `actorId <= 0`; bind đúng thứ tự `(1..6)` gồm 4 ô thông tin, `actorId` và `ROW_ID = 1`; trả `false` khi không khớp dòng hoặc `SQLException`. Đạt.

**D. Unit Test (`BranchInfoDAOTest.java`)**
- Dùng `JdbcStub` và `MockedStatic<DBContext>` thống nhất với các DAO test khác.
- 14/14 test pass, phủ đủ các nhánh: đọc đủ 6 cột, kiểm SQL truy vấn theo `branch_info_id = ?`, dòng gieo sẵn có các cột `NULL` (kể cả `updated_by` → `null`), không có dòng → `null`, lỗi kết nối / lỗi truy vấn → `null`; `update` bind đúng thứ tự tham số và `actorId`, kiểm SQL `UPDATE`, `phone`/`fax` là `null`, `info == null` / `actorId <= 0` không đụng DB, không khớp dòng nào → `false`, lỗi `executeUpdate` / lỗi kết nối → `false`.

## Ghi chú ngoài phạm vi

- `task_tracker.md` đang có thay đổi ở working tree (bổ sung kế hoạch Giai đoạn 9–10 và ngoại lệ ở Quyết định 11 / Task 8.5; Task 8.2 vẫn `[ ]`) và `docs/PROMPTS.md` đang untracked từ trước. Ở bước Commit Task 8.2, chỉ `git add` đúng các file của Task 8.2 và `docs/qa/Task-8.2.md` (không dùng `git add .`).
- 3 lớp test tích hợp (`test/poscs/integration/`) mặc định bị bỏ qua khi chạy `ant test`; việc chạy thực tế `db/schema.sql` bản mirror trên `poscs_it` sẽ được xác nhận khi người dùng chạy integration test.
