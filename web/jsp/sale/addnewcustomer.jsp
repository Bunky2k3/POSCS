<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần: validate BR-09 (SĐT), BR-10 (email), ngày tham gia không ở tương lai,
    tự sinh Mã khách hàng duy nhất, tạo/chọn địa chỉ (INSERT vào addresses nếu cần) rồi
    INSERT vào bảng enterprises.

    Request attribute cần có trước khi forward tới trang này:
      - userList     : List<poscs.model.User>     (để đổ dropdown "Nhân viên phụ trách")
      - provinceList : List<poscs.model.Province>  (để đổ dropdown "Tỉnh / Thành phố")

    Dropdown "Xã / Phường" KHÔNG đổ sẵn từ server -- JS nạp qua AJAX
    (GET /address/wards?provinceId=..., xem AddressController) ngay khi
    chọn tỉnh, thay vì đổ sẵn toàn bộ ~3.321 xã/phường vào trang.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thêm khách hàng - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 900px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 22px; flex-wrap: wrap; gap: 14px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 24px 28px 28px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 24px 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row label .req { color: var(--danger); }

        .form-control, .form-select {
            padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb;
            background-color: #f9fafb; font-size: 0.9rem;
        }
        .form-control:focus, .form-select:focus {
            background-color: #ffffff; border-color: var(--primary-light);
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
        }

        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }

        /* Hai vai là hai ô tick ĐỘC LẬP, không phải radio: khách vừa mua vừa
           bán thì tick cả hai. Viền + nền để nhìn ra ngay đây là ô chọn được,
           khác hẳn dòng chữ thường. */
        .role-check-group { display: flex; gap: 10px; flex-wrap: wrap; }
        .role-check {
            display: inline-flex; align-items: center; gap: 8px; cursor: pointer;
            padding: 0.55rem 0.9rem; border: 1px solid #e5e7eb; border-radius: 10px;
            background: #f9fafb; font-size: 0.86rem; font-weight: 600; color: #374151;
            text-transform: none; letter-spacing: 0; margin-bottom: 0;
        }
        .role-check:hover { border-color: var(--primary-light); background: #fff; }
        .role-check small { color: #9ca3af; font-weight: 500; }
        .role-check input { width: 15px; height: 15px; accent-color: var(--primary); }

        /* ===== Logo doanh nghiệp ===== */
        .logo-upload-wrap { display: flex; flex-direction: column; align-items: center; margin-bottom: 24px; }
        .logo-avatar-wrap { position: relative; width: 92px; height: 92px; }
        .logo-avatar-preview {
            width: 92px; height: 92px; border-radius: 50%; overflow: hidden;
            background: #eaf6ff; color: var(--primary);
            display: flex; align-items: center; justify-content: center; font-size: 2rem;
            border: 3px solid #eef2f6;
        }
        .logo-avatar-preview img { width: 100%; height: 100%; object-fit: cover; }
        .logo-edit-btn {
            position: absolute; bottom: 0; right: 0; width: 30px; height: 30px; border-radius: 50%;
            background: #fff; color: var(--primary); display: flex; align-items: center; justify-content: center;
            cursor: pointer; box-shadow: 0 2px 8px rgba(0,0,0,0.18); border: 2px solid var(--primary-light);
        }
        .logo-upload-hint { font-size: 0.78rem; color: #9ca3af; margin-top: 10px; }

        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            border: none; border-radius: 10px; padding: 0.6rem 1.4rem;
            font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel {
            background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280;
            border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem;
            text-decoration: none; display: inline-flex; align-items: center;
        }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) { .card-box { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="customer" scope="request"/>
        <%-- Trang này không có tham số kind trên URL, nên suy mục con cần tô
             sáng từ chính vai của khách: chỉ khi khách CHỈ là bên bán mới sáng
             "Nhà cung cấp". Khách hai vai thì sáng "Khách hàng mua" -- phải
             chọn một, và đó là danh sách mặc định. --%>
        <c:set var="activeCustomerKind" scope="request"
               value="${not empty customerRoles and customerRoles.contains('Nhà cung cấp') and not customerRoles.contains('Khách mua') ? 'supplier' : 'buyer'}"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/customer" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <div class="page-header-row">
            <div>
                <h2>Thêm khách hàng</h2>
                <p>Tạo mới hồ sơ khách hàng doanh nghiệp</p>
            </div>
        </div>

        <div class="card-box">
            <c:if test="${not empty param.error}">
                <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                    <c:choose>
                        <c:when test="${param.error == 'invalid_image_type'}">Logo chỉ nhận file ảnh JPG, PNG, GIF hoặc WEBP. Vui lòng chọn lại.</c:when>
                        <c:when test="${param.error == 'invalid'}">Thông tin khách hàng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.</c:when>
                        <c:when test="${param.error == 'create_failed'}">Không lưu được khách hàng. Có thể mã số thuế, email hoặc số điện thoại đã tồn tại.</c:when>
                        <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                    </c:choose>
                </div>
            </c:if>

            <form id="createCustomerForm" action="${pageContext.request.contextPath}/customer" method="POST" enctype="multipart/form-data" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="create">

                <div class="section-header"><h5>Thông tin khách hàng</h5></div>

                <div class="logo-upload-wrap">
                    <div class="logo-avatar-wrap">
                        <div class="logo-avatar-preview" id="logoPreview"><i class="fa-solid fa-building"></i></div>
                        <label class="logo-edit-btn" for="logoInput"><i class="fa-solid fa-camera"></i></label>
                        <input type="file" name="logo" id="logoInput" accept=".jpg,.jpeg,.png,.gif,.webp" hidden onchange="previewLogo(this)">
                    </div>
                    <div class="logo-upload-hint">Logo doanh nghiệp (không bắt buộc)</div>
                </div>

                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Tên khách hàng <span class="req">*</span></label>
                        <input type="text" class="form-control" id="customerName" name="customerName" placeholder="VD: VNPT Hà Nội">
                        <span class="error-text" id="err-customerName">Tên khách hàng không được để trống.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Mã số thuế <span class="req">*</span></label>
                        <input type="text" class="form-control" id="taxCode" name="taxCode" placeholder="VD: 0100100026">
                        <span class="error-text" id="err-taxCode">Mã số thuế không được để trống.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Loại khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customerType" name="customerType">
                            <option value="">-- Chọn loại khách hàng --</option>
                            <%-- Loại khách hàng theo VAI, đổ từ CustomerController --%>
                            <c:forEach var="ct" items="${customerTypeOptions}">
                                <option value="${fn:escapeXml(ct)}">${fn:escapeXml(ct)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-customerType">Vui lòng chọn loại khách hàng.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Nhóm khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customerGroup" name="customerGroup">
                            <option value="">-- Chọn nhóm khách hàng --</option>
                            <option value="VIP">Khách hàng VIP</option>
                            <option value="Thân thiết">Khách hàng thân thiết</option>
                            <option value="Tiềm năng">Khách hàng tiềm năng</option>
                            <option value="Thường">Khách hàng thường</option>
                        </select>
                        <span class="error-text" id="err-customerGroup">Vui lòng chọn nhóm khách hàng.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Vai của khách hàng <span class="req">*</span></label>
                        <%-- Hai vai độc lập, không phải hai lựa chọn loại trừ: một công
                             ty vừa mua thiết bị vừa cung cấp linh kiện thì tick cả hai và
                             nó xuất hiện ở cả hai danh sách. Đó là lý do vai nằm ở bảng
                             riêng chứ không phải một cột trên enterprises (xem V20). --%>
                        <div class="role-check-group">
                            <label class="role-check"><input type="checkbox" name="roles" value="Khách mua"
                                ${customerRoles.contains('Khách mua') ? 'checked' : ''}> Khách hàng mua <small>(mua của mình)</small></label>
                            <label class="role-check"><input type="checkbox" name="roles" value="Nhà cung cấp"
                                ${customerRoles.contains('Nhà cung cấp') ? 'checked' : ''}> Nhà cung cấp <small>(bán cho mình)</small></label>
                        </div>
                        <span class="error-text" id="err-roles">Chọn ít nhất một vai.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Người phụ trách chính <span class="req">*</span></label>
                        <select class="form-select" id="assignee" name="accountOwnerId">
                            <option value="">-- Chọn nhân viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}">${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <%-- Ô select bị disabled thì trình duyệt KHÔNG gửi giá trị lên.
                             Input này gánh giá trị lúc ô bị khoá; lúc ô mở thì nó tự
                             disabled để không gửi hai giá trị cùng tên. --%>
                        <input type="hidden" id="accountOwnerHidden" name="accountOwnerId" disabled>
                        <div id="goiYDiaBan" class="text-muted" style="display:none; font-size: 0.8rem; margin-top: 6px; text-transform: none; font-weight: 400;"></div>
                        <span class="error-text" id="err-assignee">Vui lòng chọn người phụ trách chính.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Người hỗ trợ</label>
                        <select class="form-select" id="supportAssignee" name="supportOwnerId">
                            <option value="">-- Chưa bố trí --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}">${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-supportAssignee">Người hỗ trợ phải khác người phụ trách chính.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Số điện thoại <span class="req">*</span></label>
                        <input type="tel" class="form-control" id="phone" name="phone" placeholder="VD: 0912345678">
                        <span class="error-text" id="err-phone">Số điện thoại không hợp lệ.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Email <span class="req">*</span></label>
                        <input type="email" class="form-control" id="email" name="email" placeholder="contact@company.vn">
                        <span class="error-text" id="err-email">Vui lòng nhập địa chỉ email hợp lệ.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Website</label>
                        <input type="text" class="form-control" id="website" name="website" placeholder="https://...">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Ngày tham gia</label>
                        <input type="date" class="form-control" id="joinDate" name="joinDate">
                        <span class="error-text" id="err-joinDate">Ngày tham gia không được là ngày trong tương lai.</span>
                    </div>
                </div>

                <div class="section-header"><h5>Địa chỉ</h5></div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Tỉnh / Thành phố <span class="req">*</span></label>
                        <select class="form-select" id="province" name="provinceId">
                            <option value="">-- Chọn tỉnh / thành phố --</option>
                            <c:forEach var="prov" items="${provinceList}">
                                <option value="${prov.provinceId}">${fn:escapeXml(prov.shortName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-province">Vui lòng chọn tỉnh / thành phố.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Xã / Phường <span class="req">*</span></label>
                        <select class="form-select" id="district" name="districtId">
                            <option value="">-- Chọn tỉnh / thành phố trước --</option>
                        </select>
                        <span class="error-text" id="err-district">Vui lòng chọn xã / phường.</span>
                    </div>
                    <div class="col-12 field-row">
                        <label>Địa chỉ chi tiết <span class="req">*</span></label>
                        <input type="text" class="form-control" id="addressDetail" name="addressDetail" placeholder="Số nhà, tên đường...">
                        <span class="error-text" id="err-addressDetail">Vui lòng nhập địa chỉ chi tiết.</span>
                    </div>
                </div>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/customer" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Tạo khách hàng</button>
                </div>
            </form>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Chấp nhận cả số di động lẫn số bàn Việt Nam (VD: 024 3822 1234), không chỉ riêng đầu số di động
        function isValidPhone(value) {
            return /^(0|\+84)[0-9]{9,10}$/.test(value.replace(/[\s.-]/g, ''));
        }
        function isValidEmail(value) {
            return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
        }

        // Nạp xã/phường theo tỉnh/thành phố đã chọn qua AJAX (thay vì đổ sẵn ~3.321
        // xã/phường vào trang) -- xem AddressController.
        var contextPath = '${pageContext.request.contextPath}';
        var districtSelect = document.getElementById('district');

        function loadWards(provinceId) {
            districtSelect.innerHTML = '';
            if (!provinceId) {
                districtSelect.innerHTML = '<option value="">-- Chọn tỉnh / thành phố trước --</option>';
                return;
            }
            districtSelect.innerHTML = '<option value="">Đang tải...</option>';
            fetch(contextPath + '/address/wards?provinceId=' + encodeURIComponent(provinceId))
                .then(function (res) { return res.json(); })
                .then(function (wards) {
                    var html = '<option value="">-- Chọn xã / phường --</option>';
                    wards.forEach(function (w) {
                        html += '<option value="' + w.id + '">' + escapeHtml(w.name) + '</option>';
                    });
                    districtSelect.innerHTML = html;
                })
                .catch(function () {
                    districtSelect.innerHTML = '<option value="">Không tải được danh sách xã/phường</option>';
                });
        }

        function escapeHtml(value) {
            var div = document.createElement('div');
            div.textContent = value;
            return div.innerHTML;
        }

        function previewLogo(input) {
            if (!input.files || !input.files[0]) {
                return;
            }
            var reader = new FileReader();
            reader.onload = function (e) {
                document.getElementById('logoPreview').innerHTML = '<img src="' + e.target.result + '" alt="Logo">';
            };
            reader.readAsDataURL(input.files[0]);
        }

        // ===== Người phụ trách chính suy từ địa bàn =====
        //
        // Bảng phân công tỉnh -> người, nhúng sẵn từ server (34 tỉnh, nhỏ).
        // Ai cầm tỉnh nào là dữ kiện tổ chức, không phải lựa chọn của người
        // nhập liệu -- nên tỉnh đã có người thì điền sẵn rồi KHOÁ ô lại.
        //
        // Tỉnh CHƯA ai cầm thì mở cho tự chọn như cũ: bảng phân công mới phủ
        // một phần trong 34 tỉnh, khoá tất thì không tạo nổi khách hàng ở
        // những tỉnh còn trống.
        //
        // Đây chỉ là tầng hiển thị. Khoá thật nằm ở CustomerController
        // .resolveAccountOwnerId -- ô disabled ai mở devtools cũng gỡ được.
        var phanCongDiaBan = {
            <c:forEach var="e" items="${territoryAssignments}" varStatus="st">'${e.key}': ${e.value}<c:if test="${!st.last}">,</c:if></c:forEach>
        };
        var oNguoiPhuTrach = document.getElementById('assignee');
        var oNguoiPhuTrachAn = document.getElementById('accountOwnerHidden');
        var goiYDiaBan = document.getElementById('goiYDiaBan');
        // Tên đang nằm trong ô là do địa bàn điền, hay do người dùng tự chọn?
        // Phải phân biệt, nếu không thì đổi từ tỉnh CÓ người sang tỉnh TRỐNG
        // sẽ để tên của tỉnh cũ nằm lại trong ô đã mở khoá -- người dùng bấm
        // lưu là gán khách cho một người chẳng liên quan gì tới tỉnh mới.
        var dienBoiDiaBan = false;

        function apDungPhanCongDiaBan(provinceId) {
            var userId = provinceId ? phanCongDiaBan[provinceId] : null;
            var opt = userId ? Array.prototype.find.call(oNguoiPhuTrach.options, function (o) {
                return o.value === String(userId);
            }) : null;

            if (!opt) {
                // Tỉnh trống, hoặc người cầm tỉnh không còn trong danh sách
                // nhân viên đang hoạt động: trả ô về cho người dùng tự chọn.
                oNguoiPhuTrach.disabled = false;
                oNguoiPhuTrachAn.disabled = true;
                goiYDiaBan.style.display = 'none';
                if (dienBoiDiaBan) {
                    oNguoiPhuTrach.value = '';
                    dienBoiDiaBan = false;
                }
                return;
            }
            oNguoiPhuTrach.value = opt.value;
            oNguoiPhuTrach.disabled = true;
            oNguoiPhuTrachAn.disabled = false;
            oNguoiPhuTrachAn.value = opt.value;
            dienBoiDiaBan = true;
            goiYDiaBan.textContent = 'Theo phân công địa bàn. Muốn đổi thì sửa người phụ trách tỉnh ở trang Nhân viên.';
            goiYDiaBan.style.display = 'block';
        }

        // Người dùng tự chọn ai đó ở tỉnh trống thì lựa chọn đó là của họ,
        // đừng xoá khi họ đổi sang một tỉnh trống khác.
        oNguoiPhuTrach.addEventListener('change', function () { dienBoiDiaBan = false; });

        document.getElementById('province').addEventListener('change', function () {
            loadWards(this.value);
            apDungPhanCongDiaBan(this.value);
        });

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            // province/district nằm trong danh sách này vì tỉnh là căn cứ chia
            // địa bàn và lọc báo cáo -- khách không có tỉnh sẽ không xuất hiện
            // ở bất kỳ thống kê theo tỉnh nào (server cũng chặn lại lần nữa).
            var requiredSelects = ['customerType', 'customerGroup', 'assignee', 'province', 'district'];
            requiredSelects.forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            // Ít nhất một vai. Server chặn lại lần nữa -- không có vai nào thì
            // khách lưu được nhưng biến khỏi cả hai danh sách.
            if (document.querySelectorAll('input[name="roles"]:checked').length === 0) {
                document.getElementById('err-roles').style.display = 'block'; valid = false;
            }

            var name = document.getElementById('customerName');
            if (!name.value.trim()) { document.getElementById('err-customerName').style.display = 'block'; valid = false; }

            var taxCode = document.getElementById('taxCode');
            if (!taxCode.value.trim()) { document.getElementById('err-taxCode').style.display = 'block'; valid = false; }

            var addressDetail = document.getElementById('addressDetail');
            if (!addressDetail.value.trim()) { document.getElementById('err-addressDetail').style.display = 'block'; valid = false; }

            // Hai vai phải là hai người: trùng nhau thì cột "Người hỗ trợ" chỉ
            // lặp lại tên ở cột bên cạnh (server cũng chặn lại lần nữa).
            var support = document.getElementById('supportAssignee');
            var mainOwner = document.getElementById('assignee');
            if (support.value && support.value === mainOwner.value) {
                document.getElementById('err-supportAssignee').style.display = 'block'; valid = false;
            }

            var phone = document.getElementById('phone');
            if (!isValidPhone(phone.value)) { document.getElementById('err-phone').style.display = 'block'; valid = false; }

            var email = document.getElementById('email');
            if (!isValidEmail(email.value)) { document.getElementById('err-email').style.display = 'block'; valid = false; }

            var joinDate = document.getElementById('joinDate');
            if (joinDate.value) {
                var today = new Date().toISOString().split('T')[0];
                if (joinDate.value > today) { document.getElementById('err-joinDate').style.display = 'block'; valid = false; }
            }

            return valid;
        }
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
