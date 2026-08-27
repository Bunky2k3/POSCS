<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
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
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/flatpickr/dist/flatpickr.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container {
            max-width: 900px;
            margin: 32px auto;
            padding: 0 20px 32px;
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

        .profile-body { padding: 24px 28px 28px; }

        .section-header {
            display: flex; justify-content: space-between; align-items: center;
            margin: 24px 0 16px; padding-bottom: 10px;
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
            .profile-body { padding: 20px 18px 22px; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="profile" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


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
                            <c:when test="${param.error == 'missing_address'}">Vui lòng chọn Tỉnh/Thành phố và Xã/Phường.</c:when>
                            <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                        </c:choose>
                    </div>
                </c:if>

                <%-- Lưu ý: chưa xử lý lưu ảnh đại diện (avatar) -- chỉ mới xem trước ở trình duyệt (JS bên dưới). --%>
                <form id="updateProfileForm" action="UpdateProfileServlet" method="POST" enctype="multipart/form-data" onsubmit="return validateForm();">
                    <input type="hidden" name="csrfToken" value="${csrfToken}">

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
                            <div class="readonly-value"><c:out value="${profile.department.departmentName}"/></div>
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
                                    <option value="${prov.provinceId}" ${profile.address != null && profile.address.district != null && prov.provinceId == profile.address.district.provinceId ? 'selected' : ''}>${prov.shortName}</option>
                                </c:forEach>
                            </select>
                            <span class="error-text" id="err-province">Vui lòng chọn tỉnh / thành phố.</span>
                        </div>
                        <div class="col-md-6 field-row">
                            <label for="district">Xã / Phường</label>
                            <select class="form-select" id="district" name="districtId">
                                <c:choose>
                                    <c:when test="${profile.address != null && profile.address.district != null}">
                                        <option value="${profile.address.districtId}" selected>${profile.address.district.shortName}</option>
                                    </c:when>
                                    <c:otherwise>
                                        <option value="">-- Chọn xã / phường --</option>
                                    </c:otherwise>
                                </c:choose>
                            </select>
                            <span class="error-text" id="err-district">Vui lòng chọn xã / phường.</span>
                        </div>
                        <div class="col-12 field-row">
                            <label for="addressDetail">Địa chỉ chi tiết <span class="text-muted" style="text-transform: none; font-weight: 400;">(không bắt buộc)</span></label>
                            <input type="text" class="form-control" id="addressDetail" name="addressDetail" value="${profile.address != null ? profile.address.streetAndLocalName : ''}">
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

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/flatpickr"></script>
    <script>
        flatpickr('#dob', { dateFormat: 'Y-m-d', altInput: true, altFormat: 'd/m/Y', minDate: '1900-01-01', maxDate: 'today' });

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

        // ===== Nạp xã/phường theo tỉnh/thành phố đã chọn qua AJAX =====
        // (thay vì đổ sẵn ~3.321 xã/phường vào trang -- xem AddressController)
        var contextPath = '${pageContext.request.contextPath}';
        var districtSelect = document.getElementById('district');
        var initialDistrictValue = districtSelect.value;

        function escapeHtml(value) {
            var div = document.createElement('div');
            div.textContent = value;
            return div.innerHTML;
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

            var requiredIds = ['lastName', 'firstName', 'citizenId', 'province', 'district'];
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

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
