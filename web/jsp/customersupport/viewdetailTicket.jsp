<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do TechnicalSupportTicketController#showDetail thiết
    lập trước khi forward tới trang này:
      - ticket        : poscs.model.TechnicalRequest (có sẵn .enterprise, .contract,
                        .assignedTechnician, .createdByUser đã join nếu tồn tại)
      - ticketHistory : List<poscs.model.TechnicalRequestHistory>, mới nhất trước
      - canDelete     : boolean -- true nếu phiếu chưa có ai xử lý dở dang (status != "Đang xử lý")
      - canEdit       : boolean -- quyền Full, HOẶC kỹ thuật viên đang được giao phiếu này
      - canManage     : boolean -- quyền Full (nút Xóa)

    technicalrequestdevices (thiết bị lỗi) chưa hiển thị -- thuộc phạm vi khác.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Chi tiết phiếu hỗ trợ - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        /* ===== Modal xác nhận (giá trị lấy đúng của viewcustomerdetail.jsp) ===== */
        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 24px 24px 0; }
        .modal-body { padding: 12px 24px 6px; color: #374151; font-size: 0.92rem; }
        .modal-footer { border-top: none; padding: 18px 24px 24px; }
        .btn-modal-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-danger { background: var(--danger); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .modal-icon-warn { width: 52px; height: 52px; border-radius: 50%; background: #fdecef; color: var(--danger); display: flex; align-items: center; justify-content: center; font-size: 1.3rem; margin-bottom: 4px; }
        .modal-title { font-weight: 700; color: var(--primary-dark); font-size: 1.05rem; }

        .page-container { max-width: 1080px; margin: 28px auto; padding: 0 20px 32px; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 16px; }
        .back-link-top:hover { text-decoration: underline; }

        .detail-header { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 16px; padding: 22px 26px; margin-bottom: 20px; }
        .detail-header .doc-icon {
            width: 56px; height: 56px; border-radius: 14px;
            background: linear-gradient(120deg, var(--primary-dark), var(--primary-light));
            color: #fff; display: flex; align-items: center; justify-content: center; font-size: 1.4rem; flex-shrink: 0;
        }
        .detail-header .doc-info { display: flex; gap: 16px; align-items: center; }
        .detail-header h2 { font-weight: 700; color: #111827; font-size: 1.2rem; margin-bottom: 4px; }
        .ticket-code { color: var(--primary); font-weight: 700; font-size: 0.82rem; }
        .type-badge { display: inline-block; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; background: #f3f4f6; color: #4b5563; margin-left: 8px; }

        .pill { display: inline-flex; align-items: center; gap: 5px; padding: 3px 11px; border-radius: 20px; font-size: 0.72rem; font-weight: 600; white-space: nowrap; margin-left: 8px; }
        .pill .dot { width: 6px; height: 6px; border-radius: 50%; }
        .priority-urgent { background: #fdecef; color: var(--danger); } .priority-urgent .dot { background: var(--danger); }
        .priority-high { background: #fff4e0; color: var(--warning); } .priority-high .dot { background: var(--warning); }
        .priority-normal { background: #eaf6ff; color: var(--primary); } .priority-normal .dot { background: var(--primary); }
        .priority-low { background: #eef2f6; color: #6b7280; } .priority-low .dot { background: #9ca3af; }
        .status-new { background: #eaf6ff; color: var(--primary); } .status-new .dot { background: var(--primary); }
        .status-progress { background: #fff4e0; color: var(--warning); } .status-progress .dot { background: var(--warning); }
        .status-closed { background: #e8faf3; color: var(--success); } .status-closed .dot { background: var(--success); }
        /* Cảnh báo hạn xử lý: cùng màu và cùng ngưỡng với danh sách phiếu. */
        .sla-overdue { background: #fdecec; color: var(--danger, #d92d20); } .sla-overdue .dot { background: var(--danger, #d92d20); }
        .sla-soon { background: #fff4e0; color: var(--warning); } .sla-soon .dot { background: var(--warning); }

        .header-actions { display: flex; gap: 10px; }
        .btn-edit-detail {
            background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; border: none;
            border-radius: 10px; padding: 9px 18px; font-weight: 600; font-size: 0.87rem; text-decoration: none;
            display: inline-flex; align-items: center; gap: 8px; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3);
        }
        .btn-edit-detail:hover { color: #fff; background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-delete-detail {
            background: #fff; border: 1.5px solid #e5e7eb; color: #9ca3af;
            border-radius: 10px; padding: 9px 16px; font-weight: 600; font-size: 0.87rem;
            display: inline-flex; align-items: center; gap: 8px; cursor: not-allowed;
        }

        .info-card { padding: 22px 26px 26px; margin-bottom: 20px; }
        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 0 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        /* Tên người viết ra nội dung của khối, nằm bên phải tiêu đề khối. */
        .author-tag {
            font-size: 0.78rem; font-weight: 600; color: #6b7280;
            display: inline-flex; align-items: center; gap: 6px; white-space: nowrap;
        }
        .author-tag i { color: #9ca3af; }
        /* Nhóm nguyên nhân bám vào nhãn "Nguyên nhân sự cố". Nhãn field-row
           viết hoa toàn bộ, nên phải trả text-transform về none -- không thì
           "Do lắp đặt" hiện thành "DO LẮP ĐẶT", lệch hẳn với chính giá trị
           đang có trong ô chọn ở form sửa. */
        .cause-tag {
            display: inline-block; margin-left: 8px; padding: 2px 10px;
            border-radius: 20px; font-size: 0.7rem; font-weight: 600;
            background: #eef2ff; color: #4338ca;
            text-transform: none; letter-spacing: 0;
        }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row .view-value {
            font-size: 0.95rem; color: #111827; font-weight: 500; min-height: 40px; display: flex; align-items: center;
            border: 1px solid #eef2f6; background: #f9fafb; border-radius: 10px; padding: 8px 14px;
        }
        .field-row .view-value a { color: var(--primary); font-weight: 600; text-decoration: none; }
        .field-row .view-value a:hover { text-decoration: underline; }
        .field-row .view-value.text-block { min-height: 80px; align-items: flex-start; white-space: pre-wrap; }

        /* Lịch sử đổi trạng thái: mốc thời gian dọc, mới nhất trên cùng. */
        .history-list { list-style: none; margin: 0; padding: 0; }
        .history-item { position: relative; padding: 0 0 18px 22px; border-left: 2px solid #eef2f6; }
        .history-item:last-child { padding-bottom: 0; border-left-color: transparent; }
        .history-item::before {
            content: ''; position: absolute; left: -6px; top: 4px; width: 10px; height: 10px;
            border-radius: 50%; background: var(--primary); border: 2px solid #fff;
        }
        .history-transition { font-size: 0.92rem; font-weight: 600; color: #111827; }
        .history-transition .arrow { color: #9ca3af; margin: 0 6px; }
        .history-meta { font-size: 0.8rem; color: #6b7280; margin-top: 2px; }
        .history-note { font-size: 0.88rem; color: #374151; margin-top: 6px; white-space: pre-wrap;
            background: #f9fafb; border: 1px solid #eef2f6; border-radius: 8px; padding: 8px 12px; }
        .history-empty { font-size: 0.9rem; color: #6b7280; }

        @media (max-width: 768px) {
            .info-card, .detail-header { padding: 16px; }
            /* Màn hẹp: tiêu đề khối và tên người viết không đủ chỗ nằm cùng
               dòng, cho xuống hàng thay vì để tên bị đẩy tràn ra ngoài thẻ. */
            .section-header { flex-wrap: wrap; gap: 6px; }
            .author-tag { white-space: normal; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="ticket" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/ticket" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại danh sách</a>

        <%-- Xoá bị từ chối (phiếu đang xử lý) thì controller đưa về ĐÚNG trang
             này kèm ?error=cannot_delete. Trước đây trang không đọc tham số đó:
             bấm xoá từ danh sách xong là rơi vào trang chi tiết, không một dòng
             nào nói vì sao phiếu vẫn còn. --%>
        <c:if test="${param.error == 'cannot_delete'}">
            <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                <i class="fa-solid fa-circle-exclamation me-1"></i>
                Không xoá được phiếu này vì phiếu đang có người xử lý (trạng thái <strong>Đang xử lý</strong>). Chỉ xoá được phiếu Mới tiếp nhận hoặc Đã đóng.
            </div>
        </c:if>

        <!-- ===== Header ===== -->
        <div class="detail-header card-box">
            <div class="doc-info">
                <div class="doc-icon"><i class="fa-solid fa-headset"></i></div>
                <div>
                    <span class="ticket-code">${fn:escapeXml(ticket.ticketCode)}</span>
                    <h2>
                        ${fn:escapeXml(ticket.ticketType)}
                        <span class="type-badge"><c:if test="${ticket.warranty}">Còn bảo hành</c:if><c:if test="${!ticket.warranty}">Hết bảo hành</c:if></span>
                        <c:choose>
                            <c:when test="${ticket.priority == 'Khẩn cấp'}"><span class="pill priority-urgent"><span class="dot"></span>Khẩn cấp</span></c:when>
                            <c:when test="${ticket.priority == 'Cao'}"><span class="pill priority-high"><span class="dot"></span>Cao</span></c:when>
                            <c:when test="${ticket.priority == 'Thấp'}"><span class="pill priority-low"><span class="dot"></span>Thấp</span></c:when>
                            <c:otherwise><span class="pill priority-normal"><span class="dot"></span>Bình thường</span></c:otherwise>
                        </c:choose>
                        <c:choose>
                            <c:when test="${ticket.status == 'Đang xử lý'}"><span class="pill status-progress"><span class="dot"></span>Đang xử lý</span></c:when>
                            <c:when test="${ticket.status == 'Đã đóng'}"><span class="pill status-closed"><span class="dot"></span>Đã đóng</span></c:when>
                            <c:otherwise><span class="pill status-new"><span class="dot"></span>Mới tiếp nhận</span></c:otherwise>
                        </c:choose>
                    </h2>
                    <div style="color:#6b7280; font-size:0.85rem;">Khách hàng:
                        <a href="${pageContext.request.contextPath}/customer?action=view&id=${ticket.enterpriseId}" style="color:var(--primary); font-weight:600; text-decoration:none;">
                            <c:choose>
                                <c:when test="${ticket.enterprise != null}">${fn:escapeXml(ticket.enterprise.enterpriseName)}</c:when>
                                <c:otherwise>&mdash;</c:otherwise>
                            </c:choose>
                        </a>
                    </div>
                </div>
            </div>
            <div class="header-actions">
                <a href="${pageContext.request.contextPath}/ticket?action=exportPdf&id=${ticket.ticketId}" class="btn-delete-detail" style="cursor:pointer; color:var(--primary); border-color:#e5e7eb; text-decoration:none;"><i class="fa-solid fa-file-pdf"></i> Xuất phiếu</a>
                <c:if test="${canEdit}">
                    <a href="${pageContext.request.contextPath}/ticket?action=edit&id=${ticket.ticketId}" class="btn-edit-detail"><i class="fa-solid fa-pen"></i> Sửa thông tin</a>
                </c:if>
                <c:if test="${canManage}">
                    <c:choose>
                        <c:when test="${canDelete}">
                            <button type="button" class="btn-delete-detail" style="cursor:pointer; color:var(--danger); border-color:var(--danger);" onclick="confirmDelete(${ticket.ticketId})"><i class="fa-solid fa-trash"></i> Xóa</button>
                        </c:when>
                        <c:otherwise>
                            <button class="btn-delete-detail" disabled title="Không thể xóa phiếu đang có người xử lý dở dang"><i class="fa-solid fa-trash"></i> Xóa</button>
                        </c:otherwise>
                    </c:choose>
                </c:if>
            </div>
        </div>

        <!-- ===== Thông tin chung ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Thông tin chung</h5></div>
            <div class="row">
                <div class="col-md-6 field-row">
                    <label>Hợp đồng liên quan</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.contract != null}">
                                <a href="${pageContext.request.contextPath}/contract?action=view&id=${ticket.contractId}">${fn:escapeXml(ticket.contract.contractCode)}</a>
                            </c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Kênh tiếp nhận</label>
                    <div class="view-value">${fn:escapeXml(ticket.receptionChannel)}</div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Kỹ thuật viên phụ trách</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.assignedTechnician != null}">${fn:escapeXml(ticket.assignedTechnician.fullName)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-6 field-row">
                    <label>Người tạo phiếu</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.createdByUser != null}">${fn:escapeXml(ticket.createdByUser.fullName)}</c:when>
                            <c:otherwise>&mdash;</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <%-- Ba mốc thời gian của phiếu đứng chung một hàng. Hạn xử lý trước
                     đây chỉ thấy trong form sửa và bản PDF -- kỹ thuật viên xem
                     phiếu không biết mình còn bao lâu, dù danh sách đã gắn nhãn
                     "Quá hạn SLA" cho chính phiếu đó. --%>
                <div class="col-md-4 field-row">
                    <label>Ngày tạo</label>
                    <div class="view-value"><fmt:formatDate value="${ticket.createdDate}" pattern="dd/MM/yyyy"/></div>
                </div>
                <div class="col-md-4 field-row">
                    <label>Hạn xử lý (SLA)</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.slaDeadline != null}">
                                <fmt:formatDate value="${ticket.slaDeadline}" pattern="dd/MM/yyyy HH:mm"/>
                                <c:if test="${ticket.slaOverdue}"><span class="pill sla-overdue" title="Đã quá hạn xử lý theo SLA"><span class="dot"></span>Quá hạn SLA</span></c:if>
                                <c:if test="${ticket.slaDueSoon}"><span class="pill sla-soon" title="Còn dưới 24 giờ tới hạn SLA"><span class="dot"></span>Sắp tới hạn</span></c:if>
                            </c:when>
                            <c:otherwise>Không đặt hạn</c:otherwise>
                        </c:choose>
                    </div>
                </div>
                <div class="col-md-4 field-row">
                    <label>Thời điểm hoàn tất</label>
                    <div class="view-value">
                        <c:choose>
                            <c:when test="${ticket.resolvedAt != null}"><fmt:formatDate value="${ticket.resolvedAt}" pattern="dd/MM/yyyy HH:mm"/></c:when>
                            <c:otherwise>Chưa xử lý xong</c:otherwise>
                        </c:choose>
                    </div>
                </div>
            </div>
        </div>

        <%--
          Nội dung của phiếu chia làm HAI khối theo NGƯỜI VIẾT RA NÓ, không
          phải theo thứ tự thời gian:

            - Khối 1 là lời của người tiếp nhận: khách báo hỏng cái gì.
            - Khối 2 là đánh giá của kỹ thuật viên: vì sao hỏng và đã làm gì.

          Trước đây mô tả / nguyên nhân / kết quả nằm thành ba thẻ rời ngang
          hàng nhau, đọc lướt không biết dòng nào do ai viết -- mà đây đúng là
          câu hỏi người theo dõi phiếu cần trả lời đầu tiên. Gộp theo tác giả
          cũng trùng khít với phân quyền: đúng những trường trong khối 2 là
          những trường role Kỹ thuật được sửa trên phiếu của mình (xem
          PERMISSIONS.md), khối 1 thì không.

          Lịch sử xử lý vẫn đứng riêng bên dưới: nó do hệ thống ghi mỗi lần
          đổi trạng thái, không phải do ai gõ vào, nên không thuộc khối nào.
        --%>

        <!-- ===== Khối 1: người tạo phiếu ghi nhận ===== -->
        <div class="info-card card-box">
            <div class="section-header">
                <h5>Người tạo phiếu ghi nhận</h5>
                <span class="author-tag">
                    <i class="fa-regular fa-user"></i>
                    <c:choose>
                        <c:when test="${ticket.createdByUser != null}">${fn:escapeXml(ticket.createdByUser.fullName)}</c:when>
                        <c:otherwise>Không rõ</c:otherwise>
                    </c:choose>
                    <c:if test="${ticket.createdDate != null}">
                        &middot; <fmt:formatDate value="${ticket.createdDate}" pattern="dd/MM/yyyy"/>
                    </c:if>
                </span>
            </div>
            <div class="field-row" style="margin-bottom: 0;">
                <label>Mô tả sự cố</label>
                <div class="view-value text-block">${fn:escapeXml(ticket.description)}</div>
            </div>
        </div>

        <!-- ===== Khối 2: kỹ thuật viên xử lý đánh giá ===== -->
        <div class="info-card card-box">
            <div class="section-header">
                <h5>Nhân viên kỹ thuật xử lý</h5>
                <span class="author-tag">
                    <i class="fa-solid fa-screwdriver-wrench"></i>
                    <c:choose>
                        <c:when test="${ticket.assignedTechnician != null}">${fn:escapeXml(ticket.assignedTechnician.fullName)}</c:when>
                        <c:otherwise>Chưa phân công</c:otherwise>
                    </c:choose>
                </span>
            </div>
            <%-- Ba mục theo đúng mạch khách yêu cầu: nguyên nhân -> phương
                 hướng xử lý -> kết quả. Mỗi mục một ô riêng, KHÔNG gộp vào
                 nhau và cũng không kể lại trong "Lịch sử xử lý" bên dưới --
                 lịch sử chỉ trả lời "vì sao lúc đó đổi trạng thái".

                 Nhóm nguyên nhân là giá trị ngắn chọn từ danh sách nên bám
                 vào nhãn, cho nó xuống dòng riêng chỉ tốn một khoảng trống. --%>
            <%-- Giá trị viết thẳng bằng EL, KHÔNG tách <c:choose> ra nhiều
                 dòng: ô này để white-space: pre-wrap (cần thiết, để giữ xuống
                 dòng người dùng gõ), nên chính khoảng trắng thụt lề của mã
                 JSP cũng bị hiển thị ra -- chữ tụt vào giữa ô như bị canh lề
                 lung tung. Bản cũ đã dính đúng lỗi này. --%>
            <div class="field-row">
                <label>Nguyên nhân sự cố<c:if test="${not empty ticket.causeCategory}"><span class="cause-tag">${fn:escapeXml(ticket.causeCategory)}</span></c:if></label>
                <div class="view-value text-block">${not empty ticket.rootCause ? fn:escapeXml(ticket.rootCause) : 'Kỹ thuật viên chưa đánh giá nguyên nhân.'}</div>
            </div>
            <div class="field-row">
                <label>Phương hướng xử lý</label>
                <div class="view-value text-block">${not empty ticket.handlingPlan ? fn:escapeXml(ticket.handlingPlan) : 'Kỹ thuật viên chưa đưa ra phương hướng xử lý.'}</div>
            </div>
            <div class="field-row" style="margin-bottom: 0;">
                <label>Kết quả xử lý</label>
                <div class="view-value text-block">${not empty ticket.resolutionSummary ? fn:escapeXml(ticket.resolutionSummary) : 'Chưa xử lý xong.'}</div>
            </div>
        </div>

        <!-- ===== Lịch sử xử lý ===== -->
        <div class="info-card card-box">
            <div class="section-header"><h5>Lịch sử xử lý</h5></div>
            <c:choose>
                <c:when test="${empty ticketHistory}">
                    <%-- Phiếu tạo xong mà chưa ai đổi trạng thái thì chưa có dòng nào --
                         nói rõ là "chưa có", đừng để khoảng trắng khiến người xem tưởng lỗi. --%>
                    <div class="history-empty">Phiếu chưa đổi trạng thái lần nào kể từ lúc tạo.</div>
                </c:when>
                <c:otherwise>
                    <ul class="history-list">
                        <c:forEach var="h" items="${ticketHistory}">
                            <li class="history-item">
                                <%--
                                  from_status rỗng = dòng đánh dấu lúc lập phiếu
                                  (TechnicalSupportTicketDAO.insert ghi dòng này,
                                  dữ liệu mẫu cũng vậy). Hiện "Tạo phiếu" thay vì
                                  để mũi tên mọc ra từ khoảng trắng.
                                --%>
                                <div class="history-transition">
                                    <c:choose>
                                        <c:when test="${empty h.fromStatus}">Tạo phiếu</c:when>
                                        <c:otherwise><c:out value="${h.fromStatus}"/></c:otherwise>
                                    </c:choose>
                                    <span class="arrow">&rarr;</span><c:out value="${h.toStatus}"/>
                                </div>
                                <div class="history-meta">
                                    <fmt:formatDate value="${h.changedAt}" pattern="dd/MM/yyyy HH:mm"/>
                                    &middot; <c:out value="${h.changedByUser.fullName}"/>
                                </div>
                                <c:if test="${not empty h.internalNote}">
                                    <div class="history-note">${fn:escapeXml(h.internalNote)}</div>
                                </c:if>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </div>
    </div>

        </div>
    </div>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/ticket" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" id="deleteFormId">
    </form>

    <%-- Hỏi bằng modal trong trang, KHÔNG dùng confirm() của trình duyệt: hộp đó
         không theo giao diện phần mềm và không đặt được nhãn tiếng Việt cho nút,
         nên người dùng đọc "OK / Cancel" cho một thao tác xoá. --%>
    <div class="modal fade" id="deleteModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <div class="modal-icon-warn"><i class="fa-solid fa-triangle-exclamation"></i></div>
                </div>
                <div class="modal-body">
                    <h5 class="modal-title">Xoá phiếu hỗ trợ</h5>
                    <div style="margin-top:8px;">Phiếu này sẽ biến khỏi danh sách. Xoá chứ?</div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Huỷ bỏ</button>
                    <button type="button" class="btn-modal-danger" id="deleteOk">Xoá phiếu</button>
                </div>
            </div>
        </div>
    </div>

    <script>
        function confirmDelete(ticketId) {
            document.getElementById('deleteFormId').value = ticketId;
            bootstrap.Modal.getOrCreateInstance(document.getElementById('deleteModal')).show();
        }
        document.getElementById('deleteOk').addEventListener('click', function () {
            document.getElementById('deleteForm').submit();
        });
    </script>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
