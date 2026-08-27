<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần đặt các request attribute sau trước khi forward tới trang này:
      - employeeList : List<poscs.model.User> (đã kèm .role)
      - roleList     : List<poscs.model.Role> (đổ dropdown lọc "Vai trò")
      - currentPage, totalPages, totalCount : thông tin phân trang (BR-12)
      - keyword, statusFilter, roleFilter   : giá trị filter hiện tại
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách nhân viên - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1240px; margin: 28px auto; padding: 0 24px 32px; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 22px; flex-wrap: wrap; gap: 14px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }

        .btn-add { background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; border: none; border-radius: 10px; padding: 10px 20px; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; gap: 8px; box-shadow: 0 8px 18px rgba(5,104,166,0.3); white-space: nowrap; }
        .btn-add:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); color: #fff; }

        .filter-bar { padding: 18px 20px; margin-bottom: 20px; display: flex; flex-wrap: wrap; gap: 14px; align-items: center; }
        .search-input-wrap { position: relative; flex: 1 1 280px; min-width: 220px; }
        .search-input-wrap i { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: #9ca3af; font-size: 0.9rem; }
        .search-input-wrap input { width: 100%; padding: 10px 14px 10px 38px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.88rem; }
        .search-input-wrap input:focus { outline: none; background: #fff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15,158,219,0.15); }
        .filter-bar select { padding: 10px 14px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.88rem; min-width: 170px; }
        .filter-bar select:focus { outline: none; border-color: var(--primary-light); }

        /* ===== Card grid nhân viên (BR-22) ===== */
        .employee-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(270px, 1fr)); gap: 18px; padding: 4px; }
        .employee-card { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0,40,80,0.08); padding: 20px; text-decoration: none; color: inherit; display: block; transition: transform 0.12s ease, box-shadow 0.12s ease; }
        .employee-card:hover { transform: translateY(-2px); box-shadow: 0 14px 34px rgba(0,40,80,0.14); color: inherit; }
        .employee-card-top { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
        .employee-avatar { width: 48px; height: 48px; border-radius: 50%; object-fit: cover; border: 2px solid #eef2f6; }
        .employee-name { font-weight: 700; color: #111827; font-size: 0.98rem; margin-bottom: 2px; }
        .employee-code { font-size: 0.76rem; color: var(--primary); font-weight: 700; }
        .employee-field { display: flex; align-items: center; gap: 8px; font-size: 0.83rem; color: #6b7280; margin-bottom: 6px; }
        .employee-field i { width: 16px; color: #9ca3af; }
        .status-badge { display: inline-block; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; margin-top: 8px; }
        .status-active { background: #e7f9f1; color: var(--success); }
        .status-inactive { background: #fdecef; color: var(--danger); }

        .empty-state { text-align: center; padding: 60px 20px; color: #9ca3af; grid-column: 1 / -1; }
        .empty-state i { font-size: 2.4rem; margin-bottom: 12px; color: #d1d5db; }
        .empty-state p { font-size: 0.92rem; }

        .pagination-bar { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-top: 1px solid #f3f4f6; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
        .pagination-info { font-size: 0.83rem; color: #6b7280; }
        .pagination { margin: 0; }
        .page-link { color: var(--primary); border-color: #e5e7eb; font-size: 0.85rem; }
        .page-item.active .page-link { background: var(--primary); border-color: var(--primary); }
        .page-link:hover { background: #eaf6ff; color: var(--primary-dark); }

        @media (max-width: 768px) { .page-container { padding: 0 14px 32px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="employee" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <div class="page-header-row">
            <div>
                <h2>Danh sách nhân viên</h2>
                <p>Quản lý tài khoản và hồ sơ nhân viên nội bộ</p>
            </div>
            <a href="${pageContext.request.contextPath}/employee?action=new" class="btn-add"><i class="fa-solid fa-plus"></i> Thêm nhân viên</a>
        </div>

        <!-- ===== Bộ lọc / tìm kiếm ===== -->
        <form class="filter-bar card-box" method="GET" action="${pageContext.request.contextPath}/employee" id="filterForm">
            <input type="hidden" name="action" value="list">
            <div class="search-input-wrap">
                <i class="fa-solid fa-magnifying-glass"></i>
                <input type="text" name="keyword" value="${fn:escapeXml(keyword)}" placeholder="Tìm theo tên, email, số điện thoại...">
            </div>
            <select id="filterRole" name="roleId">
                <option value="">Tất cả vai trò</option>
                <c:forEach var="r" items="${roleList}">
                    <option value="${r.roleId}" ${roleFilter == r.roleId ? 'selected' : ''}>${fn:escapeXml(r.roleName)}</option>
                </c:forEach>
            </select>
            <select id="filterStatus" name="status">
                <option value="">Tất cả trạng thái</option>
                <option value="Active" ${statusFilter == 'Active' ? 'selected' : ''}>Đang hoạt động</option>
                <option value="Inactive" ${statusFilter == 'Inactive' ? 'selected' : ''}>Ngừng hoạt động</option>
            </select>
        </form>

        <!-- ===== Card grid nhân viên (BR-22) ===== -->
        <div class="employee-grid">
            <c:forEach var="emp" items="${employeeList}">
                <a class="employee-card" href="${pageContext.request.contextPath}/employee?action=view&id=${emp.userId}">
                    <div class="employee-card-top">
                        <img class="employee-avatar" src="https://ui-avatars.com/api/?name=${fn:escapeXml(emp.firstName)}&background=0568a6&color=fff" alt="avatar">
                        <div>
                            <div class="employee-name">${fn:escapeXml(emp.fullName)}</div>
                            <div class="employee-code">NV-<c:out value="${emp.userId}"/></div>
                        </div>
                    </div>
                    <div class="employee-field"><i class="fa-solid fa-building"></i>${fn:escapeXml(emp.department.departmentName)} &middot; ${fn:escapeXml(emp.role.roleName)}</div>
                    <div class="employee-field"><i class="fa-solid fa-envelope"></i>${fn:escapeXml(emp.email)}</div>
                    <span class="status-badge ${emp.deleted ? 'status-inactive' : 'status-active'}">${emp.deleted ? 'Ngừng hoạt động' : 'Đang hoạt động'}</span>
                </a>
            </c:forEach>

            <!-- ===== Trạng thái rỗng (BR-23) ===== -->
            <c:if test="${empty employeeList}">
                <div class="empty-state">
                    <i class="fa-regular fa-folder-open"></i>
                    <p>Không có nhân viên để hiển thị.</p>
                </div>
            </c:if>
        </div>

        <!-- ===== Phân trang (BR-12) ===== -->
        <div class="pagination-bar card-box">
            <span class="pagination-info">Hiển thị ${fn:length(employeeList)} trong tổng số ${totalCount} nhân viên</span>
            <nav>
                <ul class="pagination pagination-sm mb-0">
                    <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/employee?action=list&page=${currentPage - 1}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&roleId=${roleFilter}">Trước</a></li>
                    <c:forEach begin="1" end="${totalPages}" var="p">
                        <li class="page-item ${p == currentPage ? 'active' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/employee?action=list&page=${p}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&roleId=${roleFilter}">${p}</a></li>
                    </c:forEach>
                    <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/employee?action=list&page=${currentPage + 1}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&roleId=${roleFilter}">Sau</a></li>
                </ul>
            </nav>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        document.getElementById('filterRole').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterStatus').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
    </script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
