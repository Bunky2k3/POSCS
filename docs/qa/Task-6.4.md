# QA Report - Task 6.4
**Kết luận:** APPROVED - Đạt yêu cầu UC & BR
**Kết quả `ant test`:** 2008 pass / 0 fail / 3 skipped (tổng 2011; 3 skipped là test tích hợp `_it`, không chạy DB thật theo rules §0; BUILD SUCCESSFUL)

**Phạm vi review:**
- File code: `src/java/poscs/controller/DashboardController.java`, `src/java/poscs/service/DashboardService.java`
- File test: `test/poscs/service/DashboardServiceTest.java`, `test/poscs/controller/DashboardControllerTest.java`
- Đối chiếu SPEC: UC-36 và BR-15, BR-18 trong `docs/SPEC.md`.

## Đã kiểm tra và đạt
- **Nghiệp vụ UC-36 & Tách phân tầng:** Tách toàn bộ nghiệp vụ tổng hợp (khách hàng, khách mới trong kỳ, phân bố trạng thái hợp đồng, doanh thu kỳ/tháng, tăng trưởng, nhãn thứ/ngày/tháng/năm, danh sách hợp đồng & khách hàng trong phạm vi) ra khỏi `DashboardController` vào `DashboardService` kế thừa `BaseService`. `DashboardController` được tinh gọn về 86 dòng, chỉ đóng vai trò Presentation thuần (đọc tham số, gọi Service, gán attribute, forward sang `dashboard.jsp`).
- **Nghiệp vụ BR-15 (Lọc Năm/Kỳ):** Sử dụng `Period.parse(yearParam, periodParam)`. Khi năm trống, null, khoảng trắng hoặc giá trị không hợp lệ (chuỗi chữ "abc", năm < 1970, năm > 9999) thì hủy lọc kỳ (`period = null`), hiển thị toàn bộ thời gian; doanh thu và cửa sổ hợp đồng tự động rơi về tháng hiện tại đúng như quy định SPEC.
- **Nghiệp vụ BR-18 (Doanh thu & Phân cách hàng nghìn):** Doanh thu được tính trực tiếp từ bảng `contract_payments` (thông qua `ContractDAO` ủy quyền sang `ContractPaymentDAO.sumInvoiceAmountInPeriod` và `sumInvoiceAmountByMonth`), đúng nguồn chân lý của SPEC. Tỷ lệ tăng trưởng so với kỳ trước/tháng trước được làm tròn 1 chữ số thập phân, phòng thủ chống chia cho 0 khi kỳ trước doanh thu bằng 0 (`trend = null`). Giao diện `dashboard.jsp` định dạng VND và phân cách hàng nghìn theo locale `vi-VN` (`formatCompactVND`).
- **Phân quyền & Phạm vi xem (UC-36):** Phân biệt chính xác phạm vi mặc định: Sales mặc định xem "mine" kèm toàn bộ cấp dưới (nhóm) qua `EmployeeDAO.findTeamUserIds`, Admin mặc định xem "all"; cả hai đều có thể chuyển đổi linh hoạt. Bộ lọc địa bàn tự động gán các tỉnh phụ trách ở lần đầu mở trang và cho phép người dùng tùy chọn hoặc bỏ tích toàn bộ để xem toàn chi nhánh.
- **An toàn đầu vào & Quy chuẩn code:** Mọi hàm đều ≤ 36 dòng (đạt chuẩn rules §4 ≤ 40 dòng). Xử lý null-safe triệt để với `safeList`, `safeAmount`, lọc khử trùng và parse an toàn mảng `provinceId`. Không có `// TODO` hay placeholder. Giữ nguyên tương thích ngược với các trường DAO trong `DashboardController` để 48 unit tests kế thừa của `DashboardControllerTest` tiếp tục pass 100% mà không phải sửa test.
- **Unit test:** Bộ 11 unit tests mới trong `DashboardServiceTest` bao phủ toàn bộ các nhánh rẽ và kịch bản biên (năm rác/trống, tăng trưởng âm/dương/chia cho 0, DAO trả null, phân quyền Sales/Admin, lọc tỉnh, định dạng nhãn ngày tiếng Việt). Chạy toàn bộ 2011 tests đều pass (2008 pass, 0 fail, 3 skipped).

## Danh sách lỗi & Khuyến nghị
- [x] 🟢 `DashboardService.java` (dòng 206-270)
  - Vi phạm: rules §4 (tệp > 250 dòng: xem xét tách module phụ trợ)
  - Mô tả: `DashboardService.java` hiện dài 271 dòng, vượt nhẹ ngưỡng 250 dòng do chứa lớp DTO nội tĩnh `DashboardSummary` (65 dòng) với đầy đủ các getter chuẩn JavaBean phục vụ Expression Language (EL) trong JSP.
  - Gợi ý sửa: Cân nhắc tách `DashboardSummary` thành tệp độc lập `src/java/poscs/service/DashboardSummary.java` (hoặc `poscs.model.DashboardSummary.java`) để `DashboardService.java` giảm xuống còn ~206 dòng. Không bắt buộc sửa ngay vì không ảnh hưởng logic vận hành.

- [x] 🟢 `DashboardService.java` › `buildContractValues()` (dòng ~140)
  - Vi phạm: Phòng thủ biên (Checklist B - An toàn đầu vào)
  - Mô tả: Vòng lặp `for (Contract c : contracts)` gọi trực tiếp `c.getContractId()`. Mặc dù `ContractDAO` không trả về phần tử null trong danh sách, việc bổ sung kiểm tra null sẽ giúp phương thức này an toàn tuyệt đối khi dùng trong các ngữ cảnh khác.
  - Gợi ý sửa:
    ```java
    for (Contract c : contracts) {
        if (c != null) {
            values.put(c.getContractId(), c.getCurrentValue());
        }
    }
    ```
