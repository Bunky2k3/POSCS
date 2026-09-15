<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ContractController#showList thiết lập trước khi forward tới trang này:
      - contractList  : List<poscs.model.Contract> (mỗi Contract có sẵn .enterprise và .owner đã join,
                         .status đã được tính lại theo BR-17)
      - statusSummary : Map<String,Integer> đếm số hợp đồng theo từng trạng thái, phục vụ dải KPI
      - provinceList  : List<poscs.model.Province> (18 tỉnh địa bàn chi nhánh, để đổ dropdown lọc "Tỉnh/Thành")
      - currentPage, totalPages, totalCount : thông tin phân trang
      - keyword, statusFilter, typeFilter, provinceFilter : giá trị filter hiện tại (để giữ lại lúc submit lại form)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách hợp đồng - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1440px; margin: 28px auto; padding: 0 24px 32px; }

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
        .filter-bar { padding: 18px 20px; margin-bottom: 20px; display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
        .search-input-wrap { position: relative; flex: 1 1 170px; min-width: 160px; }
        .search-input-wrap i { position: absolute; left: 14px; top: 50%; transform: translateY(-50%); color: #9ca3af; font-size: 0.9rem; }
        .search-input-wrap input { width: 100%; padding: 10px 14px 10px 38px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.88rem; }
        .search-input-wrap input:focus { outline: none; background: #fff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        /* min-width 180px x nhiều ô là tràn hàng ngay ở màn hình 1366px --
           thu về 150px và cho phép co lại thì cả thanh lọc nằm gọn một hàng. */
        .filter-bar select { padding: 9px 10px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.84rem; flex: 0 1 auto; min-width: 124px; max-width: 148px; }
        #filterYear { min-width: 104px; max-width: 118px; }
        #filterPeriod { min-width: 110px; max-width: 124px; }
        .filter-bar select:focus { outline: none; border-color: var(--primary-light); }

        /* ===== Table ===== */
        .table-card { overflow: hidden; }
        /* Thanh cuộn ngang: để mặc định thì Windows ẩn nó đi tới khi cuộn, người
           dùng không biết là bảng còn phần bên phải. Cho nó dày lên và luôn hiện,
           kèm con trỏ bàn tay -- nhìn là biết kéo được. */
        .table-responsive {
            overflow-x: auto; cursor: grab;
            scrollbar-width: thin; scrollbar-color: #cbd5e1 #f1f5f9;
        }
        .table-responsive.is-dragging { cursor: grabbing; user-select: none; }
        .table-responsive::-webkit-scrollbar { height: 11px; }
        .table-responsive::-webkit-scrollbar-track { background: #f1f5f9; border-radius: 8px; }
        .table-responsive::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 8px; }
        .table-responsive::-webkit-scrollbar-thumb:hover { background: #94a3b8; }

        .custom-table { margin-bottom: 0; }
        .custom-table thead th {
            background: #f8fafc; color: #6b7280; font-size: 0.74rem; text-transform: uppercase; letter-spacing: .3px;
            font-weight: 700; padding: 11px 8px; border-bottom: 1.5px solid #eef2f6; white-space: nowrap;
        }
        .custom-table tbody td { padding: 11px 8px; font-size: 0.85rem; color: #111827; vertical-align: middle; border-bottom: 1px solid #f3f4f6; }
        .custom-table tbody tr:last-child td { border-bottom: none; }
        .custom-table tbody tr:hover { background: #f9fdff; }
        /* Bảng nhiều cột + chữ tiếng Việt dài thì trình duyệt bóp cột rồi ngắt
           chữ, mỗi dòng cao 3-4 hàng. Cho bảng một bề rộng tối thiểu rồi để
           khung ngoài (.table-responsive) cuộn ngang -- thà cuộn còn hơn đọc
           bảng vỡ. Ô dài cắt bằng "..." và giữ nguyên văn ở tooltip. */
        /* Xem ghi chú ở listcustomer.jsp: table-layout:fixed + chia % để bảng vừa
           đúng khung, mỗi dòng đúng một hàng chữ, phần thừa cắt bằng "...". */
        .custom-table { table-layout: fixed; min-width: 1440px; }
        /* min-width lớn hơn bề ngang khung: mỗi cột được rộng thoải mái,
           tên/tiêu đề dài phần lớn nằm gọn một dòng. Màn hình hẹp thì
           khung ngoài (.table-responsive) cho kéo ngang -- đổi lại lấy
           được khoảng thở cho chữ. */
        .custom-table th, .custom-table td { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
        /* Chia lại khi thêm cột Tiến độ: tổng vẫn 100%, phần thêm lấy bớt từ
           hai cột rộng nhất (Hợp đồng, Thời hạn) chứ không nhồi thêm vào tổng. */
        .custom-table th:nth-child(1), .custom-table td:nth-child(1) { width: 5%; }   /* STT */
        .custom-table th:nth-child(2), .custom-table td:nth-child(2) { width: 22%; }  /* Hợp đồng + khách */
        .custom-table th:nth-child(3), .custom-table td:nth-child(3) { width: 12%; }  /* Loại HĐ */
        .custom-table th:nth-child(4), .custom-table td:nth-child(4) { width: 14%; }  /* Thời hạn */
        .custom-table th:nth-child(5), .custom-table td:nth-child(5) { width: 13%; }  /* Trạng thái (lịch) */
        .custom-table th:nth-child(6), .custom-table td:nth-child(6) { width: 12%; }  /* Tiến độ */
        .custom-table th:nth-child(7), .custom-table td:nth-child(7) { width: 12%; }  /* Phụ trách */
        .custom-table th:nth-child(8), .custom-table td:nth-child(8) { width: 10%; }  /* Thao tác */
        .custom-table td.cell-wrap { white-space: normal; }
        .cell-2line { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
        .cell-sub { font-size: 0.74rem; color: #6b7280; line-height: 1.35; }
        .prov-tag { font-weight: 700; color: var(--primary-dark); }
        .term-cell { font-variant-numeric: tabular-nums; color: #4b5563; font-size: 0.76rem; }
        .cell-clip { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

        .stt-cell { color: #6b7280; font-weight: 600; font-size: 0.83rem; width: 48px; }
        .contract-code { font-weight: 700; color: var(--primary); font-size: 0.85rem; }
        .code-link { color: inherit; text-decoration: none; }
        .code-link:hover { text-decoration: underline; }
        .contract-title-link { color: #111827; font-weight: 600; text-decoration: none; line-height: 1.3; }
        .contract-title-link:hover { color: var(--primary); text-decoration: underline; }

        .type-badge { display: inline-block; padding: 3px 9px; border-radius: 20px; font-size: 0.68rem; font-weight: 600; background: #f3f4f6; color: #4b5563; }

        .progress-pill {
            display: inline-flex; align-items: center; padding: 3px 10px; border-radius: 20px;
            font-size: 0.7rem; font-weight: 600; white-space: nowrap; border: 1.5px dashed transparent;
        }
        .progress-draft  { background: #f3f4f6; color: #4b5563; border-color: #d1d5db; }
        .progress-signed { background: #e8f3ff; color: var(--primary-dark); border-color: var(--primary-light); }
        .progress-frozen { background: #eef7ee; color: #2f6b34; border-color: #a8cfaa; }

        .status-pill { display: inline-flex; align-items: center; gap: 4px; padding: 3px 8px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; white-space: nowrap; }
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
            width: 28px; height: 28px; border-radius: 8px; border: none;
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
        <%-- kind do ContractController set (sell | buy). CHÚ Ý: mọi link dưới
             đây phải mang ĐỦ CẢ BA -- kind, year, period. Thiếu kind thì bấm
             sang trang 2 rơi về hợp đồng bán; thiếu year/period thì mất bộ lọc
             năm/quý/tháng, còn khó phát hiện hơn vì trang vẫn hiện dữ liệu. --%>
        <c:set var="activeContractKind" value="${kind}" scope="request"/>
        <c:set var="kindLabel" value="${kind == 'buy' ? 'Hợp đồng mua' : 'Hợp đồng bán'}"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>${kindLabel}</h2>
                <p>Quản lý toàn bộ hợp đồng cung cấp và thi công thiết bị viễn thông</p>
            </div>
            <div class="header-actions">
                <%-- Xuất Excel là thao tác ĐỌC: chỉ lấy đúng dữ liệu vai trò này vốn đã
                     xem được trên màn hình, đổi sang dạng file. Nên KHÔNG khoá theo
                     canManage -- xem PERMISSIONS.md. --%>
                <a href="${pageContext.request.contextPath}/contract?action=exportExcel&kind=${kind}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&progress=${fn:escapeXml(progressFilter)}&type=${fn:escapeXml(typeFilter)}&provinceId=${provinceFilter}&year=${yearFilter}&period=${periodFilter}" class="btn-outline-action"><i class="fa-solid fa-file-excel"></i> Xuất Excel</a>
                <%-- Nhập PDF thì ngược lại: nó TẠO hợp đồng mới, nên vẫn khoá. --%>
                <c:if test="${canManage}">
                    <a href="${pageContext.request.contextPath}/contract?action=importForm" class="btn-outline-action"><i class="fa-solid fa-file-pdf"></i> Nhập PDF</a>
                    <a href="${pageContext.request.contextPath}/contract?action=new&kind=${kind}" class="btn-add"><i class="fa-solid fa-plus"></i> Tạo hợp đồng</a>
                </c:if>
                <%-- Cùng lý do với màn Khách hàng: cấp dưới mất nút tạo/sửa
                     nhưng phải có đường gửi yêu cầu lên cấp trên. --%>
                <c:if test="${!canManage && sessionScope.currentUser.subordinate}">
                    <a href="${pageContext.request.contextPath}/changerequest?action=new&resourceType=Hợp đồng&intent=Tạo mới" class="btn-outline-action"><i class="fa-solid fa-paper-plane"></i> Gửi yêu cầu</a>
                </c:if>
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
            <%-- Lọc xong ở lại đúng mục con; year/period đã có ô riêng trong form. --%>
            <input type="hidden" name="kind" value="${kind}">
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
            <%-- Trục TIẾN ĐỘ, đứng riêng với trục lịch ở trên. Chọn "Đã ký" rồi
                 thêm trạng thái lịch "Đã hết hạn" sẽ ra đúng danh sách hết hạn
                 mà chưa thanh lý — hàng đợi việc còn tồn. --%>
            <select id="filterProgress" name="progress">
                <option value="">Tất cả tiến độ</option>
                <option value="Nháp" ${progressFilter == 'Nháp' ? 'selected' : ''}>Nháp (chưa ký)</option>
                <option value="Đã ký" ${progressFilter == 'Đã ký' ? 'selected' : ''}>Đã ký</option>
                <option value="Đã thanh lý" ${progressFilter == 'Đã thanh lý' ? 'selected' : ''}>Đã thanh lý</option>
                <option value="Chấm dứt sớm" ${progressFilter == 'Chấm dứt sớm' ? 'selected' : ''}>Chấm dứt sớm</option>
            </select>
            <select id="filterType" name="type">
                <option value="">Tất cả loại hợp đồng</option>
                <option value="Cung cấp thiết bị" ${typeFilter == 'Cung cấp thiết bị' ? 'selected' : ''}>Cung cấp thiết bị</option>
                <option value="Thi công lắp đặt" ${typeFilter == 'Thi công lắp đặt' ? 'selected' : ''}>Thi công lắp đặt</option>
                <option value="Bảo trì bảo dưỡng" ${typeFilter == 'Bảo trì bảo dưỡng' ? 'selected' : ''}>Bảo trì bảo dưỡng</option>
            </select>
            <select id="filterProvince" name="provinceId">
                <option value="">Tất cả tỉnh địa bàn</option>
                <c:forEach var="province" items="${provinceList}">
                    <option value="${province.provinceId}" ${provinceFilter == province.provinceId ? 'selected' : ''}>${fn:escapeXml(province.shortName)}</option>
                </c:forEach>
            </select>
            <select id="filterYear" name="year">
                <option value="">Mọi thời điểm</option>
                <c:forEach var="y" items="${yearList}">
                    <option value="${y}" ${yearFilter == y ? 'selected' : ''}>Năm ${y}</option>
                </c:forEach>
            </select>
            <select id="filterPeriod" name="period">
                <option value="">Cả năm</option>
                <c:forEach var="q" begin="1" end="4">
                    <c:set var="qVal" value="q${q}"/>
                    <option value="${qVal}" ${periodFilter == qVal ? 'selected' : ''}>Quý ${q}</option>
                </c:forEach>
                <c:forEach var="m" begin="1" end="12">
                    <c:set var="mVal" value="m${m}"/>
                    <option value="${mVal}" ${periodFilter == mVal ? 'selected' : ''}>Tháng ${m}</option>
                </c:forEach>
            </select>
        </form>

        <!-- ===== Bảng danh sách ===== -->
        <div class="table-card card-box">
            <div class="table-responsive">
                <table class="table custom-table" id="contractTable">
                    <thead>
                        <tr>
                            <th>STT</th>
                            <th>Hợp đồng</th>
                            <th>Loại HĐ</th>
                            <th>Thời hạn</th>
                            <th>Trạng thái</th>
                            <th>Tiến độ</th>
                            <th>Phụ trách</th>
                            <th class="text-end">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="contractTableBody">
                        <c:forEach var="contract" items="${contractList}" varStatus="row">
                            <tr>
                                <%-- STT theo vị trí toàn danh sách: trang 2 bắt đầu từ 11, không quay lại 1. --%>
                                <td class="stt-cell">${(currentPage - 1) * pageSize + row.index + 1}</td>
                                <%-- Ô hai dòng: tiêu đề hợp đồng đậm ở trên, "tỉnh · khách hàng"
                                     chữ nhỏ ở dưới. Gộp lại thì tiêu đề đủ chỗ hiện trọn vẹn thay
                                     vì ba cột cùng bị cắt cụt. --%>
                                <td class="cell-wrap">
                                    <div class="cell-2line">
                                        <a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}" class="contract-title-link">${fn:escapeXml(contract.title)}</a>
                                        <span class="cell-sub">
                                            <c:choose>
                                                <c:when test="${contract.enterprise != null}"><c:if test="${contract.enterprise.address.district.province != null}"><span class="prov-tag">${fn:escapeXml(contract.enterprise.address.district.province.shortName)}</span> &middot; </c:if>${fn:escapeXml(contract.enterprise.enterpriseName)}</c:when>
                                                <c:otherwise>&mdash;</c:otherwise>
                                            </c:choose>
                                        </span>
                                    </div>
                                </td>
                                <td><span class="type-badge">${fn:escapeXml(contract.contractType)}</span></td>
                                <%-- Hai cột ngày gộp thành một khoảng thời hạn, năm rút về 2 chữ số:
                                     "10/08/26 → 14/08/27" vừa một dòng mà vẫn đủ nghĩa. --%>
                                <%-- Bản nháp chưa có ngày ký; hiện "chưa ký" thay vì để
                                     trống, để người đọc phân biệt với lỗi dữ liệu. --%>
                                <td class="term-cell">
                                    <c:choose>
                                        <c:when test="${contract.signingDate != null}"><fmt:formatDate value="${contract.signingDate}" pattern="dd/MM/yy"/></c:when>
                                        <c:otherwise><span style="color:#9ca3af;">chưa ký</span></c:otherwise>
                                    </c:choose>
                                    &rarr;
                                    <c:choose>
                                        <c:when test="${contract.endDate != null}"><fmt:formatDate value="${contract.endDate}" pattern="dd/MM/yy"/></c:when>
                                        <c:otherwise><span style="color:#9ca3af;">chưa chốt</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${contract.status == 'Đang hiệu lực'}"><span class="status-pill status-active"><span class="dot"></span>Đang hiệu lực</span></c:when>
                                        <c:when test="${contract.status == 'Sắp hết hạn'}"><span class="status-pill status-soon"><span class="dot"></span>Sắp hết hạn</span></c:when>
                                        <c:when test="${contract.status == 'Đã hết hạn'}"><span class="status-pill status-expired"><span class="dot"></span>Đã hết hạn</span></c:when>
                                        <c:otherwise><span class="status-pill status-draft"><span class="dot"></span>Chưa hiệu lực</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <%-- Cột riêng, KHÔNG gộp vào cột Trạng thái bên trái:
                                     hai trục lệch nhau ở cả hai chiều, và chính chỗ
                                     lệch mới là thông tin (hết hạn mà chưa thanh lý). --%>
                                <td>
                                    <span class="progress-pill progress-${contract.draft ? 'draft' : (contract.frozen ? 'frozen' : 'signed')}">
                                        ${fn:escapeXml(contract.progressStatus)}
                                    </span>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${contract.owner != null}">${fn:escapeXml(contract.owner.fullName)}</c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <div class="action-icons">
                                        <button class="act-view" title="Xem chi tiết" onclick="location.href='${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}'"><i class="fa-regular fa-eye"></i></button>
                                        <c:if test="${canManage}">
                                            <button class="act-edit" title="Sửa" onclick="location.href='${pageContext.request.contextPath}/contract?action=edit&id=${contract.contractId}'"><i class="fa-solid fa-pen"></i></button>
                                            <%-- Không còn nút xoá ở danh sách. Hợp đồng đã ký không
                                                 xoá được theo nghiệp vụ; thứ còn lại là huỷ một bản
                                                 ghi NHẬP NHẦM -- việc của Admin, bắt buộc có lý do,
                                                 và nằm ở trang chi tiết nơi nhìn rõ mình đang huỷ cái
                                                 gì. Điều kiện "Chưa hiệu lực" cũ ở đây cũng sai: nó
                                                 tính theo lịch, nên hợp đồng ký hôm qua mà hiệu lực
                                                 tháng sau vẫn xoá được. --%>
                                        </c:if>
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
                        <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/contract?action=list&kind=${kind}&page=${currentPage - 1}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&progress=${fn:escapeXml(progressFilter)}&type=${fn:escapeXml(typeFilter)}&provinceId=${provinceFilter}&year=${yearFilter}&period=${periodFilter}">Trước</a></li>
                        <c:forEach begin="1" end="${totalPages}" var="p">
                            <li class="page-item ${p == currentPage ? 'active' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/contract?action=list&kind=${kind}&page=${p}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&progress=${fn:escapeXml(progressFilter)}&type=${fn:escapeXml(typeFilter)}&provinceId=${provinceFilter}&year=${yearFilter}&period=${periodFilter}">${p}</a></li>
                        </c:forEach>
                        <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/contract?action=list&kind=${kind}&page=${currentPage + 1}&keyword=${fn:escapeXml(keyword)}&status=${fn:escapeXml(statusFilter)}&progress=${fn:escapeXml(progressFilter)}&type=${fn:escapeXml(typeFilter)}&provinceId=${provinceFilter}&year=${yearFilter}&period=${periodFilter}">Sau</a></li>
                    </ul>
                </nav>
            </div>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Kéo chuột ngay trên bảng để cuộn ngang, không cần rê xuống tận thanh
        // cuộn ở cuối bảng. Bỏ qua khi điểm bắt đầu là link/nút/ô nhập -- nếu
        // không thì bấm "Xem chi tiết" cũng bị tính là kéo.
        (function enableDragScroll() {
            var wrap = document.querySelector('.table-responsive');
            if (!wrap) { return; }
            var dragging = false, startX = 0, startScroll = 0, moved = false;

            wrap.addEventListener('mousedown', function (e) {
                if (e.button !== 0 || e.target.closest('a, button, input, select, label')) { return; }
                dragging = true;
                moved = false;
                startX = e.pageX;
                startScroll = wrap.scrollLeft;
                wrap.classList.add('is-dragging');
            });
            wrap.addEventListener('mousemove', function (e) {
                if (!dragging) { return; }
                var dx = e.pageX - startX;
                if (Math.abs(dx) > 3) { moved = true; }
                if (moved) {
                    wrap.scrollLeft = startScroll - dx;
                    e.preventDefault();
                }
            });
            // Bắt mouseup ở window: thả chuột ngoài bảng vẫn phải kết thúc kéo,
            // không thì bảng dính theo con trỏ.
            window.addEventListener('mouseup', function () {
                dragging = false;
                wrap.classList.remove('is-dragging');
            });
            // Lăn chuột ngang (trackpad / Shift+lăn) cuộn bảng thay vì cuộn trang.
            wrap.addEventListener('wheel', function (e) {
                if (e.deltaX === 0 && !e.shiftKey) { return; }
                var before = wrap.scrollLeft;
                wrap.scrollLeft += (e.deltaX !== 0 ? e.deltaX : e.deltaY);
                if (wrap.scrollLeft !== before) { e.preventDefault(); }
            }, { passive: false });
        })();

        // Tự động submit lại form lọc khi đổi trạng thái / loại hợp đồng
        document.getElementById('filterStatus').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterProgress').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterType').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterProvince').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterYear').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
        document.getElementById('filterPeriod').addEventListener('change', function () { document.getElementById('filterForm').submit(); });
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
