<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết khách hàng - POSCS Portal</title>

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
        .page-container { max-width: 1080px; margin: 28px auto; padding: 0 20px 60px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 16px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); }

        /* ===== Header khách hàng ===== */
        .detail-header {
            display: flex; justify-content: space-between; align-items: flex-start;
            flex-wrap: wrap; gap: 16px; padding: 26px 30px; margin-bottom: 20px;
        }
        .detail-header .company-icon {
            width: 56px; height: 56px; border-radius: 14px;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center;
            font-size: 1.4rem; flex-shrink: 0;
        }
        .detail-header .company-info { display: flex; gap: 16px; align-items: center; }
        .detail-header h2 { font-weight: 700; color: #111827; font-size: 1.25rem; margin-bottom: 4px; }
        .detail-header .customer-code { color: var(--primary); font-weight: 700; font-size: 0.82rem; }
        .type-badge {
            display: inline-block; padding: 3px 11px; border-radius: 20px;
            font-size: 0.72rem; font-weight: 600; background: #eaf6ff; color: var(--primary-dark);
            margin-left: 8px;
        }
        .header-actions { display: flex; gap: 10px; }
        .btn-edit-detail {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            color: #fff; border: none; border-radius: 10px; padding: 9px 18px;
            font-weight: 600; font-size: 0.87rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px;
            box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-edit-detail:hover { color: #fff; background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-delete-detail {
            background: #fff; border: 1.5px solid #f6c3cd; color: var(--danger);
            border-radius: 10px; padding: 9px 16px; font-weight: 600; font-size: 0.87rem;
            display: inline-flex; align-items: center; gap: 8px; cursor: pointer;
        }
        .btn-delete-detail:hover { background: #fdecef; }

        /* ===== Info section ===== */
        .info-card { padding: 26px 30px 30px; margin-bottom: 20px; }
        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 0 0 18px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row .view-value {
            font-size: 0.95rem; color: #111827; font-weight: 500;
            min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f9fafb; border-radius: 10px; padding: 8px 14px;
        }

        /* ===== Người liên hệ ===== */
        .contact-item {
            display: flex; align-items: center; gap: 14px;
            padding: 14px 16px; border: 1px solid #eef2f6; border-radius: 12px; margin-bottom: 12px;
        }
        .contact-item:last-child { margin-bottom: 0; }
        .contact-avatar {
            width: 44px; height: 44px; border-radius: 50%;
            background: #eaf6ff; color: var(--primary);
            display: flex; align-items: center; justify-content: center; font-size: 1.1rem; flex-shrink: 0;
        }
        .contact-name { font-weight: 600; color: #111827; font-size: 0.9rem; }
        .contact-role { font-size: 0.78rem; color: var(--primary); font-weight: 500; margin-bottom: 2px; }
        .contact-meta { font-size: 0.8rem; color: #6b7280; display: flex; gap: 16px; flex-wrap: wrap; margin-top: 2px; }

        /* ===== Tabs hoạt động gần đây ===== */
        .nav-tabs { border-bottom: 1.5px solid #eef2f6; }
        .nav-tabs .nav-link {
            border: none; color: #6b7280; font-weight: 600; font-size: 0.87rem;
            padding: 10px 4px; margin-right: 24px; border-bottom: 2.5px solid transparent;
        }
        .nav-tabs .nav-link.active { color: var(--primary-dark); border-bottom-color: var(--primary); background: none; }

        .mini-table { width: 100%; margin-top: 16px; }
        .mini-table th {
            font-size: 0.72rem; text-transform: uppercase; color: #9ca3af; font-weight: 700;
            padding: 8px 10px; border-bottom: 1.5px solid #eef2f6; text-align: left;
        }
        .mini-table td { padding: 12px 10px; font-size: 0.86rem; color: #111827; border-bottom: 1px solid #f3f4f6; }
        .mini-table tr:last-child td { border-bottom: none; }
        .mini-table a { color: var(--primary); font-weight: 600; text-decoration: none; }
        .mini-table a:hover { text-decoration: underline; }

        .status-pill { padding: 3px 10px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; }
        .status-active { background: #e8faf3; color: var(--success); }
        .status-pending { background: #fff4e0; color: var(--warning); }
        .status-closed { background: #f3f4f6; color: #6b7280; }

        .empty-mini { text-align: center; color: #9ca3af; font-size: 0.85rem; padding: 30px 10px; }

        /* ===== Modal & toast (dùng lại) ===== */
        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 24px 24px 0; }
        .modal-body { padding: 12px 24px 6px; color: #374151; font-size: 0.92rem; }
        .modal-footer { border-top: none; padding: 18px 24px 24px; }
        .btn-modal-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-danger { background: var(--danger); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .modal-icon-warn { width: 52px; height: 52px; border-radius: 50%; background: #fdecef; color: var(--danger); display: flex; align-items: center; justify-content: center; font-size: 1.3rem; margin-bottom: 4px; }

        .toast-msg {
            position: fixed; top: 24px; right: 24px; z-index: 999;
            background: #fff; border-left: 4px solid var(--success);
            border-radius: 12px; padding: 14px 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.15);
            display: flex; align-items: center; gap: 12px; font-size: 0.88rem; color: #111827; font-weight: 500;
            transform: translateX(130%); transition: transform 0.35s ease;
        }
        .toast-msg.show { transform: translateX(0); }
        .toast-msg i { color: var(--success); font-size: 1.2rem; }

        @media (max-width: 768px) {
            .info-card, .detail-header { padding: 20px; }
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
        <a href="listcustomer.jsp" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <%-- Servlet cần: lấy customer_id từ query param ?id=, truy vấn bảng enterprises + enterprisecontacts + contracts + technicalrequests. Nếu không tồn tại thì hiển thị MSG-021 --%>

        <!-- ===== Header ===== -->
        <div class="detail-header card-box">
            <div class="company-info">
                <div class="company-icon"><i class="fa-solid fa-building"></i></div>
                <div>
                    <span class="customer-code">KH-0001</span>
                    <h2>VNPT Hà Nội <span class="type-badge">Nhà mạng viễn thông</span></h2>
                    <div style="color:#6b7280; font-size:0.85rem;">Khách hàng VIP · Tham gia từ 15/03/2023</div>
                </div>
            </div>
            <div class="header-actions">
                <a href="updatecustomer.jsp?id=1" class="btn-edit-detail"><i class="fa-solid fa-pen"></i> Sửa thông tin</a>
                <button class="btn-delete-detail" onclick="openDeleteModal()"><i class="fa-solid fa-trash"></i> Xóa</button>
            </div>
        </div>

        <!-- ===== Thông tin doanh nghiệp ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Thông tin doanh nghiệp</h5></div>
            <div class="row">
                <div class="col-md-6 field-row">
                    <label>Nhóm khách hàng</label>
                    <div class="view-value">Khách hàng VIP</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Người phụ trách</label>
                    <div class="view-value">Nguyễn Văn An</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Số điện thoại</label>
                    <div class="view-value">024 3822 1234</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Email</label>
                    <div class="view-value">contact@vnpt-hanoi.vn</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Website</label>
                    <div class="view-value">vnpt-hanoi.vn</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Ngày tham gia</label>
                    <div class="view-value">15/03/2023</div>
                </div>
                <div class="col-12 field-row">
                    <label>Địa chỉ</label>
                    <div class="view-value">Số 57 Huỳnh Thúc Kháng, Quận Cầu Giấy, Thành phố Hà Nội</div>
                </div>
            </div>
        </div>

        <!-- ===== Người đại diện / liên hệ ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Người liên hệ</h5></div>

            <div class="contact-item">
                <div class="contact-avatar"><i class="fa-solid fa-user-tie"></i></div>
                <div>
                    <div class="contact-role">Giám đốc kỹ thuật</div>
                    <div class="contact-name">Phạm Thu Hường</div>
                    <div class="contact-meta">
                        <span><i class="fa-solid fa-phone me-1"></i>0983 112 233</span>
                        <span><i class="fa-solid fa-envelope me-1"></i>huong.pt@vnpt-hanoi.vn</span>
                    </div>
                </div>
            </div>
            <div class="contact-item">
                <div class="contact-avatar"><i class="fa-solid fa-user-tie"></i></div>
                <div>
                    <div class="contact-role">Trưởng phòng thu mua</div>
                    <div class="contact-name">Đỗ Anh Quân</div>
                    <div class="contact-meta">
                        <span><i class="fa-solid fa-phone me-1"></i>0977 445 566</span>
                        <span><i class="fa-solid fa-envelope me-1"></i>quan.da@vnpt-hanoi.vn</span>
                    </div>
                </div>
            </div>
        </div>

        <!-- ===== Hoạt động gần đây ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Hoạt động gần đây</h5></div>

            <ul class="nav nav-tabs" id="activityTabs" role="tablist">
                <li class="nav-item" role="presentation">
                    <button class="nav-link active" data-bs-toggle="tab" data-bs-target="#tab-contracts" type="button">Hợp đồng</button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#tab-tickets" type="button">Phiếu hỗ trợ kỹ thuật</button>
                </li>
            </ul>

            <div class="tab-content">
                <div class="tab-pane fade show active" id="tab-contracts">
                    <table class="mini-table">
                        <thead>
                            <tr><th>Mã hợp đồng</th><th>Tên hợp đồng</th><th>Giá trị</th><th>Trạng thái</th><th>Ngày ký</th></tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td><a href="contractDetail.jsp?id=231">HD-0231</a></td>
                                <td>Cung cấp cáp quang OM4 đợt 2</td>
                                <td>1.250.000.000 đ</td>
                                <td><span class="status-pill status-active">Đang hiệu lực</span></td>
                                <td>02/01/2026</td>
                            </tr>
                            <tr>
                                <td><a href="contractDetail.jsp?id=198">HD-0198</a></td>
                                <td>Lắp đặt tủ trạm Smart Shelter</td>
                                <td>860.000.000 đ</td>
                                <td><span class="status-pill status-closed">Đã hoàn tất</span></td>
                                <td>18/09/2025</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
                <div class="tab-pane fade" id="tab-tickets">
                    <table class="mini-table">
                        <thead>
                            <tr><th>Mã phiếu</th><th>Tiêu đề</th><th>Trạng thái</th><th>Ngày tạo</th></tr>
                        </thead>
                        <tbody>
                            <tr>
                                <td><a href="ticketDetail.jsp?id=1042">TK-1042</a></td>
                                <td>Sự cố mất tín hiệu tại trạm Cầu Giấy</td>
                                <td><span class="status-pill status-pending">Đang xử lý</span></td>
                                <td>05/08/2026</td>
                            </tr>
                            <tr>
                                <td><a href="ticketDetail.jsp?id=987">TK-0987</a></td>
                                <td>Yêu cầu bảo trì định kỳ nguồn UPS</td>
                                <td><span class="status-pill status-closed">Đã đóng</span></td>
                                <td>20/06/2026</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

    <!-- ===== Modal xác nhận xóa (MSG-038) ===== -->
    <div class="modal fade" id="deleteModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <div class="modal-icon-warn"><i class="fa-solid fa-triangle-exclamation"></i></div>
                </div>
                <div class="modal-body">
                    <h5 class="mb-2" style="font-weight:700; color:#111827;">Xác nhận xóa khách hàng</h5>
                    Bạn có chắc chắn muốn xóa <strong>VNPT Hà Nội</strong>? Hành động này không thể hoàn tác.
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <button type="button" class="btn-modal-danger" id="confirmDeleteBtn">Xóa khách hàng</button>
                </div>
            </div>
        </div>
    </div>

    <div class="toast-msg" id="toastMsg">
        <i class="fa-solid fa-circle-check"></i>
        <span>Xóa khách hàng thành công.</span>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));

        function openDeleteModal() {
            deleteModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            // TODO: gọi servlet DeleteCustomerServlet, kiểm tra ràng buộc hợp đồng còn hiệu lực (BR-41, MSG-040) trước khi xóa thật
            deleteModal.hide();
            var toast = document.getElementById('toastMsg');
            toast.classList.add('show');
            setTimeout(function () {
                window.location.href = 'listcustomer.jsp';
            }, 1200);
        });
    </script>
</body>
</html>
