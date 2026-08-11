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
      - districtList : List<poscs.model.District>  (toàn bộ xã/phường; JS lọc theo tỉnh đã chọn
                        dựa vào data-province-id của mỗi <option>)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thêm khách hàng - POSCS Portal</title>

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

        body { font-family: 'Inter', sans-serif; background: #f3f4f6; min-height: 100vh; }

        /* ===== Topbar (đồng bộ toàn hệ thống) ===== */
        .topbar {
            background: #ffffff; box-shadow: 0 2px 12px rgba(0, 60, 110, 0.08);
            padding: 14px 28px; display: flex; justify-content: space-between; align-items: center;
            position: sticky; top: 0; z-index: 50;
        }
        .topbar .brand { font-weight: 700; color: var(--primary-dark); font-size: 1.05rem; display: flex; align-items: center; gap: 10px; }
        .topbar .brand i { color: var(--primary); font-size: 1.2rem; }
        .topbar-right { display: flex; align-items: center; gap: 18px; }
        .bell-icon { color: #6b7280; font-size: 1.1rem; cursor: pointer; position: relative; }
        .bell-icon .dot { position: absolute; top: -3px; right: -4px; width: 8px; height: 8px; border-radius: 50%; background: var(--danger); border: 1.5px solid #fff; }
        .avatar-mini { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; cursor: pointer; border: 2px solid var(--primary-light); }

        .dropdown-menu { border: none; border-radius: 14px; box-shadow: 0 14px 34px rgba(0, 40, 80, 0.18); padding: 8px; margin-top: 12px !important; min-width: 230px; }
        .dropdown-item { border-radius: 8px; padding: 9px 12px; font-size: 0.87rem; color: #374151; }
        .dropdown-item:hover, .dropdown-item:focus { background: #f0f9ff; color: var(--primary-dark); }
        .dropdown-item.text-danger:hover { background: #fdecef; color: var(--danger) !important; }
        .dd-user-header { display: flex; align-items: center; gap: 10px; padding: 8px 10px 12px; }
        .dd-user-header img { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
        .dd-user-header .dd-name { font-weight: 600; font-size: 0.9rem; color: #111827; }
        .dd-user-header .dd-role { font-size: 0.75rem; color: #6b7280; }
        .notif-dropdown { min-width: 320px; max-height: 380px; overflow-y: auto; }
        .notif-header { display: flex; justify-content: space-between; align-items: center; padding: 6px 10px 10px; font-weight: 700; font-size: 0.9rem; color: var(--primary-dark); }
        .notif-count { background: var(--primary); color: #fff; font-size: 0.68rem; padding: 2px 9px; border-radius: 10px; font-weight: 600; }
        .notif-item { display: flex; gap: 10px; align-items: flex-start; white-space: normal; }
        .notif-icon { width: 34px; height: 34px; border-radius: 50%; background: #eef6fb; color: var(--primary); display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 0.85rem; }
        .notif-text { font-size: 0.85rem; color: #111827; font-weight: 500; line-height: 1.3; }
        .notif-time { font-size: 0.72rem; color: #9ca3af; margin-top: 2px; }

        /* ===== Layout ===== */
        .page-container { max-width: 900px; margin: 28px auto; padding: 0 20px 60px; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 22px; flex-wrap: wrap; gap: 14px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 30px 34px 34px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 30px 0 18px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
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

        @media (max-width: 768px) { .card-box { padding: 24px 20px 28px; } }
    </style>
</head>
<body>

    <nav class="topbar">
        <div class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</div>
        <div class="topbar-right">
            <div class="dropdown">
                <div class="bell-icon" data-bs-toggle="dropdown" aria-expanded="false">
                    <i class="fa-regular fa-bell"></i><span class="dot"></span>
                </div>
                <ul class="dropdown-menu dropdown-menu-end notif-dropdown">
                    <li class="notif-header">Thông báo <span class="notif-count">3 mới</span></li>
                    <li><a class="dropdown-item notif-item" href="#">
                        <span class="notif-icon"><i class="fa-solid fa-file-contract"></i></span>
                        <div><div class="notif-text">Hợp đồng #HD-0231 sắp hết hạn</div><div class="notif-time">10 phút trước</div></div>
                    </a></li>
                    <li><a class="dropdown-item notif-item" href="#">
                        <span class="notif-icon"><i class="fa-solid fa-headset"></i></span>
                        <div><div class="notif-text">Phiếu hỗ trợ #TK-1042 vừa được giao cho bạn</div><div class="notif-time">1 giờ trước</div></div>
                    </a></li>
                    <li><a class="dropdown-item notif-item" href="#">
                        <span class="notif-icon"><i class="fa-solid fa-user-plus"></i></span>
                        <div><div class="notif-text">Khách hàng mới được thêm: Viettel Bắc Ninh</div><div class="notif-time">Hôm qua</div></div>
                    </a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-center small" href="#">Xem tất cả thông báo</a></li>
                </ul>
            </div>
            <div class="dropdown">
                <img src="https://ui-avatars.com/api/?name=Nguyen+An&background=0568a6&color=fff"
                     class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header">
                        <img src="https://ui-avatars.com/api/?name=Nguyen+An&background=0568a6&color=fff" alt="avatar">
                        <div><div class="dd-name">Nguyễn Văn An</div><div class="dd-role">Sales</div></div>
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
        <a href="${pageContext.request.contextPath}/customer" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <div class="page-header-row">
            <div>
                <h2>Thêm khách hàng</h2>
                <p>Tạo mới hồ sơ khách hàng doanh nghiệp</p>
            </div>
        </div>

        <div class="card-box">
            <form id="createCustomerForm" action="${pageContext.request.contextPath}/customer" method="POST" onsubmit="return validateForm();">
                <input type="hidden" name="action" value="create">

                <div class="section-header"><h5>Thông tin khách hàng</h5></div>
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
                            <option value="Nhà mạng viễn thông">Nhà mạng viễn thông</option>
                            <option value="Nhà thầu thi công">Nhà thầu thi công</option>
                            <option value="Đại lý phân phối">Đại lý phân phối</option>
                        </select>
                        <span class="error-text" id="err-customerType">Vui lòng chọn loại khách hàng.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Nhóm khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customerGroup" name="customerGroup">
                            <option value="">-- Chọn nhóm khách hàng --</option>
                            <option value="Khách hàng VIP">Khách hàng VIP</option>
                            <option value="Khách hàng thân thiết">Khách hàng thân thiết</option>
                            <option value="Khách hàng tiềm năng">Khách hàng tiềm năng</option>
                            <option value="Khách hàng thường">Khách hàng thường</option>
                        </select>
                        <span class="error-text" id="err-customerGroup">Vui lòng chọn nhóm khách hàng.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Nhân viên phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="assignee" name="accountOwnerId">
                            <option value="">-- Chọn nhân viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}">${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-assignee">Vui lòng chọn nhân viên phụ trách.</span>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Số điện thoại <span class="req">*</span></label>
                        <input type="tel" class="form-control" id="phone" name="phone" placeholder="VD: 0912345678">
                        <span class="error-text" id="err-phone">Số điện thoại không hợp lệ.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Email</label>
                        <input type="email" class="form-control" id="email" name="email" placeholder="contact@company.vn">
                        <span class="error-text" id="err-email">Địa chỉ email không hợp lệ.</span>
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
                        <label>Tỉnh / Thành phố</label>
                        <select class="form-select" id="province" name="provinceId">
                            <option value="">-- Chọn tỉnh / thành phố --</option>
                            <c:forEach var="prov" items="${provinceList}">
                                <option value="${prov.provinceId}">${fn:escapeXml(prov.shortName)}</option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Xã / Phường</label>
                        <select class="form-select" id="district" name="districtId">
                            <option value="">-- Chọn xã / phường --</option>
                            <c:forEach var="dist" items="${districtList}">
                                <option value="${dist.districtId}" data-province-id="${dist.provinceId}" style="display:none">${fn:escapeXml(dist.shortName)}</option>
                            </c:forEach>
                        </select>
                    </div>
                    <div class="col-12 field-row">
                        <label>Địa chỉ chi tiết</label>
                        <input type="text" class="form-control" id="addressDetail" name="addressDetail" placeholder="Số nhà, tên đường...">
                    </div>
                </div>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/customer" class="btn-cancel">Hủy</a>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Tạo khách hàng</button>
                </div>
            </form>
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

        // Lọc xã/phường theo tỉnh/thành phố đã chọn
        var districtSelect = document.getElementById('district');
        var allDistrictOptions = Array.prototype.slice.call(districtSelect.querySelectorAll('option[data-province-id]'));

        document.getElementById('province').addEventListener('change', function () {
            var provinceId = this.value;
            districtSelect.value = '';
            allDistrictOptions.forEach(function (opt) {
                opt.style.display = (opt.getAttribute('data-province-id') === provinceId) ? '' : 'none';
            });
        });

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

            var taxCode = document.getElementById('taxCode');
            if (!taxCode.value.trim()) { document.getElementById('err-taxCode').style.display = 'block'; valid = false; }

            var phone = document.getElementById('phone');
            if (!isValidPhone(phone.value)) { document.getElementById('err-phone').style.display = 'block'; valid = false; }

            var email = document.getElementById('email');
            if (email.value && !isValidEmail(email.value)) { document.getElementById('err-email').style.display = 'block'; valid = false; }

            var joinDate = document.getElementById('joinDate');
            if (joinDate.value) {
                var today = new Date().toISOString().split('T')[0];
                if (joinDate.value > today) { document.getElementById('err-joinDate').style.display = 'block'; valid = false; }
            }

            return valid;
        }
    </script>
</body>
</html>
