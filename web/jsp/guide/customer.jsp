<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%--
    Hướng dẫn sử dụng -- phân hệ Khách hàng (khách hàng mua + nhà cung cấp).

    Viết theo CODE hiện tại (CustomerController, các JSP trong jsp/sale/), không
    theo tài liệu cũ. Ảnh ở web/guide/customer/ do tools/guide/capture.mjs chụp
    theo kịch bản tools/guide/shots/customer.mjs -- số trong ol.guide-steps phải
    khớp số khoanh đỏ trên ảnh; sửa một bên thì soát lại bên kia.

    Chỉ dùng tập thẻ mô tả ở đầu css/guide.css: tools/guide/ dựng lại đúng nội
    dung này sang file Word.
--%>
<c:set var="guideTitle" value="Khách hàng"/>
<c:set var="img" value="${pageContext.request.contextPath}/guide/customer"/>
<%@ include file="/jsp/guide/_top.jspf" %>

<h1>Khách hàng và Nhà cung cấp</h1>
<p class="lead">Quản lý hồ sơ các doanh nghiệp đối tác: khách hàng mua hàng của công ty và nhà cung cấp bán hàng cho công ty.</p>

<h2 id="tong-quan">1. Tổng quan</h2>
<p>Trên thanh bên trái, mục <strong>Khách hàng</strong> có hai danh sách:</p>
<ul>
    <li><strong>Khách hàng mua</strong>: doanh nghiệp mua hàng của mình. Mã tự sinh dạng <code>KH-0001</code>, <code>KH-0002</code>…</li>
    <li><strong>Nhà cung cấp</strong>: doanh nghiệp bán hàng cho mình. Mã tự sinh dạng <code>NCC-001</code>, <code>NCC-002</code>…</li>
</ul>
<p>Một công ty vừa mua vừa bán thì nằm ở cả hai danh sách: tạo ở một trang, sau đó thêm vai còn lại ở trang Sửa (xem <a href="#sua">mục 7</a>).</p>

<table class="guide-table">
    <tr><th>Việc</th><th>Admin</th><th>Sales</th><th>Kỹ thuật</th></tr>
    <tr><td>Xem danh sách, xem chi tiết, xuất Excel</td><td>Được</td><td>Được</td><td>Được</td></tr>
    <tr><td>Thêm, sửa, xoá, đánh giá xếp hạng</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
</table>
<div class="guide-note">
    <p>(*) Sales đã có <strong>cấp trên</strong> trong sơ đồ tổ chức thì chỉ được xem, không tạo/sửa/xoá được. Các nút đó sẽ không hiện ra.</p>
</div>

<h2 id="danh-sach">2. Xem và tìm khách hàng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm <strong>Khách hàng mua</strong> (hoặc <strong>Nhà cung cấp</strong>) trên thanh bên trái. Danh sách hiện 10 dòng mỗi trang.</p>
<figure class="guide-shot">
    <a href="${img}/01-danh-sach.png" target="_blank"><img src="${img}/01-danh-sach.png" alt="Danh sách khách hàng mua"></a>
    <figcaption>Hình 1. Danh sách khách hàng mua (đăng nhập bằng một tài khoản Sales)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ô tìm kiếm</strong>: gõ mã khách hàng, tên hoặc số điện thoại rồi nhấn Enter.</li>
    <li><strong>Phạm vi</strong>:
        <em>Của tôi</em> là những khách bạn (và cấp dưới của bạn, nếu có) phụ trách chính, cộng với khách nằm trong các tỉnh bạn phụ trách. Đây là mặc định của Sales có địa bàn.
        <em>Toàn chi nhánh</em> là tất cả khách hàng.
        Chọn <em>tên một nhân viên</em> để xem những khách người đó phụ trách chính.</li>
    <li><strong>Loại khách hàng</strong>: lọc theo loại (nhà mạng viễn thông, nhà thầu thi công, đại lý phân phối…).</li>
    <li><strong>Tỉnh</strong>: lọc theo một hoặc nhiều tỉnh (xem <a href="#loc-tinh">mục 2.1</a>).</li>
    <li><strong>Xuất Excel</strong>: tải danh sách đang lọc về máy (xem <a href="#xuat-excel">mục 9</a>).</li>
    <li><strong>Thêm khách hàng</strong>: mở trang tạo khách hàng mới (xem <a href="#them-khach-hang">mục 3</a>).</li>
    <li><strong>Cột Thao tác</strong> ở cuối mỗi dòng: <i class="fa-solid fa-eye"></i> xem chi tiết, <i class="fa-solid fa-pen"></i> sửa, <i class="fa-solid fa-trash"></i> xoá. Bảng rộng hơn màn hình nên cột này thường nằm khuất bên phải: kéo thanh cuộn ngang dưới bảng, hoặc bấm giữ chuột trên bảng rồi kéo sang trái. Bấm vào tên khách cũng mở trang chi tiết.</li>
</ol>
<div class="guide-note">
    <p>Dòng <strong>“Bạn phụ trách:”</strong> ngay dưới ô lọc liệt kê các tỉnh bạn đang được giao. Dải xanh <strong>“Đang xem…”</strong> cho biết danh sách đang hiển thị theo phạm vi nào. Nếu danh sách của bạn khác của đồng nghiệp thì xem dải này trước.</p>
    <p>Danh sách Nhà cung cấp không có ô lọc tỉnh, vì nhà cung cấp không chia theo địa bàn bán hàng.</p>
</div>

<h3 id="loc-tinh">2.1. Lọc theo nhiều tỉnh</h3>
<figure class="guide-shot">
    <a href="${img}/02-loc-tinh.png" target="_blank"><img src="${img}/02-loc-tinh.png" alt="Bảng chọn tỉnh"></a>
    <figcaption>Hình 2. Bảng chọn tỉnh</figcaption>
</figure>
<ol class="guide-steps">
    <li>Bấm ô <strong>Tỉnh</strong> để mở bảng, rồi tích các tỉnh muốn xem. Tỉnh bạn phụ trách được in đậm và có nhãn <em>của bạn</em>.</li>
    <li><strong>Chọn tất cả</strong> / <strong>Bỏ hết</strong> để tích hoặc bỏ tích toàn bộ. Bỏ hết nghĩa là không lọc theo tỉnh.</li>
    <li><strong>Địa bàn của tôi</strong>: chỉ tích đúng các tỉnh bạn phụ trách.</li>
    <li>Bấm <strong>Áp dụng</strong> để lọc.</li>
</ol>

<h2 id="them-khach-hang">3. Thêm khách hàng mua</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ở danh sách <strong>Khách hàng mua</strong>, bấm <strong>Thêm khách hàng</strong>. Ô có dấu <span style="color:#e2536b">*</span> là bắt buộc.</p>
<figure class="guide-shot narrow">
    <a href="${img}/03-them-khach-hang.png" target="_blank"><img src="${img}/03-them-khach-hang.png" alt="Trang Thêm khách hàng"></a>
    <figcaption>Hình 3. Trang Thêm khách hàng (tài khoản Sales đã được giao tỉnh)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Logo</strong> (không bắt buộc): bấm biểu tượng máy ảnh để chọn ảnh JPG, PNG, GIF hoặc WEBP.</li>
    <li><strong>Tên khách hàng</strong> và <strong>Mã số thuế</strong>. Mã số thuế <u>không sửa được</u> sau khi tạo, nên nhập cho đúng.</li>
    <li><strong>Loại khách hàng</strong> và <strong>Nhóm khách hàng</strong> (VIP, thân thiết, tiềm năng, thường).</li>
    <li><strong>Người phụ trách chính</strong>:
        <ul>
            <li>Sales <strong>đã được giao tỉnh</strong>: ô này khoá sẵn tên bạn, không đổi được.</li>
            <li>Admin, hoặc Sales <strong>chưa được giao tỉnh</strong>: chọn tỉnh ở bước 7 trước. Tỉnh đã có người phụ trách thì ô này tự điền tên người đó và khoá lại. Tỉnh chưa ai phụ trách thì tự chọn.</li>
        </ul></li>
    <li><strong>Người hỗ trợ</strong> (không bắt buộc). Danh sách tự bỏ đi chính người phụ trách chính và cấp trên trực tiếp của người đó, vì người hỗ trợ không được là hai người này.</li>
    <li><strong>Số điện thoại</strong> và <strong>Email</strong>. Số điện thoại bắt đầu bằng <code>0</code> hoặc <code>+84</code>, theo sau là 9–10 chữ số; được có dấu cách, dấu chấm hoặc gạch ngang (vd. <code>024 3822 1234</code>). Website và Ngày tham gia không bắt buộc; ngày tham gia không được ở tương lai.</li>
    <li><strong>Địa chỉ</strong>: chọn Tỉnh / Thành phố, chờ danh sách Xã / Phường nạp xong rồi chọn, sau đó nhập Địa chỉ chi tiết. Sales đã được giao tỉnh chỉ thấy các tỉnh mình phụ trách; nếu chỉ phụ trách một tỉnh thì ô tỉnh được chọn sẵn và khoá.</li>
    <li>Bấm <strong>Tạo khách hàng</strong>. Hệ thống tự sinh mã <code>KH-xxxx</code> rồi chuyển sang trang chi tiết của khách vừa tạo.</li>
</ol>
<div class="guide-warn">
    <p>Nếu một công ty đã có trong hệ thống (ví dụ đang là nhà cung cấp) thì <strong>không tạo mới</strong>. Mở hồ sơ công ty đó rồi tick thêm vai <em>Khách hàng mua</em> ở trang Sửa. Tạo mới sẽ bị chặn vì trùng mã số thuế, email hoặc số điện thoại.</p>
</div>

<h3 id="loi-them">3.1. Khi không lưu được</h3>
<p>Trước khi gửi, trang kiểm các ô bắt buộc và hiện chữ đỏ ngay dưới ô sai (Hình 4). Nếu hệ thống từ chối sau khi gửi thì một thông báo đỏ hiện ở đầu form:</p>
<figure class="guide-shot narrow">
    <a href="${img}/04-them-loi.png" target="_blank"><img src="${img}/04-them-loi.png" alt="Báo lỗi khi bỏ trống ô bắt buộc"></a>
    <figcaption>Hình 4. Bấm Tạo khi còn ô bắt buộc để trống</figcaption>
</figure>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Thông tin khách hàng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.</td><td>Còn ô bắt buộc để trống, số điện thoại hoặc email sai định dạng, ngày tham gia ở tương lai, hoặc người hỗ trợ trùng người phụ trách chính.</td></tr>
    <tr><td>Email này đã thuộc về một khách hàng khác.</td><td>Email, số điện thoại và mã số thuế không được trùng với bất kỳ doanh nghiệp nào khác. Kiểm tra lại, hoặc tìm công ty đó trong danh sách.</td></tr>
    <tr><td>Số điện thoại này đã thuộc về một khách hàng khác.</td><td>Như trên.</td></tr>
    <tr><td>Mã số thuế này đã được đăng ký cho một khách hàng khác.</td><td>Công ty đã có trong hệ thống: mở hồ sơ của họ và thêm vai ở trang Sửa thay vì tạo mới.</td></tr>
    <tr><td>Xã / phường đã chọn không thuộc các tỉnh bạn phụ trách.</td><td>Sales đã được giao tỉnh chỉ tạo được khách trong các tỉnh của mình. Khách ở tỉnh khác do người phụ trách tỉnh đó tạo.</td></tr>
    <tr><td>Người hỗ trợ không được là cấp trên trực tiếp của người phụ trách chính.</td><td>Chọn người hỗ trợ khác, hoặc để trống.</td></tr>
    <tr><td>Logo chỉ nhận file ảnh JPG, PNG, GIF hoặc WEBP.</td><td>Chọn lại file ảnh đúng định dạng.</td></tr>
</table>

<h2 id="them-nha-cung-cap">4. Thêm nhà cung cấp</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ở danh sách <strong>Nhà cung cấp</strong>, bấm <strong>Thêm nhà cung cấp</strong>. Các ô giống trang Thêm khách hàng, chỉ khác ở những điểm được khoanh:</p>
<figure class="guide-shot narrow">
    <a href="${img}/05-them-nha-cung-cap.png" target="_blank"><img src="${img}/05-them-nha-cung-cap.png" alt="Trang Thêm nhà cung cấp"></a>
    <figcaption>Hình 5. Trang Thêm nhà cung cấp</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Loại nhà cung cấp</strong>: nhà sản xuất, nhà nhập khẩu, nhà phân phối hoặc đơn vị dịch vụ.</li>
    <li><strong>Người phụ trách chính</strong>: Sales tạo thì nhà cung cấp đứng tên chính bạn (ô bị khoá); Admin tự chọn.</li>
    <li><strong>Tỉnh / Thành phố</strong>: chọn được cả 34 tỉnh, vì nhà cung cấp không chia theo địa bàn bán hàng.</li>
    <li>Bấm <strong>Tạo nhà cung cấp</strong>. Mã tự sinh dạng <code>NCC-xxx</code>.</li>
</ol>

<h2 id="chi-tiet">5. Xem chi tiết khách hàng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm vào tên khách hàng, hoặc nút <i class="fa-solid fa-eye"></i> ở danh sách.</p>
<figure class="guide-shot narrow">
    <a href="${img}/06-chi-tiet.png" target="_blank"><img src="${img}/06-chi-tiet.png" alt="Trang chi tiết khách hàng"></a>
    <figcaption>Hình 6. Trang chi tiết khách hàng</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Xếp hạng quan hệ</strong> hiện tại: Tốt, Cần theo dõi, Xấu hoặc Có nguy cơ rời bỏ.</li>
    <li><strong>Đánh giá lại xếp hạng</strong>: xem <a href="#danh-gia">mục 6</a>.</li>
    <li><strong>Sửa thông tin</strong>: xem <a href="#sua">mục 7</a>.</li>
    <li><strong>Xóa</strong>: xem <a href="#xoa">mục 8</a>.</li>
    <li><strong>Thông tin doanh nghiệp</strong>: mã, mã số thuế, liên lạc, địa chỉ, người phụ trách, người hỗ trợ.</li>
    <li><strong>Người liên hệ</strong>: bấm vào một người để xem đầy đủ thông tin. Danh sách này hiện chỉ xem được, trang không có chức năng thêm hay sửa người liên hệ.</li>
    <li><strong>Lịch sử đánh giá xếp hạng</strong>: mỗi lần đánh giá, ai đánh giá, ngày nào, kèm ghi chú.</li>
    <li><strong>Hoạt động gần đây</strong>: hai tab <em>Hợp đồng</em> và <em>Phiếu hỗ trợ kỹ thuật</em> của khách này.</li>
</ol>

<h2 id="danh-gia">6. Đánh giá lại xếp hạng quan hệ</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<figure class="guide-shot">
    <a href="${img}/07-danh-gia.png" target="_blank"><img src="${img}/07-danh-gia.png" alt="Hộp đánh giá xếp hạng"></a>
    <figcaption>Hình 7. Hộp đánh giá xếp hạng khách hàng</figcaption>
</figure>
<ol class="guide-steps">
    <li>Chọn <strong>xếp hạng quan hệ</strong>: Tốt, Cần theo dõi, Xấu hoặc Có nguy cơ rời bỏ.</li>
    <li>Ghi <strong>lý do</strong> (không bắt buộc, tối đa 255 ký tự).</li>
    <li>Bấm <strong>Lưu đánh giá</strong>. Xếp hạng mới hiện ngay ở đầu trang và được thêm vào Lịch sử đánh giá xếp hạng.</li>
</ol>
<div class="guide-note"><p>Xếp hạng do người dùng tự chọn; hệ thống không tự chấm điểm. Mỗi lần lưu là một dòng lịch sử mới, kể cả khi chọn lại đúng mức cũ.</p></div>

<h2 id="sua">7. Sửa thông tin khách hàng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ở trang chi tiết bấm <strong>Sửa thông tin</strong>, hoặc bấm nút <i class="fa-solid fa-pen"></i> ở danh sách.</p>
<figure class="guide-shot narrow">
    <a href="${img}/08-sua.png" target="_blank"><img src="${img}/08-sua.png" alt="Trang sửa khách hàng"></a>
    <figcaption>Hình 8. Trang sửa thông tin khách hàng</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mã khách hàng</strong> và <strong>Mã số thuế</strong> chỉ hiển thị, không sửa được.</li>
    <li><strong>Vai của khách hàng</strong>: tick cả <em>Khách hàng mua</em> và <em>Nhà cung cấp</em> nếu công ty vừa mua vừa bán. Phải còn ít nhất một vai.</li>
    <li><strong>Người phụ trách chính</strong> đi theo tỉnh của địa chỉ: đổi địa chỉ sang tỉnh đã có người phụ trách thì khách tự chuyển sang tên người đó.</li>
    <li><strong>Người hỗ trợ</strong>: cùng luật như lúc tạo. Nếu người đang chọn trở nên không hợp lệ thì ô tự bỏ chọn và báo ngay bên dưới.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>.</li>
</ol>

<h2 id="xoa">8. Xoá khách hàng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<figure class="guide-shot">
    <a href="${img}/09-xoa.png" target="_blank"><img src="${img}/09-xoa.png" alt="Hộp xác nhận xoá"></a>
    <figcaption>Hình 9. Hộp xác nhận xoá khách hàng</figcaption>
</figure>
<ol class="guide-steps">
    <li>Bấm <strong>Xóa</strong> ở trang chi tiết (hoặc nút <i class="fa-solid fa-trash"></i> ở danh sách).</li>
    <li>Kiểm lại tên khách hàng trong hộp xác nhận rồi bấm <strong>Xóa khách hàng</strong>. Bấm <strong>Hủy</strong> nếu đổi ý.</li>
</ol>
<div class="guide-warn">
    <p><strong>Không xoá được khách hàng còn hợp đồng đang hiệu lực.</strong> Khi đó trang báo như Hình 10; cần thanh lý hoặc kết thúc các hợp đồng đó trước.</p>
</div>
<figure class="guide-shot">
    <a href="${img}/10-xoa-bi-chan.png" target="_blank"><img src="${img}/10-xoa-bi-chan.png" alt="Không xoá được vì còn hợp đồng"></a>
    <figcaption>Hình 10. Thông báo khi khách còn hợp đồng đang hiệu lực</figcaption>
</figure>
<div class="guide-note"><p>Xoá là <strong>xoá mềm</strong>: khách biến khỏi mọi danh sách, nhưng dữ liệu vẫn được giữ lại để các hợp đồng, phiếu hỗ trợ cũ vẫn tra cứu được.</p></div>

<h2 id="xuat-excel">9. Xuất Excel</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<ol class="guide-steps">
    <li class="plain">Lọc danh sách như mong muốn (tìm kiếm, phạm vi, loại, tỉnh).</li>
    <li class="plain">Bấm <strong>Xuất Excel</strong> (số 5 trên Hình 1). Trình duyệt tải về một file Excel (<code>.xls</code>).</li>
</ol>
<p>File chứa <strong>toàn bộ</strong> khách khớp bộ lọc, không chỉ trang đang xem, và được xếp theo tỉnh. Các cột gồm: STT, Mã KH, Tên doanh nghiệp, Loại KH, Nhóm KH, MST, Email, SĐT, Website, Tỉnh/Thành phố, Địa chỉ, Người phụ trách chính, Người hỗ trợ, Ngày tham gia, Xếp hạng quan hệ. Khách chưa có địa chỉ ghi <em>Chưa xác định</em> ở cột tỉnh.</p>

<%@ include file="/jsp/guide/_bottom.jspf" %>
