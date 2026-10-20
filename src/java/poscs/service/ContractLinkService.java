package poscs.service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import poscs.dao.ContractDAO;
import poscs.dao.ContractLinkDAO;
import poscs.model.Contract;
import poscs.model.ContractLink;
import poscs.model.User;

/**
 * Service xử lý nghiệp vụ Liên kết hợp đồng Bán ↔ Mua và Gỡ liên kết (Task 4.18 - [UC-47]).
 * Ràng buộc nghiệp vụ:
 * - Phân quyền RBAC qua {@link PermissionService}: Admin và Sales quản lý vùng (MSG-022 nếu vi phạm).
 * - Đúng chiều Bán - Mua, tự động chuẩn hóa chiều dù thao tác từ phía HĐ Bán hay HĐ Mua.
 * - Cả hai đều phải là hợp đồng gốc (không phải phụ lục).
 * - Không cho phép liên kết chính nó.
 * - Chặn trùng lặp cặp hợp đồng đã nối (vi phạm UNIQUE uq_contract_links_pair).
 * - Phòng chống IDOR khi gỡ liên kết: linkId phải thực sự gắn với contractId.
 */
public class ContractLinkService extends BaseService {

    public static final String ERR_FORBIDDEN = PermissionService.MSG_022;
    public static final String ERR_NOT_FOUND = "notfound";
    public static final String ERR_INVALID = "link_invalid";
    public static final String ERR_DUPLICATE = "link_duplicate";
    public static final String ERR_UNLINK_FAILED = "unlink_failed";

    private final ContractDAO contractDAO;
    private final ContractLinkDAO linkDAO;
    private final PermissionService permissionService;

    public ContractLinkService() {
        this(new ContractDAO(), new ContractLinkDAO(), new PermissionService());
    }

    public ContractLinkService(ContractDAO contractDAO, ContractLinkDAO linkDAO,
                               PermissionService permissionService) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
        this.linkDAO = linkDAO != null ? linkDAO : (this.contractDAO.getLinkDAO() != null ? this.contractDAO.getLinkDAO() : new ContractLinkDAO());
        this.permissionService = permissionService != null ? permissionService : new PermissionService();
    }

    /**
     * Nối một hợp đồng Bán với một hợp đồng Mua (UC-47).
     * Tự động xác định chiều Bán - Mua dựa vào direction của 2 hợp đồng.
     */
    public ServiceResult<Void> linkContracts(int contractId, int otherContractId, String note, User actor) {
        if (!permissionService.canLinkContracts(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }
        if (otherContractId <= 0 || contractId == otherContractId) {
            return ServiceResult.fail(ERR_INVALID);
        }

        Contract current = contractDAO.findById(contractId);
        if (current == null || current.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }

        Contract other = contractDAO.findById(otherContractId);
        if (other == null || other.isDeleted()) {
            return ServiceResult.fail(ERR_INVALID);
        }

        ContractPair pair = resolveAndValidatePair(current, other);
        if (pair == null) {
            return ServiceResult.fail(ERR_INVALID);
        }

        int actorId = actor != null ? actor.getUserId() : 0;
        int result = contractDAO.linkContracts(pair.sell.getContractId(), pair.buy.getContractId(), note, actorId);
        if (result == ContractLinkDAO.LINK_DUPLICATE) {
            return ServiceResult.fail(ERR_DUPLICATE);
        }
        // ContractLinkDAO.LINK_INVALID == LINK_FAILED == -1; giao diện updatecontract.jsp
        // hiện chỉ hỗ trợ hiển thị 2 mã lỗi cho thao tác nối là link_duplicate và link_invalid.
        if (result != ContractLinkDAO.LINK_OK) {
            return ServiceResult.fail(ERR_INVALID);
        }
        return ServiceResult.ok(null, "Nối hợp đồng thành công.");
    }

    private ContractPair resolveAndValidatePair(Contract current, Contract other) {
        if (current.isAmendment() || other.isAmendment()) {
            return null;
        }
        boolean currentIsSell = !ContractLinkDAO.DIRECTION_BUY.equals(current.getDirection());
        Contract sell = currentIsSell ? current : other;
        Contract buy = currentIsSell ? other : current;
        if (!ContractLinkDAO.DIRECTION_SELL.equals(sell.getDirection())
                || !ContractLinkDAO.DIRECTION_BUY.equals(buy.getDirection())) {
            return null;
        }
        return new ContractPair(sell, buy);
    }

    private static class ContractPair {
        final Contract sell;
        final Contract buy;

        ContractPair(Contract sell, Contract buy) {
            this.sell = sell;
            this.buy = buy;
        }
    }

    /**
     * Gỡ một liên kết giữa hai hợp đồng (UC-47).
     * Chống IDOR: kiểm tra linkId có thực sự thuộc về contractId không trước khi gỡ.
     */
    public ServiceResult<Void> unlinkContracts(int contractId, int linkId, User actor) {
        if (!permissionService.canLinkContracts(actor)) {
            return ServiceResult.fail(ERR_FORBIDDEN);
        }
        if (contractId <= 0 || linkId <= 0) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }

        Contract current = contractDAO.findById(contractId);
        if (current == null || current.isDeleted()) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }

        if (!isLinkInContract(contractId, linkId)) {
            return ServiceResult.fail(ERR_NOT_FOUND);
        }

        int actorId = actor != null ? actor.getUserId() : 0;
        boolean ok = contractDAO.unlinkContracts(linkId, actorId);
        if (!ok) {
            return ServiceResult.fail(ERR_UNLINK_FAILED);
        }
        return ServiceResult.ok(null, "Gỡ liên kết thành công.");
    }

    /**
     * Lấy danh sách liên kết của hợp đồng (UC-47).
     */
    public List<ContractLink> findLinksOf(int contractId, User viewer) {
        if (!permissionService.canViewContracts(viewer) || contractId <= 0) {
            return Collections.emptyList();
        }
        return contractDAO.findLinksOf(contractId);
    }

    /**
     * Tính tổng giá trị các đơn mua đã nối vào hợp đồng bán (UC-47).
     */
    public BigDecimal sumLinkedBuyValue(int sellContractId, User viewer) {
        if (!permissionService.canViewContracts(viewer) || sellContractId <= 0) {
            return BigDecimal.ZERO;
        }
        return contractDAO.sumLinkedBuyValue(sellContractId);
    }

    private boolean isLinkInContract(int contractId, int linkId) {
        List<ContractLink> links = contractDAO.findLinksOf(contractId);
        if (links == null) {
            return false;
        }
        for (ContractLink l : links) {
            if (l != null && l.getLinkId() == linkId) {
                return true;
            }
        }
        return false;
    }
}
