<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần đặt các request attribute sau trước khi forward tới trang này:
      - customerList : List<poscs.model.Enterprise>  (mỗi Enterprise nên có sẵn .address.district.province và .accountOwner đã join)
      - userList      : List<poscs.model.User>        (toàn bộ nhân viên, để đổ dropdown lọc "Người phụ trách")
      - currentPage, totalPages, totalCount : thông tin phân trang (BR-12)
      - keyword, typeFilter, assigneeFilter : giá trị filter hiện tại (để giữ lại lúc submit lại form tìm kiếm)
--%>
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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container {
            max-width: 1240px;
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

        .header-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
        .btn-outline-action {
            background: #fff; color: var(--primary); border: 1.5px solid #e5e7eb; border-radius: 10px;
            padding: 9px 18px; font-weight: 600; font-size: 0.87rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px; white-space: nowrap;
        }
        .btn-outline-action:hover { background: #eaf6ff; color: var(--primary-dark); border-color: var(--primary-light); }

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
            padding: 12px 16px; border-bottom: 1.5px solid #eef2f6; white-space: nowrap;
        }
        .custom-table tbody td {
            padding: 12px 16px; font-size: 0.87rem; color: #111827;
            vertical-align: middle; border-bottom: 1px solid #f3f4f6;
        }
        .custom-table tbody tr:last-child td { border-bottom: none; }
        .custom-table tbody tr:hover { background: #f9fdff; }

        .customer-code {
            font-weight: 700; color: var(--primary); font-size: 0.85rem;
        }
        .customer-name-cell { display: flex; align-items: center; gap: 10px; }
        .customer-logo {
            width: 32px; height: 32px; border-radius: 8px; overflow: hidden; flex-shrink: 0;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center; font-size: 0.85rem;
        }
        .customer-logo img { width: 100%; height: 100%; object-fit: cover; }
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
            .page-container { padding: 0 14px 32px; }
            .custom-table { font-size: 0.8rem; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="customer" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>Danh sách khách hàng</h2>
                <p>Quản lý thông tin khách hàng doanh nghiệp đối tác</p>
            </div>
            <div class="header-actions">
                <a href="${pageContext.request.contextPath}/customer?action=exportExcel&keyword=${fn:escapeXml(keyword)}&type=${fn:escapeXml(typeFilter)}&assigneeId=${assigneeFilter}" class="btn-outline-action"><i class="fa-solid fa-file-excel"></i> Xuất Excel</a>
                <a href="${pageContext.request.contextPath}/customer?action=importForm" class="btn-outline-action"><i class="fa-solid fa-file-import"></i> Nhập Excel</a>
                <a href="${pageContext.request.contextPath}/customer?action=new" class="btn-add"><i class="fa-solid fa-plus"></i> Thêm khách hàng</a>
            </div>
        </div>

        <!-- ===== Bộ lọc / tìm kiếm ===== -->
        <form class="filter-bar card-box" method="GET" action="${pageContext.request.contextPath}/customer" id="filterForm">
            <input type="hidden" name="action" value="list">
            <div class="search-input-wrap">
                <i class="fa-solid fa-magnifying-glass"></i>
                <input type="text" id="searchInput" name="keyword" value="${fn:escapeXml(keyword)}" placeholder="Tìm theo mã KH, tên, số điện thoại...">
            </div>
            <select id="filterType" name="type">
                <option value="">Tất cả loại khách hàng</option>
                <option value="Nhà mạng viễn thông" ${typeFilter == 'Nhà mạng viễn thông' ? 'selected' : ''}>Nhà mạng viễn thông</option>
                <option value="Nhà thầu thi công" ${typeFilter == 'Nhà thầu thi công' ? 'selected' : ''}>Nhà thầu thi công</option>
                <option value="Đại lý phân phối" ${typeFilter == 'Đại lý phân phối' ? 'selected' : ''}>Đại lý phân phối</option>
            </select>
            <select id="filterAssignee" name="assigneeId">
                <option value="">Tất cả người phụ trách</option>
                <c:forEach var="staff" items="${userList}">
                    <option value="${staff.userId}" ${assigneeFilter == staff.userId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                </c:forEach>
            </select>
        </form>

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
                        <c:forEach var="customer" items="${customerList}">
                            <tr>
                                <td class="customer-code">${fn:escapeXml(customer.enterpriseCode)}</td>
                                <td>
                                    <div class="customer-name-cell">
                                        <div class="customer-logo">
                                            <c:choose>
                                                <c:when test="${not empty customer.logoUrl}"><img src="${pageContext.request.contextPath}${fn:escapeXml(customer.logoUrl)}" alt="Logo"></c:when>
                                                <c:otherwise><i class="fa-solid fa-building"></i></c:otherwise>
                                            </c:choose>
                                        </div>
                                        <a href="${pageContext.request.contextPath}/customer?action=view&id=${customer.enterpriseId}" class="customer-name-link">${fn:escapeXml(customer.enterpriseName)}</a>
                                    </div>
                                </td>
                                <td><span class="type-badge">${fn:escapeXml(customer.customerType)}</span></td>
                                <td>${fn:escapeXml(customer.email)}</td>
                                <td>${fn:escapeXml(customer.phone)}</td>
                                <td>
                                    <c:choose>
                                        <c:when test="${customer.address != null}">${fn:escapeXml(customer.address.fullAddress)}</c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${customer.accountOwner != null}">${fn:escapeXml(customer.accountOwner.fullName)}</c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <div class="action-icons">
                                        <button class="act-view" title="Xem chi tiết" onclick="location.href='${pageContext.request.contextPath}/customer?action=view&id=${customer.enterpriseId}'"><i class="fa-regular fa-eye"></i></button>
                                        <button class="act-edit" title="Sửa" onclick="location.href='${pageContext.request.contextPath}/customer?action=edit&id=${customer.enterpriseId}'"><i class="fa-solid fa-pen"></i></button>
                                        <button class="act-delete" title="Xóa" onclick="openDeleteModal(${customer.enterpriseId}, '${fn:escapeXml(customer.enterpriseName)}')"><i class="fa-solid fa-trash"></i></button>
                                    </div>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>

            <!-- ===== Trạng thái rỗng (MSG-017) ===== -->
            <div class="empty-state" id="emptyState" style="${empty customerList ? 'display:block' : 'display:none'}">
                <i class="fa-regular fa-folder-open"></i>
                <p>Không có khách hàng để hiển thị.</p>
            </div>

            <!-- ===== Phân trang (BR-12) ===== -->
            <div class="pagination-bar">
                <span class="pagination-info" id="paginationInfo">Hiển thị ${fn:length(customerList)} trong tổng số ${totalCount} khách hàng</span>
                <nav>
                    <ul class="pagination pagination-sm mb-0">
                        <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/customer?action=list&page=${currentPage - 1}&keyword=${fn:escapeXml(keyword)}&type=${fn:escapeXml(typeFilter)}&assigneeId=${assigneeFilter}">Trước</a></li>
                        <c:forEach begin="1" end="${totalPages}" var="p">
                            <li class="page-item ${p == currentPage ? 'active' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/customer?action=list&page=${p}&keyword=${fn:escapeXml(keyword)}&type=${fn:escapeXml(typeFilter)}&assigneeId=${assigneeFilter}">${p}</a></li>
                        </c:forEach>
                        <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/customer?action=list&page=${currentPage + 1}&keyword=${fn:escapeXml(keyword)}&type=${fn:escapeXml(typeFilter)}&assigneeId=${assigneeFilter}">Sau</a></li>
                    </ul>
                </nav>
            </div>
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
                    Bạn có chắc chắn muốn xóa khách hàng <strong id="deleteCustomerName"></strong>? Hành động này không thể hoàn tác.
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <button type="button" class="btn-modal-danger" id="confirmDeleteBtn">Xóa khách hàng</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/customer" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" id="deleteFormId">
    </form>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));
        var customerIdToDelete = null;

        function openDeleteModal(customerId, customerName) {
            customerIdToDelete = customerId;
            document.getElementById('deleteCustomerName').textContent = customerName;
            deleteModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            if (customerIdToDelete) {
                document.getElementById('deleteFormId').value = customerIdToDelete;
                document.getElementById('deleteForm').submit();
            }
            deleteModal.hide();
        });

        // Tự động submit lại form lọc khi đổi loại KH / người phụ trách
        document.getElementById('filterType').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterAssignee').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
