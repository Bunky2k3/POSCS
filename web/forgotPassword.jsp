<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quên mật khẩu - POSCS Portal</title>

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

        .icon-circle {
            width: 60px;
            height: 60px;
            border-radius: 50%;
            background: rgba(5, 104, 166, 0.1);
            display: flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 1.25rem;
        }

        .icon-circle i {
            font-size: 1.5rem;
            color: var(--primary);
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

        .form-label {
            font-weight: 500;
            color: #374151;
            font-size: 0.9rem;
            margin-bottom: 0.5rem;
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

        .alert-info-custom {
            background-color: #f0f9ff;
            border: 1px solid #bae6fd;
            color: var(--primary-dark);
            border-radius: 12px;
            font-size: 0.85rem;
            padding: 0.9rem 1rem;
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

            <!-- Cột phải: Form Quên mật khẩu -->
            <div class="col-md-7 form-panel bg-white">

                <div class="d-md-none text-center mb-4">
                    <img src="${pageContext.request.contextPath}/img/postef-logo.png"
                         alt="POSTEF" class="brand-logo brand-logo-lg mx-auto">
                </div>

                <div class="icon-circle">
                    <i class="fa-solid fa-key"></i>
                </div>

                <div class="mb-4">
                    <h3 class="fw-bold" style="color: #111827;">Quên mật khẩu?</h3>
                    <p class="text-muted" style="font-size: 0.95rem;">
                        Nhập email đã đăng ký, hệ thống sẽ gửi cho bạn mã OTP để xác nhận và đặt lại mật khẩu.
                    </p>
                </div>

                <%-- AuthenticationController#handleForgotPassword: kiểm tra email tồn tại, sinh mã OTP, lưu vào session, gửi OTP qua email, rồi redirect sang verifyOtp.jsp --%>
                <form action="ForgotPasswordServlet" method="POST" id="forgotPasswordForm">
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <c:if test="${not empty param.error}">
                        <div class="alert alert-danger py-2 px-3 mb-4" style="font-size: 0.9rem; border-radius: 12px;">
                            <c:choose>
                                <c:when test="${param.error == 'missing_email'}">Vui lòng nhập email.</c:when>
                                <c:when test="${param.error == 'invalid_email'}">Địa chỉ email không đúng định dạng.</c:when>
                                <c:when test="${param.error == 'unauthorized'}">Vui lòng thực hiện lại từ bước nhập email.</c:when>
                                <c:when test="${param.error == 'update_failed'}">Có lỗi xảy ra, vui lòng thử lại.</c:when>
                                <c:when test="${param.error == 'session_expired'}">Phiên làm việc đã hết hạn, vui lòng thử lại.</c:when>
                                <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                            </c:choose>
                        </div>
                    </c:if>
                    <div class="mb-4">
                        <label for="email" class="form-label">Email đăng ký</label>
                        <div class="input-group">
                            <span class="input-group-text"><i class="fa-regular fa-envelope"></i></span>
                            <input type="email" class="form-control" id="email" name="email"
                                   placeholder="Nhập email của bạn..." required autofocus>
                        </div>
                    </div>

                    <div class="d-grid mb-3">
                        <button type="submit" class="btn btn-primary">
                            Gửi mã OTP <i class="fa-solid fa-paper-plane ms-2"></i>
                        </button>
                    </div>

                    <div class="alert-info-custom mb-4">
                        <i class="fa-solid fa-circle-info me-1"></i>
                        Mã OTP gồm 6 chữ số, có hiệu lực trong <strong>5 phút</strong>. Vui lòng kiểm tra cả hộp thư rác (Spam) nếu không thấy email.
                    </div>

                    <div class="text-center">
                        <a href="login.jsp" class="custom-link" style="font-size: 0.9rem;">
                            <i class="fa-solid fa-arrow-left-long me-1"></i> Quay lại đăng nhập
                        </a>
                    </div>

                    <!-- Thông báo liên hệ HR/IT dành riêng cho việc cấp tài khoản mới -->
                    <div class="p-3 rounded mt-4" style="background-color: #f8fafc; border: 1px dashed #cbd5e1; text-align: center;">
                        <span class="text-muted" style="font-size: 0.85rem; line-height: 1.5; display: block;">
                            <i class="fa-solid fa-circle-info text-primary mb-1"></i><br>
                            Không nhớ email đã đăng ký?<br>
                            Vui lòng liên hệ <strong>Phòng Nhân sự / IT</strong> để được hỗ trợ.
                        </span>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
