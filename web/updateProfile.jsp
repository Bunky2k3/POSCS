<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Sửa thông tin cá nhân - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <style>
        :root {
            --primary-dark: #003c6e;
            --primary: #0568a6;
            --primary-light: #0f9edb;
            --primary-lighter: #6fd0ff;
            --accent: #00c2ff;
            --danger: #e2536b;
            --success: #2fbf8f;
            --warning: #f5a623;
        }

        body {
            font-family: 'Inter', sans-serif;
            background: #f3f4f6;
            min-height: 100vh;
        }

        /* ===== Topbar ===== */
        .topbar {
            background: #ffffff;
            box-shadow: 0 2px 12px rgba(0, 60, 110, 0.08);
            padding: 14px 28px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            position: sticky;
            top: 0;
            z-index: 50;
        }
        .topbar .brand {
            font-weight: 700;
            color: var(--primary-dark);
            font-size: 1.05rem;
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .topbar .brand i { color: var(--primary); font-size: 1.2rem; }

        .topbar-right { display: flex; align-items: center; gap: 18px; }
        .bell-icon { color: #6b7280; font-size: 1.1rem; cursor: pointer; position: relative; }
        .bell-icon .dot {
            position: absolute; top: -3px; right: -4px;
            width: 8px; height: 8px; border-radius: 50%;
            background: var(--danger); border: 1.5px solid #fff;
        }
        .avatar-mini {
            width: 38px; height: 38px; border-radius: 50%;
            object-fit: cover; cursor: pointer;
            border: 2px solid var(--primary-light);
        }

        /* ===== Dropdown chung (avatar + thông báo) ===== */
        .dropdown-menu {
            border: none; border-radius: 14px;
            box-shadow: 0 14px 34px rgba(0, 40, 80, 0.18);
            padding: 8px; margin-top: 12px !important;
            min-width: 230px;
        }
        .dropdown-item {
            border-radius: 8px; padding: 9px 12px;
            font-size: 0.87rem; color: #374151;
        }
        .dropdown-item:hover, .dropdown-item:focus {
            background: #f0f9ff; color: var(--primary-dark);
        }
        .dropdown-item.text-danger:hover {
            background: #fdecef; color: var(--danger) !important;
        }

        .dd-user-header {
            display: flex; align-items: center; gap: 10px;
            padding: 8px 10px 12px;
        }
        .dd-user-header img {
            width: 42px; height: 42px; border-radius: 50%; object-fit: cover;
        }
        .dd-user-header .dd-name { font-weight: 600; font-size: 0.9rem; color: #111827; }
        .dd-user-header .dd-role { font-size: 0.75rem; color: #6b7280; }

        /* ===== Dropdown thông báo ===== */
        .notif-dropdown { min-width: 320px; max-height: 380px; overflow-y: auto; }
        .notif-header {
            display: flex; justify-content: space-between; align-items: center;
            padding: 6px 10px 10px; font-weight: 700; font-size: 0.9rem; color: var(--primary-dark);
        }
        .notif-count {
            background: var(--primary); color: #fff; font-size: 0.68rem;
            padding: 2px 9px; border-radius: 10px; font-weight: 600;
        }
        .notif-item { display: flex; gap: 10px; align-items: flex-start; white-space: normal; }
        .notif-icon {
            width: 34px; height: 34px; border-radius: 50%;
            background: #eef6fb; color: var(--primary);
            display: flex; align-items: center; justify-content: center;
            flex-shrink: 0; font-size: 0.85rem;
        }
        .notif-text { font-size: 0.85rem; color: #111827; font-weight: 500; line-height: 1.3; }
        .notif-time { font-size: 0.72rem; color: #9ca3af; margin-top: 2px; }

        /* ===== Layout ===== */
        .page-container {
            max-width: 900px;
            margin: 32px auto;
            padding: 0 20px 60px;
        }

        .profile-card {
            background: #ffffff;
            border-radius: 20px;
            overflow: hidden;
            box-shadow: 0 15px 40px rgba(0, 40, 80, 0.12);
        }

        .profile-banner {
            background: linear-gradient(120deg, var(--primary-dark), var(--primary), var(--primary-light));
            padding: 40px 30px 28px;
            text-align: center;
            color: #fff;
        }

        .avatar-wrap {
            position: relative;
            width: 108px;
            height: 108px;
            margin: 0 auto 14px;
        }
        .avatar-img {
            width: 108px; height: 108px; border-radius: 50%;
            object-fit: cover; border: 4px solid rgba(255,255,255,0.85);
            background: #e5e7eb;
        }
        .avatar-edit-btn {
            position: absolute; bottom: 0; right: 0;
            width: 34px; height: 34px; border-radius: 50%;
            background: #fff; color: var(--primary);
            display: flex; align-items: center; justify-content: center;
            cursor: pointer; box-shadow: 0 2px 8px rgba(0,0,0,0.25);
            border: 2px solid var(--primary-light);
        }

        .profile-banner h3 { font-weight: 700; margin-bottom: 6px; }
        .role-badge {
            display: inline-block;
            background: rgba(255,255,255,0.18);
            border: 1px solid rgba(255,255,255,0.4);
            padding: 4px 16px; border-radius: 20px;
            font-size: 0.8rem;
        }

        .profile-body { padding: 30px 34px 34px; }

        .section-header {
            display: flex; justify-content: space-between; align-items: center;
            margin: 30px 0 18px; padding-bottom: 10px;
            border-bottom: 1.5px solid #eef2f6;
        }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 {
            font-weight: 700; color: var(--primary-dark);
            font-size: 0.98rem; margin: 0;
        }
        .section-hint {
            font-size: 0.78rem; color: #9ca3af; font-weight: 500;
        }

        .field-row { margin-bottom: 18px; }
        .field-row label {
            font-size: 0.75rem; font-weight: 600; color: #6b7280;
            text-transform: uppercase; letter-spacing: .3px;
            margin-bottom: 6px; display: block;
        }
        .readonly-value {
            font-size: 0.95rem; color: #6b7280; font-weight: 500;
            min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f3f4f6;
            border-radius: 10px; padding: 8px 14px;
        }

        .form-control, .form-select {
            padding: 0.6rem 0.9rem;
            border-radius: 10px;
            border: 1px solid #e5e7eb;
            background-color: #f9fafb;
            font-size: 0.9rem;
        }
        .form-control:focus, .form-select:focus {
            background-color: #ffffff;
            border-color: var(--primary-light);
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
        }

        .error-text {
            color: var(--danger); font-size: 12px;
            margin-top: 5px; display: none;
        }

        .action-bar {
            display: flex; gap: 12px; margin-top: 28px;
            justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px;
        }

        .btn-primary {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            border: none; border-radius: 10px; padding: 0.6rem 1.4rem;
            font-weight: 600; font-size: 0.9rem;
            box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }

        .btn-cancel {
            background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280;
            border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem;
            text-decoration: none; display: inline-flex; align-items: center;
        }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) {
            .profile-body { padding: 24px 20px 28px; }
        }
    </style>
</head>
<body>

    <nav class="topbar">
        <div class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</div>
        <div class="topbar-right">

            <!-- ===== Dropdown thông báo ===== -->
            <div class="dropdown">
                <div class="bell-icon" data-bs-toggle="dropdown" aria-expanded="false">
                    <i class="fa-regular fa-bell"></i><span class="dot"></span>
                </div>
                <ul class="dropdown-menu dropdown-menu-end notif-dropdown">
                    <li class="notif-header">Thông báo <span class="notif-count">3 mới</span></li>
                    <li>
                        <a class="dropdown-item notif-item" href="#">
                            <span class="notif-icon"><i class="fa-solid fa-file-contract"></i></span>
                            <div>
                                <div class="notif-text">Hợp đồng #HD-0231 sắp hết hạn</div>
                                <div class="notif-time">10 phút trước</div>
                            </div>
                        </a>
                    </li>
                    <li>
                        <a class="dropdown-item notif-item" href="#">
                            <span class="notif-icon"><i class="fa-solid fa-headset"></i></span>
                            <div>
                                <div class="notif-text">Phiếu hỗ trợ #TK-1042 vừa được giao cho bạn</div>
                                <div class="notif-time">1 giờ trước</div>
                            </div>
                        </a>
                    </li>
                    <li>
                        <a class="dropdown-item notif-item" href="#">
                            <span class="notif-icon"><i class="fa-solid fa-user-plus"></i></span>
                            <div>
                                <div class="notif-text">Khách hàng mới được thêm: Viettel Bắc Ninh</div>
                                <div class="notif-time">Hôm qua</div>
                            </div>
                        </a>
                    </li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-center small" href="#">Xem tất cả thông báo</a></li>
                </ul>
            </div>

            <!-- ===== Dropdown avatar ===== -->
            <div class="dropdown">
                <img src="https://ui-avatars.com/api/?name=<c:out value="${profile.firstName}"/>&background=0568a6&color=fff"
                     class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header">
                        <img src="https://ui-avatars.com/api/?name=<c:out value="${profile.firstName}"/>&background=0568a6&color=fff" alt="avatar">
                        <div>
                            <div class="dd-name"><c:out value="${profile.fullName}"/></div>
                            <div class="dd-role"><c:out value="${profile.role.roleName}"/></div>
                        </div>
                    </li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/viewProfile"><i class="fa-regular fa-id-card me-2"></i>Thông tin cá nhân</a></li>
                    <li><a class="dropdown-item" href="changePassword.jsp"><i class="fa-solid fa-key me-2"></i>Đổi mật khẩu</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-danger" href="${pageContext.request.contextPath}/login?action=logout"><i class="fa-solid fa-arrow-right-from-bracket me-2"></i>Đăng xuất</a></li>
                </ul>
            </div>
        </div>
    </nav>

    <div class="page-container">
        <div class="profile-card">

            <!-- ===== Banner ===== -->
            <div class="profile-banner">
                <div class="avatar-wrap">
                    <img id="avatarPreview" class="avatar-img"
                         src="https://ui-avatars.com/api/?name=<c:out value="${profile.firstName}"/>&background=ffffff&color=0568a6&size=128" alt="Avatar">
                    <label class="avatar-edit-btn" for="avatarInput"><i class="fa-solid fa-camera"></i></label>
                    <input type="file" name="avatar" id="avatarInput" accept="image/*" hidden>
                </div>
                <h3>Sửa thông tin cá nhân</h3>
                <span class="role-badge"><c:out value="${profile.role.roleName}"/></span>
            </div>

            <div class="profile-body">

                <c:if test="${not empty param.error}">
                    <div class="alert alert-danger py-2 px-3 mb-4" style="font-size: 0.9rem; border-radius: 12px;">
                        <c:choose>
                            <c:when test="${param.error == 'missing_fields'}">Vui lòng nhập đầy đủ Họ, Tên và Số CCCD/CMND.</c:when>
                            <c:when test="${param.error == 'invalid_phone'}">Số điện thoại không hợp lệ.</c:when>
                            <c:when test="${param.error == 'invalid_email'}">Địa chỉ email không hợp lệ.</c:when>
                            <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                        </c:choose>
                    </div>
                </c:if>

                <%-- Lưu ý: chưa xử lý lưu ảnh đại diện (avatar) -- chỉ mới xem trước ở trình duyệt (JS bên dưới). --%>
                <form id="updateProfileForm" action="UpdateProfileServlet" method="POST" enctype="multipart/form-data" onsubmit="return validateForm();">

                    <!-- ===== Thông tin công việc (chỉ xem, do Admin quản lý) ===== -->
                    <div class="section-header">
                        <h5>Thông tin công việc</h5>
                        <span class="section-hint"><i class="fa-solid fa-lock me-1"></i>Chỉ Admin được chỉnh sửa</span>
                    </div>
                    <div class="row">
                        <div class="col-md-6 field-row">
                            <label>Email đăng nhập</label>
                            <div class="readonly-value"><c:out value="${profile.email}"/></div>
                        </div>
                        <div class="col-md-6 field-row">
                            <label>Phòng ban</label>
                            <div class="readonly-value"><c:out value="${profile.department}"/></div>
                        </div>
                        <div class="col-md-6 field-row">
                            <label>Vai trò</label>
                            <div class="readonly-value"><c:out value="${profile.role.roleName}"/></div>
                        </div>
                        <div class="col-md-6 field-row">
                            <label>Ngày vào làm</label>
                            <div class="readonly-value"><c:out value="${hireDateText}" default="—"/></div>
                        </div>
                    </div>

                    <!-- ===== Thông tin cá nhân (được phép chỉnh sửa) ===== -->
                    <div class="section-header"><h5>Thông tin cá nhân</h5></div>
                    <div class="row">
                        <div class="col-md-4 field-row">
                            <label for="lastName">Họ</label>
                            <input type="text" class="form-control" id="lastName" name="lastName" value="${profile.lastName}">
                            <span class="error-text" id="err-lastName">Trường này không được để trống.</span>
                        </div>
                        <div class="col-md-4 field-row">
                            <label for="middleName">Tên đệm</label>
                            <input type="text" class="form-control" id="middleName" name="middleName" value="${profile.middleName}">
                        </div>
                        <div class="col-md-4 field-row">
                            <label for="firstName">Tên</label>
                            <input type="text" class="form-control" id="firstName" name="firstName" value="${profile.firstName}">
                            <span class="error-text" id="err-firstName">Trường này không được để trống.</span>
                        </div>

                        <div class="col-md-4 field-row">
                            <label for="gender">Giới tính</label>
                            <select class="form-select" id="gender" name="gender">
                                <option value="Nam" ${profile.gender == 'Nam' ? 'selected' : ''}>Nam</option>
                                <option value="Nữ" ${profile.gender == 'Nữ' ? 'selected' : ''}>Nữ</option>
                                <option value="Khác" ${profile.gender == 'Khác' ? 'selected' : ''}>Khác</option>
                            </select>
                        </div>
                        <div class="col-md-4 field-row">
                            <label for="dob">Ngày sinh</label>
                            <input type="date" class="form-control" id="dob" name="dob" value="${profile.dateOfBirth}">
                        </div>
                        <div class="col-md-4 field-row">
                            <label for="citizenId">Số CCCD/CMND</label>
                            <input type="text" class="form-control" id="citizenId" name="citizenId" value="${profile.citizenId}">
                            <span class="error-text" id="err-citizenId">Trường này không được để trống.</span>
                        </div>

                        <div class="col-md-6 field-row">
                            <label for="phone">Số điện thoại</label>
                            <input type="tel" class="form-control" id="phone" name="phone" value="${profile.phone}">
                            <span class="error-text" id="err-phone">Số điện thoại không hợp lệ.</span>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="personalEmail">Email cá nhân</label>
                            <input type="email" class="form-control" id="personalEmail" name="personalEmail" value="${profile.personalEmail}">
                            <span class="error-text" id="err-email">Địa chỉ email không hợp lệ.</span>
                        </div>
                    </div>

                    <!-- ===== Địa chỉ ===== -->
                    <div class="section-header"><h5>Địa chỉ</h5></div>
                    <div class="row">
                        <div class="col-md-6 field-row">
                            <label for="province">Tỉnh / Thành phố</label>
                            <select class="form-select" id="province" name="provinceId">
                                <option value="">-- Chọn tỉnh / thành phố --</option>
                                <c:forEach var="prov" items="${provinceList}">
                                    <option value="${prov.provinceId}" ${profile.address != null && profile.address.district != null && prov.provinceId == profile.address.district.provinceId ? 'selected' : ''}>${prov.provinceName}</option>
                                </c:forEach>
                            </select>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="district">Xã / Phường</label>
                            <select class="form-select" id="district" name="districtId">
                                <option value="">-- Chọn xã / phường --</option>
                                <c:forEach var="dist" items="${districtList}">
                                    <option value="${dist.districtId}" data-province-id="${dist.provinceId}"
                                            ${profile.address != null && dist.districtId == profile.address.districtId ? 'selected' : ''}>${dist.districtName}</option>
                                </c:forEach>
                            </select>
                        </div>
                        <div class="col-12 field-row">
                            <label for="addressDetail">Địa chỉ chi tiết</label>
                            <input type="text" class="form-control" id="addressDetail" name="addressDetail" value="${profile.address != null ? profile.address.streetAndLocalName : ''}">
                            <span class="error-text" id="err-addressDetail">Trường này không được để trống.</span>
                        </div>
                    </div>

                    <!-- ===== Action buttons ===== -->
                    <div class="action-bar">
                        <a href="${pageContext.request.contextPath}/viewProfile" class="btn-cancel">Hủy</a>
                        <button type="submit" class="btn-primary">
                            <i class="fa-solid fa-check me-1"></i> Lưu thay đổi
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // ===== Avatar preview =====
        document.getElementById('avatarInput').addEventListener('change', function (e) {
            var file = e.target.files[0];
            if (!file) return;
            var reader = new FileReader();
            reader.onload = function (evt) {
                document.getElementById('avatarPreview').src = evt.target.result;
            };
            reader.readAsDataURL(file);
        });

        // ===== Validate (BR-09, BR-10, BR-11) =====
        function isValidPhone(value) {
            return /^(0|\+84)(3|5|7|8|9)[0-9]{8}$/.test(value.replace(/\s/g, ''));
        }
        function isValidEmail(value) {
            return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
        }

        // ===== Lọc xã/phường theo tỉnh/thành phố đã chọn =====
        var districtSelect = document.getElementById('district');
        var allDistrictOptions = Array.prototype.slice.call(districtSelect.querySelectorAll('option[data-province-id]'));
        var initialDistrictValue = districtSelect.value;

        function filterDistricts(provinceId, keepValue) {
            allDistrictOptions.forEach(function (opt) {
                opt.style.display = (opt.getAttribute('data-province-id') === provinceId) ? '' : 'none';
            });
            districtSelect.value = keepValue || '';
        }

        document.getElementById('province').addEventListener('change', function () {
            filterDistricts(this.value, null);
        });

        // Khởi tạo hiển thị đúng danh sách xã/phường theo tỉnh đã chọn sẵn khi load trang
        filterDistricts(document.getElementById('province').value, initialDistrictValue);

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            var requiredIds = ['lastName', 'firstName', 'citizenId', 'addressDetail'];
            requiredIds.forEach(function (id) {
                var el = document.getElementById(id);
                if (!el.value.trim()) {
                    var err = document.getElementById('err-' + id);
                    if (err) err.style.display = 'block';
                    valid = false;
                }
            });

            var phone = document.getElementById('phone');
            if (!isValidPhone(phone.value)) {
                document.getElementById('err-phone').style.display = 'block';
                valid = false;
            }

            var email = document.getElementById('personalEmail');
            if (!isValidEmail(email.value)) {
                document.getElementById('err-email').style.display = 'block';
                valid = false;
            }

            return valid;
        }
    </script>
</body>
</html>
