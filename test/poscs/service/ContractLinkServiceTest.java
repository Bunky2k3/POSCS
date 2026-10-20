package poscs.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import poscs.dao.ContractDAO;
import poscs.dao.ContractLinkDAO;
import poscs.model.Contract;
import poscs.model.ContractLink;
import poscs.model.Role;
import poscs.model.User;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test toàn diện cho {@link ContractLinkService} (Task 4.18 - [UC-47]).
 * Kiểm thử:
 * - linkContracts: phân quyền, kiểm tra ID, tồn tại, xóa mềm, phụ lục, chiều Bán/Mua,
 *   tự động xếp thứ tự chiều, trùng lặp, lỗi DAO, thành công.
 * - unlinkContracts: phân quyền, kiểm tra ID, tồn tại, xóa mềm, chống IDOR tampering,
 *   lỗi DAO, thành công.
 * - findLinksOf và sumLinkedBuyValue: phân quyền, tham số biên, ủy quyền DAO.
 * - Constructors và xử lý null an toàn.
 */
public class ContractLinkServiceTest {

    private ContractDAO contractDAO;
    private ContractLinkDAO linkDAO;
    private PermissionService permissionService;
    private ContractLinkService service;

    private User adminUser;
    private User salesRegionalManager;
    private User salesSubordinate;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        linkDAO = mock(ContractLinkDAO.class);
        permissionService = mock(PermissionService.class);

        service = new ContractLinkService(contractDAO, linkDAO, permissionService);

        adminUser = createUser(1, PermissionService.ROLE_ADMIN, null);
        salesRegionalManager = createUser(2, PermissionService.ROLE_SALES, null);
        salesSubordinate = createUser(3, PermissionService.ROLE_SALES, 2);

        when(permissionService.canLinkContracts(adminUser)).thenReturn(true);
        when(permissionService.canLinkContracts(salesRegionalManager)).thenReturn(true);
        when(permissionService.canLinkContracts(salesSubordinate)).thenReturn(false);
        when(permissionService.canViewContracts(any())).thenReturn(true);
    }

    private User createUser(int userId, String roleName, Integer managerId) {
        User u = new User();
        u.setUserId(userId);
        u.setManagerId(managerId);
        u.setDeleted(false);
        Role r = new Role();
        r.setRoleName(roleName);
        u.setRole(r);
        return u;
    }

    private Contract createContract(int id, String code, String direction, Integer parentId, boolean deleted) {
        Contract c = new Contract();
        c.setContractId(id);
        c.setContractCode(code);
        c.setDirection(direction);
        c.setParentContractId(parentId);
        c.setDeleted(deleted);
        return c;
    }

    // ==================================================================
    // 1. linkContracts tests (UC-47)
    // ==================================================================

    @Test
    public void linkContracts_unauthorizedUser_failsWithForbidden() {
        ServiceResult<Void> res = service.linkContracts(1, 2, "Ghi chú", salesSubordinate);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_FORBIDDEN, res.getMessage());
        verify(contractDAO, never()).linkContracts(anyInt(), anyInt(), anyString(), anyInt());
    }

    @Test
    public void linkContracts_invalidContractId_failsWithNotFound() {
        ServiceResult<Void> res1 = service.linkContracts(0, 2, "Ghi chú", adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res1.getMessage());

        ServiceResult<Void> res2 = service.linkContracts(-5, 2, "Ghi chú", adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res2.getMessage());
    }

    @Test
    public void linkContracts_invalidOtherId_failsWithInvalid() {
        ServiceResult<Void> res1 = service.linkContracts(1, 0, "Ghi chú", adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res1.getMessage());

        ServiceResult<Void> res2 = service.linkContracts(1, -9, "Ghi chú", adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res2.getMessage());
    }

    @Test
    public void linkContracts_sameContractId_failsWithInvalid() {
        ServiceResult<Void> res = service.linkContracts(5, 5, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res.getMessage());
    }

    @Test
    public void linkContracts_currentContractNotFound_failsWithNotFound() {
        when(contractDAO.findById(10)).thenReturn(null);
        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res.getMessage());
    }

    @Test
    public void linkContracts_currentContractDeleted_failsWithNotFound() {
        Contract current = createContract(10, "HD-10", ContractLinkDAO.DIRECTION_SELL, null, true);
        when(contractDAO.findById(10)).thenReturn(current);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res.getMessage());
    }

    @Test
    public void linkContracts_otherContractNotFound_failsWithInvalid() {
        Contract current = createContract(10, "HD-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        when(contractDAO.findById(10)).thenReturn(current);
        when(contractDAO.findById(20)).thenReturn(null);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res.getMessage());
    }

    @Test
    public void linkContracts_otherContractDeleted_failsWithInvalid() {
        Contract current = createContract(10, "HD-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract other = createContract(20, "HD-20", ContractLinkDAO.DIRECTION_BUY, null, true);
        when(contractDAO.findById(10)).thenReturn(current);
        when(contractDAO.findById(20)).thenReturn(other);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res.getMessage());
    }

    @Test
    public void linkContracts_currentIsAmendment_failsWithInvalid() {
        Contract current = createContract(10, "PL-10", ContractLinkDAO.DIRECTION_SELL, 5, false);
        Contract other = createContract(20, "HD-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        when(contractDAO.findById(10)).thenReturn(current);
        when(contractDAO.findById(20)).thenReturn(other);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res.getMessage());
    }

    @Test
    public void linkContracts_otherIsAmendment_failsWithInvalid() {
        Contract current = createContract(10, "HD-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract other = createContract(20, "PL-20", ContractLinkDAO.DIRECTION_BUY, 8, false);
        when(contractDAO.findById(10)).thenReturn(current);
        when(contractDAO.findById(20)).thenReturn(other);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res.getMessage());
    }

    @Test
    public void linkContracts_bothSellContracts_failsWithInvalid() {
        Contract current = createContract(10, "HD-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract other = createContract(20, "HD-20", ContractLinkDAO.DIRECTION_SELL, null, false);
        when(contractDAO.findById(10)).thenReturn(current);
        when(contractDAO.findById(20)).thenReturn(other);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res.getMessage());
    }

    @Test
    public void linkContracts_bothBuyContracts_failsWithInvalid() {
        Contract current = createContract(10, "HD-10", ContractLinkDAO.DIRECTION_BUY, null, false);
        Contract other = createContract(20, "HD-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        when(contractDAO.findById(10)).thenReturn(current);
        when(contractDAO.findById(20)).thenReturn(other);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res.getMessage());
    }

    @Test
    public void linkContracts_sellToBuy_ordersCorrectlyAndSucceeds() {
        Contract sell = createContract(10, "HD-BAN-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract buy = createContract(20, "HD-MUA-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        when(contractDAO.findById(10)).thenReturn(sell);
        when(contractDAO.findById(20)).thenReturn(buy);
        when(contractDAO.linkContracts(10, 20, "Ghi chú 1", adminUser.getUserId()))
                .thenReturn(ContractLinkDAO.LINK_OK);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú 1", adminUser);
        assertTrue(res.isSuccess());
        verify(contractDAO).linkContracts(10, 20, "Ghi chú 1", adminUser.getUserId());
    }

    @Test
    public void linkContracts_buyToSell_ordersCorrectlyAndSucceeds() {
        Contract buy = createContract(20, "HD-MUA-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        Contract sell = createContract(10, "HD-BAN-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        when(contractDAO.findById(20)).thenReturn(buy);
        when(contractDAO.findById(10)).thenReturn(sell);
        when(contractDAO.linkContracts(10, 20, "Ghi chú 2", salesRegionalManager.getUserId()))
                .thenReturn(ContractLinkDAO.LINK_OK);

        // Đứng từ phía MUA (20) nối với BÁN (10)
        ServiceResult<Void> res = service.linkContracts(20, 10, "Ghi chú 2", salesRegionalManager);
        assertTrue(res.isSuccess());
        // Verify vẫn xếp BÁN (10) trước MUA (20)
        verify(contractDAO).linkContracts(10, 20, "Ghi chú 2", salesRegionalManager.getUserId());
    }

    @Test
    public void linkContracts_duplicatePair_failsWithDuplicate() {
        Contract sell = createContract(10, "HD-BAN-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract buy = createContract(20, "HD-MUA-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        when(contractDAO.findById(10)).thenReturn(sell);
        when(contractDAO.findById(20)).thenReturn(buy);
        when(contractDAO.linkContracts(10, 20, "Ghi chú", adminUser.getUserId()))
                .thenReturn(ContractLinkDAO.LINK_DUPLICATE);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_DUPLICATE, res.getMessage());
    }

    @Test
    public void linkContracts_daoFailure_failsWithInvalid() {
        Contract sell = createContract(10, "HD-BAN-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract buy = createContract(20, "HD-MUA-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        when(contractDAO.findById(10)).thenReturn(sell);
        when(contractDAO.findById(20)).thenReturn(buy);
        when(contractDAO.linkContracts(10, 20, "Ghi chú", adminUser.getUserId()))
                .thenReturn(ContractLinkDAO.LINK_FAILED);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res.getMessage());
    }

    // ==================================================================
    // 2. unlinkContracts tests (UC-47)
    // ==================================================================

    @Test
    public void unlinkContracts_unauthorizedUser_failsWithForbidden() {
        ServiceResult<Void> res = service.unlinkContracts(1, 100, salesSubordinate);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_FORBIDDEN, res.getMessage());
        verify(contractDAO, never()).unlinkContracts(anyInt(), anyInt());
    }

    @Test
    public void unlinkContracts_invalidContractId_failsWithNotFound() {
        ServiceResult<Void> res1 = service.unlinkContracts(0, 100, adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res1.getMessage());

        ServiceResult<Void> res2 = service.unlinkContracts(-1, 100, adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res2.getMessage());
    }

    @Test
    public void unlinkContracts_invalidLinkId_failsWithNotFound() {
        ServiceResult<Void> res1 = service.unlinkContracts(10, 0, adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res1.getMessage());

        ServiceResult<Void> res2 = service.unlinkContracts(10, -5, adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res2.getMessage());
    }

    @Test
    public void unlinkContracts_contractNotFound_failsWithNotFound() {
        when(contractDAO.findById(10)).thenReturn(null);
        ServiceResult<Void> res = service.unlinkContracts(10, 100, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res.getMessage());
    }

    @Test
    public void unlinkContracts_contractDeleted_failsWithNotFound() {
        Contract c = createContract(10, "HD-10", "Bán", null, true);
        when(contractDAO.findById(10)).thenReturn(c);

        ServiceResult<Void> res = service.unlinkContracts(10, 100, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res.getMessage());
    }

    @Test
    public void unlinkContracts_linkNotInContract_idorPrevention_failsWithNotFound() {
        Contract c = createContract(10, "HD-10", "Bán", null, false);
        when(contractDAO.findById(10)).thenReturn(c);

        // Hợp đồng 10 chỉ có linkId 101, không có linkId 999
        ContractLink link1 = new ContractLink();
        link1.setLinkId(101);
        when(contractDAO.findLinksOf(10)).thenReturn(List.of(link1));

        ServiceResult<Void> res = service.unlinkContracts(10, 999, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res.getMessage());
        verify(contractDAO, never()).unlinkContracts(eq(999), anyInt());
    }

    @Test
    public void unlinkContracts_daoFailure_failsWithUnlinkFailed() {
        Contract c = createContract(10, "HD-10", "Bán", null, false);
        when(contractDAO.findById(10)).thenReturn(c);

        ContractLink link1 = new ContractLink();
        link1.setLinkId(101);
        when(contractDAO.findLinksOf(10)).thenReturn(List.of(link1));
        when(contractDAO.unlinkContracts(101, adminUser.getUserId())).thenReturn(false);

        ServiceResult<Void> res = service.unlinkContracts(10, 101, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_UNLINK_FAILED, res.getMessage());
    }

    @Test
    public void unlinkContracts_success() {
        Contract c = createContract(10, "HD-10", "Bán", null, false);
        when(contractDAO.findById(10)).thenReturn(c);

        ContractLink link1 = new ContractLink();
        link1.setLinkId(101);
        when(contractDAO.findLinksOf(10)).thenReturn(List.of(link1));
        when(contractDAO.unlinkContracts(101, salesRegionalManager.getUserId())).thenReturn(true);

        ServiceResult<Void> res = service.unlinkContracts(10, 101, salesRegionalManager);
        assertTrue(res.isSuccess());
        verify(contractDAO).unlinkContracts(101, salesRegionalManager.getUserId());
    }

    // ==================================================================
    // 3. findLinksOf & sumLinkedBuyValue tests (UC-47)
    // ==================================================================

    @Test
    public void findLinksOf_unauthorizedViewer_returnsEmpty() {
        when(permissionService.canViewContracts(salesSubordinate)).thenReturn(false);
        List<ContractLink> list = service.findLinksOf(10, salesSubordinate);
        assertTrue(list.isEmpty());
        verify(contractDAO, never()).findLinksOf(anyInt());
    }

    @Test
    public void findLinksOf_invalidContractId_returnsEmpty() {
        List<ContractLink> list1 = service.findLinksOf(0, adminUser);
        assertTrue(list1.isEmpty());
        List<ContractLink> list2 = service.findLinksOf(-1, adminUser);
        assertTrue(list2.isEmpty());
    }

    @Test
    public void findLinksOf_authorizedViewer_delegatesToDao() {
        ContractLink link = new ContractLink();
        link.setLinkId(55);
        when(contractDAO.findLinksOf(10)).thenReturn(List.of(link));

        List<ContractLink> result = service.findLinksOf(10, adminUser);
        assertEquals(1, result.size());
        assertEquals(55, result.get(0).getLinkId());
    }

    @Test
    public void sumLinkedBuyValue_unauthorizedViewer_returnsZero() {
        when(permissionService.canViewContracts(salesSubordinate)).thenReturn(false);
        BigDecimal val = service.sumLinkedBuyValue(10, salesSubordinate);
        assertEquals(BigDecimal.ZERO, val);
        verify(contractDAO, never()).sumLinkedBuyValue(anyInt());
    }

    @Test
    public void sumLinkedBuyValue_invalidContractId_returnsZero() {
        assertEquals(BigDecimal.ZERO, service.sumLinkedBuyValue(0, adminUser));
        assertEquals(BigDecimal.ZERO, service.sumLinkedBuyValue(-1, adminUser));
    }

    @Test
    public void sumLinkedBuyValue_authorizedViewer_delegatesToDao() {
        BigDecimal expected = new BigDecimal("500000000");
        when(contractDAO.sumLinkedBuyValue(10)).thenReturn(expected);

        BigDecimal actual = service.sumLinkedBuyValue(10, adminUser);
        assertEquals(expected, actual);
    }

    // ==================================================================
    // 4. Constructors & Null Safety
    // ==================================================================

    @Test
    public void linkContracts_nullActor_failsWithForbidden() {
        when(permissionService.canLinkContracts(null)).thenReturn(false);
        ServiceResult<Void> res = service.linkContracts(10, 20, "Ghi chú", null);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_FORBIDDEN, res.getMessage());
        verify(contractDAO, never()).linkContracts(anyInt(), anyInt(), anyString(), anyInt());
    }

    @Test
    public void unlinkContracts_nullActor_failsWithForbidden() {
        when(permissionService.canLinkContracts(null)).thenReturn(false);
        ServiceResult<Void> res = service.unlinkContracts(10, 100, null);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_FORBIDDEN, res.getMessage());
        verify(contractDAO, never()).unlinkContracts(anyInt(), anyInt());
    }

    @Test
    public void linkContracts_nullActorAllowedByPerm_passesZeroActorId() {
        when(permissionService.canLinkContracts(null)).thenReturn(true);
        Contract sell = createContract(10, "HD-BAN-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract buy = createContract(20, "HD-MUA-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        when(contractDAO.findById(10)).thenReturn(sell);
        when(contractDAO.findById(20)).thenReturn(buy);
        when(contractDAO.linkContracts(10, 20, "Note", 0)).thenReturn(ContractLinkDAO.LINK_OK);

        ServiceResult<Void> res = service.linkContracts(10, 20, "Note", null);
        assertTrue(res.isSuccess());
        verify(contractDAO).linkContracts(10, 20, "Note", 0);
    }

    @Test
    public void linkContracts_noteNullOrBlankOrLong_handledCorrectly() {
        Contract sell = createContract(10, "HD-BAN-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract buy = createContract(20, "HD-MUA-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        when(contractDAO.findById(10)).thenReturn(sell);
        when(contractDAO.findById(20)).thenReturn(buy);
        when(contractDAO.linkContracts(eq(10), eq(20), any(), eq(adminUser.getUserId())))
                .thenReturn(ContractLinkDAO.LINK_OK);

        // Note null
        assertTrue(service.linkContracts(10, 20, null, adminUser).isSuccess());
        verify(contractDAO).linkContracts(10, 20, null, adminUser.getUserId());

        // Note blank
        assertTrue(service.linkContracts(10, 20, "   ", adminUser).isSuccess());
        verify(contractDAO).linkContracts(10, 20, "   ", adminUser.getUserId());

        // Note very long (> 255 chars)
        String longNote = "A".repeat(300);
        assertTrue(service.linkContracts(10, 20, longNote, adminUser).isSuccess());
        verify(contractDAO).linkContracts(10, 20, longNote, adminUser.getUserId());
    }

    @Test
    public void linkContracts_directionNullOrInvalid_failsWithInvalid() {
        // Current direction null
        Contract cNull = createContract(10, "HD-10", null, null, false);
        Contract buy = createContract(20, "HD-20", ContractLinkDAO.DIRECTION_BUY, null, false);
        when(contractDAO.findById(10)).thenReturn(cNull);
        when(contractDAO.findById(20)).thenReturn(buy);
        ServiceResult<Void> res1 = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res1.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res1.getMessage());

        // Current direction unknown
        Contract cUnknown = createContract(10, "HD-10", "Khác", null, false);
        when(contractDAO.findById(10)).thenReturn(cUnknown);
        ServiceResult<Void> res2 = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res2.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res2.getMessage());

        // Other direction null
        Contract sell = createContract(10, "HD-10", ContractLinkDAO.DIRECTION_SELL, null, false);
        Contract otherNull = createContract(20, "HD-20", null, null, false);
        when(contractDAO.findById(10)).thenReturn(sell);
        when(contractDAO.findById(20)).thenReturn(otherNull);
        ServiceResult<Void> res3 = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res3.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res3.getMessage());

        // Other direction unknown
        Contract otherUnknown = createContract(20, "HD-20", "Khác", null, false);
        when(contractDAO.findById(20)).thenReturn(otherUnknown);
        ServiceResult<Void> res4 = service.linkContracts(10, 20, "Ghi chú", adminUser);
        assertFalse(res4.isSuccess());
        assertEquals(ContractLinkService.ERR_INVALID, res4.getMessage());
    }

    @Test
    public void unlinkContracts_findLinksOfReturnsNull_failsWithNotFound() {
        Contract c = createContract(10, "HD-10", "Bán", null, false);
        when(contractDAO.findById(10)).thenReturn(c);
        when(contractDAO.findLinksOf(10)).thenReturn(null);

        ServiceResult<Void> res = service.unlinkContracts(10, 101, adminUser);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_NOT_FOUND, res.getMessage());
        verify(contractDAO, never()).unlinkContracts(anyInt(), anyInt());
    }

    @Test
    public void constructors_and_null_handling_behavioral() {
        ContractLinkService customSvc = new ContractLinkService(null, null, permissionService);
        when(permissionService.canLinkContracts(salesSubordinate)).thenReturn(false);
        ServiceResult<Void> res = customSvc.linkContracts(1, 2, "test", salesSubordinate);
        assertFalse(res.isSuccess());
        assertEquals(ContractLinkService.ERR_FORBIDDEN, res.getMessage());
    }
}
