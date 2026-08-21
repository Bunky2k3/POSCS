<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần đặt các request attribute sau trước khi forward tới trang này:
      - roleList     : List<poscs.model.Role>
      - provinceList : List<poscs.model.Province>
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

    <style>
        :root {
            --primary-dark: #003c6e; --primary: #0568a6; --primary-light: #0f9edb;
            --primary-lighter: #6fd0ff; --accent: #00c2ff; --danger: #e2536b;
            --success: #2fbf8f; --warning: #f5a623;
        }
        body { font-family: 'Inter', sans-serif; background: #f3f4f6; min-height: 100vh; }

        .topbar { background: #fff; box-shadow: 0 2px 12px rgba(0,60,110,0.08); padding: 14px 28px; display: flex; justify-content: space-between; align-items: center; position: sticky; top: 0; z-index: 50; }
        .topbar .brand { font-weight: 700; color: var(--primary-dark); font-size: 1.05rem; display: flex; align-items: center; gap: 10px; text-decoration: none; }
        .topbar .brand:hover { color: var(--primary-dark); }
        .topbar .brand i { color: var(--primary); font-size: 1.2rem; }
        .topbar-right { display: flex; align-items: center; gap: 18px; }
        .avatar-mini { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; cursor: pointer; border: 2px solid var(--primary-light); }
        .dropdown-menu { border: none; border-radius: 14px; box-shadow: 0 14px 34px rgba(0,40,80,0.18); padding: 8px; margin-top: 12px !important; min-width: 230px; }
        .dropdown-item { border-radius: 8px; padding: 9px 12px; font-size: 0.87rem; color: #374151; }
        .dropdown-item:hover, .dropdown-item:focus { background: #f0f9ff; color: var(--primary-dark); }
        .dropdown-item.text-danger:hover { background: #fdecef; color: var(--danger) !important; }
        .dd-user-header { display: flex; align-items: center; gap: 10px; padding: 8px 10px 12px; }
        .dd-user-header img { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
        .dd-user-header .dd-name { font-weight: 600; font-size: 0.9rem; color: #111827; }
        .dd-user-header .dd-role { font-size: 0.75rem; color: #6b7280; }

        .app-shell { display: flex; align-items: flex-start; }
        .sidebar { width: 240px; flex-shrink: 0; background: #fff; border-right: 1px solid #eef2f6; padding: 14px 10px; position: sticky; top: 66px; height: calc(100vh - 66px); overflow-y: auto; display: flex; flex-direction: column; transition: width 0.15s ease; }
        .sidebar-link { display: flex; align-items: center; gap: 12px; padding: 9px 12px; margin-bottom: 1px; border-radius: 10px; color: #6b7280; font-weight: 600; font-size: 0.88rem; text-decoration: none; }
        .sidebar-link i { width: 18px; text-align: center; color: #9ca3af; flex-shrink: 0; }
        .sidebar-link:hover { background: #f0f9ff; color: var(--primary-dark); }
        .sidebar-link.active { background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; }
        .sidebar-link.active i { color: #fff; }
        .sidebar-toggle { display: flex; align-items: center; justify-content: center; width: 32px; height: 32px; margin: 0 0 8px; padding: 0; border: none; border-radius: 8px; background: none; color: #9ca3af; cursor: pointer; }
        .sidebar-toggle { align-self: flex-end; }
        .sidebar.collapsed .sidebar-toggle { align-self: center; }
        .sidebar-toggle i { transition: transform 0.15s ease; }
        .sidebar-toggle:hover { background: #f0f9ff; color: var(--primary-dark); }
        .sidebar.collapsed { width: 68px; }
        .sidebar.collapsed .sidebar-link { justify-content: center; }
        .sidebar.collapsed .sidebar-link span { display: none; }
        .sidebar.collapsed .sidebar-toggle i { transform: rotate(180deg); }
        .sidebar.collapsed:hover { width: 240px; }
        .sidebar.collapsed:hover .sidebar-link { justify-content: flex-start; }
        .sidebar.collapsed:hover .sidebar-link span { display: inline; }
        .sidebar.collapsed:hover .sidebar-toggle i { transform: rotate(0deg); }
        .main-content { flex: 1; min-width: 0; }
        @media (max-width: 900px) { .sidebar { display: none; } }

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

    <nav class="topbar">
        <div class="topbar-left"><a href="${pageContext.request.contextPath}/dashboard" class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</a></div>
        <div class="topbar-right">
            <div class="dropdown">
                <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header">
                        <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" alt="avatar">
                        <div><div class="dd-name"><c:out value="${sessionScope.currentUser.fullName}"/></div><div class="dd-role"><c:out value="${sessionScope.currentUser.role.roleName}"/></div></div>
                    </li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/viewProfile"><i class="fa-regular fa-id-card me-2"></i>Thông tin cá nhân</a></li>
                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/changePassword.jsp"><i class="fa-solid fa-key me-2"></i>Đổi mật khẩu</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-danger" href="${pageContext.request.contextPath}/login?action=logout"><i class="fa-solid fa-arrow-right-from-bracket me-2"></i>Đăng xuất</a></li>
                </ul>
            </div>
        </div>
    </nav>
    <div class="app-shell">
        <aside class="sidebar" id="sidebar">
            <button type="button" class="sidebar-toggle" id="sidebarToggle" aria-label="Thu gọn menu"><i class="fa-solid fa-angles-left"></i></button>
            <a href="${pageContext.request.contextPath}/dashboard" class="sidebar-link"><i class="fa-solid fa-house"></i><span>Trang chủ</span></a>
            <a href="${pageContext.request.contextPath}/customer" class="sidebar-link"><i class="fa-solid fa-users"></i><span>Khách hàng</span></a>
            <a href="${pageContext.request.contextPath}/contract" class="sidebar-link"><i class="fa-solid fa-file-contract"></i><span>Hợp đồng</span></a>
            <a href="${pageContext.request.contextPath}/product" class="sidebar-link"><i class="fa-solid fa-box"></i><span>Sản phẩm</span></a>
            <a href="${pageContext.request.contextPath}/ticket" class="sidebar-link"><i class="fa-solid fa-headset"></i><span>Phiếu hỗ trợ</span></a>
            <a href="${pageContext.request.contextPath}/employee" class="sidebar-link active"><i class="fa-solid fa-user-tie"></i><span>Nhân viên</span></a>
        </aside>
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
                            <c:when test="${param.error == 'duplicate_email'}">Email này đã được sử dụng bởi tài khoản khác.</c:when>
                            <c:when test="${param.error == 'duplicate_phone'}">Số điện thoại này đã được sử dụng bởi tài khoản khác.</c:when>
                            <c:when test="${param.error == 'duplicate_citizen'}">Số CCCD/CMND này đã được sử dụng bởi tài khoản khác.</c:when>
                            <c:when test="${param.error == 'create_failed'}">Không thể tạo nhân viên. Vui lòng thử lại.</c:when>
                            <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                        </c:choose>
                    </div>
                </c:if>

                <div class="info-banner">
                    <i class="fa-solid fa-circle-info"></i>
                    <div>Tên đăng nhập sẽ được hệ thống tự sinh từ họ tên, và mật khẩu tạm thời sẽ được gửi tới email công ty của nhân viên ngay sau khi tạo tài khoản thành công.</div>
                </div>

                <form id="addEmployeeForm" action="${pageContext.request.contextPath}/employee" method="POST" onsubmit="return validateForm();">
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <input type="hidden" name="action" value="create">

                    <!-- ===== Thông tin công việc ===== -->
                    <div class="section-header"><h5>Thông tin công việc</h5></div>
                    <div class="row">
                        <div class="col-md-6 field-row">
                            <label for="email">Email công ty (dùng để gửi tài khoản)</label>
                            <input type="email" class="form-control" id="email" name="email">
                            <span class="error-text" id="err-email">Địa chỉ email không hợp lệ.</span>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="department">Phòng ban</label>
                            <input type="text" class="form-control" id="department" name="department" placeholder="VD: Kinh doanh, Kỹ thuật...">
                            <span class="error-text" id="err-department">Trường này không được để trống.</span>
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
                            <label for="personalEmail">Email cá nhân <span class="text-muted" style="text-transform:none; font-weight:400;">(không bắt buộc)</span></label>
                            <input type="email" class="form-control" id="personalEmail" name="personalEmail">
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
    <script>
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

            var requiredIds = ['lastName', 'firstName', 'citizenId', 'department', 'roleId', 'hireDate', 'dateOfBirth'];
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

            var email = document.getElementById('email');
            if (!isValidEmail(email.value)) {
                document.getElementById('err-email').style.display = 'block';
                valid = false;
            }

            return valid;
        }

        (function () {
            var sidebar = document.getElementById('sidebar');
            var toggle = document.getElementById('sidebarToggle');
            var STORAGE_KEY = 'poscsSidebarCollapsed';
            if (localStorage.getItem(STORAGE_KEY) === '1') { sidebar.classList.add('collapsed'); }
            toggle.addEventListener('click', function () {
                var collapsed = sidebar.classList.toggle('collapsed');
                localStorage.setItem(STORAGE_KEY, collapsed ? '1' : '0');
            });
        })();
    </script>
</body>
</html>
