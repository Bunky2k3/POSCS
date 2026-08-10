<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Đổi Mật Khẩu - POSCS Portal</title>

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
        --text-dark: #0a2540;
        --text-muted: #5c7a92;
        --danger: #e2536b;
        --success: #2fbf8f;
        --bg-card: #ffffff;
    }

    * { box-sizing: border-box; margin: 0; padding: 0; }

    html, body { height: 100%; }

    body {
        min-height: 100vh;
        font-family: 'Inter', sans-serif;
        display: flex;
        flex-direction: column;
    }

    /* ===== Topbar (đồng bộ với viewProfile.jsp / updateProfile.jsp) ===== */
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

    /* ===== Vùng nội dung đổi mật khẩu (nền gradient xanh) ===== */
    .auth-content {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 24px;
        position: relative;
        overflow: hidden;
        background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary) 45%, var(--primary-light) 100%);
    }

    /* Trang trí sóng nước phía sau */
    .auth-content::before, .auth-content::after {
        content: "";
        position: absolute;
        border-radius: 50%;
        background: rgba(255,255,255,0.06);
    }
    .auth-content::before {
        width: 500px; height: 500px;
        top: -150px; left: -150px;
    }
    .auth-content::after {
        width: 400px; height: 400px;
        bottom: -120px; right: -100px;
        background: rgba(255,255,255,0.08);
    }

    .container {
        width: 100%;
        max-width: 440px;
        background: var(--bg-card);
        border-radius: 18px;
        box-shadow: 0 20px 50px rgba(0, 40, 80, 0.35);
        overflow: hidden;
        position: relative;
        z-index: 1;
        animation: fadeUp 0.5s ease;
    }

    @keyframes fadeUp {
        from { opacity: 0; transform: translateY(20px); }
        to { opacity: 1; transform: translateY(0); }
    }

    .header {
        background: linear-gradient(120deg, var(--primary-dark), var(--primary));
        padding: 34px 30px 28px;
        text-align: center;
        color: #fff;
    }

    .header .icon-circle {
        width: 64px;
        height: 64px;
        margin: 0 auto 14px;
        border-radius: 50%;
        background: rgba(255,255,255,0.15);
        display: flex;
        align-items: center;
        justify-content: center;
        border: 2px solid rgba(255,255,255,0.3);
    }

    .header .icon-circle svg {
        width: 30px;
        height: 30px;
        fill: #fff;
    }

    .header h1 {
        font-size: 22px;
        font-weight: 600;
        letter-spacing: 0.3px;
    }

    .header p {
        margin-top: 6px;
        font-size: 13.5px;
        color: rgba(255,255,255,0.85);
    }

    .form-body {
        padding: 30px 30px 34px;
    }

    .alert {
        padding: 11px 14px;
        border-radius: 10px;
        font-size: 13.5px;
        margin-bottom: 18px;
        display: flex;
        align-items: center;
        gap: 8px;
    }
    .alert-error {
        background: #fdecef;
        color: var(--danger);
        border: 1px solid #f6c3cd;
    }
    .alert-success {
        background: #e8faf3;
        color: var(--success);
        border: 1px solid #b9ecd8;
    }

    .form-group {
        margin-bottom: 20px;
        position: relative;
    }

    .form-group label {
        display: block;
        font-size: 13.5px;
        font-weight: 600;
        color: var(--text-dark);
        margin-bottom: 7px;
    }

    .input-wrap {
        position: relative;
        display: flex;
        align-items: center;
    }

    .input-wrap .icon-left {
        position: absolute;
        left: 13px;
        width: 18px;
        height: 18px;
        fill: var(--text-muted);
        pointer-events: none;
    }

    .input-wrap input {
        width: 100%;
        padding: 12px 42px 12px 40px;
        border: 1.5px solid #d9e6ee;
        border-radius: 10px;
        font-size: 14.5px;
        color: var(--text-dark);
        outline: none;
        transition: border-color 0.2s, box-shadow 0.2s;
        background: #f7fbfd;
        font-family: 'Inter', sans-serif;
    }

    .input-wrap input:focus {
        border-color: var(--primary-light);
        box-shadow: 0 0 0 3px rgba(15, 158, 219, 0.15);
        background: #fff;
    }

    .toggle-eye {
        position: absolute;
        right: 12px;
        cursor: pointer;
        width: 19px;
        height: 19px;
        fill: var(--text-muted);
        user-select: none;
    }
    .toggle-eye:hover { fill: var(--primary); }

    .error-text {
        color: var(--danger);
        font-size: 12.5px;
        margin-top: 6px;
        display: none;
    }

    .strength-meter {
        margin-top: 8px;
        height: 5px;
        border-radius: 3px;
        background: #e6eef3;
        overflow: hidden;
    }
    .strength-meter-bar {
        height: 100%;
        width: 0%;
        border-radius: 3px;
        transition: width 0.3s ease, background 0.3s ease;
    }
    .strength-label {
        font-size: 11.5px;
        color: var(--text-muted);
        margin-top: 4px;
        display: block;
    }

    .btn-submit {
        width: 100%;
        padding: 13px;
        border: none;
        border-radius: 10px;
        background: linear-gradient(120deg, var(--primary), var(--primary-light));
        color: #fff;
        font-size: 15px;
        font-weight: 600;
        cursor: pointer;
        margin-top: 8px;
        letter-spacing: 0.3px;
        transition: transform 0.15s, box-shadow 0.15s, opacity 0.15s;
        box-shadow: 0 8px 18px rgba(5, 104, 166, 0.35);
        font-family: 'Inter', sans-serif;
    }
    .btn-submit:hover { transform: translateY(-1px); box-shadow: 0 10px 22px rgba(5, 104, 166, 0.45); }
    .btn-submit:active { transform: translateY(0); }

    .back-link {
        display: block;
        text-align: center;
        margin-top: 18px;
        font-size: 13.5px;
        color: var(--primary);
        text-decoration: none;
        font-weight: 500;
    }
    .back-link:hover { text-decoration: underline; }

    .hint-list {
        font-size: 12px;
        color: var(--text-muted);
        margin-top: 10px;
        padding-left: 18px;
        line-height: 1.6;
    }

    @media (max-width: 480px) {
        .header { padding: 28px 22px 24px; }
        .form-body { padding: 24px 22px 28px; }
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
                <img src="https://ui-avatars.com/api/?name=Nguyen+An&background=0568a6&color=fff"
                     class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header">
                        <img src="https://ui-avatars.com/api/?name=Nguyen+An&background=0568a6&color=fff" alt="avatar">
                        <div>
                            <div class="dd-name">Nguyễn Văn An</div>
                            <div class="dd-role">Sales</div>
                        </div>
                    </li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item" href="viewProfile.jsp"><i class="fa-regular fa-id-card me-2"></i>Thông tin cá nhân</a></li>
                    <li><a class="dropdown-item" href="changePassword.jsp"><i class="fa-solid fa-key me-2"></i>Đổi mật khẩu</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-danger" href="login.jsp"><i class="fa-solid fa-arrow-right-from-bracket me-2"></i>Đăng xuất</a></li>
                </ul>
            </div>
        </div>
    </nav>

    <div class="auth-content">
        <div class="container">
            <div class="header">
                <div class="icon-circle">
                    <svg viewBox="0 0 24 24"><path d="M12 1a5 5 0 0 0-5 5v3H6a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-9a2 2 0 0 0-2-2h-1V6a5 5 0 0 0-5-5zm0 2a3 3 0 0 1 3 3v3H9V6a3 3 0 0 1 3-3zm0 10a2 2 0 0 1 1 3.73V19a1 1 0 1 1-2 0v-2.27A2 2 0 0 1 12 13z"/></svg>
                </div>
                <h1>Đổi Mật Khẩu</h1>
                <p>Bảo mật tài khoản của bạn với mật khẩu mới</p>
            </div>

            <div class="form-body">

                <c:if test="${not empty param.error}">
                    <div class="alert alert-error">
                        <i class="fa-solid fa-circle-exclamation"></i>
                        <c:choose>
                            <c:when test="${param.error == 'wrong_old_password'}">Mật khẩu hiện tại không đúng.</c:when>
                            <c:when test="${param.error == 'weak_password'}">Mật khẩu mới phải có ít nhất 8 ký tự.</c:when>
                            <c:when test="${param.error == 'mismatch'}">Mật khẩu xác nhận không khớp.</c:when>
                            <c:when test="${param.error == 'same_as_old'}">Mật khẩu mới không được trùng với mật khẩu cũ.</c:when>
                            <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                        </c:choose>
                    </div>
                </c:if>
                <c:if test="${param.success == '1'}">
                    <div class="alert alert-success">
                        <i class="fa-solid fa-circle-check"></i>
                        Đổi mật khẩu thành công.
                    </div>
                </c:if>

                <form id="changePasswordForm" method="post" action="${pageContext.request.contextPath}/changePassword" onsubmit="return validateForm();">

                    <div class="form-group">
                        <label for="oldPassword">Mật khẩu hiện tại</label>
                        <div class="input-wrap">
                            <svg class="icon-left" viewBox="0 0 24 24"><path d="M12 17a2 2 0 0 0 2-2 2 2 0 0 0-2-2 2 2 0 0 0-2 2 2 2 0 0 0 2 2zm6-9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V6a5 5 0 0 1 10 0v2h1zM12 3a3 3 0 0 0-3 3v2h6V6a3 3 0 0 0-3-3z"/></svg>
                            <input type="password" id="oldPassword" name="oldPassword" placeholder="Nhập mật khẩu hiện tại" required>
                            <svg class="toggle-eye" data-target="oldPassword" viewBox="0 0 24 24">
                                <path d="M12 5c-7 0-11 7-11 7s4 7 11 7 11-7 11-7-4-7-11-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/>
                            </svg>
                        </div>
                        <span class="error-text" id="err-oldPassword">Vui lòng nhập mật khẩu hiện tại</span>
                    </div>

                    <div class="form-group">
                        <label for="newPassword">Mật khẩu mới</label>
                        <div class="input-wrap">
                            <svg class="icon-left" viewBox="0 0 24 24"><path d="M12 17a2 2 0 0 0 2-2 2 2 0 0 0-2-2 2 2 0 0 0-2 2 2 2 0 0 0 2 2zm6-9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V6a5 5 0 0 1 10 0v2h1zM12 3a3 3 0 0 0-3 3v2h6V6a3 3 0 0 0-3-3z"/></svg>
                            <input type="password" id="newPassword" name="newPassword" placeholder="Nhập mật khẩu mới" required oninput="checkStrength(this.value)">
                            <svg class="toggle-eye" data-target="newPassword" viewBox="0 0 24 24">
                                <path d="M12 5c-7 0-11 7-11 7s4 7 11 7 11-7 11-7-4-7-11-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/>
                            </svg>
                        </div>
                        <div class="strength-meter"><div class="strength-meter-bar" id="strengthBar"></div></div>
                        <span class="strength-label" id="strengthLabel">Độ mạnh mật khẩu</span>
                        <span class="error-text" id="err-newPassword">Mật khẩu mới phải có ít nhất 8 ký tự</span>
                    </div>

                    <div class="form-group">
                        <label for="confirmPassword">Xác nhận mật khẩu mới</label>
                        <div class="input-wrap">
                            <svg class="icon-left" viewBox="0 0 24 24"><path d="M12 17a2 2 0 0 0 2-2 2 2 0 0 0-2-2 2 2 0 0 0-2 2 2 2 0 0 0 2 2zm6-9a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2h1V6a5 5 0 0 1 10 0v2h1zM12 3a3 3 0 0 0-3 3v2h6V6a3 3 0 0 0-3-3z"/></svg>
                            <input type="password" id="confirmPassword" name="confirmPassword" placeholder="Nhập lại mật khẩu mới" required>
                            <svg class="toggle-eye" data-target="confirmPassword" viewBox="0 0 24 24">
                                <path d="M12 5c-7 0-11 7-11 7s4 7 11 7 11-7 11-7-4-7-11-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-8a3 3 0 1 0 0 6 3 3 0 0 0 0-6z"/>
                            </svg>
                        </div>
                        <span class="error-text" id="err-confirmPassword">Mật khẩu xác nhận không khớp</span>
                    </div>

                    <ul class="hint-list">
                        <li>Tối thiểu 8 ký tự</li>
                        <li>Nên có chữ hoa, chữ thường và số</li>
                        <li>Không trùng với mật khẩu cũ</li>
                    </ul>

                    <button type="submit" class="btn-submit">Cập nhật mật khẩu</button>
                </form>

                <a href="viewProfile.jsp" class="back-link">← Quay lại trang cá nhân</a>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Toggle hiện / ẩn mật khẩu
        document.querySelectorAll('.toggle-eye').forEach(function (eye) {
            eye.addEventListener('click', function () {
                var input = document.getElementById(eye.getAttribute('data-target'));
                input.type = (input.type === 'password') ? 'text' : 'password';
            });
        });

        // Đánh giá độ mạnh mật khẩu
        function checkStrength(value) {
            var bar = document.getElementById('strengthBar');
            var label = document.getElementById('strengthLabel');
            var score = 0;

            if (value.length >= 8) score++;
            if (/[A-Z]/.test(value)) score++;
            if (/[0-9]/.test(value)) score++;
            if (/[^A-Za-z0-9]/.test(value)) score++;

            var colors = ['#e2536b', '#e2536b', '#f5a623', '#2fbf8f', '#0568a6'];
            var texts = ['Rất yếu', 'Yếu', 'Trung bình', 'Mạnh', 'Rất mạnh'];
            var percents = [15, 30, 55, 80, 100];

            if (value.length === 0) {
                bar.style.width = '0%';
                label.textContent = 'Độ mạnh mật khẩu';
                return;
            }
            bar.style.width = percents[score] + '%';
            bar.style.background = colors[score];
            label.textContent = texts[score];
        }

        // Validate form phía client trước khi submit
        function validateForm() {
            var oldPass = document.getElementById('oldPassword');
            var newPass = document.getElementById('newPassword');
            var confirmPass = document.getElementById('confirmPassword');
            var valid = true;

            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            if (oldPass.value.trim() === '') {
                document.getElementById('err-oldPassword').style.display = 'block';
                valid = false;
            }
            if (newPass.value.length < 8) {
                document.getElementById('err-newPassword').style.display = 'block';
                valid = false;
            }
            if (confirmPass.value !== newPass.value || confirmPass.value === '') {
                document.getElementById('err-confirmPassword').style.display = 'block';
                valid = false;
            }
            return valid;
        }
    </script>

</body>
</html>
