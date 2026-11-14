package poscs.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import poscs.common.Period;
import poscs.model.Contract;
import poscs.model.Enterprise;
import poscs.model.Province;

/**
 * Dữ liệu tổng hợp truyền từ {@link DashboardService} sang
 * {@link poscs.controller.DashboardController} / {@code dashboard.jsp}.
 */
public final class DashboardSummary {

    private final List<Province> provinceList;
    private final Period period;
    private final String scopeFilter;
    private final int teamSize;
    private final List<Province> myProvinces;
    private final List<Integer> provinceFilters;
    private final boolean provinceFilterIsMine;
    private final int totalCustomers;
    private final int newCustomersThisMonth;
    private final Map<String, Integer> contractStatusSummary;
    private final BigDecimal revenueThisMonth;
    private final Double revenueTrendPercent;
    private final int currentMonthNumber;
    private final String todayLabel;
    private final String contractWindowLabel;
    private final List<Contract> windowContracts;
    private final Map<Integer, BigDecimal> contractValues;
    private final List<Enterprise> scopeCustomers;

    public DashboardSummary(List<Province> provinceList, Period period, String scopeFilter, int teamSize,
            List<Province> myProvinces, List<Integer> provinceFilters, boolean provinceFilterIsMine,
            int totalCustomers, int newCustomersThisMonth, Map<String, Integer> contractStatusSummary,
            BigDecimal revenueThisMonth, Double revenueTrendPercent, int currentMonthNumber,
            String todayLabel, String contractWindowLabel, List<Contract> windowContracts,
            Map<Integer, BigDecimal> contractValues, List<Enterprise> scopeCustomers) {
        this.provinceList = provinceList;
        this.period = period;
        this.scopeFilter = scopeFilter;
        this.teamSize = teamSize;
        this.myProvinces = myProvinces;
        this.provinceFilters = provinceFilters;
        this.provinceFilterIsMine = provinceFilterIsMine;
        this.totalCustomers = totalCustomers;
        this.newCustomersThisMonth = newCustomersThisMonth;
        this.contractStatusSummary = contractStatusSummary;
        this.revenueThisMonth = revenueThisMonth;
        this.revenueTrendPercent = revenueTrendPercent;
        this.currentMonthNumber = currentMonthNumber;
        this.todayLabel = todayLabel;
        this.contractWindowLabel = contractWindowLabel;
        this.windowContracts = windowContracts;
        this.contractValues = contractValues;
        this.scopeCustomers = scopeCustomers;
    }

    public List<Province> getProvinceList() { return provinceList; }
    public Period getPeriod() { return period; }
    public String getScopeFilter() { return scopeFilter; }
    public int getTeamSize() { return teamSize; }
    public List<Province> getMyProvinces() { return myProvinces; }
    public List<Integer> getProvinceFilters() { return provinceFilters; }
    public boolean isProvinceFilterIsMine() { return provinceFilterIsMine; }
    public int getTotalCustomers() { return totalCustomers; }
    public int getNewCustomersThisMonth() { return newCustomersThisMonth; }
    public Map<String, Integer> getContractStatusSummary() { return contractStatusSummary; }
    public BigDecimal getRevenueThisMonth() { return revenueThisMonth; }
    public Double getRevenueTrendPercent() { return revenueTrendPercent; }
    public int getCurrentMonthNumber() { return currentMonthNumber; }
    public String getTodayLabel() { return todayLabel; }
    public String getContractWindowLabel() { return contractWindowLabel; }
    public List<Contract> getWindowContracts() { return windowContracts; }
    public Map<Integer, BigDecimal> getContractValues() { return contractValues; }
    public List<Enterprise> getScopeCustomers() { return scopeCustomers; }
}
