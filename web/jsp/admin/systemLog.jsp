<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Servlet cần đặt các request attribute sau trước khi forward tới trang này:
      - logDirectory : String đường dẫn thư mục log, hoặc null nếu chưa xác định được
      - logFiles     : List<poscs.common.LogFiles.LogFile>
      - selectedFile : tên file đang xem (null nếu không có file nào)
      - lines        : số dòng cuối đang hiện
      - keyword      : từ khoá lọc hiện tại (có thể null)
      - logContent   : List<String> các dòng log đã lọc

    Toàn bộ nội dung log PHẢI escape khi in ra: log có chứa nguyên văn dữ liệu
    người dùng nhập (tên khách hàng, mô tả sự cố...), nên một dòng log là chỗ
    hoàn toàn có thể mang theo <script>.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Nhật ký hệ thống - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1240px; margin: 28px auto; padding: 0 24px 32px; }
        .page-header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 22px; flex-wrap: wrap; gap: 14px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .page-header-row code { color: #374151; background: #f3f4f6; padding: 2px 6px; border-radius: 6px; font-size: 0.82rem; }

        .filter-bar { display: flex; gap: 12px; align-items: center; padding: 14px 18px; flex-wrap: wrap; margin-bottom: 18px; }
        .filter-bar select, .filter-bar input[type="text"] {
            border: 1px solid #e5e7eb; border-radius: 10px; padding: 8px 12px; font-size: 0.88rem; background: #fff;
        }
        .filter-bar .grow { flex: 1; min-width: 220px; }
        .btn-plain {
            border: 1px solid #e5e7eb; background: #fff; color: #374151; border-radius: 10px;
            padding: 8px 14px; font-size: 0.86rem; font-weight: 600; text-decoration: none; display: inline-flex; gap: 6px; align-items: center;
        }
        .btn-plain:hover { background: #f9fafb; color: var(--primary-dark); }
        .btn-download {
            background: var(--primary); color: #fff; border: none; border-radius: 10px; padding: 9px 16px;
            font-size: 0.88rem; font-weight: 600; text-decoration: none; display: inline-flex; gap: 8px; align-items: center;
        }
        .btn-download:hover { background: var(--primary-dark); color: #fff; }

        .log-box {
            background: #0f172a; color: #e2e8f0; border-radius: 12px; padding: 16px 18px;
            font-family: Consolas, "Courier New", monospace; font-size: 0.82rem; line-height: 1.55;
            max-height: 62vh; overflow: auto; white-space: pre; tab-size: 4;
        }
        .log-line { display: block; }
        .log-line.level-error { color: #fca5a5; }
        .log-line.level-warn { color: #fcd34d; }
        .log-line.level-info { color: #93c5fd; }
        .log-empty { color: #6b7280; font-size: 0.9rem; padding: 10px 0; }
        .log-meta { font-size: 0.82rem; color: #6b7280; margin: 10px 2px 0; }

        @media (max-width: 768px) { .page-container { padding: 0 14px 32px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="systemLog" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">

    <div class="page-container">
        <div class="page-header-row">
            <div>
                <h2>Nhật ký hệ thống</h2>
                <c:choose>
                    <c:when test="${empty logDirectory}">
                        <p>Chưa xác định được thư mục log của máy chủ.</p>
                    </c:when>
                    <c:otherwise>
                        <p>Thư mục: <code><c:out value="${logDirectory}"/></code></p>
                    </c:otherwise>
                </c:choose>
            </div>
            <c:if test="${not empty selectedFile}">
                <a class="btn-download"
                   href="${pageContext.request.contextPath}/systemLog?action=download&file=${fn:escapeXml(selectedFile)}">
                    <i class="fa-solid fa-download"></i> Tải file này về
                </a>
            </c:if>
        </div>

        <c:if test="${param.error == 'notfound'}">
            <div class="alert alert-warning py-2">Không tìm thấy file log đó. Danh sách bên dưới là các file đang có.</div>
        </c:if>

        <c:choose>
            <c:when test="${empty logFiles}">
                <%-- Không có file nào: nói rõ vì sao, đừng để trang trống trơn. --%>
                <div class="card-box">
                    <div class="log-empty">
                        <c:choose>
                            <c:when test="${empty logDirectory}">
                                Máy chủ không cho biết thư mục log (không chạy trên Tomcat, hoặc chưa đặt biến
                                <code>LOG_DIR</code>). Đặt <code>LOG_DIR</code> trỏ tới thư mục chứa log rồi tải lại trang.
                            </c:when>
                            <c:otherwise>
                                Thư mục log không có file <code>.log</code> / <code>.out</code> nào.
                            </c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </c:when>
            <c:otherwise>
                <form class="filter-bar card-box" method="GET" action="${pageContext.request.contextPath}/systemLog" id="filterForm">
                    <select name="file" id="fileSelect">
                        <c:forEach var="f" items="${logFiles}">
                            <option value="${fn:escapeXml(f.name)}" ${selectedFile == f.name ? 'selected' : ''}>
                                <c:out value="${f.name}"/> (<c:out value="${f.sizeText}"/>)
                            </option>
                        </c:forEach>
                    </select>
                    <select name="lines" id="linesSelect">
                        <c:forEach var="n" items="${[100, 200, 500, 1000, 2000]}">
                            <option value="${n}" ${lines == n ? 'selected' : ''}>${n} dòng cuối</option>
                        </c:forEach>
                    </select>
                    <input type="text" class="grow" name="keyword" value="${fn:escapeXml(keyword)}"
                           placeholder="Lọc theo từ khoá (vd: ERROR, Loi cap nhat, sales1)">
                    <button type="submit" class="btn-plain"><i class="fa-solid fa-magnifying-glass"></i> Xem</button>
                    <a class="btn-plain" href="${pageContext.request.contextPath}/systemLog?file=${fn:escapeXml(selectedFile)}&lines=${lines}">
                        <i class="fa-solid fa-rotate-right"></i> Tải lại
                    </a>
                </form>

                <div class="card-box">
                    <c:choose>
                        <c:when test="${empty logContent}">
                            <div class="log-empty">
                                <c:choose>
                                    <c:when test="${not empty keyword}">Không có dòng nào khớp từ khoá trong phạm vi đang xem.</c:when>
                                    <c:otherwise>File này đang rỗng.</c:otherwise>
                                </c:choose>
                            </div>
                        </c:when>
                        <c:otherwise>
                            <div class="log-box" id="logBox"><c:forEach var="line" items="${logContent}"><%--
                                --%><c:set var="level" value=""/><%--
                                --%><c:if test="${fn:contains(line, ' ERROR ')}"><c:set var="level" value=" level-error"/></c:if><%--
                                --%><c:if test="${empty level and fn:contains(line, ' WARN ')}"><c:set var="level" value=" level-warn"/></c:if><%--
                                --%><c:if test="${empty level and fn:contains(line, ' INFO ')}"><c:set var="level" value=" level-info"/></c:if><%--
                                --%><span class="log-line${level}">${fn:escapeXml(line)}</span></c:forEach></div>
                            <div class="log-meta">
                                Đang hiện <c:out value="${fn:length(logContent)}"/> dòng
                                <c:if test="${not empty keyword}">khớp từ khoá "<c:out value="${keyword}"/>" </c:if>
                                trong ${lines} dòng cuối của <c:out value="${selectedFile}"/>.
                            </div>
                        </c:otherwise>
                    </c:choose>
                </div>
            </c:otherwise>
        </c:choose>
    </div>

        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Đổi file hoặc số dòng thì nạp lại luôn, khỏi phải bấm "Xem".
        ['fileSelect', 'linesSelect'].forEach(function (id) {
            var el = document.getElementById(id);
            if (el) { el.addEventListener('change', function () { document.getElementById('filterForm').submit(); }); }
        });
        // Log đọc từ dưới lên: mở trang là nhảy thẳng xuống dòng mới nhất.
        var box = document.getElementById('logBox');
        if (box) { box.scrollTop = box.scrollHeight; }
    </script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
