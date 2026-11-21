package poscs.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import poscs.common.ExcelUtil;
import poscs.common.ListScope;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.model.Address;
import poscs.model.Contract;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;

/**
 * Service xuất danh sách hợp đồng ra Excel (Task 4.17 - [UC-41], [BR-16]).
 * Ràng buộc:
 * - Giữ nguyên cùng bộ lọc như danh sách hiển thị (từ khoá, trạng thái, loại, tỉnh, kỳ, tiến độ, chỉ HĐ gốc, phòng đang chờ, phạm vi).
 *   Bộ lọc bước giao hàng (BR-61, tham số {@code delivery}) đi trong {@link ListScope} cùng phạm vi,
 *   nên truyền nguyên {@code scope} xuống {@code ContractDAO.findAll} là Excel ra đúng danh sách đang lọc.
 * - Không phân trang (lấy toàn bộ kết quả phù hợp, pageSize = Integer.MAX_VALUE).
 * - Sắp xếp theo tỉnh/thành của đối tác đứng tên (sortByProvince = true).
 * - Hợp đồng Mua không lọc theo tỉnh (đối tác là nhà cung cấp không phân theo địa bàn).
 * - Định dạng các cột theo chuẩn BR-16.
 */
public class ContractExportService extends BaseService {

    public static final String[] CONTRACT_EXCEL_HEADERS = {
        "STT", "Mã HĐ", "Phụ lục của", "Tiêu đề", "Loại HĐ", "Tỉnh/Thành phố", "Khách hàng",
        "Người phụ trách", "Ngày ký", "Ngày hiệu lực", "Ngày kết thúc", "Trạng thái", "Tiến độ"
    };

    private final ContractDAO contractDAO;

    public ContractExportService() {
        this(new ContractDAO());
    }

    public ContractExportService(ContractDAO contractDAO) {
        this.contractDAO = contractDAO != null ? contractDAO : new ContractDAO();
    }

    public ContractDAO getContractDAO() {
        return contractDAO;
    }

    /**
     * Tiền tố tên tệp xuất Excel: hợp đồng mua -> hop_dong_mua, hợp đồng bán -> hop_dong_ban.
     */
    public String getExportFileNamePrefix(String direction) {
        if (direction == null) {
            return "hop_dong_ban";
        }
        return ContractService.DIRECTION_BUY.equalsIgnoreCase(direction)
                || "buy".equalsIgnoreCase(direction) ? "hop_dong_mua" : "hop_dong_ban";
    }

    /**
     * Tên tỉnh/thành của hợp đồng = tỉnh của khách hàng đứng tên.
     * Bỏ trống hoặc chưa có địa chỉ thì ghi "Chưa xác định".
     */
    public String provinceNameOf(Contract contract) {
        if (contract == null) {
            return "Chưa xác định";
        }
        Enterprise enterprise = contract.getEnterprise();
        Address address = enterprise != null ? enterprise.getAddress() : null;
        District district = address != null ? address.getDistrict() : null;
        Province province = district != null ? district.getProvince() : null;
        return province != null && province.getShortName() != null ? province.getShortName() : "Chưa xác định";
    }

    /**
     * Chuyển đổi danh sách hợp đồng sang các dòng dữ liệu Excel (UC-41, BR-16).
     */
    public List<Object[]> buildExportRows(List<Contract> contracts) {
        List<Object[]> rows = new ArrayList<>();
        if (contracts == null) {
            return rows;
        }
        int stt = 1;
        for (Contract c : contracts) {
            if (c == null) {
                continue;
            }
            rows.add(new Object[]{
                stt++,
                // BR-58: chưa có mã thì ghi "Chưa có số"; hợp đồng gốc để trống cột mã gốc.
                c.getDisplayCode(),
                c.getParentDisplayCode() != null ? c.getParentDisplayCode() : "",
                c.getTitle() != null ? c.getTitle() : "",
                c.getContractType() != null ? c.getContractType() : "",
                provinceNameOf(c),
                c.getEnterprise() != null && c.getEnterprise().getEnterpriseName() != null ? c.getEnterprise().getEnterpriseName() : "",
                c.getOwner() != null && c.getOwner().getFullName() != null ? c.getOwner().getFullName() : "",
                c.getSigningDate() != null ? c.getSigningDate().toString() : "",
                c.getEffectiveDate() != null ? c.getEffectiveDate().toString() : "",
                c.getEndDate() != null ? c.getEndDate().toString() : "",
                c.getStatus() != null ? c.getStatus() : "",
                c.getProgressStatus() != null ? c.getProgressStatus() : ""
            });
        }
        return rows;
    }

    /**
     * Xuất danh sách hợp đồng ra OutputStream theo định dạng Excel (.xls).
     */
    public void exportContractsToExcel(OutputStream outputStream, List<Contract> contracts) throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        List<Object[]> rows = buildExportRows(contracts);
        ExcelUtil.writeWorkbook(outputStream, CONTRACT_EXCEL_HEADERS, rows);
    }

    /**
     * Truy vấn hợp đồng từ CSDL theo bộ lọc (không phân trang, sắp theo tỉnh/thành) và xuất ra Excel.
     * {@code scope} mang cả bộ lọc bước giao hàng (BR-61) nếu danh sách đang lọc theo nó.
     */
    public void exportContractsToExcel(OutputStream outputStream, String keyword, String statusFilter,
            String typeFilter, List<Integer> provinceFilters, Period period, String direction,
            String progressFilter, boolean rootsOnly, Integer waitingDept, ListScope scope) throws IOException {
        List<Integer> effectiveProvinceFilters = (ContractService.DIRECTION_BUY.equalsIgnoreCase(direction)
                || "buy".equalsIgnoreCase(direction)) ? List.of() : provinceFilters;
        List<Contract> all = contractDAO.findAll(1, Integer.MAX_VALUE, keyword, statusFilter, typeFilter,
                effectiveProvinceFilters, true, period, direction, progressFilter, rootsOnly, waitingDept, scope);
        exportContractsToExcel(outputStream, all);
    }
}
