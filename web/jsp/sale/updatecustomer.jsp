<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần: lấy customer_id từ query param, truy vấn enterprises (JOIN address/district
    để biết provinceId hiện tại) đổ vào request attribute "customer", xử lý POST cập nhật vào
    bảng enterprises. Kiểm tra bản ghi tồn tại (MSG-021) trước khi hiển thị.

    Request attribute cần có:
      - customer      : poscs.model.Enterprise (đã join .address.district)
      - userList      : List<poscs.model.User>
      - provinceList  : List<poscs.model.Province>

    Dropdown "Xã / Phường" KHÔNG đổ sẵn từ server -- JS nạp qua AJAX
    (GET /address/wards?provinceId=..., xem AddressController), tự chọn sẵn
    xã/phường hiện tại của khách hàng sau khi nạp xong.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Cập nhật khách hàng - POSCS Portal</title>

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
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/customer?action=view&id=${customer.enterpriseId}" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại chi tiết khách hàng</a>

        <div class="page-header-row">
            <div>
                <h2>Cập nhật thông tin khách hàng</h2>
                <p>Mã khách hàng: <strong style="color:var(--primary-dark)">${fn:escapeXml(customer.enterpriseCode)}</strong> &middot; Mã số thuế: <strong style="color:var(--primary-dark)">${fn:escapeXml(customer.taxCode)}</strong></p>
            </div>
        </div>

        <div class="card-box">
            <form id="createCustomerForm" action="${pageContext.request.contextPath}/customer" method="POST" enctype="multipart/form-data" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="update">
                <input type="hidden" name="customerId" value="${customer.enterpriseId}">

                <div class="section-header"><h5>Thông tin khách hàng</h5></div>

                <div class="logo-upload-wrap">
                    <div class="logo-avatar-wrap">
                        <div class="logo-avatar-preview" id="logoPreview">
                            <c:choose>
                                <c:when test="${not empty customer.logoUrl}"><img src="${pageContext.request.contextPath}${fn:escapeXml(customer.logoUrl)}" alt="Logo"></c:when>
                                <c:otherwise><i class="fa-solid fa-building"></i></c:otherwise>
                            </c:choose>
                        </div>
                        <label class="logo-edit-btn" for="logoInput"><i class="fa-solid fa-camera"></i></label>
                        <input type="file" name="logo" id="logoInput" accept="image/*" hidden onchange="previewLogo(this)">
                    </div>
                    <div class="logo-upload-hint">Logo doanh nghiệp (không bắt buộc)</div>
                </div>

                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Tên khách hàng <span class="req">*</span></label>
                        <input type="text" class="form-control" id="customerName" name="customerName" value="${fn:escapeXml(customer.enterpriseName)}">
                        <span class="error-text" id="err-customerName">Tên khách hàng không được để trống.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Loại khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customerType" name="customerType">
                            <option value="">-- Chọn loại khách hàng --</option>
                            <option value="Nhà mạng viễn thông" ${customer.customerType == 'Nhà mạng viễn thông' ? 'selected' : ''}>Nhà mạng viễn thông</option>
                            <option value="Nhà thầu thi công" ${customer.customerType == 'Nhà thầu thi công' ? 'selected' : ''}>Nhà thầu thi công</option>
                            <option value="Đại lý phân phối" ${customer.customerType == 'Đại lý phân phối' ? 'selected' : ''}>Đại lý phân phối</option>
                        </select>
                        <span class="error-text" id="err-customerType">Vui lòng chọn loại khách hàng.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Nhóm khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customerGroup" name="customerGroup">
                            <option value="">-- Chọn nhóm khách hàng --</option>
                            <option value="VIP" ${customer.customerGroup == 'VIP' ? 'selected' : ''}>Khách hàng VIP</option>
                            <option value="Thân thiết" ${customer.customerGroup == 'Thân thiết' ? 'selected' : ''}>Khách hàng thân thiết</option>
                            <option value="Tiềm năng" ${customer.customerGroup == 'Tiềm năng' ? 'selected' : ''}>Khách hàng tiềm năng</option>
                            <option value="Thường" ${customer.customerGroup == 'Thường' ? 'selected' : ''}>Khách hàng thường</option>
                        </select>
                        <span class="error-text" id="err-customerGroup">Vui lòng chọn nhóm khách hàng.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Nhân viên phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="assignee" name="accountOwnerId">
                            <option value="">-- Chọn nhân viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}" ${staff.userId == customer.accountOwnerId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-assignee">Vui lòng chọn nhân viên phụ trách.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Số điện thoại <span class="req">*</span></label>
                        <input type="tel" class="form-control" id="phone" name="phone" value="${fn:escapeXml(customer.phone)}">
                        <span class="error-text" id="err-phone">Số điện thoại không hợp lệ.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Email <span class="req">*</span></label>
                        <input type="email" class="form-control" id="email" name="email" value="${fn:escapeXml(customer.email)}">
                        <span class="error-text" id="err-email">Vui lòng nhập địa chỉ email hợp lệ.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Website</label>
                        <input type="text" class="form-control" id="website" name="website" value="${fn:escapeXml(customer.website)}">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Ngày tham gia</label>
                        <input type="date" class="form-control" id="joinDate" name="joinDate" value="${customer.joinDate}">
                        <span class="error-text" id="err-joinDate">Ngày tham gia không được là ngày trong tương lai.</span>
                    </div>
                </div>

                <div class="section-header"><h5>Địa chỉ</h5></div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Tỉnh / Thành phố</label>
                        <select class="form-select" id="province" name="provinceId">
                            <option value="">-- Chọn tỉnh / thành phố --</option>
                            <c:forEach var="prov" items="${provinceList}">
                                <option value="${prov.provinceId}" ${customer.address != null && customer.address.district != null && prov.provinceId == customer.address.district.provinceId ? 'selected' : ''}>${fn:escapeXml(prov.shortName)}</option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Xã / Phường</label>
                        <select class="form-select" id="district" name="districtId">
                            <c:choose>
                                <c:when test="${customer.address != null && customer.address.district != null}">
                                    <option value="${customer.address.districtId}" selected>${fn:escapeXml(customer.address.district.shortName)}</option>
                                </c:when>
                                <c:otherwise>
                                    <option value="">-- Chọn xã / phường --</option>
                                </c:otherwise>
                            </c:choose>
                        </select>
                    </div>
                    <div class="col-12 field-row">
                        <label>Địa chỉ chi tiết</label>
                        <input type="text" class="form-control" id="addressDetail" name="addressDetail"
                               value="${fn:escapeXml(customer.address != null ? customer.address.streetAndLocalName : '')}">
                    </div>
                </div>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/customer?action=view&id=${customer.enterpriseId}" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Lưu thay đổi</button>
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
        // xã/phường vào trang) -- xem AddressController. Giữ nguyên lựa chọn hiện
        // tại nếu còn khớp sau khi nạp.
        var contextPath = '${pageContext.request.contextPath}';
        var districtSelect = document.getElementById('district');
        var initialDistrictValue = districtSelect.value;

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

        function loadWards(provinceId, keepValue) {
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
                        var selected = keepValue && String(w.id) === String(keepValue) ? ' selected' : '';
                        html += '<option value="' + w.id + '"' + selected + '>' + escapeHtml(w.name) + '</option>';
                    });
                    districtSelect.innerHTML = html;
                })
                .catch(function () {
                    districtSelect.innerHTML = '<option value="">Không tải được danh sách xã/phường</option>';
                });
        }

        document.getElementById('province').addEventListener('change', function () {
            loadWards(this.value, null);
        });

        // Khởi tạo đúng danh sách xã/phường theo tỉnh đã chọn sẵn khi load trang
        loadWards(document.getElementById('province').value, initialDistrictValue);

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            var requiredSelects = ['customerType', 'customerGroup', 'assignee'];
            requiredSelects.forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            var name = document.getElementById('customerName');
            if (!name.value.trim()) { document.getElementById('err-customerName').style.display = 'block'; valid = false; }

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
