<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần đặt các request attribute sau trước khi forward tới trang này:
      - roleList       : List<poscs.model.Role>
      - departmentList : List<poscs.model.Department>
      - provinceList   : List<poscs.model.Province>
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thêm nhân viên - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/flatpickr/dist/flatpickr.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 900px; margin: 32px auto; padding: 0 20px 32px; }
        .form-card { background: #fff; border-radius: 20px; overflow: hidden; box-shadow: 0 15px 40px rgba(0,40,80,0.12); }
        .form-banner { background: linear-gradient(120deg, var(--primary-dark), var(--primary), var(--primary-light)); padding: 30px 34px 24px; color: #fff; }
        .form-banner h3 { font-weight: 700; margin: 0 0 4px; }
        .form-banner p { margin: 0; opacity: 0.85; font-size: 0.88rem; }
        .form-body { padding: 24px 28px 28px; }

        .info-banner { display: flex; gap: 12px; align-items: flex-start; background: #eaf6ff; border: 1px solid #d7ecfb; border-radius: 12px; padding: 14px 16px; margin-bottom: 26px; font-size: 0.85rem; color: var(--primary-dark); }
        .info-banner i { font-size: 1.1rem; margin-top: 1px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 24px 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-of-type { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .form-control, .form-select { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .form-control:focus, .form-select:focus { background-color: #ffffff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15,158,219,0.15); }
        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }

        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5,104,166,0.3); }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) { .form-body { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="employee" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <div class="form-card">
            <div class="form-banner">
                <h3><i class="fa-solid fa-user-plus me-2"></i>Thêm nhân viên mới</h3>
                <p>Tạo hồ sơ và tài khoản đăng nhập cho nhân viên</p>
            </div>
            <div class="form-body">

                <c:if test="${not empty param.error}">
                    <div class="alert alert-danger py-2 px-3 mb-4" style="font-size: 0.9rem; border-radius: 12px;">
                        <c:choose>
                            <c:when test="${param.error == 'invalid'}">Vui lòng nhập đầy đủ và đúng định dạng các trường bắt buộc.</c:when>
                            <c:when test="${param.error == 'duplicate_phone'}">Số điện thoại này đã được sử dụng bởi tài khoản khác.</c:when>
                            <c:when test="${param.error == 'duplicate_citizen'}">Số CCCD/CMND này đã được sử dụng bởi tài khoản khác.</c:when>
                            <c:when test="${param.error == 'create_failed'}">Không thể tạo nhân viên. Vui lòng thử lại.</c:when>
                            <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                        </c:choose>
                    </div>
                </c:if>

                <div class="info-banner">
                    <i class="fa-solid fa-circle-info"></i>
                    <div>Hệ thống sẽ tự cấp tên đăng nhập và email công ty (dạng &lt;tên đăng nhập&gt;@postef.com.vn) cho nhân viên. Sau khi tạo xong, vào trang chi tiết nhân viên và bấm <strong>"Gửi thông tin tài khoản"</strong> để gửi mật khẩu tạm thời tới email cá nhân của nhân viên.</div>
                </div>

                <form id="addEmployeeForm" action="${pageContext.request.contextPath}/employee" method="POST" onsubmit="return validateForm();">
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <input type="hidden" name="action" value="create">

                    <!-- ===== Thông tin công việc ===== -->
                    <div class="section-header"><h5>Thông tin công việc</h5></div>
                    <div class="row">
                        <div class="col-md-6 field-row">
                            <label>Email công ty</label>
                            <div class="form-control" style="background:#eef2f6; color:#6b7280;">Hệ thống tự cấp theo dạng &lt;tên đăng nhập&gt;@postef.com.vn</div>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="department">Phòng ban</label>
                            <select class="form-select" id="department" name="departmentId">
                                <option value="">-- Chọn phòng ban --</option>
                                <c:forEach var="d" items="${departmentList}">
                                    <option value="${d.departmentId}">${fn:escapeXml(d.departmentName)}</option>
                                </c:forEach>
                            </select>
                            <span class="error-text" id="err-department">Vui lòng chọn phòng ban.</span>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="roleId">Vai trò</label>
                            <select class="form-select" id="roleId" name="roleId">
                                <option value="">-- Chọn vai trò --</option>
                                <c:forEach var="r" items="${roleList}">
                                    <option value="${r.roleId}">${fn:escapeXml(r.roleName)}</option>
                                </c:forEach>
                            </select>
                            <span class="error-text" id="err-roleId">Vui lòng chọn vai trò.</span>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="hireDate">Ngày vào làm</label>
                            <input type="date" class="form-control" id="hireDate" name="hireDate">
                            <span class="error-text" id="err-hireDate">Vui lòng chọn ngày vào làm.</span>
                        </div>
                    </div>

                    <!-- ===== Thông tin cá nhân ===== -->
                    <div class="section-header"><h5>Thông tin cá nhân</h5></div>
                    <div class="row">
                        <div class="col-md-4 field-row">
                            <label for="lastName">Họ</label>
                            <input type="text" class="form-control" id="lastName" name="lastName">
                            <span class="error-text" id="err-lastName">Trường này không được để trống.</span>
                        </div>
                        <div class="col-md-4 field-row">
                            <label for="middleName">Tên đệm</label>
                            <input type="text" class="form-control" id="middleName" name="middleName">
                        </div>
                        <div class="col-md-4 field-row">
                            <label for="firstName">Tên</label>
                            <input type="text" class="form-control" id="firstName" name="firstName">
                            <span class="error-text" id="err-firstName">Trường này không được để trống.</span>
                        </div>

                        <div class="col-md-4 field-row">
                            <label for="gender">Giới tính</label>
                            <select class="form-select" id="gender" name="gender">
                                <option value="Nam">Nam</option>
                                <option value="Nữ">Nữ</option>
                                <option value="Khác">Khác</option>
                            </select>
                        </div>
                        <div class="col-md-4 field-row">
                            <label for="dateOfBirth">Ngày sinh</label>
                            <input type="date" class="form-control" id="dateOfBirth" name="dateOfBirth">
                            <span class="error-text" id="err-dateOfBirth">Ngày sinh phải là một ngày hợp lệ trong quá khứ.</span>
                        </div>
                        <div class="col-md-4 field-row">
                            <label for="citizenId">Số CCCD/CMND</label>
                            <input type="text" class="form-control" id="citizenId" name="citizenId">
                            <span class="error-text" id="err-citizenId">Trường này không được để trống.</span>
                        </div>

                        <div class="col-md-6 field-row">
                            <label for="phone">Số điện thoại</label>
                            <input type="tel" class="form-control" id="phone" name="phone">
                            <span class="error-text" id="err-phone">Số điện thoại không hợp lệ.</span>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="personalEmail">Email cá nhân <span class="text-muted" style="text-transform:none; font-weight:400;">(dùng để gửi tài khoản)</span></label>
                            <input type="email" class="form-control" id="personalEmail" name="personalEmail">
                            <span class="error-text" id="err-personalEmail">Vui lòng nhập email cá nhân hợp lệ để nhận thông tin tài khoản.</span>
                        </div>
                    </div>

                    <!-- ===== Địa chỉ ===== -->
                    <div class="section-header"><h5>Địa chỉ <span class="text-muted" style="text-transform:none; font-weight:400; font-size:0.78rem;">(không bắt buộc)</span></h5></div>
                    <div class="row">
                        <div class="col-md-6 field-row">
                            <label for="province">Tỉnh / Thành phố</label>
                            <select class="form-select" id="province" name="provinceId">
                                <option value="">-- Chọn tỉnh / thành phố --</option>
                                <c:forEach var="prov" items="${provinceList}">
                                    <option value="${prov.provinceId}">${prov.shortName}</option>
                                </c:forEach>
                            </select>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="district">Xã / Phường</label>
                            <select class="form-select" id="district" name="districtId">
                                <option value="">-- Chọn tỉnh / thành phố trước --</option>
                            </select>
                        </div>
                        <div class="col-12 field-row">
                            <label for="addressDetail">Địa chỉ chi tiết</label>
                            <input type="text" class="form-control" id="addressDetail" name="addressDetail">
                        </div>
                    </div>

                    <!-- ===== Action buttons ===== -->
                    <div class="action-bar">
                        <a href="${pageContext.request.contextPath}/employee" class="btn-cancel">Hủy</a>
                        <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Tạo nhân viên</button>
                    </div>
                </form>
            </div>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/flatpickr"></script>
    <script>
        // Hiển thị dd/mm/yyyy (altFormat) nhưng vẫn submit yyyy-MM-dd (dateFormat) --
        // backend (parseDateOrNull) cần đúng định dạng ISO để Date.valueOf() parse được.
        // allowInput: true để vẫn gõ tay được như input thường; attachDateMask chỉ
        // cho gõ chữ số (không gõ được chữ cái) và tự chèn "/", tối đa 8 chữ số
        // (ddmmyyyy) nên không gõ được năm quá 4 chữ số.
        function attachDateMask(input) {
            if (!input) { return; }
            input.addEventListener('input', function () {
                var digits = input.value.replace(/\D/g, '').slice(0, 8);
                var parts = [];
                if (digits.length > 0) { parts.push(digits.slice(0, 2)); }
                if (digits.length > 2) { parts.push(digits.slice(2, 4)); }
                if (digits.length > 4) { parts.push(digits.slice(4, 8)); }
                input.value = parts.join('/');
            });
        }
        var dateFieldOptions = { dateFormat: 'Y-m-d', altInput: true, altFormat: 'd/m/Y', allowInput: true, minDate: '1900-01-01' };
        var dobPicker = flatpickr(document.getElementById('dateOfBirth'), Object.assign({}, dateFieldOptions, { maxDate: 'today' }));
        var hireDatePicker = flatpickr(document.getElementById('hireDate'), dateFieldOptions);
        [dobPicker, hireDatePicker].forEach(function (picker) {
            attachDateMask(picker.altInput);
            if (picker.altInput) { picker.altInput.placeholder = 'dd/mm/yyyy'; }
        });

        function isValidPhone(value) { return /^(0|\+84)[0-9]{9,10}$/.test(value.replace(/[\s.-]/g, '')); }
        function isValidEmail(value) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value); }

        var contextPath = '${pageContext.request.contextPath}';
        var districtSelect = document.getElementById('district');

        function escapeHtml(value) {
            var div = document.createElement('div');
            div.textContent = value;
            return div.innerHTML;
        }

        function loadWards(provinceId) {
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
        document.getElementById('province').addEventListener('change', function () { loadWards(this.value); });

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            var requiredIds = ['lastName', 'firstName', 'citizenId', 'department', 'roleId', 'hireDate', 'dateOfBirth', 'personalEmail'];
            requiredIds.forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value.trim()) {
                    var err = document.getElementById('err-' + id);
                    if (err) { err.style.display = 'block'; }
                    valid = false;
                }
            });

            var dob = document.getElementById('dateOfBirth');
            if (dob.value && new Date(dob.value) >= new Date()) {
                document.getElementById('err-dateOfBirth').style.display = 'block';
                valid = false;
            }

            var phone = document.getElementById('phone');
            if (!isValidPhone(phone.value)) {
                document.getElementById('err-phone').style.display = 'block';
                valid = false;
            }

            var personalEmail = document.getElementById('personalEmail');
            if (!isValidEmail(personalEmail.value)) {
                document.getElementById('err-personalEmail').style.display = 'block';
                valid = false;
            }

            return valid;
        }
    </script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
