<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết hợp đồng - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">

    <style>
        :root {
            --primary-dark: #003c6e; --primary: #0568a6; --primary-light: #0f9edb;
            --primary-lighter: #6fd0ff; --accent: #00c2ff;
            --danger: #e2536b; --success: #2fbf8f; --warning: #f5a623;
        }
        body { font-family: 'Inter', sans-serif; background: #f3f4f6; min-height: 100vh; }

        .topbar { background: #fff; box-shadow: 0 2px 12px rgba(0, 60, 110, 0.08); padding: 14px 28px; display: flex; justify-content: space-between; align-items: center; position: sticky; top: 0; z-index: 50; }
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

        .page-container { max-width: 1080px; margin: 28px auto; padding: 0 20px 60px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 16px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); }

        .detail-header { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; padding: 26px 30px; margin-bottom: 20px; }
        .detail-header .doc-icon {
            width: 56px; height: 56px; border-radius: 14px;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center; font-size: 1.4rem; flex-shrink: 0;
        }
        .detail-header .doc-info { display: flex; gap: 16px; align-items: center; }
        .detail-header h2 { font-weight: 700; color: #111827; font-size: 1.2rem; margin-bottom: 4px; }
        .contract-code { color: var(--primary); font-weight: 700; font-size: 0.82rem; }
        .type-badge { display: inline-block; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; background: #f3f4f6; color: #4b5563; margin-left: 8px; }

        .status-pill { display: inline-flex; align-items: center; gap: 5px; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; white-space: nowrap; margin-left: 8px; }
        .status-pill .dot { width: 6px; height: 6px; border-radius: 50%; }
        .status-soon { background: #fff4e0; color: var(--warning); }
        .status-soon .dot { background: var(--warning); }

        .header-actions { display: flex; gap: 10px; }
        .btn-edit-detail {
            background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; border: none;
            border-radius: 10px; padding: 9px 18px; font-weight: 600; font-size: 0.87rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-edit-detail:hover { color: #fff; background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-delete-detail {
            background: #fff; border: 1.5px solid #e5e7eb; color: #9ca3af;
            border-radius: 10px; padding: 9px 16px; font-weight: 600; font-size: 0.87rem;
            display: inline-flex; align-items: center; gap: 8px; cursor: not-allowed;
        }

        .info-card { padding: 26px 30px 30px; margin-bottom: 20px; }
        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 0 0 18px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row .view-value {
            font-size: 0.95rem; color: #111827; font-weight: 500; min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f9fafb; border-radius: 10px; padding: 8px 14px;
        }
        .field-row .view-value a { color: var(--primary); font-weight: 600; text-decoration: none; }
        .field-row .view-value a:hover { text-decoration: underline; }

        .item-table { width: 100%; }
        .item-table th { font-size: 0.72rem; text-transform: uppercase; color: #9ca3af; font-weight: 700; padding: 8px 10px; border-bottom: 1.5px solid #eef2f6; text-align: left; }
        .item-table td { padding: 12px 10px; font-size: 0.87rem; color: #111827; border-bottom: 1px solid #f3f4f6; }
        .item-table tr:last-child td { border-bottom: none; }
        .item-table td.num { text-align: right; }

        .totals-box { margin-top: 10px; margin-left: auto; max-width: 320px; }
        .totals-row { display: flex; justify-content: space-between; padding: 7px 0; font-size: 0.88rem; color: #374151; }
        .totals-row.grand { border-top: 1.5px solid #eef2f6; margin-top: 6px; padding-top: 12px; font-weight: 700; font-size: 1rem; color: var(--primary-dark); }

        @media (max-width: 768px) { .info-card, .detail-header { padding: 20px; } }
    </style>
</head>
<body>

    <nav class="topbar">
        <div class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</div>
        <div class="topbar-right">
            <div class="dropdown">
                <div class="bell-icon" data-bs-toggle="dropdown" aria-expanded="false"><i class="fa-regular fa-bell"></i><span class="dot"></span></div>
                <ul class="dropdown-menu dropdown-menu-end notif-dropdown">
                    <li class="notif-header">Thông báo <span class="notif-count">3 mới</span></li>
                    <li><a class="dropdown-item notif-item" href="#"><span class="notif-icon"><i class="fa-solid fa-file-contract"></i></span><div><div class="notif-text">Hợp đồng #HD-0231 sắp hết hạn</div><div class="notif-time">10 phút trước</div></div></a></li>
                    <li><a class="dropdown-item notif-item" href="#"><span class="notif-icon"><i class="fa-solid fa-headset"></i></span><div><div class="notif-text">Phiếu hỗ trợ #TK-1042 vừa được giao cho bạn</div><div class="notif-time">1 giờ trước</div></div></a></li>
                    <li><a class="dropdown-item notif-item" href="#"><span class="notif-icon"><i class="fa-solid fa-user-plus"></i></span><div><div class="notif-text">Khách hàng mới được thêm: Viettel Bắc Ninh</div><div class="notif-time">Hôm qua</div></div></a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-center small" href="#">Xem tất cả thông báo</a></li>
                </ul>
            </div>
            <div class="dropdown">
                <img src="https://ui-avatars.com/api/?name=Nguyen+An&background=0568a6&color=fff" class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header"><img src="https://ui-avatars.com/api/?name=Nguyen+An&background=0568a6&color=fff" alt="avatar"><div><div class="dd-name">Nguyễn Văn An</div><div class="dd-role">Sales</div></div></li>
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
        <a href="listcontract.jsp" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <%-- Servlet cần: lấy contract_id từ query param ?id=, truy vấn bảng contracts + contract_items + enterprises. Trạng thái tự tính theo BR-17. Nếu không tồn tại hiển thị MSG-021 --%>

        <!-- ===== Header ===== -->
        <div class="detail-header card-box">
            <div class="doc-info">
                <div class="doc-icon"><i class="fa-solid fa-file-contract"></i></div>
                <div>
                    <span class="contract-code">HD-0231</span>
                    <h2>
                        Cung cấp cáp quang OM4 đợt 2
                        <span class="type-badge">Cung cấp thiết bị</span>
                        <span class="status-pill status-soon"><span class="dot"></span>Sắp hết hạn</span>
                    </h2>
                    <div style="color:#6b7280; font-size:0.85rem;">Khách hàng: <a href="viewcustomerdetail.jsp?id=1" style="color:var(--primary); font-weight:600; text-decoration:none;">VNPT Hà Nội</a></div>
                </div>
            </div>
            <div class="header-actions">
                <a href="updatecontract.jsp?id=231" class="btn-edit-detail"><i class="fa-solid fa-pen"></i> Sửa thông tin</a>
                <button class="btn-delete-detail" disabled title="Chỉ được xóa hợp đồng ở trạng thái nháp/chưa hiệu lực"><i class="fa-solid fa-trash"></i> Xóa</button>
            </div>
        </div>

        <!-- ===== Thông tin chung ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Thông tin chung</h5></div>
            <div class="row">
                <div class="col-md-6 field-row">
                    <label>Khách hàng</label>
                    <div class="view-value"><a href="viewcustomerdetail.jsp?id=1">VNPT Hà Nội</a></div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Người phụ trách</label>
                    <div class="view-value">Nguyễn Văn An</div>
                </div>
                <div class="col-md-4 field-row">
                    <label>Ngày ký</label>
                    <div class="view-value">02/01/2026</div>
                </div>
                <div class="col-md-4 field-row">
                    <label>Ngày hiệu lực</label>
                    <div class="view-value">05/01/2026</div>
                </div>
                <div class="col-md-4 field-row">
                    <label>Ngày kết thúc</label>
                    <div class="view-value">09/08/2026</div>
                </div>
            </div>
        </div>

        <!-- ===== Hạng mục sản phẩm / dịch vụ ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
            <table class="item-table">
                <thead>
                    <tr><th>Tên sản phẩm/dịch vụ</th><th class="text-end">Số lượng</th><th class="text-end">Đơn giá</th><th class="text-end">Thành tiền</th></tr>
                </thead>
                <tbody>
                    <tr>
                        <td>Cáp quang OM4 4FO</td>
                        <td class="num">500</td>
                        <td class="num">1.800.000 đ</td>
                        <td class="num">900.000.000 đ</td>
                    </tr>
                    <tr>
                        <td>Đầu nối quang SC/APC</td>
                        <td class="num">1.000</td>
                        <td class="num">35.000 đ</td>
                        <td class="num">35.000.000 đ</td>
                    </tr>
                </tbody>
            </table>

            <div class="totals-box">
                <div class="totals-row"><span>Tổng tiền hàng</span><span>935.000.000 đ</span></div>
                <div class="totals-row"><span>Thuế GTGT (10%)</span><span>93.500.000 đ</span></div>
                <div class="totals-row grand"><span>Tổng cộng</span><span>1.028.500.000 đ</span></div>
            </div>
        </div>

        <!-- ===== Ghi chú / điều khoản ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Điều khoản & ghi chú</h5></div>
            <div class="field-row" style="margin-bottom:0;">
                <div class="view-value" style="min-height:80px; align-items:flex-start; padding-top:12px;">
                    Bên B giao hàng thành 2 đợt tại kho Cầu Giấy, Hà Nội. Bảo hành thiết bị 24 tháng kể từ ngày nghiệm thu. Thanh toán 50% khi ký hợp đồng, 50% còn lại sau nghiệm thu.
                </div>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
