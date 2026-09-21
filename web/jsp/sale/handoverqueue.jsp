<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Hàng đợi bàn giao -- do ContractController#showHandoverQueue forward tới.

    MỘT màn hình, hai người đọc:
      * Giám đốc: hợp đồng nào đang nằm ở phòng nào, bao nhiêu ngày, của nhân
        viên nào. Đây là câu hỏi sinh ra cả tính năng bàn giao.
      * Phòng nhận (Kế toán, Dự án): phần việc của phòng mình, đóng chặng ngay
        tại đây -- họ không có quyền ghi trên hợp đồng nên trang quản lý hợp
        đồng không phải chỗ của họ.

    Request attribute:
      - pendingHandovers           : List<ContractHandover> chưa xong, để lâu nhất trước
      - handoverCountByDepartment  : Map<tên phòng, số chặng đang giữ>
      - slowestHandoverDays        : số ngày của chặng để lâu nhất
      - canCompleteHandover_<id>   : cờ cho từng chặng, true khi người xem thuộc phòng đó
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Bàn giao xử lý - POSCS Portal</title>
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

        /* Chỉ đặt padding. Nền/bo góc/đổ bóng lấy từ .card-box của appshell.css --
           trang này từng khai lại cả ba với giá trị khác (bo 14px, bóng nhạt
           hơn), nên thẻ ở đây trông "phẳng" hơn hẳn thẻ ở mọi trang khác. */
        .card-box { padding: 18px 20px; }

        .dept-strip { display: flex; gap: 14px; margin-bottom: 20px; flex-wrap: wrap; }
        .dept-chip { flex: 1 1 200px; display: flex; align-items: center; gap: 12px; }
        .dept-chip .num { font-weight: 700; font-size: 1.15rem; color: #111827; }
        .dept-chip .lbl { font-size: 0.78rem; color: #6b7280; }
        .dept-chip .dot { width: 10px; height: 10px; border-radius: 50%; background: var(--primary); flex-shrink: 0; }
        /* Thẻ phòng giờ là <a> lọc. Bỏ gạch chân và trả màu chữ về mặc định --
           không có nó thì "Phòng Kế toán đang giữ" bị tô xanh gạch chân, đọc ra
           thành link đi tới trang khác chứ không phải một bộ lọc. */
        a.dept-chip { text-decoration: none; color: inherit; transition: border-color .12s, box-shadow .12s; border: 1px solid transparent; }
        a.dept-chip:hover { border-color: var(--primary-light); }
        a.dept-chip.on { border-color: var(--primary); box-shadow: 0 0 0 1px var(--primary) inset; }
        a.dept-chip.on .lbl { color: var(--primary-dark); font-weight: 600; }

        .scope-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; flex-wrap: wrap; }
        .scope-bar select { padding: 8px 10px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; font-size: 0.84rem; }
        .scope-bar select:focus { outline: none; border-color: var(--primary-light); }
        .scope-bar .hint { font-size: 0.82rem; color: #6b7280; }

        .queue-table { width: 100%; font-size: 0.9rem; }
        .queue-table th { font-size: 0.74rem; text-transform: uppercase; letter-spacing: .3px; color: #6b7280; padding: 10px 8px; border-bottom: 1px solid #eef2f6; }
        .queue-table td { padding: 12px 8px; border-bottom: 1px solid #f4f6f8; vertical-align: top; }
        /* Thang màu theo số ngày chờ: đây là thứ giám đốc quét mắt qua để tìm
           điểm nghẽn, nên nó phải nổi hơn mọi thứ khác trên dòng. */
        .waited { font-weight: 700; white-space: nowrap; }
        .waited.ok { color: #2f6b34; }
        .waited.warn { color: #8a5a00; }
        .waited.late { color: var(--danger); }

        .empty-state { text-align: center; padding: 40px 12px; color: #9ca3af; }
        .empty-state i { font-size: 2rem; margin-bottom: 10px; display: block; color: #cbd5e1; }
    </style>
</head>
<body>
    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="contract" scope="request"/>
        <c:set var="activeContractKind" value="handover" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>Bàn giao xử lý</h2>
                <p>Hợp đồng đang nằm chờ ở các phòng &mdash; để lâu nhất xếp trước.</p>
            </div>
            <a href="${pageContext.request.contextPath}/contract" class="btn-outline-action">
                <i class="fa-solid fa-arrow-left-long"></i> Danh sách hợp đồng
            </a>
        </div>

        <%-- Phạm vi. Mặc định TOÀN CHI NHÁNH, khác hai màn danh sách -- lý do ghi
             ở ContractController#showHandoverQueue: người phòng Kế toán/Dự án
             không đứng tên hợp đồng nào, nên "của tôi" với họ là màn hình trống.

             Giữ departmentId khi đổi phạm vi: đang soi phòng Kế toán mà bấm sang
             "của tôi" thì vẫn phải ở phòng Kế toán, không nhảy về tất cả. --%>
        <form class="scope-bar card-box" method="GET"
              action="${pageContext.request.contextPath}/contract" id="filterForm">
            <input type="hidden" name="action" value="handovers">
            <c:if test="${not empty departmentFilter}">
                <input type="hidden" name="departmentId" value="${departmentFilter}">
            </c:if>
            <i class="fa-solid fa-user-check" style="color:#9ca3af;"></i>
            <select name="view" id="filterView">
                <option value="all" ${viewFilter == 'all' ? 'selected' : ''}>Toàn chi nhánh</option>
                <option value="mine" ${viewFilter == 'mine' ? 'selected' : ''}>Hợp đồng của tôi</option>
            </select>
            <span class="hint">
                <c:choose>
                    <c:when test="${viewFilter == 'mine'}">Chỉ chặng của hợp đồng bạn và cấp dưới đứng tên.</c:when>
                    <c:otherwise>Mọi chặng đang chờ của chi nhánh.</c:otherwise>
                </c:choose>
            </span>
        </form>

        <%-- Dải tổng quan KIÊM công tắc lọc phòng: đây là câu hỏi hằng ngày của
             người phòng Kế toán/Dự án ("việc của phòng tôi đâu"), mà trước đây
             họ phải tự dò giữa các dòng của mọi phòng.

             Số đếm tính trên tập đã áp phạm vi nhưng CHƯA áp lọc phòng -- lọc
             luôn cả nó thì chọn Kế toán xong là mất số của Dự án, không còn
             đường bấm sang. Xem ghi chú ở controller.

             Lặp theo departmentList (thứ tự bảng departments, ổn định) rồi bỏ
             phòng đang giữ 0 chặng, thay vì lặp thẳng map đếm: ở đây cần cả id
             để dựng link lẫn tên để tra số. --%>
        <c:if test="${not empty handoverCountByDepartment}">
            <div class="dept-strip">
                <a class="card-box dept-chip ${empty departmentFilter ? 'on' : ''}"
                   href="${pageContext.request.contextPath}/contract?action=handovers&view=${viewFilter}">
                    <span class="dot" style="background:var(--primary)"></span>
                    <div>
                        <div class="num">${scopeTotal}</div>
                        <div class="lbl">Tất cả các phòng</div>
                    </div>
                </a>
                <c:forEach var="dept" items="${departmentList}">
                    <c:set var="cnt" value="${handoverCountByDepartment[dept.departmentName]}"/>
                    <c:if test="${not empty cnt}">
                        <a class="card-box dept-chip ${departmentFilter == dept.departmentId ? 'on' : ''}"
                           href="${pageContext.request.contextPath}/contract?action=handovers&view=${viewFilter}&departmentId=${dept.departmentId}">
                            <span class="dot"></span>
                            <div>
                                <div class="num">${cnt}</div>
                                <div class="lbl">Phòng ${fn:escapeXml(dept.departmentName)} đang giữ</div>
                            </div>
                        </a>
                    </c:if>
                </c:forEach>
                <%-- Không phải công tắc nên vẫn là div: nó là một con số để đọc,
                     không có "hàng đợi của chặng lâu nhất" nào để lọc ra. --%>
                <div class="card-box dept-chip">
                    <span class="dot" style="background:var(--danger)"></span>
                    <div>
                        <div class="num">${slowestHandoverDays} ngày</div>
                        <div class="lbl">Chặng để lâu nhất</div>
                    </div>
                </div>
            </div>
        </c:if>

        <div class="card-box">
            <c:choose>
                <c:when test="${empty pendingHandovers}">
                    <div class="empty-state">
                        <i class="fa-regular fa-circle-check"></i>
                        Không có hợp đồng nào đang chờ xử lý.
                    </div>
                </c:when>
                <c:otherwise>
                    <div class="table-responsive">
                        <table class="queue-table">
                            <thead>
                                <tr>
                                    <th>Hợp đồng</th>
                                    <th>Phòng đang giữ</th>
                                    <th>Bàn giao</th>
                                    <th>Đã chờ</th>
                                    <th>Xác nhận xong</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="hv" items="${pendingHandovers}">
                                    <tr>
                                        <td>
                                            <a href="${pageContext.request.contextPath}/contract?action=view&id=${hv.contractId}"
                                               style="color:var(--primary); font-weight:600; text-decoration:none;">
                                                ${fn:escapeXml(hv.contractCode)}
                                            </a>
                                            <div style="font-size:0.8rem; color:#6b7280;">${fn:escapeXml(hv.contractTitle)}</div>
                                            <%-- Người phụ trách đứng ngay đây: câu hỏi của giám đốc là
                                                 "tiến độ hợp đồng CỦA NHÂN VIÊN nào", không phải
                                                 "hợp đồng nào" nói chung. --%>
                                            <div style="font-size:0.78rem; color:#9ca3af;">
                                                Người phụ trách: ${fn:escapeXml(hv.ownerName)}
                                            </div>
                                        </td>
                                        <td><strong>${fn:escapeXml(hv.departmentName)}</strong></td>
                                        <td>
                                            <fmt:formatDate value="${hv.handedAt}" pattern="dd/MM/yyyy"/>
                                            <div style="font-size:0.78rem; color:#9ca3af;">${fn:escapeXml(hv.handedByName)}</div>
                                            <c:if test="${not empty hv.handoverNote}">
                                                <div style="font-size:0.8rem; color:#6b7280;">${fn:escapeXml(hv.handoverNote)}</div>
                                            </c:if>
                                        </td>
                                        <td>
                                            <span class="waited ${hv.daysWaiting >= 14 ? 'late' : (hv.daysWaiting >= 7 ? 'warn' : 'ok')}">
                                                ${hv.daysWaiting} ngày
                                            </span>
                                        </td>
                                        <td style="min-width:280px;">
                                            <c:choose>
                                                <%-- Nút chỉ mọc ở phòng của chính người đang xem; chốt
                                                     chặn thật nằm ở AccessControl.canCompleteHandover. --%>
                                                <c:when test="${requestScope['canCompleteHandover_'.concat(hv.handoverId)]}">
                                                    <form method="POST" action="${pageContext.request.contextPath}/contract" class="row g-2">
                                                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                                                        <input type="hidden" name="action" value="completeHandover">
                                                        <input type="hidden" name="contractId" value="${hv.contractId}">
                                                        <input type="hidden" name="handoverId" value="${hv.handoverId}">
                                                        <input type="hidden" name="departmentId" value="${hv.departmentId}">
                                                        <input type="hidden" name="returnTo" value="from-queue">
                                                        <%-- Bộ lọc đang bật, để quay về đúng chỗ vừa đứng.
                                                             Tên khác "departmentId" vì ô đó đã mang phòng CỦA
                                                             CHẶNG này (canCompleteHandover đọc nó) -- trùng tên
                                                             là hai nghĩa đè lên nhau trên cùng một form. --%>
                                                        <input type="hidden" name="view" value="${viewFilter}">
                                                        <c:if test="${not empty departmentFilter}">
                                                            <input type="hidden" name="queueDepartmentId" value="${departmentFilter}">
                                                        </c:if>
                                                        <div class="col-7">
                                                            <input type="text" name="doneNote" class="form-control" required
                                                                   maxlength="500" placeholder="Đã làm gì? (bắt buộc)">
                                                        </div>
                                                        <div class="col-5">
                                                            <button type="submit" class="btn-primary" style="width:100%;">
                                                                <i class="fa-solid fa-check me-1"></i> Xong
                                                            </button>
                                                        </div>
                                                    </form>
                                                </c:when>
                                                <c:otherwise>
                                                    <span style="font-size:0.82rem; color:#9ca3af;">
                                                        Chỉ người của phòng ${fn:escapeXml(hv.departmentName)} xác nhận được
                                                    </span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
    <script>
        // Đổi ô phạm vi là nạp lại luôn, không cần nút "Áp dụng" -- cùng cách
        // hai màn danh sách đang làm. Form vẫn là GET bình thường nên tắt JS
        // thì bấm Enter trong ô vẫn gửi được.
        (function () {
            var el = document.getElementById('filterView');
            if (el) { el.addEventListener('change', function () { document.getElementById('filterForm').submit(); }); }
        })();
    </script>
</body>
</html>
