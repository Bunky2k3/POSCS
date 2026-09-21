<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Request attribute do ContractController#showEditForm thiết lập trước khi forward tới trang này:
      - contract     : poscs.model.Contract (hợp đồng đang sửa)
      - customerList : List<poscs.model.Enterprise>
      - userList      : List<poscs.model.User>

    Form này chỉ sửa bản ghi trong bảng contracts. Hạng mục sản phẩm/dịch vụ
    được thêm/gỡ ở trang chi tiết hợp đồng, không sửa tại đây.
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${contract.amendment ? 'Cập nhật phụ lục' : 'Cập nhật hợp đồng'} - POSCS Portal</title>
    <link rel="icon" type="image/png" href="${pageContext.request.contextPath}/img/favicon.png">

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container { max-width: 1180px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 24px 28px 28px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 24px 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

        /* ===== Modal xác nhận (thay cho confirm/prompt/alert của trình duyệt) =====
           Giá trị lấy đúng của viewcustomerdetail.jsp để hai màn hình nhìn như một. */
        .modal-content { border-radius: 16px; border: none; }
        .modal-header { border-bottom: none; padding: 24px 24px 0; }
        .modal-body { padding: 12px 24px 6px; color: #374151; font-size: 0.92rem; }
        .modal-footer { border-top: none; padding: 18px 24px 24px; }
        .btn-modal-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-danger { background: var(--danger); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .btn-modal-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; color: #fff; border-radius: 10px; padding: 8px 18px; font-weight: 600; font-size: 0.88rem; }
        .modal-icon-warn { width: 52px; height: 52px; border-radius: 50%; background: #fdecef; color: var(--danger); display: flex; align-items: center; justify-content: center; font-size: 1.3rem; margin-bottom: 4px; }
        .modal-icon-ask { width: 52px; height: 52px; border-radius: 50%; background: #eaf6ff; color: var(--primary); display: flex; align-items: center; justify-content: center; font-size: 1.3rem; margin-bottom: 4px; }
        .modal-title { font-weight: 700; color: var(--primary-dark); font-size: 1.05rem; }
        .modal-body .form-label { font-weight: 600; font-size: 0.85rem; color: #374151; margin-bottom: 6px; display: block; }
        .modal-body textarea.form-control {
            width: 100%; padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb;
            background-color: #f9fafb; font-size: 0.9rem;
        }
        .modal-msg { white-space: pre-line; margin-bottom: 14px; }
        .modal-err { color: var(--danger); font-size: 0.82rem; margin-top: 6px; display: none; }

        /* ===== Thanh tab cho sáu khối thao tác ===== */
        .tab-wrap { margin-top: 20px; }
        .tab-wrap .nav-tabs {
            border-bottom: 1.5px solid #eef2f6; gap: 2px; flex-wrap: wrap;
        }
        .tab-wrap .nav-tabs .nav-link {
            border: none; border-bottom: 2.5px solid transparent; border-radius: 0;
            padding: 10px 16px; font-size: 0.87rem; font-weight: 600; color: #6b7280;
            background: none;
        }
        .tab-wrap .nav-tabs .nav-link i { color: #9ca3af; }
        .tab-wrap .nav-tabs .nav-link:hover { color: var(--primary-dark); }
        .tab-wrap .nav-tabs .nav-link.active {
            color: var(--primary-dark); border-bottom-color: var(--primary);
        }
        .tab-wrap .nav-tabs .nav-link.active i { color: var(--primary); }
        /* Thẻ bên trong tab bỏ bo góc trên cho dính vào thanh tab, nhìn ra là MỘT khối
           chứ không phải hai thứ rời nhau. */
        .tab-wrap .tab-content > .tab-pane > .card-box {
            border-top-left-radius: 0; border-top-right-radius: 0;
        }

        .field-row { margin-bottom: 18px; }
        .field-row label { font-size: 0.75rem; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: .3px; margin-bottom: 6px; display: block; }
        .field-row label .req { color: var(--danger); }
        .form-control, .form-select { padding: 0.6rem 0.9rem; border-radius: 10px; border: 1px solid #e5e7eb; background-color: #f9fafb; font-size: 0.9rem; }
        .form-control:focus, .form-select:focus { background-color: #ffffff; border-color: var(--primary-light); box-shadow: 0 0 0 4px rgba(15, 158, 219, 0.15); }
        .error-text { color: var(--danger); font-size: 12px; margin-top: 5px; display: none; }


        .action-bar { display: flex; gap: 12px; margin-top: 28px; justify-content: flex-end; border-top: 1.5px solid #eef2f6; padding-top: 22px; }
        .btn-primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); border: none; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; box-shadow: 0 6px 16px rgba(5, 104, 166, 0.3); }
        .btn-primary:hover { background: linear-gradient(120deg, var(--primary-dark), var(--primary)); }
        .btn-cancel { background: #fff; border: 1.5px solid #e5e7eb; color: #6b7280; border-radius: 10px; padding: 0.6rem 1.4rem; font-weight: 600; font-size: 0.9rem; text-decoration: none; display: inline-flex; align-items: center; }
        .btn-cancel:hover { background: #f3f4f6; color: #6b7280; }

        /* Hai khối dưới đây trang này CÓ DÙNG nhưng chưa bao giờ định nghĩa:
           .lc-alert ở cảnh báo "còn phòng chưa báo xử lý xong" (chép ý tưởng từ
           viewcontractdetail.jsp) và .filter-toggle ở các ô chọn phòng bàn giao
           (từ listcontract.jsp). Thiếu chúng thì cảnh báo hiện ra như một dòng
           chữ đen trơn, còn ô chọn phòng thành checkbox trần -- giá trị giữ
           nguyên của hai trang gốc để ba chỗ nhìn như một. */
        .lc-alert {
            display: flex; align-items: flex-start; gap: 10px; margin: 16px 0 12px;
            background: #fff8ec; border: 1px solid #f5d9a8; border-radius: 10px;
            padding: 11px 14px; font-size: 0.84rem; color: #8a5a00;
        }
        .filter-toggle {
            display: inline-flex; align-items: center; gap: 7px; font-size: 0.84rem; color: #374151;
            padding: 9px 12px; border-radius: 10px; border: 1px solid #e5e7eb; background: #f9fafb; cursor: pointer;
        }
        .filter-toggle input { accent-color: var(--primary); cursor: pointer; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 0 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }
        .item-table { width: 100%; }
        .item-table th { font-size: 0.72rem; text-transform: uppercase; color: #9ca3af; font-weight: 700; padding: 8px 10px; border-bottom: 1.5px solid #eef2f6; text-align: left; }
        .item-table td { padding: 10px 10px; font-size: 0.87rem; color: #111827; border-bottom: 1px solid #f3f4f6; }
        .item-table tr:last-child td { border-bottom: none; }
        .btn-remove-item {
            width: 28px; height: 28px; border-radius: 8px; border: 1px solid #fecaca;
            background: #fff5f5; color: var(--danger); display: inline-flex; align-items: center; justify-content: center;
            font-size: 0.8rem; cursor: pointer;
        }
        .btn-remove-item:hover { background: var(--danger); color: #fff; }
        .inline-form { margin-top: 18px; padding-top: 18px; border-top: 1.5px solid #eef2f6; }
        .inline-form .form-label { font-size: 0.75rem; font-weight: 600; color: #6b7280; margin-bottom: 4px; }
        .inline-form .form-control, .inline-form .form-select { border-radius: 10px; font-size: 0.88rem; }
        .btn-add-item {
            width: 100%; height: 38px; border-radius: 10px; border: none;
            background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; cursor: pointer;
        }
        .btn-add-item:hover { opacity: 0.9; }
        .lifecycle-actions { display: flex; gap: 10px; flex-wrap: wrap; }
        .btn-step {
            border-radius: 10px; padding: 9px 18px; font-weight: 600; font-size: 0.87rem;
            display: inline-flex; align-items: center; gap: 8px; cursor: pointer; border: 1.5px solid #e5e7eb; background: #fff;
        }
        .btn-step.primary { background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; border: none; }
        .btn-step.warn { color: var(--warning); border-color: var(--warning); }
        .btn-step.danger { color: var(--danger); border-color: var(--danger); }
        .locked-note { font-size: 0.83rem; color: #6b7280; display: flex; align-items: flex-start; gap: 8px; }

        @media (max-width: 768px) { .card-box { padding: 20px 18px 22px; } }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="contract" scope="request"/>
        <c:set var="activeContractKind" value="${kind}" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">
        <a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}" class="back-link-top"><i class="fa-solid fa-arrow-left-long"></i> Quay lại chi tiết hợp đồng</a>

        <div class="page-header-row">
            <h2>${contract.amendment ? 'Cập nhật phụ lục' : 'Cập nhật hợp đồng'}</h2>
            <p>Mã ${contract.amendment ? 'phụ lục' : 'hợp đồng'}:
               <strong style="color:var(--primary-dark)">${fn:escapeXml(contract.contractCode)}</strong></p>
        </div>

        <%-- Đường ngược về hợp đồng gốc. Đứng ở đây thay vì trong khối "Phụ lục"
             bên dưới: trên một phụ lục thì khối đó không hiện (một tầng), mà câu
             "cái này sửa cho hợp đồng nào" lại là câu đầu tiên người đọc hỏi. --%>
        <c:if test="${contract.amendment}">
            <div class="card-box" style="border-left:4px solid #4338ca;">
                <div style="display:flex; align-items:flex-start; gap:10px; font-size:0.88rem; color:#3730a3;">
                    <i class="fa-solid fa-link" style="margin-top:3px;"></i>
                    <span>Đây là <strong>phụ lục</strong> của hợp đồng
                        <a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.parentContractId}">
                            ${fn:escapeXml(contract.parentContractCode)}</a>.
                        Nó có tiến trình riêng và phải được ký như một hợp đồng thường.</span>
                </div>
            </div>
        </c:if>

        <%-- Đóng băng thì ẩn hẳn form nội dung thay vì để người dùng gõ xong
             rồi nhận lỗi: ContractDAO.update từ chối mọi thay đổi sau thanh lý.
             Các khối bên dưới vẫn còn, vì ghi nhận tiền về thì luôn phải được. --%>
        <c:if test="${contract.frozen}">
            <div class="card-box" style="border-left:4px solid #2f6b34;">
                <div style="display:flex; align-items:flex-start; gap:10px; font-size:0.88rem; color:#2f6b34;">
                    <i class="fa-solid fa-lock" style="margin-top:3px;"></i>
                    <span><strong>Hợp đồng ở trạng thái “${fn:escapeXml(contract.progressStatus)}”.</strong>
                        Nội dung không sửa được nữa, kể cả bởi quản trị viên — phát sinh sau thời điểm
                        này phải lập hợp đồng mới. Bên dưới chỉ còn phần ghi nhận tiền về.</span>
                </div>
            </div>
        </c:if>
        <%-- MỌI THAO TÁC GHI CỦA HỢP ĐỒNG NẰM Ở TRANG NÀY.

             Trang xem (viewcontractdetail.jsp) cố ý không có nút nào gây thay
             đổi -- xem ghi chú ở đầu khối tiến trình bên đó. Các khối dưới đây
             đứng NGOÀI form sửa thông tin ở trên: form lồng form là HTML không
             hợp lệ, trình duyệt sẽ tự cắt và nút bấm gửi đi thiếu tham số. --%>

        <%-- Thông báo lỗi đứng NGOÀI khối "chưa đóng băng" của form sửa.
             Trước V34 nó nằm bên trong, và điều đó vừa đủ đúng vì hợp đồng đã
             đóng băng thì chẳng còn thao tác nào ghi được. Giờ thì có: tài liệu
             thêm/huỷ được ở MỌI trạng thái, nên lỗi của chúng cũng phải hiện
             được ở mọi trạng thái -- để bên trong thì bấm hỏng trên một hợp
             đồng đã thanh lý là màn hình đứng im không nói gì. --%>
        <c:if test="${not empty param.error}">
        <div class="card-box" style="padding:14px 18px;">
                <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                    <c:choose>
                        <c:when test="${param.error == 'document_invalid'}">Không thêm được tài liệu: link phải bắt đầu bằng http:// hoặc https://, và loại tài liệu phải chọn trong danh sách.</c:when>
                        <c:when test="${param.error == 'document_reason_required'}">Phải nêu lý do thì mới huỷ được tài liệu.</c:when>
                        <c:when test="${param.error == 'document_failed'}">Không thực hiện được trên tài liệu này. Vui lòng thử lại.</c:when>
                        <c:when test="${param.error == 'invalid'}">Thông tin hợp đồng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.</c:when>
                        <c:when test="${param.error == 'duplicate_code'}">Mã hợp đồng này đã có hợp đồng khác dùng. Kiểm lại số trên bản giấy hoặc nhập mã khác.</c:when>
                        <c:when test="${param.error == 'update_failed'}">Không lưu được thay đổi. Vui lòng thử lại.</c:when>
                        <c:when test="${param.error == 'missing_reason'}">Sửa sai sót trên hợp đồng đã ký thì bắt buộc phải nêu lý do.</c:when>
                        <c:when test="${param.error == 'amendment_not_allowed'}">Hợp đồng này không lập phụ lục được: nó phải đã ký và chưa thanh lý, và bản thân nó không được là phụ lục.</c:when>
                        <c:when test="${param.error == 'value_negative'}">Khoản giảm trừ lớn hơn giá trị còn lại của hợp đồng gốc — giá trị hợp đồng sẽ âm. Ô này nhập phần CHÊNH LỆCH, không phải tổng giá trị mới.</c:when>
                        <c:when test="${param.error == 'handover_no_department'}">Chọn ít nhất một phòng để bàn giao.</c:when>
                        <c:when test="${param.error == 'handover_pending'}">Phòng đó đang còn giữ hợp đồng này — đợi họ báo xử lý xong rồi mới bàn giao lượt mới.</c:when>
                        <c:when test="${param.error == 'handover_frozen'}">Hợp đồng đã thanh lý nên không mở được lượt bàn giao mới. Phát sinh sau thanh lý phải lập hợp đồng mới.</c:when>
                        <c:when test="${param.error == 'handover_failed'}">Không bàn giao được. Vui lòng thử lại.</c:when>
                        <c:when test="${param.error == 'link_invalid'}">Không nối được hai hợp đồng này: phải là một hợp đồng bán với một hợp đồng mua, và cả hai đều phải là hợp đồng gốc (không phải phụ lục).</c:when>
                        <c:when test="${param.error == 'link_duplicate'}">Hai hợp đồng này đã nối với nhau rồi.</c:when>
                        <c:when test="${param.error == 'unlink_failed'}">Không gỡ được liên kết. Vui lòng thử lại.</c:when>
                        <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                    </c:choose>
                </div>
        </div>
        </c:if>

        <c:if test="${not contract.frozen}">
        <div class="card-box">

            <form id="createContractForm" action="${pageContext.request.contextPath}/contract" method="POST" onsubmit="return validateForm();">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <%-- Một form, hai đích. Bình thường gửi "update" (sau khi ký thì
                     server chỉ nhận người phụ trách + link). Admin bật chế độ
                     chữa sai sót thì script đổi ô này thành "correct" -- đường
                     duy nhất chạm được vào điều khoản của hợp đồng đã ký, và nó
                     bắt buộc kèm lý do. --%>
                <input type="hidden" name="action" id="formAction" value="update">
                <input type="hidden" name="contractId" value="${contract.contractId}">

                <%-- Vì sao phần lớn ô ở dưới bị mờ. Nói ngay tại chỗ, không để
                     người dùng bấm vào một ô không gõ được rồi tự đoán. --%>
                <c:if test="${not canEditTerms}">
                    <div style="border:1px solid #fed7aa; background:#fffbeb; border-radius:12px; padding:12px 14px; margin-bottom:16px;">
                        <div style="display:flex; align-items:flex-start; gap:10px; font-size:0.88rem; color:#92400e;">
                            <i class="fa-solid fa-file-signature" style="margin-top:3px;"></i>
                            <div>
                                <strong>Hợp đồng đã ký — điều khoản đã khoá.</strong>
                                Chỉ còn <strong>người phụ trách</strong> và <strong>link bản PDF</strong> sửa được;
                                hai thứ đó là dữ liệu quản trị nội bộ, không nằm trên tờ giấy hai bên ký.
                                <div style="margin-top:6px;">
                                    Cần thay đổi điều khoản thì <strong>lập phụ lục</strong> — một văn bản riêng, có chữ ký.
                                    <c:if test="${canCorrect}">
                                        Còn nếu đây chỉ là <strong>gõ sai so với bản giấy</strong>, dùng nút
                                        “Chữa sai sót nhập liệu” bên dưới.
                                    </c:if>
                                </div>
                            </div>
                        </div>
                    </div>
                </c:if>

                <div class="section-header"><h5>Thông tin chung</h5></div>
                <div class="row">
                    <%-- Mã hợp đồng sửa được: nó là số trên bản giấy do người dùng
                         nhập, và nhập sai thì phải có đường chữa. Các bảng khác trỏ
                         sang hợp đồng bằng contract_id chứ không bằng mã, nên đổi
                         chuỗi này không làm gãy liên kết nào. --%>
                    <div class="col-12 field-row">
                        <label>Mã hợp đồng <span class="req">*</span></label>
                        <input type="text" class="form-control" id="contractCode" name="contractCode"
                               maxlength="50" placeholder="VD: 01/2026/HĐKT-POSTEF"
                               value="${fn:escapeXml(contract.contractCode)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                        <span class="error-text" id="err-contractCode">Mã hợp đồng không được để trống.</span>
                    </div>

                    <div class="col-12 field-row">
                        <label>Tiêu đề hợp đồng <span class="req">*</span></label>
                        <input type="text" class="form-control" id="title" name="title" value="${fn:escapeXml(contract.title)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                        <span class="error-text" id="err-title">Tiêu đề không được để trống.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customer" name="enterpriseId" ${canEditTerms ? '' : 'disabled'} data-term="1">
                            <option value="">-- Chọn khách hàng --</option>
                            <c:forEach var="customer" items="${customerList}">
                                <option value="${customer.enterpriseId}" ${customer.enterpriseId == contract.enterpriseId ? 'selected' : ''}>${fn:escapeXml(customer.enterpriseName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-customer">Vui lòng chọn khách hàng.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Người phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="owner" name="ownerId">
                            <option value="">-- Chọn nhân viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}" ${staff.userId == contract.ownerId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-owner">Vui lòng chọn người phụ trách.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Loại hợp đồng <span class="req">*</span></label>
                        <select class="form-select" id="contractType" name="contractType" ${canEditTerms ? '' : 'disabled'} data-term="1">
                            <option value="">-- Chọn loại hợp đồng --</option>
                            <%-- Loại hợp đồng theo CHIỀU: hợp đồng mua không dùng chung bộ
                                 chữ với hợp đồng bán. Xem ContractController. --%>
                            <c:forEach var="ct" items="${contractTypeOptions}">
                                <option value="${fn:escapeXml(ct)}" ${contract.contractType == ct ? 'selected' : ''}>${fn:escapeXml(ct)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-contractType">Vui lòng chọn loại hợp đồng.</span>
                    </div>

<div class="col-12" style="margin-top:6px; margin-bottom:10px;">
                        <div style="font-size:0.8rem; font-weight:700; color:var(--primary-dark); text-transform:uppercase; letter-spacing:.3px;">
                            Thông tin ký kết
                        </div>
                        <div style="font-size:0.78rem; color:#9ca3af; margin-top:2px;">
                            Phải điền trước khi ký thì hồ sơ mới đủ. Khác đại diện pháp luật
                            của công ty khách: có uỷ quyền thì hai người này khác nhau.
                        </div>
                    </div>
                    <div class="col-md-3 field-row">
                        <label>Người ký bên mình</label>
                        <input type="text" class="form-control" id="signerName" name="signerName" maxlength="100" value="${fn:escapeXml(contract.signerName)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <div class="col-md-3 field-row">
                        <label>Chức vụ</label>
                        <input type="text" class="form-control" id="signerPosition" name="signerPosition"
                               maxlength="100" placeholder="VD: Giám đốc chi nhánh" value="${fn:escapeXml(contract.signerPosition)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <div class="col-md-3 field-row">
                        <label>Người ký bên đối tác</label>
                        <input type="text" class="form-control" id="counterpartySignerName" name="counterpartySignerName" maxlength="100" value="${fn:escapeXml(contract.counterpartySignerName)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <div class="col-md-3 field-row">
                        <label>Chức vụ</label>
                        <input type="text" class="form-control" id="counterpartySignerPosition" name="counterpartySignerPosition" maxlength="100" value="${fn:escapeXml(contract.counterpartySignerPosition)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Căn cứ uỷ quyền</label>
                        <input type="text" class="form-control" id="authorizationRef" name="authorizationRef"
                               maxlength="255" placeholder="VD: GUQ số 05/2026 ngày 10/01/2026" value="${fn:escapeXml(contract.authorizationRef)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            Chỉ điền khi người ký không phải đại diện pháp luật.
                        </span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Nơi ký</label>
                        <input type="text" class="form-control" id="signingPlace" name="signingPlace"
                               maxlength="255" placeholder="VD: Hà Nội" value="${fn:escapeXml(contract.signingPlace)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <%-- Trên một PHỤ LỤC, ô này mang phần CHÊNH LỆCH chứ không phải
                         tổng giá trị -- xem ghi chú ở addnewcontract.jsp. Ô chọn dấu
                         chỉ hiện khi còn sửa được: ký xong thì giá trị là điều khoản
                         đã khoá, đổi phải qua một phụ lục khác. --%>
                    <c:if test="${contract.amendment and canEditTerms}">
                        <div class="col-md-6 field-row">
                            <label>Điều chỉnh giá trị</label>
                            <select class="form-control" id="valueAdjustment" name="valueAdjustment" data-term="1">
                                <option value="increase" ${contract.contractValue != null and contract.contractValue.signum() > 0 ? 'selected' : ''}>Bổ sung (cộng vào giá trị hợp đồng)</option>
                                <option value="decrease" ${contract.contractValue != null and contract.contractValue.signum() < 0 ? 'selected' : ''}>Giảm trừ (trừ khỏi giá trị hợp đồng)</option>
                                <option value="none" ${contract.contractValue == null ? 'selected' : ''}>Không đổi giá trị</option>
                            </select>
                        </div>
                    </c:if>
                    <div class="col-md-6 field-row">
                        <label>${contract.amendment ? 'Số tiền điều chỉnh (VNĐ)' : 'Giá trị hợp đồng (VNĐ)'}</label>
                        <%-- Ô nhập luôn hiện số DƯƠNG: dấu nằm ở ô chọn bên cạnh. Gửi
                             lên dấu trừ lẫn "Giảm trừ" thì controller sẽ đảo dấu một
                             khoản vốn đã âm. --%>
                        <input type="text" class="form-control" id="contractValue" name="contractValue"
                               inputmode="numeric" data-money placeholder="VD: 1.500.000.000"
                               value="${contract.amendment and contract.contractValue != null ? contract.contractValue.abs() : contract.contractValue}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                        <span class="money-words" data-money-words-for="contractValue"></span>
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            <c:choose>
                                <c:when test="${contract.amendment}">
                                    Phần chênh lệch cộng vào hợp đồng gốc
                                    <strong>${fn:escapeXml(contract.parentContractCode)}</strong>, tính từ lúc phụ lục này được ký.
                                </c:when>
                                <c:when test="${contract.valueAdjusted}">
                                    Giá trị theo bản gốc đã ký. Sau ${contract.amendmentCount} phụ lục, giá trị hiện hành là
                                    <strong class="money-vnd" data-vnd="${contract.currentValue}">&mdash;</strong>
                                    (điều chỉnh <span class="money-signed" data-vnd="${contract.amendmentValueSigned}">&mdash;</span>).
                                </c:when>
                                <c:otherwise>
                                    Giá trị theo điều khoản. Số tiền thực thu ghi ở mục Kỳ thanh toán — hai
                                    con số lệch nhau chính là công nợ.
                                </c:otherwise>
                            </c:choose>
                        </span>
                    </div>
                                        <%-- THỜI HẠN tách thành khối riêng, không nằm chung "Thông tin
                         chung" nữa -- khối đó giờ chỉ mang thông tin nhận dạng hợp
                         đồng, khớp với trang xem.

                         Vẫn PHẢI giữ ô nhập ở đây: hợp đồng thiếu ngày hiệu lực hoặc
                         ngày kết thúc thì KHÔNG ký được (ContractDAO.changeProgressStatus),
                         nên bỏ hẳn là không hợp đồng nào ký được nữa. --%>
                    <div class="col-12" style="margin-top:6px; margin-bottom:10px;">
                        <div style="font-size:0.8rem; font-weight:700; color:var(--primary-dark); text-transform:uppercase; letter-spacing:.3px;">
                            Thời hạn hợp đồng
                        </div>
                        <div style="font-size:0.78rem; color:#9ca3af; margin-top:2px;">
                            Điền khi đã chốt với khách. Phải có đủ ngày hiệu lực và ngày kết thúc thì mới ký được.
                        </div>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày ký</label>
                        <%-- CHỈ ĐỌC, và không gửi lên (không có name). Ngày ký là
                             dấu của một hành động đã xảy ra, do hệ thống đóng lúc
                             bấm Ký -- sửa lại được thì nó thành một ô khai báo, và
                             hợp đồng đã ký có thể bị lùi ngày. ContractController
                             cũng giữ nguyên giá trị cũ khi lưu, không đọc từ form. --%>
                        <input type="date" class="form-control" id="signDate" disabled
                               value="<fmt:formatDate value="${contract.signingDate}" pattern="yyyy-MM-dd"/>">
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            <c:choose>
                                <c:when test="${contract.signingDate == null}">Chưa ký — ngày ký ghi vào lúc bấm "Ký hợp đồng".</c:when>
                                <c:otherwise>Ngày ký đã chốt, không sửa được.</c:otherwise>
                            </c:choose>
                        </span>
                        <span class="error-text" id="err-dates">Điền cả hai mốc, và ngày hiệu lực phải trước hoặc bằng ngày kết thúc.</span>
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày hiệu lực</label>
                        <input type="date" class="form-control" id="effectiveDate" name="effectiveDate" value="<fmt:formatDate value="${contract.effectiveDate}" pattern="yyyy-MM-dd"/>" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <div class="col-md-4 field-row">
                        <label>Ngày kết thúc</label>
                        <input type="date" class="form-control" id="endDate" name="endDate" value="<fmt:formatDate value="${contract.endDate}" pattern="yyyy-MM-dd"/>" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>

                    <%-- Ô "link file PDF" cũ đã bỏ (V34): giấy tờ giờ là danh sách
                         nhiều dòng ở tab "Tài liệu", không còn là một trường của
                         hợp đồng. Hệ quả kèm theo: sau khi KÝ, trường duy nhất còn
                         sửa được ở form này là NGƯỜI PHỤ TRÁCH -- lý do cũ để mở ô
                         link ("bản PDF đã ký thường chỉ có sau khi ký") giờ được
                         phục vụ ở tab kia, nơi thêm được ở mọi trạng thái. --%>
                </div>

                <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
                <p style="font-size:0.86rem; color:#6b7280; margin:0 0 4px;">
                    Hạng mục không sửa trong form này — nó có khối riêng
                    <a href="#hang-hoa">ngay bên dưới</a>, vì thêm/gỡ là thao tác ghi ngay chứ không chờ bấm Lưu.
                </p>


                <%-- Chế độ CHỮA SAI SÓT: chỉ Admin, chỉ trên hợp đồng đã ký và
                     chưa thanh lý. Mở ra thì các ô điều khoản gõ lại được, ô
                     action đổi sang "correct", và lý do thành bắt buộc.

                     Đây KHÔNG phải cửa sau để sửa nội dung hợp đồng: sửa đổi
                     thật thì đi qua phụ lục và để lại một văn bản có chữ ký.
                     Cái này chỉ chữa thứ gõ sai so với chính bản giấy đang cầm.
                     Server kiểm lại quyền Admin và bắt buộc lý do
                     (ContractController.handleCorrect / ContractDAO.correct). --%>
                <c:if test="${canCorrect}">
                    <div id="correctionBox" style="display:none; border:1px solid #fecaca; background:#fef2f2; border-radius:12px; padding:14px; margin-top:14px;">
                        <div style="font-size:0.88rem; color:#991b1b; margin-bottom:10px;">
                            <i class="fa-solid fa-triangle-exclamation me-1"></i>
                            <strong>Đang chữa sai sót trên hợp đồng đã ký.</strong>
                            Chỉ dùng khi dữ liệu trên hệ thống khác với bản giấy. Thay đổi sẽ ghi vào
                            nhật ký kèm lý do bạn nêu, dưới tên bạn.
                        </div>
                        <label>Lý do <span class="req">*</span></label>
                        <textarea class="form-control" id="correctionReason" name="correctionReason" rows="2"
                                  placeholder="VD: Mã hợp đồng gõ nhầm 01 thành 10, đối chiếu bản giấy ngày 12/03/2026"></textarea>
                        <span class="error-text" id="err-correctionReason">Vui lòng nêu lý do sửa.</span>
                    </div>
                </c:if>

                <div class="action-bar">
                    <a href="${pageContext.request.contextPath}/contract?action=view&id=${contract.contractId}" class="btn-cancel">Hủy</a>
                    <c:if test="${canCorrect}">
                        <button type="button" class="btn-cancel" id="toggleCorrection">
                            <i class="fa-solid fa-pen-ruler me-1"></i> Chữa sai sót nhập liệu
                        </button>
                    </c:if>
                    <button type="submit" class="btn-primary"><i class="fa-solid fa-check me-1"></i> Lưu thay đổi</button>
                </div>
            </form>
        </div>
        </c:if>

        <%-- TRANG NÀY ĐỂ MỘT CỘT, CỐ Ý -- đừng chia hai cột như trang xem.

             Đã thử chia rồi và phải bỏ: trang xem toàn nhãn/giá trị nên cột hẹp đọc
             được, còn trang quản lý thì MỌI khối đều là bảng nhiều cột kèm form.
             Nhét vào cột ~430px thì bảng Phụ lục vỡ chữ từng ô (mã hợp đồng xuống 2
             dòng, tiêu đề cột "Điều chỉnh giá trị" xuống 3 dòng), form lập kỳ bị ép,
             còn các đoạn mô tả bên cột phải thành sợi dọc. Trang ngắn hơn nhưng
             không đọc được thì không đáng.

             Khối nào dài quá thì thu gọn bản thân nó, đừng bóp bề ngang cả trang. --%>

        <%-- Sáu khối dưới đây gom vào TAB thay vì xếp dọc.

             Lý do đo được: xếp dọc thì trang cao 3770px, mà chia hai cột để rút
             ngắn thì bảng vỡ chữ (đã thử, xem ghi chú trên). Tab giữ được CẢ HAI:
             mỗi lúc chỉ hiện một khối, và khối đó rộng hết khung.

             ĐIỀU KIỆN c:if của NÚT và của KHỐI phải GIỐNG HỆT nhau -- lệch một cái là
             ra nút bấm vào không có gì, hoặc khối không có đường nào tới. Tab đầu
             Thứ tự xếp theo VIỆC PHẢI LÀM, cùng mạch với trang xem: Bước tiến
             trình (Ký / Thanh lý / Chấm dứt) đứng đầu vì đó là lý do chính người
             ta mở trang này, rồi tới hai thứ có hạn là Kỳ thanh toán và Bàn giao.
             Hàng hoá tụt xuống thứ tư: sau khi ký nó gần như không đổi nữa.

             Tab đầu dãy CŨNG là tab mở sẵn -- thứ tự và mặc định phải nói cùng
             một chuyện, chứ nhìn tab đầu mà nội dung lại là tab khác thì khó
             hiểu. Bước tiến trình có thể không có (hợp đồng đã thanh lý), lúc
             đó Kỳ thanh toán đỡ chỗ: nó luôn tồn tại, không thì người dùng nhìn
             vào một dãy nút mà bên dưới trống trơn.

             POST xong (lập kỳ, bàn giao, nối hợp đồng...) controller chuyển hướng về
             đúng trang này, không mang tab theo được -- script cuối file nhớ tab cuối
             cùng bằng sessionStorage để quay lại đúng chỗ vừa làm. --%>
        <div class="tab-wrap">
            <%-- coTienTrinh: tab "Bước tiến trình" chỉ có khi còn bước nào bấm
                 được. Phải gom vào MỘT biến vì nó quyết định hai chỗ cách nhau
                 cả nghìn dòng -- nút nào mang class active, và khung nào mang
                 "show active". Tính lại ở chỗ thứ hai là có ngày hai chỗ lệch
                 nhau, lúc đó cả dãy nút hiện mà bên dưới trống trơn. --%>
            <c:set var="coTienTrinh" value="${canSign or canClose or canVoid}"/>
            <ul class="nav nav-tabs" id="contractTabs" role="tablist">
                <c:if test="${coTienTrinh}">
                <li class="nav-item" role="presentation">
                    <button class="nav-link active" data-bs-toggle="tab" data-bs-target="#pane-tien-trinh"
                            type="button" role="tab"><i class="fa-solid fa-diagram-project me-2"></i>Bước tiến trình</button>
                </li>
                </c:if>
                <li class="nav-item" role="presentation">
                    <button class="nav-link ${coTienTrinh ? '' : 'active'}" data-bs-toggle="tab" data-bs-target="#pane-ky-thu"
                            type="button" role="tab"><i class="fa-solid fa-money-bill-wave me-2"></i>Kỳ thanh toán</button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#pane-ban-giao"
                            type="button" role="tab"><i class="fa-solid fa-share-from-square me-2"></i>Bàn giao</button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#pane-hang-hoa"
                            type="button" role="tab"><i class="fa-solid fa-boxes-stacked me-2"></i>Hàng hoá</button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#pane-tai-lieu"
                            type="button" role="tab"><i class="fa-solid fa-folder-open me-2"></i>Tài liệu<c:if
                            test="${not empty contractDocuments}"> (${fn:length(contractDocuments)})</c:if></button>
                </li>
                <c:if test="${canAddAmendment or not empty amendments}">
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#pane-phu-luc"
                            type="button" role="tab"><i class="fa-solid fa-file-circle-plus me-2"></i>Phụ lục</button>
                </li>
                </c:if>
                <c:if test="${canLinkContracts}">
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-bs-toggle="tab" data-bs-target="#pane-noi-hd"
                            type="button" role="tab"><i class="fa-solid fa-link me-2"></i>Nối bán – mua</button>
                </li>
                </c:if>
            </ul>
            <div class="tab-content">
            <div class="tab-pane fade" id="pane-hang-hoa" role="tabpanel">
        <!-- ===== Hạng mục hàng hoá ===== -->
        <div class="card-box" id="hang-hoa">
            <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
            <c:choose>
                <c:when test="${empty contractProducts}">
                    <div style="color:#9ca3af; font-size:0.87rem;">Chưa gắn hàng hoá nào.</div>
                </c:when>
                <c:otherwise>
                    <%-- Bảng 4-5 cột: dưới ~500px nó rộng hơn màn hình và đẩy CẢ TRANG trượt ngang
                     (đo được trên màn 375px: trang rộng 462px). Cho riêng bảng cuộn. --%>
                    <div class="table-responsive">
                        <table class="item-table">
                            <thead><tr><th style="width:40px;">#</th><th>Sản phẩm</th><th style="width:110px;">Số lượng</th><th style="width:90px;">Đơn vị</th><th>Ghi chú</th><th style="width:60px;"></th></tr></thead>
                            <tbody>
                                <c:forEach var="cp" items="${contractProducts}" varStatus="row">
                                    <tr>
                                        <td>${row.index + 1}</td>
                                        <td>${fn:escapeXml(cp.productName)}<div style="color:#9ca3af; font-size:0.78rem;">${fn:escapeXml(cp.productCode)}</div></td>
                                        <td>${cp.quantity}</td>
                                        <td>${fn:escapeXml(cp.unit)}</td>
                                        <td>${not empty cp.notes ? fn:escapeXml(cp.notes) : '—'}</td>
                                        <td>
                                            <c:if test="${canEditProducts}">
                                                <button type="button" class="btn-remove-item" title="Gỡ hàng hoá này"
                                                        onclick="confirmRemoveProduct(${cp.contractProductId})">
                                                    <i class="fa-solid fa-xmark"></i>
                                                </button>
                                            </c:if>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>
            <c:choose>
                <c:when test="${canEditProducts}">
                    <form class="inline-form" method="POST" action="${pageContext.request.contextPath}/contract">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="addProduct">
                        <input type="hidden" name="contractId" value="${contract.contractId}">
                        <div class="row g-2 align-items-end">
                            <div class="col-md-4">
                                <label class="form-label">Sản phẩm</label>
                                <select class="form-select" name="productId" required>
                                    <option value="">-- Chọn sản phẩm --</option>
                                    <c:forEach var="pr" items="${productOptions}">
                                        <option value="${pr.productId}">${fn:escapeXml(pr.productName)}</option>
                                    </c:forEach>
                                </select>
                            </div>
                            <div class="col-md-2">
                                <label class="form-label">Số lượng</label>
                                <input type="text" class="form-control" name="quantity" placeholder="VD: 1.000" required>
                            </div>
                            <div class="col-md-2">
                                <label class="form-label">Đơn vị</label>
                                <input type="text" class="form-control" name="unit" placeholder="Cái">
                            </div>
                            <div class="col-md-2">
                                <label class="form-label">Ghi chú</label>
                                <input type="text" class="form-control" name="notes">
                            </div>
                            <div class="col-md-2">
                                <button type="submit" class="btn-add-item"><i class="fa-solid fa-plus"></i> Thêm</button>
                            </div>
                        </div>
                    </form>
                </c:when>
                <c:otherwise>
                    <div class="inline-form locked-note">
                        <i class="fa-solid fa-lock" style="margin-top:3px; color:#9ca3af;"></i>
                        <span>Hợp đồng đã ký nên hạng mục hàng hoá đã chốt — đây là nội dung hợp đồng,
                            không sửa thẳng được. Thay đổi phát sinh phải lập phụ lục.</span>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>

            </div>
            <div class="tab-pane fade ${coTienTrinh ? '' : 'show active'}" id="pane-ky-thu" role="tabpanel">
        <!-- ===== Kỳ thanh toán ===== -->
        <div class="card-box">
            <div class="section-header"><h5>Kỳ thanh toán</h5></div>
            <c:choose>
                <c:when test="${empty contractPayments}">
                    <div style="color:#9ca3af; font-size:0.87rem;">Chưa lập kỳ thanh toán nào.</div>
                </c:when>
                <c:otherwise>
                    <%-- Bảng 4-5 cột: dưới ~500px nó rộng hơn màn hình và đẩy CẢ TRANG trượt ngang
                     (đo được trên màn 375px: trang rộng 462px). Cho riêng bảng cuộn. --%>
                    <div class="table-responsive">
                        <table class="item-table">
                            <thead><tr><th style="width:40px;">#</th><th>Số tiền</th><th style="width:130px;">Đến hạn</th><th style="width:170px;">Tình trạng</th><th style="width:170px;"></th></tr></thead>
                            <tbody>
                                <c:forEach var="pm" items="${contractPayments}" varStatus="row">
                                    <tr>
                                        <td>${row.index + 1}</td>
                                        <td><strong class="money-vnd" data-vnd="${pm.invoiceAmount}">&mdash;</strong></td>
                                        <td><fmt:formatDate value="${pm.dueDate}" pattern="dd/MM/yyyy"/></td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${pm.paidDate != null}">
                                                    <span style="color:#2f6b34; font-weight:600;"><i class="fa-solid fa-circle-check"></i>
                                                        Đã thu <fmt:formatDate value="${pm.paidDate}" pattern="dd/MM/yyyy"/></span>
                                                </c:when>
                                                <c:otherwise><span style="color:#9ca3af;">Chưa thu</span></c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td style="text-align:right;">
                                            <c:if test="${pm.paidDate == null and canRecordPayment}">
                                                <button type="button" class="btn-remove-item"
                                                        style="width:auto; padding:0 10px; border-color:#cfe3d0; background:#f3f7f3; color:#2f6b34;"
                                                        onclick="markPaid(${pm.paymentId})">
                                                    <i class="fa-solid fa-check"></i> Đã thu
                                                </button>
                                            </c:if>
                                            <c:if test="${canEditPayments}">
                                                <button type="button" class="btn-remove-item" title="Xoá kỳ này"
                                                        onclick="confirmRemovePayment(${pm.paymentId})">
                                                    <i class="fa-solid fa-xmark"></i>
                                                </button>
                                            </c:if>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>
            <c:choose>
                <c:when test="${canEditPayments}">
                    <form class="inline-form" method="POST" action="${pageContext.request.contextPath}/contract">
                        <input type="hidden" name="csrfToken" value="${csrfToken}">
                        <input type="hidden" name="action" value="addPayment">
                        <input type="hidden" name="contractId" value="${contract.contractId}">
                        <div class="row g-2 align-items-end">
                            <div class="col-md-4">
                                <label class="form-label">Số tiền (VNĐ)</label>
                                <input type="text" class="form-control" id="invoiceAmount" name="invoiceAmount" inputmode="numeric"
                                       data-money placeholder="VD: 450.000.000" required>
                                <span class="money-words" data-money-words-for="invoiceAmount"></span>
                            </div>
                            <div class="col-md-3">
                                <label class="form-label">Đến hạn</label>
                                <input type="date" class="form-control" name="dueDate" required>
                            </div>
                            <div class="col-md-3">
                                <label class="form-label">Ngày đã thu (nếu có)</label>
                                <input type="date" class="form-control" name="paidDate">
                            </div>
                            <div class="col-md-2">
                                <button type="submit" class="btn-add-item"><i class="fa-solid fa-plus"></i> Lập kỳ</button>
                            </div>
                        </div>
                    </form>
                </c:when>
                <c:otherwise>
                    <div class="inline-form locked-note">
                        <i class="fa-solid fa-lock" style="margin-top:3px; color:#9ca3af;"></i>
                        <span>Hợp đồng đã đóng băng nên không lập thêm kỳ được. Tiền của kỳ đã lập thì
                            vẫn ghi nhận được khi về — tiền bảo hành giữ lại thường về sau thanh lý.</span>
                    </div>
                </c:otherwise>
            </c:choose>
        </div>

            </div>
            <c:if test="${canAddAmendment or not empty amendments}">
            <div class="tab-pane fade" id="pane-phu-luc" role="tabpanel">
        <!-- ===== Phụ lục ===== -->
        <%-- Hiện cả khi danh sách rỗng, MIỄN LÀ hợp đồng này nhận được phụ lục:
             ở đó cái người dùng cần là cái nút, và một khối trống có nút nói rõ
             hơn hẳn việc không có gì.

             Trên chính một phụ lục thì khối này biến mất (canAddAmendment false
             vì một tầng) và thay bằng đường ngược về hợp đồng gốc. --%>
        <div class="card-box">
            <div class="section-header"><h5>Phụ lục</h5></div>
            <p style="font-size:0.86rem; color:#6b7280; margin:0 0 12px;">
                Hợp đồng đã ký không sửa thẳng được. Mọi thay đổi điều khoản đi qua một phụ lục —
                hợp đồng con có mã riêng, thời hạn riêng, và cũng phải được ký.
            </p>

            <c:choose>
                <c:when test="${empty amendments}">
                    <p style="font-size:0.88rem; color:#9ca3af; margin:0 0 14px;">Chưa có phụ lục nào.</p>
                </c:when>
                <c:otherwise>
                    <div class="table-responsive">
                        <table class="table align-middle" style="font-size:0.9rem;">
                            <thead>
                                <tr>
                                    <th>Mã phụ lục</th>
                                    <th>Tiêu đề</th>
                                    <th>Thời hạn</th>
                                    <th class="text-end">Điều chỉnh giá trị</th>
                                    <th>Tiến độ</th>
                                    <th></th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="pl" items="${amendments}">
                                    <tr>
                                        <td><strong>${fn:escapeXml(pl.contractCode)}</strong></td>
                                        <td>${fn:escapeXml(pl.title)}</td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${pl.effectiveDate == null or pl.endDate == null}">
                                                    <span style="color:#9ca3af;">Chưa chốt</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <fmt:formatDate value="${pl.effectiveDate}" pattern="dd/MM/yyyy"/>
                                                    — <fmt:formatDate value="${pl.endDate}" pattern="dd/MM/yyyy"/>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td class="text-end">
                                            <span class="money-signed" data-vnd="${pl.contractValue}">&mdash;</span>
                                            <c:if test="${pl.draft and pl.contractValue != null}">
                                                <div style="font-size:0.74rem; color:#9ca3af;">chưa ký, chưa tính</div>
                                            </c:if>
                                        </td>
                                        <td>${fn:escapeXml(pl.progressStatus)}</td>
                                        <td class="text-end">
                                            <a href="${pageContext.request.contextPath}/contract?action=view&id=${pl.contractId}"
                                               class="btn-outline-action"><i class="fa-solid fa-eye"></i> Xem</a>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                    <%-- Phụ lục KHÔNG kéo theo thời hạn của hợp đồng gốc: bản ghi cha
                         là thứ ghi trên tờ giấy đã ký, và trạng thái theo lịch của nó
                         vẫn tính từ ngày kết thúc của chính nó. Nói rõ ở đây, vì đó là
                         chỗ người dùng sẽ thấy "kỳ lạ" trên trang danh sách. --%>
                    <p style="font-size:0.8rem; color:#9ca3af; margin:4px 0 14px;">
                        Phụ lục gia hạn không đổi ngày kết thúc của hợp đồng gốc — bản ghi gốc giữ nguyên
                        những gì đã ký, thời hạn mới nằm trên chính phụ lục.
                        <%-- GIÁ TRỊ thì ngược lại: nó CÓ cộng dồn. Hai luật khác nhau
                             trên cùng một bảng, nên phải nói ra cả hai ở đúng chỗ
                             người dùng đang nhìn, nếu không thì cái này bị suy ra từ
                             cái kia. --%>
                        <c:if test="${contract.valueAdjusted}">
                            Giá trị thì có: sau ${contract.amendmentCount} phụ lục đã ký, hợp đồng này hiện là
                            <strong class="money-vnd" data-vnd="${contract.currentValue}">&mdash;</strong>.
                        </c:if>
                    </p>
                </c:otherwise>
            </c:choose>

            <c:if test="${canAddAmendment}">
                <a href="${pageContext.request.contextPath}/contract?action=newAmendment&parentId=${contract.contractId}"
                   class="btn-primary" style="display:inline-block; text-decoration:none;">
                    <i class="fa-solid fa-file-circle-plus me-1"></i> Lập phụ lục
                </a>
            </c:if>
        </div>

            </div>
            </c:if>
            <c:if test="${canSign or canClose or canVoid}">
            <div class="tab-pane fade ${coTienTrinh ? 'show active' : ''}" id="pane-tien-trinh" role="tabpanel">
        <!-- ===== Bước tiến trình ===== -->
        <div class="card-box">
            <div class="section-header"><h5>Bước tiến trình</h5></div>
            <div class="lifecycle-actions">
                <c:if test="${canSign}">
                    <button type="button" class="btn-step primary"
                            onclick="changeProgress('Đã ký', 'Ký hợp đồng này?\n\nSau khi ký, nội dung trở thành chứng cứ và không quay lại bản nháp được.', false)">
                        <i class="fa-solid fa-signature"></i> Ký hợp đồng
                    </button>
                </c:if>
                <c:if test="${canClose}">
                    <button type="button" class="btn-step"
                            onclick="changeProgress('Đã thanh lý', 'Thanh lý hợp đồng — đây là lúc hợp đồng coi như xong.\n\nSau thanh lý KHÔNG sửa được nữa, kể cả cấp cao.\n\nNhập căn cứ (số biên bản thanh lý, ngày ký biên bản...):', true)">
                        <i class="fa-solid fa-file-circle-check"></i> Thanh lý
                    </button>
                    <button type="button" class="btn-step warn"
                            onclick="changeProgress('Chấm dứt sớm', 'Chấm dứt hợp đồng trước hạn.\n\nCũng đóng băng vĩnh viễn như thanh lý, chỉ khác lý do.\n\nNhập lý do:', true)">
                        <i class="fa-solid fa-ban"></i> Chấm dứt sớm
                    </button>
                </c:if>
                <c:if test="${canVoid}">
                    <button type="button" class="btn-step danger"
                            onclick="confirmVoid(${contract.contractId})"
                            title="Gỡ một bản ghi NHẬP NHẦM khỏi danh sách. Không phải huỷ hợp đồng ngoài đời.">
                        <i class="fa-solid fa-trash"></i> Huỷ bản ghi
                    </button>
                </c:if>
            </div>
            <c:if test="${canSign and (contract.effectiveDate == null or contract.endDate == null)}">
                <div class="locked-note" style="margin-top:12px;">
                    <i class="fa-solid fa-circle-info" style="margin-top:3px; color:var(--primary);"></i>
                    <span>Còn thiếu ngày hiệu lực hoặc ngày kết thúc — điền ở form trên và lưu lại thì mới ký được.</span>
                </div>
            </c:if>
            <%-- Vì sao nút "Huỷ bản ghi" biến mất. Không nói thì trông như mất quyền. --%>
            <c:if test="${contract.amendmentCount > 0}">
                <div class="locked-note" style="margin-top:12px;">
                    <i class="fa-solid fa-circle-info" style="margin-top:3px; color:var(--primary);"></i>
                    <span>Hợp đồng này đang có ${contract.amendmentCount} phụ lục nên không huỷ bản ghi được —
                        phụ lục sẽ thành văn bản không tra ngược được nó sửa cho cái gì. Huỷ từng phụ lục trước
                        nếu thật sự cần.</span>
                </div>
            </c:if>
        </div>

            </div>
            </c:if>
            <div class="tab-pane fade" id="pane-ban-giao" role="tabpanel">
        <!-- ===== Bàn giao phòng ban ===== -->
        <%-- Kinh doanh soạn xong thì chuyển xuống Kế toán và Dự án CÙNG LÚC,
             mỗi phòng tự báo xong. Đây là trục thứ tư, đứng RIÊNG với trục tiến
             độ (Nháp/Đã ký/Thanh lý): trạng thái pháp lý của hợp đồng và công
             việc nội bộ không suy ra nhau. --%>
        <div class="card-box">
            <div class="section-header"><h5>Bàn giao xử lý</h5></div>
            <%-- Câu này trước ghi "Soạn xong thì chuyển xuống..." -- đọc ra thành việc
                 TRƯỚC khi ký, trái hẳn với thiết kế ghi ở đầu khối (bàn giao là trục
                 RIÊNG, không suy ra từ trục tiến độ). Phần lớn việc của Kế toán và
                 Dự án lại rơi vào SAU khi ký. --%>
            <p style="font-size:0.86rem; color:#6b7280; margin:0 0 12px;">
                Chuyển hợp đồng xuống các phòng liên quan &mdash; lúc nào cũng được, trước hay sau khi ký,
                vì đây là việc nội bộ chứ không phải một chặng của tiến trình ký kết. Mỗi phòng tự bấm
                <strong>Đã xử lý xong</strong> kèm ghi chú, và thời gian nằm chờ ở từng phòng được đếm từ lúc bàn giao.
            </p>

            <%-- Thanh lý xong thì không mở lượt mới nữa (chặn thật ở
                 ContractDAO.handOverToDepartments). Danh sách chặng đã giao VẪN hiện --
                 đó là hồ sơ, không phải nút bấm. --%>
            <c:if test="${contract.frozen}">
                <div class="lc-alert" style="margin-bottom:12px;">
                    <i class="fa-solid fa-lock" style="margin-top:2px;"></i>
                    <span>Hợp đồng ở trạng thái “${fn:escapeXml(contract.progressStatus)}” &mdash; không mở thêm lượt bàn giao nào nữa.
                        Chặng nào còn đang treo thì phòng giữ vẫn đóng lại được.</span>
                </div>
            </c:if>

            <c:if test="${hasPendingHandover}">
                <div class="lc-alert" style="margin-bottom:12px;">
                    <i class="fa-solid fa-triangle-exclamation" style="margin-top:2px;"></i>
                    <span>Còn phòng chưa báo xử lý xong. Hợp đồng vẫn ký được &mdash; đây là
                        cảnh báo để không ai quên, không phải điều kiện chặn.</span>
                </div>
            </c:if>

            <c:choose>
                <c:when test="${empty handovers}">
                    <p style="font-size:0.88rem; color:#9ca3af; margin:0 0 14px;">Chưa bàn giao cho phòng nào.</p>
                </c:when>
                <c:otherwise>
                    <div class="table-responsive">
                        <table class="table align-middle" style="font-size:0.9rem;">
                            <thead>
                                <tr>
                                    <th>Phòng</th>
                                    <th>Bàn giao</th>
                                    <th>Tình trạng</th>
                                    <th>Ghi chú</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="hv" items="${handovers}">
                                    <tr>
                                        <td><strong>${fn:escapeXml(hv.departmentName)}</strong></td>
                                        <td>
                                            <fmt:formatDate value="${hv.handedAt}" pattern="dd/MM/yyyy"/>
                                            <div style="font-size:0.78rem; color:#9ca3af;">${fn:escapeXml(hv.handedByName)}</div>
                                        </td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${hv.pending}">
                                                    <span style="color:#8a5a00; font-weight:600;">
                                                        Đang chờ &middot; ${hv.daysWaiting} ngày
                                                    </span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span style="color:#2f6b34; font-weight:600;">
                                                        <i class="fa-solid fa-circle-check"></i>
                                                        Xong sau ${hv.daysWaiting} ngày
                                                    </span>
                                                    <div style="font-size:0.78rem; color:#9ca3af;">
                                                        <fmt:formatDate value="${hv.doneAt}" pattern="dd/MM/yyyy"/>
                                                        &middot; ${fn:escapeXml(hv.doneByName)}
                                                    </div>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td>
                                            <c:if test="${not empty hv.handoverNote}">
                                                <div style="font-size:0.82rem;">${fn:escapeXml(hv.handoverNote)}</div>
                                            </c:if>
                                            <c:if test="${not empty hv.doneNote}">
                                                <div style="font-size:0.82rem; color:#2f6b34;">&rarr; ${fn:escapeXml(hv.doneNote)}</div>
                                            </c:if>
                                            <%-- Nút chỉ mọc ở phòng của chính người đang xem. Chốt chặn
                                                 thật nằm ở AccessControl.canCompleteHandover. --%>
                                            <c:if test="${hv.pending and requestScope['canCompleteHandover_'.concat(hv.handoverId)]}">
                                                <form method="POST" action="${pageContext.request.contextPath}/contract"
                                                      class="row g-2" style="margin-top:6px;">
                                                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                                                    <input type="hidden" name="action" value="completeHandover">
                                                    <input type="hidden" name="contractId" value="${contract.contractId}">
                                                    <input type="hidden" name="handoverId" value="${hv.handoverId}">
                                                    <input type="hidden" name="departmentId" value="${hv.departmentId}">
                                                    <div class="col-8">
                                                        <input type="text" name="doneNote" class="form-control" required
                                                               maxlength="500" placeholder="Phòng bạn đã làm gì? (bắt buộc)">
                                                    </div>
                                                    <div class="col-4">
                                                        <button type="submit" class="btn-primary" style="width:100%;">
                                                            <i class="fa-solid fa-check me-1"></i> Đã xử lý xong
                                                        </button>
                                                    </div>
                                                </form>
                                            </c:if>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>

            <c:if test="${not contract.frozen}">
                <%-- Mặc định tích sẵn Kế toán và Dự án: đó là luồng khách hàng mô
                     tả. Vẫn cho bỏ tích, vì không phải hợp đồng nào cũng qua cả hai. --%>
                <form method="POST" action="${pageContext.request.contextPath}/contract" class="row g-2 align-items-end">
                    <input type="hidden" name="csrfToken" value="${csrfToken}">
                    <input type="hidden" name="action" value="handOver">
                    <input type="hidden" name="contractId" value="${contract.contractId}">
                    <div class="col-md-5">
                        <label style="font-size:0.8rem; color:#6b7280;">Bàn giao cho phòng</label>
                        <div style="display:flex; flex-wrap:wrap; gap:12px; padding-top:6px;">
                            <c:forEach var="dept" items="${departmentList}">
                                <c:if test="${dept.departmentName == 'Kế toán' or dept.departmentName == 'Dự án'
                                              or dept.departmentName == 'Kỹ thuật'}">
                                    <label class="filter-toggle" style="font-size:0.84rem;">
                                        <input type="checkbox" name="departmentId" value="${dept.departmentId}"
                                               ${dept.departmentName == 'Kế toán' or dept.departmentName == 'Dự án' ? 'checked' : ''}>
                                        ${fn:escapeXml(dept.departmentName)}
                                    </label>
                                </c:if>
                            </c:forEach>
                        </div>
                    </div>
                    <div class="col-md-5">
                        <label style="font-size:0.8rem; color:#6b7280;">Dặn phòng nhận (không bắt buộc)</label>
                        <input type="text" name="handoverNote" class="form-control" maxlength="255"
                               placeholder="VD: đã chốt giá, nhờ kiểm điều khoản thanh toán">
                    </div>
                    <div class="col-md-2">
                        <button type="submit" class="btn-primary" style="width:100%;">
                            <i class="fa-solid fa-share-from-square me-1"></i> Bàn giao
                        </button>
                    </div>
                </form>
            </c:if>
        </div>

        <%-- Khối hợp đồng nối kèm đứng ở cột hẹp, giống trang xem. Đã thử cả hai
             chỗ và đo: ở cột hẹp, hợp đồng KHÔNG có liên kết nào (đa số) cho trang
             ngắn hơn 265px; đổi lại, hợp đồng có hai liên kết dài thêm 124px. Lấy
             ca phổ biến. --%>
            </div>
            <c:if test="${canLinkContracts}">
            <div class="tab-pane fade" id="pane-tai-lieu" role="tabpanel">
        <!-- ===== Giấy tờ kèm theo (V34) ===== -->
        <%-- CHỈ LƯU LINK, không tải file lên. Khách hàng chốt như vậy: hồ sơ của
             họ đang nằm trên Drive và vẫn sẽ nằm ở đó. Nói thẳng điều đó ra trên
             màn hình chứ không để người dùng tự đoán vì sao không có nút chọn file.

             Khối này đứng NGOÀI form sửa (form lồng form là HTML không hợp lệ),
             cùng lẽ với khối hàng hoá và kỳ thanh toán. --%>
        <div class="card-box">
            <div class="section-header"><h5>Tài liệu kèm theo</h5></div>
            <p style="font-size:0.85rem; color:#6b7280; margin:0 0 14px;">
                Treo biên bản nghiệm thu, biên bản bàn giao, biên bản thanh lý, hoá đơn… vào đúng hợp đồng này.
                Hệ thống <strong>chỉ lưu link</strong> — tải file lên Drive (hoặc kho nội bộ) rồi dán link vào đây;
                ai xoá file ở đó thì link này thành link chết.
                <strong>Thêm được ở mọi trạng thái</strong>, kể cả sau khi đã thanh lý — phần lớn giấy tờ chỉ
                sinh ra ở thời điểm đó.
            </p>

            <c:choose>
                <c:when test="${empty contractDocuments}">
                    <p style="font-size:0.88rem; color:#9ca3af; margin:0 0 14px;">Chưa có tài liệu nào.</p>
                </c:when>
                <c:otherwise>
                    <div class="table-responsive">
                        <table class="table align-middle" style="font-size:0.9rem;">
                            <thead>
                                <tr><th>Loại</th><th>Tên tài liệu</th><th>Người thêm</th><th>Ngày</th><th></th></tr>
                            </thead>
                            <tbody>
                                <c:forEach var="doc" items="${contractDocuments}">
                                    <tr>
                                        <td><span class="status-pill status-info">${fn:escapeXml(doc.docType)}</span></td>
                                        <td>
                                            <div>${fn:escapeXml(doc.displayName)}</div>
                                            <c:if test="${not empty doc.note}">
                                                <div style="font-size:0.8rem; color:#9ca3af;">${fn:escapeXml(doc.note)}</div>
                                            </c:if>
                                        </td>
                                        <td style="font-size:0.84rem;">${fn:escapeXml(doc.uploadedByName)}</td>
                                        <td style="font-size:0.84rem;">
                                            <fmt:formatDate value="${doc.uploadedAt}" pattern="dd/MM/yyyy"/>
                                        </td>
                                        <td style="text-align:right; white-space:nowrap;">
                                            <a href="${fn:escapeXml(doc.fileUrl)}" target="_blank" rel="noopener noreferrer"
                                               class="btn-outline-action" style="padding:4px 10px; font-size:0.8rem;">
                                                Mở <i class="fa-solid fa-arrow-up-right-from-square"></i>
                                            </a>
                                            <%-- Huỷ là xoá MỀM và bắt buộc có lý do -- nút chỉ mở modal,
                                                 việc gửi do submitOp làm. --%>
                                            <button type="button" class="btn-remove-item js-void-doc"
                                                    data-doc-id="${doc.documentId}"
                                                    data-doc-name="${fn:escapeXml(doc.displayName)}"
                                                    title="Huỷ tài liệu này">
                                                <i class="fa-solid fa-xmark"></i>
                                            </button>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>

            <form method="POST" action="${pageContext.request.contextPath}/contract" class="row g-2 align-items-end"
                  style="margin-top:16px; padding-top:16px; border-top:1.5px solid #eef2f6;">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="addDocument">
                <input type="hidden" name="contractId" value="${contract.contractId}">
                <div class="col-md-3">
                    <label style="font-size:0.8rem; color:#6b7280;">Loại tài liệu</label>
                    <select name="docType" class="form-control" required>
                        <c:forEach var="t" items="${documentTypes}">
                            <option value="${fn:escapeXml(t)}">${fn:escapeXml(t)}</option>
                        </c:forEach>
                    </select>
                </div>
                <div class="col-md-3">
                    <label style="font-size:0.8rem; color:#6b7280;">Tên tài liệu</label>
                    <input type="text" name="docTitle" class="form-control" maxlength="255"
                           placeholder="VD: Biên bản nghiệm thu giai đoạn 1">
                </div>
                <div class="col-md-4">
                    <label style="font-size:0.8rem; color:#6b7280;">Link tài liệu <span style="color:var(--danger);">*</span></label>
                    <input type="url" name="fileUrl" class="form-control" required maxlength="500"
                           placeholder="https://drive.google.com/file/d/...">
                </div>
                <div class="col-md-2">
                    <button type="submit" class="btn-primary" style="width:100%;">
                        <i class="fa-solid fa-plus me-1"></i> Thêm
                    </button>
                </div>
                <div class="col-12">
                    <input type="text" name="docNote" class="form-control" maxlength="255"
                           placeholder="Ghi chú (không bắt buộc) — vd: bản scan có dấu, ký ngày 10/9">
                </div>
            </form>
        </div>

            </div>
            <div class="tab-pane fade" id="pane-noi-hd" role="tabpanel">
        <!-- ===== Đầu ra kéo theo đầu vào ===== -->
        <%-- Hợp đồng BÁN nối với các đơn MUA sinh ra vì nó, và ngược lại. Quan
             hệ nhiều-nhiều nằm ở bảng contract_links, KHÁC hẳn phụ lục bên dưới
             (phụ lục là văn bản sửa đổi của chính hợp đồng này).

             Trên PHỤ LỤC thì khối này biến mất: đầu vào phục vụ cả hợp đồng
             gốc, không phục vụ riêng một văn bản sửa đổi. --%>
        <div class="card-box">
            <div class="section-header">
                <h5>${linkIsSellSide ? 'Đầu vào phục vụ hợp đồng này' : 'Hợp đồng bán mà đơn mua này phục vụ'}</h5>
            </div>
            <p style="font-size:0.86rem; color:#6b7280; margin:0 0 12px;">
                <c:choose>
                    <c:when test="${linkIsSellSide}">
                        Các hợp đồng mua vào sinh ra vì hợp đồng bán này. Một đơn mua gom có thể
                        phục vụ nhiều hợp đồng bán, nên nó xuất hiện ở nhiều nơi.
                    </c:when>
                    <c:otherwise>
                        Các hợp đồng bán mà đơn mua này phục vụ.
                    </c:otherwise>
                </c:choose>
            </p>

            <%-- Đối chiếu tiền CHỈ ở phía bán: một đơn mua phục vụ nhiều hợp
                 đồng bán, nên lấy giá trị bán trừ đi ở phía mua sẽ ra con số vô
                 nghĩa. Đây là chênh lệch THÔ (chưa trừ chi phí nào khác) —
                 màn hình nói rõ để không ai đọc nó thành lợi nhuận. --%>
            <c:if test="${linkIsSellSide and not empty contractLinks}">
                <div class="row g-2" style="margin-bottom:14px;">
                    <div class="col-md-4">
                        <div style="border:1px solid #eef2f6; border-radius:10px; padding:10px 14px; background:#f9fafb;">
                            <div style="font-size:0.72rem; color:#6b7280; text-transform:uppercase; letter-spacing:.3px;">Giá trị bán ra</div>
                            <div class="money-vnd" data-vnd="${contract.currentValue}" style="font-weight:700; color:#111827;">&mdash;</div>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <div style="border:1px solid #f5d9a8; border-radius:10px; padding:10px 14px; background:#fff8ec;">
                            <div style="font-size:0.72rem; color:#8a5a00; text-transform:uppercase; letter-spacing:.3px;">Đầu vào đã nối</div>
                            <div class="money-vnd" data-vnd="${linkedInputValue}" style="font-weight:700; color:#8a5a00;">&mdash;</div>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <div style="border:1px solid #cfe3d0; border-radius:10px; padding:10px 14px; background:#f3f7f3;">
                            <div style="font-size:0.72rem; color:#2f6b34; text-transform:uppercase; letter-spacing:.3px;">Chênh lệch thô</div>
                            <div class="money-signed" data-vnd="${linkedMargin}" style="font-weight:700;">&mdash;</div>
                        </div>
                    </div>
                </div>
                <p style="font-size:0.78rem; color:#9ca3af; margin:-6px 0 14px;">
                    Chênh lệch thô = giá trị bán ra (đã cộng phụ lục) trừ tổng giá trị các đơn mua đã nối.
                    Chưa trừ chi phí thi công, nhân công hay bảo hành — đừng đọc con số này thành lợi nhuận.
                    Đơn mua gom được tính TRỌN VẸN vào từng hợp đồng bán mà nó phục vụ — hệ thống
                    không tự chia tỉ lệ, vì chia thế nào là việc của người lập chứng từ.
                </p>
            </c:if>

            <c:choose>
                <c:when test="${empty contractLinks}">
                    <p style="font-size:0.88rem; color:#9ca3af; margin:0 0 14px;">Chưa nối hợp đồng nào.</p>
                </c:when>
                <c:otherwise>
                    <div class="table-responsive">
                        <table class="table align-middle" style="font-size:0.9rem;">
                            <thead>
                                <tr>
                                    <th>Mã hợp đồng</th>
                                    <th>Tiêu đề</th>
                                    <th>Đối tác</th>
                                    <th class="text-end">Giá trị</th>
                                    <th>Tiến độ</th>
                                    <th></th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="lk" items="${contractLinks}">
                                    <tr>
                                        <td><strong>${fn:escapeXml(lk.other.contractCode)}</strong></td>
                                        <td>
                                            ${fn:escapeXml(lk.other.title)}
                                            <%-- Đơn mua gom: giá trị của nó được tính TRỌN VẸN vào
                                                 mỗi hợp đồng bán mà nó phục vụ, nên không nói ra thì
                                                 "chênh lệch thô" âm ở đây bị đọc thành lỗ. --%>
                                            <c:if test="${lk.shared}">
                                                <div style="font-size:0.76rem; color:#8a5a00;">
                                                    <i class="fa-solid fa-code-branch"></i>
                                                    Đơn mua gom — còn phục vụ ${lk.sharedCount} hợp đồng bán khác
                                                </div>
                                            </c:if>
                                            <c:if test="${not empty lk.note}">
                                                <div style="font-size:0.78rem; color:#9ca3af;">${fn:escapeXml(lk.note)}</div>
                                            </c:if>
                                        </td>
                                        <td>${lk.other.enterprise != null ? fn:escapeXml(lk.other.enterprise.enterpriseName) : '—'}</td>
                                        <td class="text-end"><span class="money-vnd" data-vnd="${lk.other.currentValue}">&mdash;</span></td>
                                        <td>${fn:escapeXml(lk.other.progressStatus)}</td>
                                        <td class="text-end" style="white-space:nowrap;">
                                            <a href="${pageContext.request.contextPath}/contract?action=view&id=${lk.other.contractId}"
                                               class="btn-outline-action"><i class="fa-solid fa-eye"></i> Xem</a>
                                            <form method="POST" action="${pageContext.request.contextPath}/contract"
                                                  style="display:inline;"
                                                  data-confirm="Gỡ liên kết với hợp đồng ${fn:escapeXml(lk.other.contractCode)}?">
                                                <input type="hidden" name="csrfToken" value="${csrfToken}">
                                                <input type="hidden" name="action" value="unlinkContract">
                                                <input type="hidden" name="contractId" value="${contract.contractId}">
                                                <input type="hidden" name="linkId" value="${lk.linkId}">
                                                <button type="submit" class="btn-outline-action" style="color:var(--danger); border-color:#f3c7c7;">
                                                    <i class="fa-solid fa-link-slash"></i> Gỡ
                                                </button>
                                            </form>
                                        </td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </div>
                </c:otherwise>
            </c:choose>

            <%-- Nối được cả khi hợp đồng còn NHÁP và cả sau khi đã thanh lý:
                 đơn mua thường chuẩn bị trước khi ký, còn nối muộn cho một hợp
                 đồng vừa xong là chép lại lịch sử chứ không phải sửa điều khoản. --%>
            <form method="POST" action="${pageContext.request.contextPath}/contract" class="row g-2 align-items-end">
                <input type="hidden" name="csrfToken" value="${csrfToken}">
                <input type="hidden" name="action" value="linkContract">
                <input type="hidden" name="contractId" value="${contract.contractId}">
                <div class="col-md-5">
                    <label style="font-size:0.8rem; color:#6b7280;">
                        ${linkIsSellSide ? 'Chọn hợp đồng mua' : 'Chọn hợp đồng bán'}
                    </label>
                    <select name="otherContractId" class="form-control" required>
                        <option value="">-- Chọn hợp đồng --</option>
                        <c:forEach var="cand" items="${linkCandidates}">
                            <option value="${cand.contractId}">${fn:escapeXml(cand.contractCode)} — ${fn:escapeXml(cand.title)}</option>
                        </c:forEach>
                    </select>
                </div>
                <div class="col-md-5">
                    <label style="font-size:0.8rem; color:#6b7280;">Ghi chú (không bắt buộc)</label>
                    <input type="text" name="linkNote" class="form-control" maxlength="255"
                           placeholder="VD: mua 20km cáp cho giai đoạn 1">
                </div>
                <div class="col-md-2">
                    <button type="submit" class="btn-primary" style="width:100%;">
                        <i class="fa-solid fa-link me-1"></i> Nối
                    </button>
                </div>
            </form>
        </div>
            </div>
            </c:if>
            </div>
        </div>

    </div>

        </div>
    </div>

    <%-- MỘT modal dùng chung cho mọi câu hỏi xác nhận của trang này.

         Thay cho confirm()/prompt()/alert() của trình duyệt: những hộp đó không
         theo giao diện của phần mềm, không xuống dòng được tử tế, không để được
         nhãn tiếng Việt cho nút, và riêng prompt() thì một số trình duyệt đã chặn
         hẳn -- lúc đó thao tác "nhập căn cứ thanh lý" im lặng không chạy.

         Một modal chứ không phải mỗi thao tác một cái: phần khác nhau chỉ là chữ
         và có hỏi lý do hay không, dựng sáu khối gần giống nhau là sớm muộn lệch. --%>
    <div class="modal fade" id="askModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <div id="askIcon" class="modal-icon-ask"><i class="fa-solid fa-circle-question"></i></div>
                </div>
                <div class="modal-body">
                    <h5 class="modal-title" id="askTitle">Xác nhận</h5>
                    <div class="modal-msg" id="askMessage"></div>
                    <div id="askNoteWrap" style="display:none;">
                        <label class="form-label" for="askNote" id="askNoteLabel">Lý do</label>
                        <textarea class="form-control" id="askNote" rows="3" maxlength="500"></textarea>
                        <div class="modal-err" id="askNoteErr"></div>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Huỷ bỏ</button>
                    <button type="button" class="btn-modal-primary" id="askOk">Xác nhận</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Form ẩn cho các thao tác trên hợp đồng (đứng ngoài mọi form khác) -->
    <form id="opForm" method="POST" action="${pageContext.request.contextPath}/contract" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" id="opAction">
        <input type="hidden" name="contractId" value="${contract.contractId}">
        <input type="hidden" name="id" value="${contract.contractId}">
        <input type="hidden" name="toStatus" id="opToStatus">
        <input type="hidden" name="progressNote" id="opProgressNote">
        <input type="hidden" name="voidReason" id="opVoidReason">
        <input type="hidden" name="paymentId" id="opPaymentId">
        <input type="hidden" name="contractProductId" id="opContractProductId">
        <input type="hidden" name="documentId" id="opDocumentId">
        <input type="hidden" name="reason" id="opDocReason">
    </form>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        // Ô điều khoản đã khoá thì disabled -- trình duyệt KHÔNG gửi chúng lên,
        // và server cũng không đọc (ContractController.handleUpdate dựng bản ghi
        // từ giá trị đang có). Nên mọi phép kiểm dưới đây phải bỏ qua ô disabled,
        // nếu không thì một ô mờ để trống sẽ chặn cả lần lưu.
        function activeValue(id) {
            var el = document.getElementById(id);
            return (!el || el.disabled) ? null : el.value;
        }

        function validateForm() {
            var valid = true;
            document.querySelectorAll('.error-text').forEach(function (el) { el.style.display = 'none'; });

            var title = activeValue('title');
            if (title !== null && !title.trim()) { document.getElementById('err-title').style.display = 'block'; valid = false; }

            var contractCode = activeValue('contractCode');
            if (contractCode !== null && !contractCode.trim()) {
                document.getElementById('err-contractCode').style.display = 'block';
                valid = false;
            }

            ['customer', 'contractType', 'owner'].forEach(function (id) {
                var value = activeValue(id);
                if (value !== null && !value) { document.getElementById('err-' + id).style.display = 'block'; valid = false; }
            });

            // Để trống được: bản nháp chưa chốt thời hạn. Nhưng điền cả hai
            // thì thứ tự phải đúng, và phải có đủ hai mốc mới ký được.
            var effectiveDate = activeValue('effectiveDate');
            var endDate = activeValue('endDate');
            if (effectiveDate !== null && endDate !== null
                    && (effectiveDate || endDate) && !(effectiveDate && endDate && effectiveDate <= endDate)) {
                document.getElementById('err-dates').style.display = 'block';
                valid = false;
            }

            // Chế độ chữa sai sót: lý do là bắt buộc. Server từ chối khi thiếu
            // (ContractDAO.correct trả false), đây chỉ để báo sớm.
            var reason = document.getElementById('correctionReason');
            if (reason && !reason.disabled && document.getElementById('formAction').value === 'correct'
                    && !reason.value.trim()) {
                document.getElementById('err-correctionReason').style.display = 'block';
                valid = false;
            }

            return valid;
        }

        // Bật chế độ chữa sai sót: mở lại các ô điều khoản, đổi đích của form,
        // hiện ô lý do. Một chiều -- muốn thoát thì tải lại trang, để không ai
        // vô tình bật lên rồi lưu nhầm dưới dạng "sửa sai sót".
        var toggleCorrection = document.getElementById('toggleCorrection');
        if (toggleCorrection) {
            toggleCorrection.addEventListener('click', function () {
                document.querySelectorAll('[data-term]').forEach(function (el) { el.disabled = false; });
                document.getElementById('formAction').value = 'correct';
                document.getElementById('correctionBox').style.display = 'block';
                toggleCorrection.disabled = true;
                document.getElementById('correctionReason').focus();
            });
        }

        function submitOp(action) {
            document.getElementById('opAction').value = action;
            document.getElementById('opForm').submit();
        }

        // Số tiền render ở client để dùng đúng cách gom nhóm của tiếng Việt.
        // Giá trị trên dòng PHỤ LỤC là chênh lệch, nên phải mang dấu:
        // "+250.000.000" đọc ra ngay là bổ sung, "250.000.000" thì không.
        document.querySelectorAll('.money-signed').forEach(function (el) {
            var n = Number(el.dataset.vnd);
            if (el.dataset.vnd === '' || isNaN(n)) { el.textContent = 'không đổi'; return; }
            el.textContent = (n > 0 ? '+' : '') + n.toLocaleString('vi-VN', { maximumFractionDigits: 0 }) + ' ₫';
            el.style.color = n < 0 ? '#b45309' : '#2f6b34';
        });
        document.querySelectorAll('.money-vnd').forEach(function (el) {
            var n = Number(el.dataset.vnd);
            el.textContent = isNaN(n) ? '\u2014' : n.toLocaleString('vi-VN', { maximumFractionDigits: 0 }) + ' \u20ab';
        });

        // ===== Hỏi xác nhận bằng modal trong trang =====
        //
        // KHÔNG dùng confirm()/prompt()/alert() của trình duyệt nữa. Lý do cụ thể chứ
        // không phải cho đẹp: prompt() bị một số trình duyệt chặn thẳng (Chrome chặn
        // trong iframe, và chặn luôn khi người dùng tick "không hiện hộp thoại nữa")
        // — lúc đó thao tác "nhập căn cứ thanh lý" im lặng không chạy mà không báo gì.
        //
        // ask(...) gọi cb(note) khi người dùng bấm Xác nhận; bấm Huỷ thì không gọi gì.
        function ask(opts, cb) {
            var el = document.getElementById('askModal');
            var modal = bootstrap.Modal.getOrCreateInstance(el);
            var note = document.getElementById('askNote');
            var err = document.getElementById('askNoteErr');
            var ok = document.getElementById('askOk');

            document.getElementById('askTitle').textContent = opts.title;
            document.getElementById('askMessage').textContent = opts.message || '';
            var icon = document.getElementById('askIcon');
            icon.className = opts.danger ? 'modal-icon-warn' : 'modal-icon-ask';
            icon.innerHTML = opts.danger
                ? '<i class="fa-solid fa-triangle-exclamation"></i>'
                : '<i class="fa-solid fa-circle-question"></i>';
            ok.className = opts.danger ? 'btn-modal-danger' : 'btn-modal-primary';
            ok.textContent = opts.okLabel || 'Xác nhận';

            document.getElementById('askNoteWrap').style.display = opts.noteLabel ? '' : 'none';
            document.getElementById('askNoteLabel').textContent = opts.noteLabel || '';
            note.value = '';
            note.placeholder = opts.notePlaceholder || '';
            err.style.display = 'none';

            // Thay nút để bỏ mọi listener của lần gọi trước — không thì mở modal lần
            // thứ hai sẽ chạy cả hành động của lần thứ nhất.
            var fresh = ok.cloneNode(true);
            ok.parentNode.replaceChild(fresh, ok);
            fresh.addEventListener('click', function () {
                if (opts.noteLabel && note.value.trim() === '') {
                    err.textContent = opts.noteRequiredMsg || 'Phải nhập ô này thì mới tiếp tục được.';
                    err.style.display = 'block';
                    note.focus();
                    return;
                }
                modal.hide();
                cb(note.value.trim());
            });

            el.addEventListener('shown.bs.modal', function once() {
                el.removeEventListener('shown.bs.modal', once);
                if (opts.noteLabel) { note.focus(); }
            });
            modal.show();
        }

        // requireNote=true với thanh lý và chấm dứt sớm: hai bước đó đóng băng
        // hợp đồng vĩnh viễn, không có đường quay lại, nên phải biết căn cứ.
        // Server kiểm lại cả hai điều (ContractDAO.changeProgressStatus).
        function changeProgress(toStatus, message, requireNote) {
            ask({
                title: requireNote ? 'Bước này không quay lại được' : 'Xác nhận',
                message: message,
                danger: !!requireNote,
                noteLabel: requireNote ? 'Căn cứ' : null,
                notePlaceholder: 'VD: Biên bản thanh lý số 07 ngày 12/09/2026',
                noteRequiredMsg: 'Phải nhập căn cứ thì mới thực hiện được — bước này không quay lại được.'
            }, function (note) {
                document.getElementById('opToStatus').value = toStatus;
                document.getElementById('opProgressNote').value = note;
                submitOp('changeProgress');
            });
        }

        // Hỏi LÝ DO chứ không hỏi "có chắc không": bản ghi bị huỷ vẫn nằm trong
        // CSDL và vẫn có dòng nhật ký, nên thứ cần thu thập là vì sao.
        function confirmVoid() {
            ask({
                title: 'Huỷ bản ghi hợp đồng',
                message: 'Bản ghi này sẽ biến khỏi danh sách.\n'
                    + 'Đây là thao tác sửa nhập liệu sai, không phải huỷ hợp đồng ngoài đời.',
                danger: true,
                okLabel: 'Huỷ bản ghi',
                noteLabel: 'Lý do',
                notePlaceholder: 'VD: nhập trùng với 06/2026/HĐKT-POSTEF',
                noteRequiredMsg: 'Phải có lý do thì mới huỷ được bản ghi.'
            }, function (reason) {
                document.getElementById('opVoidReason').value = reason;
                submitOp('delete');
            });
        }

        function markPaid(paymentId) {
            ask({ title: 'Ghi nhận đã thu', message: 'Ghi nhận tiền của kỳ này đã về hôm nay?' },
                function () {
                    document.getElementById('opPaymentId').value = paymentId;
                    submitOp('markPaid');
                });
        }

        function confirmRemovePayment(paymentId) {
            ask({ title: 'Xoá kỳ thanh toán', message: 'Xoá kỳ thanh toán này?',
                  danger: true, okLabel: 'Xoá' },
                function () {
                    document.getElementById('opPaymentId').value = paymentId;
                    submitOp('removePayment');
                });
        }

        function confirmRemoveProduct(contractProductId) {
            ask({ title: 'Gỡ hàng hoá', message: 'Gỡ hàng hoá này khỏi hợp đồng?',
                  danger: true, okLabel: 'Gỡ' },
                function () {
                    document.getElementById('opContractProductId').value = contractProductId;
                    submitOp('removeProduct');
                });
        }

        // Huỷ tài liệu: xoá MỀM nên BẮT BUỘC lý do -- dòng ở lại trong CSDL, và
        // nhật ký phải nói được vì sao nó biến mất khỏi màn hình.
        document.querySelectorAll('.js-void-doc').forEach(function (btn) {
            btn.addEventListener('click', function () {
                ask({
                    title: 'Huỷ tài liệu',
                    message: 'Huỷ "' + btn.dataset.docName + '" khỏi hồ sơ hợp đồng? '
                           + 'Dòng này vẫn nằm trong nhật ký, kèm lý do bạn nhập.',
                    // ask() coi noteLabel là "có ô ghi chú VÀ bắt buộc điền" --
                    // không có cờ riêng, đừng thêm noteRequired cho có.
                    noteLabel: 'Lý do huỷ (bắt buộc)',
                    notePlaceholder: 'VD: dán nhầm link của hợp đồng khác',
                    danger: true,
                    okLabel: 'Huỷ tài liệu'
                }, function (reason) {
                    document.getElementById('opDocumentId').value = btn.dataset.docId;
                    document.getElementById('opDocReason').value = reason;
                    submitOp('voidDocument');
                });
            });
        });

        // Gỡ liên kết bán-mua: form nằm sẵn trong bảng, chặn lại để hỏi rồi mới gửi.
        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (e) {
                if (form.dataset.confirmed === '1') { return; }
                e.preventDefault();
                ask({ title: 'Gỡ liên kết', message: form.dataset.confirm,
                      danger: true, okLabel: 'Gỡ' },
                    function () {
                        form.dataset.confirmed = '1';
                        form.submit();
                    });
            });
        });
    </script>

    <script>
        // Nhớ tab đang mở qua các lần tải lại trang.
        //
        // Cần vì mọi thao tác ở đây đều là POST rồi chuyển hướng về chính trang này:
        // lập một kỳ thu xong mà nhảy về tab Hàng hoá thì lần nào cũng phải bấm lại.
        //
        // sessionStorage chứ không phải localStorage: nhớ trong phiên làm việc thôi,
        // mở lại hôm sau thì quay về tab đầu. Bọc try/catch vì trình duyệt chặn
        // cookie/site data sẽ ném lỗi ngay ở lần đọc đầu tiên.
        (function () {
            var KEY = 'poscsContractTab';
            var tabs = document.getElementById('contractTabs');
            if (!tabs || !window.bootstrap) { return; }

            try {
                var saved = sessionStorage.getItem(KEY);
                if (saved) {
                    var btn = tabs.querySelector('[data-bs-target="' + saved + '"]');
                    // Tab đã lưu có thể KHÔNG còn (vd vừa thanh lý xong thì tab Bước
                    // tiến trình biến mất) -- lúc đó cứ để tab đầu.
                    if (btn) { new bootstrap.Tab(btn).show(); }
                }
            } catch (e) { /* không đọc được thì mở tab đầu, không phải lỗi */ }

            tabs.addEventListener('shown.bs.tab', function (e) {
                try {
                    sessionStorage.setItem(KEY, e.target.getAttribute('data-bs-target'));
                } catch (err) { /* bỏ qua */ }
            });
        })();
    </script>
    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
