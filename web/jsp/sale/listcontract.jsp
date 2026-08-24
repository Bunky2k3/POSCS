<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ContractController#showList thiết lập trước khi forward tới trang này:
      - contractList  : List<poscs.model.Contract> (mỗi Contract có sẵn .enterprise và .owner đã join,
                         .status đã được tính lại theo BR-17)
      - statusSummary : Map<String,Integer> đếm số hợp đồng theo từng trạng thái, phục vụ dải KPI
      - currentPage, totalPages, totalCount : thông tin phân trang
      - keyword, statusFilter, typeFilter : giá trị filter hiện tại (để giữ lại lúc submit lại form)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách hợp đồng - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1280px; margin: 28px auto; padding: 0 24px 32px; }

        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 22px; flex-wrap: wrap; gap: 14px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }

        .btn-add {
            background: linear-gradient(120deg, var(--primary), var(--primary-light));
            color: #fff; border: none; border-radius: 10px;
            padding: 10px 20px; font-weight: 600; font-size: 0.9rem;
            text-decoration: none; display: inline-flex; align-items: center; gap: 8px;
            box-shadow: 0 8px 18px rgba(5, 104, 166, 0.3); white-space: nowrap;
        }
        .btn-add:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); color: #fff; }

        .header-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
        .btn-outline-action {
            background: #fff; color: var(--primary); border: 1.5px solid #e5e7eb; border-radius: 10px;
            padding: 9px 18px; font-weight: 600; font-size: 0.87rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px; white-space: nowrap;
        }
        .btn-outline-action:hover { background: #eaf6ff; color: var(--primary-dark); border-color: var(--primary-light); }

        /* ===== KPI mini strip ===== */
        .status-strip { display: flex; gap: 14px; margin-bottom: 20px; flex-wrap: wrap; }
        .status-chip {
            flex: 1 1 200px; padding: 12px 15px; display: flex; align-items: center; gap: 12px;
        }
        .status-chip .dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
        .status-chip .num { font-weight: 700; font-size: 1.15rem; color: #111827; }
        .status-chip .lbl { font-size: 0.78rem; color: #6b7280; }

        /* ===== Filter bar ===== */
        .filter-bar { padding: 18px 20px; margin-bottom: 20px; display: flex; flex-wrap: wrap; gap: 14px; align-items: center; }
        .search-input-wrap { position: relative; flex: 1 1 280px; min-width: 220px; }
        .search-input-wrap i { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: #9ca3af; font-size: 0.9rem; }
        .search-input-wrap input { width: 100%; padding: 10px 14px 10px 38px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.88rem; }
        .search-input-wrap input:focus { outline: none; background: #fff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        .filter-bar select { padding: 10px 14px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.88rem; min-width: 180px; }
        .filter-bar select:focus { outline: none; border-color: var(--primary-light); }

        /* ===== Table ===== */
        .table-card { overflow: hidden; }
        .custom-table { margin-bottom: 0; }
        .custom-table thead th {
            background: #f8fafc; color: #6b7280; font-size: 0.74rem; text-transform: uppercase; letter-spacing: .3px;
            font-weight: 700; padding: 12px 16px; border-bottom: 1.5px solid #eef2f6; white-space: nowrap;
        }
        .custom-table tbody td { padding: 12px 16px; font-size: 0.86rem; color: #111827; vertical-align: middle; border-bottom: 1px solid #f3f4f6; }
        .custom-table tbody tr:last-child td { border-bottom: none; }
        .custom-table tbody tr:hover { background: #f9fdff; }

        .contract-code { font-weight: 700; color: var(--primary); font-size: 0.85rem; }
        .contract-title-link { color: #111827; font-weight: 600; text-decoration: none; }
        .contract-title-link:hover { color: var(--primary); text-decoration: underline; }

        .type-badge { display: inline-block; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; background: #f3f4f6; color: #4b5563; }

        .status-pill { display: inline-flex; align-items: center; gap: 5px; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; white-space: nowrap; }
        .status-pill .dot { width: 6px; height: 6px; border-radius: 50%; }
        .status-active { background: #e8faf3; color: var(--success); }
        .status-active .dot { background: var(--success); }
        .status-soon { background: #fff4e0; color: var(--warning); }
        .status-soon .dot { background: var(--warning); }
        .status-expired { background: #fdecef; color: var(--danger); }
        .status-expired .dot { background: var(--danger); }
        .status-draft { background: #eef2f6; color: #6b7280; }
        .status-draft .dot { background: #9ca3af; }

        .action-icons { display: flex; gap: 6px; justify-content: flex-end; }
        .action-icons button {
            width: 32px; height: 32px; border-radius: 8px; border: none;
            background: #f3f4f6; color: #6b7280; cursor: pointer;
            display: flex; align-items: center; justify-content: center; font-size: 0.82rem; transition: all 0.15s;
        }
        .action-icons .act-view:hover { background: #eaf6ff; color: var(--primary); }
        .action-icons .act-edit:hover { background: #fff4e0; color: var(--warning); }
        .action-icons .act-delete:hover:not(:disabled) { background: #fdecef; color: var(--danger); }
        .action-icons button:disabled { opacity: 0.4; cursor: not-allowed; }

        .empty-state { text-align: center; padding: 60px 20px; color: #9ca3af; }
        .empty-state i { font-size: 2.4rem; margin-bottom: 12px; color: #d1d5db; }
        .empty-state p { font-size: 0.92rem; }

        .pagination-bar { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-top: 1px solid #f3f4f6; flex-wrap: wrap; gap: 10px; }
        .pagination-info { font-size: 0.83rem; color: #6b7280; }
        .pagination { margin: 0; }
        .page-link { color: var(--primary); border-color: #e5e7eb; font-size: 0.85rem; }
        .page-item.active .page-link { background: var(--primary); border-color: var(--primary); }
        .page-link:hover { background: #eaf6ff; color: var(--primary-dark); }

        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 24px 24px 0; }
        .modal-body { padding: 12px 24px 6px; color: #374151; font-size: 0.92rem; }
        .modal-footer { border-top: none; padding: 18px 24px 24px; }
        .btn-modal-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-danger { background: var(--danger); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .modal-icon-warn { width: 52px; height: 52px; border-radius: 50%; background: #fdecef; color: var(--danger); display: flex; align-items: center; justify-content: center; font-size: 1.3rem; margin-bottom: 4px; }

        @media (max-width: 768px) {
            .page-container { padding: 0 14px 32px; }
            .custom-table { font-size: 0.8rem; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="contract" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>Danh sách hợp đồng</h2>
                <p>Quản lý toàn bộ hợp đồng cung cấp và thi công thiết bị viễn thông</p>
            </div>
            <div class="header-actions">
                <a href="${pageContext.request.contextPath}/contract?action=exportExcel&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&type=${fn:escapeXml(typeFilter)}" class="btn-outline-action"><i class="fa-solid fa-file-excel"></i> Xuất Excel</a>
                <a href="${pageContext.request.contextPath}/contract?action=new" class="btn-add"><i class="fa-solid fa-plus"></i> Tạo hợp đồng</a>
            </div>
        </div>

        <!-- ===== Dải trạng thái tổng quan (BR-17) ===== -->
        <div class="status-strip">
            <div class="card-box status-chip"><span class="dot" style="background:var(--success)"></span><div><div class="num">${statusSummary['Đang hiệu lực']}</div><div class="lbl">Đang hiệu lực</div></div></div>
            <div class="card-box status-chip"><span class="dot" style="background:var(--warning)"></span><div><div class="num">${statusSummary['Sắp hết hạn']}</div><div class="lbl">Sắp hết hạn (≤30 ngày)</div></div></div>
            <div class="card-box status-chip"><span class="dot" style="background:var(--danger)"></span><div><div class="num">${statusSummary['Đã hết hạn']}</div><div class="lbl">Đã hết hạn</div></div></div>
            <div class="card-box status-chip"><span class="dot" style="background:#9ca3af"></span><div><div class="num">${statusSummary['Chưa hiệu lực']}</div><div class="lbl">Chưa hiệu lực</div></div></div>
        </div>

        <!-- ===== Bộ lọc / tìm kiếm ===== -->
        <form class="filter-bar card-box" method="GET" action="${pageContext.request.contextPath}/contract" id="filterForm">
            <input type="hidden" name="action" value="list">
            <div class="search-input-wrap">
                <i class="fa-solid fa-magnifying-glass"></i>
                <input type="text" id="searchInput" name="keyword" value="${fn:escapeXml(keyword)}" placeholder="Tìm theo mã HĐ, tiêu đề, khách hàng...">
            </div>
            <select id="filterStatus" name="status">
                <option value="">Tất cả trạng thái</option>
                <option value="Đang hiệu lực" ${statusFilter == 'Đang hiệu lực' ? 'selected' : ''}>Đang hiệu lực</option>
                <option value="Sắp hết hạn" ${statusFilter == 'Sắp hết hạn' ? 'selected' : ''}>Sắp hết hạn</option>
                <option value="Đã hết hạn" ${statusFilter == 'Đã hết hạn' ? 'selected' : ''}>Đã hết hạn</option>
                <option value="Chưa hiệu lực" ${statusFilter == 'Chưa hiệu lực' ? 'selected' : ''}>Chưa hiệu lực</option>
            </select>
            <select id="filterType" name="type">
                <option value="">Tất cả loại hợp đồng</option>
                <option value="Cung cấp thiết bị" ${typeFilter == 'Cung cấp thiết bị' ? 'selected' : ''}>Cung cấp thiết bị</option>
                <option value="Thi công lắp đặt" ${typeFilter == 'Thi công lắp đặt' ? 'selected' : ''}>Thi công lắp đặt</option>
                <option value="Bảo trì bảo dưỡng" ${typeFilter == 'Bảo trì bảo dưỡng' ? 'selected' : ''}>Bảo trì bảo dưỡng</option>
            </select>
        </form>

        <!-- ===== Bảng danh sách ===== -->
        <div class="table-card card-box">
            <div class="table-responsive">
                <table class="table custom-table" id="contractTable">
                    <thead>
                        <tr>
                            <th>Mã HĐ</th>
                            <th>Tiêu đề</th>
                            <th>Khách hàng</th>
                            <th>Loại HĐ</th>
                            <th>Ngày ký</th>
                            <th>Ngày kết thúc</th>
                            <th>Trạng thái</th>
                            <th class="text-end">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="contractTableBody">
                        <c:forEach var="contract" items="${contractList}">
                            <tr>
                                <td class="contract-code">${fn:escapeXml(contract.contractCode)}</td>
                                <td><a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}" class="contract-title-link">${fn:escapeXml(contract.title)}</a></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${contract.enterprise != null}">${fn:escapeXml(contract.enterprise.enterpriseName)}</c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td><span class="type-badge">${fn:escapeXml(contract.contractType)}</span></td>
                                <td><fmt:formatDate value="${contract.signingDate}" pattern="dd/MM/yyyy"/></td>
                                <td><fmt:formatDate value="${contract.endDate}" pattern="dd/MM/yyyy"/></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${contract.status == 'Đang hiệu lực'}"><span class="status-pill status-active"><span class="dot"></span>Đang hiệu lực</span></c:when>
                                        <c:when test="${contract.status == 'Sắp hết hạn'}"><span class="status-pill status-soon"><span class="dot"></span>Sắp hết hạn</span></c:when>
                                        <c:when test="${contract.status == 'Đã hết hạn'}"><span class="status-pill status-expired"><span class="dot"></span>Đã hết hạn</span></c:when>
                                        <c:otherwise><span class="status-pill status-draft"><span class="dot"></span>Chưa hiệu lực</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <div class="action-icons">
                                        <button class="act-view" title="Xem chi tiết" onclick="location.href='${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}'"><i class="fa-regular fa-eye"></i></button>
                                        <button class="act-edit" title="Sửa" onclick="location.href='${pageContext.request.contextPath}/contract?action=edit&id=${contract.contractId}'"><i class="fa-solid fa-pen"></i></button>
                                        <c:choose>
                                            <c:when test="${contract.status == 'Chưa hiệu lực'}">
                                                <button class="act-delete" title="Xóa" onclick="openDeleteModal(${contract.contractId}, '${fn:escapeXml(contract.contractCode)}')"><i class="fa-solid fa-trash"></i></button>
                                            </c:when>
                                            <c:otherwise>
                                                <button class="act-delete" title="Chỉ được xóa hợp đồng ở trạng thái chưa hiệu lực" disabled><i class="fa-solid fa-trash"></i></button>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>

            <!-- ===== Trạng thái rỗng (MSG-020) ===== -->
            <div class="empty-state" id="emptyState" style="${empty contractList ? 'display:block' : 'display:none'}">
                <i class="fa-regular fa-folder-open"></i>
                <p>Không có hợp đồng để hiển thị.</p>
            </div>

            <!-- ===== Phân trang ===== -->
            <div class="pagination-bar">
                <span class="pagination-info">Hiển thị ${fn:length(contractList)} trong tổng số ${totalCount} hợp đồng</span>
                <nav>
                    <ul class="pagination pagination-sm mb-0">
                        <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/contract?action=list&page=${currentPage - 1}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&type=${fn:escapeXml(typeFilter)}">Trước</a></li>
                        <c:forEach begin="1" end="${totalPages}" var="p">
                            <li class="page-item ${p == currentPage ? 'active' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/contract?action=list&page=${p}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&type=${fn:escapeXml(typeFilter)}">${p}</a></li>
                        </c:forEach>
                        <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/contract?action=list&page=${currentPage + 1}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&type=${fn:escapeXml(typeFilter)}">Sau</a></li>
                    </ul>
                </nav>
            </div>
        </div>
    </div>

        </div>
    </div>

    <!-- ===== Modal xác nhận xóa (MSG-043) ===== -->
    <div class="modal fade" id="deleteModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <div class="modal-icon-warn"><i class="fa-solid fa-triangle-exclamation"></i></div>
                </div>
                <div class="modal-body">
                    <h5 class="mb-2" style="font-weight:700; color:#111827;">Xác nhận xóa hợp đồng</h5>
                    Bạn có chắc chắn muốn xóa hợp đồng <strong id="deleteContractCode"></strong>?
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <button type="button" class="btn-modal-danger" id="confirmDeleteBtn">Xóa hợp đồng</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/contract" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" id="deleteFormId">
    </form>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));
        var contractIdToDelete = null;

        function openDeleteModal(contractId, contractCode) {
            contractIdToDelete = contractId;
            document.getElementById('deleteContractCode').textContent = contractCode;
            deleteModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            if (contractIdToDelete) {
                document.getElementById('deleteFormId').value = contractIdToDelete;
                document.getElementById('deleteForm').submit();
            }
            deleteModal.hide();
        });

        // Tự động submit lại form lọc khi đổi trạng thái / loại hợp đồng
        document.getElementById('filterStatus').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterType').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
