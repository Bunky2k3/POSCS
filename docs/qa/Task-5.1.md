# QA Report - Task 5.1
**Kết luận:** APPROVED - Đạt yêu cầu UC & BR
**Kết quả `ant test`:** 1789 pass / 0 fail / 3 skipped (tổng 1792; 3 skipped là test tích hợp `_it`, không chạy DB thật theo rules §0)

**Phạm vi review:**
- File code: `src/java/poscs/validator/TicketValidator.java`
- File test: `test/poscs/validator/TicketValidatorTest.java`
- Đối chiếu SPEC: UC-21, UC-22 và BR-39, BR-41, BR-43 (kèm hỗ trợ BR-40, BR-47).

## Đã kiểm tra và đạt
- **Nghiệp vụ BR-39:** Kiểm tra đầy đủ 6 trường bắt buộc khi tạo phiếu: Khách hàng (`enterpriseId > 0`), Loại phiếu (`ticketType` không blank), Mức ưu tiên (`priority` hợp lệ), Kênh tiếp nhận (`receptionChannel` không blank), Mô tả sự cố (`description` không blank), Kỹ thuật viên (`assignedTechnicianId > 0`). Chặn gán trạng thái khác `Mới tiếp nhận` khi tạo mới và chặn ngày tạo trong tương lai (MSG-019).
- **Nghiệp vụ BR-41:** Kiểm tra tập giá trị ưu tiên hợp lệ ∈ {Thấp, Bình thường, Cao, Khẩn cấp}; chặn giá trị ngoài danh mục và trả lỗi `MSG_PRIORITY_INVALID`.
- **Nghiệp vụ BR-43:** Xử lý cờ bảo hành mặc định Có (true) qua `isWarrantyDefault()` và `resolveWarranty()`.
- **Hỗ trợ cập nhật & xóa (BR-40, BR-47):** Kiểm soát luồng chuyển trạng thái hợp lệ (`Mới tiếp nhận -> Đang xử lý -> Đã đóng`), vi phạm trả đúng `MSG-051` theo SPEC; chặn xóa phiếu ở trạng thái `Đang xử lý` theo BR-47 với thông báo lỗi nguyên văn SPEC.
- **Ràng buộc đối tác & hợp đồng:** Kiểm tra đúng vai `Khách mua` (UC-21) và hợp đồng liên kết phải thuộc cùng khách hàng (`validateContractBelongsToCustomer`).
- **An toàn đầu vào & Quy chuẩn code:** Xử lý null-safe triệt để, kiểm soát độ dài theo schema (`ticket_type` 100, `reception_channel` 50, `cause_category` 100). Không có `// TODO` hay placeholder. Mọi hàm đều ≤ 32 dòng (đạt chuẩn ≤ 40 dòng).
- **Phạm vi & Unit test:** Không sửa đổi file code/test cũ ngoài task; 29/29 unit tests mới bao phủ toàn bộ nhánh hợp lệ và biên; assertion kiểm tra chặt chẽ đúng mã và chuỗi thông báo SPEC.

## Danh sách lỗi
*Không có lỗi.*
