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
        .scope-note {
            display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
            background: #eaf6ff; border: 1px solid #cfe8fb; border-radius: 10px;
            padding: 10px 16px; margin-bottom: 18px;
            font-size: 0.84rem; color: var(--primary-dark);
        }
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

        /* ===== Charts ===== */
        .chart-card { padding: 20px 22px 16px; }
        .chart-card .section-title { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin-bottom: 2px; }
        .chart-card .section-sub { font-size: 0.8rem; color: #9ca3af; margin-bottom: 16px; }

        .legend-row { display: flex; gap: 18px; justify-content: center; margin-top: 14px; flex-wrap: wrap; }
        .doughnut-wrap { max-width: 260px; margin: 0 auto; }
        .legend-item { display: flex; align-items: center; gap: 7px; font-size: 0.8rem; color: #6b7280; }
        .legend-dot { width: 9px; height: 9px; border-radius: 50%; }

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
        .mini-table a.link { color: var(--primary); font-weight: 600; text-decoration: none; }
        .mini-table a.link:hover { text-decoration: underline; }

        .status-pill { padding: 3px 10px; border-radius: 20px; font-size: 0.7rem; font-weight: 600; white-space: nowrap; }
        .status-danger { background: #fdecef; color: var(--danger); }
        .status-warn { background: #fff4e0; color: var(--warning); }
        .status-info { background: #eaf6ff; color: var(--primary); }
        .status-success { background: #e8faf3; color: var(--success); }
        .status-gray { background: #f3f4f6; color: #6b7280; }

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
                <p>Đây là tổng quan hoạt động kinh doanh và hỗ trợ kỹ thuật của bạn</p>
            </div>
            <div class="welcome-actions">
                <form method="GET" action="${pageContext.request.contextPath}/dashboard" id="provinceFilterForm">
                    <select id="filterProvince" name="provinceId" class="province-filter">
                        <option value="">Toàn bộ 18 tỉnh địa bàn</option>
                        <c:forEach var="province" items="${provinceList}">
                            <option value="${province.provinceId}" ${provinceFilter == province.provinceId ? 'selected' : ''}>${fn:escapeXml(province.shortName)}</option>
                        </c:forEach>
                    </select>
                    <select id="filterYear" name="year" class="province-filter">
                        <option value="">Mọi thời điểm</option>
                        <c:forEach var="y" items="${yearList}">
                            <option value="${y}" ${yearFilter == y ? 'selected' : ''}>Năm ${y}</option>
                        </c:forEach>
                    </select>
                    <select id="filterPeriod" name="period" class="province-filter">
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
                <div class="today-badge"><i class="fa-regular fa-calendar"></i>${todayLabel}</div>
            </div>
        </div>

        <c:if test="${not empty provinceFilter or not empty periodLabel}">
            <div class="scope-note">
                <i class="fa-solid fa-filter"></i>
                <span>
                    Số liệu đang thu hẹp theo<c:if test="${not empty provinceFilter}"> <strong>địa bàn đang chọn</strong></c:if><c:if test="${not empty provinceFilter and not empty periodLabel}"> và</c:if><c:if test="${not empty periodLabel}"> <strong>${fn:escapeXml(periodLabel)}</strong></c:if>.
                    Riêng hai bảng cuối trang luôn tính tới hôm nay.
                </span>
                <a href="${pageContext.request.contextPath}/dashboard">Bỏ lọc</a>
            </div>
        </c:if>

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

        <!-- ===== Biểu đồ + 2 bảng hành động (xếp chồng cùng cột, không để biểu đồ chen giữa) ===== -->
        <div class="row g-4">
            <div class="col-lg-4">
                <div class="card-box chart-card h-100">
                    <div class="section-title">Phiếu hỗ trợ theo trạng thái</div>
                    <div class="section-sub">Tổng số ${ticketStatusSummary['Mới tiếp nhận'] + ticketStatusSummary['Đang xử lý'] + ticketStatusSummary['Đã đóng']} phiếu hiện có</div>
                    <div class="doughnut-wrap"><canvas id="ticketChart" height="200"></canvas></div>
                    <div class="legend-row">
                        <div class="legend-item"><span class="legend-dot" style="background:var(--primary-light)"></span>Mới tiếp nhận (${ticketStatusSummary['Mới tiếp nhận']})</div>
                        <div class="legend-item"><span class="legend-dot" style="background:var(--warning)"></span>Đang xử lý (${ticketStatusSummary['Đang xử lý']})</div>
                        <div class="legend-item"><span class="legend-dot" style="background:var(--success)"></span>Đã đóng (${ticketStatusSummary['Đã đóng']})</div>
                    </div>
                </div>
            </div>

            <div class="col-lg-8 d-flex flex-column gap-4">
                <div class="card-box table-section">
                    <div class="table-section-header">
                        <h6>Hợp đồng sắp hết hạn <span class="as-of-today">tính tới hôm nay</span></h6>
                        <a href="${pageContext.request.contextPath}/contract">Xem tất cả</a>
                    </div>
                    <table class="mini-table">
                        <thead><tr><th>Mã HĐ</th><th>Khách hàng</th><th>Người phụ trách</th><th>Giá trị</th><th>Còn lại</th></tr></thead>
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
                                                    <c:when test="${ct.owner != null}">${fn:escapeXml(ct.owner.fullName)}</c:when>
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

                <div class="card-box table-section">
                    <div class="table-section-header">
                        <h6>Phiếu hỗ trợ cần xử lý <span class="as-of-today">tính tới hôm nay</span></h6>
                        <a href="${pageContext.request.contextPath}/ticket">Xem tất cả</a>
                    </div>
                    <table class="mini-table">
                        <thead><tr><th>Mã phiếu</th><th>Khách hàng</th><th>Người phụ trách</th><th>Ưu tiên</th><th>Trạng thái</th></tr></thead>
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
                                                    <c:when test="${tk.assignedTechnician != null}">${fn:escapeXml(tk.assignedTechnician.fullName)}</c:when>
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

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js"></script>
    <script>
        // Chọn tỉnh là nạp lại trang ngay, không cần nút "Lọc" -- giống bộ lọc
        // ở danh sách khách hàng/hợp đồng.
        ['filterProvince', 'filterYear', 'filterPeriod'].forEach(function (id) {
            document.getElementById(id).addEventListener('change', function () {
                document.getElementById('provinceFilterForm').submit();
            });
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

        // ===== Biểu đồ phiếu hỗ trợ theo trạng thái =====
        var ticketCtx = document.getElementById('ticketChart').getContext('2d');
        new Chart(ticketCtx, {
            type: 'doughnut',
            data: {
                labels: ['Mới tiếp nhận', 'Đang xử lý', 'Đã đóng'],
                datasets: [{
                    data: [${ticketStatusSummary['Mới tiếp nhận']}, ${ticketStatusSummary['Đang xử lý']}, ${ticketStatusSummary['Đã đóng']}],
                    backgroundColor: ['#0f9edb', '#f5a623', '#2fbf8f'],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                cutout: '68%',
                plugins: { legend: { display: false } }
            }
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
