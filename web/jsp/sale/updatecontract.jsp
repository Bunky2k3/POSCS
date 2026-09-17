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
        .page-container { max-width: 980px; margin: 28px auto; padding: 0 20px 32px; }
        .page-header-row { margin-bottom: 22px; }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }
        .back-link-top { color: var(--primary); font-size: 0.85rem; text-decoration: none; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; margin-bottom: 10px; }
        .back-link-top:hover { text-decoration: underline; }

        .card-box { background: #fff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0, 40, 80, 0.08); padding: 24px 28px 28px; }

        .section-header { display: flex; justify-content: space-between; align-items: center; margin: 24px 0 16px; padding-bottom: 10px; border-bottom: 1.5px solid #eef2f6; }
        .section-header:first-child { margin-top: 0; }
        .section-header h5 { font-weight: 700; color: var(--primary-dark); font-size: 0.98rem; margin: 0; }

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
                        Nó có vòng đời riêng và phải được ký như một hợp đồng thường.</span>
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
                    <span><strong>Hợp đồng đã ${fn:escapeXml(contract.progressStatus)}.</strong>
                        Nội dung không sửa được nữa, kể cả bởi quản trị viên — phát sinh sau thời điểm
                        này phải lập hợp đồng mới. Bên dưới chỉ còn phần ghi nhận tiền về.</span>
                </div>
            </div>
        </c:if>
        <c:if test="${not contract.frozen}">
        <div class="card-box">
            <c:if test="${not empty param.error}">
                <div class="alert alert-danger py-2 px-3 mb-3" style="font-size: 0.9rem; border-radius: 12px;">
                    <c:choose>
                        <c:when test="${param.error == 'invalid_drive_link'}">Link file PDF phải là địa chỉ bắt đầu bằng http:// hoặc https://. Vui lòng dán lại.</c:when>
                        <c:when test="${param.error == 'invalid'}">Thông tin hợp đồng chưa hợp lệ. Vui lòng kiểm tra lại các ô bắt buộc.</c:when>
                        <c:when test="${param.error == 'duplicate_code'}">Mã hợp đồng này đã có hợp đồng khác dùng. Kiểm lại số trên bản giấy hoặc nhập mã khác.</c:when>
                        <c:when test="${param.error == 'update_failed'}">Không lưu được thay đổi. Vui lòng thử lại.</c:when>
                        <c:when test="${param.error == 'missing_reason'}">Sửa sai sót trên hợp đồng đã ký thì bắt buộc phải nêu lý do.</c:when>
                        <c:when test="${param.error == 'amendment_not_allowed'}">Hợp đồng này không lập phụ lục được: nó phải đã ký và chưa thanh lý, và bản thân nó không được là phụ lục.</c:when>
                        <c:otherwise>Đã có lỗi xảy ra. Vui lòng thử lại.</c:otherwise>
                    </c:choose>
                </div>
            </c:if>

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
                    <div class="col-md-6 field-row">
                        <label>Khách hàng <span class="req">*</span></label>
                        <select class="form-select" id="customer" name="enterpriseId" ${canEditTerms ? '' : 'disabled'} data-term="1">
                            <option value="">-- Chọn khách hàng --</option>
                            <c:forEach var="customer" items="${customerList}">
                                <option value="${customer.enterpriseId}" ${customer.enterpriseId == contract.enterpriseId ? 'selected' : ''}>${fn:escapeXml(customer.enterpriseName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-customer">Vui lòng chọn khách hàng.</span>
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Người phụ trách <span class="req">*</span></label>
                        <select class="form-select" id="owner" name="ownerId">
                            <option value="">-- Chọn nhân viên --</option>
                            <c:forEach var="staff" items="${userList}">
                                <option value="${staff.userId}" ${staff.userId == contract.ownerId ? 'selected' : ''}>${fn:escapeXml(staff.fullName)}</option>
                            </c:forEach>
                        </select>
                        <span class="error-text" id="err-owner">Vui lòng chọn người phụ trách.</span>
                    </div>
                    <div class="col-md-6 field-row">
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
                    <div class="col-md-6 field-row">
                        <label>Người ký bên mình</label>
                        <input type="text" class="form-control" id="signerName" name="signerName" maxlength="100" value="${fn:escapeXml(contract.signerName)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Chức vụ</label>
                        <input type="text" class="form-control" id="signerPosition" name="signerPosition"
                               maxlength="100" placeholder="VD: Giám đốc chi nhánh" value="${fn:escapeXml(contract.signerPosition)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <div class="col-md-6 field-row">
                        <label>Người ký bên đối tác</label>
                        <input type="text" class="form-control" id="counterpartySignerName" name="counterpartySignerName" maxlength="100" value="${fn:escapeXml(contract.counterpartySignerName)}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                    </div>
                    <div class="col-md-6 field-row">
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
                    <div class="col-md-6 field-row">
                        <label>Giá trị hợp đồng (VNĐ)</label>
                        <input type="text" class="form-control" id="contractValue" name="contractValue"
                               inputmode="numeric" placeholder="VD: 1.500.000.000" value="${contract.contractValue}" ${canEditTerms ? '' : 'disabled'} data-term="1">
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            Giá trị theo điều khoản. Số tiền thực thu ghi ở mục Kỳ thanh toán — hai
                            con số lệch nhau chính là công nợ.
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

                    <div class="col-12 field-row">
                        <label>Link file PDF hợp đồng (Google Drive)</label>
                        <input type="url" class="form-control" id="attachmentUrl" name="attachmentUrl"
                               value="${fn:escapeXml(contract.attachmentUrl)}"
                               placeholder="VD: https://drive.google.com/file/d/...">
                        <span style="font-size:0.78rem; color:#9ca3af; display:block; margin-top:6px;">
                            Tải bản PDF đã ký lên Drive rồi dán link vào đây. Để trống nếu chưa có.
                        </span>
                    </div>
                </div>

                <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
                <p style="font-size:0.86rem; color:#6b7280; margin:0 0 4px;">
                    Hạng mục được thêm và gỡ ở trang chi tiết hợp đồng, không sửa tại đây.
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

        <%-- MỌI THAO TÁC GHI CỦA HỢP ĐỒNG NẰM Ở TRANG NÀY.

             Trang xem (viewcontractdetail.jsp) cố ý không có nút nào gây thay
             đổi -- xem ghi chú ở đầu khối vòng đời bên đó. Các khối dưới đây
             đứng NGOÀI form sửa thông tin ở trên: form lồng form là HTML không
             hợp lệ, trình duyệt sẽ tự cắt và nút bấm gửi đi thiếu tham số. --%>

        <!-- ===== Bước vòng đời ===== -->
        <c:if test="${canSign or canClose or canVoid}">
        <div class="card-box" style="margin-top:20px;">
            <div class="section-header"><h5>Bước vòng đời</h5></div>
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
        </c:if>

        <!-- ===== Phụ lục ===== -->
        <%-- Hiện cả khi danh sách rỗng, MIỄN LÀ hợp đồng này nhận được phụ lục:
             ở đó cái người dùng cần là cái nút, và một khối trống có nút nói rõ
             hơn hẳn việc không có gì.

             Trên chính một phụ lục thì khối này biến mất (canAddAmendment false
             vì một tầng) và thay bằng đường ngược về hợp đồng gốc. --%>
        <c:if test="${canAddAmendment or not empty amendments}">
        <div class="card-box" style="margin-top:20px;">
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
        </c:if>

        <!-- ===== Hạng mục hàng hoá ===== -->
        <div class="card-box" style="margin-top:20px;">
            <div class="section-header"><h5>Hạng mục sản phẩm / dịch vụ</h5></div>
            <c:choose>
                <c:when test="${empty contractProducts}">
                    <div style="color:#9ca3af; font-size:0.87rem;">Chưa gắn hàng hoá nào.</div>
                </c:when>
                <c:otherwise>
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

        <!-- ===== Kỳ thanh toán ===== -->
        <div class="card-box" style="margin-top:20px;">
            <div class="section-header"><h5>Kỳ thanh toán</h5></div>
            <c:choose>
                <c:when test="${empty contractPayments}">
                    <div style="color:#9ca3af; font-size:0.87rem;">Chưa lập kỳ thanh toán nào.</div>
                </c:when>
                <c:otherwise>
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
                                <input type="text" class="form-control" name="invoiceAmount" inputmode="numeric"
                                       placeholder="VD: 450.000.000" required>
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
        document.querySelectorAll('.money-vnd').forEach(function (el) {
            var n = Number(el.dataset.vnd);
            el.textContent = isNaN(n) ? '\u2014' : n.toLocaleString('vi-VN', { maximumFractionDigits: 0 }) + ' \u20ab';
        });

        // requireNote=true với thanh lý và chấm dứt sớm: hai bước đó đóng băng
        // hợp đồng vĩnh viễn, không có đường quay lại, nên phải biết căn cứ.
        // Server kiểm lại cả hai điều (ContractDAO.changeProgressStatus).
        function changeProgress(toStatus, message, requireNote) {
            var note = null;
            if (requireNote) {
                note = prompt(message);
                if (note === null) { return; }
                if (note.trim() === '') {
                    alert('Phải nhập căn cứ thì mới thực hiện được — bước này không quay lại được.');
                    return;
                }
            } else if (!confirm(message)) {
                return;
            }
            document.getElementById('opToStatus').value = toStatus;
            document.getElementById('opProgressNote').value = note === null ? '' : note.trim();
            submitOp('changeProgress');
        }

        // Hỏi LÝ DO chứ không hỏi "có chắc không": bản ghi bị huỷ vẫn nằm trong
        // CSDL và vẫn có dòng nhật ký, nên thứ cần thu thập là vì sao.
        function confirmVoid() {
            var reason = prompt('Huỷ bản ghi hợp đồng này khỏi danh sách.\n'
                + 'Đây là thao tác sửa nhập liệu sai, không phải huỷ hợp đồng ngoài đời.\n\n'
                + 'Nhập lý do:');
            if (reason === null) { return; }
            if (reason.trim() === '') {
                alert('Phải có lý do thì mới huỷ được bản ghi.');
                return;
            }
            document.getElementById('opVoidReason').value = reason.trim();
            submitOp('delete');
        }

        function markPaid(paymentId) {
            if (!confirm('Ghi nhận tiền của kỳ này đã về hôm nay?')) { return; }
            document.getElementById('opPaymentId').value = paymentId;
            submitOp('markPaid');
        }

        function confirmRemovePayment(paymentId) {
            if (!confirm('Xoá kỳ thanh toán này?')) { return; }
            document.getElementById('opPaymentId').value = paymentId;
            submitOp('removePayment');
        }

        function confirmRemoveProduct(contractProductId) {
            if (!confirm('Gỡ hàng hoá này khỏi hợp đồng?')) { return; }
            document.getElementById('opContractProductId').value = contractProductId;
            submitOp('removeProduct');
        }
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
