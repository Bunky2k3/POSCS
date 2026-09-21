<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
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

        <%-- Kiểu của thẻ biểu đồ (.chart-card, .doughnut-wrap, .legend-*) đã xoá
             cùng với chính cái biểu đồ. Không giữ CSS mồ côi: lần bật lại sẽ
             viết theo bố cục lúc đó chứ không phải theo bố cục hôm nay. --%>

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

        /* ===== Tables ===== */
        .table-section { padding: 18px 20px 15px; }
        .table-section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
        .table-section-header h6 { font-weight: 700; color: var(--primary-dark); font-size: 0.92rem; margin: 0; }
        .table-section-header a { font-size: 0.8rem; color: var(--primary); text-decoration: none; font-weight: 600; }
        .table-section-header a:hover { text-decoration: underline; }

        /* table-layout: fixed -- KHÔNG để trình duyệt tự chia cột.
           Cách chia tự động dựa vào chỗ xuống dòng được của nội dung, và ở đây
           nó cho ra kết quả ngược đời: "08/2026/HĐKT-POSTEF" gần như không ngắt
           được nên trình duyệt giữ hẳn 131px cho cột Mã HĐ, còn "Công ty Cổ
           phần Đầu tư Hạ tầng Đất Tổ" ngắt được ở mọi dấu cách nên bị ép xuống
           86px -- tên khách rơi thành 5 dòng, dòng bảng cao 141px trong khi
           bảng khách hàng ngay bên cạnh chỉ 61px. Chia cứng thì cột tên được
           phần rộng nhất, đúng như nó cần. */
        .mini-table { width: 100%; table-layout: fixed; }
        .mini-table th {
            font-size: 0.7rem; text-transform: uppercase; color: #9ca3af; font-weight: 700;
            padding: 8px 7px; border-bottom: 1.5px solid #eef2f6; text-align: left;
        }
        .mini-table td { padding: 10px 7px; font-size: 0.84rem; color: #111827; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
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
        /* Tỉ lệ cột của TỪNG bảng. Để trong CSS chứ không dựng colgroup trong
           JSP: hai bảng khác số cột, mà colgroup lẫn vào giữa các thẻ c:forEach
           thì lần sửa sau rất dễ thêm cột mà quên sửa colgroup. */
        /* Ba cột cuối được cấp ĐÚNG bề rộng tự nhiên đo được của nội dung
           (91 / 85 / 114px kể cả đệm, trong thẻ rộng 516px), phần còn lại chia
           cho mã và tên. Cấp thiếu thì nội dung nowrap tràn ra khỏi ô và bảng
           mọc thanh cuộn ngang ngay trong thẻ. */
        /* Tỉ lệ phần trăm co theo thẻ, còn chữ thì không: khi thẻ hẹp lại
           (màn 1280 cho mỗi thẻ ~436px) mấy cột nowrap không co thêm được nữa
           và nội dung THÒ RA ngoài ô. min-width chặn chỗ đó -- hẹp hơn ngần
           này thì cho chính bảng cuộn ngang trong thẻ, đúng việc của
           .table-responsive, còn hơn là chữ đè lên nhau. */
        /* 465px = bề rộng nhỏ nhất mà MỌI cột còn đủ chỗ theo tỉ lệ dưới đây
           (đo trên trình duyệt: bảng hợp đồng cần 459, bảng khách hàng 452).
           Đúng bằng cỡ thẻ ở màn 1366 nên laptop phổ biến vẫn không phải cuộn;
           hẹp hơn nữa thì cuộn trong thẻ, còn hơn để chữ tràn ra ngoài ô. */
        .mini-table.t-contract, .mini-table.t-customer { min-width: 465px; }
        .mini-table.t-contract th:nth-child(1), .mini-table.t-contract td:nth-child(1) { width: 44%; }
        .mini-table.t-contract th:nth-child(2), .mini-table.t-contract td:nth-child(2) { width: 17%; }
        .mini-table.t-contract th:nth-child(3), .mini-table.t-contract td:nth-child(3) { width: 17%; }
        .mini-table.t-contract th:nth-child(4), .mini-table.t-contract td:nth-child(4) { width: 22%; }
        /* Ô hai dòng: mã đậm ở trên, tên khách chữ nhỏ mờ ở dưới. */
        .cell-2line { display: flex; flex-direction: column; gap: 2px; }
        .cell-2line .cell-sub { font-size: 0.76rem; color: #6b7280; line-height: 1.35; }
        .mini-table.t-customer th:nth-child(1), .mini-table.t-customer td:nth-child(1) { width: 38%; }
        .mini-table.t-customer th:nth-child(2), .mini-table.t-customer td:nth-child(2) { width: 16%; }
        .mini-table.t-customer th:nth-child(3), .mini-table.t-customer td:nth-child(3) { width: 27%; }
        /* Cột ĐỊA BÀN không xuống dòng ở cả hai bảng: "Quảng Ninh" bị bẻ thành
           "Quảng / Ninh" đọc rất khó chịu, mà tên tỉnh dài nhất cũng chỉ cần
           70px trong khi cột được cấp tối thiểu 74px (nhờ min-width ở trên).
           Luật nowrap chung ở trên chỉ áp từ cột thứ BA trở đi, và ở bảng hợp
           đồng địa bàn đã lùi về cột thứ hai sau khi gộp mã với tên khách. */
        .mini-table.t-contract td:nth-child(2), .mini-table.t-contract th:nth-child(2),
        .mini-table.t-customer td:nth-child(2), .mini-table.t-customer th:nth-child(2) { white-space: nowrap; }
        /* Cột "Loại" được xuống dòng, khác ba cột cuối của bảng kia: nó là
           CHỮ ("Nhà mạng viễn thông"), xuống dòng vẫn đọc được, mà ép một hàng
           thì riêng nó ngốn 144px và đẩy cả bảng vượt quá bề rộng thẻ. */
        .mini-table.t-customer td:nth-child(3), .mini-table.t-customer th:nth-child(3) { white-space: normal; }
        .mini-table.t-customer th:nth-child(4), .mini-table.t-customer td:nth-child(4) { width: 19%; }
        /* Khổ điện thoại: bỏ min-width. Thẻ chỉ còn ~307px, giữ 465 là bắt
           cuộn ngang ở mọi dòng, trong khi ba cột cuối đã được trả về cho
           xuống dòng (media query phía trên) nên bảng vừa khít vẫn đọc được.
           Phải đứng SAU khối tỉ lệ ở trên: cùng độ ưu tiên thì luật viết sau
           thắng, để trong media query phía trên là không có tác dụng. */
        @media (max-width: 767px) {
            .mini-table.t-contract, .mini-table.t-customer { min-width: 0; }
            /* Thẻ chỉ còn ~307px nên hai thứ vốn không bao giờ xuống dòng cũng
               phải chịu xuống: mã hợp đồng (không có chỗ ngắt tự nhiên) và
               nhãn trạng thái. Không cho thì chúng thò ra ngoài ô và bảng lại
               mọc thanh cuộn ngang -- đúng thứ min-width: 0 vừa bỏ đi. */
            .mini-table.t-contract td:nth-child(1) { overflow-wrap: anywhere; }
            .mini-table .status-pill { white-space: normal; }
            .mini-table.t-contract td:nth-child(2), .mini-table.t-contract th:nth-child(2),
            .mini-table.t-customer td:nth-child(2), .mini-table.t-customer th:nth-child(2) { white-space: normal; }
        }
        /* Ô trống ("Không có hợp đồng nào...") trải hết bảng -- tỉ lệ ở trên
           dành cho cột thật, colspan không được ăn theo cột thứ nhất. */
        .mini-table td[colspan] { width: auto; }

        .mini-table a.link { color: var(--primary); font-weight: 600; text-decoration: none; }
        .mini-table a.link:hover { text-decoration: underline; }

        .status-pill { padding: 3px 10px; border-radius: 20px; font-size: 0.7rem; font-weight: 600; white-space: nowrap; }
        .status-danger { background: #fdecef; color: var(--danger); }
        .status-warn { background: #fff4e0; color: var(--warning); }
        .status-info { background: #eaf6ff; color: var(--primary); }
        .status-success { background: #e8faf3; color: var(--success); }
        .status-gray { background: #f3f4f6; color: #6b7280; }

        /* Hàng lọc xuống dòng thì ba ô dạt về TRÁI. Việc neo lại bảng chọn
           đã nằm ở appshell.css, ở đây chỉ còn phần bố cục của thanh lọc. */
        @media (max-width: 991px) {
            .filter-bar form { margin-left: 0; }
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
                <div class="prov-pop" data-prov-pop>
                        <button type="button" class="province-filter" data-popover-toggle
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
                <input type="hidden" name="year" value="${yearFilter}" data-period-year-input>
                <input type="hidden" name="period" value="${periodFilter}" data-period-value-input>
                <div class="period-pop" data-period-pop>
                    <button type="button" class="province-filter" data-popover-toggle
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
                    <div class="period-panel" id="periodPanel" data-popover-panel
                         data-selected-year="${empty yearFilter ? '' : yearFilter}"
                         data-selected-period="${empty periodFilter ? '' : fn:escapeXml(periodFilter)}"
                         data-years="<c:forEach var="y" items="${yearList}" varStatus="st">${y}<c:if test="${not st.last}">,</c:if></c:forEach>">
                        <div class="period-nav">
                            <button type="button" data-period-prev aria-label="Năm trước">&lsaquo;</button>
                            <span class="yr" data-period-year></span>
                            <button type="button" data-period-next aria-label="Năm sau">&rsaquo;</button>
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
            <div class="col-6 col-lg-4">
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
            <div class="col-6 col-lg-4">
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
            <div class="col-6 col-lg-4">
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
        </div>

        <!-- ===== Bảng hợp đồng trong kỳ ===== -->
        <%-- MỌI thứ về phiếu hỗ trợ đã ra khỏi trang này theo yêu cầu: biểu đồ
             trạng thái, ô KPI "Phiếu hỗ trợ đang xử lý" và bảng "Phiếu hỗ trợ
             cần xử lý". Phần khách hàng giữ nguyên.

             Controller cũng thôi gọi ba truy vấn phiếu -- để lại thì trang trả
             tiền cho dữ liệu không ai đọc. Các hàm DAO thì còn nguyên (màn hình
             phiếu hỗ trợ và NotificationScheduler vẫn dùng), nên bật lại là
             dựng lại phần hiển thị chứ không phải viết lại truy vấn. --%>
        <div class="row g-4">
            <div class="col-lg-6">
                <div class="card-box table-section h-100">
                    <div class="table-section-header">
                        <%-- Không còn lọc "sắp hết hạn": bảng liệt kê MỌI hợp đồng
                             còn hiệu lực trong kỳ, cột trạng thái tự nói ra từng cái
                             đang ở đâu. Nhãn kỳ lấy từ chính cửa sổ controller đã
                             dùng để truy vấn, không dựng lại ở đây -- hai chỗ tự
                             tính thì có ngày tiêu đề nói một đằng số liệu một nẻo. --%>
                        <h6>Hợp đồng <span class="as-of-today">${fn:escapeXml(contractWindowLabel)}</span></h6>
                        <a href="${pageContext.request.contextPath}/contract">Xem tất cả</a>
                    </div>
                    <%-- Bọc cuộn ngang: ở khổ điện thoại bảng 5 cột hẹp nhất cũng
                         rộng hơn thẻ (~366px so với 347px) và kéo CẢ TRANG trượt
                         ngang. Cho riêng bảng cuộn, giống cách listcontract.jsp
                         đang làm. --%>
                    <div class="table-responsive">
                    <table class="mini-table t-contract">
                        <%-- Cột ĐỊA BÀN thay cho "Người phụ trách": phạm vi của trang là
                             "việc của tôi HOẶC trong địa bàn tôi giữ", nên câu người đọc cần
                             trả lời khi nhìn một dòng lạ là "nó ở tỉnh nào", chứ không phải
                             "ai đứng tên" -- phần lớn các dòng đứng tên chính họ. --%>
                        <%-- Mã hợp đồng và tên khách gộp thành MỘT ô hai dòng, giống
                             cách listcontract.jsp đã làm với cột "Hợp đồng". Để hai cột
                             rời thì trong thẻ rộng 516px chúng tranh nhau chỗ và cùng
                             thua: mã gần như không ngắt dòng được nên trình duyệt giữ
                             hẳn 131px cho nó, tên khách bị ép còn 86px và rơi thành năm
                             dòng. Gộp lại thì mã nằm trọn một dòng, tên khách có cả
                             phần rộng còn lại. --%>
                        <thead><tr><th>Hợp đồng</th><th>Địa bàn</th><th>Giá trị</th><th>Trạng thái</th></tr></thead>
                        <tbody>
                            <c:choose>
                                <c:when test="${empty windowContracts}">
                                    <tr><td colspan="4" style="text-align:center; color:#9ca3af; padding:20px 8px;">Không có hợp đồng nào trong ${fn:escapeXml(contractWindowLabel)}.</td></tr>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach var="ct" items="${windowContracts}">
                                        <tr>
                                            <td>
                                                <div class="cell-2line">
                                                    <a href="${pageContext.request.contextPath}/contract?action=view&id=${ct.contractId}" class="link">${fn:escapeXml(ct.contractCode)}</a>
                                                    <span class="cell-sub">
                                                        <c:choose>
                                                            <c:when test="${ct.enterprise != null}">${fn:escapeXml(ct.enterprise.enterpriseName)}</c:when>
                                                            <c:otherwise>&mdash;</c:otherwise>
                                                        </c:choose>
                                                    </span>
                                                </div>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${ct.enterprise.address.district.province != null}">${fn:escapeXml(ct.enterprise.address.district.province.shortName)}</c:when>
                                                    <c:otherwise>&mdash;</c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td><span class="contract-value" data-vnd="${contractValues[ct.contractId]}">&mdash;</span></td>
                                            <%-- Trạng thái theo LỊCH (BR-17, ContractDAO.computeStatus), không
                                                 phải trạng thái tiến trình: bảng này nói hợp đồng đang ở đâu
                                                 trên trục thời gian, mà đó mới là thứ đọc cùng cột kỳ ở trên. --%>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${ct.status == 'Sắp hết hạn'}"><span class="status-pill status-warn">Sắp hết hạn</span></c:when>
                                                    <c:when test="${ct.status == 'Đã hết hạn'}"><span class="status-pill status-danger">Đã hết hạn</span></c:when>
                                                    <c:when test="${ct.status == 'Chưa hiệu lực'}"><span class="status-pill status-gray">Chưa hiệu lực</span></c:when>
                                                    <c:otherwise><span class="status-pill status-success">Đang hiệu lực</span></c:otherwise>
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

            <div class="col-lg-6">
                <div class="card-box table-section h-100">
                    <div class="table-section-header">
                        <%-- Lọc ĐÚNG như ô KPI "Tổng khách hàng" phía trên: cùng phạm
                             vi người/địa bàn, cùng mốc "tham gia tính tới hết kỳ".
                             Hai chỗ lệch điều kiện thì ô đếm 5 mà bảng liệt kê 7. --%>
                        <h6>Khách hàng <span class="as-of-today">mới nhất trước</span></h6>
                        <a href="${pageContext.request.contextPath}/customer">Xem tất cả</a>
                    </div>
                    <div class="table-responsive">
                    <table class="mini-table t-customer">
                        <thead><tr><th>Khách hàng</th><th>Địa bàn</th><th>Loại</th><th>Tham gia</th></tr></thead>
                        <tbody>
                            <c:choose>
                                <c:when test="${empty scopeCustomers}">
                                    <tr><td colspan="4" style="text-align:center; color:#9ca3af; padding:20px 8px;">Chưa có khách hàng nào trong phạm vi này.</td></tr>
                                </c:when>
                                <c:otherwise>
                                    <c:forEach var="kh" items="${scopeCustomers}">
                                        <tr>
                                            <td><a href="${pageContext.request.contextPath}/customer?action=view&id=${kh.enterpriseId}" class="link">${fn:escapeXml(kh.enterpriseName)}</a></td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${kh.address.district.province != null}">${fn:escapeXml(kh.address.district.province.shortName)}</c:when>
                                                    <c:otherwise>&mdash;</c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${not empty kh.customerType}">${fn:escapeXml(kh.customerType)}</c:when>
                                                    <c:otherwise>&mdash;</c:otherwise>
                                                </c:choose>
                                            </td>
                                            <td>
                                                <c:choose>
                                                    <c:when test="${kh.joinDate != null}"><fmt:formatDate value="${kh.joinDate}" pattern="dd/MM/yyyy"/></c:when>
                                                    <c:otherwise>&mdash;</c:otherwise>
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
