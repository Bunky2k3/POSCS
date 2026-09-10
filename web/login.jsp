<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đăng nhập - POSCS Portal</title>
    
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
        }

        body {
            font-family: 'Inter', sans-serif;
            background: #f3f4f6;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .login-wrapper {
            background: #ffffff;
            border-radius: 24px;
            box-shadow: 0 20px 40px rgba(0, 60, 110, 0.15);
            overflow: hidden;
            max-width: 1000px;
            width: 100%;
            margin: 20px;
        }

        /* Logo lấy từ postef.com.vn (web/img/postef-logo.png) là bản màu gốc:
           chữ xanh trên nền trong suốt. Trên panel branding nền xanh đậm cần
           bản trắng, nên lật màu bằng filter thay vì kèm thêm một file ảnh
           thứ hai phải tự chỉnh và giữ đồng bộ. */
        .brand-logo {
            height: 44px;
            width: auto;
            display: block;
            /* .brand-panel là flex column, mặc định align-items kéo giãn item
               con cho bằng chiều ngang panel -- ảnh logo là item con trực tiếp
               nên bị kéo bè ra gần gấp đôi (345px thay vì 175px), chữ POSTEF
               nhìn dãn hết cỡ. width:auto KHÔNG cứu được, phải chặn stretch. */
            align-self: flex-start;
        }

        .brand-logo-light {
            /* brightness(0) invert(1): lật logo màu gốc thành trắng.
               drop-shadow: ảnh nền phía sau có mảng sáng, cần bóng mềm để logo
               trắng không lẫn vào. */
            filter: brightness(0) invert(1) drop-shadow(0 2px 6px rgba(0, 30, 60, 0.55));
        }

        /* Logo đứng một mình ở bản mobile (không còn dòng "POSCS Portal" kèm
           bên dưới nữa) nên phóng to hơn một chút. */
        .brand-logo-lg {
            height: 52px;
        }

        .brand-copy p {
            color: rgba(255, 255, 255, 0.9);
            line-height: 1.6;
        }

        .brand-panel {
            /* Ảnh nền doanh nghiệp (banner trang chủ postef.com.vn). Lớp phủ
               chuyển sắc theo CHIỀU DỌC chứ không phủ đều: đậm ở trên cho logo
               trắng nổi, gần như trong suốt ở giữa để nhìn rõ ảnh, đậm hẳn ở
               dưới cho khối chữ trắng đọc được. Phủ đều tay như trước làm ảnh
               chìm hẳn, nhìn chỉ thấy một mảng xanh. */
            background:
                linear-gradient(180deg,
                    rgba(0, 45, 85, 0.62) 0%,
                    rgba(0, 45, 85, 0.12) 28%,
                    rgba(0, 45, 85, 0.22) 52%,
                    rgba(0, 40, 78, 0.95) 100%),
                url("${pageContext.request.contextPath}/img/brand-bg.jpg") center / cover no-repeat;
            color: white;
            padding: 2.25rem 2.25rem 2.75rem;
            display: flex;
            flex-direction: column;
            /* Logo bám mép trên, khối chữ dồn xuống đáy -- tách hẳn hai khối
               nhận diện ra hai đầu panel thay vì xếp sát nhau ở giữa. */
            justify-content: space-between;
            position: relative;
            overflow: hidden;
        }

        .form-panel {
            padding: 4rem 3rem;
        }

        .form-control {
            padding: 0.75rem 1rem;
            border-radius: 12px;
            border: 1px solid #e5e7eb;
            background-color: #f9fafb;
            font-size: 0.95rem;
            transition: all 0.3s;
        }

        .form-control:focus {
            background-color: #ffffff;
            border-color: var(--primary-light);
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
        }

        .input-group-text {
            background-color: #f9fafb;
            border: 1px solid #e5e7eb;
            border-right: none;
            color: #6b7280;
            border-radius: 12px 0 0 12px;
        }

        .form-control { border-left: none; }

        .btn-toggle-pass {
            background-color: #f9fafb;
            border: 1px solid #e5e7eb;
            border-left: none;
            color: #6b7280;
            border-radius: 0 12px 12px 0;
            cursor: pointer;
        }

        .form-label {
            font-weight: 500;
            color: #374151;
            font-size: 0.9rem;
            margin-bottom: 0.5rem;
        }

        .form-check-input {
            accent-color: var(--primary);
        }
        .form-check-input:checked {
            background-color: var(--primary);
            border-color: var(--primary);
        }
        .form-check-input:focus {
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
            border-color: var(--primary-light);
        }

        .btn-primary {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            border: none;
            border-radius: 12px;
            padding: 0.8rem;
            font-weight: 600;
            font-size: 1rem;
            transition: all 0.3s ease;
            box-shadow: 0 8px 18px rgba(5, 104, 166, 0.3);
        }

        .btn-primary:hover {
            background: linear-gradient(120deg, var(--primary-dark), var(--primary));
            transform: translateY(-2px);
            box-shadow: 0 10px 22px rgba(5, 104, 166, 0.4);
        }

        .custom-link {
            color: var(--primary);
            font-weight: 500;
            text-decoration: none;
            transition: color 0.2s;
        }

        .custom-link:hover {
            color: var(--primary-dark);
            text-decoration: underline;
        }

        .text-primary {
            color: var(--primary) !important;
        }

        @media (max-width: 768px) {
            .form-panel { padding: 2.5rem 1.5rem; }
        }
    </style>
</head>
<body>

    <div class="login-wrapper">
        <div class="row g-0">
            <!-- Cột trái: Branding (Ẩn trên Mobile) -->
            <div class="col-md-5 d-none d-md-flex brand-panel">
                <img src="${pageContext.request.contextPath}/img/postef-logo.png"
                     alt="POSTEF" class="brand-logo brand-logo-light">

                <div class="brand-copy">
                    <h2 class="fw-bold mb-3">POSCS Portal</h2>
                    <p class="fs-6 mb-0">
                        Hệ thống quản lý dịch vụ hỗ trợ kỹ thuật và hợp đồng chuyên nghiệp dành cho doanh nghiệp B2B.
                    </p>
                </div>
            </div>

            <!-- Cột phải: Form Đăng nhập -->
            <div class="col-md-7 form-panel bg-white">
                
                <div class="d-md-none text-center mb-4">
                    <img src="${pageContext.request.contextPath}/img/postef-logo.png"
                         alt="POSTEF" class="brand-logo brand-logo-lg mx-auto">
                </div>

                <div class="mb-4">
                    <h3 class="fw-bold" style="color: #111827;">Chào mừng trở lại! 👋</h3>
                    <p class="text-muted" style="font-size: 0.95rem;">Vui lòng nhập thông tin để truy cập hệ thống.</p>
                </div>

                <form action="${pageContext.request.contextPath}/login" method="POST" id="loginForm">
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <c:if test="${not empty param.error}">
                        <div class="alert alert-danger py-2 px-3 mb-4" style="font-size: 0.9rem; border-radius: 12px;">
                            <c:choose>
                                <c:when test="${param.error == 'invalid_credentials'}">Sai tên đăng nhập hoặc mật khẩu.</c:when>
                                <c:when test="${param.error == 'account_inactive'}">Tài khoản này đã bị khóa hoặc ngừng hoạt động. Vui lòng liên hệ quản trị viên để được hỗ trợ.</c:when>
                                <c:when test="${param.error == 'missing_fields'}">Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.</c:when>
                                <c:when test="${param.error == 'too_many_attempts'}">Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau ít phút.</c:when>
                                <c:otherwise>Đăng nhập thất bại. Vui lòng thử lại.</c:otherwise>
                            </c:choose>
                        </div>
                    </c:if>
                    <c:if test="${param.reset == 'success'}">
                        <div class="alert alert-success py-2 px-3 mb-4" style="font-size: 0.9rem; border-radius: 12px;">
                            Đổi mật khẩu thành công. Vui lòng đăng nhập lại.
                        </div>
                    </c:if>
                    <div class="mb-4">
                        <label for="username" class="form-label">Tên đăng nhập / Email</label>
                        <div class="input-group">
                            <span class="input-group-text"><i class="fa-regular fa-user"></i></span>
                            <input type="text" class="form-control" id="username" name="username"
                                   value="<c:out value="${param.username}"/>"
                                   placeholder="Nhập email hoặc username..." required autofocus>
                        </div>
                    </div>

                    <div class="mb-4">
                        <label for="password" class="form-label">Mật khẩu</label>
                        <div class="input-group">
                            <span class="input-group-text"><i class="fa-solid fa-lock"></i></span>
                            <input type="password" class="form-control" id="password" name="password" 
                                   placeholder="••••••••" required>
                            <span class="input-group-text btn-toggle-pass" id="togglePassword">
                                <i class="fa-regular fa-eye"></i>
                            </span>
                        </div>
                    </div>

                    <!-- Ghi nhớ & Quên mật khẩu (Vẫn giữ link forgotPassword.jsp) -->
                    <div class="d-flex justify-content-between align-items-center mb-4">
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="rememberMe" style="cursor: pointer;">
                            <label class="form-check-label text-muted" for="rememberMe" style="font-size: 0.9rem; cursor: pointer;">
                                Ghi nhớ thiết bị
                            </label>
                        </div>
                        <a href="forgotPassword.jsp" class="custom-link" style="font-size: 0.9rem;">Quên mật khẩu?</a>
                    </div>

                    <div class="d-grid mb-3">
                        <button type="submit" class="btn btn-primary">
                            Đăng nhập <i class="fa-solid fa-arrow-right-to-bracket ms-2"></i>
                        </button>
                    </div>

                    <!-- Thông báo liên hệ HR/IT dành riêng cho việc cấp tài khoản mới (Đã bỏ phần Đăng ký tự do) -->
                    <div class="p-3 rounded mt-4" style="background-color: #f8fafc; border: 1px dashed #cbd5e1; text-align: center;">
                        <span class="text-muted" style="font-size: 0.85rem; line-height: 1.5; display: block;">
                            <i class="fa-solid fa-circle-info text-primary mb-1"></i><br>
                            Bạn chưa có tài khoản hệ thống?<br>
                            Vui lòng liên hệ <strong>Phòng Nhân sự / IT</strong> để được cấp tài khoản.
                        </span>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        document.getElementById('togglePassword').addEventListener('click', function () {
            const passwordInput = document.getElementById('password');
            const icon = this.querySelector('i');
            if (passwordInput.type === 'password') {
                passwordInput.type = 'text';
                icon.classList.replace('fa-eye', 'fa-eye-slash');
            } else {
                passwordInput.type = 'password';
                icon.classList.replace('fa-eye-slash', 'fa-eye');
            }
        });

        // Bấm Back quay lại login.jsp (vd. sau khi đăng nhập xong, KHÔNG qua
        // đăng xuất) có thể phục hồi nguyên trạng form từ bfcache -- kể cả
        // username/password đã gõ trước đó, khiến bấm "Đăng nhập" lại là vào
        // được ngay không cần biết mật khẩu. Ép reload thật khi phát hiện
        // trang được phục hồi từ bfcache để form luôn trống, giống trang mới.
        window.addEventListener('pageshow', function (event) {
            if (event.persisted) {
                window.location.reload();
            }
        });
    </script>
</body>
</html>
