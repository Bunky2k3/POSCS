package poscs.service;

import java.io.IOException;
import java.io.OutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import poscs.common.ExcelUtil;
import poscs.common.ListScope;
import poscs.dao.AddressDAO;
import poscs.dao.CustomerDAO;
import poscs.dao.EmployeeDAO;
import poscs.model.Address;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.User;
import poscs.validator.CustomerValidator;

/**
 * Service quản lý nghiệp vụ Khách hàng & Đối tác doanh nghiệp (Enterprises).
 *
 * <p>Quản lý:
 * <ul>
 *   <li>Phân quyền dữ liệu theo địa bàn (user_provinces) và cây nhân sự (team).</li>
 *   <li>Ràng buộc tạo/sửa/xóa khách hàng (BR-09, BR-10, BR-11, BR-27, BR-34, BR-52).</li>
 *   <li>Xuất dữ liệu Excel chuẩn theo vai trò và bộ lọc.</li>
 * </ul>
 *
 * <p>Không phụ thuộc vào Servlet API.
 */
public class CustomerService extends BaseService {

    public static final String SALES_ROLE = "Sales";

    public static final String ROLE_BUYER = "Khách mua";
    public static final String ROLE_SUPPLIER = "Nhà cung cấp";

    public static final List<String> BUYER_TYPES = List.of(
            "Nhà mạng viễn thông", "Nhà thầu thi công", "Đại lý phân phối");

    public static final List<String> SUPPLIER_TYPES = List.of(
            "Nhà sản xuất", "Nhà nhập khẩu", "Nhà phân phối", "Đơn vị dịch vụ");

    public static final String[] CUSTOMER_EXCEL_HEADERS = {
        "STT", "Mã KH", "Tên doanh nghiệp", "Loại KH", "Nhóm KH", "MST", "Email", "SĐT",
        "Website", "Tỉnh/Thành phố", "Địa chỉ", "Người phụ trách chính", "Người hỗ trợ",
        "Ngày tham gia", "Xếp hạng quan hệ"
    };

    private final CustomerDAO customerDAO;
    private final EmployeeDAO employeeDAO;
    private final AddressDAO addressDAO;
    private final CustomerValidator customerValidator;

    public CustomerService() {
        this(new CustomerDAO(), new EmployeeDAO(), new AddressDAO(), new CustomerValidator());
    }

    public CustomerService(CustomerDAO customerDAO, EmployeeDAO employeeDAO, AddressDAO addressDAO) {
        this(customerDAO, employeeDAO, addressDAO, new CustomerValidator());
    }

    public CustomerService(CustomerDAO customerDAO, EmployeeDAO employeeDAO, AddressDAO addressDAO,
            CustomerValidator customerValidator) {
        this.customerDAO = customerDAO;
        this.employeeDAO = employeeDAO;
        this.addressDAO = addressDAO;
        this.customerValidator = customerValidator;
    }

    // ------------------------------------------------------------------
    // Helpers vai trò & danh mục
    // ------------------------------------------------------------------

    public static String roleFromKind(String kind) {
        return "supplier".equals(kind) ? ROLE_SUPPLIER : ROLE_BUYER;
    }

    public static List<String> customerTypesFor(String role) {
        return ROLE_SUPPLIER.equals(role) ? SUPPLIER_TYPES : BUYER_TYPES;
    }

    public static boolean isSupplierOnly(List<String> roles) {
        if (roles == null) {
            return false;
        }
        return roles.contains(ROLE_SUPPLIER) && !roles.contains(ROLE_BUYER);
    }

    public static Integer provinceIdOf(Enterprise customer) {
        if (customer == null) {
            return null;
        }
        Address address = customer.getAddress();
        District district = address != null ? address.getDistrict() : null;
        return district != null ? district.getProvinceId() : null;
    }

    public static String provinceNameOf(Enterprise enterprise) {
        if (enterprise == null) {
            return "Chưa xác định";
        }
        Address address = enterprise.getAddress();
        District district = address != null ? address.getDistrict() : null;
        Province province = district != null ? district.getProvince() : null;
        return province != null ? province.getShortName() : "Chưa xác định";
    }

    public List<Province> withProvince(List<Province> provinces, Integer provinceId) {
        if (provinces == null) {
            return Collections.emptyList();
        }
        if (provinceId == null || provinces.stream().anyMatch(p -> p.getProvinceId() == provinceId)) {
            return provinces;
        }
        List<Province> result = new ArrayList<>(provinces);
        addressDAO.findAllProvinces().stream()
                .filter(p -> p.getProvinceId() == provinceId)
                .findFirst()
                .ifPresent(result::add);
        return result;
    }

    public static List<String> withCurrentType(List<String> types, String current) {
        if (types == null) {
            return (current != null && !current.trim().isEmpty()) ? List.of(current.trim()) : Collections.emptyList();
        }
        if (current == null || current.trim().isEmpty() || types.contains(current)) {
            return types;
        }
        List<String> result = new ArrayList<>(types);
        result.add(current);
        return result;
    }

    public static String editRoleOf(String kind, List<String> roles) {
        if (roles == null) {
            return ROLE_BUYER;
        }
        String asked = "supplier".equals(kind) ? ROLE_SUPPLIER : "buyer".equals(kind) ? ROLE_BUYER : null;
        if (asked != null && roles.contains(asked)) {
            return asked;
        }
        return isSupplierOnly(roles) ? ROLE_SUPPLIER : ROLE_BUYER;
    }

    public static List<String> rolesForUpdate(String[] submitted, String pageRole) {
        List<String> ticked = submitted != null ? List.of(submitted) : List.of();
        List<String> result = new ArrayList<>();
        for (String role : List.of(ROLE_BUYER, ROLE_SUPPLIER)) {
            if (role.equals(pageRole) || ticked.contains(role)) {
                result.add(role);
            }
        }
        return result;
    }

    public String getExportFileNamePrefix(String roleFilter) {
        return ROLE_SUPPLIER.equals(roleFilter) ? "nha_cung_cap" : "khach_hang_mua";
    }

    // ------------------------------------------------------------------
    // Địa bàn & Phân quyền dữ liệu
    // ------------------------------------------------------------------

    /**
     * Các tỉnh người đang đăng nhập TRỰC TIẾP cầm, nếu họ là Sales -- rỗng với
     * mọi trường hợp còn lại. Xếp theo shortName bằng Collator Tiếng Việt.
     */
    public List<Province> territoryOf(User user, boolean isSales) {
        if (!isSales || user == null) {
            return List.of();
        }
        List<Province> provinces = new ArrayList<>(employeeDAO.findProvincesOf(user.getUserId()));
        provinces.sort(Comparator.comparing(Province::getShortName,
                Collator.getInstance(Locale.forLanguageTag("vi"))));
        return provinces;
    }

    public List<Integer> myProvinceIds(User user, boolean isSales) {
        return territoryOf(user, isSales).stream().map(Province::getProvinceId).collect(Collectors.toList());
    }

    public List<Integer> teamOf(User user) {
        if (user == null) {
            return List.of();
        }
        return employeeDAO.findTeamUserIds(user.getUserId());
    }

    /**
     * Kiểm tra đối tác có nằm ngoài quyền ghi của Sales hay không.
     */
    public boolean isOutsideSalesScope(Enterprise customer, List<String> roles, User user, boolean isSales) {
        if (!isSales || user == null) {
            return false;
        }
        return outsideScope(customer, roles, teamOf(user), myProvinceIds(user, isSales));
    }

    public static boolean outsideScope(Enterprise customer, List<String> roles,
            List<Integer> team, List<Integer> myProvinceIds) {
        if (customer == null) {
            return false;
        }
        if (isSupplierOnly(roles)) {
            return team == null || !team.contains(customer.getAccountOwnerId());
        }
        if (myProvinceIds == null || myProvinceIds.isEmpty()) {
            return false;
        }
        return !ListScope.of(team, myProvinceIds).includes(customer.getAccountOwnerId(), provinceIdOf(customer));
    }

    public Set<Integer> lockedIdsOf(List<Enterprise> customers, String listRole, User user, boolean isSales) {
        if (!isSales || user == null || customers == null) {
            return Collections.emptySet();
        }
        List<Integer> team = teamOf(user);
        List<Integer> myProvinceIds = myProvinceIds(user, isSales);
        Set<Integer> locked = new HashSet<>();
        for (Enterprise c : customers) {
            List<String> roles = ROLE_BUYER.equals(listRole)
                    ? List.of(ROLE_BUYER)
                    : customerDAO.findRolesOf(c.getEnterpriseId());
            if (outsideScope(c, roles, team, myProvinceIds)) {
                locked.add(c.getEnterpriseId());
            }
        }
        return locked;
    }

    public boolean provinceAllowedForSales(User user, boolean isSales, Integer wardId, Enterprise existing) {
        if (!isSales) {
            return true;
        }
        List<Integer> myProvinceIds = myProvinceIds(user, isSales);
        if (myProvinceIds.isEmpty() || wardId == null) {
            return true;
        }
        Integer wardProvinceId = addressDAO.findProvinceIdOfWard(wardId);
        return wardProvinceId != null
                && (myProvinceIds.contains(wardProvinceId) || wardProvinceId.equals(provinceIdOf(existing)));
    }

    public Integer resolveAccountOwnerId(Integer wardId, Integer inputOwnerId) {
        if (wardId != null) {
            Integer territoryOwnerId = employeeDAO.findAssigneeOfWard(wardId);
            if (territoryOwnerId != null && territoryOwnerId > 0) {
                return territoryOwnerId;
            }
        }
        return inputOwnerId;
    }

    public Integer ownerForUpdate(User user, boolean isSales, Enterprise existing, List<String> roles,
            Integer wardId, Integer inputOwnerId) {
        if (isSales) {
            return existing != null ? existing.getAccountOwnerId() : null;
        }
        return isSupplierOnly(roles)
                ? inputOwnerId
                : resolveAccountOwnerId(wardId, inputOwnerId);
    }

    public boolean supportIsOwnersManager(Enterprise e) {
        if (e == null) {
            return false;
        }
        Integer supportOwnerId = e.getSupportOwnerId();
        if (supportOwnerId == null || e.getAccountOwnerId() <= 0) {
            return false;
        }
        Map<Integer, Integer> managerMap = employeeDAO.findManagerMap();
        return managerMap != null && supportOwnerId.equals(managerMap.get(e.getAccountOwnerId()));
    }

    public String findDuplicateField(Enterprise e, Integer excludeId) {
        if (e == null) {
            return null;
        }
        if (customerDAO.existsByEmail(e.getEmail(), excludeId)) {
            return "duplicate_email";
        }
        if (customerDAO.existsByPhone(e.getPhone(), excludeId)) {
            return "duplicate_phone";
        }
        if (customerDAO.existsByTaxCode(e.getTaxCode(), excludeId)) {
            return "duplicate_tax_code";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Hỗ trợ hiển thị Form Create/Edit
    // ------------------------------------------------------------------

    public List<Province> getCreateProvinces(User user, boolean isSales, String role) {
        if (ROLE_SUPPLIER.equals(role)) {
            return addressDAO.findAllProvinces();
        }
        List<Province> myProvinces = territoryOf(user, isSales);
        if (!myProvinces.isEmpty()) {
            return myProvinces;
        }
        return addressDAO.findBranchProvinces();
    }

    public List<Province> getEditProvinces(User user, boolean isSales, List<String> roles, Integer currentProvinceId) {
        if (isSupplierOnly(roles)) {
            return addressDAO.findAllProvinces();
        }
        List<Province> myProvinces = territoryOf(user, isSales);
        if (myProvinces.isEmpty()) {
            return addressDAO.findBranchProvincesIncluding(currentProvinceId);
        }
        return withProvince(myProvinces, currentProvinceId);
    }

    // ------------------------------------------------------------------
    // Truy vấn dữ liệu
    // ------------------------------------------------------------------

    public Enterprise findById(int id) {
        return customerDAO.findById(id);
    }

    public List<String> findRolesOf(int id) {
        return customerDAO.findRolesOf(id);
    }

    public List<Enterprise> findAll(int page, int pageSize, String keyword, String customerType,
            Integer accountOwnerId, List<Integer> provinceIds, boolean sortByProvince, String role,
            ListScope scope) {
        return customerDAO.findAll(page, pageSize, keyword, customerType, accountOwnerId,
                provinceIds, sortByProvince, role, scope);
    }

    public int countAll(String keyword, String customerType, Integer accountOwnerId,
            List<Integer> provinceIds, String role, ListScope scope) {
        return customerDAO.countAll(keyword, customerType, accountOwnerId, provinceIds, role, scope);
    }

    // ------------------------------------------------------------------
    // Nghiệp vụ Thêm, Sửa, Xóa
    // ------------------------------------------------------------------

    public static class UpdatePreparedState {
        private final Enterprise enterprise;
        private final List<String> roles;
        private final Enterprise existing;

        public UpdatePreparedState(Enterprise enterprise, List<String> roles, Enterprise existing) {
            this.enterprise = enterprise;
            this.roles = roles;
            this.existing = existing;
        }

        public Enterprise getEnterprise() {
            return enterprise;
        }

        public List<String> getRoles() {
            return roles;
        }

        public Enterprise getExisting() {
            return existing;
        }
    }

    public ServiceResult<Enterprise> prepareAndValidateCreate(Enterprise enterprise, String role, User actor, boolean isSales,
            Integer wardId, String addressDetail, Integer inputOwnerId, Integer inputSupportOwnerId) {
        if (enterprise == null || actor == null) {
            return failure("invalid");
        }

        boolean supplier = ROLE_SUPPLIER.equals(role);
        Integer accountOwnerId;
        if (supplier) {
            accountOwnerId = isSales ? actor.getUserId() : inputOwnerId;
        } else if (!territoryOf(actor, isSales).isEmpty()) {
            if (wardId != null && !Integer.valueOf(actor.getUserId()).equals(employeeDAO.findAssigneeOfWard(wardId))) {
                return failure("province_not_allowed");
            }
            accountOwnerId = actor.getUserId();
        } else {
            accountOwnerId = resolveAccountOwnerId(wardId, inputOwnerId);
        }

        if (accountOwnerId != null) {
            enterprise.setAccountOwnerId(accountOwnerId);
        }
        enterprise.setSupportOwnerId(inputSupportOwnerId);

        if (enterprise.getPhone() != null) {
            enterprise.setPhone(CustomerValidator.cleanPhone(enterprise.getPhone()));
        }

        if (wardId != null || (addressDetail != null && !addressDetail.trim().isEmpty())) {
            if (wardId != null && wardId > 0 && addressDetail != null && !addressDetail.trim().isEmpty()) {
                Address address = new Address();
                address.setStreetAndLocalName(addressDetail.trim());
                address.setDistrictId(wardId);
                enterprise.setAddress(address);
            } else {
                enterprise.setAddress(null);
            }
        }

        if (!customerValidator.isValidCommonFields(enterprise)
                || enterprise.getTaxCode() == null || enterprise.getTaxCode().trim().isEmpty()
                || !customerValidator.isValidAddress(enterprise.getAddress())) {
            return failure("invalid");
        }

        if (supportIsOwnersManager(enterprise)) {
            return failure("support_is_superior");
        }

        String duplicate = findDuplicateField(enterprise, null);
        if (duplicate != null) {
            return failure(duplicate);
        }

        if (enterprise.getEnterpriseCode() == null || enterprise.getEnterpriseCode().trim().isEmpty()) {
            enterprise.setEnterpriseCode(supplier
                    ? customerDAO.generateNextSupplierCode()
                    : customerDAO.generateNextEnterpriseCode());
        }

        return success(enterprise);
    }

    public ServiceResult<Integer> insertCustomer(Enterprise enterprise, String role) {
        if (enterprise == null) {
            return failure("invalid");
        }
        int newId = customerDAO.insert(enterprise);
        if (newId <= 0) {
            log.warning("Them khach hang that bai (enterpriseCode=" + enterprise.getEnterpriseCode() + ")");
            return failure("create_failed");
        }

        String safeRole = (role != null && !role.trim().isEmpty()) ? role.trim() : ROLE_BUYER;
        if (!customerDAO.replaceRolesOf(newId, List.of(safeRole))) {
            log.warning("Ghi vai khach hang moi that bai (enterpriseId=" + newId + ")");
            return success(newId, "roles_not_saved");
        }

        return success(newId);
    }

    public ServiceResult<Integer> createCustomer(Enterprise enterprise, String role, User actor, boolean isSales,
            Integer wardId, String addressDetail, Integer inputOwnerId, Integer inputSupportOwnerId) {
        ServiceResult<Enterprise> valResult = prepareAndValidateCreate(enterprise, role, actor, isSales,
                wardId, addressDetail, inputOwnerId, inputSupportOwnerId);
        if (!valResult.isSuccess()) {
            return failure(valResult.getMessage());
        }
        return insertCustomer(enterprise, role);
    }

    public ServiceResult<UpdatePreparedState> prepareAndValidateUpdate(Enterprise enterprise, String[] submittedRoles, String pageRole,
            User actor, boolean isSales, Integer wardId, String addressDetail, Integer inputOwnerId,
            Integer inputSupportOwnerId) {
        if (enterprise == null || actor == null) {
            return failure("invalid");
        }

        int id = enterprise.getEnterpriseId();
        Enterprise existing = customerDAO.findById(id);
        if (existing == null) {
            return failure("notfound");
        }

        List<String> currentRoles = customerDAO.findRolesOf(id);
        if (isOutsideSalesScope(existing, currentRoles, actor, isSales)) {
            return failure("not_your_customer");
        }

        List<String> roles = rolesForUpdate(submittedRoles, pageRole);
        if (!isSupplierOnly(roles) && !provinceAllowedForSales(actor, isSales, wardId, existing)) {
            return failure("province_not_allowed");
        }

        Integer accountOwnerId = ownerForUpdate(actor, isSales, existing, roles, wardId, inputOwnerId);
        if (accountOwnerId != null) {
            enterprise.setAccountOwnerId(accountOwnerId);
        }
        enterprise.setSupportOwnerId(inputSupportOwnerId);

        if (enterprise.getPhone() != null) {
            enterprise.setPhone(CustomerValidator.cleanPhone(enterprise.getPhone()));
        }

        if (wardId != null || (addressDetail != null && !addressDetail.trim().isEmpty())) {
            if (wardId != null && wardId > 0 && addressDetail != null && !addressDetail.trim().isEmpty()) {
                Address address = new Address();
                address.setStreetAndLocalName(addressDetail.trim());
                address.setDistrictId(wardId);
                enterprise.setAddress(address);
            } else {
                return failure("invalid");
            }
        }
        enterprise.setAddressId(existing.getAddressId());

        if (!customerValidator.isValidCommonFields(enterprise)
                || (enterprise.getAddress() != null && !customerValidator.isValidAddress(enterprise.getAddress()))
                || (enterprise.getAddress() == null && (existing.getAddressId() == null || existing.getAddressId() <= 0))) {
            return failure("invalid");
        }

        if (supportIsOwnersManager(enterprise)) {
            return failure("support_is_superior");
        }

        String duplicate = findDuplicateField(enterprise, id);
        if (duplicate != null) {
            return failure(duplicate);
        }

        if (enterprise.getLogoUrl() == null) {
            enterprise.setLogoUrl(existing.getLogoUrl());
        }

        return success(new UpdatePreparedState(enterprise, roles, existing));
    }

    public ServiceResult<Enterprise> commitUpdate(Enterprise enterprise, List<String> roles) {
        if (enterprise == null) {
            return failure("invalid");
        }
        if (roles == null || roles.isEmpty()) {
            return failure("invalid_roles");
        }
        boolean ok = customerDAO.update(enterprise);
        if (!ok) {
            log.warning("Cap nhat khach hang that bai (enterpriseId=" + enterprise.getEnterpriseId() + ")");
            return failure("update_failed");
        }

        if (!customerDAO.replaceRolesOf(enterprise.getEnterpriseId(), roles)) {
            log.warning("Ghi vai khach hang that bai (enterpriseId=" + enterprise.getEnterpriseId() + ")");
            return success(enterprise, "roles_not_saved");
        }

        return success(enterprise);
    }

    public ServiceResult<Enterprise> updateCustomer(Enterprise enterprise, String[] submittedRoles, String pageRole,
            User actor, boolean isSales, Integer wardId, String addressDetail, Integer inputOwnerId,
            Integer inputSupportOwnerId) {
        ServiceResult<UpdatePreparedState> prepResult = prepareAndValidateUpdate(enterprise, submittedRoles,
                pageRole, actor, isSales, wardId, addressDetail, inputOwnerId, inputSupportOwnerId);
        if (!prepResult.isSuccess()) {
            return failure(prepResult.getMessage());
        }
        return commitUpdate(enterprise, prepResult.getData().getRoles());
    }

    public ServiceResult<Void> deleteCustomer(int id, User actor, boolean isSales) {
        if (actor == null || actor.getUserId() <= 0 || id <= 0) {
            return failure("invalid");
        }
        Enterprise existing = customerDAO.findById(id);
        if (existing == null) {
            return failure("notfound");
        }
        if (isOutsideSalesScope(existing, customerDAO.findRolesOf(id), actor, isSales)) {
            return failure("not_your_customer");
        }
        if (customerDAO.hasActiveContracts(id)) {
            return failure("has_active_contracts");
        }
        if (!customerDAO.softDelete(id)) {
            log.warning("Xoa khach hang that bai (enterpriseId=" + id + ")");
            return failure("delete_failed");
        }
        return success(null);
    }

    // ------------------------------------------------------------------
    // Xuất Excel
    // ------------------------------------------------------------------

    public List<Object[]> buildExportRows(List<Enterprise> customers) {
        List<Object[]> rows = new ArrayList<>();
        if (customers == null) {
            return rows;
        }
        int stt = 1;
        for (Enterprise e : customers) {
            rows.add(new Object[]{
                stt++,
                e.getEnterpriseCode(),
                e.getEnterpriseName(),
                e.getCustomerType(),
                e.getCustomerGroup(),
                e.getTaxCode(),
                e.getEmail(),
                e.getPhone(),
                e.getWebsite(),
                provinceNameOf(e),
                e.getAddress() != null ? e.getAddress().getFullAddress() : "",
                e.getAccountOwner() != null ? e.getAccountOwner().getFullName() : "",
                e.getSupportOwner() != null ? e.getSupportOwner().getFullName() : "",
                e.getJoinDate() != null ? e.getJoinDate().toString() : "",
                e.getCurrentRelationshipRating() != null ? e.getCurrentRelationshipRating().toString() : ""
            });
        }
        return rows;
    }

    public void exportCustomersToExcel(OutputStream outputStream, String keyword, String typeFilter,
            Integer assigneeFilter, List<Integer> provinceFilters, String roleFilter, ListScope scope)
            throws IOException {
        if (ROLE_SUPPLIER.equals(roleFilter)) {
            provinceFilters = List.of();
        }
        List<Enterprise> all = customerDAO.findAll(1, Integer.MAX_VALUE, keyword, typeFilter, assigneeFilter,
                provinceFilters, true, roleFilter, scope);
        List<Object[]> rows = buildExportRows(all);
        ExcelUtil.writeWorkbook(outputStream, CUSTOMER_EXCEL_HEADERS, rows);
    }
}
