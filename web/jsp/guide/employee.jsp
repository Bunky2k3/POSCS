<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%--
    Hướng dẫn sử dụng -- phân hệ Nhân viên (chỉ Admin dùng).

    Viết theo CODE hiện tại (EmployeeController, EmployeeService, EmployeeDAO,
    EmployeeValidator, AccessControl, AuthenticationFilter, BranchInfoController
    và các JSP trong jsp/admin/), không theo tài liệu cũ. Ba vai Admin / Sales /
    Tổng hợp và ba phòng Ban giám đốc / Kinh doanh / Tổng hợp đọc từ bảng roles,
    departments (Giai đoạn 9). Ví dụ lấy từ dữ liệu mẫu Task 10.6.
    Ảnh ở web/guide/employee/ do tools/guide/capture.mjs chụp theo kịch bản
    tools/guide/shots/employee.mjs -- số trong ol.guide-steps phải khớp số
    khoanh đỏ trên ảnh; sửa một bên thì soát lại bên kia.

    Phần nhân viên tự làm (đăng nhập lần đầu, đổi mật khẩu, bổ sung hồ sơ) chỉ
    tả ngắn ở mục 1.1 -- ảnh của các trang đó thuộc hướng dẫn phần chung.

    Chỉ dùng tập thẻ mô tả ở đầu css/guide.css: tools/guide/ dựng lại đúng nội
    dung này sang file Word.
--%>
<c:set var="guideTitle" value="Nhân viên"/>
<c:set var="img" value="${pageContext.request.contextPath}/guide/employee"/>
<%@ include file="/jsp/guide/_top.jspf" %>

<h1>Nhân viên</h1>
<p class="lead">Tạo và quản lý tài khoản đăng nhập, hồ sơ và địa bàn phụ trách của nhân viên. Chỉ quản trị viên dùng được phân hệ này.</p>

<h2 id="tong-quan">1. Tổng quan</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Trên thanh bên trái, bấm <strong>Nhân viên</strong>. Mục này chỉ hiện với Admin; Sales hay Tổng hợp mở thẳng đường dẫn sẽ bị báo không có quyền. Mỗi nhân viên gồm:</p>
<ul>
    <li><strong>Tài khoản đăng nhập</strong>: tên đăng nhập do hệ thống tự sinh từ họ tên, và mật khẩu tạm Admin gửi qua email cá nhân.</li>
    <li><strong>Vai trò</strong> quyết định nhân viên làm được gì trên phần mềm (bảng dưới).</li>
    <li><strong>Phòng ban</strong> là nơi nhân viên làm việc: <em>Ban giám đốc</em>, <em>Kinh doanh</em> hoặc <em>Tổng hợp</em>. Phòng ban quyết định ai xác nhận được bàn giao hợp đồng: hợp đồng bàn giao cho phòng Tổng hợp thì người thuộc phòng Tổng hợp (và Admin) xác nhận.</li>
    <li><strong>Địa bàn phụ trách</strong>: các tỉnh một Sales cầm (<a href="#dia-ban">mục 3.1</a>).</li>
    <li><strong>Trạng thái</strong>: <em>Đang hoạt động</em>, hoặc <em>Ngừng hoạt động</em> khi đã bị khoá (<a href="#khoa">mục 7</a>).</li>
</ul>
<table class="guide-table">
    <tr><th>Vai trò</th><th>Làm được gì</th></tr>
    <tr><td>Admin</td><td>Toàn quyền, và là vai duy nhất vào được Nhân viên, Thông tin chi nhánh. Không cấp được vai này qua màn hình Nhân viên.</td></tr>
    <tr><td>Sales</td><td>Phụ trách khách hàng và hợp đồng, quản lý sản phẩm, tạo và xử lý phiếu hỗ trợ.</td></tr>
    <tr><td>Tổng hợp</td><td>Xem khách hàng, hợp đồng, sản phẩm (chỉ xem); xác nhận bàn giao hợp đồng gửi cho phòng mình; ghi nhận đã thu một kỳ thanh toán. Không vào phiếu hỗ trợ và Nhân viên.</td></tr>
</table>
<p>Phòng ban và vai trò chọn riêng, phần mềm không bắt cặp hai ô này. Thường thì Ban giám đốc mang vai Admin, phòng Kinh doanh mang vai Sales, phòng Tổng hợp mang vai Tổng hợp.</p>
<p>Mã nhân viên dạng <code>NV-3</code> là số hệ thống tự cấp, không sửa được.</p>
<p>Ngay dưới mục Nhân viên trên thanh bên trái là <strong>Thông tin chi nhánh</strong>: nơi khai Giám đốc chi nhánh, người ký bên mình trên hợp đồng (<a href="#thong-tin-chi-nhanh">mục 9</a>).</p>

<h3 id="vong-doi">1.1. Nhân viên mới nhận tài khoản thế nào</h3>
<table class="guide-table">
    <tr><th>Bước</th><th>Ai làm</th><th>Việc</th></tr>
    <tr><td>1</td><td>Admin</td><td>Tạo hồ sơ nhân viên (<a href="#them-nhan-vien">mục 3</a>). Chưa có email nào được gửi ở bước này.</td></tr>
    <tr><td>2</td><td>Admin</td><td>Ở trang chi tiết, bấm <strong>Gửi thông tin tài khoản</strong> (<a href="#gui-tai-khoan">mục 5</a>): hệ thống gửi tên đăng nhập và một mật khẩu tạm tới email cá nhân.</td></tr>
    <tr><td>3</td><td>Nhân viên</td><td>Đăng nhập bằng tên đăng nhập và mật khẩu tạm. Hệ thống buộc đổi mật khẩu ngay.</td></tr>
    <tr><td>4</td><td>Nhân viên</td><td>Nếu hồ sơ còn thiếu giới tính, ngày sinh, CCCD/CMND hoặc số điện thoại, hệ thống buộc bổ sung trước khi vào các trang khác.</td></tr>
</table>
<div class="guide-note">
    <p>Nhân viên quên mật khẩu thì tự lấy lại bằng mã OTP ở trang đăng nhập, hoặc Admin bấm Gửi thông tin tài khoản để cấp mật khẩu tạm mới.</p>
</div>

<h2 id="danh-sach">2. Xem và tìm nhân viên</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Danh sách hiện 10 nhân viên mỗi trang, người tạo sau cùng đứng đầu. Nhân viên đã bị khoá vẫn nằm trong danh sách để còn mở khoá lại được.</p>
<figure class="guide-shot">
    <a href="${img}/01-danh-sach.png" target="_blank"><img src="${img}/01-danh-sach.png" alt="Danh sách nhân viên"></a>
    <figcaption>Hình 1. Danh sách nhân viên</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Ô tìm kiếm</strong>: gõ họ tên (hoặc một phần, ví dụ <em>Minh Hoàng</em>), tên đăng nhập hoặc số điện thoại rồi nhấn Enter.</li>
    <li><strong>Vai trò</strong>: Admin, Sales hoặc Tổng hợp.</li>
    <li><strong>Trạng thái</strong>: Đang hoạt động hoặc Ngừng hoạt động.</li>
    <li><strong>Thêm nhân viên</strong> (<a href="#them-nhan-vien">mục 3</a>).</li>
    <li><strong>Thẻ nhân viên</strong>: ảnh chữ cái, họ tên, mã NV, phòng ban · vai trò, tên đăng nhập, trạng thái. Bấm vào thẻ để mở trang chi tiết.</li>
    <li>Nhãn <strong>Ngừng hoạt động</strong>: tài khoản đang bị khoá.</li>
    <li><strong>Phân trang</strong>. Chi nhánh có từ 10 nhân viên trở xuống thì chỉ có một trang, như trong hình.</li>
</ol>
<div class="guide-note">
    <p>Đổi ô Vai trò hoặc Trạng thái là danh sách tải lại ngay. Danh sách chưa có ô lọc theo phòng ban.</p>
</div>

<h2 id="them-nhan-vien">3. Thêm nhân viên</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Ở danh sách, bấm <strong>Thêm nhân viên</strong>. Ô có dấu <span style="color:#e2536b">*</span> là bắt buộc.</p>
<figure class="guide-shot narrow">
    <a href="${img}/02-them.png" target="_blank"><img src="${img}/02-them.png" alt="Trang Thêm nhân viên mới"></a>
    <figcaption>Hình 2. Trang Thêm nhân viên mới (đang điền ví dụ)</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Dải nhắc</strong>: tên đăng nhập do hệ thống tự sinh, và tạo xong thì phải gửi tài khoản riêng (<a href="#gui-tai-khoan">mục 5</a>).</li>
    <li><strong>Phòng ban</strong>: Ban giám đốc, Kinh doanh hoặc Tổng hợp.</li>
    <li><strong>Vai trò</strong>: Sales hoặc Tổng hợp. Ô này không có Admin.</li>
    <li><strong>Ngày vào làm</strong>: gõ dạng <code>dd/mm/yyyy</code> hoặc chọn trên lịch.</li>
    <li><strong>Địa bàn phụ trách</strong> (không bắt buộc, chỉ dành cho Sales): bấm để mở bảng tỉnh (<a href="#dia-ban">mục 3.1</a>). Trong hình đã tích một tỉnh chưa ai cầm.</li>
    <li><strong>Họ</strong>, <strong>Tên đệm</strong>, <strong>Tên</strong>. Tên đệm không bắt buộc.</li>
    <li><strong>Giới tính</strong>, <strong>Ngày sinh</strong>, <strong>CCCD/CMND</strong>, <strong>Số điện thoại</strong>: không bắt buộc. Để trống thì nhân viên tự bổ sung ở lần đăng nhập đầu. Nếu điền: ngày sinh phải ở quá khứ; số điện thoại là số di động 10 chữ số bắt đầu bằng <code>03</code>, <code>05</code>, <code>07</code>, <code>08</code> hoặc <code>09</code> (gõ <code>+84</code> thay cho số <code>0</code> đầu cũng được); số điện thoại và CCCD không được trùng với nhân viên khác.</li>
    <li><strong>Email cá nhân</strong>: nơi nhận tên đăng nhập và mật khẩu tạm, nên phải đúng hộp thư của nhân viên.</li>
    <li><strong>Địa chỉ</strong> (không bắt buộc): chọn Tỉnh / Thành phố, chờ danh sách Xã / Phường nạp xong rồi chọn, sau đó nhập Địa chỉ chi tiết.</li>
    <li>Bấm <strong>Tạo nhân viên</strong>. Trang chi tiết của nhân viên vừa tạo mở ra.</li>
</ol>
<div class="guide-note">
    <p>Tên đăng nhập ghép liền họ, tên đệm, tên, bỏ dấu, viết thường: <em>Trần Minh Khoa</em> thành <code>tranminhkhoa</code>. Đã có người trùng thì thêm số: <code>tranminhkhoa2</code>, <code>tranminhkhoa3</code>… Tên đăng nhập không sửa được sau khi tạo.</p>
    <p>Phần mềm chưa có chỗ cấp vai Admin: tài khoản Admin mới (ví dụ một Phó giám đốc) do người quản trị hệ thống tạo trực tiếp trong cơ sở dữ liệu.</p>
</div>

<h3 id="dia-ban">3.1. Địa bàn phụ trách</h3>
<p>Địa bàn là các tỉnh một Sales trực tiếp cầm. Khách hàng mới tạo ở tỉnh nào sẽ tự đứng tên người cầm tỉnh đó, và Sales thấy khách, hợp đồng ở các tỉnh mình cầm trong phạm vi <em>Của tôi</em>. Bảng chỉ liệt kê 18 tỉnh thuộc địa bàn chi nhánh.</p>
<figure class="guide-shot">
    <a href="${img}/03-dia-ban.png" target="_blank"><img src="${img}/03-dia-ban.png" alt="Bảng chọn địa bàn phụ trách"></a>
    <figcaption>Hình 3. Bảng chọn địa bàn trong trang sửa của một Sales đang cầm 4 tỉnh</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tỉnh của người này</strong>: đã tích, chữ đậm, có nhãn <em>của người này</em>. Bỏ tích rồi lưu là trả tỉnh ra.</li>
    <li><strong>Tỉnh người khác đang cầm</strong>: chữ mờ, hiện tên người cầm bên phải, không tích được.</li>
    <li><strong>Tỉnh chưa ai cầm</strong>: chữ thường, không có tên ai, tích được.</li>
    <li><strong>Bỏ hết</strong>: bỏ tích mọi tỉnh của người này. Tỉnh của người khác không bị ảnh hưởng.</li>
    <li><strong>Ô địa bàn</strong> đếm số tỉnh đang tích. Thay đổi chỉ được lưu khi bấm nút lưu của form.</li>
</ol>
<div class="guide-warn">
    <p><strong>Một tỉnh chỉ một người cầm.</strong> Muốn chuyển một tỉnh sang người khác: mở trang Sửa của người đang cầm, bỏ tích tỉnh đó, bấm Lưu thay đổi; rồi mới giao cho người mới. Tỉnh của người đã bị khoá tài khoản vẫn tính là đang có người cầm.</p>
</div>
<div class="guide-note">
    <p>Chỉ giao địa bàn cho Sales. Form không chặn giao tỉnh cho Admin hay Tổng hợp, nhưng khi đó khách hàng mới tạo ở tỉnh ấy sẽ tự đứng tên người đó.</p>
</div>

<h3 id="loi-them">3.2. Khi không lưu được</h3>
<p>Trước khi gửi, trang kiểm các ô bắt buộc và hiện chữ đỏ ngay dưới ô còn thiếu hoặc sai. Nếu hệ thống từ chối sau khi gửi, thông báo đỏ hiện ở đầu form:</p>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Vui lòng nhập đầy đủ và đúng định dạng các trường bắt buộc.</td><td>Còn ô có dấu * để trống, hoặc số điện thoại, email, ngày sinh sai định dạng.</td></tr>
    <tr><td>Số điện thoại này đã được sử dụng bởi tài khoản khác.</td><td>Số điện thoại đã thuộc về một nhân viên khác. Kiểm lại số.</td></tr>
    <tr><td>Số CCCD/CMND này đã được sử dụng bởi tài khoản khác.</td><td>Như trên, với số CCCD/CMND.</td></tr>
    <tr><td>Có tỉnh trong ô Địa bàn phụ trách vừa được giao cho người khác…</td><td>Tỉnh đã tích vừa được giao cho người khác (ví dụ ở một tab khác). Chọn lại địa bàn.</td></tr>
    <tr><td>Không thể tạo nhân viên. Vui lòng thử lại.</td><td>Lỗi hệ thống. Thử lại; vẫn lỗi thì báo người quản trị hệ thống.</td></tr>
</table>

<h2 id="chi-tiet">4. Xem chi tiết nhân viên</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Ở danh sách, bấm vào thẻ của nhân viên.</p>
<figure class="guide-shot narrow">
    <a href="${img}/04-chi-tiet.png" target="_blank"><img src="${img}/04-chi-tiet.png" alt="Trang chi tiết nhân viên"></a>
    <figcaption>Hình 4. Trang chi tiết nhân viên</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Vai trò</strong> và <strong>trạng thái</strong>, dưới họ tên và mã NV.</li>
    <li><strong>Sửa thông tin</strong> (<a href="#sua">mục 6</a>).</li>
    <li><strong>Tên đăng nhập</strong>: tên nhân viên dùng để đăng nhập. Bên cạnh là phòng ban.</li>
    <li><strong>Địa bàn phụ trách</strong>: các tỉnh người này cầm, hoặc <em>Chưa được giao địa bàn nào.</em></li>
    <li><strong>Thông tin cá nhân</strong>. Ô nào nhân viên chưa bổ sung thì ghi <em>Chưa cập nhật</em>.</li>
    <li><strong>Gửi thông tin tài khoản</strong> (<a href="#gui-tai-khoan">mục 5</a>).</li>
    <li><strong>Khóa tài khoản</strong>, hoặc <strong>Mở khóa tài khoản</strong> nếu đang bị khoá (<a href="#khoa">mục 7</a>).</li>
</ol>
<div class="guide-note">
    <p>Người có cấp dưới đang cầm tỉnh còn có thêm dòng <strong>Địa bàn quản lý</strong>: gộp tỉnh của các cấp dưới, không nhập tay (<a href="#cap-tren">mục 8</a>).</p>
</div>

<h2 id="gui-tai-khoan">5. Gửi thông tin tài khoản</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Ở trang chi tiết, bấm <strong>Gửi thông tin tài khoản</strong> (số 6 ở Hình 4). Dùng sau khi tạo nhân viên mới, và cả khi cần cấp lại mật khẩu.</p>
<figure class="guide-shot narrow">
    <a href="${img}/05-gui-tai-khoan.png" target="_blank"><img src="${img}/05-gui-tai-khoan.png" alt="Hộp xác nhận gửi thông tin tài khoản"></a>
    <figcaption>Hình 5. Hộp xác nhận gửi thông tin tài khoản</figcaption>
</figure>
<ol class="guide-steps">
    <li>Kiểm lại <strong>email nhận</strong> và tên nhân viên.</li>
    <li>Bấm <strong>Xác nhận gửi</strong>. Trang báo <em>Đã gửi thông tin tài khoản (mật khẩu tạm mới) tới email cá nhân của nhân viên.</em></li>
</ol>
<div class="guide-warn">
    <p>Mỗi lần gửi là <strong>cấp một mật khẩu tạm mới</strong>: mật khẩu nhân viên đang dùng không còn đăng nhập được, và lần đăng nhập sau họ phải đổi mật khẩu. Đừng bấm gửi với người đang làm việc bình thường nếu họ không cần.</p>
</div>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Nhân viên này chưa có email cá nhân nên không gửi được thông tin tài khoản…</td><td>Bổ sung Email cá nhân ở trang Sửa rồi gửi lại.</td></tr>
    <tr><td>Gửi email thất bại nên chưa cấp mật khẩu mới…</td><td>Email không đi được. Mật khẩu cũ của nhân viên vẫn dùng được. Kiểm địa chỉ email, hoặc báo người quản trị hệ thống kiểm cấu hình gửi thư.</td></tr>
    <tr><td>Không thể gửi thông tin tài khoản. Vui lòng thử lại.</td><td>Email có thể đã tới nhưng mật khẩu trong đó chưa dùng được. Bấm gửi lại để cấp mật khẩu mới.</td></tr>
</table>

<h2 id="sua">6. Sửa thông tin nhân viên</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Ở trang chi tiết, bấm <strong>Sửa thông tin</strong>.</p>
<figure class="guide-shot narrow">
    <a href="${img}/06-sua.png" target="_blank"><img src="${img}/06-sua.png" alt="Trang sửa thông tin nhân viên"></a>
    <figcaption>Hình 6. Trang sửa thông tin nhân viên</figcaption>
</figure>
<ol class="guide-steps">
    <li><strong>Tên đăng nhập</strong>: chỉ để xem, không sửa được.</li>
    <li><strong>Phòng ban</strong>: Ban giám đốc, Kinh doanh hoặc Tổng hợp.</li>
    <li><strong>Vai trò</strong>: Sales hoặc Tổng hợp. Đổi vai có hiệu lực ngay ở lần tải trang kế tiếp của nhân viên, không cần đăng nhập lại.</li>
    <li><strong>Địa bàn phụ trách</strong> (<a href="#dia-ban">mục 3.1</a>).</li>
    <li><strong>Email cá nhân</strong>: bắt buộc. Tài khoản cũ chưa có email thì phải điền mới lưu được.</li>
    <li>Bấm <strong>Lưu thay đổi</strong>. Trang chi tiết mở ra.</li>
</ol>
<div class="guide-note">
    <p>Không nâng được một nhân viên lên vai Admin qua form này. Ô Vai trò chỉ có thêm Admin khi đang sửa một người vốn là Admin; người đó giữ nguyên vai khi lưu. Chọn Sales hoặc Tổng hợp cho một Admin rồi lưu là hạ vai, và không nâng lại lên Admin qua form này được nữa.</p>
</div>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Chưa lưu gì: có tỉnh trong ô Địa bàn phụ trách đang do người khác cầm…</td><td>Một tỉnh đã tích vừa được giao cho người khác (ví dụ ở một tab khác). Chọn lại địa bàn rồi lưu lại.</td></tr>
    <tr><td>Thông tin nhân viên đã lưu, nhưng địa bàn thì chưa…</td><td>Hai người giao cùng một tỉnh gần như cùng lúc. Hồ sơ đã lưu; chọn lại địa bàn rồi bấm Lưu thay đổi.</td></tr>
    <tr><td>Không thể cập nhật nhân viên. Vui lòng thử lại.</td><td>Lỗi hệ thống. Thử lại; vẫn lỗi thì báo người quản trị hệ thống.</td></tr>
</table>
<p>Các thông báo thiếu ô bắt buộc, trùng số điện thoại, trùng CCCD giống như khi thêm nhân viên (<a href="#loi-them">mục 3.2</a>).</p>

<h2 id="khoa">7. Khoá và mở khoá tài khoản</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Khoá khi nhân viên nghỉ việc hoặc tạm thời không được dùng phần mềm. Ở trang chi tiết, bấm <strong>Khóa tài khoản</strong> (số 7 ở Hình 4).</p>
<figure class="guide-shot narrow">
    <a href="${img}/07-khoa.png" target="_blank"><img src="${img}/07-khoa.png" alt="Hộp xác nhận khoá tài khoản"></a>
    <figcaption>Hình 7. Hộp xác nhận khoá tài khoản</figcaption>
</figure>
<ol class="guide-steps">
    <li>Đọc lại tên nhân viên sắp khoá.</li>
    <li>Bấm <strong>Xác nhận</strong>. Trạng thái chuyển sang <em>Ngừng hoạt động</em>.</li>
</ol>
<p>Nhân viên đang bị khoá có nút <strong>Mở khóa tài khoản</strong> thay cho nút Khóa:</p>
<figure class="guide-shot narrow">
    <a href="${img}/08-mo-khoa.png" target="_blank"><img src="${img}/08-mo-khoa.png" alt="Trang chi tiết của một nhân viên đang bị khoá"></a>
    <figcaption>Hình 8. Nhân viên đang bị khoá (Sales đã nghỉ việc, đã trả hết địa bàn)</figcaption>
</figure>
<ol class="guide-steps">
    <li>Nhãn <strong>Ngừng hoạt động</strong>.</li>
    <li>Bấm <strong>Mở khóa tài khoản</strong>, rồi <strong>Xác nhận</strong>. Nhân viên đăng nhập lại được bằng mật khẩu cũ.</li>
</ol>
<div class="guide-note">
    <p>Khoá có hiệu lực <strong>ngay</strong>: người đang đăng nhập bị đưa về trang đăng nhập ở lần bấm kế tiếp, kèm câu <em>Tài khoản này đã bị khóa hoặc ngừng hoạt động…</em> Admin không tự khoá được tài khoản đang đăng nhập của chính mình.</p>
</div>
<div class="guide-warn">
    <p><strong>Khoá không chuyển giao gì cả.</strong> Tỉnh người đó cầm vẫn đứng tên họ; khách hàng, hợp đồng, phiếu hỗ trợ họ phụ trách vẫn giữ nguyên. Khi nhân viên nghỉ việc:</p>
    <p>1. Khoá tài khoản. 2. Mở trang Sửa của người đó, bỏ tích địa bàn, bấm Lưu thay đổi. 3. Giao các tỉnh đó cho người mới. 4. Đổi người phụ trách ở từng khách hàng, hợp đồng (với hợp đồng, chỉ đổi được sau khi đã khoá tài khoản ở bước 1: xem hướng dẫn Hợp đồng, mục 5.2), và người xử lý ở các phiếu hỗ trợ còn mở.</p>
</div>

<h2 id="cap-tren">8. Cấp trên và địa bàn quản lý</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Phần mềm hiểu được sơ đồ hai tầng trong phòng Kinh doanh: <strong>quản lý vùng</strong> và <strong>nhân viên cầm tỉnh</strong> là cấp dưới của họ. Cấp trên chỉ có tác dụng với Sales.</p>
<ul>
    <li>Sales đã có cấp trên thì chỉ <strong>xem</strong> được khách hàng và hợp đồng; tạo, sửa, ký thuộc về cấp trên. Phiếu hỗ trợ và sản phẩm không bị ảnh hưởng.</li>
    <li>Trang chi tiết của quản lý vùng có thêm dòng <strong>Địa bàn quản lý</strong>, gộp tỉnh của các cấp dưới.</li>
</ul>
<div class="guide-note">
    <p>Phần mềm <strong>chưa có chỗ gán cấp trên</strong>: form thêm/sửa không có ô này, và việc gán hiện do người quản trị hệ thống làm trực tiếp trong cơ sở dữ liệu. Hiện chưa ai được gán cấp trên, nên mọi Sales đều làm được như nhau.</p>
</div>

<h2 id="thong-tin-chi-nhanh">9. Thông tin chi nhánh</h2>
<div class="guide-who"><span class="lbl">Ai làm được:</span><span class="role">Admin</span><span class="role no">Sales</span><span class="role no">Tổng hợp</span></div>
<p>Thông tin Giám đốc chi nhánh — người ký bên mình trên mọi hợp đồng. Chi nhánh chỉ có một bản ghi này: khai một lần, sửa khi đổi giám đốc; không có nút tạo hay xoá. Trên thanh bên trái, bấm <strong>Thông tin chi nhánh</strong> (mục chỉ hiện với Admin), sửa các ô rồi bấm <strong>Lưu thay đổi</strong>. Lưu xong, góc trên bên phải báo <em>Đã lưu thông tin chi nhánh.</em></p>
<ul>
    <li><strong>Họ tên Giám đốc chi nhánh</strong> và <strong>Chức vụ</strong>: bắt buộc, tối đa 100 ký tự.</li>
    <li><strong>Điện thoại</strong> và <strong>Fax</strong> (không bắt buộc): tối đa 20 ký tự, có từ 8 tới 15 chữ số; ngoài chữ số chỉ được dấu <code>+</code> ở đầu, khoảng trắng, dấu chấm, gạch ngang và ngoặc đơn. Nhận cả số máy bàn có mã vùng trong ngoặc.</li>
</ul>
<p>Dưới form có dòng <em>Cập nhật lần cuối bởi … lúc …</em> cho biết ai sửa gần nhất.</p>
<div class="guide-note">
    <p>Hợp đồng chép thông tin này lúc tạo hợp đồng, lập phụ lục và mỗi lần lưu bản Nháp (xem hướng dẫn Hợp đồng, mục 3). Hợp đồng đã ký giữ nguyên người ký lúc ký: đổi giám đốc không làm đổi các hợp đồng đó.</p>
</div>
<div class="guide-warn">
    <p>Chưa khai họ tên Giám đốc chi nhánh thì form hợp đồng để trống bốn ô người ký bên mình và hiện dải vàng nhắc Admin khai. Nên khai ngay khi bắt đầu dùng phần mềm.</p>
</div>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Vui lòng nhập họ tên và chức vụ Giám đốc chi nhánh.</td><td>Còn trống ô Họ tên hoặc Chức vụ.</td></tr>
    <tr><td>Số điện thoại không hợp lệ.</td><td>Ô Điện thoại có chữ cái, ít hơn 8 hoặc nhiều hơn 15 chữ số, hoặc dài quá 20 ký tự.</td></tr>
    <tr><td>Số fax không hợp lệ.</td><td>Như trên, với ô Fax.</td></tr>
    <tr><td>Không lưu được thông tin chi nhánh. Vui lòng thử lại.</td><td>Lỗi hệ thống. Thử lại; vẫn lỗi thì báo người quản trị hệ thống.</td></tr>
    <tr><td>Có lỗi khi tải dữ liệu. Vui lòng thử lại.</td><td>Không đọc được thông tin chi nhánh nên trang không hiện form. Tải lại trang; vẫn lỗi thì báo người quản trị hệ thống.</td></tr>
</table>

<h2 id="loi-thuong-gap">10. Thông báo lỗi thường gặp</h2>
<table class="guide-table">
    <tr><th>Thông báo</th><th>Nguyên nhân và cách xử lý</th></tr>
    <tr><td>Bạn không có quyền truy cập nội dung này.</td><td>Sales hoặc Tổng hợp mở trang nhân viên hoặc trang Thông tin chi nhánh. Chỉ Admin vào được.</td></tr>
    <tr><td>Không tìm thấy nhân viên này…</td><td>Link cũ, hoặc mã nhân viên trên đường dẫn không đúng.</td></tr>
    <tr><td>Vui lòng nhập đầy đủ và đúng định dạng các trường bắt buộc.</td><td>Còn ô có dấu * để trống, hoặc số điện thoại, email, ngày sinh sai định dạng.</td></tr>
    <tr><td>Số điện thoại / Số CCCD/CMND này đã được sử dụng bởi tài khoản khác.</td><td>Trùng với một nhân viên khác. Kiểm lại số.</td></tr>
    <tr><td>Có tỉnh trong ô Địa bàn phụ trách… / Thông tin nhân viên đã lưu, nhưng địa bàn thì chưa…</td><td>Tỉnh vừa được giao cho người khác. Chọn lại địa bàn (<a href="#dia-ban">mục 3.1</a>).</td></tr>
    <tr><td>Nhân viên này chưa có email cá nhân…</td><td>Bổ sung email ở trang Sửa rồi gửi lại tài khoản.</td></tr>
    <tr><td>Gửi email thất bại nên chưa cấp mật khẩu mới…</td><td>Mật khẩu cũ vẫn dùng được. Kiểm địa chỉ email hoặc cấu hình gửi thư.</td></tr>
    <tr><td>Không thể tự khóa tài khoản đang đăng nhập của chính bạn.</td><td>Admin không khoá được chính mình. Nhờ một Admin khác nếu thật sự cần.</td></tr>
    <tr><td>Chưa đổi được trạng thái tài khoản. Tài khoản vẫn như trước…</td><td>Lỗi hệ thống khi khoá / mở khoá. Bấm lại; vẫn lỗi thì báo quản trị viên.</td></tr>
</table>

<%@ include file="/jsp/guide/_bottom.jspf" %>
