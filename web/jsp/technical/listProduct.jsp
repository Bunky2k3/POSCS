<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Bố cục lấy cảm hứng từ trang danh mục sản phẩm postef.com.vn/san-pham/:
    panel "Danh mục sản phẩm" bên trái (kèm số lượng, bấm để lọc) + lưới thẻ
    sản phẩm bên phải, thay cho bảng liệt kê thông thường.

    Servlet cần đặt các request attribute sau trước khi forward tới trang này:
      - productList    : List<poscs.model.Product>          (mỗi Product nên có sẵn .category đã join)
      - categoryList   : List<poscs.model.ProductCategory>   (toàn bộ danh mục, để đổ panel bên trái)
      - categoryCounts : Map<Integer, Integer>                (category_id -> số sản phẩm còn hiệu lực)
      - grandTotal     : int                                  (tổng số sản phẩm, không lọc, cho mục "Tất cả")
      - currentPage, totalPages, totalCount : thông tin phân trang (BR-12)
      - keyword, categoryFilter : giá trị filter hiện tại (để giữ lại lúc submit lại form tìm kiếm)
      - csrfToken : cho form xoá (POST)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách sản phẩm - POSCS Portal</title>

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
        .topbar-left { display: flex; align-items: center; gap: 32px; }

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

        .page-container {
            max-width: 1320px;
            margin: 28px auto;
            padding: 0 24px 32px;
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

        /* ===== Bố cục: panel danh mục + nội dung ===== */
        .catalog-layout { display: flex; align-items: flex-start; gap: 22px; }

        /* ===== Panel "Danh mục sản phẩm" (kiểu postef.com.vn/san-pham/) ===== */
        .category-panel { width: 260px; flex-shrink: 0; padding: 20px 18px; position: sticky; top: 90px; }
        .category-panel-title {
            font-weight: 700; color: var(--primary-dark); font-size: 0.82rem;
            text-transform: uppercase; letter-spacing: .4px; margin-bottom: 14px;
        }
        .category-list { list-style: none; margin: 0; padding: 0; }
        .category-list li { margin-bottom: 2px; }
        .category-list a {
            display: flex; justify-content: space-between; align-items: center; gap: 8px;
            padding: 9px 12px; border-radius: 10px;
            color: #374151; font-weight: 600; font-size: 0.87rem; text-decoration: none;
        }
        .category-list a:hover { background: #f0f9ff; color: var(--primary-dark); }
        .category-list a.active { background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; }
        .category-list a.active .category-count { color: #fff; opacity: 0.85; }
        .category-count { font-size: 0.76rem; color: #9ca3af; font-weight: 600; }
        .category-list-divider { border: none; border-top: 1px solid #eef2f6; margin: 10px 0; }

        /* ===== Ô tìm kiếm ===== */
        .search-bar { padding: 16px 18px; margin-bottom: 20px; }
        .search-input-wrap { position: relative; }
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
        .active-filter-chip {
            display: inline-flex; align-items: center; gap: 8px;
            background: #eaf6ff; color: var(--primary-dark); font-weight: 600; font-size: 0.82rem;
            border-radius: 20px; padding: 6px 12px; margin-top: 12px;
        }
        .active-filter-chip a { color: var(--primary-dark); text-decoration: none; }
        .active-filter-chip a:hover { color: var(--danger); }

        /* ===== Lưới thẻ sản phẩm ===== */
        .product-grid {
            display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
            gap: 20px;
        }
        .product-card {
            display: flex; flex-direction: column; overflow: hidden;
            transition: box-shadow 0.15s ease, transform 0.15s ease;
        }
        .product-card:hover { box-shadow: 0 16px 36px rgba(0, 40, 80, 0.14); transform: translateY(-2px); }
        .product-card-media {
            position: relative; width: 100%; height: 150px;
            background: #eef6fb; display: flex; align-items: center; justify-content: center;
            color: var(--primary-light); font-size: 2rem; overflow: hidden;
        }
        .product-card-media img { width: 100%; height: 100%; object-fit: cover; }
        .product-card-category {
            position: absolute; top: 10px; left: 10px;
            background: rgba(255,255,255,0.92); color: var(--primary-dark);
            font-size: 0.68rem; font-weight: 700; padding: 3px 10px; border-radius: 20px;
        }
        .product-card-body { padding: 14px 16px 16px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
        .product-card-code { font-size: 0.72rem; font-weight: 700; color: var(--primary); }
        .product-card-name {
            color: #111827; font-weight: 600; font-size: 0.92rem; text-decoration: none;
            display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
            min-height: 2.5em; line-height: 1.25;
        }
        .product-card-name:hover { color: var(--primary); }
        .product-card-footer {
            display: flex; justify-content: space-between; align-items: center;
            margin-top: auto; padding-top: 10px; border-top: 1px solid #f3f4f6;
        }
        .product-card-updated { font-size: 0.72rem; color: #9ca3af; }
        .action-icons { display: flex; gap: 6px; }
        .action-icons button {
            width: 30px; height: 30px; border-radius: 8px; border: none;
            background: #f3f4f6; color: #6b7280; cursor: pointer;
            display: flex; align-items: center; justify-content: center;
            font-size: 0.78rem; transition: all 0.15s;
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
            padding: 18px 4px 0; flex-wrap: wrap; gap: 10px;
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

        @media (max-width: 900px) {
            .catalog-layout { flex-direction: column; }
            .category-panel { width: 100%; position: static; }
        }
        @media (max-width: 768px) {
            .page-container { padding: 0 14px 32px; }
        }
    </style>
</head>
<body>

    <nav class="topbar">
        <div class="topbar-left">
            <div class="brand"><i class="fa-solid fa-tower-broadcast"></i> POSCS Portal</div>
        </div>
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
                <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff"
                     class="avatar-mini" alt="avatar" data-bs-toggle="dropdown" aria-expanded="false">
                <ul class="dropdown-menu dropdown-menu-end">
                    <li class="dd-user-header">
                        <img src="https://ui-avatars.com/api/?name=${fn:escapeXml(sessionScope.currentUser.firstName)}&background=0568a6&color=fff" alt="avatar">
                        <div>
                            <div class="dd-name"><c:out value="${sessionScope.currentUser.fullName}"/></div>
                            <div class="dd-role"><c:out value="${sessionScope.currentUser.role.roleName}"/></div>
                        </div>
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
            <c:if test="${sessionScope.currentUser.role.roleName == 'Admin'}"><a href="${pageContext.request.contextPath}/employee" class="sidebar-link"><i class="fa-solid fa-user-tie"></i><span>Nhân viên</span></a></c:if>
        </aside>
        <div class="main-content">


    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>Danh sách sản phẩm</h2>
                <p>Quản lý danh mục thiết bị / sản phẩm POS</p>
            </div>
            <a href="${pageContext.request.contextPath}/product?action=new" class="btn-add"><i class="fa-solid fa-plus"></i> Thêm sản phẩm</a>
        </div>

        <div class="catalog-layout">
            <!-- ===== Panel "Danh mục sản phẩm" ===== -->
            <aside class="category-panel card-box">
                <div class="category-panel-title">Danh mục sản phẩm</div>
                <ul class="category-list">
                    <li>
                        <a href="${pageContext.request.contextPath}/product?action=list&keyword=${fn:escapeXml(keyword)}"
                           class="${empty categoryFilter ? 'active' : ''}">
                            <span>Tất cả sản phẩm</span>
                            <span class="category-count">(${grandTotal})</span>
                        </a>
                    </li>
                    <li><hr class="category-list-divider"></li>
                    <c:forEach var="cat" items="${categoryList}">
                        <li>
                            <a href="${pageContext.request.contextPath}/product?action=list&categoryId=${cat.categoryId}&keyword=${fn:escapeXml(keyword)}"
                               class="${categoryFilter == cat.categoryId ? 'active' : ''}">
                                <span>${fn:escapeXml(cat.categoryName)}</span>
                                <span class="category-count">(${empty categoryCounts[cat.categoryId] ? 0 : categoryCounts[cat.categoryId]})</span>
                            </a>
                        </li>
                    </c:forEach>
                </ul>
            </aside>

            <div style="flex:1; min-width:0;">
                <!-- ===== Ô tìm kiếm ===== -->
                <form class="search-bar card-box" method="GET" action="${pageContext.request.contextPath}/product" id="filterForm">
                    <input type="hidden" name="action" value="list">
                    <input type="hidden" name="categoryId" id="categoryIdInput" value="${categoryFilter}">
                    <div class="search-input-wrap">
                        <i class="fa-solid fa-magnifying-glass"></i>
                        <input type="text" id="searchInput" name="keyword" value="${fn:escapeXml(keyword)}" placeholder="Tìm theo mã SP, tên sản phẩm...">
                    </div>
                    <c:if test="${not empty categoryFilter}">
                        <span class="active-filter-chip">
                            <i class="fa-solid fa-filter"></i> Đang lọc theo danh mục
                            <a href="${pageContext.request.contextPath}/product?action=list&keyword=${fn:escapeXml(keyword)}" title="Bỏ lọc"><i class="fa-solid fa-xmark"></i></a>
                        </span>
                    </c:if>
                </form>

                <!-- ===== Lưới thẻ sản phẩm ===== -->
                <c:choose>
                    <c:when test="${empty productList}">
                        <div class="empty-state card-box">
                            <i class="fa-regular fa-folder-open"></i>
                            <p>Không có sản phẩm để hiển thị.</p>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="product-grid">
                            <c:forEach var="product" items="${productList}">
                                <div class="product-card card-box">
                                    <div class="product-card-media">
                                        <c:choose>
                                            <c:when test="${not empty product.primaryImageUrl}">
                                                <c:choose>
                                                    <c:when test="${fn:startsWith(product.primaryImageUrl, 'http')}"><c:set var="cardImgSrc" value="${product.primaryImageUrl}"/></c:when>
                                                    <c:otherwise><c:set var="cardImgSrc" value="${pageContext.request.contextPath}${product.primaryImageUrl}"/></c:otherwise>
                                                </c:choose>
                                                <img src="${fn:escapeXml(cardImgSrc)}" alt="${fn:escapeXml(product.productName)}">
                                            </c:when>
                                            <c:otherwise>
                                                <i class="fa-solid fa-box"></i>
                                            </c:otherwise>
                                        </c:choose>
                                        <c:if test="${product.category != null}">
                                            <span class="product-card-category">${fn:escapeXml(product.category.categoryName)}</span>
                                        </c:if>
                                    </div>
                                    <div class="product-card-body">
                                        <span class="product-card-code">${fn:escapeXml(product.productCode)}</span>
                                        <a href="${pageContext.request.contextPath}/product?action=view&id=${product.productId}" class="product-card-name">${fn:escapeXml(product.productName)}</a>
                                        <div class="product-card-footer">
                                            <span class="product-card-updated">
                                                <c:if test="${product.updatedAt != null}"><fmt:formatDate value="${product.updatedAt}" pattern="dd/MM/yyyy"/></c:if>
                                            </span>
                                            <div class="action-icons">
                                                <button class="act-view" title="Xem chi tiết" onclick="location.href='${pageContext.request.contextPath}/product?action=view&id=${product.productId}'"><i class="fa-regular fa-eye"></i></button>
                                                <button class="act-edit" title="Sửa" onclick="location.href='${pageContext.request.contextPath}/product?action=edit&id=${product.productId}'"><i class="fa-solid fa-pen"></i></button>
                                                <button class="act-delete" title="Xóa" onclick="openDeleteModal(${product.productId}, '${fn:escapeXml(product.productName)}')"><i class="fa-solid fa-trash"></i></button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>

                        <!-- ===== Phân trang (BR-12) ===== -->
                        <div class="pagination-bar">
                            <span class="pagination-info">Hiển thị ${fn:length(productList)} trong tổng số ${totalCount} sản phẩm</span>
                            <nav>
                                <ul class="pagination pagination-sm mb-0">
                                    <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/product?action=list&page=${currentPage - 1}&keyword=${fn:escapeXml(keyword)}&categoryId=${categoryFilter}">Trước</a></li>
                                    <c:forEach begin="1" end="${totalPages}" var="p">
                                        <li class="page-item ${p == currentPage ? 'active' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/product?action=list&page=${p}&keyword=${fn:escapeXml(keyword)}&categoryId=${categoryFilter}">${p}</a></li>
                                    </c:forEach>
                                    <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/product?action=list&page=${currentPage + 1}&keyword=${fn:escapeXml(keyword)}&categoryId=${categoryFilter}">Sau</a></li>
                                </ul>
                            </nav>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
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
                    Bạn có chắc chắn muốn xóa sản phẩm <strong id="deleteProductName"></strong>? Hành động này không thể hoàn tác.
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <button type="button" class="btn-modal-danger" id="confirmDeleteBtn">Xóa sản phẩm</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/product" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" id="deleteFormId">
    </form>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));
        var productIdToDelete = null;

        function openDeleteModal(productId, productName) {
            productIdToDelete = productId;
            document.getElementById('deleteProductName').textContent = productName;
            deleteModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            if (productIdToDelete) {
                document.getElementById('deleteFormId').value = productIdToDelete;
                document.getElementById('deleteForm').submit();
            }
            deleteModal.hide();
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
