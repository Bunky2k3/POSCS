<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%--
    Hướng dẫn sử dụng -- phân hệ Hợp đồng (hợp đồng bán, hợp đồng mua, phụ lục,
    bàn giao, tài liệu, kỳ thanh toán, nhập PDF).

    Viết theo CODE hiện tại (ContractController, ContractDAO, AccessControl và
    các JSP trong jsp/sale/), không theo tài liệu cũ. Ảnh ở web/guide/contract/
    do tools/guide/capture.mjs chụp theo kịch bản tools/guide/shots/contract.mjs
    -- số trong ol.guide-steps phải khớp số khoanh đỏ trên ảnh; sửa một bên thì
    soát lại bên kia.

    Chỉ dùng tập thẻ mô tả ở đầu css/guide.css: tools/guide/ dựng lại đúng nội
    dung này sang file Word.
--%>
<c:set var="guideTitle" value="Hợp đồng"/>
<c:set var="img" value="${pageContext.request.contextPath}/guide/contract"/>
<%@ include file="/jsp/guide/_top.jspf" %>

<h1>Hợp đồng</h1>
<p class="lead">Theo dõi hợp đồng bán (với khách hàng) và hợp đồng mua (với nhà cung cấp) từ lúc soạn thảo, ký, thực hiện cho tới khi thanh lý.</p>

<h2 id="tong-quan">1. Tổng quan</h2>
<p>Trên thanh bên trái, mục <strong>Hợp đồng</strong> có ba mục con:</p>
<ul>
    <li><strong>Hợp đồng bán</strong>: mình bán hàng, làm dịch vụ cho khách hàng.</li>
    <li><strong>Hợp đồng mua</strong>: mình mua hàng, thuê dịch vụ của nhà cung cấp.</li>
    <li><strong>Bàn giao xử lý</strong>: các hợp đồng đang chờ phòng Kế toán, Dự án, Kỹ thuật xử lý (xem <a href="#hang-doi-ban-giao">mục 8.1</a>).</li>
</ul>
<p>Hai loại hợp đồng dùng chung mọi màn hình và thao tác; chỉ khác ở mấy điểm sau:</p>
<table class="guide-table">
    <tr><th></th><th>Hợp đồng bán</th><th>Hợp đồng mua</th></tr>
    <tr><td>Đối tác</td><td>Khách hàng mua</td><td>Nhà cung cấp</td></tr>
    <tr><td>Loại hợp đồng</td><td>Cung cấp thiết bị, Thi công lắp đặt, Bảo trì bảo dưỡng</td><td>Mua thiết bị, Mua vật tư, Thuê thi công lắp đặt, Thuê bảo trì</td></tr>
    <tr><td>Lọc theo tỉnh</td><td>Có (tỉnh của khách hàng)</td><td>Không, vì nhà cung cấp không chia theo địa bàn</td></tr>
    <tr><td>Nhập từ file PDF</td><td>Có</td><td>Không</td></tr>
    <tr><td>Xuất PDF bản hợp đồng</td><td>Có</td><td>Không, vì mẫu in hiện chỉ dành cho hợp đồng bán</td></tr>
</table>
<p><strong>Mã hợp đồng</strong> là số ghi trên bản hợp đồng giấy, do người dùng nhập (ví dụ <code>01/2026/HĐKT-POSTEF</code>). Hệ thống không tự sinh mã, và không hợp đồng hay phụ lục nào được trùng mã.</p>

<h3 id="vong-doi">1.1. Vòng đời hợp đồng</h3>
<p>Mỗi hợp đồng đi qua các bước sau, <strong>chỉ đi tới, không quay lại</strong>:</p>
<table class="guide-table">
    <tr><th>Tiến độ</th><th>Nghĩa</th><th>Còn làm được gì</th></tr>
    <tr><td>Nháp</td><td>Vừa tạo, chưa ký.</td><td>Sửa mọi thông tin, điền thời hạn, thêm/gỡ hàng hoá. Xoá được (huỷ bản ghi).</td></tr>
    <tr><td>Đã ký</td><td>Hai bên đã ký; nội dung trở thành chứng cứ.</td><td>Chỉ còn đổi người phụ trách; thay đổi điều khoản phải lập <a href="#phu-luc">phụ lục</a>. Vẫn lập kỳ thanh toán, bàn giao, thêm tài liệu, nối bán – mua.</td></tr>
    <tr><td>Đã thanh lý</td><td>Hợp đồng đã xong, có biên bản thanh lý.</td><td>Không sửa được gì nữa, kể cả quản trị viên. Vẫn ghi nhận tiền về, thêm/huỷ tài liệu, nối bán – mua, đóng chặng bàn giao còn treo.</td></tr>
    <tr><td>Chấm dứt sớm</td><td>Dừng trước hạn.</td><td>Như Đã thanh lý.</td></tr>
</table>
<div class="guide-note">
    <p>Hợp đồng chỉ coi là <strong>xong</strong> khi đã thanh lý. Hết hạn theo lịch chưa phải là xong: một hợp đồng hết hạn mà chưa thanh lý là việc còn tồn, và trang chi tiết sẽ cảnh báo.</p>
</div>

<h3 id="hai-loai-trang-thai">1.2. Hai loại trạng thái</h3>
<p>Mỗi hợp đồng mang hai nhãn đứng cạnh nhau, trả lời hai câu hỏi khác nhau:</p>
<ul>
    <li><strong>Trạng thái theo lịch</strong> (nhãn nền màu, có chấm tròn): hệ thống tự tính từ ngày hiệu lực và ngày kết thúc.
        <em>Chưa hiệu lực</em> (chưa có đủ thời hạn, hoặc chưa tới ngày hiệu lực),
        <em>Đang hiệu lực</em>,
        <em>Sắp hết hạn</em> (còn 30 ngày trở xuống),
        <em>Đã hết hạn</em> (đã qua ngày kết thúc).</li>
    <li><strong>Tiến độ</strong> (nhãn viền nét đứt): do người bấm — <em>Nháp</em>, <em>Đã ký</em>, <em>Đã thanh lý</em>, <em>Chấm dứt sớm</em>.</li>
</ul>
<div class="guide-warn">
    <p>Hai nhãn <strong>không suy ra nhau</strong>. <em>Đã hết hạn</em> + <em>Đã ký</em> nghĩa là hết hạn mà chưa thanh lý. <em>Chưa hiệu lực</em> + <em>Đã ký</em> nghĩa là đã ký nhưng chưa tới ngày hiệu lực. Đọc cả hai nhãn trước khi kết luận về một hợp đồng.</p>
</div>

<h3 id="quyen">1.3. Ai làm được gì</h3>
<table class="guide-table">
    <tr><th>Việc</th><th>Admin</th><th>Sales</th><th>Kỹ thuật</th></tr>
    <tr><td>Xem danh sách, chi tiết, hàng đợi bàn giao; xuất Excel, xuất PDF</td><td>Được</td><td>Được</td><td>Được</td></tr>
    <tr><td>Tạo hợp đồng, nhập PDF, sửa bản nháp, lập phụ lục</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
    <tr><td>Ký hợp đồng</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
    <tr><td>Kỳ thanh toán, bàn giao, tài liệu, nối bán – mua, thanh lý, chấm dứt sớm</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
    <tr><td>Xoá bản nháp</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
    <tr><td>Huỷ bản ghi hợp đồng đã ký, chữa sai sót nhập liệu</td><td>Được</td><td>Không</td><td>Không</td></tr>
    <tr><td>Báo “Đã xử lý xong” một chặng bàn giao</td><td>Mọi phòng</td><td>Nếu thuộc phòng nhận</td><td>Nếu thuộc phòng nhận</td></tr>
</table>
<div class="guide-note">
    <p>(*) Sales đã có <strong>cấp trên</strong> trong sơ đồ tổ chức thì chỉ được xem: tạo, sửa và ký thuộc về cấp trên. Các nút không dùng được sẽ không hiện ra.</p>
    <p>Việc báo xong một chặng bàn giao xét theo <strong>phòng ban</strong> của người dùng, không theo vai: nhân viên phòng Kỹ thuật báo xong được các chặng giao cho phòng Kỹ thuật.</p>
</div>

<h2 id="danh-sach">2. Xem và tìm hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm <strong>Hợp đồng bán</strong> (hoặc <strong>Hợp đồng mua</strong>) trên thanh bên trái. Danh sách hiện 10 dòng mỗi trang, hợp đồng tạo sau cùng đứng đầu.</p>
<figure class="guide-shot">
    <a href="${img}/01-danh-sach.png" target="_blank"><img src="${img}/01-danh-sach.png" alt="Đầu trang danh sách hợp đồng bán"></a>
    <figcaption>Hình 1. Đầu trang danh sách hợp đồng bán (tài khoản Sales đã được giao tỉnh)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Bốn ô trạng thái theo lịch</strong>: số hợp đồng ở mỗi trạng thái. Bấm một ô để chỉ xem trạng thái đó; bấm lại ô đang sáng để bỏ lọc.</li>
    <li><strong>Ô tìm kiếm</strong>: gõ mã hợp đồng, tiêu đề hoặc tên khách hàng rồi nhấn Enter.</li>
    <li><strong>Phạm vi</strong>:
        <em>Của tôi</em> là những hợp đồng bạn (và cấp dưới của bạn, nếu có) phụ trách, cộng với hợp đồng của khách hàng thuộc các tỉnh bạn phụ trách. Đây là mặc định của Sales.
        <em>Toàn chi nhánh</em> là tất cả hợp đồng — mặc định của Admin và Kỹ thuật.</li>
    <li><strong>Tiến độ</strong>: Nháp (chưa ký), Đã ký, Đã thanh lý, Chấm dứt sớm.</li>
    <li><strong>Chỉ hợp đồng gốc</strong>: ẩn các phụ lục (mặc định phụ lục hiện thành dòng riêng). <strong>Đang bàn giao</strong>: chỉ những hợp đồng còn chờ ở một phòng nào đó.</li>
    <li><strong>Lọc thêm</strong>: loại hợp đồng, tỉnh, ngày ký, đang chờ ở phòng nào (xem <a href="#loc-them">mục 2.1</a>).</li>
    <li><strong>Dải “Đang xem”</strong>: danh sách đang thu hẹp tới đâu — của ai, và còn hiệu lực trong khoảng nào. Bấm <em>Xem mọi thời điểm</em> (hoặc <em>Chỉ tháng này</em>) để đổi.</li>
    <li><strong>Xuất Excel</strong>: tải danh sách đang lọc về máy (xem <a href="#xuat-file">mục 15</a>).</li>
    <li><strong>Nhập PDF</strong>: tạo hợp đồng từ file PDF điền theo mẫu (xem <a href="#nhap-pdf">mục 14</a>).</li>
    <li><strong>Tạo hợp đồng</strong> (xem <a href="#tao-hop-dong">mục 3</a>).</li>
</ol>
<div class="guide-warn">
    <p><strong>Mặc định danh sách chỉ hiện hợp đồng còn hiệu lực trong tháng hiện tại</strong>, với mọi vai. Bản nháp chưa có thời hạn vẫn hiện, nhưng hợp đồng đã hết hạn từ tháng trước, hoặc bắt đầu hiệu lực từ tháng sau, sẽ không hiện cho tới khi bấm <em>Xem mọi thời điểm</em> (số 7). Không tìm thấy một hợp đồng thì xem dải này trước.</p>
</div>
<div class="guide-note">
    <p>Đổi một ô lọc là danh sách tải lại ngay. Các lọc đang bật hiện thành nhãn nhỏ ngay dưới thanh lọc: bấm <strong>×</strong> trên nhãn để bỏ từng lọc, hoặc <strong>Xoá tất cả</strong>. Dòng <strong>“Bạn phụ trách:”</strong> liệt kê các tỉnh bạn được giao.</p>
</div>

<h3 id="loc-them">2.1. Lọc thêm</h3>
<p>Bấm <strong>Lọc thêm</strong> (số 6 ở Hình 1) để mở thêm một hàng ô lọc. Nút này hiện số lọc đang bật trong hàng đó, và hàng tự mở sẵn khi có lọc đang bật.</p>
<figure class="guide-shot">
    <a href="${img}/02-loc-them.png" target="_blank"><img src="${img}/02-loc-them.png" alt="Hàng Lọc thêm và bảng chọn kỳ"></a>
    <figcaption>Hình 2. Hàng Lọc thêm, đang mở bảng chọn ngày ký</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Loại hợp đồng</strong>.</li>
    <li><strong>Tỉnh</strong>: tích một hoặc nhiều tỉnh rồi bấm Áp dụng — cách dùng như ở danh sách khách hàng. Tỉnh của hợp đồng là tỉnh của khách hàng đứng tên. Danh sách hợp đồng mua không có ô này.</li>
    <li><strong>Ngày ký</strong>: chọn năm bằng hai mũi tên, rồi bấm <em>Cả năm</em>, một quý hoặc một tháng. <em>Mọi thời điểm</em> là bỏ lọc.</li>
    <li><strong>Đang chờ ở phòng</strong>: chỉ những hợp đồng còn chờ Kế toán, Dự án hoặc Kỹ thuật xử lý.</li>
</ol>
<div class="guide-note">
    <p>Lọc theo ngày ký thì bản nháp (chưa ký) không hiện. Ô này khác dải “Đang xem” (số 7 ở Hình 1): ô này lọc theo <strong>ngày ký</strong>, còn dải kia lọc theo <strong>thời hạn còn hiệu lực</strong>.</p>
</div>

<h3 id="doc-dong">2.2. Đọc một dòng của danh sách</h3>
<figure class="guide-shot">
    <a href="${img}/03-doc-dong.png" target="_blank"><img src="${img}/03-doc-dong.png" alt="Bảng danh sách hợp đồng"></a>
    <figcaption>Hình 3. Bảng danh sách (phạm vi Toàn chi nhánh, mọi thời điểm)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Nhãn “PL của …”</strong>: dòng này là phụ lục của hợp đồng mang mã đó. Dòng chữ nhỏ dưới tiêu đề còn cho biết tỉnh và tên khách hàng.</li>
    <li><strong>Thời hạn</strong>: ngày ký → ngày kết thúc. <em>chưa ký</em>, <em>chưa chốt</em> nghĩa là chưa có ngày đó.</li>
    <li><strong>Trạng thái</strong> theo lịch. Ở dòng cuối, hợp đồng đã hết hạn…</li>
    <li>…nhưng <strong>Tiến độ</strong> vẫn là <em>Đã ký</em>: hết hạn mà chưa thanh lý (xem <a href="#hai-loai-trang-thai">mục 1.2</a>).</li>
    <li><strong>“Chờ … · N ngày”</strong>: hợp đồng đang nằm ở những phòng nào, bao lâu rồi. Chữ chuyển vàng nâu từ 7 ngày, đỏ từ 14 ngày.</li>
    <li><strong>Thao tác</strong>: <i class="fa-regular fa-eye"></i> xem chi tiết, <i class="fa-solid fa-pen"></i> mở trang Quản lý (chỉ người có quyền sửa mới thấy). Bảng rộng hơn màn hình nên cột này thường nằm khuất bên phải: kéo thanh cuộn ngang dưới bảng, hoặc giữ chuột trên bảng rồi kéo sang trái. Bấm vào tiêu đề cũng mở trang chi tiết.</li>
</ol>
<div class="guide-note"><p>Danh sách không có nút xoá. Xoá bản nháp và huỷ bản ghi nằm ở trang Quản lý (xem <a href="#huy-ban-ghi">mục 13</a>).</p></div>

<h2 id="tao-hop-dong">3. Tạo hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ở danh sách <strong>Hợp đồng bán</strong> (hoặc <strong>Hợp đồng mua</strong>), bấm <strong>Tạo hợp đồng</strong>. Ô có dấu <span style="color:#e2536b">*</span> là bắt buộc.</p>
<figure class="guide-shot narrow">
    <a href="${img}/04-tao-hop-dong.png" target="_blank"><img src="${img}/04-tao-hop-dong.png" alt="Trang Tạo hợp đồng"></a>
    <figcaption>Hình 4. Trang Tạo hợp đồng</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mã hợp đồng</strong>: nhập đúng số ghi trên bản hợp đồng giấy. Không được trùng với hợp đồng hay phụ lục nào khác.</li>
    <li><strong>Tiêu đề hợp đồng</strong>.</li>
    <li><strong>Khách hàng</strong>: hợp đồng bán chỉ chọn được khách hàng mua; hợp đồng mua chỉ chọn được nhà cung cấp (ô vẫn mang nhãn “Khách hàng”). Đối tác chưa có trong danh sách thì tạo ở phân hệ Khách hàng trước, hoặc tick thêm vai còn thiếu ở trang Sửa của họ.</li>
    <li><strong>Người phụ trách</strong>: chọn một nhân viên Sales.</li>
    <li><strong>Loại hợp đồng</strong>: bộ loại khác nhau giữa hợp đồng bán và mua (xem bảng ở <a href="#tong-quan">mục 1</a>).</li>
    <li><strong>Thông tin ký kết</strong> (không bắt buộc): người ký hai bên và chức vụ, căn cứ uỷ quyền (chỉ khi người ký không phải đại diện pháp luật), nơi ký. Nên điền đủ trước khi ký.</li>
    <li><strong>Giá trị hợp đồng</strong> (VNĐ): tổng giá trị theo điều khoản. Gõ số, hệ thống tự chèn dấu chấm và đọc thành chữ ngay bên dưới. Để trống nếu chưa chốt giá.</li>
    <li>Bấm <strong>Tạo hợp đồng</strong>. Trang chi tiết của hợp đồng vừa tạo mở ra.</li>
</ol>
<div class="guide-note">
    <p>Form tạo <strong>không có ô ngày nào</strong>, và hợp đồng mới luôn ở trạng thái <strong>Nháp</strong>. Thời hạn (ngày hiệu lực, ngày kết thúc) điền ở trang Quản lý khi đã chốt với khách (<a href="#quan-ly">mục 5</a>); ngày ký do hệ thống ghi lúc bấm Ký (<a href="#ky">mục 6</a>). Hàng hoá cũng thêm ở trang Quản lý.</p>
</div>

<h3 id="loi-tao">3.1. Khi không lưu được</h3>
<p>Trước khi gửi, trang kiểm các ô bắt buộc và hiện chữ đỏ ngay dưới ô còn trống. Nếu hệ thống từ chối sau khi gửi, thông báo đỏ hiện ở đầu form:</p>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Thông tin hợp đồng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.</td><td>Còn ô bắt buộc để trống, hoặc đối tác vừa chọn không còn giữ đúng vai (khách hàng mua cho hợp đồng bán, nhà cung cấp cho hợp đồng mua).</td></tr>
    <tr><td>Mã hợp đồng này đã có hợp đồng khác dùng. Kiểm lại số trên bản giấy hoặc nhập mã khác.</td><td>Mã trùng với một hợp đồng hoặc phụ lục đã có. Tìm mã đó ở danh sách (nhớ bấm <em>Xem mọi thời điểm</em>).</td></tr>
    <tr><td>Không lưu được hợp đồng. Vui lòng thử lại.</td><td>Lỗi hệ thống. Thử lại; vẫn lỗi thì báo quản trị viên.</td></tr>
</table>

<h2 id="chi-tiet">4. Xem chi tiết hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm vào tiêu đề hợp đồng, hoặc nút <i class="fa-regular fa-eye"></i> ở danh sách. Trang này <strong>chỉ để xem</strong>: mọi thao tác làm thay đổi hợp đồng nằm ở trang Quản lý (<a href="#quan-ly">mục 5</a>).</p>
<figure class="guide-shot narrow">
    <a href="${img}/05-chi-tiet.png" target="_blank"><img src="${img}/05-chi-tiet.png" alt="Trang chi tiết hợp đồng"></a>
    <figcaption>Hình 5. Trang chi tiết của một hợp đồng đã ký nhưng chưa tới ngày hiệu lực</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Hai nhãn</strong>: trạng thái theo lịch và tiến độ (<a href="#hai-loai-trang-thai">mục 1.2</a>).</li>
    <li><strong>Mở bản PDF</strong>: mở bản hợp đồng đã ký ở tab mới. Nút chỉ hiện khi tab Tài liệu có một tài liệu loại <em>Hợp đồng đã ký</em> (<a href="#tai-lieu">mục 9</a>).</li>
    <li><strong>Xuất PDF</strong>: tải về bản hợp đồng in theo mẫu của công ty (<a href="#xuat-file">mục 15</a>). Chỉ có ở hợp đồng bán.</li>
    <li><strong>Quản lý hợp đồng</strong>: sang trang có các nút thao tác. Chỉ người có quyền sửa mới thấy nút này.</li>
    <li><strong>Tiến trình hợp đồng</strong>: Soạn thảo → Ký hợp đồng → Thanh lý (hoặc Chấm dứt sớm). Mỗi bước đã qua ghi ngày và người làm; bước thanh lý ghi kèm căn cứ.</li>
    <li><strong>Thông tin hợp đồng</strong>: mã, loại, khách hàng (bấm để mở hồ sơ khách), người phụ trách, chiều mua/bán, người ký, nơi ký, giá trị. Thẻ này luôn hiện, dù đang đứng ở tab nào.</li>
    <li><strong>Bảy tab</strong>: Kỳ thanh toán, Bàn giao, Hàng hoá, Tài liệu, Phụ lục, Nối bán – mua, Nhật ký. Số trong ngoặc là số dòng đang có. Trang nhớ tab bạn mở gần nhất.</li>
</ol>
<div class="guide-note">
    <p>Thời hạn (ngày hiệu lực, ngày kết thúc) không hiện ở trang này: xem ở cột <em>Thời hạn</em> của danh sách, hoặc ở trang Quản lý. Hợp đồng hết hạn mà chưa thanh lý thì thanh tiến trình có dải cảnh báo màu vàng.</p>
</div>

<h3 id="nhat-ky">4.1. Nhật ký thay đổi</h3>
<p>Tab <strong>Nhật ký</strong> ghi lại mọi thay đổi của hợp đồng, mới nhất ở trên: ai làm, lúc nào, đổi gì.</p>
<figure class="guide-shot">
    <a href="${img}/06-nhat-ky.png" target="_blank"><img src="${img}/06-nhat-ky.png" alt="Tab Nhật ký"></a>
    <figcaption>Hình 6. Nhật ký của một hợp đồng đã thanh lý</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mốc tiến trình</strong> (chữ đậm, chấm xanh): Ký, Thanh lý, Chấm dứt sớm, kèm trạng thái trước → sau.</li>
    <li><strong>Dòng thao tác thường</strong>: sửa thông tin, thêm/gỡ hàng hoá, lập kỳ, ghi nhận đã thu, bàn giao, tài liệu, nối hợp đồng…</li>
    <li><strong>Dòng có dấu ngoặc kép</strong>: chữ do người dùng tự nhập — căn cứ thanh lý, lý do huỷ, ghi chú bàn giao — tách khỏi câu do hệ thống ghi.</li>
</ol>
<div class="guide-note"><p>Nhật ký không sửa, không xoá được. Bấm Lưu mà không đổi gì thì không sinh dòng mới.</p></div>

<h2 id="quan-ly">5. Trang Quản lý hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ở trang chi tiết bấm <strong>Quản lý hợp đồng</strong>, hoặc bấm <i class="fa-solid fa-pen"></i> ở danh sách. Mọi thao tác làm thay đổi hợp đồng đều ở trang này: phần trên là form thông tin, phần dưới là các tab thao tác. Hình 7 là một bản nháp còn đang đàm phán, chưa có thời hạn và giá trị.</p>
<figure class="guide-shot narrow">
    <a href="${img}/07-quan-ly.png" target="_blank"><img src="${img}/07-quan-ly.png" alt="Trang Quản lý của một bản nháp"></a>
    <figcaption>Hình 7. Trang Quản lý của một bản nháp chưa chốt thời hạn</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Thông tin chung</strong> và <strong>thông tin ký kết</strong>: khi còn Nháp sửa được hết — mã, tiêu đề, khách hàng, người phụ trách, loại, người ký.</li>
    <li><strong>Giá trị hợp đồng</strong>.</li>
    <li><strong>Ngày hiệu lực</strong> và <strong>ngày kết thúc</strong>: điền khi đã chốt với khách. Phải có đủ hai ngày thì mới ký được; ngày hiệu lực không được sau ngày kết thúc.</li>
    <li><strong>Ngày ký</strong>: chỉ để xem — hệ thống ghi vào lúc bấm Ký.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>. Trang chi tiết mở ra.</li>
    <li><strong>Các tab thao tác</strong>: Bước tiến trình (mục <a href="#ky">6</a>, <a href="#thanh-ly">12</a>, <a href="#huy-ban-ghi">13</a>), Kỳ thanh toán (<a href="#ky-thanh-toan">mục 7</a>), Bàn giao (<a href="#ban-giao">mục 8</a>), Hàng hoá (<a href="#hang-hoa">mục 5.1</a>), Tài liệu (<a href="#tai-lieu">mục 9</a>), Phụ lục (<a href="#phu-luc">mục 10</a>), Nối bán – mua (<a href="#noi-ban-mua">mục 11</a>).</li>
</ol>
<div class="guide-note">
    <p>Tab chỉ hiện khi có việc để làm: tab Phụ lục chỉ có ở hợp đồng đã ký (hoặc đã có phụ lục); trên một phụ lục không có tab Phụ lục và Nối bán – mua; hợp đồng đã thanh lý không còn nút Ký, Thanh lý. Các thao tác trong tab ghi ngay, không chờ bấm Lưu thay đổi; xong thì trang tải lại và mở đúng tab vừa dùng.</p>
</div>

<h3 id="hang-hoa">5.1. Hàng hoá</h3>
<p>Mở tab <strong>Hàng hoá</strong> ở trang Quản lý.</p>
<figure class="guide-shot">
    <a href="${img}/08-hang-hoa.png" target="_blank"><img src="${img}/08-hang-hoa.png" alt="Tab Hàng hoá của một bản nháp"></a>
    <figcaption>Hình 8. Thêm hàng hoá vào một bản nháp</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Sản phẩm</strong>: chọn trong danh mục Sản phẩm.</li>
    <li><strong>Số lượng</strong>: số nguyên lớn hơn 0 (viết <code>1.000</code> cũng được).</li>
    <li><strong>Đơn vị</strong>: để trống sẽ là “Cái”.</li>
    <li><strong>Ghi chú</strong> (không bắt buộc).</li>
    <li>Bấm <strong>Thêm</strong>.</li>
    <li>Nút <strong>×</strong> cuối dòng: gỡ hàng hoá đó (có hộp xác nhận).</li>
</ol>
<div class="guide-warn">
    <p>Hàng hoá <strong>chỉ thêm/gỡ được khi hợp đồng còn là Nháp</strong>. Ký rồi thì hàng hoá là nội dung hợp đồng: thay đổi phải lập <a href="#phu-luc">phụ lục</a>. Hệ thống không lưu đơn giá từng dòng — tiền của hợp đồng là một con số tổng ở ô Giá trị hợp đồng.</p>
</div>

<h2 id="ky">6. Ký hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ký là một bước riêng: tạo hợp đồng chưa phải là ký. Ở trang Quản lý, mở tab <strong>Bước tiến trình</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/09-buoc-tien-trinh.png" target="_blank"><img src="${img}/09-buoc-tien-trinh.png" alt="Tab Bước tiến trình của một bản nháp"></a>
    <figcaption>Hình 9. Tab Bước tiến trình của một bản nháp còn thiếu thời hạn</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ký hợp đồng</strong>.</li>
    <li><strong>Huỷ bản ghi</strong>: xoá một bản nháp tạo nhầm (xem <a href="#huy-ban-ghi">mục 13</a>).</li>
    <li>Dòng nhắc <strong>còn thiếu ngày</strong>: bản nháp chưa có đủ ngày hiệu lực và ngày kết thúc thì bấm Ký sẽ bị từ chối. Điền hai ngày ở form phía trên (số 3 ở Hình 7), bấm Lưu thay đổi, rồi quay lại tab này.</li>
</ol>
<p>Bấm <strong>Ký hợp đồng</strong>, đọc hộp xác nhận:</p>
<figure class="guide-shot narrow">
    <a href="${img}/10-ky.png" target="_blank"><img src="${img}/10-ky.png" alt="Hộp xác nhận ký hợp đồng"></a>
    <figcaption>Hình 10. Hộp xác nhận ký</figcaption>
</figure>
<ol class="guide-steps">
    <li>Bấm <strong>Xác nhận</strong>. Hợp đồng chuyển sang <em>Đã ký</em>, ngày ký là ngày hôm nay, và trang chi tiết mở ra.</li>
</ol>
<div class="guide-note">
    <p><strong>Ai ký được:</strong> Admin, và Sales chưa có cấp trên trong sơ đồ tổ chức. Nhân viên có cấp trên không tự ký được — việc ký thuộc về cấp trên.</p>
    <p>Hợp đồng nhập từ file PDF đã mang sẵn ngày ký ghi trong file: bấm Ký thì giữ nguyên ngày đó.</p>
</div>
<div class="guide-warn">
    <p>Ký rồi thì <strong>không quay lại bản nháp được</strong>. Từ lúc này nội dung hợp đồng là chứng cứ: mọi điều khoản bị khoá (<a href="#sau-khi-ky">mục 6.1</a>).</p>
</div>

<h3 id="sau-khi-ky">6.1. Sau khi ký: điều khoản bị khoá</h3>
<figure class="guide-shot narrow">
    <a href="${img}/11-da-ky.png" target="_blank"><img src="${img}/11-da-ky.png" alt="Form của một hợp đồng đã ký"></a>
    <figcaption>Hình 11. Form thông tin của một hợp đồng đã ký (tài khoản Admin)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Dải vàng</strong> báo điều khoản đã khoá.</li>
    <li><strong>Người phụ trách</strong>: ô duy nhất còn sửa được, dùng khi đổi người theo dõi hợp đồng.</li>
    <li><strong>Các ô mờ</strong>: đã khoá, không sửa được. Muốn đổi điều khoản — thêm hàng, gia hạn, đổi giá trị — thì lập <a href="#phu-luc">phụ lục</a>.</li>
    <li><strong>Chữa sai sót nhập liệu</strong>: chỉ Admin thấy nút này (<a href="#chua-sai-sot">mục 6.2</a>).</li>
    <li><strong>Lưu thay đổi</strong>.</li>
</ol>

<h3 id="chua-sai-sot">6.2. Chữa sai sót nhập liệu</h3>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Dùng khi dữ liệu trên hệ thống gõ sai so với chính bản giấy đã ký — nhầm một chữ số trong mã, chọn nhầm khách hàng. Không dùng để thay đổi điều khoản: việc đó là phụ lục. Bấm <strong>Chữa sai sót nhập liệu</strong> (số 4 ở Hình 11): các ô mở lại để sửa và ô Lý do hiện ra.</p>
<figure class="guide-shot">
    <a href="${img}/12-chua-sai-sot.png" target="_blank"><img src="${img}/12-chua-sai-sot.png" alt="Chế độ chữa sai sót nhập liệu"></a>
    <figcaption>Hình 12. Chế độ chữa sai sót nhập liệu</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Lý do</strong> (bắt buộc): sai ở đâu, đối chiếu với bản giấy nào.</li>
    <li>Sửa ô sai rồi bấm <strong>Lưu thay đổi</strong>.</li>
</ol>
<div class="guide-note">
    <p>Thay đổi được ghi vào Nhật ký dưới tên bạn, kèm lý do. Ngày ký vẫn không sửa được. Muốn thoát chế độ này mà không lưu thì tải lại trang. Hợp đồng đã thanh lý hoặc chấm dứt thì không chữa được nữa.</p>
</div>

<h2 id="ky-thanh-toan">7. Kỳ thanh toán</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Lịch thu tiền của hợp đồng: mỗi kỳ là một khoản tiền có ngày đến hạn. Ở trang Quản lý, mở tab <strong>Kỳ thanh toán</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/13-thanh-toan.png" target="_blank"><img src="${img}/13-thanh-toan.png" alt="Tab Kỳ thanh toán ở trang Quản lý"></a>
    <figcaption>Hình 13. Lập kỳ và ghi nhận tiền về</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Số tiền</strong> (VNĐ): lớn hơn 0.</li>
    <li><strong>Đến hạn</strong>.</li>
    <li><strong>Ngày đã thu</strong>: chỉ điền khi tiền của kỳ đó đã về từ trước.</li>
    <li>Bấm <strong>Lập kỳ</strong>.</li>
    <li><strong>Đã thu</strong>: bấm khi tiền của kỳ về. Ngày thu ghi là ngày hôm nay; cần ghi ngày khác thì xoá kỳ đó và lập lại kèm Ngày đã thu (số 3).</li>
    <li>Nút <strong>×</strong>: xoá một kỳ lập nhầm (có hộp xác nhận).</li>
</ol>
<div class="guide-note">
    <p>Lập và xoá kỳ được cho tới khi hợp đồng thanh lý hoặc chấm dứt. Sau đó vẫn bấm <strong>Đã thu</strong> được cho các kỳ đã lập — tiền bảo hành giữ lại thường về sau thanh lý. Mọi thao tác đều ghi vào Nhật ký.</p>
</div>

<h3 id="cong-no">7.1. Theo dõi công nợ</h3>
<p>Ở trang chi tiết, tab <strong>Kỳ thanh toán</strong> có thêm ba con số và một phép đối chiếu.</p>
<figure class="guide-shot">
    <a href="${img}/14-cong-no.png" target="_blank"><img src="${img}/14-cong-no.png" alt="Tab Kỳ thanh toán ở trang chi tiết"></a>
    <figcaption>Hình 14. Kỳ thanh toán ở trang chi tiết, khi tổng các kỳ chưa khớp giá trị hợp đồng</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tổng các kỳ đã lập</strong>, <strong>Đã thu</strong>, <strong>Còn phải thu</strong>.</li>
    <li><strong>Cảnh báo không khớp</strong>: tổng các kỳ khác giá trị hợp đồng — có thể còn kỳ chưa lập. Hợp đồng có phụ lục thì so cả cụm: giá trị hiện hành (đã cộng các phụ lục đã ký) với tổng các kỳ của hợp đồng gốc và các phụ lục.</li>
    <li><strong>Bảng các kỳ</strong>: <em>Chưa thu</em> hoặc <em>Đã thu</em> kèm ngày.</li>
</ol>

<h2 id="ban-giao">8. Bàn giao cho các phòng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Chuyển hợp đồng xuống các phòng liên quan để họ xử lý phần việc của mình — Kế toán kiểm điều khoản thanh toán, Dự án lên kế hoạch thực hiện… Làm lúc nào cũng được, trước hay sau khi ký. Ở trang Quản lý, mở tab <strong>Bàn giao</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/15-ban-giao.png" target="_blank"><img src="${img}/15-ban-giao.png" alt="Tab Bàn giao ở trang Quản lý"></a>
    <figcaption>Hình 15. Tab Bàn giao: một phòng đã xong, một phòng đang chờ</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Các chặng đã bàn giao</strong>: mỗi phòng một dòng — <em>Đang chờ · N ngày</em>, hoặc <em>Xong sau N ngày</em> kèm người xác nhận và ghi chú của phòng đó.</li>
    <li><strong>Bàn giao cho phòng</strong>: Kế toán và Dự án được tích sẵn; tích thêm Kỹ thuật nếu cần, bỏ tích phòng không liên quan.</li>
    <li><strong>Dặn phòng nhận</strong> (không bắt buộc).</li>
    <li>Bấm <strong>Bàn giao</strong>. Mỗi phòng đã tích được mở một chặng riêng và bắt đầu đếm ngày chờ.</li>
</ol>
<div class="guide-note">
    <p>Phòng nào còn đang giữ hợp đồng (chưa báo xong) thì không bàn giao lại cho phòng đó được. Còn phòng chưa báo xong thì trang hiện cảnh báo, nhưng không chặn việc ký. Hợp đồng đã thanh lý thì không mở lượt bàn giao mới; chặng còn treo vẫn đóng được.</p>
</div>

<h3 id="hang-doi-ban-giao">8.1. Hàng đợi Bàn giao xử lý</h3>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm <strong>Bàn giao xử lý</strong> trên thanh bên trái. Trang liệt kê mọi chặng chưa xong của chi nhánh, chặng chờ lâu nhất đứng đầu. Đây cũng là chỗ phòng nhận báo đã làm xong phần việc của mình.</p>
<figure class="guide-shot">
    <a href="${img}/16-hang-doi.png" target="_blank"><img src="${img}/16-hang-doi.png" alt="Trang Bàn giao xử lý"></a>
    <figcaption>Hình 16. Trang Bàn giao xử lý (tài khoản Admin)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Phạm vi</strong>: <em>Toàn chi nhánh</em> (mặc định) hoặc <em>Hợp đồng của tôi</em> (hợp đồng bạn và cấp dưới đứng tên).</li>
    <li><strong>Thẻ từng phòng</strong>: số chặng phòng đó đang giữ. Bấm thẻ để chỉ xem việc của phòng đó; bấm <em>Tất cả các phòng</em> để bỏ lọc.</li>
    <li><strong>Chặng để lâu nhất</strong>: số ngày của chặng chờ lâu nhất.</li>
    <li><strong>Đã chờ</strong>: xanh dưới 7 ngày, vàng nâu từ 7 ngày, đỏ từ 14 ngày.</li>
    <li><strong>Xác nhận xong</strong>: ghi phòng đã làm gì (bắt buộc) rồi bấm <strong>Xong</strong>. Chỉ người thuộc đúng phòng đó (và Admin) thấy ô này; người khác thấy dòng “Chỉ người của phòng … xác nhận được”.</li>
</ol>
<div class="guide-note">
    <p>Phòng nhận cũng báo xong được ngay ở trang chi tiết hợp đồng, tab Bàn giao — cùng ô ghi chú và nút <strong>Đã xử lý xong</strong>. Đó là nút thao tác duy nhất trên trang chi tiết, dành cho người phòng nhận vốn không mở được trang Quản lý.</p>
</div>

<h2 id="tai-lieu">9. Tài liệu kèm theo</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Treo bản hợp đồng đã ký, biên bản nghiệm thu, bàn giao, thanh lý, hoá đơn… vào hợp đồng (hoặc phụ lục). Hệ thống <strong>chỉ lưu đường link</strong>: tải file lên Google Drive (hoặc kho nội bộ) trước, rồi dán link vào đây. Ở trang Quản lý, mở tab <strong>Tài liệu</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/17-tai-lieu.png" target="_blank"><img src="${img}/17-tai-lieu.png" alt="Tab Tài liệu ở trang Quản lý"></a>
    <figcaption>Hình 17. Tab Tài liệu</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Loại tài liệu</strong>: Hợp đồng đã ký, Phụ lục đã ký, Biên bản nghiệm thu, Biên bản bàn giao, Biên bản thanh lý, Báo giá / Đơn đặt hàng, Hoá đơn, Khác.</li>
    <li><strong>Tên tài liệu</strong> (không bắt buộc).</li>
    <li><strong>Link tài liệu</strong> (bắt buộc): phải bắt đầu bằng <code>http://</code> hoặc <code>https://</code>.</li>
    <li><strong>Ghi chú</strong> (không bắt buộc).</li>
    <li>Bấm <strong>Thêm</strong>.</li>
    <li><strong>Mở</strong>: mở tài liệu ở tab mới.</li>
    <li>Nút <strong>×</strong>: huỷ một tài liệu dán nhầm — bắt buộc nêu lý do. Tài liệu biến khỏi danh sách nhưng vẫn ghi trong Nhật ký.</li>
</ol>
<div class="guide-note">
    <p>Thêm được ở <strong>mọi trạng thái</strong>, kể cả sau khi thanh lý — phần lớn biên bản chỉ có vào lúc đó. Tài liệu loại <em>Hợp đồng đã ký</em> thành nút <strong>Mở bản PDF</strong> ở đầu trang chi tiết (số 2 ở Hình 5). Hệ thống không giữ bản sao: file bị xoá ở nơi lưu thì link ở đây thành link chết.</p>
</div>

<h2 id="phu-luc">10. Phụ lục hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Hợp đồng đã ký không sửa thẳng được. Mọi thay đổi điều khoản — bổ sung hàng, giảm trừ, gia hạn — đi qua một <strong>phụ lục</strong>: văn bản riêng có mã riêng, thời hạn riêng, hàng hoá riêng, và cũng phải được <strong>ký</strong>.</p>
<ul>
    <li>Chỉ lập được từ hợp đồng gốc <strong>đã ký và chưa thanh lý / chấm dứt</strong>. Hợp đồng đã thanh lý thì phát sinh sau đó là một hợp đồng mới.</li>
    <li>Chỉ một tầng: không lập phụ lục cho một phụ lục.</li>
    <li>Khách hàng và chiều mua/bán lấy theo hợp đồng gốc.</li>
    <li>Phụ lục hiện thành dòng riêng trên danh sách, mang nhãn “PL của &lt;mã gốc&gt;”.</li>
</ul>
<p>Ở trang Quản lý của hợp đồng gốc, mở tab <strong>Phụ lục</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/18-phu-luc.png" target="_blank"><img src="${img}/18-phu-luc.png" alt="Tab Phụ lục ở trang Quản lý"></a>
    <figcaption>Hình 18. Tab Phụ lục của một hợp đồng đã có hai phụ lục</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Danh sách phụ lục</strong>: mã, thời hạn, điều chỉnh giá trị (dấu + là bổ sung, dấu − là giảm trừ), tiến độ, nút Xem.</li>
    <li>Bấm <strong>Lập phụ lục</strong>.</li>
</ol>

<h3 id="lap-phu-luc">10.1. Lập phụ lục</h3>
<figure class="guide-shot narrow">
    <a href="${img}/19-lap-phu-luc.png" target="_blank"><img src="${img}/19-lap-phu-luc.png" alt="Form lập phụ lục"></a>
    <figcaption>Hình 19. Form lập phụ lục (đang điền ví dụ)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Dải nhắc</strong>: phụ lục ra đời ở trạng thái Nháp và phải được ký như một hợp đồng thường.</li>
    <li><strong>Mã phụ lục</strong>: số ghi trên bản phụ lục giấy, không trùng mã nào khác.</li>
    <li><strong>Khách hàng</strong>: theo hợp đồng gốc, không đổi được.</li>
    <li><strong>Điều chỉnh giá trị</strong>: <em>Bổ sung</em>, <em>Giảm trừ</em>, hoặc <em>Không đổi giá trị</em> (phụ lục chỉ gia hạn hoặc sửa hàng hoá).</li>
    <li><strong>Số tiền điều chỉnh</strong>: nhập số dương, là <strong>phần chênh lệch</strong> — không phải tổng giá trị mới.</li>
    <li>Bấm <strong>Lập phụ lục</strong>. Trang chi tiết của phụ lục mở ra; từ đó bấm Quản lý hợp đồng để điền thời hạn, thêm hàng hoá, rồi <a href="#ky">Ký</a> như hợp đồng thường.</li>
</ol>
<div class="guide-warn">
    <p>Khoản giảm trừ lớn hơn giá trị còn lại của hợp đồng gốc sẽ bị từ chối — thường là do gõ nhầm tổng giá trị mới vào ô chênh lệch.</p>
</div>

<h3 id="gia-tri-hien-hanh">10.2. Giá trị hiện hành</h3>
<p>Khi đã có phụ lục đã ký làm đổi giá trị, thẻ Thông tin hợp đồng của hợp đồng gốc hiện hai con số:</p>
<figure class="guide-shot">
    <a href="${img}/20-gia-tri-hien-hanh.png" target="_blank"><img src="${img}/20-gia-tri-hien-hanh.png" alt="Giá trị hợp đồng và giá trị hiện hành"></a>
    <figcaption>Hình 20. Giá trị theo bản gốc và giá trị hiện hành sau hai phụ lục</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Giá trị hợp đồng</strong>: con số trên bản gốc đã ký — không đổi.</li>
    <li><strong>Giá trị hiện hành</strong> = giá trị gốc cộng điều chỉnh của các phụ lục <strong>đã ký</strong>.</li>
</ol>
<div class="guide-note">
    <p>Phụ lục còn Nháp chưa được tính; trang chi tiết ghi “Đang có phụ lục chờ ký…”. Phụ lục gia hạn <strong>không đổi ngày kết thúc</strong> của hợp đồng gốc: thời hạn mới nằm trên chính phụ lục, nên trạng thái theo lịch của hợp đồng gốc vẫn tính theo ngày trên bản gốc.</p>
</div>

<h2 id="noi-ban-mua">11. Nối hợp đồng bán – mua</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Ghi lại những hợp đồng mua vào sinh ra vì một hợp đồng bán (và ngược lại), để thấy đầu vào của từng hợp đồng. Ở trang Quản lý của hợp đồng gốc, mở tab <strong>Nối bán – mua</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/21-noi-ban-mua.png" target="_blank"><img src="${img}/21-noi-ban-mua.png" alt="Tab Nối bán – mua của một hợp đồng bán"></a>
    <figcaption>Hình 21. Tab Nối bán – mua của một hợp đồng bán</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ba ô</strong> (chỉ có ở hợp đồng bán): <em>Giá trị bán ra</em> (đã cộng phụ lục), <em>Đầu vào đã nối</em> (tổng giá trị các hợp đồng mua đã nối), <em>Chênh lệch thô</em>.</li>
    <li><strong>Đơn mua gom</strong>: một hợp đồng mua phục vụ nhiều hợp đồng bán. Giá trị của nó được tính trọn vào từng hợp đồng bán mà nó phục vụ; hệ thống không tự chia.</li>
    <li><strong>Gỡ</strong>: bỏ liên kết (có hộp xác nhận).</li>
    <li><strong>Chọn hợp đồng</strong> ở chiều ngược lại — chỉ hợp đồng gốc, không có phụ lục.</li>
    <li><strong>Ghi chú</strong> (không bắt buộc).</li>
    <li>Bấm <strong>Nối</strong>.</li>
</ol>
<div class="guide-warn">
    <p><strong>Chênh lệch thô không phải lợi nhuận</strong>: chưa trừ chi phí thi công, nhân công, bảo hành. Nó có thể âm khi có đơn mua gom, như ở Hình 21.</p>
</div>
<div class="guide-note"><p>Nối được cả khi hợp đồng còn Nháp và cả sau khi thanh lý. Phụ lục không nối được.</p></div>

<h2 id="thanh-ly">12. Thanh lý hoặc chấm dứt sớm</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p><strong>Thanh lý</strong> là lúc hợp đồng coi như xong. <strong>Chấm dứt sớm</strong> là dừng trước hạn. Cả hai đều khoá hợp đồng vĩnh viễn. Ở trang Quản lý của một hợp đồng đã ký, mở tab <strong>Bước tiến trình</strong> và bấm <strong>Thanh lý</strong> (hoặc <strong>Chấm dứt sớm</strong>).</p>
<figure class="guide-shot narrow">
    <a href="${img}/22-thanh-ly.png" target="_blank"><img src="${img}/22-thanh-ly.png" alt="Hộp xác nhận thanh lý"></a>
    <figcaption>Hình 22. Hộp xác nhận thanh lý</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Căn cứ</strong> (bắt buộc): số và ngày biên bản thanh lý, hoặc lý do chấm dứt.</li>
    <li>Bấm <strong>Xác nhận</strong>. Trang chi tiết mở ra.</li>
</ol>
<p>Sau khi thanh lý, thanh tiến trình ở trang chi tiết ghi lại bước này:</p>
<figure class="guide-shot">
    <a href="${img}/23-da-thanh-ly.png" target="_blank"><img src="${img}/23-da-thanh-ly.png" alt="Thanh tiến trình của hợp đồng đã thanh lý"></a>
    <figcaption>Hình 23. Thanh tiến trình của một hợp đồng đã thanh lý</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mốc Thanh lý</strong>: ngày, người thanh lý và căn cứ.</li>
    <li><strong>Dải báo đã đóng băng</strong>.</li>
</ol>
<div class="guide-warn">
    <p><strong>Không quay lại được.</strong> Sau khi thanh lý hoặc chấm dứt, không ai sửa được nội dung, kể cả quản trị viên; không lập phụ lục, không lập hay xoá kỳ thanh toán, không mở bàn giao mới. Vẫn làm được: bấm Đã thu cho các kỳ đã lập, thêm/huỷ tài liệu, nối bán – mua, đóng chặng bàn giao còn treo. Phát sinh sau đó phải lập hợp đồng mới.</p>
</div>

<h2 id="huy-ban-ghi">13. Xoá bản nháp / Huỷ bản ghi</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Dùng để gỡ một bản ghi tạo nhầm khỏi danh sách — <strong>không phải huỷ hợp đồng ngoài đời</strong>. Ở trang Quản lý, mở tab <strong>Bước tiến trình</strong>, bấm <strong>Huỷ bản ghi</strong> (số 2 ở Hình 9).</p>
<figure class="guide-shot narrow">
    <a href="${img}/24-huy-ban-ghi.png" target="_blank"><img src="${img}/24-huy-ban-ghi.png" alt="Hộp huỷ bản ghi hợp đồng"></a>
    <figcaption>Hình 24. Hộp huỷ bản ghi</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Lý do</strong> (bắt buộc).</li>
    <li>Bấm <strong>Huỷ bản ghi</strong>. Xong thì danh sách hợp đồng mở ra.</li>
</ol>
<table class="guide-table">
    <tr><th>Hợp đồng</th><th>Ai huỷ được</th></tr>
    <tr><td>Nháp</td><td>Admin, Sales</td></tr>
    <tr><td>Đã ký, đã thanh lý, chấm dứt sớm</td><td>Chỉ Admin</td></tr>
    <tr><td>Đang có phụ lục</td><td>Không ai — huỷ từng phụ lục trước. Nút Huỷ bản ghi không hiện, và tab ghi rõ lý do.</td></tr>
</table>
<div class="guide-note">
    <p>Bản ghi bị huỷ biến khỏi mọi danh sách nhưng vẫn nằm trong cơ sở dữ liệu, kèm dòng nhật ký ghi lý do và người huỷ. Huỷ một phụ lục đã ký thì phần giá trị của nó rút khỏi hợp đồng gốc.</p>
</div>

<h2 id="nhap-pdf">14. Nhập hợp đồng từ file PDF</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Kỹ thuật</span></div>
<p>Dùng khi đã có sẵn thông tin một hợp đồng bán: điền vào file mẫu rồi tải lên, hệ thống tạo hợp đồng — và cả khách hàng, nếu khách chưa có. Mỗi file một hợp đồng. Ở danh sách, bấm <strong>Nhập PDF</strong> (số 9 ở Hình 1).</p>
<figure class="guide-shot narrow">
    <a href="${img}/25-nhap-pdf.png" target="_blank"><img src="${img}/25-nhap-pdf.png" alt="Trang Nhập PDF hợp đồng"></a>
    <figcaption>Hình 25. Trang Nhập PDF hợp đồng</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tải file mẫu (.pdf)</strong>. Mở file bằng một trình đọc PDF điền được form (Adobe Acrobat Reader, Foxit, trình duyệt…), điền theo bảng dưới rồi lưu lại. Giữ nguyên file, đừng in ra PDF khác: hệ thống đọc các ô của chính file mẫu.</li>
    <li><strong>Chọn tệp</strong>: chọn file đã điền.</li>
    <li>Bấm <strong>Nhập dữ liệu</strong>.</li>
</ol>
<table class="guide-table">
    <tr><th>Ô trong file mẫu</th><th>Quy định</th></tr>
    <tr><td>Mã hợp đồng</td><td>Bắt buộc, không trùng mã nào đã có. Dòng “để trống = tự sinh” in trên file mẫu đã cũ: để trống sẽ bị từ chối.</td></tr>
    <tr><td>Tiêu đề hợp đồng</td><td>Bắt buộc.</td></tr>
    <tr><td>Loại hợp đồng</td><td>Cung cấp thiết bị, Thi công lắp đặt hoặc Bảo trì bảo dưỡng — file mẫu chỉ dành cho hợp đồng bán.</td></tr>
    <tr><td>Ngày ký, Hiệu lực từ, Đến ngày</td><td>Bắt buộc, dạng <code>dd/MM/yyyy</code>; ngày ký ≤ hiệu lực từ ≤ đến ngày.</td></tr>
    <tr><td>Người phụ trách</td><td>Tên đăng nhập của nhân viên. Để trống thì hợp đồng đứng tên người nhập file.</td></tr>
    <tr><td>Mã số thuế, Tên doanh nghiệp, Email, Điện thoại</td><td>Bắt buộc. Mã số thuế trùng một khách hàng đã có thì hợp đồng gắn vào khách đó.</td></tr>
    <tr><td>Loại KH, Nhóm KH, Tỉnh/Thành, Xã/Phường, Địa chỉ chi tiết</td><td>Chỉ cần khi mã số thuế chưa có trong hệ thống: hệ thống tạo khách hàng mới từ các ô này. Email và điện thoại không được trùng khách hàng khác.</td></tr>
    <tr><td>Website, Người đại diện</td><td>Không bắt buộc; chỉ dùng khi tạo khách hàng mới.</td></tr>
    <tr><td>Hạng mục sản phẩm (tối đa 15 dòng)</td><td>Mỗi dòng: <em>Mã SP</em> đúng như ở trang Sản phẩm và <em>Số lượng</em> (viết <code>1.000</code> cũng được); Đơn vị để trống là “Cái”. Dòng không có Mã SP bị bỏ qua.</td></tr>
</table>
<div class="guide-warn">
    <p>Gõ sai tên đăng nhập ở ô Người phụ trách (không có nhân viên đó) thì hệ thống <strong>không báo lỗi</strong> mà gán hợp đồng cho chính người nhập file. Kiểm lại người phụ trách ở trang chi tiết sau khi nhập.</p>
</div>
<div class="guide-note">
    <p>Nút Nhập PDF có ở cả danh sách hợp đồng mua, nhưng file luôn tạo ra một hợp đồng <strong>bán</strong>.</p>
</div>

<h3 id="loi-nhap-pdf">14.1. Khi nhập không được</h3>
<p>Chỉ cần một lỗi là hệ thống <strong>không ghi gì cả</strong>, và liệt kê mọi lỗi một lượt để sửa một lần. Sửa file rồi tải lên lại.</p>
<figure class="guide-shot narrow">
    <a href="${img}/26-nhap-pdf-loi.png" target="_blank"><img src="${img}/26-nhap-pdf-loi.png" alt="Danh sách lỗi khi nhập PDF"></a>
    <figcaption>Hình 26. Tải lên chính file mẫu còn trống: tám lỗi cần sửa</figcaption>
</figure>
<div class="guide-note">
    <p>Nhập thành công thì trang báo “Đã tạo hợp đồng &lt;mã&gt;”, kèm link xem chi tiết. Hợp đồng nhập vào vẫn ở trạng thái <strong>Nháp</strong>: kiểm lại rồi <a href="#ky">Ký</a> — ngày ký giữ theo file.</p>
</div>

<h2 id="xuat-file">15. Xuất Excel và xuất PDF</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p><strong>Xuất Excel</strong> (số 8 ở Hình 1) tải về file <code>.xls</code> chứa <strong>toàn bộ</strong> hợp đồng khớp bộ lọc đang bật — mọi trang, cùng phạm vi và cùng dải “Đang xem” —, xếp theo tỉnh. Các cột gồm: STT, Mã HĐ, Phụ lục của, Tiêu đề, Loại HĐ, Tỉnh/Thành phố, Khách hàng, Người phụ trách, Ngày ký, Ngày hiệu lực, Ngày kết thúc, Trạng thái, Tiến độ. Khách chưa có địa chỉ ghi <em>Chưa xác định</em> ở cột tỉnh.</p>
<p><strong>Xuất PDF</strong> (số 3 ở Hình 5) tải về bản hợp đồng mua bán in theo mẫu của công ty cho một hợp đồng bán: công ty là bên bán (Bên A), khách hàng là bên mua (Bên B). File gồm mã, ngày ký, thời hạn, người đại diện bên bán (người phụ trách), thông tin bên mua (tên, mã số thuế, địa chỉ, người đại diện, điện thoại, email) và bảng hàng hoá (không có đơn giá). Nếu hàng hoá quá dài không vừa trang, hệ thống báo lỗi thay vì xuất một file thiếu dòng.</p>
<div class="guide-note">
    <p>Xuất PDF <strong>chỉ có ở hợp đồng bán</strong>. Mẫu in hiện có viết cho chiều bán — công ty đứng ở phần bên bán — nên chưa dùng được cho hợp đồng mua: trang chi tiết của hợp đồng mua, và của các phụ lục của nó, không có nút Xuất PDF.</p>
</div>

<h2 id="loi-thuong-gap">16. Thông báo lỗi thường gặp</h2>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Chưa ký được: hợp đồng còn thiếu ngày hiệu lực hoặc ngày kết thúc…</td><td>Bấm Quản lý hợp đồng, điền đủ hai ngày ở form, Lưu thay đổi rồi Ký lại.</td></tr>
    <tr><td>Không chuyển được trạng thái — có thể hợp đồng đã được người khác chuyển trước đó.</td><td>Tải lại trang và xem tiến độ hiện tại: có thể đã có người ký hoặc thanh lý trước bạn.</td></tr>
    <tr><td>Không lập được phụ lục: phụ lục chỉ lập từ hợp đồng gốc đã ký và chưa thanh lý…</td><td>Hợp đồng gốc còn Nháp (sửa thẳng, không cần phụ lục), đã thanh lý (lập hợp đồng mới), hoặc chính nó là một phụ lục.</td></tr>
    <tr><td>Khoản giảm trừ lớn hơn giá trị còn lại của hợp đồng gốc…</td><td>Ô Số tiền điều chỉnh nhập phần chênh lệch, không phải tổng giá trị mới.</td></tr>
    <tr><td>Sửa sai sót trên hợp đồng đã ký thì bắt buộc phải nêu lý do.</td><td>Điền ô Lý do ở chế độ chữa sai sót.</td></tr>
    <tr><td>Không thêm được hàng hoá — hàng hoá chỉ sửa được khi hợp đồng còn là bản Nháp…</td><td>Hợp đồng đã được ký: thay đổi hàng hoá phải lập phụ lục.</td></tr>
    <tr><td>Không thêm được hàng hoá: chọn một sản phẩm và nhập số lượng…</td><td>Chọn sản phẩm; số lượng là số nguyên lớn hơn 0.</td></tr>
    <tr><td>Không thực hiện được trên kỳ thanh toán…</td><td>Số tiền phải lớn hơn 0 và có ngày đến hạn; kỳ đã ghi nhận thu thì không ghi lại được; hợp đồng đã thanh lý thì không lập hay xoá kỳ được.</td></tr>
    <tr><td>Chọn ít nhất một phòng để bàn giao.</td><td>Tích ít nhất một phòng.</td></tr>
    <tr><td>Phòng đó đang còn giữ hợp đồng này…</td><td>Đợi phòng đó báo xong (hoặc bỏ tích phòng đó) rồi bàn giao lại.</td></tr>
    <tr><td>Không xác nhận được chặng bàn giao…</td><td>Chặng có thể đã được báo xong trước đó; ô ghi chú là bắt buộc.</td></tr>
    <tr><td>Không thêm được tài liệu: link phải bắt đầu bằng http:// hoặc https://…</td><td>Dán lại link đầy đủ, chọn loại tài liệu trong danh sách.</td></tr>
    <tr><td>Không nối được hai hợp đồng này…</td><td>Phải là một hợp đồng bán với một hợp đồng mua, và cả hai đều là hợp đồng gốc.</td></tr>
    <tr><td>Hai hợp đồng này đã nối với nhau rồi.</td><td>Không cần nối lại.</td></tr>
    <tr><td>Phải có lý do thì mới huỷ được bản ghi hợp đồng.</td><td>Điền ô Lý do trong hộp huỷ bản ghi.</td></tr>
    <tr><td>Không huỷ được bản ghi này…</td><td>Thường do hợp đồng vừa có phụ lục: huỷ các phụ lục trước.</td></tr>
    <tr><td>Không xuất được PDF: hợp đồng có quá nhiều dòng sản phẩm/ghi chú dài…</td><td>Rút gọn ghi chú của các dòng hàng hoá, hoặc báo quản trị viên.</td></tr>
    <tr><td>Chưa xuất được PDF cho hợp đồng mua…</td><td>Mẫu in chỉ dành cho hợp đồng bán. Thông báo này hiện khi mở một link xuất PDF cũ của hợp đồng mua; trang chi tiết hợp đồng mua không có nút Xuất PDF.</td></tr>
    <tr><td>Vui lòng chọn file .pdf để nhập. / File không đúng mẫu… / Không đọc được file…</td><td>Tải lại file mẫu, điền trên chính file đó và lưu dạng PDF có form.</td></tr>
</table>

<%@ include file="/jsp/guide/_bottom.jspf" %>
