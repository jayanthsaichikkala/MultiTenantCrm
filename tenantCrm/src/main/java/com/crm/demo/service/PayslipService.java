package com.crm.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crm.demo.model.LeaveRequest;
import com.crm.demo.model.PayrollTemplate;
import com.crm.demo.model.Payslip;
import com.crm.demo.model.User;
import com.crm.demo.repository.LeaveRequestRepository;
import com.crm.demo.repository.PayrollTemplateRepository;
import com.crm.demo.repository.PayslipRepository;
import com.crm.demo.repository.UserRepository;

@Service
public class PayslipService {

    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollTemplateRepository payrollTemplateRepository;
    private final PayslipRepository payslipRepository;
    private final UserRepository userRepository;

    public PayslipService(LeaveRequestRepository leaveRequestRepository,
                          PayrollTemplateRepository payrollTemplateRepository,
                          PayslipRepository payslipRepository,
                          UserRepository userRepository) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.payrollTemplateRepository = payrollTemplateRepository;
        this.payslipRepository = payslipRepository;
        this.userRepository = userRepository;
    }

    public int countApprovedLeaveDays(User user, String leaveType, int month, int year) {
        var leaves = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user);
        var count = 0;
        var startOfMonth = LocalDate.of(year, month, 1);
        var endOfMonth = startOfMonth.plusMonths(1).minusDays(1);
        for (LeaveRequest leave : leaves) {
            if ("Approved".equalsIgnoreCase(leave.getStatus()) && leaveType.equalsIgnoreCase(leave.getType())) {
                count += calculateOverlapDays(leave, startOfMonth, endOfMonth);
            }
        }
        return count;
    }

    private int calculateOverlapDays(LeaveRequest leave, LocalDate startOfMonth, LocalDate endOfMonth) {
        var start = leave.getFromDate();
        var end = leave.getToDate();
        if (start != null && end != null) {
            // Overlap between [start, end] and [startOfMonth, endOfMonth]
            var overlapStart = start.isBefore(startOfMonth) ? startOfMonth : start;
            var overlapEnd = end.isAfter(endOfMonth) ? endOfMonth : end;
            if (!overlapStart.isAfter(overlapEnd)) {
                return (int) ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
            }
        }
        return 0;
    }

    public BigDecimal calculateLeaveDeduction(User user, BigDecimal basicSalary, int month, int year) {
        if (basicSalary == null || basicSalary.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        var sickDays = countApprovedLeaveDays(user, "Sick Leave", month, year);
        var casualDays = countApprovedLeaveDays(user, "Casual Leave", month, year);

        var unpaidSickDays = Math.max(0, sickDays - 1);
        var unpaidCasualDays = Math.max(0, casualDays - 1);

        var totalUnpaidDays = unpaidSickDays + unpaidCasualDays;
        if (totalUnpaidDays <= 0) {
            return BigDecimal.ZERO;
        }

        // Daily Rate = Basic Salary / 30
        var dailyRate = basicSalary.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        return dailyRate.multiply(new BigDecimal(totalUnpaidDays));
    }

    @Transactional
    public int generatePayslipsForTenant(String tenant, int month, int year) {
        var templates = payrollTemplateRepository.findByTenantSegmentOrderByCreatedAtDesc(tenant);
        var generatedCount = 0;

        for (PayrollTemplate pt : templates) {
            var employee = pt.getEmployee();
            if (employee == null) {
                continue;
            }

            // Check if payslip already exists for this period
            var existing = payslipRepository.findByEmployeeAndPaymentMonthAndPaymentYear(employee, month, year);
            if (existing.isEmpty() && isJoinedOnOrBefore(employee, month, year)) {
                generateAndSavePayslip(pt, employee, tenant, month, year);
                generatedCount++;
            }
        }
        return generatedCount;
    }

    private boolean isJoinedOnOrBefore(User employee, int month, int year) {
        if (employee.getJoiningDate() == null) {
            return true;
        }
        var periodDate = LocalDate.of(year, month, 1);
        var joiningMonthStart = LocalDate.of(
            employee.getJoiningDate().getYear(),
            employee.getJoiningDate().getMonthValue(),
            1
        );
        return !periodDate.isBefore(joiningMonthStart);
    }

    private void generateAndSavePayslip(PayrollTemplate pt, User employee, String tenant, int month, int year) {
        var payslip = new Payslip();
        payslip.setEmployee(employee);
        payslip.setTenantSegment(tenant);
        payslip.setDesignation(pt.getDesignation());
        payslip.setDepartment(pt.getDepartment());
        payslip.setBasicSalary(pt.getBasicSalary());
        payslip.setHra(pt.getHra());
        payslip.setTransportAllowance(pt.getTransportAllowance());
        payslip.setOtherAllowance(pt.getOtherAllowance());
        payslip.setTaxDeduction(pt.getTaxDeduction());
        payslip.setPfDeduction(pt.getPfDeduction());
        payslip.setOtherDeduction(pt.getOtherDeduction());

        // Calculate leave deductions
        var leaveDeduction = calculateLeaveDeduction(employee, pt.getBasicSalary(), month, year);
        payslip.setLeaveDeduction(leaveDeduction);

        // Gross salary = Basic + Allowances
        var gross = pt.getGrossSalary();
        payslip.setGrossSalary(gross);

        // Net salary = Gross - Deductions - Leave Deduction
        var totalDeductions = pt.getTotalDeductions().add(leaveDeduction);
        var net = gross.subtract(totalDeductions);
        if (net.compareTo(BigDecimal.ZERO) < 0) {
            net = BigDecimal.ZERO;
        }
        payslip.setNetSalary(net);

        payslip.setPaymentMonth(month);
        payslip.setPaymentYear(year);
        payslip.setBankAccount(pt.getBankAccount());
        payslip.setCreatedAt(LocalDateTime.now());

        payslipRepository.save(payslip);
    }

    @Transactional
    public void generateAllPayslips(int month, int year) {
        userRepository.findAll().stream()
                .map(this::getTenantSegmentFromUser)
                .filter(t -> !t.isEmpty() && !"crm".equalsIgnoreCase(t))
                .distinct()
                .forEach(tenant -> generatePayslipsForTenant(tenant, month, year));
    }

    private String getTenantSegmentFromUser(User u) {
        if (u == null || u.getEmail() == null) {
            return "";
        }
        var email = u.getEmail();
        var atIdx = email.indexOf('@');
        if (atIdx == -1) {
            return "";
        }
        var domain = email.substring(atIdx + 1);
        var dotIdx = domain.indexOf('.');
        if (dotIdx == -1) {
            return domain;
        }
        return domain.substring(0, dotIdx);
    }
}
