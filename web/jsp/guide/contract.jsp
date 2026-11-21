<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%--
    Hướng dẫn sử dụng -- phân hệ Hợp đồng (hợp đồng bán, hợp đồng mua, phụ lục,
    giấy tờ, kỳ thanh toán, bàn giao).

    Viết theo CODE hiện tại (ContractController, ContractDAO, PermissionService,
    AccessControl và các JSP trong jsp/sale/), không theo tài liệu cũ. Vòng đời
    theo mục 14, 15 "Quyết định đã chốt" của task_tracker.md (Giai đoạn 10).
    Ảnh ở web/guide/contract/ do tools/guide/capture.mjs chụp theo kịch bản
    tools/guide/shots/contract.mjs -- số trong ol.guide-steps phải khớp số
    khoanh đỏ trên ảnh; sửa một bên thì soát lại bên kia.

    Chỉ dùng tập thẻ mô tả ở đầu css/guide.css: tools/guide/ dựng lại đúng nội
    dung này sang file Word.
--%>
<c:set var="guideTitle" value="Hợp đồng"/>
<c:set var="img" value="${pageContext.request.contextPath}/guide/contract"/>
<%@ include file="/jsp/guide/_top.jspf" %>

<h1>Hợp đồng</h1>
<p class="lead">Theo dõi hợp đồng bán (với khách hàng) và hợp đồng mua (với nhà cung cấp) từ lúc làm phiếu GQNCKH, giao hàng, ký, thu tiền cho tới khi thanh lý.</p>

<h2 id="tong-quan">1. Tổng quan</h2>
<p>Trên thanh bên trái, mục <strong>Hợp đồng</strong> có ba mục con:</p>
<ul>
    <li><strong>Hợp đồng bán</strong>: mình bán hàng, làm dịch vụ cho khách hàng.</li>
    <li><strong>Hợp đồng mua</strong>: mình mua hàng, thuê dịch vụ của nhà cung cấp.</li>
    <li><strong>Bàn giao xử lý</strong>: các hợp đồng đang chờ phòng Tổng hợp xác nhận (xem <a href="#hang-doi-ban-giao">mục 8.1</a>).</li>
</ul>
<p>Hai loại hợp đồng dùng chung mọi màn hình và thao tác; chỉ khác ở mấy điểm sau:</p>
<table class="guide-table">
    <tr><th></th><th>Hợp đồng bán</th><th>Hợp đồng mua</th></tr>
    <tr><td>Đối tác</td><td>Khách hàng mua</td><td>Nhà cung cấp</td></tr>
    <tr><td>Loại hợp đồng</td><td>Cung cấp thiết bị, Thi công lắp đặt, Bảo trì bảo dưỡng</td><td>Mua thiết bị, Mua vật tư, Thuê thi công lắp đặt, Thuê bảo trì</td></tr>
    <tr><td>Lọc theo tỉnh</td><td>Có (tỉnh của khách hàng)</td><td>Không, vì nhà cung cấp không chia theo địa bàn</td></tr>
</table>
<p><strong>Mã hợp đồng</strong> là số ghi trên bản hợp đồng giấy, do người dùng nhập (ví dụ <code>05/2026/HĐMB/POT-HTN</code>). Hồ sơ mở từ trước khi có số, nên lúc còn <em>Nháp</em> được để trống; chỗ nào cần hiện mã thì ghi <em>Chưa có số</em>. Trước khi ký thì <strong>bắt buộc</strong> có mã. Hệ thống không tự sinh mã, và mã đã nhập không được trùng với hợp đồng hay phụ lục nào khác.</p>

<h3 id="vong-doi">1.1. Vòng đời hợp đồng</h3>
<p>Hồ sơ hợp đồng mở ngay từ lúc làm phiếu GQNCKH, rồi đi theo đúng các bước chi nhánh làm ngoài đời. Bảng dưới cho biết mỗi bước ghi gì trên hệ thống và hệ thống hiện ra sao:</p>
<table class="guide-table">
    <tr><th>Bước ngoài đời</th><th>Làm trên hệ thống</th><th>Hệ thống hiện</th></tr>
    <tr><td>Làm phiếu GQNCKH</td><td>Tạo hợp đồng (<a href="#tao-hop-dong">mục 3</a>), mã để trống. Tích <em>Phiếu GQNCKH</em> ở tab Giấy tờ (<a href="#tai-lieu">mục 9</a>).</td><td>Tiến độ <em>Nháp</em>, nhãn <em>Chuẩn bị hàng</em>.</td></tr>
    <tr><td>Làm phiếu ĐCNB, gọi xe</td><td>Tích <em>Phiếu ĐCNB</em>.</td><td><em>Chuẩn bị hàng</em>.</td></tr>
    <tr><td>Giao hàng cho khách, ký biên bản giao nhận</td><td>Tích <em>Biên bản giao nhận hàng hoá</em>, ngày trên giấy là ngày giao.</td><td>Nhãn <em>Đã giao – chưa ký</em>. Đã giao từ 15 ngày trở lên mà chưa ký thì người phụ trách nhận một thông báo nhắc ký (xem hướng dẫn Bắt đầu, mục 6).</td></tr>
    <tr><td>Thương thảo, ký hợp đồng</td><td>Ở trang Quản lý: nhập mã, thời hạn, giá trị (<a href="#quan-ly">mục 5</a>), rồi bấm Ký (<a href="#ky">mục 6</a>). Tích <em>Hợp đồng đã ký</em>.</td><td>Tiến độ <em>Đã ký</em>; nhãn giao hàng mất.</td></tr>
    <tr><td>Bàn giao phòng Tổng hợp</td><td>Tab Bàn giao (<a href="#ban-giao">mục 8</a>). Phòng Tổng hợp bấm <em>Đã xử lý xong</em>.</td><td><em>Chờ Tổng hợp · N ngày</em> cho tới khi phòng đó báo xong.</td></tr>
    <tr><td>Tạm ứng (nếu có)</td><td>Lập kỳ loại <em>Tạm ứng</em> (<a href="#ky-thanh-toan">mục 7</a>); tích <em>Đề nghị tạm ứng</em>, <em>Bảo lãnh tạm ứng</em>; bấm <em>Đã nộp hồ sơ</em>, tiền về thì <em>Đã thu</em>.</td><td>Kỳ chuyển <em>Chờ thu</em> rồi <em>Đã thu</em>.</td></tr>
    <tr><td>Nghiệm thu, nộp hồ sơ thanh toán cho bên mua</td><td>Tích <em>Biên bản nghiệm thu bàn giao</em>, <em>Đề nghị thanh toán</em>, <em>Hoá đơn GTGT</em>; kỳ <em>Thanh toán</em> bấm <em>Đã nộp hồ sơ</em>.</td><td>Hạn thu = ngày nộp + số ngày đã thoả thuận; kỳ <em>Chờ thu</em>, qua hạn thì <em>Quá hạn</em>.</td></tr>
    <tr><td>Bên mua trả tiền</td><td>Bấm <em>Đã thu</em>, ghi số tiền thực thu.</td><td>Kỳ <em>Đã thu</em>; trang chủ cộng vào doanh thu tháng có ngày thu.</td></tr>
    <tr><td>Thanh lý</td><td>Tích <em>Biên bản thanh lý</em>, bấm Thanh lý (<a href="#thanh-ly">mục 12</a>).</td><td>Tiến độ <em>Đã thanh lý</em>; hợp đồng khoá lại.</td></tr>
</table>
<p>Hệ thống chỉ <strong>theo dõi</strong> hợp đồng: không quản lý kho, số lượng hàng đã giao, điều xe hay sổ sách kế toán. Giấy tờ chỉ ghi ngày, số và link (nếu có), không lưu file.</p>
<p>Riêng <strong>tiến độ</strong> do người bấm, và <strong>chỉ đi tới, không quay lại</strong>:</p>
<table class="guide-table">
    <tr><th>Tiến độ</th><th>Nghĩa</th><th>Còn làm được gì</th></tr>
    <tr><td>Nháp</td><td>Hồ sơ đã mở, chưa ký: đang chuẩn bị hàng, đã giao hoặc đang thương thảo.</td><td>Sửa thông tin (trừ người phụ trách và người ký bên mình), điền mã, thời hạn, thêm/gỡ hàng hoá, tích giấy tờ, lập kỳ thanh toán. Xoá được (huỷ bản ghi).</td></tr>
    <tr><td>Đã ký</td><td>Hai bên đã ký; nội dung trở thành chứng cứ.</td><td>Form thông tin khoá hết; thay đổi điều khoản phải lập <a href="#phu-luc">phụ lục</a>. Vẫn tích giấy tờ, lập và theo dõi kỳ thanh toán, bàn giao, nối bán – mua.</td></tr>
    <tr><td>Đã thanh lý</td><td>Hợp đồng đã xong, có biên bản thanh lý.</td><td>Không sửa được gì nữa, kể cả quản trị viên. Vẫn ghi nhận nộp hồ sơ và tiền về cho kỳ đã lập, tích/bỏ tích giấy tờ, nối bán – mua, đóng chặng bàn giao còn treo.</td></tr>
    <tr><td>Chấm dứt sớm</td><td>Dừng trước hạn.</td><td>Như Đã thanh lý.</td></tr>
</table>
<div class="guide-note">
    <p>Hợp đồng chỉ coi là <strong>xong</strong> khi đã thanh lý. Hết hạn theo lịch chưa phải là xong: một hợp đồng hết hạn mà chưa thanh lý là việc còn tồn, và trang chi tiết sẽ cảnh báo.</p>
</div>

<h3 id="hai-loai-trang-thai">1.2. Ba loại nhãn</h3>
<p>Mỗi hợp đồng mang tới ba nhãn đứng cạnh nhau, trả lời ba câu hỏi khác nhau:</p>
<ul>
    <li><strong>Trạng thái theo lịch</strong> (nhãn nền màu, có chấm tròn): hệ thống tự tính từ ngày hiệu lực và ngày kết thúc.
        <em>Chưa hiệu lực</em> (chưa có đủ thời hạn, hoặc chưa tới ngày hiệu lực),
        <em>Đang hiệu lực</em>,
        <em>Sắp hết hạn</em> (còn 30 ngày trở xuống),
        <em>Đã hết hạn</em> (đã qua ngày kết thúc).</li>
    <li><strong>Tiến độ</strong> (nhãn viền nét đứt): do người bấm — <em>Nháp</em>, <em>Đã ký</em>, <em>Đã thanh lý</em>, <em>Chấm dứt sớm</em>.</li>
    <li><strong>Bước giao hàng</strong> (chỉ ở hợp đồng Nháp): hệ thống tự suy ra từ giấy tờ đã tích. Có Phiếu GQNCKH hoặc Phiếu ĐCNB mà chưa có Biên bản giao nhận hàng hoá là <em>Chuẩn bị hàng</em>; có Biên bản giao nhận hàng hoá là <em>Đã giao – chưa ký</em>. Ký xong thì nhãn này mất.</li>
</ul>
<div class="guide-warn">
    <p>Các nhãn <strong>không suy ra nhau</strong>. <em>Đã hết hạn</em> + <em>Đã ký</em> nghĩa là hết hạn mà chưa thanh lý. <em>Chưa hiệu lực</em> + <em>Đã ký</em> nghĩa là đã ký nhưng chưa tới ngày hiệu lực. <em>Nháp</em> + <em>Đã giao – chưa ký</em> nghĩa là hàng đã tới khách mà hợp đồng chưa ký. Đọc đủ các nhãn trước khi kết luận về một hợp đồng.</p>
</div>

<h3 id="quyen">1.3. Ai làm được gì</h3>
<table class="guide-table">
    <tr><th>Việc</th><th>Admin</th><th>Sales</th><th>Tổng hợp</th></tr>
    <tr><td>Xem danh sách, chi tiết, hàng đợi bàn giao; xuất Excel</td><td>Được</td><td>Được</td><td>Được</td></tr>
    <tr><td>Tạo hợp đồng, sửa bản nháp, lập phụ lục, ký</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
    <tr><td>Tích / bỏ tích giấy tờ</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
    <tr><td>Lập, sửa, xoá kỳ thanh toán; ghi nhận đã nộp hồ sơ</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
    <tr><td>Ghi nhận đã thu</td><td>Được</td><td>Được (*)</td><td>Được, ở trang chi tiết</td></tr>
    <tr><td>Bàn giao, nối bán – mua, thanh lý, chấm dứt sớm, xoá bản nháp</td><td>Được</td><td>Được (*)</td><td>Không</td></tr>
    <tr><td>Huỷ bản ghi hợp đồng đã ký, chữa sai sót nhập liệu</td><td>Được</td><td>Không</td><td>Không</td></tr>
    <tr><td>Chọn người phụ trách khi tạo; thay người phụ trách đã bị khoá tài khoản</td><td>Được</td><td>Không</td><td>Không</td></tr>
    <tr><td>Báo “Đã xử lý xong” một chặng bàn giao</td><td>Mọi chặng</td><td>Không</td><td>Chặng của phòng Tổng hợp</td></tr>
</table>
<div class="guide-note">
    <p>(*) Sales có <strong>cấp trên</strong> trong sơ đồ tổ chức thì chỉ được xem. Phòng Kinh doanh hiện không gán cấp trên cho ai, nên mọi Sales đều làm được. Các nút không dùng được sẽ không hiện ra.</p>
    <p>Việc báo xong một chặng bàn giao xét theo <strong>phòng ban</strong> của người dùng, không theo vai: người thuộc phòng Tổng hợp báo xong được chặng giao cho phòng mình.</p>
</div>

<h2 id="danh-sach">2. Xem và tìm hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Tổng hợp</span></div>
<p>Bấm <strong>Hợp đồng bán</strong> (hoặc <strong>Hợp đồng mua</strong>) trên thanh bên trái. Danh sách hiện 10 dòng mỗi trang, hợp đồng tạo sau cùng đứng đầu.</p>
<figure class="guide-shot">
    <a href="${img}/01-danh-sach.png" target="_blank"><img src="${img}/01-danh-sach.png" alt="Đầu trang danh sách hợp đồng bán"></a>
    <figcaption>Hình 1. Đầu trang danh sách hợp đồng bán (tài khoản Sales đã được giao tỉnh)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Bốn ô trạng thái theo lịch</strong>: số hợp đồng ở mỗi trạng thái. Bấm một ô để chỉ xem trạng thái đó; bấm lại ô đang sáng để bỏ lọc.</li>
    <li><strong>Ô tìm kiếm</strong>: gõ mã hợp đồng, tiêu đề hoặc tên khách hàng rồi nhấn Enter. Hợp đồng chưa có số thì tìm theo tiêu đề.</li>
    <li><strong>Phạm vi</strong>:
        <em>Của tôi</em> là những hợp đồng bạn (và cấp dưới của bạn, nếu có) phụ trách, cộng với hợp đồng của khách hàng thuộc các tỉnh bạn phụ trách. Đây là mặc định của Sales.
        <em>Toàn chi nhánh</em> là tất cả hợp đồng — mặc định của Admin và Tổng hợp.</li>
    <li><strong>Tiến độ</strong>: Nháp (chưa ký), Đã ký, Đã thanh lý, Chấm dứt sớm.</li>
    <li><strong>Chỉ hợp đồng gốc</strong>: ẩn các phụ lục (mặc định phụ lục hiện thành dòng riêng). <strong>Đang bàn giao</strong>: chỉ những hợp đồng còn chờ phòng Tổng hợp xác nhận.</li>
    <li><strong>Lọc thêm</strong>: loại hợp đồng, tỉnh, ngày ký, đang chờ ở phòng nào, bước giao hàng (xem <a href="#loc-them">mục 2.1</a>).</li>
    <li><strong>Dải “Đang xem”</strong>: danh sách đang thu hẹp tới đâu — của ai, và còn hiệu lực trong khoảng nào. Bấm <em>Xem mọi thời điểm</em> (hoặc <em>Chỉ tháng này</em>) để đổi.</li>
    <li><strong>Xuất Excel</strong>: tải danh sách đang lọc về máy (xem <a href="#xuat-file">mục 14</a>).</li>
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
    <li><strong>Đang chờ ở phòng</strong>: <em>Đang chờ: Tổng hợp</em> là những hợp đồng phòng Tổng hợp chưa báo xong.</li>
    <li><strong>Bước giao hàng</strong>: <em>Chuẩn bị hàng</em> hoặc <em>Đã giao – chưa ký</em> (<a href="#hai-loai-trang-thai">mục 1.2</a>). Ô <em>Đã giao – chưa ký</em> ở trang chủ mở thẳng danh sách đã lọc theo ô này.</li>
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
    <li><strong>Trạng thái</strong> theo lịch. Bản nháp chưa có thời hạn luôn là <em>Chưa hiệu lực</em>.</li>
    <li><strong>Tiến độ</strong>, kèm nhãn <strong>bước giao hàng</strong> của bản nháp: <em>Chuẩn bị hàng</em> (chữ xám) hoặc <em>Đã giao – chưa ký</em> (chữ vàng nâu).</li>
    <li><strong>“Chờ Tổng hợp · N ngày”</strong>: hợp đồng đang chờ phòng Tổng hợp xác nhận, bao lâu rồi. Chữ chuyển vàng nâu từ 7 ngày, đỏ từ 14 ngày.</li>
    <li><strong>Thao tác</strong>: <i class="fa-regular fa-eye"></i> xem chi tiết, <i class="fa-solid fa-pen"></i> mở trang Quản lý (chỉ người có quyền sửa mới thấy). Bảng rộng hơn màn hình nên cột này thường nằm khuất bên phải: kéo thanh cuộn ngang dưới bảng, hoặc giữ chuột trên bảng rồi kéo sang trái. Bấm vào tiêu đề cũng mở trang chi tiết.</li>
</ol>
<div class="guide-note"><p>Mở một hợp đồng đã bị huỷ bản ghi (hoặc link cũ) thì danh sách hiện dải vàng <em>Không tìm thấy hợp đồng này</em>.</p></div>
<div class="guide-note"><p>Danh sách không có nút xoá. Xoá bản nháp và huỷ bản ghi nằm ở trang Quản lý (xem <a href="#huy-ban-ghi">mục 13</a>).</p></div>

<h2 id="tao-hop-dong">3. Tạo hợp đồng (mở hồ sơ)</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Mở hồ sơ ngay khi làm phiếu GQNCKH cho khách, chưa cần số hợp đồng hay thời hạn. Ở danh sách <strong>Hợp đồng bán</strong> (hoặc <strong>Hợp đồng mua</strong>), bấm <strong>Tạo hợp đồng</strong>. Ô có dấu <span style="color:#e2536b">*</span> là bắt buộc.</p>
<figure class="guide-shot narrow">
    <a href="${img}/04-tao-hop-dong.png" target="_blank"><img src="${img}/04-tao-hop-dong.png" alt="Trang Tạo hợp đồng"></a>
    <figcaption>Hình 4. Trang Tạo hợp đồng</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mã hợp đồng</strong>: <em>để trống nếu chưa có số</em>; bắt buộc trước khi ký. Khi đã có số thì nhập đúng số ghi trên bản hợp đồng giấy, không trùng với hợp đồng hay phụ lục nào khác.</li>
    <li><strong>Tiêu đề hợp đồng</strong>.</li>
    <li><strong>Khách hàng</strong> (ở hợp đồng mua ô này mang nhãn <strong>Nhà cung cấp</strong>): hợp đồng bán chỉ chọn được khách hàng mua; hợp đồng mua chỉ chọn được nhà cung cấp. Đối tác chưa có trong danh sách thì tạo ở phân hệ Khách hàng trước, hoặc tick thêm vai còn thiếu ở trang Sửa của họ.</li>
    <li><strong>Người phụ trách</strong>: Sales tạo thì là chính mình — ô chỉ hiện tên, không chọn được. Admin tạo thì chọn một nhân viên Sales đang hoạt động. Tạo xong thì người phụ trách không đổi được nữa, kể cả khi hợp đồng còn Nháp (ngoại lệ duy nhất ở <a href="#thay-nguoi-phu-trach">mục 5.2</a>).</li>
    <li><strong>Loại hợp đồng</strong>: bộ loại khác nhau giữa hợp đồng bán và mua (xem bảng ở <a href="#tong-quan">mục 1</a>).</li>
    <li><strong>Thông tin ký kết</strong>: <em>người ký bên mình</em> luôn là Giám đốc chi nhánh — bốn ô Người ký bên mình, Chức vụ, Điện thoại, Fax do hệ thống tự điền từ trang Thông tin chi nhánh, chỉ để xem, không gõ được. Bạn chỉ nhập phần bên đối tác (không bắt buộc, nên điền đủ trước khi ký): người ký bên đối tác và chức vụ, căn cứ uỷ quyền (chỉ khi người ký không phải đại diện pháp luật), nơi ký.</li>
    <li><strong>Giá trị hợp đồng</strong> (VNĐ): tổng giá trị theo điều khoản. Gõ số, hệ thống tự chèn dấu chấm và đọc thành chữ ngay bên dưới. Để trống nếu chưa chốt giá.</li>
    <li>Bấm <strong>Tạo hợp đồng</strong>. Trang chi tiết của hợp đồng vừa tạo mở ra.</li>
</ol>
<div class="guide-note">
    <p>Form tạo <strong>không có ô ngày nào</strong>, và hợp đồng mới luôn ở trạng thái <strong>Nháp</strong>. Mã, thời hạn (ngày hiệu lực, ngày kết thúc) điền ở trang Quản lý khi đã chốt với khách (<a href="#quan-ly">mục 5</a>); ngày ký do hệ thống ghi lúc bấm Ký (<a href="#ky">mục 6</a>). Hàng hoá thêm ở trang Quản lý; phiếu GQNCKH, phiếu ĐCNB và các giấy tờ khác tích ở tab Giấy tờ (<a href="#tai-lieu">mục 9</a>).</p>
</div>
<div class="guide-warn">
    <p>Admin chưa khai Giám đốc chi nhánh thì bốn ô người ký bên mình để trống và form hiện dải vàng <em>Chưa có thông tin Giám đốc chi nhánh. Admin khai tại trang Thông tin chi nhánh.</em> Hợp đồng vẫn tạo được; khi Admin khai xong, bản Nháp nhận thông tin ở lần bấm Lưu thay đổi kế tiếp. Cách khai: xem hướng dẫn Nhân viên, mục 9.</p>
</div>

<h3 id="loi-tao">3.1. Khi không lưu được</h3>
<p>Trước khi gửi, trang kiểm các ô bắt buộc và hiện chữ đỏ ngay dưới ô còn trống. Nếu hệ thống từ chối sau khi gửi, thông báo đỏ hiện ở đầu form:</p>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Thông tin hợp đồng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.</td><td>Còn ô bắt buộc để trống, đối tác vừa chọn không còn giữ đúng vai (khách hàng mua cho hợp đồng bán, nhà cung cấp cho hợp đồng mua), hoặc người phụ trách Admin vừa chọn đã bị khoá tài khoản.</td></tr>
    <tr><td>Mã hợp đồng này đã có hợp đồng khác dùng. Kiểm lại số trên bản giấy hoặc nhập mã khác.</td><td>Mã trùng với một hợp đồng hoặc phụ lục đã có. Tìm mã đó ở danh sách (nhớ bấm <em>Xem mọi thời điểm</em>). Chưa có số thì để trống ô mã.</td></tr>
    <tr><td>Không lưu được hợp đồng. Vui lòng thử lại.</td><td>Lỗi hệ thống. Thử lại; vẫn lỗi thì báo quản trị viên.</td></tr>
</table>

<h2 id="chi-tiet">4. Xem chi tiết hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Tổng hợp</span></div>
<p>Bấm vào tiêu đề hợp đồng, hoặc nút <i class="fa-regular fa-eye"></i> ở danh sách. Trang này <strong>chỉ để xem</strong>: mọi thao tác làm thay đổi hợp đồng nằm ở trang Quản lý (<a href="#quan-ly">mục 5</a>). Ngoại lệ là hai nút dành cho phòng Tổng hợp: <em>Ghi nhận đã thu</em> (<a href="#cong-no">mục 7.4</a>) và <em>Đã xử lý xong</em> (<a href="#hang-doi-ban-giao">mục 8.1</a>).</p>
<figure class="guide-shot narrow">
    <a href="${img}/05-chi-tiet.png" target="_blank"><img src="${img}/05-chi-tiet.png" alt="Trang chi tiết hợp đồng"></a>
    <figcaption>Hình 5. Trang chi tiết của một bản nháp đã giao hàng mà chưa ký</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Các nhãn</strong>: trạng thái theo lịch, tiến độ, và bước giao hàng nếu là bản nháp (<a href="#hai-loai-trang-thai">mục 1.2</a>).</li>
    <li><strong>Quản lý hợp đồng</strong>: sang trang có các nút thao tác. Chỉ người có quyền sửa mới thấy nút này.</li>
    <li><strong>Tiến trình hợp đồng</strong>: Soạn thảo → Ký hợp đồng → Thanh lý (hoặc Chấm dứt sớm). Mỗi bước đã qua ghi ngày và người làm; bước thanh lý ghi kèm căn cứ. Bản nháp đã giao hàng có thêm dải vàng ghi ngày giao và số ngày đã giao mà chưa ký.</li>
    <li><strong>Thông tin hợp đồng</strong>: mã (chưa có thì ghi <em>Chưa có số</em>), loại, khách hàng (bấm để mở hồ sơ khách), người phụ trách, chiều mua/bán, người ký hai bên (bên mình kèm điện thoại, fax nếu có), nơi ký, giá trị. Thẻ này luôn hiện, dù đang đứng ở tab nào.</li>
    <li><strong>Bảy tab</strong>: Kỳ thanh toán, Bàn giao, Hàng hoá, Giấy tờ, Phụ lục, Nối bán – mua, Nhật ký. Số trong ngoặc là số dòng đang có. Trang nhớ tab bạn mở gần nhất.</li>
</ol>
<div class="guide-note">
    <p>Hợp đồng có tờ <em>Hợp đồng đã ký</em> kèm link ở tab Giấy tờ thì đầu trang có thêm nút <strong>Mở bản PDF</strong>, mở bản đó ở tab mới. Thời hạn (ngày hiệu lực, ngày kết thúc) không hiện ở trang này: xem ở cột <em>Thời hạn</em> của danh sách, hoặc ở trang Quản lý. Hợp đồng hết hạn mà chưa thanh lý thì thanh tiến trình có dải cảnh báo màu vàng.</p>
</div>

<h3 id="nhat-ky">4.1. Nhật ký thay đổi</h3>
<p>Tab <strong>Nhật ký</strong> ghi lại mọi thay đổi của hợp đồng, mới nhất ở trên: ai làm, lúc nào, đổi gì.</p>
<figure class="guide-shot">
    <a href="${img}/06-nhat-ky.png" target="_blank"><img src="${img}/06-nhat-ky.png" alt="Tab Nhật ký"></a>
    <figcaption>Hình 6. Nhật ký của một hợp đồng đã thanh lý</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mốc tiến trình</strong> (chữ đậm, chấm xanh): Ký, Thanh lý, Chấm dứt sớm, kèm trạng thái trước → sau.</li>
    <li><strong>Dòng thao tác thường</strong>: sửa thông tin, thêm/gỡ hàng hoá, tích/bỏ tích giấy tờ, lập/sửa kỳ, nộp hồ sơ, ghi nhận đã thu, bàn giao, nối hợp đồng…</li>
    <li><strong>Dòng có dấu ngoặc kép</strong>: chữ do người dùng tự nhập — căn cứ thanh lý, lý do huỷ, lý do bỏ tích, ghi chú bàn giao — tách khỏi câu do hệ thống ghi.</li>
</ol>
<div class="guide-note"><p>Nhật ký không sửa, không xoá được. Bấm Lưu mà không đổi gì thì không sinh dòng mới.</p></div>

<h2 id="quan-ly">5. Trang Quản lý hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Ở trang chi tiết bấm <strong>Quản lý hợp đồng</strong>, hoặc bấm <i class="fa-solid fa-pen"></i> ở danh sách. Mọi thao tác làm thay đổi hợp đồng đều ở trang này: phần trên là form thông tin, phần dưới là các tab thao tác. Hình 7 là một hồ sơ vừa mở: chưa có số, chưa có thời hạn và giá trị.</p>
<figure class="guide-shot narrow">
    <a href="${img}/07-quan-ly.png" target="_blank"><img src="${img}/07-quan-ly.png" alt="Trang Quản lý của một bản nháp"></a>
    <figcaption>Hình 7. Trang Quản lý của một bản nháp chưa có số, chưa chốt thời hạn</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Thông tin chung</strong> và <strong>thông tin ký kết</strong>: khi còn Nháp sửa được mã (để trống được tới lúc ký), tiêu đề, khách hàng, loại và phần bên đối tác. <em>Người phụ trách</em> và bốn ô <em>người ký bên mình</em> chỉ để xem: mỗi lần bấm Lưu thay đổi, hệ thống chép lại thông tin Giám đốc chi nhánh mới nhất vào bản Nháp.</li>
    <li><strong>Giá trị hợp đồng</strong>.</li>
    <li><strong>Ngày hiệu lực</strong> và <strong>ngày kết thúc</strong>: điền khi đã chốt với khách. Phải có đủ hai ngày thì mới ký được; ngày hiệu lực không được sau ngày kết thúc.</li>
    <li><strong>Ngày ký</strong>: chỉ để xem — hệ thống ghi vào lúc bấm Ký.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>. Trang chi tiết mở ra.</li>
    <li><strong>Các tab thao tác</strong>: Bước tiến trình (mục <a href="#ky">6</a>, <a href="#thanh-ly">12</a>, <a href="#huy-ban-ghi">13</a>), Kỳ thanh toán (<a href="#ky-thanh-toan">mục 7</a>), Bàn giao (<a href="#ban-giao">mục 8</a>), Hàng hoá (<a href="#hang-hoa">mục 5.1</a>), Giấy tờ (<a href="#tai-lieu">mục 9</a>), Phụ lục (<a href="#phu-luc">mục 10</a>), Nối bán – mua (<a href="#noi-ban-mua">mục 11</a>).</li>
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
    <p>Hàng hoá <strong>chỉ thêm/gỡ được khi hợp đồng còn là Nháp</strong>. Ký rồi thì hàng hoá là nội dung hợp đồng: thay đổi phải lập <a href="#phu-luc">phụ lục</a>. Hệ thống không lưu đơn giá từng dòng, cũng không theo dõi số lượng đã giao — tiền của hợp đồng là một con số tổng ở ô Giá trị hợp đồng.</p>
</div>

<h3 id="thay-nguoi-phu-trach">5.2. Thay người phụ trách đã nghỉ việc</h3>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Người phụ trách là người tạo hợp đồng (hoặc Sales do Admin chọn lúc tạo) và không đổi được ở mọi tiến độ. Ngoại lệ duy nhất: người đó đã bị <strong>khoá tài khoản</strong>, thường vì nghỉ việc (xem hướng dẫn Nhân viên, mục 7). Khi đó Admin mở trang Quản lý sẽ thấy ô <strong>Người phụ trách</strong> thành ô chọn, kèm dòng <em>Người phụ trách đã bị khoá tài khoản, chọn người thay.</em> Chọn một nhân viên Sales đang hoạt động rồi bấm <strong>Lưu thay đổi</strong>. Việc thay người được ghi vào Nhật ký.</p>
<div class="guide-note">
    <p>Thay được cả ở bản Nháp lẫn hợp đồng đã ký, trừ hợp đồng đã thanh lý hoặc chấm dứt sớm. Ở bản Nháp, để trống ô chọn thì vẫn giữ người cũ và chỉ lưu các ô khác; ở hợp đồng đã ký thì phải chọn người thay mới lưu được. Mỗi phụ lục có người phụ trách riêng (chép từ hợp đồng gốc lúc lập): thay ở hợp đồng gốc thì thay tiếp ở từng phụ lục còn đứng tên người cũ.</p>
</div>

<h2 id="ky">6. Ký hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Ký là một bước riêng: mở hồ sơ, giao hàng chưa phải là ký. Thương thảo xong với khách thì điền <strong>mã hợp đồng</strong> và <strong>thời hạn</strong> ở form trang Quản lý, bấm Lưu thay đổi, rồi mở tab <strong>Bước tiến trình</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/09-buoc-tien-trinh.png" target="_blank"><img src="${img}/09-buoc-tien-trinh.png" alt="Tab Bước tiến trình của một bản nháp"></a>
    <figcaption>Hình 9. Tab Bước tiến trình của một bản nháp còn thiếu mã và thời hạn</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ký hợp đồng</strong>.</li>
    <li><strong>Huỷ bản ghi</strong>: xoá một bản nháp tạo nhầm (xem <a href="#huy-ban-ghi">mục 13</a>).</li>
    <li>Dòng nhắc <strong>chưa có mã</strong>: bấm Ký sẽ bị từ chối với câu <em>Hợp đồng chưa có mã. Vui lòng nhập mã hợp đồng trước khi ký.</em> Nhập mã ở form phía trên (số 1 ở Hình 7) rồi Lưu thay đổi.</li>
    <li>Dòng nhắc <strong>còn thiếu ngày</strong>: chưa có đủ ngày hiệu lực và ngày kết thúc thì cũng không ký được. Điền hai ngày ở form phía trên (số 3 ở Hình 7), bấm Lưu thay đổi, rồi quay lại tab này.</li>
</ol>
<p>Đủ mã và thời hạn thì hai dòng nhắc biến mất. Bấm <strong>Ký hợp đồng</strong>, đọc hộp xác nhận:</p>
<figure class="guide-shot narrow">
    <a href="${img}/10-ky.png" target="_blank"><img src="${img}/10-ky.png" alt="Hộp xác nhận ký hợp đồng"></a>
    <figcaption>Hình 10. Hộp xác nhận ký</figcaption>
</figure>
<ol class="guide-steps">
    <li>Bấm <strong>Xác nhận</strong>. Hợp đồng chuyển sang <em>Đã ký</em>, ngày ký là ngày hôm nay, nhãn bước giao hàng mất, và trang chi tiết mở ra.</li>
</ol>
<div class="guide-note">
    <p><strong>Ai ký được:</strong> Admin, và Sales chưa có cấp trên trong sơ đồ tổ chức. Ký xong, nhớ tích <em>Hợp đồng đã ký</em> ở tab Giấy tờ (<a href="#tai-lieu">mục 9</a>) và bàn giao cho phòng Tổng hợp (<a href="#ban-giao">mục 8</a>).</p>
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
    <li><strong>Các ô mờ</strong>: mọi ô đều đã khoá, kể cả mã, người phụ trách và người ký bên mình. Muốn đổi điều khoản — thêm hàng, gia hạn, đổi giá trị — thì lập <a href="#phu-luc">phụ lục</a>.</li>
    <li><strong>Chữa sai sót nhập liệu</strong>: chỉ Admin thấy nút này (<a href="#chua-sai-sot">mục 6.2</a>).</li>
</ol>
<div class="guide-note">
    <p>Form của hợp đồng đã ký không có nút <strong>Lưu thay đổi</strong>: nút chỉ hiện khi Admin bật chữa sai sót, hoặc khi Admin đang thay người phụ trách đã bị khoá tài khoản (<a href="#thay-nguoi-phu-trach">mục 5.2</a>). Người ký bên mình giữ đúng người lúc ký, kể cả khi sau này Admin đổi Thông tin chi nhánh.</p>
</div>

<h3 id="chua-sai-sot">6.2. Chữa sai sót nhập liệu</h3>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Dùng khi dữ liệu trên hệ thống gõ sai so với chính bản giấy đã ký — nhầm một chữ số trong mã, chọn nhầm khách hàng. Không dùng để thay đổi điều khoản: việc đó là phụ lục. Bấm <strong>Chữa sai sót nhập liệu</strong> (số 3 ở Hình 11): các ô mở lại để sửa, ô Lý do và nút Lưu thay đổi hiện ra.</p>
<figure class="guide-shot">
    <a href="${img}/12-chua-sai-sot.png" target="_blank"><img src="${img}/12-chua-sai-sot.png" alt="Chế độ chữa sai sót nhập liệu"></a>
    <figcaption>Hình 12. Chế độ chữa sai sót nhập liệu</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Lý do</strong> (bắt buộc): sai ở đâu, đối chiếu với bản giấy nào.</li>
    <li>Sửa ô sai rồi bấm <strong>Lưu thay đổi</strong>.</li>
</ol>
<div class="guide-note">
    <p>Thay đổi được ghi vào Nhật ký dưới tên bạn, kèm lý do. Mã hợp đồng vẫn bắt buộc (không xoá trắng được); ngày ký, người phụ trách và bốn ô người ký bên mình không sửa được. Muốn thoát chế độ này mà không lưu thì tải lại trang. Hợp đồng đã thanh lý hoặc chấm dứt thì không chữa được nữa.</p>
</div>

<h2 id="ky-thanh-toan">7. Kỳ thanh toán</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Lịch thu tiền theo điều khoản thanh toán của hợp đồng, ví dụ tạm ứng 80% sau khi ký, thanh toán 20% còn lại sau khi nghiệm thu. Mỗi kỳ có <strong>loại</strong> (<em>Tạm ứng</em> hoặc <em>Thanh toán</em>), số tiền, điều kiện, số ngày bên mua phải trả kể từ khi nhận hồ sơ, và hạn thu. Ở trang Quản lý, mở tab <strong>Kỳ thanh toán</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/13-thanh-toan.png" target="_blank"><img src="${img}/13-thanh-toan.png" alt="Tab Kỳ thanh toán ở trang Quản lý"></a>
    <figcaption>Hình 13. Tab Kỳ thanh toán: kỳ tạm ứng đã thu, kỳ thanh toán đang chờ thu</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Bảng các kỳ</strong>: loại, số tiền (dòng <em>Thực thu</em> hiện thêm khi tiền về khác số tiền kỳ), điều kiện kèm số ngày phải trả, hạn thu (<em>Chưa có</em> khi chưa nộp hồ sơ).</li>
    <li><strong>Tình trạng</strong>, hệ thống tự suy ra: <em>Chưa nộp hồ sơ</em> → <em>Chờ thu</em> (đã nộp hồ sơ hoặc đã có hạn thu) → <em>Quá hạn</em> (qua hạn thu mà chưa thu, cả dòng tô đỏ) → <em>Đã thu</em> kèm ngày. Dòng nhỏ bên dưới ghi ngày nộp hồ sơ.</li>
    <li><strong>Sửa</strong>: sửa một kỳ chưa thu (<a href="#lap-ky">mục 7.1</a>).</li>
    <li><strong>Đã nộp hồ sơ</strong>: bấm khi đã nộp hồ sơ thanh toán của kỳ này cho bên mua (<a href="#nop-ho-so">mục 7.2</a>).</li>
    <li><strong>Đã thu</strong>: bấm khi tiền về (<a href="#da-thu">mục 7.3</a>).</li>
    <li>Nút <strong>×</strong>: xoá một kỳ lập nhầm (có hộp xác nhận).</li>
    <li><strong>Lập kỳ</strong>: thêm một kỳ mới (<a href="#lap-ky">mục 7.1</a>).</li>
</ol>
<div class="guide-note">
    <p>Kỳ đã thu thì không còn nút nào: không sửa, không xoá, không ghi lại. Lập, sửa, xoá kỳ được cho tới khi hợp đồng thanh lý hoặc chấm dứt. Sau đó vẫn bấm <strong>Đã nộp hồ sơ</strong> và <strong>Đã thu</strong> được cho các kỳ đã lập — tiền bảo hành giữ lại thường về sau thanh lý. Mọi thao tác đều ghi vào Nhật ký.</p>
</div>

<h3 id="lap-ky">7.1. Lập hoặc sửa một kỳ</h3>
<p>Bấm <strong>Lập kỳ</strong> (hoặc <strong>Sửa</strong> ở một dòng chưa thu). Ghi theo đúng điều khoản thanh toán của hợp đồng:</p>
<figure class="guide-shot narrow">
    <a href="${img}/13b-lap-ky.png" target="_blank"><img src="${img}/13b-lap-ky.png" alt="Hộp Lập kỳ thanh toán"></a>
    <figcaption>Hình 14. Hộp Lập kỳ thanh toán (đang điền ví dụ kỳ tạm ứng 80%)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Loại kỳ</strong>: <em>Tạm ứng</em> hoặc <em>Thanh toán</em>.</li>
    <li><strong>% giá trị hợp đồng</strong>: gõ tỷ lệ thì ô Số tiền tự tính sẵn từ giá trị hợp đồng. Ô này chỉ để tính, không lưu; hợp đồng chưa có giá trị thì ô bị tắt.</li>
    <li><strong>Số tiền</strong> (VNĐ, bắt buộc): lớn hơn 0, đọc thành chữ ngay bên dưới. Đây là con số được lưu.</li>
    <li><strong>Điều kiện</strong>: ghi ngắn, ví dụ “sau khi nghiệm thu, bàn giao và nộp đủ hồ sơ thanh toán”.</li>
    <li><strong>Số ngày phải trả</strong> kể từ khi nộp hồ sơ (1 đến 999, không bắt buộc). Có số ngày thì hạn thu tự tính khi bấm Đã nộp hồ sơ.</li>
    <li><strong>Hạn thu</strong>: để trống được — thường tới lúc nộp hồ sơ mới có.</li>
    <li><strong>Ngày đã thu</strong>: chỉ điền khi nhập lại một kỳ đã thu đủ tiền từ trước. Khi sửa kỳ không có ô này.</li>
    <li>Bấm <strong>Lưu kỳ</strong>.</li>
</ol>
<div class="guide-note">
    <p>Tạm ứng không tự trừ dần vào các kỳ sau: lập mỗi kỳ đúng số tiền ghi trong hợp đồng. Phụ lục làm đổi giá trị thì số tiền các kỳ cũ không tự tính lại; sửa tay nếu cần. Sửa một kỳ đã nộp hồ sơ mà có số ngày thì hạn thu tự tính lại theo ngày nộp.</p>
</div>

<h3 id="nop-ho-so">7.2. Ghi nhận đã nộp hồ sơ</h3>
<p>Bấm <strong>Đã nộp hồ sơ</strong> (số 4 ở Hình 13) khi đã gửi hồ sơ thanh toán của kỳ đó cho bên mua (đề nghị thanh toán, hoá đơn, biên bản nghiệm thu… — nhớ tích chúng ở tab Giấy tờ).</p>
<figure class="guide-shot narrow">
    <a href="${img}/13c-nop-ho-so.png" target="_blank"><img src="${img}/13c-nop-ho-so.png" alt="Hộp Đã nộp hồ sơ thanh toán"></a>
    <figcaption>Hình 15. Hộp Đã nộp hồ sơ của một kỳ có số ngày phải trả</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ngày nộp hồ sơ</strong> (bắt buộc): mặc định hôm nay, không được sau hôm nay.</li>
    <li><strong>Hạn thu</strong>: kỳ có số ngày phải trả thì hệ thống tự tính = ngày nộp + số ngày, chỉ hiện ra để xem. Kỳ không có số ngày thì chỗ này là ô <em>Hạn thu</em> để nhập tay theo thoả thuận, để trống được.</li>
    <li>Bấm <strong>Ghi nhận</strong>. Kỳ chuyển sang <em>Chờ thu</em>.</li>
</ol>
<div class="guide-note">
    <p>Ghi sai ngày nộp thì bấm Đã nộp hồ sơ lần nữa: ngày mới thay ngày cũ, hạn thu tính lại.</p>
</div>

<h3 id="da-thu">7.3. Ghi nhận đã thu</h3>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Tổng hợp</span></div>
<p>Bấm <strong>Đã thu</strong> (số 5 ở Hình 13) khi tiền của kỳ đã về. Tài khoản Tổng hợp không mở được trang Quản lý: họ bấm <strong>Ghi nhận đã thu</strong> ở trang chi tiết (<a href="#cong-no">mục 7.4</a>), cùng một hộp như dưới đây.</p>
<figure class="guide-shot narrow">
    <a href="${img}/13d-da-thu.png" target="_blank"><img src="${img}/13d-da-thu.png" alt="Hộp Ghi nhận đã thu"></a>
    <figcaption>Hình 16. Hộp Ghi nhận đã thu</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ngày thu</strong> (bắt buộc): mặc định hôm nay.</li>
    <li><strong>Số tiền thực thu</strong> (bắt buộc, lớn hơn 0): điền sẵn số tiền kỳ. Bên mua trả khác số này (bị phạt, trừ bớt…) thì sửa lại, không cần lý do.</li>
    <li>Bấm <strong>Ghi nhận đã thu</strong>. Kỳ chuyển sang <em>Đã thu</em> và không sửa được nữa.</li>
</ol>
<div class="guide-note">
    <p>Doanh thu ở trang chủ cộng <strong>số tiền thực thu</strong> vào tháng có <strong>ngày thu</strong>, chỉ tính hợp đồng bán (xem hướng dẫn Bắt đầu, mục 5).</p>
</div>

<h3 id="cong-no">7.4. Theo dõi công nợ</h3>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Tổng hợp</span></div>
<p>Ở trang chi tiết, tab <strong>Kỳ thanh toán</strong> có thêm ba con số đối chiếu. Đây cũng là chỗ tài khoản Tổng hợp ghi nhận tiền về.</p>
<figure class="guide-shot">
    <a href="${img}/14-cong-no.png" target="_blank"><img src="${img}/14-cong-no.png" alt="Tab Kỳ thanh toán ở trang chi tiết"></a>
    <figcaption>Hình 17. Kỳ thanh toán ở trang chi tiết, một kỳ đã quá hạn (tài khoản Tổng hợp)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tổng các kỳ đã lập</strong>, <strong>Đã thu</strong> (cộng số tiền thực thu), <strong>Còn phải thu</strong> (các kỳ chưa thu).</li>
    <li><strong>Tình trạng</strong> từng kỳ, như ở trang Quản lý. Kỳ quá hạn tô đỏ.</li>
    <li><strong>Ghi nhận đã thu</strong>: chỉ tài khoản Tổng hợp thấy nút này, ở các kỳ chưa thu. Hộp mở ra giống Hình 16.</li>
</ol>
<div class="guide-note">
    <p>Tổng các kỳ khác giá trị hợp đồng thì tab hiện dải vàng <em>Tổng các kỳ đã lập không khớp giá trị hợp đồng</em> — có thể còn kỳ chưa lập. Hợp đồng có phụ lục thì so cả cụm: giá trị hiện hành (đã cộng các phụ lục đã ký) với tổng các kỳ của hợp đồng gốc và các phụ lục.</p>
</div>

<h2 id="ban-giao">8. Bàn giao cho phòng Tổng hợp</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Ký xong, phòng Kinh doanh chuyển hợp đồng xuống phòng Tổng hợp để làm phần việc của họ: kế toán, theo dõi thu tiền, nhận hồ sơ dự án. Làm lúc nào cũng được, trước hay sau khi ký. Ở trang Quản lý, mở tab <strong>Bàn giao</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/15-ban-giao.png" target="_blank"><img src="${img}/15-ban-giao.png" alt="Tab Bàn giao ở trang Quản lý"></a>
    <figcaption>Hình 18. Tab Bàn giao của một hợp đồng đang chờ phòng Tổng hợp xác nhận</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Các chặng đã bàn giao</strong>: mỗi lượt một dòng — <em>Đang chờ · N ngày</em>, hoặc <em>Xong sau N ngày</em> kèm người xác nhận và ghi chú của phòng Tổng hợp.</li>
    <li><strong>Bàn giao cho phòng</strong>: phòng Tổng hợp, tích sẵn.</li>
    <li><strong>Dặn phòng nhận</strong> (không bắt buộc).</li>
    <li>Bấm <strong>Bàn giao</strong>. Chặng mới mở ra và bắt đầu đếm ngày chờ.</li>
</ol>
<div class="guide-note">
    <p>Phòng Tổng hợp còn đang giữ hợp đồng (chưa báo xong) thì không bàn giao lượt mới được. Còn chặng chưa xong thì trang hiện cảnh báo, nhưng không chặn việc ký. Hợp đồng đã thanh lý thì không mở lượt bàn giao mới; chặng còn treo vẫn đóng được.</p>
</div>

<h3 id="hang-doi-ban-giao">8.1. Hàng đợi Bàn giao xử lý</h3>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Tổng hợp</span></div>
<p>Bấm <strong>Bàn giao xử lý</strong> trên thanh bên trái. Trang liệt kê mọi chặng chưa xong của chi nhánh, chặng chờ lâu nhất đứng đầu. Đây là chỗ phòng Tổng hợp báo đã làm xong phần việc của mình.</p>
<figure class="guide-shot">
    <a href="${img}/16-hang-doi.png" target="_blank"><img src="${img}/16-hang-doi.png" alt="Trang Bàn giao xử lý"></a>
    <figcaption>Hình 19. Trang Bàn giao xử lý (tài khoản phòng Tổng hợp)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Phạm vi</strong>: <em>Toàn chi nhánh</em> (mặc định) hoặc <em>Hợp đồng của tôi</em> (hợp đồng bạn và cấp dưới đứng tên).</li>
    <li><strong>Thẻ phòng</strong>: số chặng phòng Tổng hợp đang giữ. Bấm thẻ để chỉ xem chặng của phòng đó; bấm <em>Tất cả các phòng</em> để bỏ lọc.</li>
    <li><strong>Chặng để lâu nhất</strong>: số ngày của chặng chờ lâu nhất.</li>
    <li><strong>Đã chờ</strong>: xanh dưới 7 ngày, vàng nâu từ 7 ngày, đỏ từ 14 ngày.</li>
    <li><strong>Xác nhận xong</strong>: ghi phòng đã làm gì (bắt buộc) rồi bấm <strong>Xong</strong>. Chỉ người thuộc phòng Tổng hợp (và Admin) thấy ô này; người khác thấy dòng “Chỉ người của phòng Tổng hợp xác nhận được”.</li>
</ol>
<div class="guide-note">
    <p>Phòng Tổng hợp cũng báo xong được ngay ở trang chi tiết hợp đồng, tab Bàn giao — cùng ô ghi chú và nút <strong>Đã xử lý xong</strong>.</p>
</div>

<h2 id="tai-lieu">9. Giấy tờ của hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Tab <strong>Giấy tờ</strong> là danh sách cố định các loại giấy của một hợp đồng, dạng ô tích: tờ nào đã có thì tích và ghi <strong>ngày trên giấy</strong>, số giấy. Nhìn vào là biết hợp đồng đang tới bước nào và còn thiếu giấy gì. Hệ thống <strong>không lưu file</strong>; link bản scan (Google Drive hoặc kho nội bộ) không bắt buộc. Ở trang Quản lý, mở tab <strong>Giấy tờ</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/17-tai-lieu.png" target="_blank"><img src="${img}/17-tai-lieu.png" alt="Tab Giấy tờ ở trang Quản lý"></a>
    <figcaption>Hình 20. Tab Giấy tờ của một bản nháp đã giao hàng</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Loại đã có</strong>: ô đã tích, kèm từng tờ bên dưới.</li>
    <li><strong>Một tờ</strong>: ngày trên giấy, số giấy, tên (nếu có), ghi chú, người thêm và ngày thêm. Tờ có link thì có nút <strong>Mở</strong>.</li>
    <li><strong>Ô trống</strong>: loại chưa có tờ nào. Bấm vào ô để thêm tờ đầu tiên (<a href="#them-to">mục 9.1</a>).</li>
    <li><strong>Thêm tờ</strong>: một loại có thể có nhiều tờ, ví dụ giao hàng nhiều đợt, mỗi kỳ một đề nghị thanh toán.</li>
    <li>Nút <strong>×</strong>: bỏ tích một tờ ghi nhầm — bắt buộc nêu lý do. Tờ biến khỏi danh sách nhưng vẫn ghi trong Nhật ký.</li>
</ol>
<p>Danh mục, theo đúng thứ tự trên màn hình:</p>
<table class="guide-table">
    <tr><th>Giai đoạn</th><th>Loại giấy</th></tr>
    <tr><td>Trước khi ký</td><td>Phiếu GQNCKH, Phiếu ĐCNB, Biên bản giao nhận hàng hoá (tờ ký lúc hàng tới khách, có thể trước ngày ký hợp đồng)</td></tr>
    <tr><td>Ký kết</td><td>Hợp đồng đã ký, Phụ lục đã ký</td></tr>
    <tr><td>Tạm ứng</td><td>Đề nghị tạm ứng, Bảo lãnh tạm ứng</td></tr>
    <tr><td>Hồ sơ thanh toán</td><td>Biên bản nghiệm thu bàn giao, CO, CQ, Đề nghị thanh toán, Hoá đơn GTGT</td></tr>
    <tr><td>Kết thúc</td><td>Biên bản phạt, Biên bản thanh lý</td></tr>
    <tr><td>Khác</td><td>Mọi giấy ngoài danh mục; phải ghi tên giấy tờ</td></tr>
</table>
<div class="guide-note">
    <p>Ba loại đầu quyết định nhãn bước giao hàng của bản nháp (<a href="#hai-loai-trang-thai">mục 1.2</a>): tích Phiếu GQNCKH hoặc Phiếu ĐCNB là <em>Chuẩn bị hàng</em>, tích Biên bản giao nhận hàng hoá là <em>Đã giao – chưa ký</em>, ngày giao là ngày trên biên bản sớm nhất. Bỏ tích biên bản thì nhãn quay về theo giấy còn lại.</p>
    <p>Tích và bỏ tích được ở <strong>mọi tiến độ</strong>, kể cả sau khi thanh lý. Tờ <em>Hợp đồng đã ký</em> có link thì thành nút <strong>Mở bản PDF</strong> ở đầu trang chi tiết. Trang chi tiết có cùng danh sách này nhưng chỉ để xem.</p>
</div>

<h3 id="them-to">9.1. Thêm một tờ</h3>
<p>Bấm ô trống của một loại, hoặc <strong>Thêm tờ</strong> cạnh loại đã có:</p>
<figure class="guide-shot narrow">
    <a href="${img}/17b-them-to.png" target="_blank"><img src="${img}/17b-them-to.png" alt="Hộp Thêm tờ"></a>
    <figcaption>Hình 21. Hộp Thêm tờ (đang điền ví dụ biên bản giao nhận đợt 2)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ngày trên giấy</strong> (bắt buộc): ngày ghi trên tờ giấy, không được sau hôm nay.</li>
    <li><strong>Số giấy</strong> (không bắt buộc, tối đa 50 ký tự).</li>
    <li><strong>Tên giấy tờ</strong>: không bắt buộc, trừ loại <em>Khác</em>.</li>
    <li><strong>Link bản scan</strong> (không bắt buộc): nếu có thì phải bắt đầu bằng <code>http://</code> hoặc <code>https://</code>.</li>
    <li><strong>Ghi chú</strong> (không bắt buộc), ví dụ nơi lưu bản gốc.</li>
    <li>Bấm <strong>Lưu tờ này</strong>. Ô của loại đó được tích, tờ mới hiện bên dưới.</li>
</ol>

<h2 id="phu-luc">10. Phụ lục hợp đồng</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Hợp đồng đã ký không sửa thẳng được. Mọi thay đổi điều khoản — bổ sung hàng, giảm trừ, gia hạn, đổi địa điểm giao — đi qua một <strong>phụ lục</strong>: văn bản riêng có mã riêng, thời hạn riêng, hàng hoá riêng, và cũng phải được <strong>ký</strong>.</p>
<ul>
    <li>Chỉ lập được từ hợp đồng gốc <strong>đã ký và chưa thanh lý / chấm dứt</strong>. Hợp đồng đã thanh lý thì phát sinh sau đó là một hợp đồng mới.</li>
    <li>Chỉ một tầng: không lập phụ lục cho một phụ lục.</li>
    <li>Khách hàng, chiều mua/bán và người phụ trách lấy theo hợp đồng gốc. Người ký bên mình tự điền từ Thông tin chi nhánh như khi tạo hợp đồng.</li>
    <li>Phụ lục hiện thành dòng riêng trên danh sách, mang nhãn “PL của &lt;mã gốc&gt;”.</li>
</ul>
<p>Ở trang Quản lý của hợp đồng gốc, mở tab <strong>Phụ lục</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/18-phu-luc.png" target="_blank"><img src="${img}/18-phu-luc.png" alt="Tab Phụ lục ở trang Quản lý"></a>
    <figcaption>Hình 22. Tab Phụ lục của một hợp đồng đã có một phụ lục</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Danh sách phụ lục</strong>: mã, thời hạn, điều chỉnh giá trị (dấu + là bổ sung, dấu − là giảm trừ), tiến độ, nút Xem.</li>
    <li>Bấm <strong>Lập phụ lục</strong>.</li>
</ol>

<h3 id="lap-phu-luc">10.1. Lập phụ lục</h3>
<figure class="guide-shot narrow">
    <a href="${img}/19-lap-phu-luc.png" target="_blank"><img src="${img}/19-lap-phu-luc.png" alt="Form lập phụ lục"></a>
    <figcaption>Hình 23. Form lập phụ lục (đang điền ví dụ)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Dải nhắc</strong>: phụ lục ra đời ở trạng thái Nháp và phải được ký như một hợp đồng thường.</li>
    <li><strong>Mã phụ lục</strong>: số ghi trên bản phụ lục giấy; để trống được tới lúc ký, như mã hợp đồng.</li>
    <li><strong>Khách hàng</strong>: theo hợp đồng gốc, không đổi được. Ô <em>Người phụ trách</em> bên cạnh cũng vậy.</li>
    <li><strong>Điều chỉnh giá trị</strong>: <em>Bổ sung</em>, <em>Giảm trừ</em>, hoặc <em>Không đổi giá trị</em> (phụ lục chỉ gia hạn hoặc sửa hàng hoá).</li>
    <li><strong>Số tiền điều chỉnh</strong>: nhập số dương, là <strong>phần chênh lệch</strong> — không phải tổng giá trị mới.</li>
    <li>Bấm <strong>Lập phụ lục</strong>. Trang chi tiết của phụ lục mở ra; từ đó bấm Quản lý hợp đồng để điền mã, thời hạn, thêm hàng hoá, rồi <a href="#ky">Ký</a> như hợp đồng thường.</li>
</ol>
<div class="guide-warn">
    <p>Khoản giảm trừ lớn hơn giá trị còn lại của hợp đồng gốc sẽ bị từ chối — thường là do gõ nhầm tổng giá trị mới vào ô chênh lệch.</p>
</div>

<h3 id="gia-tri-hien-hanh">10.2. Giá trị hiện hành</h3>
<p>Khi đã có phụ lục <strong>đã ký</strong> làm đổi giá trị, thẻ Thông tin hợp đồng của hợp đồng gốc hiện thêm dòng <strong>Giá trị hiện hành</strong> cạnh <strong>Giá trị hợp đồng</strong>:</p>
<ul>
    <li><strong>Giá trị hợp đồng</strong>: con số trên bản gốc đã ký — không đổi.</li>
    <li><strong>Giá trị hiện hành</strong> = giá trị gốc cộng điều chỉnh của các phụ lục đã ký.</li>
</ul>
<div class="guide-note">
    <p>Phụ lục còn Nháp chưa được tính; trang chi tiết ghi “Đang có phụ lục chờ ký…”. Phụ lục không đổi giá trị thì không có dòng Giá trị hiện hành. Phụ lục gia hạn <strong>không đổi ngày kết thúc</strong> của hợp đồng gốc: thời hạn mới nằm trên chính phụ lục, nên trạng thái theo lịch của hợp đồng gốc vẫn tính theo ngày trên bản gốc.</p>
</div>

<h2 id="noi-ban-mua">11. Nối hợp đồng bán – mua</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Ghi lại những hợp đồng mua vào sinh ra vì một hợp đồng bán (và ngược lại), để thấy đầu vào của từng hợp đồng. Ở trang Quản lý của hợp đồng gốc, mở tab <strong>Nối bán – mua</strong>.</p>
<figure class="guide-shot">
    <a href="${img}/21-noi-ban-mua.png" target="_blank"><img src="${img}/21-noi-ban-mua.png" alt="Tab Nối bán – mua của một hợp đồng bán"></a>
    <figcaption>Hình 24. Tab Nối bán – mua của một hợp đồng bán đã nối một hợp đồng mua</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ba ô</strong> (chỉ có ở hợp đồng bán): <em>Giá trị bán ra</em> (đã cộng phụ lục), <em>Đầu vào đã nối</em> (tổng giá trị các hợp đồng mua đã nối), <em>Chênh lệch thô</em>.</li>
    <li><strong>Gỡ</strong>: bỏ liên kết (có hộp xác nhận).</li>
    <li><strong>Chọn hợp đồng</strong> ở chiều ngược lại — chỉ hợp đồng gốc, không có phụ lục.</li>
    <li><strong>Ghi chú</strong> (không bắt buộc).</li>
    <li>Bấm <strong>Nối</strong>.</li>
</ol>
<div class="guide-warn">
    <p><strong>Chênh lệch thô không phải lợi nhuận</strong>: chưa trừ chi phí thi công, nhân công, bảo hành. Một hợp đồng mua phục vụ nhiều hợp đồng bán (dòng ghi <em>Đơn mua gom</em>) thì giá trị của nó được tính trọn vào từng hợp đồng bán mà nó phục vụ — hệ thống không tự chia, nên chênh lệch có thể âm.</p>
</div>
<div class="guide-note"><p>Nối được cả khi hợp đồng còn Nháp và cả sau khi thanh lý. Phụ lục không nối được.</p></div>

<h2 id="thanh-ly">12. Thanh lý hoặc chấm dứt sớm</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p><strong>Thanh lý</strong> là lúc hợp đồng coi như xong. <strong>Chấm dứt sớm</strong> là dừng trước hạn. Cả hai đều khoá hợp đồng vĩnh viễn. Tích <em>Biên bản thanh lý</em> ở tab Giấy tờ, rồi ở trang Quản lý của một hợp đồng đã ký, mở tab <strong>Bước tiến trình</strong> và bấm <strong>Thanh lý</strong> (hoặc <strong>Chấm dứt sớm</strong>).</p>
<figure class="guide-shot narrow">
    <a href="${img}/22-thanh-ly.png" target="_blank"><img src="${img}/22-thanh-ly.png" alt="Hộp xác nhận thanh lý"></a>
    <figcaption>Hình 25. Hộp xác nhận thanh lý</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Căn cứ</strong> (bắt buộc): số và ngày biên bản thanh lý, hoặc lý do chấm dứt.</li>
    <li>Bấm <strong>Xác nhận</strong>. Trang chi tiết mở ra.</li>
</ol>
<p>Sau khi thanh lý, thanh tiến trình ở trang chi tiết ghi lại bước này:</p>
<figure class="guide-shot">
    <a href="${img}/23-da-thanh-ly.png" target="_blank"><img src="${img}/23-da-thanh-ly.png" alt="Thanh tiến trình của hợp đồng đã thanh lý"></a>
    <figcaption>Hình 26. Thanh tiến trình của một hợp đồng đã thanh lý</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mốc Thanh lý</strong>: ngày, người thanh lý và căn cứ.</li>
    <li><strong>Dải báo đã đóng băng</strong>.</li>
</ol>
<div class="guide-warn">
    <p><strong>Không quay lại được.</strong> Sau khi thanh lý hoặc chấm dứt, không ai sửa được nội dung, kể cả quản trị viên; không lập phụ lục, không lập, sửa hay xoá kỳ thanh toán, không mở bàn giao mới. Vẫn làm được: bấm Đã nộp hồ sơ và Đã thu cho các kỳ đã lập, tích/bỏ tích giấy tờ, nối bán – mua, đóng chặng bàn giao còn treo. Phát sinh sau đó phải lập hợp đồng mới.</p>
</div>

<h2 id="huy-ban-ghi">13. Xoá bản nháp / Huỷ bản ghi</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Dùng để gỡ một bản ghi tạo nhầm khỏi danh sách — <strong>không phải huỷ hợp đồng ngoài đời</strong>. Ở trang Quản lý, mở tab <strong>Bước tiến trình</strong>, bấm <strong>Huỷ bản ghi</strong> (số 2 ở Hình 9).</p>
<figure class="guide-shot narrow">
    <a href="${img}/24-huy-ban-ghi.png" target="_blank"><img src="${img}/24-huy-ban-ghi.png" alt="Hộp huỷ bản ghi hợp đồng"></a>
    <figcaption>Hình 27. Hộp huỷ bản ghi</figcaption>
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

<h2 id="xuat-file">14. Xuất Excel</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Tổng hợp</span></div>
<p><strong>Xuất Excel</strong> (số 8 ở Hình 1) tải về file <code>.xls</code> chứa <strong>toàn bộ</strong> hợp đồng khớp bộ lọc đang bật — mọi trang, cùng phạm vi, cùng bước giao hàng và cùng dải “Đang xem” —, xếp theo tỉnh. Các cột gồm: STT, Mã HĐ, Phụ lục của, Tiêu đề, Loại HĐ, Tỉnh/Thành phố, Khách hàng, Người phụ trách, Ngày ký, Ngày hiệu lực, Ngày kết thúc, Trạng thái, Tiến độ. Hợp đồng chưa có mã ghi <em>Chưa có số</em>; khách chưa có địa chỉ ghi <em>Chưa xác định</em> ở cột tỉnh.</p>
<h2 id="loi-thuong-gap">15. Thông báo lỗi thường gặp</h2>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Hợp đồng chưa có mã. Vui lòng nhập mã hợp đồng trước khi ký.</td><td>Bấm Quản lý hợp đồng, nhập mã ở form, Lưu thay đổi rồi Ký lại.</td></tr>
    <tr><td>Chưa ký được: hợp đồng còn thiếu ngày hiệu lực hoặc ngày kết thúc…</td><td>Bấm Quản lý hợp đồng, điền đủ hai ngày ở form, Lưu thay đổi rồi Ký lại.</td></tr>
    <tr><td>Mã hợp đồng này đã có hợp đồng khác dùng…</td><td>Mã vừa nhập (hoặc mã của bản nháp đang ký) trùng một hợp đồng, phụ lục khác. Kiểm lại số trên bản giấy.</td></tr>
    <tr><td>Không chuyển được trạng thái — có thể hợp đồng đã được người khác chuyển trước đó.</td><td>Tải lại trang và xem tiến độ hiện tại: có thể đã có người ký hoặc thanh lý trước bạn.</td></tr>
    <tr><td>Không lập được phụ lục: phụ lục chỉ lập từ hợp đồng gốc đã ký và chưa thanh lý…</td><td>Hợp đồng gốc còn Nháp (sửa thẳng, không cần phụ lục), đã thanh lý (lập hợp đồng mới), hoặc chính nó là một phụ lục.</td></tr>
    <tr><td>Khoản giảm trừ lớn hơn giá trị còn lại của hợp đồng gốc…</td><td>Ô Số tiền điều chỉnh nhập phần chênh lệch, không phải tổng giá trị mới.</td></tr>
    <tr><td>Sửa sai sót trên hợp đồng đã ký thì bắt buộc phải nêu lý do.</td><td>Điền ô Lý do ở chế độ chữa sai sót.</td></tr>
    <tr><td>Không thêm được hàng hoá — hàng hoá chỉ sửa được khi hợp đồng còn là bản Nháp…</td><td>Hợp đồng đã được ký: thay đổi hàng hoá phải lập phụ lục.</td></tr>
    <tr><td>Không thêm được hàng hoá: chọn một sản phẩm và nhập số lượng…</td><td>Chọn sản phẩm; số lượng là số nguyên lớn hơn 0.</td></tr>
    <tr><td>Vui lòng nhập ngày trên giấy tờ.</td><td>Hộp Thêm tờ: chọn ngày ghi trên tờ giấy.</td></tr>
    <tr><td>Ngày trên giấy tờ không được sau hôm nay.</td><td>Ghi đúng ngày trên giấy; giấy chưa có thì chưa tích.</td></tr>
    <tr><td>Loại “Khác” phải nhập tên giấy tờ.</td><td>Điền ô Tên giấy tờ.</td></tr>
    <tr><td>Phải nêu lý do thì mới bỏ tích được giấy tờ.</td><td>Điền lý do trong hộp bỏ tích.</td></tr>
    <tr><td>Không lưu được giấy tờ: link (nếu có) phải bắt đầu bằng http:// hoặc https://…</td><td>Dán lại link đầy đủ hoặc để trống; số giấy tối đa 50 ký tự.</td></tr>
    <tr><td>Không lưu được kỳ thanh toán: loại kỳ phải là Tạm ứng hoặc Thanh toán…</td><td>Điều kiện tối đa 255 ký tự; số ngày phải trả là số nguyên từ 1 đến 999.</td></tr>
    <tr><td>Không thực hiện được trên kỳ thanh toán…</td><td>Số tiền phải lớn hơn 0; kỳ đã thu thì không sửa, không nộp hồ sơ hay ghi thu lại được; hợp đồng đã thanh lý hoặc chấm dứt thì không lập, sửa hay xoá kỳ được.</td></tr>
    <tr><td>Ngày nộp hồ sơ không được sau hôm nay.</td><td>Chọn đúng ngày đã nộp hồ sơ cho bên mua.</td></tr>
    <tr><td>Số tiền thực thu phải lớn hơn 0 và không vượt giới hạn cho phép.</td><td>Nhập lại số tiền bên mua đã trả.</td></tr>
    <tr><td>Chọn ít nhất một phòng để bàn giao.</td><td>Tích phòng Tổng hợp.</td></tr>
    <tr><td>Phòng đó đang còn giữ hợp đồng này…</td><td>Đợi phòng Tổng hợp báo xong chặng đang mở rồi mới bàn giao lượt mới.</td></tr>
    <tr><td>Không xác nhận được chặng bàn giao…</td><td>Chặng có thể đã được báo xong trước đó; ô ghi chú là bắt buộc.</td></tr>
    <tr><td>Không nối được hai hợp đồng này…</td><td>Phải là một hợp đồng bán với một hợp đồng mua, và cả hai đều là hợp đồng gốc.</td></tr>
    <tr><td>Hai hợp đồng này đã nối với nhau rồi.</td><td>Không cần nối lại.</td></tr>
    <tr><td>Phải có lý do thì mới huỷ được bản ghi hợp đồng.</td><td>Điền ô Lý do trong hộp huỷ bản ghi.</td></tr>
    <tr><td>Không huỷ được bản ghi này…</td><td>Thường do hợp đồng vừa có phụ lục: huỷ các phụ lục trước.</td></tr>
</table>

<%@ include file="/jsp/guide/_bottom.jspf" %>
