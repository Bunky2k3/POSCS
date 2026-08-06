<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tổng quan - POSCS Portal</title>

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

        /* ===== Topbar ===== */
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
        .page-container { max-width: 1280px; margin: 28px auto; padding: 0 24px 60px; }

        .welcome-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
        .welcome-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .welcome-row p { color: #6b7280; font-size: 0.9rem; }
        .today-badge {
            background: #fff; border: 1px solid #eef2f6; border-radius: 10px;
            padding: 8px 16px; font-size: 0.85rem; color: #374151; font-weight: 500;
            box-shadow: 0 4px 12px rgba(0,40,80,0.06);
        }
        .today-badge i { color: var(--primary); margin-right: 6px; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); }

        /* ===== KPI cards ===== */
        .kpi-card { padding: 22px 22px 20px; display: flex; flex-direction: column; gap: 10px; height: 100%; }
        .kpi-top { display: flex; justify-content: space-between; align-items: flex-start; }
        .kpi-icon {
            width: 46px; height: 46px; border-radius: 12px;
            display: flex; align-items: center; justify-content: center; font-size: 1.15rem; color: #fff;
        }
        .kpi-icon.bg-blue { background: linear-gradient(135deg, var(--primary-dark), var(--primary-light)); }
        .kpi-icon.bg-amber { background: linear-gradient(135deg, #c97a0f, var(--warning)); }
        .kpi-icon.bg-green { background: linear-gradient(135deg, #1a8f68, var(--success)); }
        .kpi-icon.bg-red { background: linear-gradient(135deg, #b8384f, var(--danger)); }

        .kpi-label { font-size: 0.8rem; color: #6b7280; font-weight: 500; }
        .kpi-value { font-size: 1.65rem; font-weight: 700; color: #111827; line-height: 1.1; }
        .kpi-trend { font-size: 0.78rem; font-weight: 600; display: inline-flex; align-items: center; gap: 5px; }
        .kpi-trend.up { color: var(--success); }
        .kpi-trend.warn { color: var(--warning); }
        .kpi-trend.down { color: var(--danger); }

        /* ===== Charts ===== */
        .chart-card { padding: 24px 26px 20px; }
        .chart-card .section-title { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin-bottom: 2px; }
        .chart-card .section-sub { font-size: 0.8rem; color: #9ca3af; margin-bottom: 16px; }

        .legend-row { display: flex; gap: 18px; justify-content: center; margin-top: 14px; flex-wrap: wrap; }
        .legend-item { display: flex; align-items: center; gap: 7px; font-size: 0.8rem; color: #6b7280; }
        .legend-dot { width: 9px; height: 9px; border-radius: 50%; }

        /* ===== Tables ===== */
        .table-section { padding: 22px 24px 18px; }
        .table-section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
        .table-section-header h6 { font-weight: 700; color: var(--primary-dark); font-size: 0.92rem; margin: 0; }
        .table-section-header a { font-size: 0.8rem; color: var(--primary); text-decoration: none; font-weight: 600; }
        .table-section-header a:hover { text-decoration: underline; }

        .mini-table { width: 100%; }
        .mini-table th {
            font-size: 0.7rem; text-transform: uppercase; color: #9ca3af; font-weight: 700;
            padding: 8px 8px; border-bottom: 1.5px solid #eef2f6; text-align: left;
        }
        .mini-table td { padding: 11px 8px; font-size: 0.84rem; color: #111827; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
        .mini-table tr:last-child td { border-bottom: none; }
        .mini-table a.link { color: var(--primary); font-weight: 600; text-decoration: none; }
        .mini-table a.link:hover { text-decoration: underline; }

        .status-pill { padding: 3px 10px; border-radius: 20px; font-size: 0.7rem; font-weight: 600; white-space: nowrap; }
        .status-danger { background: #fdecef; color: var(--danger); }
        .status-warn { background: #fff4e0; color: var(--warning); }
        .status-info { background: #eaf6ff; color: var(--primary); }
        .status-success { background: #e8faf3; color: var(--success); }
        .status-gray { background: #f3f4f6; color: #6b7280; }

        @media (max-width: 768px) {
            .page-container { padding: 0 14px 60px; }
        }
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
                    <li><a class="dropdown-item" href="viewProfile.jsp"><i class="fa-regular fa-id-card me-2"></i>Thông tin cá nhân</a></li>
                    <li><a class="dropdown-item" href="changePassword.jsp"><i class="fa-solid fa-key me-2"></i>Đổi mật khẩu</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-danger" href="login.jsp"><i class="fa-solid fa-arrow-right-from-bracket me-2"></i>Đăng xuất</a></li>
                </ul>
            </div>
        </div>
    </nav>

    <div class="page-container">

        <div class="welcome-row">
            <div>
                <h2>Chào mừng trở lại, Nguyễn Văn An 👋</h2>
                <p>Đây là tổng quan hoạt động kinh doanh và hỗ trợ kỹ thuật của bạn</p>
            </div>
            <div class="today-badge"><i class="fa-regular fa-calendar"></i>Thứ Năm, 06/08/2026</div>
        </div>

        <%-- Servlet cần: tổng hợp số liệu từ bảng enterprises, contracts, technicalrequests theo phạm vi phân quyền của người dùng đang đăng nhập --%>

        <!-- ===== KPI cards ===== -->
        <div class="row g-4 mb-4">
            <div class="col-6 col-lg-3">
                <div class="card-box kpi-card">
                    <div class="kpi-top">
                        <div>
                            <div class="kpi-label">Tổng khách hàng</div>
                            <div class="kpi-value">128</div>
                        </div>
                        <div class="kpi-icon bg-blue"><i class="fa-solid fa-building"></i></div>
                    </div>
                    <span class="kpi-trend up"><i class="fa-solid fa-arrow-trend-up"></i> +8 khách hàng mới tháng này</span>
                </div>
            </div>
            <div class="col-6 col-lg-3">
                <div class="card-box kpi-card">
                    <div class="kpi-top">
                        <div>
                            <div class="kpi-label">Hợp đồng đang hiệu lực</div>
                            <div class="kpi-value">47</div>
                        </div>
                        <div class="kpi-icon bg-amber"><i class="fa-solid fa-file-contract"></i></div>
                    </div>
                    <span class="kpi-trend warn"><i class="fa-solid fa-triangle-exclamation"></i> 3 hợp đồng sắp hết hạn</span>
                </div>
            </div>
            <div class="col-6 col-lg-3">
                <div class="card-box kpi-card">
                    <div class="kpi-top">
                        <div>
                            <div class="kpi-label">Doanh thu hợp đồng (tháng 8)</div>
                            <div class="kpi-value">4,2 tỷ đ</div>
                        </div>
                        <div class="kpi-icon bg-green"><i class="fa-solid fa-sack-dollar"></i></div>
                    </div>
                    <span class="kpi-trend up"><i class="fa-solid fa-arrow-trend-up"></i> +15% so với tháng trước</span>
                </div>
            </div>
            <div class="col-6 col-lg-3">
                <div class="card-box kpi-card">
                    <div class="kpi-top">
                        <div>
                            <div class="kpi-label">Phiếu hỗ trợ đang xử lý</div>
                            <div class="kpi-value">9</div>
                        </div>
                        <div class="kpi-icon bg-red"><i class="fa-solid fa-headset"></i></div>
                    </div>
                    <span class="kpi-trend down"><i class="fa-solid fa-clock"></i> 2 phiếu sắp trễ hạn xử lý</span>
                </div>
            </div>
        </div>

        <!-- ===== Biểu đồ ===== -->
        <div class="row g-4 mb-4">
            <div class="col-lg-8">
                <div class="card-box chart-card">
                    <div class="section-title">Doanh thu hợp đồng theo tháng</div>
                    <div class="section-sub">6 tháng gần nhất (đơn vị: tỷ đồng)</div>
                    <canvas id="revenueChart" height="110"></canvas>
                </div>
            </div>
            <div class="col-lg-4">
                <div class="card-box chart-card">
                    <div class="section-title">Phiếu hỗ trợ theo trạng thái</div>
                    <div class="section-sub">Tổng số 46 phiếu trong tháng</div>
                    <canvas id="ticketChart" height="200"></canvas>
                    <div class="legend-row">
                        <div class="legend-item"><span class="legend-dot" style="background:var(--primary-light)"></span>Mới (5)</div>
                        <div class="legend-item"><span class="legend-dot" style="background:var(--warning)"></span>Đang xử lý (9)</div>
                        <div class="legend-item"><span class="legend-dot" style="background:var(--success)"></span>Đã đóng (32)</div>
                    </div>
                </div>
            </div>
        </div>

        <!-- ===== Bảng hành động ===== -->
        <div class="row g-4">
            <div class="col-lg-6">
                <div class="card-box table-section">
                    <div class="table-section-header">
                        <h6>Hợp đồng sắp hết hạn</h6>
                        <a href="contractList.jsp">Xem tất cả</a>
                    </div>
                    <table class="mini-table">
                        <thead><tr><th>Mã HĐ</th><th>Khách hàng</th><th>Giá trị</th><th>Còn lại</th></tr></thead>
                        <tbody>
                            <tr>
                                <td><a href="contractDetail.jsp?id=231" class="link">HD-0231</a></td>
                                <td>VNPT Hà Nội</td>
                                <td>1,25 tỷ đ</td>
                                <td><span class="status-pill status-danger">3 ngày</span></td>
                            </tr>
                            <tr>
                                <td><a href="contractDetail.jsp?id=214" class="link">HD-0214</a></td>
                                <td>Viettel Bắc Ninh</td>
                                <td>680 triệu đ</td>
                                <td><span class="status-pill status-warn">9 ngày</span></td>
                            </tr>
                            <tr>
                                <td><a href="contractDetail.jsp?id=205" class="link">HD-0205</a></td>
                                <td>FPT Telecom Đà Nẵng</td>
                                <td>430 triệu đ</td>
                                <td><span class="status-pill status-warn">14 ngày</span></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>

            <div class="col-lg-6">
                <div class="card-box table-section">
                    <div class="table-section-header">
                        <h6>Phiếu hỗ trợ cần xử lý</h6>
                        <a href="ticketList.jsp">Xem tất cả</a>
                    </div>
                    <table class="mini-table">
                        <thead><tr><th>Mã phiếu</th><th>Khách hàng</th><th>Ưu tiên</th><th>Trạng thái</th></tr></thead>
                        <tbody>
                            <tr>
                                <td><a href="ticketDetail.jsp?id=1042" class="link">TK-1042</a></td>
                                <td>VNPT Hà Nội</td>
                                <td><span class="status-pill status-danger">Khẩn cấp</span></td>
                                <td><span class="status-pill status-warn">Đang xử lý</span></td>
                            </tr>
                            <tr>
                                <td><a href="ticketDetail.jsp?id=1039" class="link">TK-1039</a></td>
                                <td>CMC Telecom</td>
                                <td><span class="status-pill status-warn">Cao</span></td>
                                <td><span class="status-pill status-info">Mới</span></td>
                            </tr>
                            <tr>
                                <td><a href="ticketDetail.jsp?id=1035" class="link">TK-1035</a></td>
                                <td>MobiFone Hải Phòng</td>
                                <td><span class="status-pill status-gray">Bình thường</span></td>
                                <td><span class="status-pill status-warn">Đang xử lý</span></td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script>
        // ===== Biểu đồ doanh thu hợp đồng theo tháng =====
        var revenueCtx = document.getElementById('revenueChart').getContext('2d');
        var revenueGradient = revenueCtx.createLinearGradient(0, 0, 0, 260);
        revenueGradient.addColorStop(0, 'rgba(5, 104, 166, 0.85)');
        revenueGradient.addColorStop(1, 'rgba(15, 158, 219, 0.55)');

        new Chart(revenueCtx, {
            type: 'bar',
            data: {
                labels: ['Th.3', 'Th.4', 'Th.5', 'Th.6', 'Th.7', 'Th.8'],
                datasets: [{
                    label: 'Doanh thu (tỷ đồng)',
                    data: [2.8, 3.1, 2.5, 3.6, 3.9, 4.2],
                    backgroundColor: revenueGradient,
                    borderRadius: 8,
                    maxBarThickness: 42
                }]
            },
            options: {
                responsive: true,
                plugins: { legend: { display: false } },
                scales: {
                    y: { beginAtZero: true, grid: { color: '#f0f2f5' }, ticks: { color: '#9ca3af', font: { size: 11 } } },
                    x: { grid: { display: false }, ticks: { color: '#6b7280', font: { size: 11 } } }
                }
            }
        });

        // ===== Biểu đồ phiếu hỗ trợ theo trạng thái =====
        var ticketCtx = document.getElementById('ticketChart').getContext('2d');
        new Chart(ticketCtx, {
            type: 'doughnut',
            data: {
                labels: ['Mới', 'Đang xử lý', 'Đã đóng'],
                datasets: [{
                    data: [5, 9, 32],
                    backgroundColor: ['#0f9edb', '#f5a623', '#2fbf8f'],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                cutout: '68%',
                plugins: { legend: { display: false } }
            }
        });
    </script>
</body>
</html>
