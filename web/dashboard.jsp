<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Tổng quan - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1280px; margin: 28px auto; padding: 0 24px 32px; }

        .welcome-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 12px; }
        .welcome-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .welcome-row p { color: #6b7280; font-size: 0.9rem; }
        .today-badge {
            background: #fff; border: 1px solid #eef2f6; border-radius: 10px;
            padding: 8px 16px; font-size: 0.85rem; color: #374151; font-weight: 500;
            box-shadow: 0 4px 12px rgba(0,40,80,0.06);
        }
        .today-badge i { color: var(--primary); margin-right: 6px; }

        .welcome-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
        .province-filter {
            background: #fff; border: 1px solid #eef2f6; border-radius: 10px;
            padding: 8px 14px; font-size: 0.85rem; color: #374151; font-weight: 500;
            box-shadow: 0 4px 12px rgba(0,40,80,0.06); min-width: 190px;
        }
        .province-filter:focus { outline: none; border-color: var(--primary-light); }

        /* ===== Ô tích nhiều tỉnh =====
           18 tỉnh mà bày hết ra thanh lọc thì đẩy KPI xuống quá nửa màn hình,
           nên gói vào một nút mở ra bảng tích. Nút vẫn nói rõ đang chọn mấy
           tỉnh -- một nút câm thì người dùng không biết trang đang thu hẹp. */
        /* ===== Thanh lọc =====
           Một hàng mang CẢ địa bàn của người đang xem (trái) lẫn ba ô lọc
           (phải). Trước đây bốn ô nhét vào góc phải cạnh lời chào và gãy thành
           hai hàng lệch nhau; tách ra hàng riêng thì thẳng nhưng tốn thêm một
           dòng, mà hàng chip địa bàn vốn đã chiếm sẵn một dòng. Gộp là hoà. */
        .filter-bar {
            display: flex; align-items: center; gap: 12px 16px; flex-wrap: wrap;
            background: #fff; border: 1px solid #eef2f6; border-radius: 14px;
            box-shadow: 0 6px 18px rgba(0,40,80,0.06);
            padding: 10px 16px; margin-bottom: 16px;
        }
        /* Ô lọc luôn dồn về PHẢI, kể cả khi bên trái trống (Admin không cầm
           tỉnh nào) -- vị trí của chúng không được nhảy theo việc người xem có
           địa bàn hay không. */
        .filter-bar form {
            display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
            margin: 0 0 0 auto;
            /* flex-shrink phải là 1: khoá 0 thì trên màn hẹp cụm ba ô giữ
               nguyên bề rộng tự nhiên (~530px) và TRÀN ra ngoài thẻ thay vì
               xuống dòng. min-width:0 là vế còn lại -- thiếu nó thì một flex
               item vẫn không co xuống dưới kích thước nội dung của nó. */
            flex: 0 1 auto; min-width: 0;
        }
        /* Trong thanh này các ô THU theo chữ của chính nó, không giữ min-width
           190px như bộ lọc ở chỗ khác: ba ô cứng 190px cộng lại là 620px, cộng
           hàng chip nữa thì tràn và gãy dòng ngay ở màn 1440. */
        .filter-bar .province-filter { min-width: 0; white-space: nowrap; padding: 8px 12px; }
        /* Chip là phần NHƯỜNG chỗ: hết chỗ thì chúng tự xuống dòng bên trong ô
           của mình, còn ba ô lọc giữ nguyên một hàng. Ngược lại -- ép chip một
           hàng -- là đẩy ô lọc xuống, đúng thứ vừa bỏ đi. */
        .filter-bar .my-prov { flex: 1 1 240px; min-width: 0; }

        /* CẢ HAI ô mở bảng đều phải là mốc định vị: bảng bên trong dùng
           position:absolute, thiếu relative ở đây thì nó neo vào cả trang và
           rơi lên góc trái, bẹp lại còn vài chục pixel. Đã dính đúng lỗi này
           với .period-pop khi bỏ lớp .fb-field (lớp đó mới là chỗ mang
           relative trước đây) -- nên hai ô viết CHUNG một rule, thêm ô thứ ba
           thì thêm vào đây. */
        .prov-pop, .period-pop { position: relative; }
        .prov-pop > button {
            cursor: pointer; text-align: left; display: flex; align-items: center; gap: 8px;
        }
        .prov-pop > button i.fa-chevron-down { margin-left: auto; font-size: 0.7rem; color: #9ca3af; }
        /* Neo vào mép PHẢI của chính ô lọc, vì ba ô này đứng ở nửa phải màn
           hình: neo trái thì bảng rộng 320px chạy tràn khỏi mép phải trang.
           Dưới 992px hàng lọc xuống dòng và dạt về trái, lúc đó ngược lại --
           xem media query cuối file. max-width là chốt chặn cuối để không bao
           giờ rộng hơn màn hình. */
        .prov-panel, .period-panel {
            display: none; position: absolute; z-index: 70; top: calc(100% + 6px); right: 0;
            max-width: calc(100vw - 32px);
            background: #fff; border: 1px solid #eef2f6; border-radius: 12px;
            box-shadow: 0 16px 40px rgba(0,40,80,0.18); padding: 12px;
        }
        .prov-panel { width: 320px; max-height: 340px; overflow-y: auto; }
        .prov-panel.open, .period-panel.open { display: block; }
        .prov-panel .prov-actions {
            display: flex; gap: 10px; align-items: center; flex-wrap: wrap;
            padding-bottom: 8px; margin-bottom: 8px; border-bottom: 1px solid #eef2f6;
        }
        .prov-panel .prov-actions button {
            background: none; border: none; padding: 0; cursor: pointer;
            color: var(--primary); font-size: 0.8rem; font-weight: 600;
        }
        .prov-panel .prov-actions .prov-apply {
            margin-left: auto; background: var(--primary); color: #fff;
            border-radius: 8px; padding: 5px 14px;
        }
        .prov-row {
            display: flex; align-items: center; gap: 8px;
            padding: 5px 2px; font-size: 0.85rem; color: #374151;
        }
        .prov-row input { width: 15px; height: 15px; }
        .prov-row.mine strong { color: var(--primary-dark); }
        .prov-row .mine-tag {
            margin-left: auto; font-size: 0.68rem; font-weight: 700; color: var(--primary);
            background: #eaf6ff; border-radius: 6px; padding: 1px 6px;
        }

        /* ===== Bảng chọn kỳ =====
           Gộp hai ô "năm" và "quý/tháng" làm một. Hai ô rời buộc người dùng
           hiểu rằng chọn quý mà quên chọn năm thì KHÔNG lọc gì cả (Period.parse
           bỏ qua khi thiếu năm) -- một cái bẫy im lặng. Ở đây năm luôn có sẵn,
           và mỗi ô quý/tháng là một lựa chọn hoàn chỉnh. */
        .period-panel { width: 300px; }
        .period-nav {
            display: flex; align-items: center; justify-content: space-between;
            padding-bottom: 8px; margin-bottom: 8px; border-bottom: 1px solid #eef2f6;
        }
        .period-nav button {
            background: #f3f4f6; border: none; border-radius: 8px; width: 28px; height: 28px;
            cursor: pointer; color: #374151;
        }
        .period-nav button:disabled { opacity: 0.35; cursor: default; }
        .period-nav .yr { font-weight: 700; color: var(--primary-dark); font-size: 0.95rem; }
        .period-grid { display: grid; gap: 6px; margin-bottom: 8px; }
        .period-grid.q { grid-template-columns: repeat(4, 1fr); }
        .period-grid.m { grid-template-columns: repeat(4, 1fr); }
        .period-grid button, .period-wide {
            background: #fff; border: 1px solid #eef2f6; border-radius: 8px;
            padding: 6px 0; font-size: 0.8rem; color: #374151; cursor: pointer; font-weight: 500;
        }
        .period-grid button:hover, .period-wide:hover { border-color: var(--primary-light); color: var(--primary-dark); }
        .period-grid button.sel, .period-wide.sel {
            background: var(--primary); border-color: var(--primary); color: #fff; font-weight: 700;
        }
        .period-wide { width: 100%; margin-bottom: 8px; padding: 7px 0; }
        .period-lbl {
            font-size: 0.7rem; font-weight: 700; color: #9ca3af;
            text-transform: uppercase; letter-spacing: 0.04em; margin-bottom: 5px;
        }

        /* Chip địa bàn dưới lời chào -- trả lời "tôi đang phụ trách tỉnh nào". */
        .my-prov { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
        .my-prov .lbl { font-size: 0.8rem; color: #6b7280; }
        .my-prov .chip {
            font-size: 0.76rem; font-weight: 600; color: var(--primary-dark);
            background: #eaf6ff; border: 1px solid #cfe8fb; border-radius: 999px; padding: 2px 10px;
        }
        /* Nằm TRONG thẻ lọc, là dòng thứ hai của chính nó -- trước đây nó là
           một dải màu riêng nằm ngay dưới, tức ba dải xếp chồng (lời chào,
           thanh lọc, chú thích) trước khi tới con số đầu tiên. Nó nói về mấy ô
           ngay trên nó nên đứng chung là đúng chỗ, và tiết kiệm một dải. */
        .scope-note {
            display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
            flex-basis: 100%; margin: 2px 0 0; padding-top: 10px;
            border-top: 1px dashed #eef2f6;
            font-size: 0.82rem; color: #6b7280;
        }
        .scope-note strong { color: var(--primary-dark); }
        .scope-note i { color: var(--primary); }
        .scope-note a { color: var(--primary); font-weight: 600; margin-left: auto; }
        .as-of-today { font-weight: 500; font-size: 0.74rem; color: #9ca3af; text-transform: none; letter-spacing: 0; }

        /* ===== KPI cards ===== */
        .kpi-card { padding: 18px 18px 16px; display: flex; flex-direction: column; gap: 10px; height: 100%; }
        .kpi-top { display: flex; justify-content: space-between; align-items: flex-start; }
        .kpi-icon {
            width: 46px; height: 46px; border-radius: 12px;
            display: flex; align-items: center; justify-content: center; font-size: 1.15rem; color: #fff;
        }
        .kpi-icon.bg-blue { background: linear-gradient(135deg, var(--primary-dark), var(--primary-light)); }
        .kpi-icon.bg-amber { background: linear-gradient(135deg, #c97a0f, var(--warning)); }
        .kpi-icon.bg-green { background: linear-gradient(135deg, #1a8f68, var(--success)); }
        .kpi-icon.bg-red { background: linear-gradient(135deg, #b8384f, var(--danger)); }

        .kpi-label { font-size: 0.8rem; color: #6b7280; font-weight: 500; }
        .kpi-value { font-size: 1.65rem; font-weight: 700; color: #111827; line-height: 1.1; }
        .kpi-trend { font-size: 0.78rem; font-weight: 600; display: inline-flex; align-items: center; gap: 5px; }
        .kpi-trend.up { color: var(--success); }
        .kpi-trend.warn { color: var(--warning); }
        .kpi-trend.down { color: var(--danger); }

        <%-- Kiểu của thẻ biểu đồ (.chart-card, .doughnut-wrap, .legend-*) đã xoá
             cùng với chính cái biểu đồ. Không giữ CSS mồ côi: lần bật lại sẽ
             viết theo bố cục lúc đó chứ không phải theo bố cục hôm nay. --%>

        /* ===== Tables ===== */
        .table-section { padding: 18px 20px 15px; }
        .table-section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
        .table-section-header h6 { font-weight: 700; color: var(--primary-dark); font-size: 0.92rem; margin: 0; }
        .table-section-header a { font-size: 0.8rem; color: var(--primary); text-decoration: none; font-weight: 600; }
        .table-section-header a:hover { text-decoration: underline; }

        .mini-table { width: 100%; }
        .mini-table th {
            font-size: 0.7rem; text-transform: uppercase; color: #9ca3af; font-weight: 700;
            padding: 8px 10px; border-bottom: 1.5px solid #eef2f6; text-align: left;
        }
        .mini-table td { padding: 10px 10px; font-size: 0.84rem; color: #111827; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
        .mini-table tr:last-child td { border-bottom: none; }
        /* Hai bảng giờ chia đôi bề ngang nên mỗi cột hẹp đi một nửa. Ba cột
           cuối là số tiền, số ngày và nhãn trạng thái -- những thứ xuống dòng
           giữa chừng thì đọc thành hai mẩu vô nghĩa ("600 triệu" / "đ"). Cột
           mã và tên khách vẫn cho xuống dòng: chúng dài thật, ép một hàng là
           đẩy cả bảng trượt ngang. */
        .mini-table th:nth-child(n+3), .mini-table td:nth-child(n+3) { white-space: nowrap; }
        /* Dưới 768px hai bảng đã xếp dọc và mỗi bảng chỉ còn ~347px: giữ nowrap
           ở đó thì bảng rộng hơn màn hình và kéo CẢ TRANG trượt ngang (đo được:
           scrollWidth > clientWidth ở 375px). Trả về cho xuống dòng. */
        @media (max-width: 767px) {
            .mini-table th:nth-child(n+3), .mini-table td:nth-child(n+3) { white-space: normal; }
        }
        .mini-table a.link { color: var(--primary); font-weight: 600; text-decoration: none; }
        .mini-table a.link:hover { text-decoration: underline; }

        .status-pill { padding: 3px 10px; border-radius: 20px; font-size: 0.7rem; font-weight: 600; white-space: nowrap; }
        .status-danger { background: #fdecef; color: var(--danger); }
        .status-warn { background: #fff4e0; color: var(--warning); }
        .status-info { background: #eaf6ff; color: var(--primary); }
        .status-success { background: #e8faf3; color: var(--success); }
        .status-gray { background: #f3f4f6; color: #6b7280; }

        /* Hàng lọc xuống dòng thì ba ô dạt về TRÁI, nên bảng phải neo trái lại
           -- neo phải lúc đó đẩy mép trái của bảng ra ngoài màn hình. */
        @media (max-width: 991px) {
            .filter-bar form { margin-left: 0; }
            .prov-panel, .period-panel { left: 0; right: auto; }
        }

        @media (max-width: 768px) {
            .page-container { padding: 0 14px 32px; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>

    <div class="app-shell">
        <c:set var="activeNav" value="dashboard" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">

        <div class="welcome-row">
            <div>
                <h2>Chào mừng trở lại, <c:out value="${sessionScope.currentUser.fullName}"/> 👋</h2>
                <%-- Nói rõ trang đang tính trên phạm vi nào. Không có dòng này thì
                     con số của một nhân viên và của Admin khác nhau mà chẳng ai
                     biết vì đâu -- mặc định của hai vai trò vốn khác nhau. --%>
                <p>
                    <c:choose>
                        <c:when test="${scopeFilter == 'mine' and teamSize > 1}">
                            Số liệu của <strong>bạn và ${teamSize - 1} nhân viên cấp dưới</strong>.
                        </c:when>
                        <c:when test="${scopeFilter == 'mine'}">
                            Số liệu phần việc <strong>bạn đang phụ trách</strong>.
                        </c:when>
                        <c:otherwise>
                            Số liệu <strong>toàn chi nhánh</strong> &mdash; hoạt động kinh doanh và hỗ trợ kỹ thuật.
                        </c:otherwise>
                    </c:choose>
                </p>
            </div>
            <div class="welcome-actions">
                <div class="today-badge"><i class="fa-regular fa-calendar"></i>${todayLabel}</div>
            </div>
        </div>

        <%-- MỘT hàng mang cả hai thứ: bên trái là địa bàn của người đang xem,
             bên phải là ba ô lọc. Thanh lọc đứng riêng thì tốn thêm một dòng,
             mà hàng chip vốn đã chiếm sẵn một dòng rồi -- gộp lại là hoà.

             Địa bàn phải đọc được KHÔNG CẦN BẤM: nó trả lời "vì sao số của tôi
             khác số của đồng nghiệp". Nên nó là chữ nằm ngoài chứ không phải
             thứ giấu trong bảng tích. Ai chưa được giao tỉnh nào thì bên trái
             trống và ba ô lọc tự dồn sang phải -- một dòng "phụ trách: (trống)"
             chỉ làm người ta tưởng hỏng.

             BỎ nhãn xếp trên từng ô: nhãn đội thêm một tầng chữ, mà hàng này
             phải thấp. Thay bằng cách cho mỗi ô tự xưng -- "Phạm vi: Của tôi",
             biểu tượng địa điểm, biểu tượng lịch. --%>
        <div class="filter-bar">
            <c:if test="${not empty myProvinces}">
                <div class="my-prov">
                    <span class="lbl"><i class="fa-solid fa-map-location-dot"></i> Bạn phụ trách:</span>
                    <c:forEach var="mp" items="${myProvinces}">
                        <span class="chip">${fn:escapeXml(mp.shortName)}</span>
                    </c:forEach>
                </div>
            </c:if>
            <form method="GET" action="${pageContext.request.contextPath}/dashboard" id="provinceFilterForm">
                    <%-- Phạm vi người phụ trách. Đứng TRƯỚC các ô kia vì nó là ô
                         đổi nghĩa cả trang, còn tỉnh và kỳ chỉ thu hẹp thêm.
                         Mặc định khác nhau theo vai trò (nhân viên: của tôi,
                         Admin: toàn chi nhánh) nên ô này luôn gửi giá trị rõ
                         ràng, không để trống rồi đoán lại. --%>
                    <select id="filterScope" name="scope" class="province-filter">
                        <option value="mine" ${scopeFilter == 'mine' ? 'selected' : ''}>Của tôi</option>
                        <option value="all" ${scopeFilter == 'all' ? 'selected' : ''}>Toàn chi nhánh</option>
                    </select>
                    <%-- Ô tích NHIỀU tỉnh. provinceSet là cờ "form này có gửi phần
                         tỉnh lên": bỏ tích hết thì không có tham số provinceId nào,
                         giống hệt lần đầu mở trang -- không có cờ này thì controller
                         không phân biệt được "chưa chọn" với "đã bỏ hết" và sẽ tự
                         tích lại địa bàn của người dùng ngay sau khi họ vừa bỏ. --%>
                    <input type="hidden" name="provinceSet" value="1">
                <div class="prov-pop">
                        <button type="button" class="province-filter" id="provToggle"
                                aria-expanded="false" aria-controls="provPanel">
                            <i class="fa-solid fa-location-dot" style="color:#9ca3af;"></i>
                            <span id="provToggleLabel">
                                <c:choose>
                                    <c:when test="${empty provinceFilters}">Toàn bộ 18 tỉnh địa bàn</c:when>
                                    <c:when test="${provinceFilterIsMine}">Địa bàn của bạn (${fn:length(provinceFilters)} tỉnh)</c:when>
                                    <c:when test="${fn:length(provinceFilters) == 1}">1 tỉnh đang chọn</c:when>
                                    <c:otherwise>${fn:length(provinceFilters)} tỉnh đang chọn</c:otherwise>
                                </c:choose>
                            </span>
                            <i class="fa-solid fa-chevron-down"></i>
                        </button>
                        <div class="prov-panel" id="provPanel">
                            <div class="prov-actions">
                                <button type="button" data-prov-all="1">Chọn tất cả</button>
                                <button type="button" data-prov-none="1">Bỏ hết</button>
                                <c:if test="${not empty myProvinces}">
                                    <button type="button" data-prov-mine="1">Địa bàn của tôi</button>
                                </c:if>
                                <button type="submit" class="prov-apply">Áp dụng</button>
                            </div>
                            <c:forEach var="province" items="${provinceList}">
                                <%-- myProvinces là List<Province> nên không dùng contains
                                     thẳng được; đánh dấu bằng vòng lặp con, 18 x 6 phép so
                                     sánh thì rẻ hơn hẳn một lần gọi CSDL nữa. --%>
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
                <%-- MỘT ô cho cả năm lẫn quý/tháng. Hai ô rời trước đây có một
                     cái bẫy im lặng: chọn "Quý 3" mà quên chọn năm thì Period.parse
                     trả null, tức là KHÔNG lọc gì -- người dùng thấy ô đang hiện
                     "Quý 3" và tin là trang đã thu hẹp. Ở đây năm luôn đi kèm, và
                     mỗi ô quý/tháng là một lựa chọn hoàn chỉnh.

                     Hai ô ẩn mới là thứ thật sự gửi lên; các nút chỉ ghi giá trị
                     vào chúng rồi submit. Giữ đúng hai tham số year/period cũ nên
                     link cũ và bookmark cũ vẫn mở đúng kỳ như trước. --%>
                <input type="hidden" name="year" id="fYear" value="${yearFilter}">
                <input type="hidden" name="period" id="fPeriod" value="${periodFilter}">
                <div class="period-pop">
                    <button type="button" class="province-filter" id="periodToggle"
                            aria-expanded="false" aria-controls="periodPanel">
                        <i class="fa-regular fa-calendar" style="color:#9ca3af;"></i>
                        <span id="periodToggleLabel">
                            <c:choose>
                                <c:when test="${not empty periodLabel}">${fn:escapeXml(periodLabel)}</c:when>
                                <c:otherwise>Mọi thời điểm</c:otherwise>
                            </c:choose>
                        </span>
                        <i class="fa-solid fa-chevron-down"></i>
                    </button>
                    <%-- data-* để script biết năm nào đang chọn và năm nào được
                         phép -- danh sách năm do Period.availableYears() quyết
                         định, đừng đoán lại ở tầng JS. --%>
                    <div class="period-panel" id="periodPanel"
                         data-selected-year="${empty yearFilter ? '' : yearFilter}"
                         data-selected-period="${empty periodFilter ? '' : fn:escapeXml(periodFilter)}"
                         data-years="<c:forEach var="y" items="${yearList}" varStatus="st">${y}<c:if test="${not st.last}">,</c:if></c:forEach>">
                        <div class="period-nav">
                            <button type="button" id="yrPrev" aria-label="Năm trước">&lsaquo;</button>
                            <span class="yr" id="yrLabel"></span>
                            <button type="button" id="yrNext" aria-label="Năm sau">&rsaquo;</button>
                        </div>
                        <button type="button" class="period-wide" data-pick="any">Mọi thời điểm</button>
                        <button type="button" class="period-wide" data-pick="year">Cả năm</button>
                        <div class="period-lbl">Quý</div>
                        <div class="period-grid q">
                            <c:forEach var="q" begin="1" end="4">
                                <button type="button" data-pick="q${q}">Q${q}</button>
                            </c:forEach>
                        </div>
                        <div class="period-lbl">Tháng</div>
                        <div class="period-grid m">
                            <c:forEach var="m" begin="1" end="12">
                                <button type="button" data-pick="m${m}">Th${m}</button>
                            </c:forEach>
                        </div>
                    </div>
                </div>
            </form>
        <c:if test="${not empty provinceFilters or not empty periodLabel}">
            <div class="scope-note">
                <i class="fa-solid fa-filter"></i>
                <%-- Gom câu chữ vào một biến TRƯỚC khi in: viết c:choose thẳng vào
                     câu thì mỗi nhánh kéo theo xuống dòng và thụt lề của chính nó,
                     ra "... 2 tỉnh đang chọn ." -- thừa một dấu cách trước dấu chấm. --%>
                <c:choose>
                    <c:when test="${provinceFilterIsMine}"><c:set var="provNote" value="địa bàn bạn phụ trách"/></c:when>
                    <c:when test="${fn:length(provinceFilters) == 1}"><c:set var="provNote" value="1 tỉnh đang chọn"/></c:when>
                    <c:when test="${not empty provinceFilters}"><c:set var="provNote" value="${fn:length(provinceFilters)} tỉnh đang chọn"/></c:when>
                    <c:otherwise><c:set var="provNote" value=""/></c:otherwise>
                </c:choose>
                <%-- Câu chữ phải nói đúng phép ghép là HOẶC. Với người đang ở phạm
                     vi "Của tôi" thì địa bàn KHÔNG cắt bớt việc của họ, nó CỘNG
                     thêm việc trong tỉnh họ giữ -- viết "thu hẹp theo địa bàn" là
                     nói ngược, và đó chính là thứ làm người dùng đi tìm mấy hợp
                     đồng bị thiếu. --%>
                <span>
                    <c:choose>
                        <c:when test="${not empty provNote and scopeFilter == 'mine'}">
                            Số liệu gồm <strong>phần việc của bạn</strong> và mọi việc trong <strong>${fn:escapeXml(provNote)}</strong><c:if test="${not empty periodLabel}">, tính trong <strong>${fn:escapeXml(periodLabel)}</strong></c:if>.
                        </c:when>
                        <c:otherwise>
                            Số liệu đang thu hẹp theo<c:if test="${not empty provNote}"> <strong>${fn:escapeXml(provNote)}</strong></c:if><c:if test="${not empty provNote and not empty periodLabel}"> và</c:if><c:if test="${not empty periodLabel}"> <strong>${fn:escapeXml(periodLabel)}</strong></c:if>.
                        </c:otherwise>
                    </c:choose>
                    Riêng hai bảng cuối trang luôn tính tới hôm nay.
                </span>
                <%-- "Xem toàn chi nhánh" chứ không phải "Bỏ lọc": link trỏ /dashboard
                     trần sẽ rơi về MẶC ĐỊNH, mà mặc định giờ đã là địa bàn của mình --
                     bấm xong thấy y nguyên thì người dùng tưởng nút hỏng. Phải mang
                     theo provinceSet để nói rõ "đã chọn, và chọn là không tỉnh nào". --%>
                <a href="${pageContext.request.contextPath}/dashboard?provinceSet=1">Xem toàn chi nhánh</a>
            </div>
        </c:if>
        </div>


        <!-- ===== KPI cards ===== -->
        <div class="row g-4 mb-4">
            <div class="col-6 col-lg-3">
                <div class="card-box kpi-card">
                    <div class="kpi-top">
                        <div>
                            <div class="kpi-label">Tổng khách hàng</div>
                            <div class="kpi-value">${totalCustomers}</div>
                        </div>
                        <div class="kpi-icon bg-blue"><i class="fa-solid fa-building"></i></div>
                    </div>
                    <span class="kpi-trend up"><i class="fa-solid fa-arrow-trend-up"></i> +${newCustomersThisMonth} khách hàng mới <c:choose><c:when test="${not empty periodLabel}">trong kỳ</c:when><c:otherwise>tháng này</c:otherwise></c:choose></span>
                </div>
            </div>
            <div class="col-6 col-lg-3">
                <div class="card-box kpi-card">
                    <div class="kpi-top">
                        <div>
                            <div class="kpi-label">Hợp đồng đang hiệu lực</div>
                            <div class="kpi-value">${contractStatusSummary['Đang hiệu lực']}</div>
                        </div>
                        <div class="kpi-icon bg-amber"><i class="fa-solid fa-file-contract"></i></div>
                    </div>
                    <span class="kpi-trend warn"><i class="fa-solid fa-triangle-exclamation"></i> ${contractStatusSummary['Sắp hết hạn']} hợp đồng sắp hết hạn</span>
                </div>
            </div>
            <div class="col-6 col-lg-3">
                <div class="card-box kpi-card">
                    <div class="kpi-top">
                        <div>
                            <div class="kpi-label">Doanh thu hợp đồng (<c:choose><c:when test="${not empty periodLabel}">${fn:escapeXml(periodLabel)}</c:when><c:otherwise>tháng ${currentMonthNumber}</c:otherwise></c:choose>)</div>
                            <div class="kpi-value" id="revenueKpiValue" data-vnd="${revenueThisMonth}">&mdash;</div>
                        </div>
                        <div class="kpi-icon bg-green"><i class="fa-solid fa-sack-dollar"></i></div>
                    </div>
                    <c:choose>
                        <c:when test="${not empty revenueTrendPercent}">
                            <span class="kpi-trend ${revenueTrendPercent >= 0 ? 'up' : 'down'}"><i class="fa-solid fa-arrow-trend-${revenueTrendPercent >= 0 ? 'up' : 'down'}"></i> ${revenueTrendPercent >= 0 ? '+' : ''}${revenueTrendPercent}% so với <c:choose><c:when test="${not empty periodLabel}">kỳ trước</c:when><c:otherwise>tháng trước</c:otherwise></c:choose></span>
                        </c:when>
                        <c:otherwise>
                            <span class="kpi-trend"><i class="fa-regular fa-circle-question"></i> Chưa đủ dữ liệu <c:choose><c:when test="${not empty periodLabel}">kỳ trước</c:when><c:otherwise>tháng trước</c:otherwise></c:choose> để so sánh</span>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
            <div class="col-6 col-lg-3">
                <div class="card-box kpi-card">
                    <div class="kpi-top">
                        <div>
                            <div class="kpi-label">Phiếu hỗ trợ đang xử lý</div>
                            <div class="kpi-value">${ticketStatusSummary['Đang xử lý']}</div>
                        </div>
                        <div class="kpi-icon bg-red"><i class="fa-solid fa-headset"></i></div>
                    </div>
                    <c:choose>
                        <c:when test="${overdueOrDueSoonCount > 0}">
                            <span class="kpi-trend down"><i class="fa-solid fa-clock"></i> ${overdueOrDueSoonCount} phiếu sắp trễ hạn xử lý</span>
                        </c:when>
                        <c:otherwise>
                            <span class="kpi-trend up"><i class="fa-solid fa-check"></i> Không có phiếu nào sắp trễ hạn</span>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>

        <!-- ===== Hai bảng hành động, nằm cạnh nhau ===== -->
        <%-- Biểu đồ "Phiếu hỗ trợ theo trạng thái" TẠM BỎ khỏi giao diện theo
             yêu cầu; hai bảng chia đôi bề ngang. Phần dữ liệu của nó vẫn còn
             nguyên ở tầng dưới (DashboardController vẫn đặt ticketStatusSummary,
             ô KPI "Phiếu hỗ trợ đang xử lý" vẫn đọc từ đó), nên bật lại chỉ là
             dựng lại thẻ biểu đồ -- không phải làm lại truy vấn. --%>
        <div class="row g-4">
            <div class="col-lg-6">
                <div class="card-box table-section h-100">
                    <div class="table-section-header">
                        <h6>Hợp đồng sắp hết hạn <span class="as-of-today">tính tới hôm nay</span></h6>
                        <a href="${pageContext.request.contextPath}/contract">Xem tất cả</a>
                    </div>
                    <%-- Bọc cuộn ngang: ở khổ điện thoại bảng 5 cột hẹp nhất cũng
                         rộng hơn thẻ (~366px so với 347px) và kéo CẢ TRANG trượt
                         ngang. Cho riêng bảng cuộn, giống cách listcontract.jsp
                         đang làm. --%>
                    <div class="table-responsive">
                    <table class="mini-table">
                        <%-- Cột ĐỊA BÀN thay cho "Người phụ trách": phạm vi của trang là
                             "việc của tôi HOẶC trong địa bàn tôi giữ", nên câu người đọc cần
                             trả lời khi nhìn một dòng lạ là "nó ở tỉnh nào", chứ không phải
                             "ai đứng tên" -- phần lớn các dòng đứng tên chính họ. --%>
                        <thead><tr><th>Mã HĐ</th><th>Khách hàng</th><th>Địa bàn</th><th>Giá trị</th><th>Còn lại</th></tr></thead>
                        <tbody>
                            <c:choose>
                                <c:when test="${empty expiringContracts}">
                                    <tr><td colspan="5" style="text-align:center; color:#9ca3af; padding:20px 8px;">Không có hợp đồng nào sắp hết hạn.</td></tr>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach var="ct" items="${expiringContracts}">
                                        <c:set var="daysLeft" value="${daysRemaining[ct.contractId]}"/>
                                        <tr>
                                            <td><a href="${pageContext.request.contextPath}/contract?action=view&id=${ct.contractId}" class="link">${fn:escapeXml(ct.contractCode)}</a></td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${ct.enterprise != null}">${fn:escapeXml(ct.enterprise.enterpriseName)}</c:when>
                                                    <c:otherwise>&mdash;</c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${ct.enterprise.address.district.province != null}">${fn:escapeXml(ct.enterprise.address.district.province.shortName)}</c:when>
                                                    <c:otherwise>&mdash;</c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td><span class="contract-value" data-vnd="${contractValues[ct.contractId]}">&mdash;</span></td>
                                            <td><span class="status-pill ${daysLeft <= 7 ? 'status-danger' : 'status-warn'}">${daysLeft} ngày</span></td>
                                        </tr>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </tbody>
                    </table>
                    </div>
                </div>
            </div>

            <div class="col-lg-6">
                <div class="card-box table-section h-100">
                    <div class="table-section-header">
                        <h6>Phiếu hỗ trợ cần xử lý <span class="as-of-today">tính tới hôm nay</span></h6>
                        <a href="${pageContext.request.contextPath}/ticket">Xem tất cả</a>
                    </div>
                    <%-- Bọc cuộn ngang: ở khổ điện thoại bảng 5 cột hẹp nhất cũng
                         rộng hơn thẻ (~366px so với 347px) và kéo CẢ TRANG trượt
                         ngang. Cho riêng bảng cuộn, giống cách listcontract.jsp
                         đang làm. --%>
                    <div class="table-responsive">
                    <table class="mini-table">
                        <thead><tr><th>Mã phiếu</th><th>Khách hàng</th><th>Địa bàn</th><th>Ưu tiên</th><th>Trạng thái</th></tr></thead>
                        <tbody>
                            <c:choose>
                                <c:when test="${empty attentionTickets}">
                                    <tr><td colspan="5" style="text-align:center; color:#9ca3af; padding:20px 8px;">Không có phiếu nào cần xử lý.</td></tr>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach var="tk" items="${attentionTickets}">
                                        <tr>
                                            <td><a href="${pageContext.request.contextPath}/ticket?action=view&id=${tk.ticketId}" class="link">${fn:escapeXml(tk.ticketCode)}</a></td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${tk.enterprise != null}">${fn:escapeXml(tk.enterprise.enterpriseName)}</c:when>
                                                    <c:otherwise>&mdash;</c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${tk.enterprise.address.district.province != null}">${fn:escapeXml(tk.enterprise.address.district.province.shortName)}</c:when>
                                                    <c:otherwise>&mdash;</c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${tk.priority == 'Khẩn cấp'}"><span class="status-pill status-danger">Khẩn cấp</span></c:when>
                                                    <c:when test="${tk.priority == 'Cao'}"><span class="status-pill status-warn">Cao</span></c:when>
                                                    <c:when test="${tk.priority == 'Thấp'}"><span class="status-pill status-gray">Thấp</span></c:when>
                                                    <c:otherwise><span class="status-pill status-info">Bình thường</span></c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${tk.status == 'Đang xử lý'}"><span class="status-pill status-warn">Đang xử lý</span></c:when>
                                                    <c:when test="${tk.status == 'Đã đóng'}"><span class="status-pill status-success">Đã đóng</span></c:when>
                                                    <c:otherwise><span class="status-pill status-info">Mới tiếp nhận</span></c:otherwise>
                                                </c:choose>
                                            </td>
                                        </tr>
                                    </c:forEach>
                                </c:otherwise>
                            </c:choose>
                        </tbody>
                    </table>
                    </div>
                </div>
            </div>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Đổi ô Phạm vi là nạp lại trang ngay, không cần nút "Lọc" -- giống bộ
        // lọc ở danh sách khách hàng/hợp đồng.
        //
        // Ô TỈNH thì KHÔNG, cố ý: nó tích được nhiều tỉnh, nạp lại sau mỗi lần
        // tích thì chọn ba tỉnh là ba lần tải trang và hai lần đầu ra số liệu
        // chẳng ai cần. Nó có nút "Áp dụng" riêng. Ô KỲ thì ngược lại -- mỗi lựa
        // chọn ở đó là một kỳ hoàn chỉnh, nên bấm phát nào nạp lại phát đó.
        document.getElementById('filterScope').addEventListener('change', function () {
            document.getElementById('provinceFilterForm').submit();
        });

        // ===== Bảng tích tỉnh =====
        (function () {
            var toggle = document.getElementById('provToggle');
            var panel = document.getElementById('provPanel');
            if (!toggle || !panel) { return; }
            var boxes = panel.querySelectorAll('input[name="provinceId"]');

            function setOpen(open) {
                panel.classList.toggle('open', open);
                toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            }
            toggle.addEventListener('click', function (e) {
                e.stopPropagation();
                setOpen(!panel.classList.contains('open'));
            });
            // Bấm ra ngoài thì đóng, nhưng bấm TRONG bảng thì không -- tích một ô
            // mà bảng tự đóng thì không tích được ô thứ hai.
            panel.addEventListener('click', function (e) { e.stopPropagation(); });
            document.addEventListener('click', function () { setOpen(false); });
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape') { setOpen(false); }
            });

            function apply(fn) {
                boxes.forEach(fn);
            }
            panel.querySelector('[data-prov-all]').addEventListener('click', function () {
                apply(function (b) { b.checked = true; });
            });
            panel.querySelector('[data-prov-none]').addEventListener('click', function () {
                apply(function (b) { b.checked = false; });
            });
            var mineBtn = panel.querySelector('[data-prov-mine]');
            if (mineBtn) {
                mineBtn.addEventListener('click', function () {
                    apply(function (b) { b.checked = b.dataset.mine === 'true'; });
                });
            }
        })();

        // ===== Bảng chọn kỳ (năm - quý - tháng trong MỘT ô) =====
        (function () {
            var toggle = document.getElementById('periodToggle');
            var panel = document.getElementById('periodPanel');
            if (!toggle || !panel) { return; }

            var years = panel.dataset.years.split(',').map(Number).sort(function (a, b) { return a - b; });
            var selYear = panel.dataset.selectedYear ? Number(panel.dataset.selectedYear) : null;
            var selPeriod = panel.dataset.selectedPeriod || '';
            // Đang xem năm nào: năm đã chọn, hoặc năm mới nhất khi chưa lọc gì.
            var viewYear = selYear || years[years.length - 1];

            var yrLabel = document.getElementById('yrLabel');
            var prev = document.getElementById('yrPrev');
            var next = document.getElementById('yrNext');

            function render() {
                yrLabel.textContent = 'Năm ' + viewYear;
                prev.disabled = viewYear <= years[0];
                next.disabled = viewYear >= years[years.length - 1];
                // Tô đậm ô đang chọn -- CHỈ khi đang xem đúng năm đã chọn, nếu
                // không thì bấm mũi tên sang năm khác vẫn thấy "Q3" sáng và
                // tưởng mình đang xem quý 3 của năm đó.
                panel.querySelectorAll('[data-pick]').forEach(function (b) {
                    var pick = b.dataset.pick;
                    var on;
                    if (pick === 'any') {
                        on = selYear === null;
                    } else if (pick === 'year') {
                        on = selYear === viewYear && selPeriod === '';
                    } else {
                        on = selYear === viewYear && selPeriod === pick;
                    }
                    b.classList.toggle('sel', on);
                });
            }

            function setOpen(open) {
                panel.classList.toggle('open', open);
                toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
                if (open) { render(); }
            }
            toggle.addEventListener('click', function (e) {
                e.stopPropagation();
                setOpen(!panel.classList.contains('open'));
            });
            panel.addEventListener('click', function (e) { e.stopPropagation(); });
            document.addEventListener('click', function () { setOpen(false); });
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape') { setOpen(false); }
            });

            prev.addEventListener('click', function () {
                if (viewYear > years[0]) { viewYear--; render(); }
            });
            next.addEventListener('click', function () {
                if (viewYear < years[years.length - 1]) { viewYear++; render(); }
            });

            // Mỗi lựa chọn ghi thẳng vào hai ô ẩn rồi gửi form -- không có
            // trạng thái nào sống riêng ở tầng JS, nên nạp lại trang thì thứ
            // hiện ra luôn là thứ máy chủ đang thật sự lọc.
            panel.querySelectorAll('[data-pick]').forEach(function (b) {
                b.addEventListener('click', function () {
                    var pick = b.dataset.pick;
                    document.getElementById('fYear').value = pick === 'any' ? '' : viewYear;
                    document.getElementById('fPeriod').value = (pick === 'any' || pick === 'year') ? '' : pick;
                    document.getElementById('provinceFilterForm').submit();
                });
            });
        })();

        // ===== Định dạng tiền tệ rút gọn (tỷ / triệu đ) =====
        function formatCompactVND(n) {
            if (isNaN(n)) return '0 đ';
            if (Math.abs(n) >= 1e9) return (n / 1e9).toLocaleString('vi-VN', { maximumFractionDigits: 2 }) + ' tỷ đ';
            if (Math.abs(n) >= 1e6) return (n / 1e6).toLocaleString('vi-VN', { maximumFractionDigits: 0 }) + ' triệu đ';
            return n.toLocaleString('vi-VN') + ' đ';
        }

        document.getElementById('revenueKpiValue').textContent =
            formatCompactVND(Number(document.getElementById('revenueKpiValue').dataset.vnd));

        document.querySelectorAll('.contract-value').forEach(function (el) {
            el.textContent = formatCompactVND(Number(el.dataset.vnd));
        });

        // Một số trình duyệt (đặc biệt Chrome bản cũ) vẫn phục hồi trang từ
        // bfcache khi bấm Back dù đã có header Cache-Control: no-store --
        // event.persisted = true nghĩa là trang này KHÔNG được tải lại từ
        // server mà chỉ là ảnh chụp cũ trong bộ nhớ trình duyệt. Ép reload
        // thật để trang phải đi qua AuthenticationFilter lần nữa: nếu session
        // đã bị huỷ (vd. do vừa đăng xuất) thì filter sẽ tự đá về login.jsp.
        window.addEventListener('pageshow', function (event) {
            if (event.persisted) {
                window.location.reload();
            }
        });

    </script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
