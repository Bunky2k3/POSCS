<%@page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@taglib prefix="c" uri="jakarta.tags.core"%>
<%@taglib prefix="fmt" uri="jakarta.tags.fmt"%>
<%@taglib prefix="fn" uri="jakarta.tags.functions"%>
<%--
    Bố cục lấy cảm hứng từ trang danh mục sản phẩm postef.com.vn/san-pham/:
    panel "Danh mục sản phẩm" bên trái (kèm số lượng, bấm để lọc) + lưới thẻ
    sản phẩm bên phải, thay cho bảng liệt kê thông thường.

    Servlet cần đặt các request attribute sau trước khi forward tới trang này:
      - productList    : List<poscs.model.Product>          (mỗi Product nên có sẵn .category đã join)
      - categoryList   : List<poscs.model.ProductCategory>   (toàn bộ danh mục, để đổ panel bên trái)
      - categoryCounts : Map<Integer, Integer>                (category_id -> số sản phẩm còn hiệu lực)
      - grandTotal     : int                                  (tổng số sản phẩm, không lọc, cho mục "Tất cả")
      - currentPage, totalPages, totalCount : thông tin phân trang (BR-12)
      - keyword, categoryFilter : giá trị filter hiện tại (để giữ lại lúc submit lại form tìm kiếm)
      - csrfToken : cho form xoá (POST)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Danh sách sản phẩm - POSCS Portal</title>

    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/appshell.css">

    <style>
        .page-container {
            max-width: 1320px;
            margin: 28px auto;
            padding: 0 24px 32px;
        }

        .page-header-row {
            display: flex; justify-content: space-between; align-items: flex-start;
            margin-bottom: 22px; flex-wrap: wrap; gap: 14px;
        }
        .page-header-row h2 { font-weight: 700; color: var(--primary-dark); font-size: 1.4rem; margin-bottom: 4px; }
        .page-header-row p { color: #6b7280; font-size: 0.9rem; }

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

        /* ===== Bố cục: panel danh mục + nội dung ===== */
        .catalog-layout { display: flex; align-items: flex-start; gap: 22px; }

        /* ===== Panel "Danh mục sản phẩm" (kiểu postef.com.vn/san-pham/) ===== */
        .category-panel { width: 260px; flex-shrink: 0; padding: 20px 18px; position: sticky; top: 90px; }
        .category-panel-title {
            font-weight: 700; color: var(--primary-dark); font-size: 0.82rem;
            text-transform: uppercase; letter-spacing: .4px; margin-bottom: 14px;
        }
        .category-list { list-style: none; margin: 0; padding: 0; }
        .category-list li { margin-bottom: 2px; }
        .category-count { font-size: 0.76rem; color: #9ca3af; font-weight: 600; }
        .category-list-divider { border: none; border-top: 1px solid #eef2f6; margin: 10px 0; }

        /* ===== Cây danh mục 3 cấp dạng accordion (thả xuống) ===== */
        .cat-row { display: flex; align-items: center; gap: 2px; }
        .cat-link {
            flex: 1; min-width: 0; display: flex; justify-content: space-between; align-items: center; gap: 8px;
            padding: 9px 12px; border-radius: 10px;
            color: #374151; font-weight: 600; font-size: 0.87rem; text-decoration: none;
        }
        .cat-link:hover { background: #f0f9ff; color: var(--primary-dark); }
        .cat-link.active { background: linear-gradient(120deg, var(--primary), var(--primary-light)); color: #fff; }
        .cat-link.active .category-count { color: #fff; opacity: 0.85; }
        .cat-toggle {
            flex-shrink: 0; width: 26px; height: 26px; border: none; background: transparent;
            color: #9ca3af; border-radius: 8px; cursor: pointer;
            display: flex; align-items: center; justify-content: center; font-size: 0.7rem;
        }
        .cat-toggle:hover { background: #f0f9ff; color: var(--primary); }
        .cat-toggle i { transition: transform 0.2s ease; }
        .cat-toggle.collapsed i { transform: rotate(-90deg); }
        .category-sublist {
            list-style: none; margin: 2px 0 6px 12px; padding: 0 0 0 12px;
            border-left: 1px dashed #e5e7eb;
        }
        .category-sublist .cat-link { font-size: 0.83rem; font-weight: 500; padding: 7px 10px; }
        .category-sublist .category-sublist { margin-left: 8px; }
        .category-sublist .category-sublist .cat-link { font-size: 0.8rem; color: #4b5563; }

        /* ===== Ô tìm kiếm ===== */
        .search-bar { padding: 16px 18px; margin-bottom: 20px; }
        .search-input-wrap { position: relative; }
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
        .active-filter-chip {
            display: inline-flex; align-items: center; gap: 8px;
            background: #eaf6ff; color: var(--primary-dark); font-weight: 600; font-size: 0.82rem;
            border-radius: 20px; padding: 6px 12px; margin-top: 12px;
        }
        .active-filter-chip a { color: var(--primary-dark); text-decoration: none; }
        .active-filter-chip a:hover { color: var(--danger); }

        /* ===== Lưới thẻ sản phẩm ===== */
        .product-grid {
            display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
            gap: 20px;
        }
        .product-card {
            display: flex; flex-direction: column; overflow: hidden;
            transition: box-shadow 0.15s ease, transform 0.15s ease;
        }
        .product-card:hover { box-shadow: 0 16px 36px rgba(0, 40, 80, 0.14); transform: translateY(-2px); }
        .product-card-media {
            position: relative; width: 100%; height: 150px;
            background: #eef6fb; display: flex; align-items: center; justify-content: center;
            color: var(--primary-light); font-size: 2rem; overflow: hidden;
        }
        .product-card-media img { width: 100%; height: 100%; object-fit: cover; }
        .product-card-category {
            position: absolute; top: 10px; left: 10px;
            background: rgba(255,255,255,0.92); color: var(--primary-dark);
            font-size: 0.68rem; font-weight: 700; padding: 3px 10px; border-radius: 20px;
        }
        .product-card-body { padding: 14px 16px 16px; display: flex; flex-direction: column; gap: 6px; flex: 1; }
        .product-card-code { font-size: 0.72rem; font-weight: 700; color: var(--primary); }
        .product-card-name {
            color: #111827; font-weight: 600; font-size: 0.92rem; text-decoration: none;
            display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
            min-height: 2.5em; line-height: 1.25;
        }
        .product-card-name:hover { color: var(--primary); }
        .product-card-footer {
            display: flex; justify-content: space-between; align-items: center;
            margin-top: auto; padding-top: 10px; border-top: 1px solid #f3f4f6;
        }
        .product-card-updated { font-size: 0.72rem; color: #9ca3af; }
        .action-icons { display: flex; gap: 6px; }
        .action-icons button {
            width: 30px; height: 30px; border-radius: 8px; border: none;
            background: #f3f4f6; color: #6b7280; cursor: pointer;
            display: flex; align-items: center; justify-content: center;
            font-size: 0.78rem; transition: all 0.15s;
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
            padding: 18px 4px 0; flex-wrap: wrap; gap: 10px;
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

        @media (max-width: 900px) {
            .catalog-layout { flex-direction: column; }
            .category-panel { width: 100%; position: static; }
        }
        @media (max-width: 768px) {
            .page-container { padding: 0 14px 32px; }
        }
    </style>
</head>
<body>

    <%@ include file="/jsp/common/topbar.jsp" %>
    <div class="app-shell">
        <c:set var="activeNav" value="product" scope="request"/>
        <%@ include file="/jsp/common/sidebar.jsp" %>
        <div class="main-content">


    <div class="page-container">

        <div class="page-header-row">
            <div>
                <h2>Danh sách sản phẩm</h2>
                <p>Quản lý danh mục thiết bị / sản phẩm POS</p>
            </div>
            <div class="header-actions">
                <a href="${pageContext.request.contextPath}/product?action=importForm" class="btn-outline-action"><i class="fa-solid fa-file-import"></i> Nhập Excel</a>
                <a href="${pageContext.request.contextPath}/product?action=new" class="btn-add"><i class="fa-solid fa-plus"></i> Thêm sản phẩm</a>
            </div>
        </div>

        <div class="catalog-layout">
            <!-- ===== Panel "Danh mục sản phẩm" ===== -->
            <aside class="category-panel card-box">
                <div class="category-panel-title">Danh mục sản phẩm</div>
                <ul class="category-list">
                    <li>
                        <a href="${pageContext.request.contextPath}/product?action=list&keyword=${fn:escapeXml(keyword)}"
                           class="cat-link ${empty categoryFilter ? 'active' : ''}">
                            <span>Tất cả sản phẩm</span>
                            <span class="category-count">(${grandTotal})</span>
                        </a>
                    </li>
                    <li><hr class="category-list-divider"></li>

                    <c:forEach var="l1" items="${rootCategories}">
                        <li class="cat-node">
                            <div class="cat-row">
                                <a href="${pageContext.request.contextPath}/product?action=list&categoryId=${l1.categoryId}&keyword=${fn:escapeXml(keyword)}"
                                   class="cat-link ${categoryFilter == l1.categoryId ? 'active' : ''}">
                                    <span>${fn:escapeXml(l1.categoryName)}</span>
                                    <span class="category-count">(${empty categoryCounts[l1.categoryId] ? 0 : categoryCounts[l1.categoryId]})</span>
                                </a>
                                <c:if test="${not empty childrenByParent[l1.categoryId]}">
                                    <button type="button" class="cat-toggle ${expandedCategoryIds.contains(l1.categoryId) ? '' : 'collapsed'}"
                                            data-bs-toggle="collapse" data-bs-target="#catkids-${l1.categoryId}"
                                            aria-expanded="${expandedCategoryIds.contains(l1.categoryId)}" aria-label="Mở/thu danh mục con">
                                        <i class="fa-solid fa-chevron-down"></i>
                                    </button>
                                </c:if>
                            </div>
                            <c:if test="${not empty childrenByParent[l1.categoryId]}">
                                <div class="collapse ${expandedCategoryIds.contains(l1.categoryId) ? 'show' : ''}" id="catkids-${l1.categoryId}">
                                    <ul class="category-sublist">
                                        <c:forEach var="l2" items="${childrenByParent[l1.categoryId]}">
                                            <li class="cat-node">
                                                <div class="cat-row">
                                                    <a href="${pageContext.request.contextPath}/product?action=list&categoryId=${l2.categoryId}&keyword=${fn:escapeXml(keyword)}"
                                                       class="cat-link ${categoryFilter == l2.categoryId ? 'active' : ''}">
                                                        <span>${fn:escapeXml(l2.categoryName)}</span>
                                                        <span class="category-count">(${empty categoryCounts[l2.categoryId] ? 0 : categoryCounts[l2.categoryId]})</span>
                                                    </a>
                                                    <c:if test="${not empty childrenByParent[l2.categoryId]}">
                                                        <button type="button" class="cat-toggle ${expandedCategoryIds.contains(l2.categoryId) ? '' : 'collapsed'}"
                                                                data-bs-toggle="collapse" data-bs-target="#catkids-${l2.categoryId}"
                                                                aria-expanded="${expandedCategoryIds.contains(l2.categoryId)}" aria-label="Mở/thu danh mục con">
                                                            <i class="fa-solid fa-chevron-down"></i>
                                                        </button>
                                                    </c:if>
                                                </div>
                                                <c:if test="${not empty childrenByParent[l2.categoryId]}">
                                                    <div class="collapse ${expandedCategoryIds.contains(l2.categoryId) ? 'show' : ''}" id="catkids-${l2.categoryId}">
                                                        <ul class="category-sublist">
                                                            <c:forEach var="l3" items="${childrenByParent[l2.categoryId]}">
                                                                <li>
                                                                    <a href="${pageContext.request.contextPath}/product?action=list&categoryId=${l3.categoryId}&keyword=${fn:escapeXml(keyword)}"
                                                                       class="cat-link ${categoryFilter == l3.categoryId ? 'active' : ''}">
                                                                        <span>${fn:escapeXml(l3.categoryName)}</span>
                                                                        <span class="category-count">(${empty categoryCounts[l3.categoryId] ? 0 : categoryCounts[l3.categoryId]})</span>
                                                                    </a>
                                                                </li>
                                                            </c:forEach>
                                                        </ul>
                                                    </div>
                                                </c:if>
                                            </li>
                                        </c:forEach>
                                    </ul>
                                </div>
                            </c:if>
                        </li>
                    </c:forEach>
                </ul>
            </aside>

            <div style="flex:1; min-width:0;">
                <!-- ===== Ô tìm kiếm ===== -->
                <form class="search-bar card-box" method="GET" action="${pageContext.request.contextPath}/product" id="filterForm">
                    <input type="hidden" name="action" value="list">
                    <input type="hidden" name="categoryId" id="categoryIdInput" value="${categoryFilter}">
                    <div class="search-input-wrap">
                        <i class="fa-solid fa-magnifying-glass"></i>
                        <input type="text" id="searchInput" name="keyword" value="${fn:escapeXml(keyword)}" placeholder="Tìm theo mã SP, tên sản phẩm...">
                    </div>
                    <c:if test="${not empty categoryFilter}">
                        <span class="active-filter-chip">
                            <i class="fa-solid fa-filter"></i> Đang lọc theo danh mục
                            <a href="${pageContext.request.contextPath}/product?action=list&keyword=${fn:escapeXml(keyword)}" title="Bỏ lọc"><i class="fa-solid fa-xmark"></i></a>
                        </span>
                    </c:if>
                </form>

                <!-- ===== Lưới thẻ sản phẩm ===== -->
                <c:choose>
                    <c:when test="${empty productList}">
                        <div class="empty-state card-box">
                            <i class="fa-regular fa-folder-open"></i>
                            <p>Không có sản phẩm để hiển thị.</p>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="product-grid">
                            <c:forEach var="product" items="${productList}">
                                <div class="product-card card-box">
                                    <div class="product-card-media">
                                        <c:choose>
                                            <c:when test="${not empty product.primaryImageUrl}">
                                                <c:choose>
                                                    <c:when test="${fn:startsWith(product.primaryImageUrl, 'http')}"><c:set var="cardImgSrc" value="${product.primaryImageUrl}"/></c:when>
                                                    <c:otherwise><c:set var="cardImgSrc" value="${pageContext.request.contextPath}${product.primaryImageUrl}"/></c:otherwise>
                                                </c:choose>
                                                <img src="${fn:escapeXml(cardImgSrc)}" alt="${fn:escapeXml(product.productName)}">
                                            </c:when>
                                            <c:otherwise>
                                                <i class="fa-solid fa-box"></i>
                                            </c:otherwise>
                                        </c:choose>
                                        <c:if test="${product.category != null}">
                                            <span class="product-card-category">${fn:escapeXml(product.category.categoryName)}</span>
                                        </c:if>
                                    </div>
                                    <div class="product-card-body">
                                        <span class="product-card-code">${fn:escapeXml(product.productCode)}</span>
                                        <a href="${pageContext.request.contextPath}/product?action=view&id=${product.productId}" class="product-card-name">${fn:escapeXml(product.productName)}</a>
                                        <div class="product-card-footer">
                                            <span class="product-card-updated">
                                                <c:if test="${product.updatedAt != null}"><fmt:formatDate value="${product.updatedAt}" pattern="dd/MM/yyyy"/></c:if>
                                            </span>
                                            <div class="action-icons">
                                                <button class="act-view" title="Xem chi tiết" onclick="location.href='${pageContext.request.contextPath}/product?action=view&id=${product.productId}'"><i class="fa-regular fa-eye"></i></button>
                                                <button class="act-edit" title="Sửa" onclick="location.href='${pageContext.request.contextPath}/product?action=edit&id=${product.productId}'"><i class="fa-solid fa-pen"></i></button>
                                                <button class="act-delete" title="Xóa" onclick="openDeleteModal(${product.productId}, '${fn:escapeXml(product.productName)}')"><i class="fa-solid fa-trash"></i></button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>

                        <!-- ===== Phân trang (BR-12) ===== -->
                        <div class="pagination-bar">
                            <span class="pagination-info">Hiển thị ${fn:length(productList)} trong tổng số ${totalCount} sản phẩm</span>
                            <nav>
                                <ul class="pagination pagination-sm mb-0">
                                    <li class="page-item ${currentPage <= 1 ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/product?action=list&page=${currentPage - 1}&keyword=${fn:escapeXml(keyword)}&categoryId=${categoryFilter}">Trước</a></li>
                                    <c:forEach begin="1" end="${totalPages}" var="p">
                                        <li class="page-item ${p == currentPage ? 'active' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/product?action=list&page=${p}&keyword=${fn:escapeXml(keyword)}&categoryId=${categoryFilter}">${p}</a></li>
                                    </c:forEach>
                                    <li class="page-item ${currentPage >= totalPages ? 'disabled' : ''}"><a class="page-link" href="${pageContext.request.contextPath}/product?action=list&page=${currentPage + 1}&keyword=${fn:escapeXml(keyword)}&categoryId=${categoryFilter}">Sau</a></li>
                                </ul>
                            </nav>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>

        </div>
    </div>

    <!-- ===== Modal xác nhận xóa ===== -->
    <div class="modal fade" id="deleteModal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content">
                <div class="modal-header">
                    <div class="modal-icon-warn"><i class="fa-solid fa-triangle-exclamation"></i></div>
                </div>
                <div class="modal-body">
                    <h5 class="mb-2" style="font-weight:700; color:#111827;">Xác nhận xóa sản phẩm</h5>
                    Bạn có chắc chắn muốn xóa sản phẩm <strong id="deleteProductName"></strong>? Hành động này không thể hoàn tác.
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn-modal-cancel" data-bs-dismiss="modal">Hủy</button>
                    <button type="button" class="btn-modal-danger" id="confirmDeleteBtn">Xóa sản phẩm</button>
                </div>
            </div>
        </div>
    </div>

    <!-- Form ẩn để gửi yêu cầu xoá qua POST (không đổi state bằng GET) -->
    <form id="deleteForm" method="POST" action="${pageContext.request.contextPath}/product" style="display:none">
        <input type="hidden" name="csrfToken" value="${csrfToken}">
        <input type="hidden" name="action" value="delete">
        <input type="hidden" name="id" id="deleteFormId">
    </form>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script>
        var deleteModal = new bootstrap.Modal(document.getElementById('deleteModal'));
        var productIdToDelete = null;

        function openDeleteModal(productId, productName) {
            productIdToDelete = productId;
            document.getElementById('deleteProductName').textContent = productName;
            deleteModal.show();
        }

        document.getElementById('confirmDeleteBtn').addEventListener('click', function () {
            if (productIdToDelete) {
                document.getElementById('deleteFormId').value = productIdToDelete;
                document.getElementById('deleteForm').submit();
            }
            deleteModal.hide();
        });
    </script>

    <script src="${pageContext.request.contextPath}/js/appshell.js"></script>
</body>
</html>
