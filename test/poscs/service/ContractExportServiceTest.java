package poscs.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import poscs.common.ListScope;
import poscs.common.Period;
import poscs.dao.ContractDAO;
import poscs.model.Address;
import poscs.model.Contract;
import poscs.model.District;
import poscs.model.Enterprise;
import poscs.model.Province;
import poscs.model.User;

public class ContractExportServiceTest {

    private ContractDAO contractDAO;
    private ContractExportService service;

    @Before
    public void setUp() {
        contractDAO = mock(ContractDAO.class);
        service = new ContractExportService(contractDAO);
    }

    @Test
    public void constructor_defaultInitializesDAO() {
        ContractExportService defaultService = new ContractExportService();
        assertNotNull(defaultService.getContractDAO());
        ContractExportService nullDaoService = new ContractExportService(null);
        assertNotNull(nullDaoService.getContractDAO());
    }

    @Test
    public void headers_correctBr16Columns() {
        String[] headers = ContractExportService.CONTRACT_EXCEL_HEADERS;
        assertEquals(13, headers.length);
        assertArrayEquals(new String[]{
            "STT", "Mã HĐ", "Phụ lục của", "Tiêu đề", "Loại HĐ", "Tỉnh/Thành phố", "Khách hàng",
            "Người phụ trách", "Ngày ký", "Ngày hiệu lực", "Ngày kết thúc", "Trạng thái", "Tiến độ"
        }, headers);
    }

    @Test
    public void getExportFileNamePrefix_buyDirection() {
        assertEquals("hop_dong_mua", service.getExportFileNamePrefix("Mua"));
        assertEquals("hop_dong_mua", service.getExportFileNamePrefix("mua"));
        assertEquals("hop_dong_mua", service.getExportFileNamePrefix("buy"));
        assertEquals("hop_dong_mua", service.getExportFileNamePrefix("BUY"));
    }

    @Test
    public void getExportFileNamePrefix_sellOrOtherOrNull() {
        assertEquals("hop_dong_ban", service.getExportFileNamePrefix("Bán"));
        assertEquals("hop_dong_ban", service.getExportFileNamePrefix("bán"));
        assertEquals("hop_dong_ban", service.getExportFileNamePrefix("sell"));
        assertEquals("hop_dong_ban", service.getExportFileNamePrefix(null));
        assertEquals("hop_dong_ban", service.getExportFileNamePrefix(""));
        assertEquals("hop_dong_ban", service.getExportFileNamePrefix("unknown"));
    }

    @Test
    public void provinceNameOf_handlesNullHierarchyGracefully() {
        assertEquals("Chưa xác định", service.provinceNameOf(null));

        Contract c = new Contract();
        assertEquals("Chưa xác định", service.provinceNameOf(c));

        Enterprise e = new Enterprise();
        c.setEnterprise(e);
        assertEquals("Chưa xác định", service.provinceNameOf(c));

        Address a = new Address();
        e.setAddress(a);
        assertEquals("Chưa xác định", service.provinceNameOf(c));

        District d = new District();
        a.setDistrict(d);
        assertEquals("Chưa xác định", service.provinceNameOf(c));

        Province p = new Province();
        d.setProvince(p);
        assertEquals("Chưa xác định", service.provinceNameOf(c));

        p.setProvinceName("Tỉnh Đà Nẵng");
        assertEquals("Đà Nẵng", service.provinceNameOf(c));
    }

    @Test
    public void buildExportRows_nullAndEmptyList() {
        assertTrue(service.buildExportRows(null).isEmpty());
        assertTrue(service.buildExportRows(List.of()).isEmpty());
    }

    @Test
    public void buildExportRows_skipsNullElements() {
        List<Contract> list = new ArrayList<>();
        list.add(null);
        Contract c = new Contract();
        c.setContractCode("HD-001");
        list.add(c);
        list.add(null);

        List<Object[]> rows = service.buildExportRows(list);
        assertEquals(1, rows.size());
        assertEquals(1, rows.get(0)[0]);
        assertEquals("HD-001", rows.get(0)[1]);
    }

    @Test
    public void buildExportRows_singleContractFullFields() {
        Contract c = new Contract();
        c.setContractCode("HD-2026-01");
        c.setParentContractCode("HD-ROOT");
        c.setTitle("Cung cấp thiết bị POS");
        c.setContractType("Trọn gói");

        Enterprise e = new Enterprise();
        e.setEnterpriseName("Công ty VNPT");
        Address a = new Address();
        District d = new District();
        Province p = new Province();
        p.setProvinceName("Thành phố Hà Nội");
        d.setProvince(p);
        a.setDistrict(d);
        e.setAddress(a);
        c.setEnterprise(e);

        User owner = new User();
        owner.setLastName("Nguyễn");
        owner.setFirstName("Văn A");
        c.setOwner(owner);

        c.setSigningDate(Date.valueOf("2026-01-10"));
        c.setEffectiveDate(Date.valueOf("2026-01-15"));
        c.setEndDate(Date.valueOf("2026-12-31"));
        c.setStatus("Đang hiệu lực");
        c.setProgressStatus("Đang thực hiện");

        List<Object[]> rows = service.buildExportRows(List.of(c));
        assertEquals(1, rows.size());
        Object[] row = rows.get(0);
        assertEquals(1, row[0]);
        assertEquals("HD-2026-01", row[1]);
        assertEquals("HD-ROOT", row[2]);
        assertEquals("Cung cấp thiết bị POS", row[3]);
        assertEquals("Trọn gói", row[4]);
        assertEquals("Hà Nội", row[5]);
        assertEquals("Công ty VNPT", row[6]);
        assertEquals("Nguyễn Văn A", row[7]);
        assertEquals("2026-01-10", row[8]);
        assertEquals("2026-01-15", row[9]);
        assertEquals("2026-12-31", row[10]);
        assertEquals("Đang hiệu lực", row[11]);
        assertEquals("Đang thực hiện", row[12]);
    }

    @Test
    public void buildExportRows_nullFields_usesEmptyStrings() {
        Contract c = new Contract();
        List<Object[]> rows = service.buildExportRows(List.of(c));
        assertEquals(1, rows.size());
        Object[] row = rows.get(0);
        assertEquals(1, row[0]);
        assertEquals("", row[1]); // code
        assertEquals("", row[2]); // parent code
        assertEquals("", row[3]); // title
        assertEquals("", row[4]); // type
        assertEquals("Chưa xác định", row[5]); // province
        assertEquals("", row[6]); // enterprise
        assertEquals("", row[7]); // owner
        assertEquals("", row[8]); // signing date
        assertEquals("", row[9]); // effective date
        assertEquals("", row[10]); // end date
        assertEquals("", row[11]); // status
        assertEquals("", row[12]); // progress
    }

    @Test
    public void buildExportRows_multipleContracts_sttIncrements() {
        Contract c1 = new Contract();
        c1.setContractCode("HD-01");
        Contract c2 = new Contract();
        c2.setContractCode("HD-02");
        Contract c3 = new Contract();
        c3.setContractCode("HD-03");

        List<Object[]> rows = service.buildExportRows(List.of(c1, c2, c3));
        assertEquals(3, rows.size());
        assertEquals(1, rows.get(0)[0]);
        assertEquals(2, rows.get(1)[0]);
        assertEquals(3, rows.get(2)[0]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void exportContractsToExcel_nullOutputStream_throwsException() throws IOException {
        service.exportContractsToExcel(null, List.of());
    }

    @Test
    public void exportContractsToExcel_withContractsList_writesValidExcel() throws IOException {
        Contract c = new Contract();
        c.setContractCode("HD-TEST");
        c.setTitle("Hợp đồng thử nghiệm");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.exportContractsToExcel(out, List.of(c));
        byte[] bytes = out.toByteArray();
        assertTrue(bytes.length > 0);

        try (Workbook wb = new HSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Data");
            assertNotNull(sheet);
            Row headerRow = sheet.getRow(0);
            assertNotNull(headerRow);
            assertEquals("STT", headerRow.getCell(0).getStringCellValue());
            assertEquals("Mã HĐ", headerRow.getCell(1).getStringCellValue());

            Row dataRow = sheet.getRow(1);
            assertNotNull(dataRow);
            assertEquals(1.0, dataRow.getCell(0).getNumericCellValue(), 0.001);
            assertEquals("HD-TEST", dataRow.getCell(1).getStringCellValue());
            assertEquals("Hợp đồng thử nghiệm", dataRow.getCell(3).getStringCellValue());
        }
    }

    @Test
    public void exportContractsToExcel_buyDirection_forcesEmptyProvinceFilters() throws IOException {
        List<Contract> mockList = List.of();
        when(contractDAO.findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), anyBoolean(), any(), any())).thenReturn(mockList);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> provinceFilters = List.of(1, 2, 3);
        Period period = Period.parse("2026", "q1");
        ListScope scope = ListScope.all();

        service.exportContractsToExcel(out, "kw", "ACTIVE", "SERVICE", provinceFilters,
                period, "Mua", "IN_PROGRESS", true, 5, scope);

        verify(contractDAO).findAll(
                eq(1),
                eq(Integer.MAX_VALUE),
                eq("kw"),
                eq("ACTIVE"),
                eq("SERVICE"),
                eq(List.of()), // provinceFilters forced to empty
                eq(true),      // sortByProvince is always true
                eq(period),
                eq("Mua"),
                eq("IN_PROGRESS"),
                eq(true),
                eq(5),
                eq(scope)
        );
    }

    @Test
    public void exportContractsToExcel_sellDirection_retainsProvinceFilters() throws IOException {
        List<Contract> mockList = List.of();
        when(contractDAO.findAll(anyInt(), anyInt(), any(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), anyBoolean(), any(), any())).thenReturn(mockList);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> provinceFilters = List.of(10, 20);
        Period period = Period.parse("2026", "m3");
        ListScope scope = ListScope.all();

        service.exportContractsToExcel(out, "kw", "ACTIVE", "SERVICE", provinceFilters,
                period, "Bán", "IN_PROGRESS", false, null, scope);

        verify(contractDAO).findAll(
                eq(1),
                eq(Integer.MAX_VALUE),
                eq("kw"),
                eq("ACTIVE"),
                eq("SERVICE"),
                eq(provinceFilters), // retained
                eq(true),            // sortByProvince is true
                eq(period),
                eq("Bán"),
                eq("IN_PROGRESS"),
                eq(false),
                isNull(),
                eq(scope)
        );
    }
}
