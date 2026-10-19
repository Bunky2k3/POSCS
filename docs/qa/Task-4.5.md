# QA Report - Task 4.5
**Kết luận:** CẦN SỬA
**Kết quả `ant test`:** 1233/0/3 (1233 pass / 0 fail / 3 skipped tích hợp theo rules §0)

## Danh sách lỗi

- [x] 🟡 `ContractDocumentDAO.java` › `findDocumentById(int)`, `countActiveDocuments(int)` & `mapResultSetToFullDocument(ResultSet)` (dòng ~174–218, ~330–339)
  - Vi phạm: `rules.md` §1 ("Luồng chi tiết UC-36 → UC-54: lấy hành vi code và JSP hiện có làm chuẩn, chỉ tách nhỏ theo rules §3, không thêm tính năng"), `task_tracker.md` Giai đoạn 4 ("Chỉ **tách**, không thêm tính năng") & `rules.md` §4 ("Hàm > 40 dòng hoặc file > 250 dòng: xem xét tách module phụ trợ" — file hiện dài **353 dòng**).
  - Mô tả:
    Trong `ContractDAO` gốc chỉ có đúng 3 phương thức phục vụ UC-52: `addDocument(ContractDocument, int)`, `voidDocument(int, int, String)`, và `findDocumentsOf(int)`. Việc tự thêm `findDocumentById(int)`, `countActiveDocuments(int)` và `mapResultSetToFullDocument(ResultSet)` không có bất kỳ nơi nào trong toàn bộ codebase gọi tới, vi phạm nguyên tắc "Chỉ tách, không thêm tính năng" của Giai đoạn 4 và làm file phình lên 353 dòng (vượt ngưỡng 250 dòng tại `rules.md` §4).
  - Gợi ý sửa: Xóa `findDocumentById(int)`, `countActiveDocuments(int)` và `mapResultSetToFullDocument(ResultSet)` (cùng các test tương ứng cho 2 hàm thừa này trong `ContractDocumentDAOTest.java`) để giữ đúng hành vi gốc và đưa `ContractDocumentDAO.java` về ~250 dòng.

- [x] 🟡 `ContractDocumentDAO.java` › `addDocument(ContractDocument, int)` & `insertDocumentRow(Connection, ContractDocument, int)` (dòng ~67–97, ~234–246)
  - Vi phạm: Checklist B (An toàn đầu vào: xử lý khoảng trắng thừa và độ dài tối đa theo `db/schema.sql` dòng 4608–4611: `doc_type varchar(50)`, `title varchar(255)`, `file_url varchar(500)`, `note varchar(255)`).
  - Mô tả:
    1. Chưa chuẩn hóa khoảng trắng đồng nhất: ở dòng 69–70 có gọi `trimToNull(doc.getDocType())` và `trimToNull(doc.getFileUrl())` để kiểm tra khác null, nhưng khi ghi DB (`insertDocumentRow` dòng 239, 241) và ghi lịch sử (`addDocument` dòng 85–86) lại truyền `doc.getDocType()`, `doc.getFileUrl()` và `doc.getNote()` chưa `trim`.
    2. Chưa kiểm soát độ dài tối đa của các cột theo `db/schema.sql` (trong khi `voidDocument` đã có cắt `delete_reason` ở 255 ký tự): nếu `docType > 50`, `fileUrl > 500`, hoặc `title` / `note > 255` ký tự, câu lệnh `INSERT` sẽ ném `SQLException` thay vì từ chối đầu vào sai quy cách hoặc cắt gọn an toàn.
  - Gợi ý sửa:
    ```java
    String docType = trimToNull(doc.getDocType());
    String fileUrl = trimToNull(doc.getFileUrl());
    if (docType == null || docType.length() > 50
            || fileUrl == null || fileUrl.length() > 500) {
        return DOC_FAILED;
    }
    String title = truncate(trimToNull(doc.getTitle()), 255);
    String note = truncate(trimToNull(doc.getNote()), 255);
    ```
    Và bổ sung test trong `ContractDocumentDAOTest.java` cho trường hợp `docType` / `fileUrl` vượt độ dài tối đa hoặc `title` / `note` vượt 255 ký tự.

- [x] 🟢 `ContractDAO.java` › `trimToNull(String)` & `fullName(ResultSet, String, String, String)` (dòng ~1528–1547)
  - Vi phạm: `rules.md` §4 (Clean Code, dọn mã trùng lặp sau khi tách).
  - Mô tả: Tương tự `formatDate` và `truncate` đã ủy quyền sang các DAO con ở Task 4.3 và 4.4, hai hàm `trimToNull` và `fullName` đã được mở `public static` ở `ContractDocumentDAO` (dòng 296–314), nhưng trong `ContractDAO.java` (dòng 1528–1547) vẫn giữ nguyên thân hàm trùng lặp.
  - Gợi ý sửa:
    ```java
    private static String trimToNull(String value) {
        return ContractDocumentDAO.trimToNull(value);
    }

    private static String fullName(ResultSet rs, String lastCol, String midCol, String firstCol) throws SQLException {
        return ContractDocumentDAO.fullName(rs, lastCol, midCol, firstCol);
    }
    ```
