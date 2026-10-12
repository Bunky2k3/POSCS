package poscs.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import poscs.common.ListScope;
import poscs.dao.AddressDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.Role;
import poscs.model.User;
import poscs.validator.CustomerValidator;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test cho CustomerService (Task 2.2).
 * Kiểm thử toàn diện các quy tắc nghiệp vụ:
 * - Phân quyền địa bàn và cây nhân sự (Sales vs non-Sales)
 * - Ràng buộc thêm/sửa/xoá khách hàng (BR-09, BR-10, BR-11, BR-34, BR-52)
 * - Xử lý trùng lặp Email/SĐT/MST
 * - Xuất Excel danh sách khách hàng
 */
public class CustomerServiceTest {

    private CustomerDAO customerDAO;
    private EmployeeDAO employeeDAO;
    private AddressDAO addressDAO;
    private CustomerValidator customerValidator;
    private CustomerService service;

    private User salesUser;
    private User managerUser;

    @Before
    public void setUp() {
        customerDAO = mock(CustomerDAO.class);
        employeeDAO = mock(EmployeeDAO.class);
        addressDAO = mock(AddressDAO.class);
        customerValidator = new CustomerValidator();

        service = new CustomerService(customerDAO, employeeDAO, addressDAO, customerValidator);

        salesUser = new User();
        salesUser.setUserId(10);
        salesUser.setRole(new Role(1, "Sales"));

        managerUser = new User();
        managerUser.setUserId(1);
        managerUser.setRole(new Role(2, "Sales Manager"));
    }

    private Enterprise sampleEnterprise(int id, String name, String code, String email, String phone, String taxCode) {
        Enterprise e = new Enterprise();
        e.setEnterpriseId(id);
        e.setEnterpriseName(name);
        e.setEnterpriseCode(code);
        e.setEmail(email);
        e.setPhone(phone);
        e.setTaxCode(taxCode);
        e.setCustomerType("Nhà mạng viễn thông");
        e.setCustomerGroup("Doanh nghiệp nhỏ");
        e.setAccountOwnerId(salesUser.getUserId());

        Address addr = new Address();
        addr.setAddressId(100 + id);
        addr.setStreetAndLocalName("123 Duong ABC");
        District district = new District(5, "Quan 1", 1);
        addr.setDistrict(district);
        addr.setDistrictId(5);
        e.setAddress(addr);
        e.setAddressId(addr.getAddressId());

        return e;
    }

    // ------------------------------------------------------------------
    // Helpers vai trò & danh mục
    // ------------------------------------------------------------------

    @Test
    public void testRoleFromKind() {
        assertEquals(CustomerService.ROLE_SUPPLIER, CustomerService.roleFromKind("supplier"));
        assertEquals(CustomerService.ROLE_BUYER, CustomerService.roleFromKind("buyer"));
        assertEquals(CustomerService.ROLE_BUYER, CustomerService.roleFromKind(null));
        assertEquals(CustomerService.ROLE_BUYER, CustomerService.roleFromKind("other"));
    }

    @Test
    public void testCustomerTypesFor() {
        assertEquals(CustomerService.SUPPLIER_TYPES, CustomerService.customerTypesFor(CustomerService.ROLE_SUPPLIER));
        assertEquals(CustomerService.BUYER_TYPES, CustomerService.customerTypesFor(CustomerService.ROLE_BUYER));
    }

    @Test
    public void testIsSupplierOnly() {
        assertFalse(CustomerService.isSupplierOnly(null));
        assertFalse(CustomerService.isSupplierOnly(List.of()));
        assertFalse(CustomerService.isSupplierOnly(List.of(CustomerService.ROLE_BUYER)));
        assertTrue(CustomerService.isSupplierOnly(List.of(CustomerService.ROLE_SUPPLIER)));
        assertFalse(CustomerService.isSupplierOnly(List.of(CustomerService.ROLE_BUYER, CustomerService.ROLE_SUPPLIER)));
    }

    @Test
    public void testProvinceIdAndNameOf() {
        assertNull(CustomerService.provinceIdOf(null));
        assertEquals("Chưa xác định", CustomerService.provinceNameOf(null));

        Enterprise e = new Enterprise();
        assertNull(CustomerService.provinceIdOf(e));
        assertEquals("Chưa xác định", CustomerService.provinceNameOf(e));

        Address addr = new Address();
        District d = new District();
        d.setProvinceId(29);
        Province p = new Province(29, "Hà Nội");
        d.setProvince(p);
        addr.setDistrict(d);
        e.setAddress(addr);

        assertEquals(Integer.valueOf(29), CustomerService.provinceIdOf(e));
        assertEquals("Hà Nội", CustomerService.provinceNameOf(e));
    }

    @Test
    public void testWithProvince() {
        Province p1 = new Province(1, "Hà Nội");
        Province p2 = new Province(2, "Thành phố Hồ Chí Minh");
        when(addressDAO.findAllProvinces()).thenReturn(List.of(p1, p2));

        List<Province> list = List.of(p1);
        // Da co san trong list
        assertSame(list, service.withProvince(list, 1));
        // ProvinceId null
        assertSame(list, service.withProvince(list, null));
        // Chua co trong list -> them tu addressDAO
        List<Province> updated = service.withProvince(list, 2);
        assertEquals(2, updated.size());
        assertTrue(updated.stream().anyMatch(p -> p.getProvinceId() == 2));

        assertTrue(service.withProvince(null, 1).isEmpty());
    }

    @Test
    public void testWithCurrentType() {
        List<String> types = List.of("Type A", "Type B");
        assertSame(types, CustomerService.withCurrentType(types, null));
        assertSame(types, CustomerService.withCurrentType(types, "  "));
        assertSame(types, CustomerService.withCurrentType(types, "Type A"));

        List<String> updated = CustomerService.withCurrentType(types, "Type C");
        assertEquals(3, updated.size());
        assertTrue(updated.contains("Type C"));

        assertEquals(List.of("Type X"), CustomerService.withCurrentType(null, "Type X"));
        assertTrue(CustomerService.withCurrentType(null, null).isEmpty());
    }

    @Test
    public void testEditRoleOf() {
        assertEquals(CustomerService.ROLE_BUYER, CustomerService.editRoleOf(null, null));
        assertEquals(CustomerService.ROLE_SUPPLIER, CustomerService.editRoleOf("supplier", List.of(CustomerService.ROLE_SUPPLIER)));
        assertEquals(CustomerService.ROLE_BUYER, CustomerService.editRoleOf("buyer", List.of(CustomerService.ROLE_BUYER)));
        assertEquals(CustomerService.ROLE_SUPPLIER, CustomerService.editRoleOf(null, List.of(CustomerService.ROLE_SUPPLIER)));
        assertEquals(CustomerService.ROLE_BUYER, CustomerService.editRoleOf(null, List.of(CustomerService.ROLE_BUYER, CustomerService.ROLE_SUPPLIER)));
    }

    @Test
    public void testRolesForUpdate() {
        List<String> roles = CustomerService.rolesForUpdate(new String[]{"Khách mua"}, CustomerService.ROLE_BUYER);
        assertEquals(List.of("Khách mua"), roles);

        List<String> both = CustomerService.rolesForUpdate(new String[]{"Nhà cung cấp"}, CustomerService.ROLE_BUYER);
        assertEquals(List.of("Khách mua", "Nhà cung cấp"), both);
    }

    @Test
    public void testGetExportFileNamePrefix() {
        assertEquals("nha_cung_cap", service.getExportFileNamePrefix(CustomerService.ROLE_SUPPLIER));
        assertEquals("khach_hang_mua", service.getExportFileNamePrefix(CustomerService.ROLE_BUYER));
    }

    // ------------------------------------------------------------------
    // Địa bàn & Phân quyền dữ liệu
    // ------------------------------------------------------------------

    @Test
    public void testTerritoryOf_NonSalesReturnsEmpty() {
        List<Province> provinces = service.territoryOf(managerUser, false);
        assertTrue(provinces.isEmpty());

        List<Province> nullUserProvinces = service.territoryOf(null, true);
        assertTrue(nullUserProvinces.isEmpty());
    }

    @Test
    public void testTerritoryOf_SalesReturnsSortedByVietnameseCollator() {
        Province pDanang = new Province(3, "Đà Nẵng");
        Province pHanoi = new Province(1, "Hà Nội");
        Province pAnGiang = new Province(2, "An Giang");

        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(pHanoi, pDanang, pAnGiang));

        List<Province> sorted = service.territoryOf(salesUser, true);
        assertEquals(3, sorted.size());
        assertEquals("An Giang", sorted.get(0).getShortName());
        assertEquals("Đà Nẵng", sorted.get(1).getShortName());
        assertEquals("Hà Nội", sorted.get(2).getShortName());
    }

    @Test
    public void testTeamOf() {
        assertTrue(service.teamOf(null).isEmpty());

        when(employeeDAO.findTeamUserIds(10)).thenReturn(List.of(10, 11, 12));
        assertEquals(List.of(10, 11, 12), service.teamOf(salesUser));
    }

    @Test
    public void testIsOutsideSalesScope() {
        // Non-sales is never outside scope
        Enterprise e = sampleEnterprise(1, "Cong ty A", "KH-0001", "a@test.com", "0901234567", "0123456789");
        assertFalse(service.isOutsideSalesScope(e, List.of(CustomerService.ROLE_BUYER), managerUser, false));
        assertFalse(service.isOutsideSalesScope(e, List.of(CustomerService.ROLE_BUYER), null, true));

        // Sales scope: Sales owns enterprise in his team
        when(employeeDAO.findTeamUserIds(salesUser.getUserId())).thenReturn(List.of(salesUser.getUserId()));
        Province p = new Province(1, "Hà Nội");
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(p));

        // In scope
        assertFalse(service.isOutsideSalesScope(e, List.of(CustomerService.ROLE_BUYER), salesUser, true));

        // Supplier only outside scope if owner is not in sales' team
        e.setAccountOwnerId(999);
        assertTrue(service.isOutsideSalesScope(e, List.of(CustomerService.ROLE_SUPPLIER), salesUser, true));
        // Team null an toan khong bi NPE
        assertTrue(CustomerService.outsideScope(e, List.of(CustomerService.ROLE_SUPPLIER), null, List.of()));
    }

    @Test
    public void testLockedIdsOf() {
        Enterprise e1 = sampleEnterprise(1, "Cong ty 1", "KH-0001", "c1@test.com", "0901234561", "0123456781");
        Enterprise e2 = sampleEnterprise(2, "Cong ty 2", "KH-0002", "c2@test.com", "0901234562", "0123456782");
        e2.setAccountOwnerId(999); // outside team
        e2.getAddress().getDistrict().setProvinceId(2); // outside province

        // Non sales: lockedIds is empty
        Set<Integer> lockedNonSales = service.lockedIdsOf(List.of(e1, e2), CustomerService.ROLE_BUYER, managerUser, false);
        assertTrue(lockedNonSales.isEmpty());

        // Sales
        when(employeeDAO.findTeamUserIds(salesUser.getUserId())).thenReturn(List.of(salesUser.getUserId()));
        Province p = new Province(1, "Hà Nội");
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(p));

        Set<Integer> lockedSales = service.lockedIdsOf(List.of(e1, e2), CustomerService.ROLE_BUYER, salesUser, true);
        assertEquals(Set.of(2), lockedSales);
    }

    @Test
    public void testProvinceAllowedForSales() {
        // Non-sales always allowed
        assertTrue(service.provinceAllowedForSales(managerUser, false, 10, null));

        // Sales with no assigned province (unassigned) -> allowed
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of());
        assertTrue(service.provinceAllowedForSales(salesUser, true, 10, null));

        // Sales with assigned province
        Province p1 = new Province(1, "Hà Nội");
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(p1));
        when(addressDAO.findProvinceIdOfWard(10)).thenReturn(1);
        when(addressDAO.findProvinceIdOfWard(20)).thenReturn(2);

        assertTrue(service.provinceAllowedForSales(salesUser, true, 10, null));
        assertFalse(service.provinceAllowedForSales(salesUser, true, 20, null));

        // If ward matches existing customer's province
        Enterprise existing = sampleEnterprise(1, "Cong ty 1", "KH-0001", "c1@test.com", "0901234561", "0123456781");
        existing.getAddress().getDistrict().setProvinceId(2);
        assertTrue(service.provinceAllowedForSales(salesUser, true, 20, existing));
    }

    @Test
    public void testResolveAccountOwnerId() {
        when(employeeDAO.findAssigneeOfWard(5)).thenReturn(42);
        when(employeeDAO.findAssigneeOfWard(6)).thenReturn(null);

        assertEquals(Integer.valueOf(42), service.resolveAccountOwnerId(5, 10));
        assertEquals(Integer.valueOf(10), service.resolveAccountOwnerId(6, 10));
        assertEquals(Integer.valueOf(10), service.resolveAccountOwnerId(null, 10));
    }

    @Test
    public void testOwnerForUpdate() {
        Enterprise existing = sampleEnterprise(1, "Cong ty 1", "KH-0001", "c1@test.com", "0901234561", "0123456781");
        existing.setAccountOwnerId(77);

        // Sales preserves existing owner
        assertEquals(Integer.valueOf(77), service.ownerForUpdate(salesUser, true, existing, List.of("Khách mua"), 5, 99));

        // Non sales supplier only -> inputOwnerId
        assertEquals(Integer.valueOf(99), service.ownerForUpdate(managerUser, false, existing, List.of("Nhà cung cấp"), 5, 99));

        // Non sales buyer -> resolved by ward
        when(employeeDAO.findAssigneeOfWard(5)).thenReturn(42);
        assertEquals(Integer.valueOf(42), service.ownerForUpdate(managerUser, false, existing, List.of("Khách mua"), 5, 99));
    }

    @Test
    public void testSupportIsOwnersManager() {
        assertFalse(service.supportIsOwnersManager(null));

        Enterprise e = new Enterprise();
        e.setAccountOwnerId(10);
        e.setSupportOwnerId(null);
        assertFalse(service.supportIsOwnersManager(e));

        e.setSupportOwnerId(1); // manager
        Map<Integer, Integer> map = new HashMap<>();
        map.put(10, 1); // 10's manager is 1
        when(employeeDAO.findManagerMap()).thenReturn(map);

        assertTrue(service.supportIsOwnersManager(e));

        e.setSupportOwnerId(2); // not 10's manager
        assertFalse(service.supportIsOwnersManager(e));
    }

    @Test
    public void testFindDuplicateField() {
        assertNull(service.findDuplicateField(null, null));

        Enterprise e = sampleEnterprise(1, "Cong ty 1", "KH-0001", "dup@test.com", "0901234567", "0123456789");

        when(customerDAO.existsByEmail("dup@test.com", null)).thenReturn(true);
        assertEquals("duplicate_email", service.findDuplicateField(e, null));

        when(customerDAO.existsByEmail("dup@test.com", null)).thenReturn(false);
        when(customerDAO.existsByPhone("0901234567", null)).thenReturn(true);
        assertEquals("duplicate_phone", service.findDuplicateField(e, null));

        when(customerDAO.existsByPhone("0901234567", null)).thenReturn(false);
        when(customerDAO.existsByTaxCode("0123456789", null)).thenReturn(true);
        assertEquals("duplicate_tax_code", service.findDuplicateField(e, null));

        when(customerDAO.existsByTaxCode("0123456789", null)).thenReturn(false);
        assertNull(service.findDuplicateField(e, null));
    }

    // ------------------------------------------------------------------
    // Tạo khách hàng (Create Customer)
    // ------------------------------------------------------------------

    @Test
    public void testCreateCustomer_SuccessBuyer() {
        Enterprise e = sampleEnterprise(0, "Cong ty Moi", null, "moi@test.com", "0901234567", "0123456789");
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(new Province(1, "Hà Nội")));
        when(employeeDAO.findAssigneeOfWard(5)).thenReturn(salesUser.getUserId());
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0009");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(9);
        when(customerDAO.replaceRolesOf(eq(9), anyList())).thenReturn(true);

        ServiceResult<Integer> result = service.createCustomer(e, CustomerService.ROLE_BUYER, salesUser, true,
                5, "123 Duong ABC", null, null);

        assertTrue(result.isSuccess());
        assertEquals(Integer.valueOf(9), result.getData());
        assertEquals("KH-0009", e.getEnterpriseCode());
        verify(customerDAO).insert(e);
        verify(customerDAO).replaceRolesOf(9, List.of("Khách mua"));
    }

    @Test
    public void testCreateCustomer_ProvinceNotAllowedForSales() {
        Enterprise e = sampleEnterprise(0, "Cong ty Moi", null, "moi@test.com", "0901234567", "0123456789");
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(new Province(1, "Hà Nội")));
        when(employeeDAO.findAssigneeOfWard(5)).thenReturn(999); // Ward assigned to someone else

        ServiceResult<Integer> result = service.createCustomer(e, CustomerService.ROLE_BUYER, salesUser, true,
                5, "123 Duong ABC", null, null);

        assertFalse(result.isSuccess());
        assertEquals("province_not_allowed", result.getMessage());
        verify(customerDAO, never()).insert(any());
    }

    @Test
    public void testCreateCustomer_SupportIsSuperior() {
        Enterprise e = sampleEnterprise(0, "Cong ty Moi", null, "moi@test.com", "0901234567", "0123456789");
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(new Province(1, "Hà Nội")));
        when(employeeDAO.findAssigneeOfWard(5)).thenReturn(salesUser.getUserId());

        Map<Integer, Integer> map = new HashMap<>();
        map.put(salesUser.getUserId(), 1); // Manager of salesUser is 1
        when(employeeDAO.findManagerMap()).thenReturn(map);

        ServiceResult<Integer> result = service.createCustomer(e, CustomerService.ROLE_BUYER, salesUser, true,
                5, "123 Duong ABC", null, 1);

        assertFalse(result.isSuccess());
        assertEquals("support_is_superior", result.getMessage());
        verify(customerDAO, never()).insert(any());
    }

    @Test
    public void testCreateCustomer_DuplicateTaxCode() {
        Enterprise e = sampleEnterprise(0, "Cong ty Moi", null, "moi@test.com", "0901234567", "0123456789");
        when(customerDAO.existsByTaxCode("0123456789", null)).thenReturn(true);

        ServiceResult<Integer> result = service.createCustomer(e, CustomerService.ROLE_BUYER, managerUser, false,
                5, "123 Duong ABC", 10, null);

        assertFalse(result.isSuccess());
        assertEquals("duplicate_tax_code", result.getMessage());
        verify(customerDAO, never()).insert(any());
    }

    @Test
    public void testCreateCustomer_InsertFailed() {
        Enterprise e = sampleEnterprise(0, "Cong ty Moi", null, "moi@test.com", "0901234567", "0123456789");
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0009");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(-1);

        ServiceResult<Integer> result = service.createCustomer(e, CustomerService.ROLE_BUYER, managerUser, false,
                5, "123 Duong ABC", 10, null);

        assertFalse(result.isSuccess());
        assertEquals("create_failed", result.getMessage());
    }

    @Test
    public void testCreateCustomer_PhoneNormalized() {
        Enterprise e = sampleEnterprise(0, "Cong ty Moi", null, "moi@test.com", "0912.345.678", "0123456789");
        when(customerDAO.generateNextEnterpriseCode()).thenReturn("KH-0009");
        when(customerDAO.insert(any(Enterprise.class))).thenReturn(9);
        when(customerDAO.replaceRolesOf(eq(9), anyList())).thenReturn(true);

        ServiceResult<Integer> result = service.createCustomer(e, CustomerService.ROLE_BUYER, managerUser, false,
                5, "123 Duong ABC", 10, null);

        assertTrue(result.isSuccess());
        assertEquals("0912345678", e.getPhone());
    }

    @Test
    public void testCreateCustomer_InvalidWardId() {
        Enterprise e = sampleEnterprise(0, "Cong ty Moi", null, "moi@test.com", "0912345678", "0123456789");

        // wardId = 0
        ServiceResult<Integer> r1 = service.createCustomer(e, CustomerService.ROLE_BUYER, managerUser, false,
                0, "123 Duong ABC", 10, null);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        // wardId = -1
        ServiceResult<Integer> r2 = service.createCustomer(e, CustomerService.ROLE_BUYER, managerUser, false,
                -1, "123 Duong ABC", 10, null);
        assertFalse(r2.isSuccess());
        assertEquals("invalid", r2.getMessage());
    }

    @Test
    public void testInsertCustomer_NullRoleFallback() {
        Enterprise e = sampleEnterprise(1, "Cong ty", "KH-0001", "a@test.com", "0901234567", "0123456789");
        when(customerDAO.insert(e)).thenReturn(10);
        when(customerDAO.replaceRolesOf(eq(10), anyList())).thenReturn(true);

        ServiceResult<Integer> result = service.insertCustomer(e, null);

        assertTrue(result.isSuccess());
        verify(customerDAO).replaceRolesOf(10, List.of("Khách mua"));
    }

    // ------------------------------------------------------------------
    // Cập nhật khách hàng (Update Customer)
    // ------------------------------------------------------------------

    @Test
    public void testUpdateCustomer_Success() {
        Enterprise existing = sampleEnterprise(1, "Cong ty Cu", "KH-0001", "old@test.com", "0901234561", "0123456781");
        when(customerDAO.findById(1)).thenReturn(existing);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(employeeDAO.findTeamUserIds(salesUser.getUserId())).thenReturn(List.of(salesUser.getUserId()));
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(new Province(1, "Hà Nội")));
        when(addressDAO.findProvinceIdOfWard(5)).thenReturn(1);
        when(customerDAO.update(any(Enterprise.class))).thenReturn(true);
        when(customerDAO.replaceRolesOf(eq(1), anyList())).thenReturn(true);

        Enterprise updateData = sampleEnterprise(1, "Cong ty Moi", "KH-0001", "new@test.com", "0901234562", "0123456782");
        ServiceResult<Enterprise> result = service.updateCustomer(updateData, new String[]{"Khách mua"},
                CustomerService.ROLE_BUYER, salesUser, true, 5, "456 Duong XYZ", null, null);

        assertTrue(result.isSuccess());
        verify(customerDAO).update(updateData);
        verify(customerDAO).replaceRolesOf(1, List.of("Khách mua"));
    }

    @Test
    public void testUpdateCustomer_NotFound() {
        when(customerDAO.findById(99)).thenReturn(null);
        Enterprise updateData = sampleEnterprise(99, "Cong ty", "KH-0099", "a@test.com", "0901234567", "0123456789");

        ServiceResult<Enterprise> result = service.updateCustomer(updateData, null, CustomerService.ROLE_BUYER,
                managerUser, false, 5, "Addr", null, null);

        assertFalse(result.isSuccess());
        assertEquals("notfound", result.getMessage());
    }

    @Test
    public void testUpdateCustomer_OutsideSalesScope() {
        Enterprise existing = sampleEnterprise(1, "Cong ty Cu", "KH-0001", "old@test.com", "0901234561", "0123456781");
        existing.setAccountOwnerId(999); // outside team
        existing.getAddress().getDistrict().setProvinceId(2); // outside province
        when(customerDAO.findById(1)).thenReturn(existing);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(employeeDAO.findTeamUserIds(salesUser.getUserId())).thenReturn(List.of(salesUser.getUserId()));
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(new Province(1, "Hà Nội")));

        Enterprise updateData = sampleEnterprise(1, "Cong ty Cu", "KH-0001", "old@test.com", "0901234561", "0123456781");
        ServiceResult<Enterprise> result = service.updateCustomer(updateData, null, CustomerService.ROLE_BUYER,
                salesUser, true, 5, "Addr", null, null);

        assertFalse(result.isSuccess());
        assertEquals("not_your_customer", result.getMessage());
        verify(customerDAO, never()).update(any());
    }

    // ------------------------------------------------------------------
    // Xoá khách hàng (Delete Customer - BR-34)
    // ------------------------------------------------------------------

    @Test
    public void testDeleteCustomer_Success() {
        Enterprise existing = sampleEnterprise(1, "Cong ty Cu", "KH-0001", "old@test.com", "0901234561", "0123456781");
        when(customerDAO.findById(1)).thenReturn(existing);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(employeeDAO.findTeamUserIds(salesUser.getUserId())).thenReturn(List.of(salesUser.getUserId()));
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(new Province(1, "Hà Nội")));
        when(customerDAO.hasActiveContracts(1)).thenReturn(false);
        when(customerDAO.softDelete(1)).thenReturn(true);

        ServiceResult<Void> result = service.deleteCustomer(1, salesUser, true);

        assertTrue(result.isSuccess());
        verify(customerDAO).softDelete(1);
    }

    @Test
    public void testDeleteCustomer_HasActiveContracts_BR34() {
        Enterprise existing = sampleEnterprise(1, "Cong ty Cu", "KH-0001", "old@test.com", "0901234561", "0123456781");
        when(customerDAO.findById(1)).thenReturn(existing);
        when(customerDAO.findRolesOf(1)).thenReturn(List.of("Khách mua"));
        when(employeeDAO.findTeamUserIds(salesUser.getUserId())).thenReturn(List.of(salesUser.getUserId()));
        when(employeeDAO.findProvincesOf(salesUser.getUserId())).thenReturn(List.of(new Province(1, "Hà Nội")));
        when(customerDAO.hasActiveContracts(1)).thenReturn(true); // BR-34 chặn

        ServiceResult<Void> result = service.deleteCustomer(1, salesUser, true);

        assertFalse(result.isSuccess());
        assertEquals("has_active_contracts", result.getMessage());
        verify(customerDAO, never()).softDelete(anyInt());
    }

    @Test
    public void testDeleteCustomer_NotFound() {
        when(customerDAO.findById(99)).thenReturn(null);

        ServiceResult<Void> result = service.deleteCustomer(99, managerUser, false);

        assertFalse(result.isSuccess());
        assertEquals("notfound", result.getMessage());
    }

    @Test
    public void testDeleteCustomer_GuardClauses() {
        // id <= 0
        ServiceResult<Void> r1 = service.deleteCustomer(0, salesUser, true);
        assertFalse(r1.isSuccess());
        assertEquals("invalid", r1.getMessage());

        ServiceResult<Void> r2 = service.deleteCustomer(-5, salesUser, true);
        assertFalse(r2.isSuccess());
        assertEquals("invalid", r2.getMessage());

        // actor == null
        ServiceResult<Void> r3 = service.deleteCustomer(1, null, true);
        assertFalse(r3.isSuccess());
        assertEquals("invalid", r3.getMessage());

        // actor.userId <= 0
        User invalidUser = new User();
        invalidUser.setUserId(0);
        ServiceResult<Void> r4 = service.deleteCustomer(1, invalidUser, false);
        assertFalse(r4.isSuccess());
        assertEquals("invalid", r4.getMessage());
    }

    @Test
    public void testCommitUpdate_EmptyRoles() {
        Enterprise e = sampleEnterprise(1, "Cong ty", "KH-0001", "a@test.com", "0901234567", "0123456789");

        ServiceResult<Enterprise> r1 = service.commitUpdate(e, null);
        assertFalse(r1.isSuccess());
        assertEquals("invalid_roles", r1.getMessage());

        ServiceResult<Enterprise> r2 = service.commitUpdate(e, List.of());
        assertFalse(r2.isSuccess());
        assertEquals("invalid_roles", r2.getMessage());
    }

    // ------------------------------------------------------------------
    // Xuất Excel
    // ------------------------------------------------------------------

    @Test
    public void testBuildExportRows() {
        Enterprise e = sampleEnterprise(1, "Cong ty ABC", "KH-0001", "abc@test.com", "0901234567", "0123456789");
        e.setCustomerType("Nhà mạng viễn thông");
        e.setCustomerGroup("VIP");
        e.setWebsite("https://abc.com");
        User owner = new User();
        owner.setLastName("Nguyen");
        owner.setFirstName("Van A");
        e.setAccountOwner(owner);

        List<Object[]> rows = service.buildExportRows(List.of(e));
        assertEquals(1, rows.size());
        Object[] row = rows.get(0);
        assertEquals(1, row[0]); // STT
        assertEquals("KH-0001", row[1]); // Mã KH
        assertEquals("Cong ty ABC", row[2]); // Tên doanh nghiệp
        assertEquals("Nhà mạng viễn thông", row[3]); // Loại KH
        assertEquals("VIP", row[4]); // Nhóm KH
        assertEquals("0123456789", row[5]); // MST
        assertEquals("abc@test.com", row[6]); // Email
        assertEquals("0901234567", row[7]); // SĐT
        assertEquals("https://abc.com", row[8]); // Website
        assertEquals("Nguyen Van A", row[11]); // Người phụ trách
    }

    @Test
    public void testExportCustomersToExcel() throws IOException {
        Enterprise e = sampleEnterprise(1, "Cong ty ABC", "KH-0001", "abc@test.com", "0901234567", "0123456789");
        when(customerDAO.findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(e));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        service.exportCustomersToExcel(baos, null, null, null, List.of(), CustomerService.ROLE_BUYER, ListScope.all());

        assertTrue("Output Excel bytes should be generated", baos.size() > 0);
    }
}
