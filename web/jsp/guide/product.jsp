<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%--
    Hướng dẫn sử dụng -- phân hệ Sản phẩm.

    Viết theo CODE hiện tại (ProductController, ProductDAO, AccessControl,
    AuthenticationFilter và các JSP trong jsp/technical/), không theo tài liệu
    cũ. Ảnh ở web/guide/product/ do tools/guide/capture.mjs chụp theo kịch bản
    tools/guide/shots/product.mjs -- số trong ol.guide-steps phải khớp số
    khoanh đỏ trên ảnh; sửa một bên thì soát lại bên kia.

    Chỉ dùng tập thẻ mô tả ở đầu css/guide.css: tools/guide/ dựng lại đúng nội
    dung này sang file Word.
--%>
<c:set var="guideTitle" value="Sản phẩm"/>
<c:set var="img" value="${pageContext.request.contextPath}/guide/product"/>
<%@ include file="/jsp/guide/_top.jspf" %>

<h1>Sản phẩm</h1>
<p class="lead">Danh mục thiết bị, sản phẩm POSTEF cung cấp: ảnh, catalogue và các hợp đồng đang dùng từng sản phẩm. Admin và Kỹ thuật quản lý; Sales xem để tư vấn và lập hợp đồng.</p>

<h2 id="tong-quan">1. Tổng quan</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Kỹ thuật</span><span class="role">Sales (chỉ xem)</span></div>
<p>Trên thanh bên trái, bấm <strong>Sản phẩm</strong>. Mỗi sản phẩm gồm:</p>
<ul>
    <li><strong>Mã sản phẩm</strong> dạng <code>SP-0006</code>: hệ thống tự cấp khi tạo, không sửa được.</li>
    <li><strong>Tên</strong> và <strong>Mô tả</strong> (tính năng, thông số kỹ thuật).</li>
    <li><strong>Danh mục</strong>: cây ba cấp, ví dụ <em>CNTT &amp; IOT › LoRa › Sensors</em>. Sản phẩm luôn nằm ở danh mục cuối của một nhánh.</li>
    <li><strong>Hình ảnh</strong>: một hoặc nhiều ảnh; ảnh đầu tiên là ảnh đại diện trên thẻ sản phẩm.</li>
    <li><strong>Catalogue</strong>: một hoặc nhiều tệp PDF giới thiệu sản phẩm.</li>
</ul>
<p>Sản phẩm <strong>không có giá</strong>. Khi lập hợp đồng, người lập chọn sản phẩm ở phần Hàng hoá và ghi số lượng, đơn vị (xem hướng dẫn Hợp đồng, mục 5.1).</p>
<table class="guide-table">
    <tr><th>Việc</th><th>Admin</th><th>Kỹ thuật</th><th>Sales</th></tr>
    <tr><td>Xem danh sách, tìm, xem chi tiết</td><td>Có</td><td>Có</td><td>Có</td></tr>
    <tr><td>Thêm, sửa, xoá sản phẩm</td><td>Có</td><td>Có</td><td>Không: các nút này không hiện</td></tr>
</table>

<h2 id="danh-sach">2. Xem và tìm sản phẩm</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Kỹ thuật</span><span class="role">Sales</span></div>
<p>Danh sách hiện 10 sản phẩm mỗi trang, sản phẩm thêm sau cùng đứng đầu.</p>
<figure class="guide-shot">
    <a href="${img}/01-danh-sach.png" target="_blank"><img src="${img}/01-danh-sach.png" alt="Danh sách sản phẩm"></a>
    <figcaption>Hình 1. Danh sách sản phẩm</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Danh mục sản phẩm</strong>: bấm tên một danh mục để chỉ xem sản phẩm của nó (<a href="#loc-danh-muc">mục 2.1</a>). Số trong ngoặc là tổng số sản phẩm của danh mục đó, cộng cả các danh mục con. <em>Tất cả sản phẩm</em> bỏ lọc.</li>
    <li><strong>Ô tìm kiếm</strong>: gõ mã (ví dụ <code>SP-0006</code>) hoặc một phần tên rồi nhấn Enter. Tìm trong danh mục đang lọc.</li>
    <li><strong>Thêm sản phẩm</strong> (<a href="#them-san-pham">mục 3</a>).</li>
    <li><strong>Thẻ sản phẩm</strong>: ảnh đại diện, nhãn danh mục, mã, tên và ngày cập nhật gần nhất. Bấm vào tên để mở trang chi tiết.</li>
    <li><strong>Ba nút trên thẻ</strong>: <i class="fa-regular fa-eye"></i> xem chi tiết, <i class="fa-solid fa-pen"></i> sửa (<a href="#sua">mục 5</a>), <i class="fa-solid fa-trash"></i> xoá (<a href="#xoa">mục 6</a>).</li>
    <li><strong>Phân trang</strong>, kèm số sản phẩm đang hiện trên tổng số.</li>
</ol>
<div class="guide-note">
    <p>Tài khoản Sales không thấy nút Thêm sản phẩm, sửa và xoá. Mở đường dẫn tới một sản phẩm đã bị xoá thì danh sách báo <em>Không tìm thấy sản phẩm này</em>.</p>
</div>

<h3 id="loc-danh-muc">2.1. Lọc theo danh mục</h3>
<p>Ví dụ dưới đây đang xem các cảm biến LoRa: <em>CNTT &amp; IOT › LoRa › Sensors</em>.</p>
<figure class="guide-shot">
    <a href="${img}/02-loc-danh-muc.png" target="_blank"><img src="${img}/02-loc-danh-muc.png" alt="Danh sách đang lọc theo danh mục Sensors"></a>
    <figcaption>Hình 2. Danh sách đang lọc theo một danh mục</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Danh mục đang lọc</strong> được tô xanh; các nhánh chứa nó tự mở sẵn.</li>
    <li><strong>Mũi tên</strong> bên phải một danh mục: mở hoặc thu các danh mục con. Bấm mũi tên chỉ để xem cây, chưa lọc gì.</li>
    <li>Nhãn <strong>Đang lọc theo danh mục</strong>: bấm <strong>×</strong> để bỏ lọc. Từ khoá tìm (nếu có) vẫn giữ.</li>
    <li>Dòng <strong>Hiển thị … trong tổng số …</strong>: số sản phẩm khớp với bộ lọc.</li>
</ol>
<div class="guide-note">
    <p>Lọc theo một nhóm lớn, ví dụ <em>CNTT &amp; IOT</em>, là xem sản phẩm của mọi danh mục con bên trong nhóm đó.</p>
</div>

<h2 id="them-san-pham">3. Thêm sản phẩm</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Kỹ thuật</span><span class="role no">Sales</span></div>
<p>Ở danh sách, bấm <strong>Thêm sản phẩm</strong>. Ô có dấu <span style="color:#e2536b">*</span> là bắt buộc.</p>
<figure class="guide-shot narrow">
    <a href="${img}/03-them.png" target="_blank"><img src="${img}/03-them.png" alt="Trang Thêm sản phẩm"></a>
    <figcaption>Hình 3. Trang Thêm sản phẩm (đang điền ví dụ)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tên sản phẩm</strong>.</li>
    <li><strong>Danh mục</strong>: ô chỉ liệt kê danh mục cuối, mỗi dòng ghi kèm cả nhánh, ví dụ <em>CNTT &amp; IOT › LoRa › Sensors</em>. Không chọn được nhóm lớn như <em>CNTT &amp; IOT</em>.</li>
    <li><strong>Mô tả</strong> (không bắt buộc): tính năng, thông số kỹ thuật. Xuống dòng được, trang chi tiết giữ nguyên các dòng.</li>
    <li><strong>Hình ảnh</strong> (không bắt buộc): bấm <strong>Chọn ảnh từ máy</strong>, chọn một hoặc nhiều ảnh JPG, PNG, GIF, WEBP, mỗi ảnh tối đa 20 MB. Ảnh xem trước hiện ngay bên dưới; bấm <strong>×</strong> trên ảnh để bỏ ảnh đó. Muốn thêm ảnh thì bấm chọn tiếp, ảnh đã chọn vẫn giữ.</li>
    <li><strong>Catalogue</strong> (không bắt buộc): bấm <strong>Chọn file catalogue</strong>, chọn một hoặc nhiều tệp PDF, mỗi tệp tối đa 20 MB. Mỗi tệp hiện tên và dung lượng; bấm <strong>×</strong> để bỏ.</li>
    <li>Bấm <strong>Tạo sản phẩm</strong>. Hệ thống cấp mã sản phẩm và mở trang chi tiết của sản phẩm vừa tạo.</li>
</ol>
<div class="guide-note">
    <p>Tổng dung lượng ảnh và catalogue chọn thêm trong một lần lưu tối đa 100 MB. Chọn một tệp quá 20 MB thì tệp đó không được thêm, và trang ghi tên tệp bị bỏ ngay dưới nút chọn.</p>
</div>

<h3 id="loi-them">3.1. Khi không lưu được</h3>
<p>Trước khi gửi, trang kiểm các ô bắt buộc và hiện chữ đỏ ngay dưới ô còn thiếu. Nếu hệ thống từ chối sau khi gửi, thông báo đỏ hiện ở đầu form:</p>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Không thêm tệp quá 20 MB: …</td><td>Tệp vừa chọn lớn hơn 20 MB nên bị bỏ. Chọn tệp nhỏ hơn (nén ảnh, tách catalogue).</td></tr>
    <tr><td>Tổng dung lượng ảnh và catalogue chọn thêm vượt 100 MB…</td><td>Bấm × bỏ bớt tệp, lưu phần còn lại, rồi mở trang Sửa thêm tiếp.</td></tr>
    <tr><td>Thông tin sản phẩm chưa hợp lệ…</td><td>Tên sản phẩm hoặc danh mục đang trống.</td></tr>
    <tr><td>Danh mục đã chọn không còn dùng được…</td><td>Danh mục vừa bị đổi hoặc xoá sau khi bạn mở form. Chọn lại danh mục.</td></tr>
    <tr><td>Ảnh sản phẩm chỉ nhận file JPG, PNG, GIF hoặc WEBP…</td><td>Có ảnh sai định dạng. Chọn lại ảnh.</td></tr>
    <tr><td>Catalogue chỉ nhận file PDF…</td><td>Có catalogue không phải PDF. Chuyển sang PDF rồi chọn lại.</td></tr>
    <tr><td>Không lưu được sản phẩm. Vui lòng thử lại.</td><td>Lỗi hệ thống. Thử lại; vẫn lỗi thì báo người quản trị hệ thống.</td></tr>
    <tr><td>Trang <em>Tệp tải lên quá lớn</em> (lỗi 413)</td><td>Tệp vượt giới hạn mà trang chưa chặn được. Chưa có gì được lưu: bấm <strong>Quay lại</strong>, chọn tệp nhỏ hơn rồi lưu lại.</td></tr>
</table>

<h2 id="chi-tiet">4. Xem chi tiết sản phẩm</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Kỹ thuật</span><span class="role">Sales</span></div>
<p>Bấm tên sản phẩm hoặc nút <i class="fa-regular fa-eye"></i> ở danh sách.</p>
<figure class="guide-shot narrow">
    <a href="${img}/04-chi-tiet.png" target="_blank"><img src="${img}/04-chi-tiet.png" alt="Trang chi tiết sản phẩm"></a>
    <figcaption>Hình 4. Trang chi tiết sản phẩm</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Đầu trang</strong>: ảnh đại diện, mã, tên, nhãn danh mục và thời điểm cập nhật lần cuối.</li>
    <li><strong>Sửa thông tin</strong> (<a href="#sua">mục 5</a>).</li>
    <li><strong>Xóa</strong> (<a href="#xoa">mục 6</a>). Hai nút này chỉ hiện với Admin và Kỹ thuật.</li>
    <li><strong>Hình ảnh</strong>: bấm một ảnh để mở ảnh gốc ở tab mới.</li>
    <li><strong>Catalogue</strong>: bấm tên tệp để mở ở tab mới, xem hoặc tải về.</li>
    <li><strong>Hợp đồng sử dụng sản phẩm này</strong>: các hợp đồng, kể cả phụ lục, có sản phẩm trong phần Hàng hoá, kèm khách hàng và trạng thái. Bấm mã hợp đồng để mở. Hợp đồng đã huỷ bản ghi không hiện ở đây.</li>
</ol>

<h2 id="sua">5. Sửa sản phẩm</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Kỹ thuật</span><span class="role no">Sales</span></div>
<p>Ở trang chi tiết, bấm <strong>Sửa thông tin</strong>; hoặc bấm <i class="fa-solid fa-pen"></i> trên thẻ ở danh sách.</p>
<figure class="guide-shot narrow">
    <a href="${img}/05-sua.png" target="_blank"><img src="${img}/05-sua.png" alt="Trang Cập nhật sản phẩm"></a>
    <figcaption>Hình 5. Trang Cập nhật sản phẩm</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mã sản phẩm</strong>: chỉ hiển thị, không sửa được.</li>
    <li><strong>Danh mục</strong>: như lúc thêm, chỉ chọn danh mục cuối. Sản phẩm cũ đang nằm ở một nhóm lớn thì nhóm đó vẫn có trong ô, nên để nguyên cũng lưu được.</li>
    <li><strong>Ảnh đang có</strong>: bấm <strong>×</strong> trên ảnh để gỡ ảnh đó.</li>
    <li><strong>Thêm ảnh mới</strong>: như lúc thêm sản phẩm.</li>
    <li><strong>Catalogue đang có</strong>: bấm <strong>×</strong> ở cuối dòng để gỡ tệp đó.</li>
    <li><strong>Thêm file catalogue mới</strong>: như lúc thêm sản phẩm.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>. Trang chi tiết mở lại với thông tin mới.</li>
</ol>
<div class="guide-warn">
    <p>Bấm <strong>×</strong> chỉ đánh dấu gỡ: ảnh hoặc catalogue chỉ thật sự bị gỡ khi bấm <strong>Lưu thay đổi</strong>. Bấm <strong>Hủy</strong> thì không gỡ gì.</p>
</div>
<p>Các thông báo lỗi giống khi thêm sản phẩm (<a href="#loi-them">mục 3.1</a>); riêng lỗi hệ thống ghi <em>Không lưu được thay đổi. Vui lòng thử lại.</em></p>

<h2 id="xoa">6. Xoá sản phẩm</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Kỹ thuật</span><span class="role no">Sales</span></div>
<figure class="guide-shot">
    <a href="${img}/06-xoa.png" target="_blank"><img src="${img}/06-xoa.png" alt="Hộp xác nhận xoá sản phẩm"></a>
    <figcaption>Hình 6. Hộp xác nhận xoá sản phẩm</figcaption>
</figure>
<ol class="guide-steps">
    <li>Ở trang chi tiết, bấm <strong>Xóa</strong>; hoặc bấm <i class="fa-solid fa-trash"></i> trên thẻ ở danh sách.</li>
    <li>Hộp xác nhận hiện tên sản phẩm. Bấm <strong>Xóa sản phẩm</strong> để xoá, <strong>Hủy</strong> để thôi.</li>
</ol>
<p>Sản phẩm đã xoá biến khỏi danh sách và không chọn được khi thêm hàng hoá cho hợp đồng. Phần mềm chưa có chức năng khôi phục sản phẩm đã xoá.</p>
<figure class="guide-shot">
    <a href="${img}/07-xoa-bi-chan.png" target="_blank"><img src="${img}/07-xoa-bi-chan.png" alt="Thông báo không xoá được sản phẩm đang có trong hợp đồng"></a>
    <figcaption>Hình 7. Sản phẩm đang có trong hợp đồng thì không xoá được</figcaption>
</figure>
<div class="guide-warn">
    <p><strong>Không xoá được sản phẩm đang nằm trong hợp đồng</strong>, kể cả hợp đồng đã hết hạn: trang báo <em>Không thể xoá: sản phẩm đang được dùng trong hợp đồng</em> và giữ nguyên sản phẩm, để các hợp đồng đó vẫn tra được hàng hoá. Xem các hợp đồng đó ở cuối trang chi tiết (<a href="#chi-tiet">mục 4</a>).</p>
</div>

<%@ include file="/jsp/guide/_bottom.jspf" %>
