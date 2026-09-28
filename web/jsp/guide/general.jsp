<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%--
    Hướng dẫn sử dụng -- phần chung (tab "Bắt đầu"): đăng nhập, lần đầu đăng
    nhập, quên / đổi mật khẩu, thanh trên và thanh bên, trang chủ, thông báo,
    thông tin cá nhân, đăng xuất.

    Viết theo CODE hiện tại (AuthenticationController, AuthenticationFilter,
    DashboardController, NotificationController, NotificationScheduler và các
    JSP ở gốc web/), không theo tài liệu cũ. Ảnh ở web/guide/general/ do
    tools/guide/capture.mjs chụp theo kịch bản tools/guide/shots/general.mjs
    -- số trong ol.guide-steps phải khớp số khoanh đỏ trên ảnh; sửa một bên thì
    soát lại bên kia.

    Chỉ dùng tập thẻ mô tả ở đầu css/guide.css: tools/guide/ dựng lại đúng nội
    dung này sang file Word.
--%>
<c:set var="guideTitle" value="Bắt đầu"/>
<c:set var="img" value="${pageContext.request.contextPath}/guide/general"/>
<%@ include file="/jsp/guide/_top.jspf" %>

<h1>Bắt đầu</h1>
<p class="lead">Những việc ai cũng dùng: đăng nhập, lấy lại mật khẩu, trang chủ, thông báo, thông tin cá nhân. Hướng dẫn từng phân hệ nằm ở các tab bên cạnh.</p>

<h2 id="dang-nhap">1. Đăng nhập</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Tài khoản do quản trị viên cấp. Chưa có tài khoản thì liên hệ Phòng Nhân sự / IT.</p>
<figure class="guide-shot">
    <a href="${img}/01-dang-nhap.png" target="_blank"><img src="${img}/01-dang-nhap.png" alt="Trang đăng nhập"></a>
    <figcaption>Hình 1. Trang đăng nhập</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tên đăng nhập</strong>, ví dụ <code>sales4</code>. Đăng nhập bằng tên đăng nhập, không phải bằng email.</li>
    <li><strong>Mật khẩu</strong>. Bấm biểu tượng con mắt để xem lại mật khẩu vừa gõ.</li>
    <li><strong>Quên mật khẩu?</strong> (<a href="#quen-mat-khau">mục 3</a>).</li>
    <li>Bấm <strong>Đăng nhập</strong>. Trang chủ mở ra (<a href="#trang-chu">mục 5</a>).</li>
</ol>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Sai tên đăng nhập hoặc mật khẩu.</td><td>Một trong hai bị sai. Vì lý do bảo mật, trang không nói rõ là cái nào. Gõ lại; không nhớ mật khẩu thì dùng Quên mật khẩu.</td></tr>
    <tr><td>Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau ít phút.</td><td>Sai 5 lần liên tiếp từ cùng một máy thì máy đó bị tạm chặn đăng nhập 15 phút.</td></tr>
    <tr><td>Tài khoản này đã bị khóa hoặc ngừng hoạt động…</td><td>Quản trị viên đã khoá tài khoản. Liên hệ quản trị viên.</td></tr>
</table>
<div class="guide-note">
    <p>Không thao tác quá 30 phút thì phiên đăng nhập hết hạn và phải đăng nhập lại. Tài khoản bị khoá trong lúc đang dùng thì bị đưa ra trang đăng nhập ngay ở lần bấm tiếp theo.</p>
</div>

<h2 id="lan-dau">2. Lần đầu đăng nhập</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Khi cấp tài khoản, quản trị viên gửi tên đăng nhập và một <strong>mật khẩu tạm</strong> tới email cá nhân của bạn. Lần đầu đăng nhập bằng mật khẩu tạm, hệ thống buộc làm hai việc dưới đây trước khi dùng các trang khác. Mở trang khác lúc này sẽ bị đưa về đúng trang đang cần làm.</p>
<h3 id="doi-mat-khau-tam">2.1. Đổi mật khẩu tạm</h3>
<figure class="guide-shot narrow">
    <a href="${img}/02-doi-mat-khau-tam.png" target="_blank"><img src="${img}/02-doi-mat-khau-tam.png" alt="Trang đổi mật khẩu khi đang dùng mật khẩu tạm"></a>
    <figcaption>Hình 2. Đổi mật khẩu tạm (đang điền ví dụ)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Dải nhắc</strong>: bạn đang dùng mật khẩu tạm.</li>
    <li><strong>Mật khẩu hiện tại</strong>: mật khẩu tạm trong email.</li>
    <li><strong>Mật khẩu mới</strong>. Thanh bên dưới cho biết mật khẩu mạnh hay yếu.</li>
    <li><strong>Xác nhận mật khẩu mới</strong>: gõ lại đúng mật khẩu mới.</li>
    <li><strong>Yêu cầu</strong>: tối thiểu 8 ký tự; nên có chữ hoa, chữ thường và số; không trùng mật khẩu cũ.</li>
    <li>Bấm <strong>Cập nhật mật khẩu</strong>. Hệ thống đăng xuất để bạn đăng nhập lại bằng mật khẩu mới.</li>
</ol>
<h3 id="bo-sung-ho-so">2.2. Bổ sung thông tin cá nhân</h3>
<p>Nếu hồ sơ còn thiếu giới tính, ngày sinh, số CCCD/CMND hoặc số điện thoại, sau khi đăng nhập lại bạn được đưa tới trang sửa thông tin cá nhân.</p>
<figure class="guide-shot narrow">
    <a href="${img}/03-bo-sung-ho-so.png" target="_blank"><img src="${img}/03-bo-sung-ho-so.png" alt="Trang sửa thông tin cá nhân khi hồ sơ còn thiếu"></a>
    <figcaption>Hình 3. Bổ sung thông tin cá nhân còn thiếu</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Dải nhắc</strong>: cần bổ sung thông tin trước khi dùng hệ thống.</li>
    <li><strong>Bốn ô dải nhắc kể ra</strong>: giới tính, ngày sinh, số CCCD/CMND, số điện thoại. Ô Giới tính mặc định hiện <em>Nam</em>, nên kiểm lại cho đúng.</li>
    <li><strong>Địa chỉ</strong>: dải nhắc không kể, nhưng trang vẫn bắt buộc chọn Tỉnh / Thành phố và Xã / Phường khi lưu. Email cá nhân cũng phải đúng định dạng.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>. Xong bước này là dùng được mọi trang theo quyền của bạn.</li>
</ol>
<p>Các ô khác và thông báo lỗi xem ở <a href="#sua-ho-so">mục 7.1</a>.</p>

<h2 id="quen-mat-khau">3. Quên mật khẩu</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Ở trang đăng nhập, bấm <strong>Quên mật khẩu?</strong>. Hệ thống gửi một mã OTP tới <strong>email cá nhân trong hồ sơ</strong> của bạn, nên email đó phải đúng (<a href="#sua-ho-so">mục 7.1</a>).</p>
<h3 id="quen-b1">3.1. Bước 1: nhập tên đăng nhập</h3>
<figure class="guide-shot">
    <a href="${img}/04-quen-mat-khau.png" target="_blank"><img src="${img}/04-quen-mat-khau.png" alt="Trang quên mật khẩu"></a>
    <figcaption>Hình 4. Nhập tên đăng nhập để nhận mã</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tên đăng nhập</strong> của bạn.</li>
    <li>Bấm <strong>Gửi mã OTP</strong>.</li>
    <li>Mã gồm 6 chữ số, có hiệu lực <strong>5 phút</strong>. Không thấy email thì kiểm cả hộp thư rác (Spam).</li>
</ol>
<div class="guide-note">
    <p>Vì lý do bảo mật, trang luôn chuyển sang bước 2 dù tên đăng nhập có tồn tại hay không. Không nhận được mã thì kiểm lại tên đăng nhập, hoặc nhờ quản trị viên xem email cá nhân trong hồ sơ.</p>
</div>
<h3 id="quen-b2">3.2. Bước 2: nhập mã OTP</h3>
<figure class="guide-shot">
    <a href="${img}/05-nhap-otp.png" target="_blank"><img src="${img}/05-nhap-otp.png" alt="Trang nhập mã OTP"></a>
    <figcaption>Hình 5. Nhập mã OTP (mã trong hình là ví dụ)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Sáu ô mã</strong>: gõ từng số, hoặc dán cả mã từ email.</li>
    <li><strong>Thời gian còn hiệu lực</strong> của mã, đếm ngược từ 05:00.</li>
    <li>Bấm <strong>Xác nhận</strong>.</li>
    <li><strong>Gửi lại mã</strong>: mở ra sau 30 giây đếm ngược. Mã mới thay mã cũ; mã cũ không dùng được nữa.</li>
</ol>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Mã OTP không đúng, vui lòng thử lại.</td><td>Gõ lại cho đúng mã trong email mới nhất.</td></tr>
    <tr><td>Mã OTP đã hết hạn…</td><td>Quá 5 phút. Bấm Gửi lại mã.</td></tr>
    <tr><td>Bạn đã nhập sai mã OTP quá nhiều lần…</td><td>Sai 5 lần thì mã bị huỷ và bạn quay về bước 1 để nhận mã mới.</td></tr>
</table>
<h3 id="quen-b3">3.3. Bước 3: đặt mật khẩu mới</h3>
<figure class="guide-shot">
    <a href="${img}/06-dat-lai-mat-khau.png" target="_blank"><img src="${img}/06-dat-lai-mat-khau.png" alt="Trang đặt lại mật khẩu"></a>
    <figcaption>Hình 6. Đặt mật khẩu mới</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Mật khẩu mới</strong>, kèm thanh độ mạnh.</li>
    <li><strong>Xác nhận mật khẩu mới</strong>.</li>
    <li><strong>Yêu cầu</strong>: tối thiểu 8 ký tự; nên có chữ hoa, chữ thường và số; không trùng mật khẩu cũ.</li>
    <li>Bấm <strong>Đặt lại mật khẩu</strong>. Trang đăng nhập mở ra với dòng <em>Đổi mật khẩu thành công</em>; đăng nhập bằng mật khẩu mới.</li>
</ol>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Mật khẩu mới phải có ít nhất 8 ký tự.</td><td>Mật khẩu quá ngắn.</td></tr>
    <tr><td>Mật khẩu xác nhận không khớp.</td><td>Hai ô gõ khác nhau.</td></tr>
    <tr><td>Mật khẩu mới không được trùng với mật khẩu cũ.</td><td>Chọn một mật khẩu khác mật khẩu đang dùng.</td></tr>
</table>

<h2 id="giao-dien">4. Thanh trên và thanh bên</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<figure class="guide-shot">
    <a href="${img}/07-thanh-tren.png" target="_blank"><img src="${img}/07-thanh-tren.png" alt="Thanh trên, thanh bên và menu tài khoản"></a>
    <figcaption>Hình 7. Thanh trên, thanh bên và menu tài khoản đang mở</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Nút ≡</strong>: thu gọn hoặc mở thanh bên. Bấm logo POSCS là về trang chủ.</li>
    <li><strong>Thanh bên</strong>: các phân hệ. Mục đang mở được tô xanh. Mục <em>Nhân viên</em> chỉ Admin thấy.</li>
    <li><strong>Chuông</strong>: chấm đỏ nghĩa là có thông báo chưa đọc (<a href="#thong-bao">mục 6</a>).</li>
    <li>Bấm <strong>ảnh đại diện</strong> để mở menu tài khoản: họ tên, vai trò, <em>Thông tin cá nhân</em> (<a href="#ho-so">mục 7</a>), <em>Đổi mật khẩu</em> (<a href="#doi-mat-khau">mục 8</a>), <em>Đăng xuất</em> (<a href="#dang-xuat">mục 9</a>).</li>
</ol>

<h2 id="trang-chu">5. Trang chủ</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Số liệu khách hàng, hợp đồng và doanh thu. Tài khoản Sales mặc định xem phần việc của mình; Admin và Kỹ thuật mặc định xem toàn chi nhánh.</p>
<figure class="guide-shot">
    <a href="${img}/08-trang-chu.png" target="_blank"><img src="${img}/08-trang-chu.png" alt="Trang chủ"></a>
    <figcaption>Hình 8. Trang chủ của một tài khoản Sales</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Lời chào</strong> và phạm vi số liệu đang xem, kèm ngày hôm nay.</li>
    <li><strong>Phạm vi</strong>: <em>Của tôi</em> gồm khách, hợp đồng do bạn (hoặc cấp dưới) đứng tên, cộng mọi việc ở các tỉnh bạn phụ trách; <em>Toàn chi nhánh</em> là tất cả.</li>
    <li><strong>Tỉnh</strong>: chỉ tính các tỉnh đã chọn (<a href="#loc-tinh">mục 5.1</a>).</li>
    <li><strong>Kỳ</strong>: <em>Mọi thời điểm</em>, cả năm, một quý hoặc một tháng. Đổi năm bằng hai nút ‹ ›.</li>
    <li><strong>Ba ô số liệu</strong>: tổng khách hàng (kèm số khách mới), hợp đồng đang hiệu lực (kèm số sắp hết hạn), doanh thu hợp đồng (so với kỳ trước). Doanh thu chỉ tính hợp đồng bán.</li>
    <li><strong>Bảng Hợp đồng</strong>: các hợp đồng còn hiệu lực trong kỳ đang chọn; chưa chọn kỳ thì là tháng hiện tại. Giá trị đã cộng các phụ lục đã ký.</li>
    <li><strong>Bảng Khách hàng</strong>: khách mới nhất trong phạm vi đang xem. Bấm <em>Xem tất cả</em> ở mỗi bảng để mở danh sách đầy đủ.</li>
</ol>
<h3 id="loc-tinh">5.1. Lọc theo tỉnh</h3>
<figure class="guide-shot">
    <a href="${img}/09-loc-tinh.png" target="_blank"><img src="${img}/09-loc-tinh.png" alt="Bảng chọn tỉnh ở trang chủ"></a>
    <figcaption>Hình 9. Bảng chọn tỉnh đang mở</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tích các tỉnh</strong> cần xem. Tỉnh bạn phụ trách có nhãn <em>của bạn</em>.</li>
    <li><strong>Địa bàn của tôi</strong>: chọn nhanh đúng các tỉnh bạn phụ trách. <em>Chọn tất cả</em> và <em>Bỏ hết</em> ở cùng hàng.</li>
    <li>Bấm <strong>Áp dụng</strong>. Không tích tỉnh nào là xem cả 18 tỉnh địa bàn.</li>
</ol>

<h2 id="thong-bao">6. Thông báo</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Mỗi giờ hệ thống tự kiểm và gửi thông báo khi:</p>
<ul>
    <li>một hợp đồng bạn phụ trách chuyển sang <em>Sắp hết hạn</em> (còn 30 ngày trở xuống);</li>
    <li>một phiếu hỗ trợ giao cho bạn sắp hoặc đã quá hạn xử lý (SLA).</li>
</ul>
<p>Mỗi hợp đồng, mỗi phiếu chỉ được báo một lần.</p>
<figure class="guide-shot narrow">
    <a href="${img}/10-chuong.png" target="_blank"><img src="${img}/10-chuong.png" alt="Chuông thông báo đang mở"></a>
    <figcaption>Hình 10. Chuông thông báo trên thanh trên</figcaption>
</figure>
<ol class="guide-steps">
    <li>Bấm <strong>chuông</strong>: hiện 5 thông báo gần nhất và số thông báo mới.</li>
    <li>Bấm <strong>một thông báo</strong> để mở thẳng hợp đồng hoặc phiếu nó nói tới. Thông báo đó tự chuyển thành đã đọc.</li>
    <li><strong>Xem tất cả thông báo</strong>.</li>
</ol>
<figure class="guide-shot">
    <a href="${img}/11-thong-bao.png" target="_blank"><img src="${img}/11-thong-bao.png" alt="Trang thông báo"></a>
    <figcaption>Hình 11. Trang Thông báo</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Đánh dấu tất cả đã đọc</strong>.</li>
    <li>Bấm <strong>nội dung thông báo</strong> để mở hợp đồng hoặc phiếu, như ở chuông. Thông báo chưa đọc in đậm.</li>
    <li><strong>Đánh dấu đã đọc</strong>: chỉ đánh dấu, không mở gì.</li>
</ol>

<h2 id="ho-so">7. Thông tin cá nhân</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm ảnh đại diện, chọn <strong>Thông tin cá nhân</strong>.</p>
<figure class="guide-shot narrow">
    <a href="${img}/12-ho-so.png" target="_blank"><img src="${img}/12-ho-so.png" alt="Trang thông tin cá nhân"></a>
    <figcaption>Hình 12. Trang Thông tin cá nhân</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Thông tin công việc</strong>: tên đăng nhập, phòng ban, vai trò, ngày vào làm. Chỉ Admin sửa được, ở phân hệ Nhân viên.</li>
    <li><strong>Sửa thông tin</strong> (<a href="#sua-ho-so">mục 7.1</a>).</li>
    <li><strong>Địa chỉ</strong>. Chưa khai thì ghi <em>Chưa cập nhật địa chỉ</em>.</li>
</ol>
<h3 id="sua-ho-so">7.1. Sửa thông tin cá nhân</h3>
<figure class="guide-shot narrow">
    <a href="${img}/13-sua-ho-so.png" target="_blank"><img src="${img}/13-sua-ho-so.png" alt="Trang sửa thông tin cá nhân"></a>
    <figcaption>Hình 13. Trang Sửa thông tin cá nhân</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ảnh đại diện</strong>: bấm biểu tượng máy ảnh, chọn ảnh JPG, PNG, GIF hoặc WEBP. Chưa có ảnh thì hiện chữ cái đầu của tên.</li>
    <li><strong>Thông tin công việc</strong>: chỉ để xem.</li>
    <li><strong>Số điện thoại</strong>: số di động bắt đầu bằng <code>0</code> hoặc <code>+84</code>, không trùng với nhân viên khác.</li>
    <li><strong>Email cá nhân</strong>: nơi nhận mật khẩu tạm và mã OTP khi quên mật khẩu, nên phải là hộp thư của chính bạn.</li>
    <li><strong>Địa chỉ</strong> (bắt buộc): chọn Tỉnh / Thành phố, chờ danh sách Xã / Phường nạp xong rồi chọn. Địa chỉ chi tiết không bắt buộc.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>. Trang Thông tin cá nhân mở lại với thông tin mới.</li>
</ol>
<p>Bắt buộc: họ, tên, giới tính, ngày sinh (phải ở quá khứ), số CCCD/CMND, số điện thoại, email cá nhân và địa chỉ tới Xã / Phường. Nếu hệ thống từ chối, thông báo đỏ hiện ở đầu trang:</p>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Vui lòng nhập đầy đủ Họ, Tên và Số CCCD/CMND.</td><td>Còn ô bắt buộc để trống.</td></tr>
    <tr><td>Số điện thoại không hợp lệ.</td><td>Không phải số di động Việt Nam dạng <code>0…</code> hoặc <code>+84…</code>.</td></tr>
    <tr><td>Vui lòng chọn ngày sinh hợp lệ trong quá khứ.</td><td>Ngày sinh trống hoặc ở tương lai.</td></tr>
    <tr><td>Địa chỉ email không hợp lệ.</td><td>Email cá nhân trống hoặc sai định dạng.</td></tr>
    <tr><td>Vui lòng chọn Tỉnh/Thành phố và Xã/Phường.</td><td>Chưa chọn xã / phường. Địa chỉ là bắt buộc.</td></tr>
    <tr><td>Số điện thoại (hoặc số CCCD/CMND) này đã được dùng cho một nhân viên khác…</td><td>Kiểm lại số; đúng là của bạn thì báo quản trị viên.</td></tr>
    <tr><td>Họ tên và địa chỉ không được chứa ký tự &lt; &gt; "…</td><td>Bỏ các ký tự đó.</td></tr>
    <tr><td>Ảnh đại diện chỉ nhận file JPG, PNG, GIF hoặc WEBP…</td><td>Chọn lại ảnh đúng định dạng.</td></tr>
</table>

<h2 id="doi-mat-khau">8. Đổi mật khẩu</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm ảnh đại diện, chọn <strong>Đổi mật khẩu</strong>. Trang giống <a href="#doi-mat-khau-tam">Hình 2</a> nhưng không có dải nhắc: nhập mật khẩu hiện tại, mật khẩu mới, gõ lại mật khẩu mới rồi bấm <strong>Cập nhật mật khẩu</strong>. Đổi xong, hệ thống đăng xuất để bạn đăng nhập lại bằng mật khẩu mới.</p>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Mật khẩu hiện tại không đúng.</td><td>Gõ lại mật khẩu đang dùng. Không nhớ thì đăng xuất rồi dùng Quên mật khẩu.</td></tr>
    <tr><td>Mật khẩu mới phải có ít nhất 8 ký tự.</td><td>Mật khẩu quá ngắn.</td></tr>
    <tr><td>Mật khẩu xác nhận không khớp.</td><td>Hai ô gõ khác nhau.</td></tr>
    <tr><td>Mật khẩu mới không được trùng với mật khẩu cũ.</td><td>Chọn một mật khẩu khác.</td></tr>
</table>

<h2 id="dang-xuat">9. Đăng xuất</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role">Sales</span><span class="role">Kỹ thuật</span></div>
<p>Bấm ảnh đại diện, chọn <strong>Đăng xuất</strong> (<a href="#giao-dien">Hình 7</a>). Mở lại trang đăng nhập, ví dụ bấm nút quay lại của trình duyệt về tới trang đó, cũng tự đăng xuất.</p>
<div class="guide-warn">
    <p>Dùng máy chung thì luôn đăng xuất khi xong việc.</p>
</div>

<%@ include file="/jsp/guide/_bottom.jspf" %>
