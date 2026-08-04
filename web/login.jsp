<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
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
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.08);
            overflow: hidden;
            max-width: 1000px;
            width: 100%;
            margin: 20px;
        }

        .placeholder-box {
            border: 2px dashed;
            display: flex;
            align-items: center;
            justify-content: center;
            text-align: center;
            border-radius: 8px;
            font-size: 0.85rem;
            font-weight: 600;
            padding: 10px;
            line-height: 1.4;
        }
        
        .placeholder-light {
            border-color: rgba(255, 255, 255, 0.7);
            background: rgba(255, 255, 255, 0.15);
            color: #ffffff;
        }

        .placeholder-dark {
            border-color: #a1a1aa;
            background: #f4f4f5;
            color: #52525b;
        }

        .brand-panel {
            background: linear-gradient(rgba(79, 70, 229, 0.85), rgba(37, 99, 235, 0.85));
            color: white;
            padding: 3rem;
            display: flex;
            flex-direction: column;
            justify-content: center;
            position: relative;
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
            border-color: #4f46e5;
            box-shadow: 0 0 0 4px rgba(79, 70, 229, 0.1);
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

        .btn-primary {
            background: #4f46e5;
            border: none;
            border-radius: 12px;
            padding: 0.8rem;
            font-weight: 600;
            font-size: 1rem;
            transition: all 0.3s ease;
        }

        .btn-primary:hover {
            background: #4338ca;
            transform: translateY(-2px);
            box-shadow: 0 8px 15px rgba(79, 70, 229, 0.3);
        }

        .custom-link {
            color: #4f46e5;
            font-weight: 500;
            text-decoration: none;
            transition: color 0.2s;
        }

        .custom-link:hover {
            color: #3730a3;
            text-decoration: underline;
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
                <div class="placeholder-box placeholder-light mb-4" style="position: absolute; top: 20px; right: 20px; padding: 5px 15px; font-size: 0.75rem;">
                    [VỊ TRÍ 2]<br>Ảnh Nền Doanh Nghiệp
                </div>

                <div style="z-index: 1;">
                    <div class="placeholder-box placeholder-light mb-4" style="height: 60px; width: 220px;">
                        <i class="fa-regular fa-image me-2 fs-4"></i>
                        <div class="text-start">
                            [VỊ TRÍ 3] <br>
                            <span style="font-size: 0.75rem; font-weight: 400;">Logo (Màu trắng)</span>
                        </div>
                    </div>
                    
                    <h2 class="fw-bold mb-3">POSCS Portal</h2>
                    <p class="fs-6" style="color: rgba(255,255,255,0.85); line-height: 1.6;">
                        Hệ thống quản lý dịch vụ hỗ trợ kỹ thuật và hợp đồng chuyên nghiệp dành cho doanh nghiệp B2B.
                    </p>
                </div>
            </div>

            <!-- Cột phải: Form Đăng nhập -->
            <div class="col-md-7 form-panel bg-white">
                
                <div class="d-md-none text-center mb-4">
                    <div class="placeholder-box placeholder-dark mx-auto mb-3" style="height: 60px; width: 200px;">
                        <i class="fa-regular fa-image me-2 fs-4 text-secondary"></i>
                        <div class="text-start">
                            [VỊ TRÍ 4] <br>
                            <span style="font-size: 0.75rem; font-weight: 400;">Logo (Màu gốc)</span>
                        </div>
                    </div>
                    <h3 class="fw-bold">POSCS Portal</h3>
                </div>

                <div class="mb-4">
                    <h3 class="fw-bold" style="color: #111827;">Chào mừng trở lại! 👋</h3>
                    <p class="text-muted" style="font-size: 0.95rem;">Vui lòng nhập thông tin để truy cập hệ thống.</p>
                </div>

                <form action="#" method="POST" id="loginForm">
                    <div class="mb-4">
                        <label for="username" class="form-label">Tên đăng nhập / Email</label>
                        <div class="input-group">
                            <span class="input-group-text"><i class="fa-regular fa-user"></i></span>
                            <input type="text" class="form-control" id="username" name="username" 
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
    </script>
</body>
</html>