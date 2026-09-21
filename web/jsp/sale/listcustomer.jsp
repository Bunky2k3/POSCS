<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần đặt các request attribute sau trước khi forward tới trang này:
      - customerList : List<poscs.model.Enterprise>  (mỗi Enterprise nên có sẵn .address.district.province và .accountOwner đã join)
      - userList      : List<poscs.model.User>        (nhân viên vai Sales, để đổ dropdown lọc "Người phụ trách")
      - provinceList  : List<poscs.model.Province>    (18 tỉnh địa bàn chi nhánh, để đổ dropdown lọc "Tỉnh/Thành")
      - currentPage, totalPages, totalCount : thông tin phân trang (BR-12)
      - keyword, typeFilter, assigneeFilter, provinceFilters : giá trị filter hiện tại (để giữ lại lúc submit lại form tìm kiếm)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách khách hàng - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container {
            max-width: 1440px;
            margin: 28px auto;
            padding: 0 24px 32px;
        }

        .page-header-row {
            display: flex; justify-content: space-between; align-items: flex-start;
            margin-bottom: 22px; flex-wrap: wrap; gap: 14px;
        }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        /* Dải nói rõ danh sách đang bị thu hẹp tới đâu. BẮT BUỘC phải có: con số
           "tổng số N" bên dưới giờ là tổng CỦA PHẠM VI chứ không phải của toàn chi
           nhánh -- không nói ra thì người dùng tưởng mất dữ liệu. Lấy đúng bảng màu
           của .scope-note trên Dashboard để hai chỗ đọc ra cùng một ý. */
        .scope-note {
            display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
            background: #eaf6ff; border: 1px solid #cfe8fb; border-radius: 10px;
            padding: 10px 16px; margin-bottom: 16px;
            font-size: 0.84rem; color: var(--primary-dark);
        }
        .scope-note i { color: var(--primary); }
        .scope-note .sep { color: #9ca3af; }
        .scope-note a { color: var(--primary); font-weight: 600; text-decoration: none; }
        .scope-note a:hover { text-decoration: underline; }
        .scope-note .spacer { margin-left: auto; }


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
            display: flex; flex-wrap: wrap; gap: 10px; align-items: center;
        }
        .search-input-wrap {
            position: relative; flex: 1 1 220px; min-width: 190px;
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
        /* min-width 180px x nhiều ô là tràn hàng ngay ở màn hình 1366px --
           thu về 150px và cho phép co lại thì cả thanh lọc nằm gọn một hàng. */
        .filter-bar select {
            padding: 10px 12px; border-radius: 10px; border: 1px solid #e5e7eb;
            background: #f9fafb; font-size: 0.86rem;
            flex: 0 1 auto; min-width: 150px; max-width: 200px;
        }
        .filter-bar select:focus { outline: none; border-color: var(--primary-light); }
        /* Nút mở bảng chọn tỉnh. Khung và bảng bên trong nằm ở appshell.css để
           Dashboard và hai danh sách dùng chung; ở đây chỉ sơn cho khớp với các
           ô select đứng cạnh -- Dashboard sơn nền trắng có đổ bóng, thanh lọc
           này nền xám nhạt. */
        .filter-bar .pop-btn {
            padding: 10px 12px; border-radius: 10px; border: 1px solid #e5e7eb;
            background: #f9fafb; font-size: 0.86rem; color: #374151;
            flex: 0 1 auto; min-width: 170px; max-width: 220px;
        }
        .filter-bar .pop-btn:focus { outline: none; border-color: var(--primary-light); }
        .filter-bar .pop-btn > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
        <%-- Bảng chọn tỉnh giữ mặc định của appshell.css là neo mép PHẢI: ở
             trang này nút tỉnh là ô CUỐI hàng lọc, neo trái thì bảng rộng
             320px chạy thẳng ra ngoài mép phải trang (đã dính đúng thế). Màn
             hẹp hơn 991px thì appshell tự lật sang neo trái, vì lúc đó hàng
             lọc xuống dòng và các ô dạt về bên trái. --%>
        /* Dải phạm vi chuyển vào TRONG thẻ lọc: nó nói cùng một chuyện với ô
           Phạm vi ngay trên nó, để tách ra ngoài thì lời giải thích và cái công
           tắc nằm ở hai chỗ khác nhau. */
        .filter-bar .scope-note { flex: 1 1 100%; margin-bottom: 0; }
        /* Hàng địa bàn chiếm trọn một dòng, khác Dashboard (ở đó nó đứng cạnh
           ba ô lọc). Lý do: thanh lọc ở đây đã có bốn ô, chèn thêm chip vào
           cùng dòng là chúng bị bóp lại còn vài chục pixel. Kiểu dáng của
           .my-prov/.chip nằm ở appshell.css, dùng chung -- chỉ phần bố cục
           mới theo từng trang. */
        .filter-bar .my-prov { flex: 1 1 100%; }

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
            background: #f8fafc; color: #6b7280; font-size: 0.74rem;
            text-transform: uppercase; letter-spacing: .3px; font-weight: 700;
            padding: 11px 8px; border-bottom: 1.5px solid #eef2f6; white-space: nowrap;
        }
        .custom-table tbody td {
            padding: 11px 8px; font-size: 0.85rem; color: #111827;
            vertical-align: middle; border-bottom: 1px solid #f3f4f6;
        }
        .custom-table tbody tr:last-child td { border-bottom: none; }
        .custom-table tbody tr:hover { background: #f9fdff; }

        /* 9 cột chữ tiếng Việt trong ~980px: để trình duyệt tự chia thì nó bóp
           cột rồi ngắt chữ, mỗi dòng cao 3-4 hàng. table-layout:fixed + chia %
           cho bảng vừa đúng bề ngang khung, mỗi dòng đúng một hàng chữ, phần
           thừa cắt bằng "..." (nguyên văn vẫn còn ở tooltip). Dưới 900px thì
           khung ngoài cuộn ngang thay vì bóp tiếp. */
        .custom-table { table-layout: fixed; min-width: 1440px; }
        /* min-width lớn hơn bề ngang khung: mỗi cột được rộng thoải mái,
           tên/tiêu đề dài phần lớn nằm gọn một dòng. Màn hình hẹp thì
           khung ngoài (.table-responsive) cho kéo ngang -- đổi lại lấy
           được khoảng thở cho chữ. */
        .custom-table th, .custom-table td {
            white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
        }
        .custom-table th:nth-child(1), .custom-table td:nth-child(1) { width: 5%; }   /* STT */
        .custom-table th:nth-child(2), .custom-table td:nth-child(2) { width: 26%; }  /* Khách hàng + email */
        .custom-table th:nth-child(3), .custom-table td:nth-child(3) { width: 14%; }  /* Loại KH */
        .custom-table th:nth-child(4), .custom-table td:nth-child(4) { width: 10%; }  /* SĐT */
        .custom-table th:nth-child(5), .custom-table td:nth-child(5) { width: 20%; }  /* Địa bàn */
        .custom-table th:nth-child(6), .custom-table td:nth-child(6) { width: 14%; }  /* Phụ trách */
        .custom-table th:nth-child(7), .custom-table td:nth-child(7) { width: 11%; }  /* Thao tác */
        /* Ô hai dòng: dòng chính đậm, dòng phụ chữ nhỏ xám. Dòng chính được
           phép xuống dòng để KHÔNG bao giờ phải cắt bằng "..."; chỉ dòng phụ
           mới cắt, vì email/địa chỉ chi tiết không đáng chiếm thêm một hàng. */
        .custom-table td.cell-wrap { white-space: normal; }
        .cell-2line { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
        .cell-sub { font-size: 0.74rem; color: #6b7280; line-height: 1.35; }
        .prov-tag { font-weight: 700; color: var(--primary-dark); }
        .cell-clip {
            display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
        }

        .stt-cell { color: #6b7280; font-weight: 600; font-size: 0.83rem; width: 48px; }
        .customer-code {
            font-weight: 700; color: var(--primary); font-size: 0.85rem;
        }
        .code-link { color: inherit; text-decoration: none; }
        .code-link:hover { text-decoration: underline; }
        .customer-name-cell { display: flex; align-items: center; gap: 9px; min-width: 0; }
        .customer-logo {
            width: 28px; height: 28px; border-radius: 8px; overflow: hidden; flex-shrink: 0;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center; font-size: 0.85rem;
        }
        .customer-logo img { width: 100%; height: 100%; object-fit: cover; }
        .customer-name-link {
            color: #111827; font-weight: 600; text-decoration: none;
            display: block; min-width: 0; line-height: 1.3;
        }
        .customer-name-link:hover { color: var(--primary); text-decoration: underline; }

        .type-badge {
            display: inline-block; padding: 3px 9px; border-radius: 20px;
            font-size: 0.68rem; font-weight: 600;
            background: #eaf6ff; color: var(--primary-dark);
        }

        .action-icons { display: flex; gap: 6px; justify-content: flex-end; }
        .action-icons button {
            width: 28px; height: 28px; border-radius: 8px; border: none;
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
        <%-- kind do CustomerController set (buyer | supplier). Sidebar dùng để
             tô sáng đúng mục con; các link dưới đây phải mang nó theo, nếu
             không thì bấm sang trang 2 hay bấm Xuất Excel là rơi về danh sách
             khách mua. --%>
        <c:set var="activeCustomerKind" value="${kind}" scope="request"/>
        <c:set var="kindLabel" value="${kind == 'supplier' ? 'Nhà cung cấp' : 'Khách hàng mua'}"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>${kindLabel}</h2>
                <p>Quản lý thông tin khách hàng doanh nghiệp đối tác</p>
            </div>
            <div class="header-actions">
                <%-- Xuất Excel là thao tác ĐỌC: chỉ lấy đúng dữ liệu vai trò này vốn đã
                     xem được trên màn hình, đổi sang dạng file. Nên KHÔNG khoá theo
                     canManage -- xem PERMISSIONS.md. --%>
                <a href="${pageContext.request.contextPath}/customer?action=exportExcel&kind=${kind}&keyword=${fn:escapeXml(keyword)}&type=${fn:escapeXml(typeFilter)}&assigneeId=${assigneeFilter}${provinceQuery}" class="btn-outline-action"><i class="fa-solid fa-file-excel"></i> Xuất Excel</a>
                <c:if test="${canManage}">
                    <a href="${pageContext.request.contextPath}/customer?action=new&kind=${kind}" class="btn-add"><i class="fa-solid fa-plus"></i> Thêm khách hàng</a>
                </c:if>
                <%-- Cấp dưới không có nút "Thêm" vì không được ghi (V16), nhưng
                     không được để họ cụt đường: đây là lối gửi yêu cầu lên cấp
                     trên. Điều kiện là CÓ CẤP TRÊN chứ không phải "không có
                     canManage" -- người chỉ View only vì vai trò (Kỹ thuật,
                     CSKH) thì vốn không dính gì tới cây tổ chức, hiện nút này
                     ra chỉ làm họ bối rối. --%>
                <%-- Nút "Gửi yêu cầu" đã ẩn cùng mục Yêu cầu thay đổi (21/09/2026)
                     -- xem ghi chú ở sidebar.jsp. --%>
            </div>
        </div>

        <!-- ===== Bộ lọc / tìm kiếm ===== -->
        <form class="filter-bar card-box" method="GET" action="${pageContext.request.contextPath}/customer" id="filterForm">
            <input type="hidden" name="action" value="list">
            <%-- Lọc xong phải ở lại đúng danh sách vừa đứng, không rơi về khách mua. --%>
            <input type="hidden" name="kind" value="${kind}">
            <div class="search-input-wrap">
                <i class="fa-solid fa-magnifying-glass"></i>
                <input type="text" id="searchInput" name="keyword" value="${fn:escapeXml(keyword)}" placeholder="Tìm theo mã KH, tên, số điện thoại...">
            </div>
            <%-- Phạm vi người phụ trách. Trước đây là một LINK "Xem toàn chi
                 nhánh" ở dải phía trên, tách hẳn khỏi các ô lọc; giờ là một ô
                 như mọi ô khác, giống Dashboard. Chỉ hiện khi việc thu hẹp có
                 nghĩa -- người không có cấp dưới lẫn địa bàn thì hai lựa chọn
                 cho ra cùng một danh sách. --%>
            <c:if test="${viewNarrowed or viewFilter == 'all'}">
                <select id="filterView" name="view">
                    <option value="mine" ${viewFilter != 'all' ? 'selected' : ''}>Của tôi</option>
                    <option value="all" ${viewFilter == 'all' ? 'selected' : ''}>Toàn chi nhánh</option>
                </select>
            </c:if>
            <select id="filterType" name="type">
                <option value="">Tất cả loại khách hàng</option>
                <%-- Danh sách khác nhau theo vai: khách mua phân theo họ là nhà
                     mạng / nhà thầu / đại lý, còn nhà cung cấp phân theo họ sản
                     xuất / nhập khẩu / phân phối. Xem CustomerController. --%>
                <c:forEach var="ct" items="${customerTypeOptions}">
                    <option value="${fn:escapeXml(ct)}" ${typeFilter == ct ? 'selected' : ''}>${fn:escapeXml(ct)}</option>
                </c:forEach>
            </select>
            <select id="filterAssignee" name="assigneeId">
                <%-- Nhãn nói rõ "chính": bộ lọc chỉ soi cột phụ trách chính,
                     chọn một người sẽ KHÔNG kéo theo khách họ chỉ đứng hỗ trợ. --%>
                <option value="">Tất cả người phụ trách chính</option>
                <c:forEach var="staff" items="${userList}">
                    <option value="${staff.userId}" ${assigneeFilter == staff.userId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                </c:forEach>
            </select>
            <%-- Nhà cung cấp KHÔNG lọc theo tỉnh. Tỉnh ở đây là địa bàn BÁN
                 HÀNG -- nó quyết định ai cầm khách nào (user_provinces); bên bán
                 hàng cho mình thì không chia theo địa bàn. Controller cũng bỏ
                 luôn tham số đó với vai này, nên URL còn sót provinceId cũng
                 không âm thầm cắt mất kết quả. --%>
            <%-- Bảng tích NHIỀU tỉnh, dùng chung mã với Dashboard và danh sách
                 hợp đồng (appshell.css + appshell.js). Trước đây là ô chọn MỘT
                 tỉnh: người phụ trách năm tỉnh phải mở năm lượt trang mới xem
                 hết địa bàn của mình, và không lượt nào cho ra tổng số đúng.

                 KHÔNG có cờ "provinceSet" như Dashboard: ở đây bỏ tích hết
                 nghĩa là không lọc tỉnh, y như ô cũ để "Tất cả" -- việc thu hẹp
                 theo địa bàn đã do phạm vi (ListScope) lo rồi. --%>
            <c:if test="${showProvinceFilter}">
                <div class="prov-pop" data-prov-pop>
                    <button type="button" class="pop-btn" data-popover-toggle
                            aria-expanded="false" aria-controls="provPanel">
                        <i class="fa-solid fa-location-dot" style="color:#9ca3af;"></i>
                        <span>
                            <c:choose>
                                <c:when test="${empty provinceFilters}">Tất cả tỉnh địa bàn</c:when>
                                <c:when test="${fn:length(provinceFilters) == 1}">1 tỉnh đang chọn</c:when>
                                <c:otherwise>${fn:length(provinceFilters)} tỉnh đang chọn</c:otherwise>
                            </c:choose>
                        </span>
                        <i class="fa-solid fa-chevron-down"></i>
                    </button>
                    <div class="prov-panel" id="provPanel" data-popover-panel>
                        <div class="prov-actions">
                            <button type="button" data-prov-all>Chọn tất cả</button>
                            <button type="button" data-prov-none>Bỏ hết</button>
                            <c:if test="${not empty myProvinces}">
                                <button type="button" data-prov-mine>Địa bàn của tôi</button>
                            </c:if>
                            <button type="submit" class="prov-apply">Áp dụng</button>
                        </div>
                        <c:forEach var="province" items="${provinceList}">
                            <%-- myProvinces là List<Province> nên không dùng contains
                                 thẳng được; đánh dấu bằng vòng lặp con. --%>
                            <c:set var="isMine" value="false"/>
                            <c:forEach var="mp" items="${myProvinces}">
                                <c:if test="${mp.provinceId == province.provinceId}"><c:set var="isMine" value="true"/></c:if>
                            </c:forEach>
                            <label class="prov-row ${isMine ? 'mine' : ''}">
                                <input type="checkbox" name="provinceId" value="${province.provinceId}"
                                       data-mine="${isMine}"
                                       <c:if test="${provinceFilters.contains(province.provinceId)}">checked</c:if>>
                                <span><c:choose>
                                    <c:when test="${isMine}"><strong>${fn:escapeXml(province.shortName)}</strong></c:when>
                                    <c:otherwise>${fn:escapeXml(province.shortName)}</c:otherwise>
                                </c:choose></span>
                                <c:if test="${isMine}"><span class="mine-tag">của bạn</span></c:if>
                            </label>
                        </c:forEach>
                    </div>
                </div>
            </c:if>

            <%-- Địa bàn của người đang xem, GỌI TÊN chứ không chỉ đếm.
                 Cùng mã và cùng lẽ với Dashboard: nó phải đọc được KHÔNG CẦN
                 BẤM, vì nó trả lời "vì sao danh sách của tôi khác của đồng
                 nghiệp". Nằm trong bảng tích thì chỉ ai nghĩ tới việc mở ra
                 mới thấy, mà người cần câu trả lời đó thường chưa biết mình
                 đang thắc mắc chuyện gì.

                 Dải "Đang xem..." ngay dưới vẫn giữ con số: hai câu khác
                 nhau -- hàng này nói BẠN phụ trách tỉnh nào, dải kia nói danh
                 sách ĐANG hiển thị theo phạm vi nào.

                 Theo showProvinceFilter: ở mục Nhà cung cấp không có bảng
                 chọn tỉnh nào cả (địa bàn là chuyện của bên mua), nên một
                 hàng "Bạn phụ trách..." đứng cạnh chỗ trống chỉ gây khó hiểu. --%>
            <c:if test="${showProvinceFilter and not empty myProvinces}">
                <div class="my-prov">
                    <span class="lbl"><i class="fa-solid fa-map-location-dot"></i> Bạn phụ trách:</span>
                    <c:forEach var="mp" items="${myProvinces}">
                        <span class="chip">${fn:escapeXml(mp.shortName)}</span>
                    </c:forEach>
                </div>
            </c:if>

            <%-- Dải phạm vi, nằm TRONG thẻ lọc ngay dưới ô Phạm vi đã đổi nó. --%>
            <c:if test="${viewNarrowed or viewFilter == 'all'}">
                <div class="scope-note">
                    <i class="fa-solid fa-user-check"></i>
                    <c:choose>
                        <c:when test="${viewNarrowed and viewProvinceCount > 0}">
                            Đang xem <strong>khách bạn phụ trách và khách trong ${viewProvinceCount} tỉnh địa bàn của bạn</strong>.
                        </c:when>
                        <c:when test="${viewNarrowed}">
                            Đang xem <strong>khách bạn phụ trách</strong>.
                            <span class="sep">&middot;</span>
                            <span style="color:#6b7280;">Bạn chưa được giao tỉnh địa bàn nào</span>
                        </c:when>
                        <c:otherwise>Đang xem <strong>toàn chi nhánh</strong>.</c:otherwise>
                    </c:choose>
                </div>
            </c:if>
        </form>

        <!-- ===== Bảng danh sách ===== -->
        <div class="table-card card-box">
            <div class="table-responsive">
                <table class="table custom-table" id="customerTable">
                    <thead>
                        <tr>
                            <th>STT</th>
                            <th>Khách hàng</th>
                            <th>Loại KH</th>
                            <th>Số điện thoại</th>
                            <th>Địa bàn</th>
                            <th>Phụ trách chính</th>
                            <th class="text-end">Thao tác</th>
                        </tr>
                    </thead>
                    <tbody id="customerTableBody">
                        <c:forEach var="customer" items="${customerList}" varStatus="row">
                            <tr>
                                <%-- STT tính theo vị trí toàn danh sách, không phải trong trang:
                                     trang 2 phải bắt đầu từ 11 chứ không quay lại 1. --%>
                                <td class="stt-cell">${(currentPage - 1) * pageSize + row.index + 1}</td>
                                <%-- O hai dong: ten khach dam o tren, email chu nho xam o duoi.
                                     Gop email vao day thi cot ten du cho hien tron ven, khong
                                     phai cat bang "..." nhu khi email chiem mot cot rieng. --%>
                                <td class="cell-wrap">
                                    <div class="customer-name-cell">
                                        <div class="customer-logo">
                                            <c:choose>
                                                <c:when test="${not empty customer.logoUrl}"><img src="${pageContext.request.contextPath}${fn:escapeXml(customer.logoUrl)}" alt="Logo"></c:when>
                                                <c:otherwise><i class="fa-solid fa-building"></i></c:otherwise>
                                            </c:choose>
                                        </div>
                                        <div class="cell-2line">
                                            <a href="${pageContext.request.contextPath}/customer?action=view&id=${customer.enterpriseId}&kind=${kind}" class="customer-name-link">${fn:escapeXml(customer.enterpriseName)}</a>
                                            <span class="cell-sub">${fn:escapeXml(customer.email)}</span>
                                        </div>
                                    </div>
                                </td>
                                <td><span class="type-badge">${fn:escapeXml(customer.customerType)}</span></td>
                                <td class="nowrap">${fn:escapeXml(customer.phone)}</td>
                                <%-- Cung hai dong: tinh in dam o tren (thu nguoi dung quet mat
                                     khi quan ly theo dia ban), dia chi chi tiet chu nho o duoi. --%>
                                <td class="cell-wrap">
                                    <c:choose>
                                        <c:when test="${customer.address != null}">
                                            <div class="cell-2line">
                                                <span class="prov-tag"><c:choose><c:when test="${customer.address.district.province != null}">${fn:escapeXml(customer.address.district.province.shortName)}</c:when><c:otherwise>Chưa xác định</c:otherwise></c:choose></span>
                                                <span class="cell-sub" title="${fn:escapeXml(customer.address.fullAddress)}">${fn:escapeXml(customer.address.streetAndLocalName)}<c:if test="${customer.address.district != null}">, ${fn:escapeXml(customer.address.district.shortName)}</c:if></span>
                                            </div>
                                        </c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td class="nowrap">
                                    <c:choose>
                                        <c:when test="${customer.accountOwner != null}">${fn:escapeXml(customer.accountOwner.fullName)}</c:when>
                                        <c:otherwise>&mdash;</c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <div class="action-icons">
                                        <button class="act-view" title="Xem chi tiết" onclick="location.href='${pageContext.request.contextPath}/customer?action=view&id=${customer.enterpriseId}&kind=${kind}'"><i class="fa-regular fa-eye"></i></button>
                                        <c:if test="${canManage}">
                                            <button class="act-edit" title="Sửa" onclick="location.href='${pageContext.request.contextPath}/customer?action=edit&id=${customer.enterpriseId}'"><i class="fa-solid fa-pen"></i></button>
                                            <button class="act-delete" title="Xóa" onclick="openDeleteModal(${customer.enterpriseId}, '${fn:escapeXml(customer.enterpriseName)}')"><i class="fa-solid fa-trash"></i></button>
                                        </c:if>
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
                        <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/customer?action=list&kind=${kind}&page=${currentPage - 1}&keyword=${fn:escapeXml(keyword)}&type=${fn:escapeXml(typeFilter)}&assigneeId=${assigneeFilter}${provinceQuery}">Trước</a></li>
                        <c:forEach begin="1" end="${totalPages}" var="p">
                            <li class="page-item ${p == currentPage ? 'active' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/customer?action=list&kind=${kind}&page=${p}&keyword=${fn:escapeXml(keyword)}&type=${fn:escapeXml(typeFilter)}&assigneeId=${assigneeFilter}${provinceQuery}">${p}</a></li>
                        </c:forEach>
                        <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/customer?action=list&kind=${kind}&page=${currentPage + 1}&keyword=${fn:escapeXml(keyword)}&type=${fn:escapeXml(typeFilter)}&assigneeId=${assigneeFilter}${provinceQuery}">Sau</a></li>
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

        // Tự động submit lại form lọc khi đổi một ô select.
        // Gắn theo DANH SÁCH id: ô Phạm vi chỉ có khi việc thu hẹp có nghĩa, mà
        // gắn sự kiện lên null thì vỡ cả đoạn script phía sau, kể cả các bộ lọc
        // khác. Ô TỈNH không có ở đây: nó là bảng tích, mã dùng chung ở
        // appshell.js chờ nút "Áp dụng" mới gửi -- tích ba tỉnh mà gửi ngay là
        // hai lượt tải trang thừa.
        ['filterType', 'filterAssignee', 'filterView'].forEach(function (id) {
            var el = document.getElementById(id);
            if (el) { el.addEventListener('change', function () { document.getElementById('filterForm').submit(); }); }
        });
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
