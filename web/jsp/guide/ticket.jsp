<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%--
    Hướng dẫn sử dụng -- phân hệ Phiếu hỗ trợ kỹ thuật.

    Viết theo CODE hiện tại (TechnicalSupportTicketController,
    TechnicalSupportTicketDAO, AccessControl, NotificationScheduler và các JSP
    trong jsp/customersupport/), không theo tài liệu cũ. Ảnh ở web/guide/ticket/
    do tools/guide/capture.mjs chụp theo kịch bản tools/guide/shots/ticket.mjs
    -- số trong ol.guide-steps phải khớp số khoanh đỏ trên ảnh; sửa một bên thì
    soát lại bên kia.

    Chỉ dùng tập thẻ mô tả ở đầu css/guide.css: tools/guide/ dựng lại đúng nội
    dung này sang file Word.
--%>
<c:set var="guideTitle" value="Phiếu hỗ trợ"/>
<c:set var="img" value="${pageContext.request.contextPath}/guide/ticket"/>
<%@ include file="/jsp/guide/_top.jspf" %>

<h1>Phiếu hỗ trợ kỹ thuật</h1>
<p class="lead">Tiếp nhận yêu cầu hỗ trợ của khách hàng, giao cho kỹ thuật viên xử lý và theo dõi tới khi đóng phiếu.</p>

<h2 id="tong-quan">1. Tổng quan</h2>
<p>Trên thanh bên trái, bấm <strong>Phiếu hỗ trợ</strong>. Mỗi phiếu là một yêu cầu hỗ trợ của một <strong>khách hàng mua</strong>: bảo hành, bảo trì, sửa chữa, tư vấn… Mã phiếu tự sinh dạng <code>TK-0001</code>, <code>TK-0002</code>…</p>
<p>Nội dung một phiếu chia theo người viết:</p>
<ul>
    <li><strong>Người tạo phiếu</strong> (Sales hoặc Admin) ghi nhận yêu cầu: khách hàng, hợp đồng liên quan, loại phiếu, mức ưu tiên, kênh tiếp nhận, hạn xử lý, kỹ thuật viên phụ trách và <strong>mô tả sự cố</strong>.</li>
    <li><strong>Kỹ thuật viên phụ trách</strong> đánh giá theo mạch <strong>nguyên nhân</strong> (kèm nhóm nguyên nhân) → <strong>phương hướng xử lý</strong> → <strong>kết quả xử lý</strong>, và chuyển trạng thái của phiếu.</li>
</ul>

<h3 id="trang-thai">1.1. Trạng thái của phiếu</h3>
<table class="guide-table">
    <tr><th>Trạng thái</th><th>Nghĩa</th></tr>
    <tr><td>Mới tiếp nhận</td><td>Phiếu vừa tạo, kỹ thuật viên chưa bắt tay xử lý. Phiếu mới luôn ở trạng thái này.</td></tr>
    <tr><td>Đang xử lý</td><td>Kỹ thuật viên đang xử lý. Phiếu ở trạng thái này <strong>không xoá được</strong>.</td></tr>
    <tr><td>Đã đóng</td><td>Đã xử lý xong. Lần đầu chuyển sang Đã đóng, hệ thống ghi <strong>Thời điểm hoàn tất</strong>.</td></tr>
</table>
<div class="guide-note">
    <p>Trạng thái đổi ở form sửa phiếu (<a href="#cap-nhat-xu-ly">mục 6</a>). Chọn trạng thái nào cũng được, không bắt buộc đi đúng thứ tự trên; phiếu đã đóng vẫn mở lại được. Mỗi lần đổi trạng thái, <strong>Lịch sử xử lý</strong> của phiếu có thêm một dòng (<a href="#chi-tiet">mục 4</a>).</p>
</div>

<h3 id="quyen">1.2. Ai làm được gì</h3>
<table class="guide-table">
    <tr><th>Việc</th><th>Admin</th><th>Sales</th><th>Kỹ thuật</th></tr>
    <tr><td>Xem danh sách và chi tiết mọi phiếu, xuất Excel, xuất phiếu PDF</td><td>Được</td><td>Được</td><td>Được</td></tr>
    <tr><td>Tạo phiếu, sửa mọi thông tin, xoá phiếu</td><td>Được</td><td>Được</td><td>Không</td></tr>
    <tr><td>Cập nhật trạng thái, nhóm nguyên nhân, nguyên nhân, phương hướng, kết quả xử lý, ghi chú nội bộ</td><td>Được</td><td>Được</td><td>Chỉ phiếu giao cho mình</td></tr>
</table>
<div class="guide-note">
    <p>Kỹ thuật viên chỉ thấy nút <strong>Sửa thông tin</strong> ở phiếu giao cho chính mình. Form mở ra khoá phần Thông tin chung và Mô tả sự cố (<a href="#cap-nhat-xu-ly">mục 6</a>).</p>
    <p>Khác với Khách hàng và Hợp đồng, Sales đã có <strong>cấp trên</strong> trong sơ đồ tổ chức vẫn toàn quyền trên phiếu hỗ trợ.</p>
</div>

<h2 id="danh-sach">2. Xem và tìm phiếu</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm <strong>Phiếu hỗ trợ</strong> trên thanh bên trái. Danh sách hiện 10 phiếu mỗi trang, phiếu tạo sau cùng đứng đầu. Vai nào cũng thấy toàn bộ phiếu.</p>
<figure class="guide-shot">
    <a href="${img}/01-danh-sach.png" target="_blank"><img src="${img}/01-danh-sach.png" alt="Đầu trang danh sách phiếu hỗ trợ"></a>
    <figcaption>Hình 1. Đầu trang danh sách phiếu hỗ trợ (tài khoản Sales)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Bốn ô đếm</strong>: số phiếu Mới tiếp nhận, Đang xử lý, Đã đóng, và số phiếu mức Khẩn cấp (ở mọi trạng thái). Các ô đếm theo kỳ đang chọn ở số 5, không theo ô tìm kiếm hay ô lọc trạng thái, mức ưu tiên.</li>
    <li><strong>Ô tìm kiếm</strong>: gõ mã phiếu, một đoạn mô tả sự cố hoặc tên khách hàng rồi nhấn Enter.</li>
    <li><strong>Trạng thái</strong>: chỉ xem phiếu ở một trạng thái.</li>
    <li><strong>Mức ưu tiên</strong>: Khẩn cấp, Cao, Bình thường hoặc Thấp.</li>
    <li><strong>Năm</strong> và <strong>kỳ</strong>: chọn năm, rồi <em>Cả năm</em>, một quý hoặc một tháng. Lọc theo <strong>ngày tạo</strong> phiếu. <em>Mọi thời điểm</em> (mặc định) là không lọc; ô kỳ chỉ có tác dụng khi đã chọn năm.</li>
    <li><strong>Xuất Excel</strong>: tải danh sách đang lọc về máy (<a href="#xuat-file">mục 9</a>).</li>
    <li><strong>Tạo phiếu hỗ trợ</strong> (<a href="#tao-phieu">mục 3</a>). Chỉ Admin và Sales thấy nút này.</li>
</ol>
<div class="guide-note">
    <p>Đổi ô Trạng thái, Mức ưu tiên, Năm hay Kỳ là danh sách tải lại ngay. Phiếu của một khách hàng còn xem được ở trang chi tiết khách hàng, tab <em>Phiếu hỗ trợ kỹ thuật</em>.</p>
    <p>Danh sách chưa có ô lọc theo người xử lý. Kỹ thuật viên tìm phiếu của mình ở cột <strong>Người xử lý</strong> (số 4 ở Hình 2), hoặc qua thông báo nhắc hạn (<a href="#sla">mục 7</a>).</p>
</div>

<h3 id="doc-dong">2.1. Đọc một dòng của danh sách</h3>
<figure class="guide-shot">
    <a href="${img}/02-doc-dong.png" target="_blank"><img src="${img}/02-doc-dong.png" alt="Bảng danh sách phiếu hỗ trợ"></a>
    <figcaption>Hình 2. Bảng danh sách phiếu, đã kéo sang phải cho lộ cột Thao tác</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Loại phiếu</strong>: bấm vào (hoặc vào mã phiếu ở cột đầu tiên) để mở trang chi tiết.</li>
    <li><strong>Khách hàng</strong>, ngay dưới là <strong>hợp đồng liên quan</strong> (bấm mã hợp đồng để mở hợp đồng). <em>Không gắn hợp đồng</em>: phiếu không gắn với hợp đồng nào, như ở dòng được khoanh.</li>
    <li><strong>Trạng thái</strong>, kèm nhãn hạn xử lý nếu có: <em>Quá hạn SLA</em> (đã qua hạn mà chưa đóng) hoặc <em>Sắp tới hạn</em> (còn dưới 24 giờ). Xem <a href="#sla">mục 7</a>.</li>
    <li><strong>Người xử lý</strong>: kỹ thuật viên phụ trách phiếu.</li>
    <li><strong>Thao tác</strong>: <i class="fa-regular fa-eye"></i> xem chi tiết, <i class="fa-solid fa-pen"></i> sửa, <i class="fa-solid fa-trash"></i> xoá. Sửa và xoá chỉ Admin, Sales thấy; ở phiếu Đang xử lý nút xoá mờ đi, như ở dòng được khoanh. Bảng rộng hơn màn hình nên cột này thường nằm khuất bên phải: kéo thanh cuộn ngang dưới bảng, hoặc giữ chuột trên bảng rồi kéo sang trái.</li>
</ol>

<h2 id="tao-phieu">3. Tạo phiếu hỗ trợ</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ở danh sách, bấm <strong>Tạo phiếu hỗ trợ</strong>. Ô có dấu <span style="color:#e2536b">*</span> là bắt buộc.</p>
<figure class="guide-shot narrow">
    <a href="${img}/03-tao-phieu.png" target="_blank"><img src="${img}/03-tao-phieu.png" alt="Trang Tạo phiếu hỗ trợ"></a>
    <figcaption>Hình 3. Trang Tạo phiếu hỗ trợ (đang điền ví dụ)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Khách hàng</strong>: bấm vào ô để mở hộp chọn, gõ tên, mã khách hàng hoặc tên người phụ trách để tìm, rồi bấm chọn. Hộp chọn chỉ có khách hàng mua; nhà cung cấp không lập phiếu hỗ trợ.</li>
    <li><strong>Hợp đồng liên quan</strong> (không bắt buộc): mở được sau khi đã chọn khách hàng, và chỉ liệt kê hợp đồng của khách đó (Hình 4).</li>
    <li><strong>Loại phiếu</strong>: Bảo hành, Bảo trì, Sửa chữa, Tư vấn hoặc Khác.</li>
    <li><strong>Mức ưu tiên</strong>: Khẩn cấp, Cao, Bình thường hoặc Thấp.</li>
    <li><strong>Kênh tiếp nhận</strong>: khách báo qua Điện thoại, Email, Trực tiếp hay Website.</li>
    <li><strong>Hạn xử lý (SLA)</strong> (không bắt buộc): ngày giờ phải xử lý xong. Có hạn thì hệ thống cảnh báo khi phiếu sắp hoặc đã quá hạn (<a href="#sla">mục 7</a>).</li>
    <li><strong>Kỹ thuật viên phụ trách</strong>: người sẽ xử lý phiếu. Ô này chỉ liệt kê nhân viên vai Kỹ thuật.</li>
    <li><strong>Còn trong thời hạn bảo hành</strong>: được tích sẵn; bỏ tích nếu thiết bị đã hết bảo hành.</li>
    <li><strong>Mô tả sự cố</strong>: khách báo gì, ở đâu, từ bao giờ.</li>
    <li>Bấm <strong>Tạo phiếu hỗ trợ</strong>. Hệ thống sinh mã <code>TK-xxxx</code>, phiếu ở trạng thái Mới tiếp nhận, và trang chi tiết của phiếu mở ra.</li>
</ol>
<div class="guide-note">
    <p>Form tạo không có ô nguyên nhân, phương hướng hay kết quả: đó là phần kỹ thuật viên điền sau khi xuống xử lý (<a href="#cap-nhat-xu-ly">mục 6</a>). Người tạo phiếu và ngày tạo lấy theo tài khoản đang đăng nhập và ngày hôm nay. Lịch sử xử lý có ngay dòng đầu tiên <em>Tạo phiếu → Mới tiếp nhận</em>.</p>
</div>
<p>Bấm ô <strong>Hợp đồng liên quan</strong> (số 2 ở Hình 3) để mở hộp chọn:</p>
<figure class="guide-shot narrow">
    <a href="${img}/04-chon-hop-dong.png" target="_blank"><img src="${img}/04-chon-hop-dong.png" alt="Hộp chọn hợp đồng liên quan"></a>
    <figcaption>Hình 4. Hộp chọn hợp đồng liên quan của khách hàng KH-0001</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ô tìm</strong>: gõ mã hoặc tên hợp đồng.</li>
    <li><strong>-- Không gắn hợp đồng --</strong>: chọn khi phiếu không liên quan tới hợp đồng nào, hoặc để gỡ hợp đồng đã chọn.</li>
    <li><strong>Hợp đồng của khách</strong>: mã ở trên, tiêu đề ở dưới. Bấm để chọn. Phụ lục hợp đồng (mã có đuôi <code>/PL01</code>…) cũng là một dòng riêng.</li>
</ol>
<div class="guide-note">
    <p>Đổi sang khách hàng khác thì ô hợp đồng tự bỏ chọn và nạp lại danh sách của khách mới. Khách chưa có hợp đồng nào thì ô ghi <em>-- Không có hợp đồng liên quan --</em>.</p>
</div>

<h3 id="loi-tao">3.1. Khi không lưu được</h3>
<p>Trước khi gửi, trang kiểm các ô bắt buộc và hiện chữ đỏ ngay dưới ô còn trống. Nếu hệ thống từ chối sau khi gửi, thông báo đỏ hiện ở đầu form:</p>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Vui lòng nhập đầy đủ các trường bắt buộc.</td><td>Còn ô bắt buộc để trống. Điền đủ rồi gửi lại.</td></tr>
    <tr><td>Phiếu hỗ trợ chỉ lập cho khách hàng mua. Vui lòng chọn lại khách hàng.</td><td>Khách hàng đã chọn không giữ vai khách hàng mua. Chọn lại trong hộp chọn khách hàng.</td></tr>
    <tr><td>Hợp đồng đã chọn không thuộc về khách hàng của phiếu này…</td><td>Chọn lại hợp đồng của đúng khách, hoặc chọn <em>Không gắn hợp đồng</em>.</td></tr>
    <tr><td>Không tạo được phiếu hỗ trợ. Vui lòng thử lại.</td><td>Lỗi hệ thống. Thử lại; vẫn lỗi thì báo quản trị viên.</td></tr>
</table>

<h2 id="chi-tiet">4. Xem chi tiết phiếu</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Ở danh sách, bấm vào mã phiếu hoặc loại phiếu, hoặc nút <i class="fa-regular fa-eye"></i>.</p>
<figure class="guide-shot narrow">
    <a href="${img}/05-chi-tiet.png" target="_blank"><img src="${img}/05-chi-tiet.png" alt="Trang chi tiết phiếu hỗ trợ"></a>
    <figcaption>Hình 5. Trang chi tiết của một phiếu đã đóng</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Đầu trang</strong>: mã phiếu, loại phiếu, nhãn bảo hành (<em>Còn bảo hành</em> / <em>Hết bảo hành</em>), mức ưu tiên, trạng thái, và khách hàng (bấm để mở hồ sơ khách).</li>
    <li><strong>Xuất phiếu</strong>: tải bản PDF của phiếu (<a href="#xuat-file">mục 9</a>).</li>
    <li><strong>Sửa thông tin</strong>: Admin, Sales (<a href="#sua">mục 5</a>), và kỹ thuật viên được giao phiếu này (<a href="#cap-nhat-xu-ly">mục 6</a>).</li>
    <li><strong>Xóa</strong>: chỉ Admin, Sales thấy (<a href="#xoa">mục 8</a>).</li>
    <li><strong>Thông tin chung</strong>: hợp đồng liên quan, kênh tiếp nhận, kỹ thuật viên phụ trách, người tạo phiếu, ngày tạo, hạn xử lý (kèm nhãn <em>Quá hạn SLA</em> hoặc <em>Sắp tới hạn</em> nếu có) và thời điểm hoàn tất.</li>
    <li><strong>Người tạo phiếu ghi nhận</strong>: mô tả sự cố, kèm tên người tạo và ngày tạo.</li>
    <li><strong>Nhân viên kỹ thuật xử lý</strong>: nguyên nhân sự cố (nhóm nguyên nhân là nhãn nhỏ cạnh tiêu đề), phương hướng xử lý và kết quả xử lý, kèm tên kỹ thuật viên. Mục nào chưa điền thì hiện câu nhắc, ví dụ <em>Kỹ thuật viên chưa đánh giá nguyên nhân.</em></li>
    <li><strong>Lịch sử xử lý</strong>: mỗi lần đổi trạng thái là một dòng, mới nhất ở trên: trạng thái trước → sau, lúc nào, ai đổi, và ghi chú nội bộ nếu có. Dòng dưới cùng <em>Tạo phiếu</em> là lúc lập phiếu.</li>
</ol>
<div class="guide-note">
    <p>Lịch sử xử lý do hệ thống tự ghi, không sửa hay xoá được. Chỉ đổi trạng thái mới sinh dòng mới: sửa mô tả hay nguyên nhân mà giữ nguyên trạng thái thì không.</p>
</div>

<h2 id="sua">5. Sửa phiếu</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ở trang chi tiết bấm <strong>Sửa thông tin</strong>, hoặc bấm nút <i class="fa-solid fa-pen"></i> ở danh sách. Kỹ thuật viên xem <a href="#cap-nhat-xu-ly">mục 6</a>.</p>
<figure class="guide-shot narrow">
    <a href="${img}/06-sua.png" target="_blank"><img src="${img}/06-sua.png" alt="Trang Cập nhật phiếu hỗ trợ"></a>
    <figcaption>Hình 6. Trang Cập nhật phiếu hỗ trợ (tài khoản Sales)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Thông tin chung</strong>: sửa được hết, gồm khách hàng, hợp đồng, loại phiếu, mức ưu tiên, kênh tiếp nhận, hạn xử lý, kỹ thuật viên phụ trách, bảo hành. Đổi khách hàng thì ô hợp đồng tự bỏ chọn. Xoá trắng ô Hạn xử lý rồi lưu là gỡ hạn.</li>
    <li><strong>Mô tả sự cố</strong>.</li>
    <li><strong>Nhân viên kỹ thuật xử lý</strong>: trạng thái, nhóm nguyên nhân, nguyên nhân, phương hướng, kết quả. Cách điền xem <a href="#cap-nhat-xu-ly">mục 6</a>.</li>
    <li><strong>Ghi chú nội bộ</strong>: vì sao đổi trạng thái lần này. Chỉ được lưu khi trạng thái đổi.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>. Trang chi tiết mở ra.</li>
</ol>
<div class="guide-note">
    <p>Đổi <strong>Kỹ thuật viên phụ trách</strong> là giao phiếu cho người khác: từ lúc lưu, người mới sửa được phần xử lý, người cũ thì không.</p>
</div>

<h2 id="cap-nhat-xu-ly">6. Cập nhật xử lý (kỹ thuật viên)</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật (phiếu giao cho mình)</span></div>
<p>Kỹ thuật viên mở phiếu được giao cho mình rồi bấm <strong>Sửa thông tin</strong> (số 3 ở Hình 5). Admin và Sales điền cùng các ô này ở trang sửa phiếu (<a href="#sua">mục 5</a>).</p>
<figure class="guide-shot narrow">
    <a href="${img}/07-cap-nhat-xu-ly.png" target="_blank"><img src="${img}/07-cap-nhat-xu-ly.png" alt="Form cập nhật xử lý của kỹ thuật viên"></a>
    <figcaption>Hình 7. Form của kỹ thuật viên được giao phiếu (đang điền ví dụ đóng phiếu)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Dòng nhắc</strong>: với kỹ thuật viên, phần Thông tin chung (có nhãn <em>Chỉ xem</em>) và Mô tả sự cố bị khoá. Cần đổi những ô đó, ví dụ lùi hạn xử lý, thì báo Sales hoặc Admin.</li>
    <li><strong>Trạng thái</strong>: <em>Đang xử lý</em> khi bắt tay xử lý, <em>Đã đóng</em> khi đã xong.</li>
    <li><strong>Nhóm nguyên nhân</strong>: Do vận chuyển, Do lắp đặt, Do thiết bị hoặc Khác. Chưa rõ thì để <em>-- Chưa xác định --</em>.</li>
    <li><strong>Nguyên nhân sự cố</strong>: vì sao hỏng.</li>
    <li><strong>Phương hướng xử lý</strong>: định làm gì để khắc phục.</li>
    <li><strong>Kết quả xử lý</strong>: đã làm gì, kết quả ra sao. Điền khi đóng phiếu.</li>
    <li><strong>Ghi chú nội bộ</strong>: vì sao đổi trạng thái lần này. Ghi chú chỉ hiện ở Lịch sử xử lý, không nằm trên phiếu.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>. Trang chi tiết mở ra.</li>
</ol>
<div class="guide-warn">
    <p><strong>Ghi chú nội bộ chỉ được lưu khi trạng thái đổi.</strong> Giữ nguyên trạng thái mà viết ghi chú thì ghi chú bị bỏ qua. Nguyên nhân, phương hướng và kết quả thì lần nào bấm Lưu cũng được lưu.</p>
</div>
<p>Cách dùng thường gặp:</p>
<table class="guide-table">
    <tr><th>Việc</th><th>Trạng thái chọn</th><th>Nên điền</th></tr>
    <tr><td>Nhận phiếu, bắt tay xử lý</td><td>Đang xử lý</td><td>Ghi chú nội bộ, ví dụ lịch xuống hiện trường.</td></tr>
    <tr><td>Đã tìm ra nguyên nhân</td><td>Giữ Đang xử lý</td><td>Nhóm nguyên nhân, Nguyên nhân sự cố, Phương hướng xử lý.</td></tr>
    <tr><td>Xử lý xong</td><td>Đã đóng</td><td>Kết quả xử lý; ghi chú nội bộ, ví dụ khách đã nghiệm thu.</td></tr>
    <tr><td>Khách báo lỗi tái diễn sau khi đóng</td><td>Đang xử lý</td><td>Ghi chú nội bộ: lý do mở lại.</td></tr>
</table>
<div class="guide-note">
    <p>Chuyển sang <em>Đã đóng</em> lần đầu thì <strong>Thời điểm hoàn tất</strong> là lúc bấm Lưu. Mở lại phiếu thì thời điểm đó bị xoá, đóng lại thì ghi thời điểm mới. Phiếu đã đóng không còn nhãn Quá hạn SLA.</p>
</div>

<h2 id="sla">7. Hạn xử lý (SLA) và nhắc hạn</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<ul>
    <li><strong>Đặt hạn</strong>: ô <em>Hạn xử lý (SLA)</em> ở form tạo và form sửa phiếu, do Admin hoặc Sales điền. Ô này không bắt buộc; phiếu không có hạn thì không có cảnh báo, cũng không có nhắc.</li>
    <li><strong>Nhãn cảnh báo</strong>: <em>Quá hạn SLA</em> khi đã qua hạn mà phiếu chưa đóng, <em>Sắp tới hạn</em> khi còn dưới 24 giờ. Nhãn hiện ở cột Trạng thái của danh sách (số 3 ở Hình 2) và ở ô Hạn xử lý của trang chi tiết.</li>
    <li><strong>Thông báo</strong>: kỹ thuật viên phụ trách nhận thông báo <em>Phiếu TK-… sắp/đã quá hạn xử lý SLA.</em> ở biểu tượng chuông trên thanh trên cùng. Hệ thống kiểm mỗi giờ một lần, và mỗi phiếu chỉ báo một lần cho mỗi người; giao phiếu cho người khác thì người mới được báo riêng.</li>
</ul>

<h2 id="xoa">8. Xoá phiếu</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<figure class="guide-shot">
    <a href="${img}/08-xoa.png" target="_blank"><img src="${img}/08-xoa.png" alt="Hộp xác nhận xoá phiếu"></a>
    <figcaption>Hình 8. Hộp xác nhận xoá phiếu hỗ trợ</figcaption>
</figure>
<ol class="guide-steps">
    <li>Bấm <strong>Xóa</strong> ở trang chi tiết (hoặc nút <i class="fa-solid fa-trash"></i> ở danh sách).</li>
    <li>Bấm <strong>Xoá phiếu</strong> để xoá, hoặc <strong>Huỷ bỏ</strong> nếu đổi ý. Xoá xong, danh sách phiếu mở ra.</li>
</ol>
<div class="guide-warn">
    <p><strong>Không xoá được phiếu Đang xử lý.</strong> Nút Xóa của phiếu đó mờ đi, cả ở trang chi tiết lẫn danh sách. Nếu phiếu vừa bị người khác chuyển sang Đang xử lý ngay trước khi bạn bấm xoá, trang chi tiết báo như Hình 9. Muốn xoá thì chuyển trạng thái trước.</p>
</div>
<figure class="guide-shot">
    <a href="${img}/09-xoa-bi-chan.png" target="_blank"><img src="${img}/09-xoa-bi-chan.png" alt="Thông báo không xoá được phiếu đang xử lý"></a>
    <figcaption>Hình 9. Thông báo khi xoá một phiếu đang xử lý</figcaption>
</figure>
<div class="guide-note">
    <p>Xoá là <strong>xoá mềm</strong>: phiếu biến khỏi danh sách và các ô đếm, nhưng dữ liệu vẫn được giữ lại trong hệ thống.</p>
</div>

<h2 id="xuat-file">9. Xuất Excel và xuất phiếu PDF</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p><strong>Xuất Excel</strong> (số 6 ở Hình 1) tải về file <code>.xls</code> chứa <strong>toàn bộ</strong> phiếu khớp bộ lọc đang bật (tìm kiếm, trạng thái, mức ưu tiên, năm và kỳ) chứ không chỉ trang đang xem, phiếu mới nhất ở đầu. Các cột gồm: Mã phiếu, Loại phiếu, Khách hàng, Hợp đồng liên quan, Mức ưu tiên, Kênh tiếp nhận, Trạng thái, Người xử lý, Người tạo, Ngày tạo, Hạn xử lý (SLA), Thời điểm đóng, Bảo hành, Mô tả sự cố, Nguyên nhân sự cố, Nhóm nguyên nhân, Phương hướng xử lý, Kết quả xử lý.</p>
<p><strong>Xuất phiếu</strong> (số 2 ở Hình 5) tải về bản PDF khổ A4 tiêu đề <em>PHIẾU HỖ TRỢ KỸ THUẬT</em>, để in hoặc gửi khách: mã phiếu, khách hàng, hợp đồng liên quan, loại phiếu, mức ưu tiên, kênh tiếp nhận, trạng thái, ngày tạo, hạn xử lý, kỹ thuật viên phụ trách, thời điểm đóng; rồi bốn đoạn Mô tả sự cố → Nguyên nhân sự cố (nhóm nguyên nhân đứng đầu, trong ngoặc vuông) → Phương hướng xử lý → Kết quả xử lý. Mục trống in dấu gạch ngang; nội dung dài tự sang trang.</p>

<h2 id="loi-thuong-gap">10. Thông báo lỗi thường gặp</h2>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Vui lòng nhập đầy đủ các trường bắt buộc.</td><td>Còn ô bắt buộc để trống ở form tạo hoặc sửa. Điền đủ rồi lưu lại.</td></tr>
    <tr><td>Phiếu hỗ trợ chỉ lập cho khách hàng mua. Vui lòng chọn lại khách hàng.</td><td>Khách hàng đã chọn không giữ vai khách hàng mua. Chọn lại khách hàng.</td></tr>
    <tr><td>Hợp đồng đã chọn không thuộc về khách hàng của phiếu này…</td><td>Chọn lại hợp đồng của đúng khách, hoặc chọn <em>Không gắn hợp đồng</em>.</td></tr>
    <tr><td>Không tạo được phiếu hỗ trợ. / Không lưu được thay đổi. Vui lòng thử lại.</td><td>Lỗi hệ thống, hoặc phiếu vừa bị người khác xoá. Tải lại trang rồi thử lại; vẫn lỗi thì báo quản trị viên.</td></tr>
    <tr><td>Không xoá được phiếu này vì phiếu đang có người xử lý…</td><td>Phiếu đang ở trạng thái Đang xử lý. Chuyển trạng thái trước rồi xoá (<a href="#xoa">mục 8</a>).</td></tr>
    <tr><td>Không tìm thấy phiếu hỗ trợ này. Có thể phiếu đã bị xoá.</td><td>Mở một link cũ tới phiếu đã bị xoá, hoặc mã trên đường dẫn không đúng.</td></tr>
    <tr><td>Không tải được danh sách hợp đồng (trong ô Hợp đồng liên quan)</td><td>Mất kết nối lúc nạp danh sách hợp đồng. Chọn lại khách hàng, hoặc tải lại trang.</td></tr>
    <tr><td>Bạn không có quyền thực hiện thao tác này.</td><td>Kỹ thuật viên mở trang tạo phiếu, hoặc trang sửa của một phiếu không giao cho mình.</td></tr>
</table>

<%@ include file="/jsp/guide/_bottom.jspf" %>
