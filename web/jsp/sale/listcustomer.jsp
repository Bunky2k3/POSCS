<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách khách hàng - POSCS Portal</title>

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

        /* ===== Topbar (đồng bộ toàn hệ thống) ===== */
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
        .dd-user-header img { width: 42px; height: 42px; border-radius: 50%; object-fit: cover; }
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

        /* ===== Layout ===== */
        .page-container {
            max-width: 1240px;
            margin: 28px auto;
            padding: 0 24px 60px;
        }

        .page-header-row {
            display: flex; justify-content: space-between; align-items: flex-start;
            margin-bottom: 22px; flex-wrap: wrap; gap: 14px;
        }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }

        .btn-add {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            color: #fff; border: none; border-radius: 10px;
            padding: 10px 20px; font-weight: 600; font-size: 0.9rem;
            text-decoration: none; display: inline-flex; align-items: center; gap: 8px;
            box-shadow: 0 8px 18px rgba(5, 104, 166, 0.3);
            white-space: nowrap;
        }
        .btn-add:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); color: #fff; }

        .card-box {
            background: #fff; border-radius: 16px;
            box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08);
        }

        /* ===== Filter bar ===== */
        .filter-bar {
            padding: 18px 20px; margin-bottom: 20px;
            display: flex; flex-wrap: wrap; gap: 14px; align-items: center;
        }
        .search-input-wrap {
            position: relative; flex: 1 1 280px; min-width: 220px;
        }
        .search-input-wrap i {
            position: absolute; left: 14px; top: 50%; transform: translateY(-50%);
            color: #9ca3af; font-size: 0.9rem;
        }
        .search-input-wrap input {
            width: 100%; padding: 10px 14px 10px 38px;
            border-radius: 10px; border: 1px solid #e5e7eb;
            background: #f9fafb; font-size: 0.88rem;
        }
        .search-input-wrap input:focus {
            outline: none; background: #fff; border-color: var(--primary-light);
            box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15);
        }
        .filter-bar select {
            padding: 10px 14px; border-radius: 10px; border: 1px solid #e5e7eb;
            background: #f9fafb; font-size: 0.88rem; min-width: 180px;
        }
        .filter-bar select:focus { outline: none; border-color: var(--primary-light); }

        /* ===== Table ===== */
        .table-card { overflow: hidden; }
        .custom-table { margin-bottom: 0; }
        .custom-table thead th {
            background: #f8fafc; color: #6b7280; font-size: 0.74rem;
            text-transform: uppercase; letter-spacing: .3px; font-weight: 700;
            padding: 14px 18px; border-bottom: 1.5px solid #eef2f6; white-space: nowrap;
        }
        .custom-table tbody td {
            padding: 14px 18px; font-size: 0.87rem; color: #111827;
            vertical-align: middle; border-bottom: 1px solid #f3f4f6;
        }
        .custom-table tbody tr:last-child td { border-bottom: none; }
        .custom-table tbody tr:hover { background: #f9fdff; }

        .customer-code {
            font-weight: 700; color: var(--primary); font-size: 0.85rem;
        }
        .customer-name-link {
            color: #111827; font-weight: 600; text-decoration: none;
        }
        .customer-name-link:hover { color: var(--primary); text-decoration: underline; }

        .type-badge {
            display: inline-block; padding: 3px 11px; border-radius: 20px;
            font-size: 0.72rem; font-weight: 600;
            background: #eaf6ff; color: var(--primary-dark);
        }

        .action-icons { display: flex; gap: 6px; justify-content: flex-end; }
        .action-icons button {
            width: 32px; height: 32px; border-radius: 8px; border: none;
            background: #f3f4f6; color: #6b7280; cursor: pointer;
            display: flex; align-items: center; justify-content: center;
            font-size: 0.82rem; transition: all 0.15s;
        }
        .action-icons .act-view:hover { background: #eaf6ff; color: var(--primary); }
        .action-icons .act-edit:hover { background: #fff4e0; color: var(--warning); }
        .action-icons .act-delete:hover { background: #fdecef; color: var(--danger); }

        .empty-state {
            text-align: center; padding: 60px 20px; color: #9ca3af;
        }
        .empty-state i { font-size: 2.4rem; margin-bottom: 12px; color: #d1d5db; }
        .empty-state p { font-size: 0.92rem; }

        /* ===== Pagination bar ===== */
        .pagination-bar {
            display: flex; justify-content: space-between; align-items: center;
            padding: 16px 20px; border-top: 1px solid #f3f4f6; flex-wrap: wrap; gap: 10px;
        }
        .pagination-info { font-size: 0.83rem; color: #6b7280; }
        .pagination { margin: 0; }
        .page-link {
            color: var(--primary); border-color: #e5e7eb; font-size: 0.85rem;
        }
        .page-item.active .page-link {
            background: var(--primary); border-color: var(--primary);
        }
        .page-link:hover { background: #eaf6ff; color: var(--primary-dark); }

        /* ===== Toast ===== */
        .toast-msg {
            position: fixed; top: 24px; right: 24px; z-index: 999;
            background: #fff; border-left: 4px solid var(--success);
            border-radius: 12px; padding: 14px 20px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.15);
            display: flex; align-items: center; gap: 12px;
            font-size: 0.88rem; color: #111827; font-weight: 500;
            transform: translateX(130%); transition: transform 0.35s ease;
        }
        .toast-msg.show { transform: translateX(0); }
        .toast-msg.blocked { border-left-color: var(--danger); }
        .toast-msg i { color: var(--success); font-size: 1.2rem; }
        .toast-msg.blocked i { color: var(--danger); }

        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 24px 24px 0; }
        .modal-body { padding: 12px 24px 6px; color: #374151; font-size: 0.92rem; }
        .modal-footer { border-top: none; padding: 18px 24px 24px; }
        .btn-modal-cancel {
            background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280;
            border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem;
        }
        .btn-modal-danger {
            background: var(--danger); border: none; color: #fff;
            border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem;
        }
        .modal-icon-warn {
            width: 52px; height: 52px; border-radius: 50%;
            background: #fdecef; color: var(--danger);
            display: flex; align-items: center; justify-content: center;
            font-size: 1.3rem; margin-bottom: 4px;
        }

        @media (max-width: 768px) {
            .page-container { padding: 0 14px 60px; }
            .custom-table { font-size: 0.8rem; }
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

    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>Danh sách khách hàng</h2>
                <p>Quản lý thông tin khách hàng doanh nghiệp đối tác</p>
            </div>
            <a href="addnewcustomer.jsp" class="btn-add"><i class="fa-solid fa-plus"></i> Thêm khách hàng</a>
        </div>

        <!-- ===== Bộ lọc / tìm kiếm ===== -->
        <div class="filter-bar card-box">
            <div class="search-input-wrap">
                <i class="fa-solid fa-magnifying-glass"></i>
                <input type="text" id="searchInput" placeholder="Tìm theo mã KH, tên, số điện thoại...">
            </div>
            <select id="filterType">
                <option value="">Tất cả loại khách hàng</option>
                <option value="Nhà mạng viễn thông">Nhà mạng viễn thông</option>
                <option value="Nhà thầu thi công">Nhà thầu thi công</option>
                <option value="Đại lý phân phối">Đại lý phân phối</option>
            </select>
            <select id="filterAssignee">
                <option value="">Tất cả người phụ trách</option>
                <option value="Nguyễn Văn An">Nguyễn Văn An</option>
                <option value="Trần Thị Bình">Trần Thị Bình</option>
                <option value="Lê Minh Châu">Lê Minh Châu</option>
                <option value="Phạm Quốc Huy">Phạm Quốc Huy</option>
            </select>
        </div>

        <%-- Servlet cần: truy vấn bảng enterprises (JOIN addresses, users làm người phụ trách), hỗ trợ phân trang (BR-12), tìm kiếm/lọc theo query param --%>

        <!-- ===== Bảng danh sách ===== -->
        <div class="table-card card-box">
            <div class="table-responsive">
                <table class="table custom-table" id="customerTable">
                    <thead>
                        <tr>
                            <th>Mã KH</th>
                            <th>Tên khách hàng</th>
                            <th>Loại KH</th>
                            <th>Email</th>
                            <th>Số điện thoại</th>
                            <th>Địa chỉ</th>
                            <th>Người phụ trách</th>
                            <th class="text-end">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="customerTableBody">
                        <tr data-type="Nhà mạng viễn thông" data-assignee="Nguyễn Văn An">
                            <td class="customer-code">KH-0001</td>
                            <td><a href="viewcustomerdetail.jsp?id=1" class="customer-name-link">VNPT Hà Nội</a></td>
                            <td><span class="type-badge">Nhà mạng viễn thông</span></td>
                            <td>contact@vnpt-hanoi.vn</td>
                            <td>024 3822 1234</td>
                            <td>Hà Nội</td>
                            <td>Nguyễn Văn An</td>
                            <td>
                                <div class="action-icons">
                                    <button class="act-view" title="Xem chi tiết" onclick="location.href='viewcustomerdetail.jsp?id=1'"><i class="fa-regular fa-eye"></i></button>
                                    <button class="act-edit" title="Sửa" onclick="location.href='updatecustomer.jsp?id=1'"><i class="fa-solid fa-pen"></i></button>
                                    <button class="act-delete" title="Xóa" onclick="openDeleteModal(this)"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </td>
                        </tr>
                        <tr data-type="Nhà mạng viễn thông" data-assignee="Trần Thị Bình">
                            <td class="customer-code">KH-0002</td>
                            <td><a href="viewcustomerdetail.jsp?id=2" class="customer-name-link">Viettel Bắc Ninh</a></td>
                            <td><span class="type-badge">Nhà mạng viễn thông</span></td>
                            <td>lienhe@viettel-bacninh.vn</td>
                            <td>0222 384 5678</td>
                            <td>Bắc Ninh</td>
                            <td>Trần Thị Bình</td>
                            <td>
                                <div class="action-icons">
                                    <button class="act-view" title="Xem chi tiết" onclick="location.href='viewcustomerdetail.jsp?id=2'"><i class="fa-regular fa-eye"></i></button>
                                    <button class="act-edit" title="Sửa" onclick="location.href='updatecustomer.jsp?id=2'"><i class="fa-solid fa-pen"></i></button>
                                    <button class="act-delete" title="Xóa" onclick="openDeleteModal(this)"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </td>
                        </tr>
                        <tr data-type="Nhà mạng viễn thông" data-assignee="Nguyễn Văn An">
                            <td class="customer-code">KH-0003</td>
                            <td><a href="viewcustomerdetail.jsp?id=3" class="customer-name-link">MobiFone Hải Phòng</a></td>
                            <td><span class="type-badge">Nhà mạng viễn thông</span></td>
                            <td>cskh@mobifone-hp.vn</td>
                            <td>0225 385 9012</td>
                            <td>Hải Phòng</td>
                            <td>Nguyễn Văn An</td>
                            <td>
                                <div class="action-icons">
                                    <button class="act-view" title="Xem chi tiết" onclick="location.href='viewcustomerdetail.jsp?id=3'"><i class="fa-regular fa-eye"></i></button>
                                    <button class="act-edit" title="Sửa" onclick="location.href='updatecustomer.jsp?id=3'"><i class="fa-solid fa-pen"></i></button>
                                    <button class="act-delete" title="Xóa" onclick="openDeleteModal(this)"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </td>
                        </tr>
                        <tr data-type="Nhà thầu thi công" data-assignee="Lê Minh Châu">
                            <td class="customer-code">KH-0004</td>
                            <td><a href="viewcustomerdetail.jsp?id=4" class="customer-name-link">Cty TNHH Xây lắp Điện Nam Hà</a></td>
                            <td><span class="type-badge">Nhà thầu thi công</span></td>
                            <td>info@namha-const.vn</td>
                            <td>0228 123 4567</td>
                            <td>Nam Định</td>
                            <td>Lê Minh Châu</td>
                            <td>
                                <div class="action-icons">
                                    <button class="act-view" title="Xem chi tiết" onclick="location.href='viewcustomerdetail.jsp?id=4'"><i class="fa-regular fa-eye"></i></button>
                                    <button class="act-edit" title="Sửa" onclick="location.href='updatecustomer.jsp?id=4'"><i class="fa-solid fa-pen"></i></button>
                                    <button class="act-delete" title="Xóa" onclick="openDeleteModal(this)"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </td>
                        </tr>
                        <tr data-type="Đại lý phân phối" data-assignee="Trần Thị Bình">
                            <td class="customer-code">KH-0005</td>
                            <td><a href="viewcustomerdetail.jsp?id=5" class="customer-name-link">Đại lý Thiết bị Viễn thông Đông Á</a></td>
                            <td><span class="type-badge">Đại lý phân phối</span></td>
                            <td>sales@dongatelecom.vn</td>
                            <td>024 6655 7788</td>
                            <td>Hà Nội</td>
                            <td>Trần Thị Bình</td>
                            <td>
                                <div class="action-icons">
                                    <button class="act-view" title="Xem chi tiết" onclick="location.href='viewcustomerdetail.jsp?id=5'"><i class="fa-regular fa-eye"></i></button>
                                    <button class="act-edit" title="Sửa" onclick="location.href='updatecustomer.jsp?id=5'"><i class="fa-solid fa-pen"></i></button>
                                    <button class="act-delete" title="Xóa" onclick="openDeleteModal(this)"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </td>
                        </tr>
                        <tr data-type="Nhà mạng viễn thông" data-assignee="Phạm Quốc Huy">
                            <td class="customer-code">KH-0006</td>
                            <td><a href="viewcustomerdetail.jsp?id=6" class="customer-name-link">FPT Telecom Đà Nẵng</a></td>
                            <td><span class="type-badge">Nhà mạng viễn thông</span></td>
                            <td>support@fpt-dn.vn</td>
                            <td>0236 123 9988</td>
                            <td>Đà Nẵng</td>
                            <td>Phạm Quốc Huy</td>
                            <td>
                                <div class="action-icons">
                                    <button class="act-view" title="Xem chi tiết" onclick="location.href='viewcustomerdetail.jsp?id=6'"><i class="fa-regular fa-eye"></i></button>
                                    <button class="act-edit" title="Sửa" onclick="location.href='updatecustomer.jsp?id=6'"><i class="fa-solid fa-pen"></i></button>
                                    <button class="act-delete" title="Xóa" onclick="openDeleteModal(this)"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </td>
                        </tr>
                        <tr data-type="Nhà mạng viễn thông" data-assignee="Lê Minh Châu">
                            <td class="customer-code">KH-0007</td>
                            <td><a href="viewcustomerdetail.jsp?id=7" class="customer-name-link">CMC Telecom</a></td>
                            <td><span class="type-badge">Nhà mạng viễn thông</span></td>
                            <td>contact@cmctelecom.vn</td>
                            <td>024 7300 8899</td>
                            <td>Hà Nội</td>
                            <td>Lê Minh Châu</td>
                            <td>
                                <div class="action-icons">
                                    <button class="act-view" title="Xem chi tiết" onclick="location.href='viewcustomerdetail.jsp?id=7'"><i class="fa-regular fa-eye"></i></button>
                                    <button class="act-edit" title="Sửa" onclick="location.href='updatecustomer.jsp?id=7'"><i class="fa-solid fa-pen"></i></button>
                                    <button class="act-delete" title="Xóa" onclick="openDeleteModal(this)"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </td>
                        </tr>
                        <tr data-type="Nhà thầu thi công" data-assignee="Phạm Quốc Huy">
                            <td class="customer-code">KH-0008</td>
                            <td><a href="viewcustomerdetail.jsp?id=8" class="customer-name-link">Cty Xây dựng Hạ tầng Miền Trung</a></td>
                            <td><span class="type-badge">Nhà thầu thi công</span></td>
                            <td>info@hatangmt.vn</td>
                            <td>0234 987 6543</td>
                            <td>Huế</td>
                            <td>Phạm Quốc Huy</td>
                            <td>
                                <div class="action-icons">
                                    <button class="act-view" title="Xem chi tiết" onclick="location.href='viewcustomerdetail.jsp?id=8'"><i class="fa-regular fa-eye"></i></button>
                                    <button class="act-edit" title="Sửa" onclick="location.href='updatecustomer.jsp?id=8'"><i class="fa-solid fa-pen"></i></button>
                                    <button class="act-delete" title="Xóa" onclick="openDeleteModal(this)"><i class="fa-solid fa-trash"></i></button>
                                </div>
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>

            <!-- ===== Trạng thái rỗng (MSG-017) ===== -->
            <div class="empty-state" id="emptyState" style="display:none">
                <i class="fa-regular fa-folder-open"></i>
                <p>Không có khách hàng để hiển thị.</p>
            </div>

            <!-- ===== Phân trang (BR-12) ===== -->
            <div class="pagination-bar">
                <span class="pagination-info" id="paginationInfo">Hiển thị 1–8 trong tổng số 8 khách hàng</span>
                <nav>
                    <ul class="pagination pagination-sm mb-0">
                        <li class="page-item disabled"><a class="page-link" href="#">Trước</a></li>
                        <li class="page-item active"><a class="page-link" href="#">1</a></li>
                        <li class="page-item disabled"><a class="page-link" href="#">Sau</a></li>
                    </ul>
                </nav>
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
                    Bạn có chắc chắn muốn xóa khách hàng này? Hành động này không thể hoàn tác.
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
        <span id="toastMsgText">Xóa khách hàng thành công.</span>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));
        var rowToDelete = null;

        function openDeleteModal(btn) {
            rowToDelete = btn.closest('tr');
            deleteModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            // TODO: gọi servlet DeleteCustomerServlet, kiểm tra ràng buộc hợp đồng còn hiệu lực (BR-41) trước khi xóa thật
            if (rowToDelete) {
                rowToDelete.remove();
                rowToDelete = null;
                updateRowCount();
                showToast('Xóa khách hàng thành công.', false);
            }
            deleteModal.hide();
        });

        function showToast(message, blocked) {
            var toast = document.getElementById('toastMsg');
            var toastText = document.getElementById('toastMsgText');
            toastText.textContent = message;
            toast.classList.toggle('blocked', !!blocked);
            toast.querySelector('i').className = blocked ? 'fa-solid fa-circle-xmark' : 'fa-solid fa-circle-check';
            toast.classList.add('show');
            setTimeout(function () { toast.classList.remove('show'); }, 3000);
        }

        function updateRowCount() {
            var rows = document.querySelectorAll('#customerTableBody tr:not([style*="display: none"])');
            var total = rows.length;
            document.getElementById('paginationInfo').textContent =
                total > 0 ? 'Hiển thị 1–' + total + ' trong tổng số ' + total + ' khách hàng' : '';
            document.getElementById('emptyState').style.display = total === 0 ? 'block' : 'none';
        }

        // ===== Tìm kiếm & lọc phía client (demo giao diện) =====
        function applyFilters() {
            var keyword = document.getElementById('searchInput').value.trim().toLowerCase();
            var type = document.getElementById('filterType').value;
            var assignee = document.getElementById('filterAssignee').value;
            var rows = document.querySelectorAll('#customerTableBody tr');
            var visibleCount = 0;

            rows.forEach(function (row) {
                var text = row.textContent.toLowerCase();
                var matchesKeyword = !keyword || text.indexOf(keyword) !== -1;
                var matchesType = !type || row.getAttribute('data-type') === type;
                var matchesAssignee = !assignee || row.getAttribute('data-assignee') === assignee;
                var visible = matchesKeyword && matchesType && matchesAssignee;
                row.style.display = visible ? '' : 'none';
                if (visible) visibleCount++;
            });

            document.getElementById('paginationInfo').textContent =
                visibleCount > 0 ? 'Hiển thị 1–' + visibleCount + ' trong tổng số ' + visibleCount + ' khách hàng' : '';
            document.getElementById('emptyState').style.display = visibleCount === 0 ? 'block' : 'none';
        }

        document.getElementById('searchInput').addEventListener('input', applyFilters);
        document.getElementById('filterType').addEventListener('change', applyFilters);
        document.getElementById('filterAssignee').addEventListener('change', applyFilters);
    </script>
</body>
</html>
