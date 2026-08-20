<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do TechnicalSupportTicketController#showList thiết
    lập trước khi forward tới trang này:
      - ticketList    : List<poscs.model.TechnicalRequest> (đã join enterprise/contract/assignedTechnician)
      - statusSummary : Map<String, Integer> (đếm theo từng trạng thái + số phiếu khẩn cấp, cho dải KPI đầu trang)
      - currentPage, totalPages, totalCount : thông tin phân trang
      - keyword, statusFilter, priorityFilter : giá trị filter hiện tại (để giữ lại lúc submit lại form tìm kiếm)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách phiếu hỗ trợ kỹ thuật - POSCS Portal</title>

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

        .app-shell { display: flex; align-items: flex-start; }
        .sidebar {
            width: 240px; flex-shrink: 0;
            background: #fff; border-right: 1px solid #eef2f6;
            padding: 20px 12px; position: sticky; top: 66px;
            height: calc(100vh - 66px); overflow-y: auto;
            display: flex; flex-direction: column;
            transition: width 0.15s ease;
        }
        .sidebar-link {
            display: flex; align-items: center; gap: 12px;
            padding: 11px 14px; margin-bottom: 2px; border-radius: 10px;
            color: #6b7280; font-weight: 600; font-size: 0.88rem; text-decoration: none;
        }
        .sidebar-link i { width: 18px; text-align: center; color: #9ca3af; flex-shrink: 0; }
        .sidebar-link:hover { background: #f0f9ff; color: var(--primary-dark); }
        .sidebar-link.active { background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; }
        .sidebar-link.active i { color: #fff; }
        .sidebar-toggle {
            display: flex; align-items: center; justify-content: center; width: 32px; height: 32px;
            margin: 0 0 12px; padding: 0; border: none; border-radius: 8px;
            background: none; color: #9ca3af; cursor: pointer;
        }
        .sidebar-toggle i { transition: transform 0.15s ease; }
        .sidebar-toggle:hover { background: #f0f9ff; color: var(--primary-dark); }
        .sidebar.collapsed { width: 68px; }
        .sidebar.collapsed .sidebar-link { justify-content: center; }
        .sidebar.collapsed .sidebar-link span { display: none; }
        .sidebar.collapsed .sidebar-toggle i { transform: rotate(180deg); }
        .sidebar.collapsed:hover { width: 240px; }
        .sidebar.collapsed:hover .sidebar-link { justify-content: flex-start; }
        .sidebar.collapsed:hover .sidebar-link span { display: inline; }
        .sidebar.collapsed:hover .sidebar-toggle i { transform: rotate(0deg); }
        .main-content { flex: 1; min-width: 0; }
        @media (max-width: 900px) { .sidebar { display: none; } /* TODO: drawer thu gọn thay vì ẩn hẳn */ }

        .page-container { max-width: 1280px; margin: 28px auto; padding: 0 24px 60px; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 22px; flex-wrap: wrap; gap: 14px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }

        .btn-add {
            background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; border: none; border-radius: 10px;
            padding: 10px 20px; font-weight: 600; font-size: 0.9rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px; box-shadow: 0 8px 18px rgba(5, 104, 166, 0.3); white-space: nowrap;
        }
        .btn-add:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); color: #fff; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); }

        .status-strip { display: flex; gap: 14px; margin-bottom: 20px; flex-wrap: wrap; }
        .status-chip { flex: 1 1 200px; padding: 14px 18px; display: flex; align-items: center; gap: 12px; }
        .status-chip .dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
        .status-chip .num { font-weight: 700; font-size: 1.15rem; color: #111827; }
        .status-chip .lbl { font-size: 0.78rem; color: #6b7280; }

        .filter-bar { padding: 18px 20px; margin-bottom: 20px; display: flex; flex-wrap: wrap; gap: 14px; align-items: center; }
        .search-input-wrap { position: relative; flex: 1 1 260px; min-width: 200px; }
        .search-input-wrap i { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: #9ca3af; font-size: 0.9rem; }
        .search-input-wrap input { width: 100%; padding: 10px 14px 10px 38px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.88rem; }
        .search-input-wrap input:focus { outline: none; background: #fff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        .filter-bar select { padding: 10px 14px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.88rem; min-width: 160px; }
        .filter-bar select:focus { outline: none; border-color: var(--primary-light); }

        .table-card { overflow: hidden; }
        .custom-table { margin-bottom: 0; }
        .custom-table thead th {
            background: #f8fafc; color: #6b7280; font-size: 0.74rem; text-transform: uppercase; letter-spacing: .3px;
            font-weight: 700; padding: 14px 16px; border-bottom: 1.5px solid #eef2f6; white-space: nowrap;
        }
        .custom-table tbody td { padding: 14px 16px; font-size: 0.86rem; color: #111827; vertical-align: middle; border-bottom: 1px solid #f3f4f6; }
        .custom-table tbody tr:last-child td { border-bottom: none; }
        .custom-table tbody tr:hover { background: #f9fdff; }

        .ticket-code { font-weight: 700; color: var(--primary); font-size: 0.85rem; }
        .ticket-title-link { color: #111827; font-weight: 600; text-decoration: none; }
        .ticket-title-link:hover { color: var(--primary); text-decoration: underline; }
        .contract-link { color: var(--primary); font-weight: 600; text-decoration: none; font-size: 0.83rem; }
        .contract-link:hover { text-decoration: underline; }
        .no-contract { color: #9ca3af; font-size: 0.83rem; font-style: italic; }

        .pill { display: inline-flex; align-items: center; gap: 5px; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; white-space: nowrap; }
        .pill .dot { width: 6px; height: 6px; border-radius: 50%; }
        .priority-urgent { background: #fdecef; color: var(--danger); } .priority-urgent .dot { background: var(--danger); }
        .priority-high { background: #fff4e0; color: var(--warning); } .priority-high .dot { background: var(--warning); }
        .priority-normal { background: #eaf6ff; color: var(--primary); } .priority-normal .dot { background: var(--primary); }
        .priority-low { background: #eef2f6; color: #6b7280; } .priority-low .dot { background: #9ca3af; }

        .status-new { background: #eaf6ff; color: var(--primary); } .status-new .dot { background: var(--primary); }
        .status-progress { background: #fff4e0; color: var(--warning); } .status-progress .dot { background: var(--warning); }
        .status-closed { background: #e8faf3; color: var(--success); } .status-closed .dot { background: var(--success); }

        .action-icons { display: flex; gap: 6px; justify-content: flex-end; }
        .action-icons button {
            width: 32px; height: 32px; border-radius: 8px; border: none; background: #f3f4f6; color: #6b7280; cursor: pointer;
            display: flex; align-items: center; justify-content: center; font-size: 0.82rem; transition: all 0.15s;
        }
        .action-icons .act-view:hover { background: #eaf6ff; color: var(--primary); }
        .action-icons .act-edit:hover { background: #fff4e0; color: var(--warning); }
        .action-icons .act-delete:hover { background: #fdecef; color: var(--danger); }

        .empty-state { text-align: center; padding: 60px 20px; color: #9ca3af; }
        .empty-state i { font-size: 2.4rem; margin-bottom: 12px; color: #d1d5db; }
        .empty-state p { font-size: 0.92rem; }

        .pagination-bar { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-top: 1px solid #f3f4f6; flex-wrap: wrap; gap: 10px; }
        .pagination-info { font-size: 0.83rem; color: #6b7280; }
        .pagination { margin: 0; }
        .page-link { color: var(--primary); border-color: #e5e7eb; font-size: 0.85rem; }
        .page-item.active .page-link { background: var(--primary); border-color: var(--primary); }
        .page-link:hover { background: #eaf6ff; color: var(--primary-dark); }

        .toast-msg {
            position: fixed; top: 24px; right: 24px; z-index: 999; background: #fff; border-left: 4px solid var(--success);
            border-radius: 12px; padding: 14px 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.15);
            display: flex; align-items: center; gap: 12px; font-size: 0.88rem; color: #111827; font-weight: 500;
            transform: translateX(130%); transition: transform 0.35s ease;
        }
        .toast-msg.show { transform: translateX(0); }
        .toast-msg i { color: var(--success); font-size: 1.2rem; }

        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 24px 24px 0; }
        .modal-body { padding: 12px 24px 6px; color: #374151; font-size: 0.92rem; }
        .modal-footer { border-top: none; padding: 18px 24px 24px; }
        .btn-modal-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-danger { background: var(--danger); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .modal-icon-warn { width: 52px; height: 52px; border-radius: 50%; background: #fdecef; color: var(--danger); display: flex; align-items: center; justify-content: center; font-size: 1.3rem; margin-bottom: 4px; }

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
                <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header"><img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" alt="avatar"><div><div class="dd-name"><c:out value="${sessionScope.currentUser.fullName}"/></div><div class="dd-role"><c:out value="${sessionScope.currentUser.role.roleName}"/></div></div></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/viewProfile"><i class="fa-regular fa-id-card me-2"></i>Thông tin cá nhân</a></li>
                    <li><a class="dropdown-item" href="${pageContext.request.contextPath}/changePassword.jsp"><i class="fa-solid fa-key me-2"></i>Đổi mật khẩu</a></li>
                    <li><hr class="dropdown-divider"></li>
                    <li><a class="dropdown-item text-danger" href="${pageContext.request.contextPath}/login?action=logout"><i class="fa-solid fa-arrow-right-from-bracket me-2"></i>Đăng xuất</a></li>
                </ul>
            </div>
        </div>
    </nav>

    <div class="app-shell">
        <aside class="sidebar" id="sidebar">
            <button type="button" class="sidebar-toggle" id="sidebarToggle" aria-label="Thu gọn menu">
                <i class="fa-solid fa-angles-left"></i>
            </button>
            <a href="${pageContext.request.contextPath}/dashboard" class="sidebar-link"><i class="fa-solid fa-house"></i><span>Trang chủ</span></a>
            <a href="${pageContext.request.contextPath}/customer" class="sidebar-link"><i class="fa-solid fa-users"></i><span>Khách hàng</span></a>
            <a href="${pageContext.request.contextPath}/contract" class="sidebar-link"><i class="fa-solid fa-file-contract"></i><span>Hợp đồng</span></a>
            <a href="${pageContext.request.contextPath}/product" class="sidebar-link"><i class="fa-solid fa-box"></i><span>Sản phẩm</span></a>
            <a href="${pageContext.request.contextPath}/ticket" class="sidebar-link active"><i class="fa-solid fa-headset"></i><span>Phiếu hỗ trợ</span></a>
            <c:if test="${sessionScope.currentUser.role.roleName == 'Admin'}"><a href="${pageContext.request.contextPath}/employee" class="sidebar-link"><i class="fa-solid fa-user-tie"></i><span>Nhân viên</span></a></c:if>
        </aside>
        <div class="main-content">

    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>Danh sách phiếu hỗ trợ kỹ thuật</h2>
                <p>Theo dõi và xử lý các yêu cầu hỗ trợ kỹ thuật từ khách hàng</p>
            </div>
            <a href="${pageContext.request.contextPath}/ticket?action=new" class="btn-add"><i class="fa-solid fa-plus"></i> Tạo phiếu hỗ trợ</a>
        </div>

        <!-- ===== Dải trạng thái tổng quan ===== -->
        <div class="status-strip">
            <div class="card-box status-chip"><span class="dot" style="background:var(--primary)"></span><div><div class="num">${statusSummary['Mới tiếp nhận']}</div><div class="lbl">Mới tiếp nhận</div></div></div>
            <div class="card-box status-chip"><span class="dot" style="background:var(--warning)"></span><div><div class="num">${statusSummary['Đang xử lý']}</div><div class="lbl">Đang xử lý</div></div></div>
            <div class="card-box status-chip"><span class="dot" style="background:var(--success)"></span><div><div class="num">${statusSummary['Đã đóng']}</div><div class="lbl">Đã đóng</div></div></div>
            <div class="card-box status-chip"><span class="dot" style="background:var(--danger)"></span><div><div class="num">${statusSummary['Khẩn cấp']}</div><div class="lbl">Mức Khẩn cấp</div></div></div>
        </div>

        <!-- ===== Bộ lọc / tìm kiếm ===== -->
        <form class="filter-bar card-box" method="GET" action="${pageContext.request.contextPath}/ticket" id="filterForm">
            <input type="hidden" name="action" value="list">
            <div class="search-input-wrap">
                <i class="fa-solid fa-magnifying-glass"></i>
                <input type="text" id="searchInput" name="keyword" value="${fn:escapeXml(keyword)}" placeholder="Tìm theo mã phiếu, mô tả, khách hàng...">
            </div>
            <select id="filterStatus" name="status">
                <option value="">Tất cả trạng thái</option>
                <option value="Mới tiếp nhận" ${statusFilter == 'Mới tiếp nhận' ? 'selected' : ''}>Mới tiếp nhận</option>
                <option value="Đang xử lý" ${statusFilter == 'Đang xử lý' ? 'selected' : ''}>Đang xử lý</option>
                <option value="Đã đóng" ${statusFilter == 'Đã đóng' ? 'selected' : ''}>Đã đóng</option>
            </select>
            <select id="filterPriority" name="priority">
                <option value="">Tất cả mức ưu tiên</option>
                <option value="Khẩn cấp" ${priorityFilter == 'Khẩn cấp' ? 'selected' : ''}>Khẩn cấp</option>
                <option value="Cao" ${priorityFilter == 'Cao' ? 'selected' : ''}>Cao</option>
                <option value="Bình thường" ${priorityFilter == 'Bình thường' ? 'selected' : ''}>Bình thường</option>
                <option value="Thấp" ${priorityFilter == 'Thấp' ? 'selected' : ''}>Thấp</option>
            </select>
        </form>

        <!-- ===== Bảng danh sách ===== -->
        <div class="table-card card-box">
            <div class="table-responsive">
                <table class="table custom-table" id="ticketTable">
                    <thead>
                        <tr>
                            <th>Mã phiếu</th>
                            <th>Loại phiếu</th>
                            <th>Khách hàng</th>
                            <th>Hợp đồng liên quan</th>
                            <th>Ưu tiên</th>
                            <th>Trạng thái</th>
                            <th>Người xử lý</th>
                            <th>Ngày tạo</th>
                            <th class="text-end">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="ticketTableBody">
                        <c:forEach var="ticket" items="${ticketList}">
                            <tr>
                                <td class="ticket-code">${fn:escapeXml(ticket.ticketCode)}</td>
                                <td><a href="${pageContext.request.contextPath}/ticket?action=view&id=${ticket.ticketId}" class="ticket-title-link">${fn:escapeXml(ticket.ticketType)}</a></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${ticket.enterprise != null}">${fn:escapeXml(ticket.enterprise.enterpriseName)}</c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${ticket.contract != null}"><a href="${pageContext.request.contextPath}/contract?action=view&id=${ticket.contractId}" class="contract-link">${fn:escapeXml(ticket.contract.contractCode)}</a></c:when>
                                        <c:otherwise><span class="no-contract">Không có</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${ticket.priority == 'Khẩn cấp'}"><span class="pill priority-urgent"><span class="dot"></span>Khẩn cấp</span></c:when>
                                        <c:when test="${ticket.priority == 'Cao'}"><span class="pill priority-high"><span class="dot"></span>Cao</span></c:when>
                                        <c:when test="${ticket.priority == 'Thấp'}"><span class="pill priority-low"><span class="dot"></span>Thấp</span></c:when>
                                        <c:otherwise><span class="pill priority-normal"><span class="dot"></span>Bình thường</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${ticket.status == 'Đang xử lý'}"><span class="pill status-progress"><span class="dot"></span>Đang xử lý</span></c:when>
                                        <c:when test="${ticket.status == 'Đã đóng'}"><span class="pill status-closed"><span class="dot"></span>Đã đóng</span></c:when>
                                        <c:otherwise><span class="pill status-new"><span class="dot"></span>Mới tiếp nhận</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${ticket.assignedTechnician != null}">${fn:escapeXml(ticket.assignedTechnician.fullName)}</c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td><fmt:formatDate value="${ticket.createdDate}" pattern="dd/MM/yyyy"/></td>
                                <td>
                                    <div class="action-icons">
                                        <button class="act-view" title="Xem chi tiết" onclick="location.href='${pageContext.request.contextPath}/ticket?action=view&id=${ticket.ticketId}'"><i class="fa-regular fa-eye"></i></button>
                                        <button class="act-edit" title="Sửa" onclick="location.href='${pageContext.request.contextPath}/ticket?action=edit&id=${ticket.ticketId}'"><i class="fa-solid fa-pen"></i></button>
                                        <button class="act-delete" title="Xóa" onclick="openDeleteModal(${ticket.ticketId}, '${fn:escapeXml(ticket.ticketCode)}')"><i class="fa-solid fa-trash"></i></button>
                                    </div>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>

            <!-- ===== Trạng thái rỗng (BR-14) ===== -->
            <div class="empty-state" id="emptyState" style="${empty ticketList ? 'display:block' : 'display:none'}">
                <i class="fa-regular fa-folder-open"></i>
                <p>Không có phiếu hỗ trợ để hiển thị.</p>
            </div>

            <!-- ===== Phân trang ===== -->
            <div class="pagination-bar">
                <span class="pagination-info" id="paginationInfo">Hiển thị ${fn:length(ticketList)} trong tổng số ${totalCount} phiếu hỗ trợ</span>
                <nav>
                    <ul class="pagination pagination-sm mb-0">
                        <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/ticket?action=list&page=${currentPage - 1}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&priority=${fn:escapeXml(priorityFilter)}">Trước</a></li>
                        <c:forEach begin="1" end="${totalPages}" var="p">
                            <li class="page-item ${p == currentPage ? 'active' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/ticket?action=list&page=${p}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&priority=${fn:escapeXml(priorityFilter)}">${p}</a></li>
                        </c:forEach>
                        <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/ticket?action=list&page=${currentPage + 1}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&priority=${fn:escapeXml(priorityFilter)}">Sau</a></li>
                    </ul>
                </nav>
            </div>
        </div>
    </div>

        </div>
    </div>

    <!-- ===== Modal xác nhận xóa ===== -->
    <div class="modal fade" id="deleteModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header"><div class="modal-icon-warn"><i class="fa-solid fa-triangle-exclamation"></i></div></div>
                <div class="modal-body">
                    <h5 class="mb-2" style="font-weight:700; color:#111827;">Xác nhận xóa phiếu hỗ trợ</h5>
                    Bạn có chắc chắn muốn xóa phiếu hỗ trợ <strong id="deleteTicketCode"></strong>? Hành động này không thể hoàn tác.
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <button type="button" class="btn-modal-danger" id="confirmDeleteBtn">Xóa phiếu</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/ticket" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" id="deleteFormId">
    </form>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));
        var ticketIdToDelete = null;

        function openDeleteModal(ticketId, ticketCode) {
            ticketIdToDelete = ticketId;
            document.getElementById('deleteTicketCode').textContent = ticketCode;
            deleteModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            if (ticketIdToDelete) {
                document.getElementById('deleteFormId').value = ticketIdToDelete;
                document.getElementById('deleteForm').submit();
            }
            deleteModal.hide();
        });

        // Tự động submit lại form lọc khi đổi trạng thái / mức ưu tiên
        document.getElementById('filterStatus').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterPriority').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
    </script>

    <script>
        // ===== Thu gọn / mở rộng sidebar =====
        (function () {
            var sidebar = document.getElementById('sidebar');
            var toggle = document.getElementById('sidebarToggle');
            var STORAGE_KEY = 'poscsSidebarCollapsed';
            if (localStorage.getItem(STORAGE_KEY) === '1') {
                sidebar.classList.add('collapsed');
            }
            toggle.addEventListener('click', function () {
                var collapsed = sidebar.classList.toggle('collapsed');
                localStorage.setItem(STORAGE_KEY, collapsed ? '1' : '0');
            });
        })();
    </script>
</body>
</html>
