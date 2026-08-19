<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần lấy product_id từ query param ?id=, truy vấn bảng products
    (JOIN productcategories) + contractproducts (JOIN contracts) để liệt kê
    các hợp đồng có sử dụng sản phẩm này. Nếu không tồn tại thì hiển thị lỗi
    (redirect hoặc forward sang trang lỗi).

    Request attribute cần có:
      - product      : poscs.model.Product (đã join .category)
      - contractList : List<poscs.model.Contract> (hợp đồng có dùng sản phẩm này, qua contractproducts)
      - csrfToken
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết sản phẩm - POSCS Portal</title>

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
        .topbar-left { display: flex; align-items: center; gap: 32px; }
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

        .page-container { max-width: 1080px; margin: 28px auto; padding: 0 20px 60px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 16px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); }

        /* ===== Header sản phẩm ===== */
        .detail-header {
            display: flex; justify-content: space-between; align-items: flex-start;
            flex-wrap: wrap; gap: 16px; padding: 26px 30px; margin-bottom: 20px;
        }
        .detail-header .product-info { display: flex; gap: 16px; align-items: center; }
        .detail-header .product-thumb {
            width: 64px; height: 64px; border-radius: 14px; object-fit: cover;
            background: #f3f4f6; border: 1px solid #eef2f6; flex-shrink: 0;
        }
        .detail-header .product-thumb-placeholder {
            width: 64px; height: 64px; border-radius: 14px;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center;
            font-size: 1.5rem; flex-shrink: 0;
        }
        .detail-header h2 { font-weight: 700; color: #111827; font-size: 1.25rem; margin-bottom: 4px; }
        .detail-header .product-code { color: var(--primary); font-weight: 700; font-size: 0.82rem; }
        .category-badge {
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
        .field-row .view-value.multiline { align-items: flex-start; white-space: pre-line; line-height: 1.5; }
        .field-row .view-value a { color: var(--primary); font-weight: 600; text-decoration: none; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        .field-row .view-value a:hover { text-decoration: underline; }

        .product-image-box {
            width: 100%; max-width: 260px; height: 170px; border-radius: 12px;
            border: 1px solid #eef2f6; background: #f9fafb; overflow: hidden;
            display: flex; align-items: center; justify-content: center; color: #9ca3af; font-size: 1.8rem;
        }
        .product-image-box img { width: 100%; height: 100%; object-fit: cover; }

        /* ===== Bảng hợp đồng sử dụng ===== */
        .mini-table { width: 100%; margin-top: 4px; }
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
        .status-soon { background: #fff4e0; color: var(--warning); }
        .status-expired { background: #fdecef; color: var(--danger); }

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
        .toast-msg.blocked { border-left-color: var(--danger); }
        .toast-msg.blocked i { color: var(--danger); }

        @media (max-width: 768px) {
            .info-card, .detail-header { padding: 20px; }
        }
    </style>
</head>
<body>

    <nav class="topbar">
        <div class="topbar-left">
            <div class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</div>
        </div>
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
                <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff"
                     class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header">
                        <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" alt="avatar">
                        <div><div class="dd-name"><c:out value="${sessionScope.currentUser.fullName}"/></div><div class="dd-role"><c:out value="${sessionScope.currentUser.role.roleName}"/></div></div>
                    </li>
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
            <a href="${pageContext.request.contextPath}/product" class="sidebar-link active"><i class="fa-solid fa-box"></i><span>Sản phẩm</span></a>
            <a href="${pageContext.request.contextPath}/ticket" class="sidebar-link"><i class="fa-solid fa-headset"></i><span>Phiếu hỗ trợ</span></a>
        </aside>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/product" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <!-- ===== Header ===== -->
        <div class="detail-header card-box">
            <div class="product-info">
                <c:choose>
                    <c:when test="${not empty product.imageUrl}">
                        <img src="${fn:escapeXml(product.imageUrl)}" class="product-thumb" alt="${fn:escapeXml(product.productName)}">
                    </c:when>
                    <c:otherwise>
                        <div class="product-thumb-placeholder"><i class="fa-solid fa-box"></i></div>
                    </c:otherwise>
                </c:choose>
                <div>
                    <span class="product-code">${fn:escapeXml(product.productCode)}</span>
                    <h2>
                        ${fn:escapeXml(product.productName)}
                        <c:if test="${product.category != null}">
                            <span class="category-badge">${fn:escapeXml(product.category.categoryName)}</span>
                        </c:if>
                    </h2>
                    <c:if test="${product.updatedAt != null}">
                        <div style="color:#6b7280; font-size:0.85rem;">Cập nhật lần cuối: <fmt:formatDate value="${product.updatedAt}" pattern="dd/MM/yyyy HH:mm"/></div>
                    </c:if>
                </div>
            </div>
            <div class="header-actions">
                <a href="${pageContext.request.contextPath}/product?action=edit&id=${product.productId}" class="btn-edit-detail"><i class="fa-solid fa-pen"></i> Sửa thông tin</a>
                <button class="btn-delete-detail" onclick="openDeleteModal()"><i class="fa-solid fa-trash"></i> Xóa</button>
            </div>
        </div>

        <!-- ===== Thông tin sản phẩm ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Thông tin sản phẩm</h5></div>
            <div class="row">
                <div class="col-md-6 field-row">
                    <label>Mã sản phẩm</label>
                    <div class="view-value">${fn:escapeXml(product.productCode)}</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Danh mục</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${product.category != null}">${fn:escapeXml(product.category.categoryName)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-12 field-row">
                    <label>Mô tả</label>
                    <div class="view-value multiline">
                        <c:choose>
                            <c:when test="${not empty product.description}">${fn:escapeXml(product.description)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Ngày tạo</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${product.createdAt != null}"><fmt:formatDate value="${product.createdAt}" pattern="dd/MM/yyyy"/></c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>URL catalogue</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${not empty product.catalogueUrl}"><a href="${fn:escapeXml(product.catalogueUrl)}" target="_blank" rel="noopener"><i class="fa-solid fa-file-pdf me-1"></i>${fn:escapeXml(product.catalogueUrl)}</a></c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <c:if test="${not empty product.imageUrl}">
                    <div class="col-12 field-row">
                        <label>Hình ảnh</label>
                        <div class="product-image-box">
                            <img src="${fn:escapeXml(product.imageUrl)}" alt="${fn:escapeXml(product.productName)}" onerror="this.parentElement.innerHTML='&lt;i class=&quot;fa-solid fa-triangle-exclamation&quot;&gt;&lt;/i&gt;'">
                        </div>
                    </div>
                </c:if>
            </div>
        </div>

        <!-- ===== Hợp đồng sử dụng sản phẩm này ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Hợp đồng sử dụng sản phẩm này</h5></div>
            <c:choose>
                <c:when test="${empty contractList}">
                    <div class="empty-mini">Chưa có hợp đồng nào sử dụng sản phẩm này.</div>
                </c:when>
                <c:otherwise>
                    <table class="mini-table">
                        <thead>
                            <tr><th>Mã hợp đồng</th><th>Tên hợp đồng</th><th>Khách hàng</th><th>Trạng thái</th></tr>
                        </thead>
                        <tbody>
                            <c:forEach var="contract" items="${contractList}">
                                <tr>
                                    <td><a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}">${fn:escapeXml(contract.contractCode)}</a></td>
                                    <td>${fn:escapeXml(contract.title)}</td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${contract.enterprise != null}">${fn:escapeXml(contract.enterprise.enterpriseName)}</c:when>
                                            <c:otherwise>&mdash;</c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${contract.status == 'Đang hiệu lực'}"><span class="status-pill status-active">${contract.status}</span></c:when>
                                            <c:when test="${contract.status == 'Sắp hết hạn'}"><span class="status-pill status-soon">${contract.status}</span></c:when>
                                            <c:otherwise><span class="status-pill status-expired">${contract.status}</span></c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

        </div>
    </div>

    <!-- ===== Modal xác nhận xóa ===== -->
    <div class="modal fade" id="deleteModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <div class="modal-icon-warn"><i class="fa-solid fa-triangle-exclamation"></i></div>
                </div>
                <div class="modal-body">
                    <h5 class="mb-2" style="font-weight:700; color:#111827;">Xác nhận xóa sản phẩm</h5>
                    Bạn có chắc chắn muốn xóa <strong>${fn:escapeXml(product.productName)}</strong>? Hành động này không thể hoàn tác.
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <button type="button" class="btn-modal-danger" id="confirmDeleteBtn">Xóa sản phẩm</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Chặn xoá nếu sản phẩm còn nằm trong hợp đồng -->
    <c:if test="${param.error == 'has_active_contracts'}">
        <div class="toast-msg blocked show">
            <i class="fa-solid fa-circle-xmark"></i>
            <span>Không thể xoá: sản phẩm đang được dùng trong hợp đồng.</span>
        </div>
    </c:if>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/product" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" value="${product.productId}">
    </form>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));

        function openDeleteModal() {
            deleteModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            document.getElementById('deleteForm').submit();
        });
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
