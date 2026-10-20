package poscs.controller;

import java.sql.Date;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import poscs.common.AccessControl;
import poscs.common.MoneyVnd;
import poscs.common.TextRules;
import poscs.dao.ContractDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.dao.ProductDAO;
import poscs.model.Contract;
import poscs.model.ContractDocument;
import poscs.model.ContractHandover;
import poscs.model.ContractHistory;
import poscs.model.ContractLink;
import poscs.model.ContractPayment;
import poscs.model.User;
import poscs.service.ContractService;
import poscs.service.PermissionService;

/**
 * Lớp tiện ích hỗ trợ cho các controller phân hệ Hợp đồng (Task 4.12+).
 */
public final class ContractControllerHelper {

    private ContractControllerHelper() {}

    public static final String LIST_VIEW = "/jsp/sale/listcontract.jsp";
    public static final String DETAIL_VIEW = "/jsp/sale/viewcontractdetail.jsp";
    public static final String CREATE_VIEW = "/jsp/sale/addnewcontract.jsp";
    public static final String UPDATE_VIEW = "/jsp/sale/updatecontract.jsp";
    public static final String HANDOVER_VIEW = "/jsp/sale/handoverqueue.jsp";

    public static final String ADJUST_INCREASE = "increase";
    public static final String ADJUST_DECREASE = "decrease";
    public static final String ADJUST_NONE = "none";

    public static boolean isBlank(String v) { return v == null || v.trim().isEmpty(); }
    public static String emptyToNull(String v) { return (v == null || v.trim().isEmpty()) ? null : v.trim(); }

    public static Integer parseIntOrNull(String v) {
        if (isBlank(v)) return null;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ex) { return null; }
    }
    public static int parseIntOrDefault(String v, int d) { Integer p = parseIntOrNull(v); return p != null ? p : d; }

    public static Integer parseQuantityOrNull(String v) {
        if (isBlank(v)) return null;
        String t = v.trim();
        return t.matches("\\d+(\\.\\d{3})*") ? parseIntOrNull(t.replace(".", "")) : null;
    }

    public static Date parseDateOrNull(String v) {
        if (isBlank(v)) return null;
        try { return Date.valueOf(v.trim()); } catch (IllegalArgumentException ex) { return null; }
    }

    public static String notFoundUrl(HttpServletRequest req) { return req.getContextPath() + "/contract?error=notfound"; }

    public static java.math.BigDecimal parseContractValue(HttpServletRequest req) {
        java.math.BigDecimal amount = MoneyVnd.parseOrNull(req.getParameter("contractValue"));
        String adj = req.getParameter("valueAdjustment");
        if (adj == null) return amount;
        if (ADJUST_NONE.equals(adj) || amount == null) return null;
        return ADJUST_DECREASE.equals(adj) ? amount.negate() : amount;
    }

    public static Contract buildContractFromRequest(HttpServletRequest req, Contract c) {
        c.setContractCode(emptyToNull(req.getParameter("contractCode")));
        c.setTitle(emptyToNull(req.getParameter("title")));
        c.setContractType(emptyToNull(req.getParameter("contractType")));
        c.setEffectiveDate(parseDateOrNull(req.getParameter("effectiveDate")));
        c.setEndDate(parseDateOrNull(req.getParameter("endDate")));
        Integer entId = parseIntOrNull(req.getParameter("enterpriseId"));
        if (entId != null) c.setEnterpriseId(entId);
        Integer ownerId = parseIntOrNull(req.getParameter("ownerId"));
        if (ownerId != null) c.setOwnerId(ownerId);
        c.setSignerName(emptyToNull(req.getParameter("signerName")));
        c.setSignerPosition(emptyToNull(req.getParameter("signerPosition")));
        c.setCounterpartySignerName(emptyToNull(req.getParameter("counterpartySignerName")));
        c.setCounterpartySignerPosition(emptyToNull(req.getParameter("counterpartySignerPosition")));
        c.setAuthorizationRef(emptyToNull(req.getParameter("authorizationRef")));
        c.setSigningPlace(emptyToNull(req.getParameter("signingPlace")));
        c.setContractValue(parseContractValue(req));
        return c;
    }

    public static Contract extractCreateContract(HttpServletRequest req) {
        Contract c = buildContractFromRequest(req, new Contract());
        c.setDirection(ContractService.directionFromKind(req.getParameter("kind")));
        return c;
    }

    public static Contract extractUpdateContract(HttpServletRequest req, Contract existing) {
        Contract c = buildContractFromRequest(req, new Contract());
        c.setContractId(existing.getContractId());
        c.setDirection(existing.getDirection());
        c.setSigningDate(existing.getSigningDate());
        return c;
    }

    public static Contract extractAmendmentContract(HttpServletRequest req, Contract parent) {
        Contract c = buildContractFromRequest(req, new Contract());
        c.setParentContractId(parent.getContractId());
        c.setEnterpriseId(parent.getEnterpriseId());
        c.setDirection(parent.getDirection());
        return c;
    }

    public static void populateCreateForm(HttpServletRequest req, CustomerDAO custDAO, EmployeeDAO empDAO) {
        String dir = ContractService.directionFromKind(req.getParameter("kind"));
        setDropdownAttributes(req, custDAO, empDAO, null, dir, null);
        req.setAttribute("direction", dir);
        req.setAttribute("kind", ContractService.DIRECTION_BUY.equals(dir) ? ContractService.KIND_BUY : ContractService.KIND_SELL);
        req.setAttribute("contractTypeOptions", ContractService.contractTypesFor(dir));
    }

    public static void populateAmendmentForm(HttpServletRequest req, Contract parent, CustomerDAO custDAO, EmployeeDAO empDAO) {
        req.setAttribute("parentContract", parent);
        setDropdownAttributes(req, custDAO, empDAO, parent.getOwnerId(), parent.getDirection(), parent.getEnterpriseId());
        req.setAttribute("direction", parent.getDirection());
        req.setAttribute("kind", ContractService.DIRECTION_BUY.equals(parent.getDirection()) ? ContractService.KIND_BUY : ContractService.KIND_SELL);
        req.setAttribute("contractTypeOptions", ContractService.contractTypesFor(parent.getDirection()));
    }

    public static void setDropdownAttributes(HttpServletRequest req, CustomerDAO custDAO, EmployeeDAO empDAO,
                                           Integer keepUserId, String dir, Integer keepCustId) {
        req.setAttribute("customerList", custDAO.findAllByRole(ContractService.counterpartyRoleFor(dir), keepCustId));
        req.setAttribute("userList", empDAO.findActiveByRole(PermissionService.ROLE_SALES, keepUserId));
    }

    public static void bindEditFormAttributes(HttpServletRequest req, Contract c, User u, ContractDAO cd,
                                              CustomerDAO custDAO, EmployeeDAO empDAO, ProductDAO pd,
                                              PermissionService ps, boolean canSign) {
        req.setAttribute("contract", c);
        setDropdownAttributes(req, custDAO, empDAO, c.getOwnerId(), c.getDirection(), c.getEnterpriseId());
        req.setAttribute("direction", c.getDirection());
        req.setAttribute("kind", ContractService.DIRECTION_BUY.equals(c.getDirection()) ? ContractService.KIND_BUY : ContractService.KIND_SELL);
        req.setAttribute("contractTypeOptions", ContractService.contractTypesFor(c.getDirection()));
        putContractWorkspace(req, c, cd, empDAO);
        req.setAttribute("productOptions", pd.findAll(1, Integer.MAX_VALUE, null, null));
        req.setAttribute("canEditProducts", c.isDraft());
        req.setAttribute("canEditPayments", !c.isFrozen());
        req.setAttribute("canRecordPayment", true);
        req.setAttribute("canSign", c.isDraft() && canSign);
        req.setAttribute("canClose", ContractService.PROGRESS_SIGNED.equals(c.getProgressStatus()));
        req.setAttribute("canVoid", ps.canVoidContract(u, c.isDraft()) && c.getAmendmentCount() == 0);
        req.setAttribute("canEditTerms", !c.isTermsLocked());
        req.setAttribute("canCorrect", ps.isAdmin(u) && c.isSigned());
        req.setAttribute("canAddAmendment", c.isSigned() && !c.isAmendment());
        if (!c.isAmendment()) {
            String otherDir = ContractService.DIRECTION_BUY.equals(c.getDirection()) ? ContractService.DIRECTION_SELL : ContractService.DIRECTION_BUY;
            req.setAttribute("linkCandidates", cd.findAll(1, Integer.MAX_VALUE, null, null, null, null, false, null, otherDir, null, true));
        }
    }

    public static void putContractWorkspace(HttpServletRequest req, Contract c, ContractDAO cd, EmployeeDAO empDAO) {
        int id = c.getContractId();
        bindHistoryAndProducts(req, c, cd, id);
        bindPaymentsAndCluster(req, c, cd, id);
        bindHandovers(req, cd, empDAO, id);
        bindLinksAndDocuments(req, c, cd, id);
    }

    private static void bindHistoryAndProducts(HttpServletRequest req, Contract c, ContractDAO cd, int id) {
        List<ContractHistory> h = cd.findHistoryByContractId(id);
        req.setAttribute("contractHistory", h);
        req.setAttribute("createdEvent", milestoneOf(h, ContractHistory.EVENT_CREATED));
        req.setAttribute("signedEvent", milestoneOf(h, ContractHistory.EVENT_SIGNED));
        ContractHistory closed = milestoneOf(h, ContractHistory.EVENT_LIQUIDATED);
        req.setAttribute("closedEvent", closed != null ? closed : milestoneOf(h, ContractHistory.EVENT_TERMINATED));
        req.setAttribute("overdueUnclosed", ContractDAO.STATUS_EXPIRED.equals(c.getStatus()) && ContractDAO.PROGRESS_SIGNED.equals(c.getProgressStatus()));
        req.setAttribute("contractProducts", cd.findProductsByContractId(id));
        req.setAttribute("amendments", cd.findAmendmentsByParentId(id));
    }

    private static void bindPaymentsAndCluster(HttpServletRequest req, Contract c, ContractDAO cd, int id) {
        List<ContractPayment> p = cd.findPaymentsByContractId(id);
        req.setAttribute("contractPayments", p);
        java.math.BigDecimal sched = java.math.BigDecimal.ZERO, coll = java.math.BigDecimal.ZERO;
        for (ContractPayment item : p) {
            sched = sched.add(item.getInvoiceAmount());
            if (item.getPaidDate() != null) coll = coll.add(item.getInvoiceAmount());
        }
        req.setAttribute("paymentScheduled", sched);
        req.setAttribute("paymentCollected", coll);
        req.setAttribute("paymentOutstanding", sched.subtract(coll));

        Contract root = c.isAmendment() ? cd.findById(c.getParentContractId()) : c;
        java.math.BigDecimal cVal = root == null ? null : root.getCurrentValue();
        java.math.BigDecimal cSched = root == null ? sched : cd.sumScheduledPaymentsForCluster(root.getContractId());
        if (cSched == null) cSched = java.math.BigDecimal.ZERO;
        req.setAttribute("rootContract", root == c ? null : root);
        req.setAttribute("clusterValue", cVal);
        req.setAttribute("clusterScheduled", cSched);
        req.setAttribute("clusterHasAmendments", c.isAmendment() || c.getAmendmentCount() > 0);
        req.setAttribute("paymentMismatch", cVal != null && cVal.compareTo(cSched) != 0);
    }

    private static void bindHandovers(HttpServletRequest req, ContractDAO cd, EmployeeDAO empDAO, int id) {
        List<ContractHandover> ho = cd.findHandoversOf(id);
        req.setAttribute("handovers", ho);
        boolean pending = false;
        for (ContractHandover item : ho) {
            if (item.isPending()) {
                pending = true;
                req.setAttribute("canCompleteHandover_" + item.getHandoverId(), AccessControl.canCompleteHandover(req, item.getDepartmentId()));
            }
        }
        req.setAttribute("hasPendingHandover", pending);
        req.setAttribute("departmentList", empDAO.findAllDepartments());
    }

    private static void bindLinksAndDocuments(HttpServletRequest req, Contract c, ContractDAO cd, int id) {
        List<ContractLink> links = cd.findLinksOf(id);
        req.setAttribute("contractLinks", links);
        boolean isSell = !ContractService.DIRECTION_BUY.equals(c.getDirection());
        req.setAttribute("linkIsSellSide", isSell);
        req.setAttribute("canLinkContracts", !c.isAmendment());
        if (isSell) {
            java.math.BigDecimal inVal = cd.sumLinkedBuyValue(id);
            req.setAttribute("linkedInputValue", inVal != null ? inVal : java.math.BigDecimal.ZERO);
            req.setAttribute("linkedMargin", c.getCurrentValue() == null ? null : c.getCurrentValue().subtract(inVal != null ? inVal : java.math.BigDecimal.ZERO));
        }

        List<ContractDocument> docs = cd.findDocumentsOf(id);
        req.setAttribute("contractDocuments", docs);
        req.setAttribute("documentTypes", ContractDocument.TYPES);
        for (ContractDocument d : docs) {
            if (ContractDocument.TYPE_SIGNED_CONTRACT.equals(d.getDocType()) && TextRules.isSafeHttpUrl(d.getFileUrl())) {
                req.setAttribute("primaryDocumentUrl", d.getFileUrl());
                break;
            }
        }
    }

    public static ContractHistory milestoneOf(List<ContractHistory> history, String eventType) {
        if (history == null) return null;
        for (ContractHistory h : history) {
            if (eventType.equals(h.getEventType())) return h;
        }
        return null;
    }
}
