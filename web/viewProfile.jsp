<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Thông tin cá nhân - POSCS Portal</title>

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

        .avatar-img {
            width: 108px; height: 108px; border-radius: 50%;
            object-fit: cover; border: 4px solid rgba(255,255,255,0.85);
            background: #e5e7eb; margin-bottom: 14px;
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

        .btn-outline-edit {
            border: 1.5px solid var(--primary); color: var(--primary);
            background: #fff; font-weight: 600; border-radius: 10px;
            padding: 6px 16px; font-size: 0.83rem; transition: all 0.2s;
            text-decoration: none; display: inline-flex; align-items: center;
        }
        .btn-outline-edit:hover { background: var(--primary); color: #fff; }

        .field-row { margin-bottom: 18px; }
        .field-row label {
            font-size: 0.75rem; font-weight: 600; color: #6b7280;
            text-transform: uppercase; letter-spacing: .3px;
            margin-bottom: 6px; display: block;
        }
        .field-row .view-value {
            font-size: 0.95rem; color: #111827; font-weight: 500;
            min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f9fafb;
            border-radius: 10px; padding: 8px 14px;
        }

        @media (max-width: 768px) {
            .profile-body { padding: 24px 20px 28px; }
        }
    </style>
</head>
<body>

    <nav class="topbar">
        <div class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</div>
        <div class="topbar-right">
            <div class="bell-icon"><i class="fa-regular fa-bell"></i><span class="dot"></span></div>
            <img src="https://ui-avatars.com/api/?name=Nguyen+An&background=0568a6&color=fff" class="avatar-mini" alt="avatar">
        </div>
    </nav>

    <div class="page-container">
        <div class="profile-card">

            <!-- ===== Banner ===== -->
            <div class="profile-banner">
                <img class="avatar-img"
                     src="https://ui-avatars.com/api/?name=Nguyen+An&background=ffffff&color=0568a6&size=128" alt="Avatar">
                <h3>Nguyễn Văn An</h3>
                <span class="role-badge">Nhân viên Kinh doanh (Sales)</span>
            </div>

            <div class="profile-body">

                <%-- Servlet cần: lấy user_id từ session, truy vấn bảng users + addresses/provinces/districts để đổ dữ liệu vào các view-value bên dưới --%>

                <!-- ===== Thông tin công việc (chỉ xem, do Admin quản lý) ===== -->
                <div class="section-header"><h5>Thông tin công việc</h5></div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Email đăng nhập</label>
                        <div class="view-value">nguyenvana@company.com</div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Phòng ban</label>
                        <div class="view-value">Phòng Kinh doanh</div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Vai trò</label>
                        <div class="view-value">Sales</div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Ngày vào làm</label>
                        <div class="view-value">15/03/2023</div>
                    </div>
                </div>

                <!-- ===== Thông tin cá nhân ===== -->
                <div class="section-header">
                    <h5>Thông tin cá nhân</h5>
                    <a href="updateProfile.jsp" class="btn-outline-edit">
                        <i class="fa-solid fa-pen me-1"></i> Sửa thông tin
                    </a>
                </div>
                <div class="row">
                    <div class="col-md-4 field-row">
                        <label>Họ</label>
                        <div class="view-value">Nguyễn</div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Tên đệm</label>
                        <div class="view-value">Văn</div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Tên</label>
                        <div class="view-value">An</div>
                    </div>

                    <div class="col-md-4 field-row">
                        <label>Giới tính</label>
                        <div class="view-value">Nam</div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày sinh</label>
                        <div class="view-value">20/05/1995</div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Số CCCD/CMND</label>
                        <div class="view-value">038095006xxx</div>
                    </div>

                    <div class="col-md-6 field-row">
                        <label>Số điện thoại</label>
                        <div class="view-value">0912 345 678</div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Email cá nhân</label>
                        <div class="view-value">nguyenvana.personal@gmail.com</div>
                    </div>
                </div>

                <!-- ===== Địa chỉ ===== -->
                <div class="section-header"><h5>Địa chỉ</h5></div>
                <div class="row">
                    <div class="col-md-6 field-row">
                        <label>Tỉnh / Thành phố</label>
                        <div class="view-value">Thành phố Hà Nội</div>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Quận / Huyện</label>
                        <div class="view-value">Quận Cầu Giấy</div>
                    </div>
                    <div class="col-12 field-row">
                        <label>Địa chỉ chi tiết</label>
                        <div class="view-value">Số 12, ngõ 34, đường Xuân Thủy</div>
                    </div>
                </div>

            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
