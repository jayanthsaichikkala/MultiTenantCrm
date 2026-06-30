package com.crm.demo.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private PayrollTemplateRepository payrollTemplateRepository;

    @Autowired
    private PayslipRepository payslipRepository;

    @Autowired
    private UserRepository userRepository;

    public int countApprovedLeaveDays(User user, String leaveType, int month, int year) {
        List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeOrderByCreatedAtDesc(user);
        int count = 0;
        LocalDate startOfMonth = LocalDate.of(year, month, 1);
        LocalDate endOfMonth = startOfMonth.plusMonths(1).minusDays(1);
        for (LeaveRequest leave : leaves) {
            if (!"Approved".equalsIgnoreCase(leave.getStatus()) || !leaveType.equalsIgnoreCase(leave.getType())) {
                continue;
            }
            LocalDate start = leave.getFromDate();
            LocalDate end = leave.getToDate();
            if (start == null || end == null) {
                continue;
            }
            // Overlap between [start, end] and [startOfMonth, endOfMonth]
            LocalDate overlapStart = start.isBefore(startOfMonth) ? startOfMonth : start;
            LocalDate overlapEnd = end.isAfter(endOfMonth) ? endOfMonth : end;
            if (!overlapStart.isAfter(overlapEnd)) {
                count += (int) ChronoUnit.DAYS.between(overlapStart, overlapEnd) + 1;
            }
        }
        return count;
    }

    public BigDecimal calculateLeaveDeduction(User user, BigDecimal basicSalary, int month, int year) {
        if (basicSalary == null || basicSalary.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        int sickDays = countApprovedLeaveDays(user, "Sick Leave", month, year);
        int casualDays = countApprovedLeaveDays(user, "Casual Leave", month, year);

        int unpaidSickDays = Math.max(0, sickDays - 1);
        int unpaidCasualDays = Math.max(0, casualDays - 1);

        int totalUnpaidDays = unpaidSickDays + unpaidCasualDays;
        if (totalUnpaidDays <= 0) {
            return BigDecimal.ZERO;
        }

        // Daily Rate = Basic Salary / 30
        BigDecimal dailyRate = basicSalary.divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);
        return dailyRate.multiply(new BigDecimal(totalUnpaidDays));
    }

    @Transactional
    public int generatePayslipsForTenant(String tenant, int month, int year) {
        List<PayrollTemplate> templates = payrollTemplateRepository.findByTenantSegmentOrderByCreatedAtDesc(tenant);
        int generatedCount = 0;

        for (PayrollTemplate pt : templates) {
            User employee = pt.getEmployee();
            if (employee == null) {
                continue;
            }

            // Check if payslip already exists for this period
            Optional<Payslip> existing = payslipRepository.findByEmployeeAndPaymentMonthAndPaymentYear(employee, month, year);
            if (existing.isPresent()) {
                continue;
            }

            // Skip generation if the target period is before the employee's joining date (by month/year)
            if (employee.getJoiningDate() != null) {
                java.time.LocalDate periodDate = java.time.LocalDate.of(year, month, 1);
                java.time.LocalDate joiningMonthStart = java.time.LocalDate.of(
                    employee.getJoiningDate().getYear(),
                    employee.getJoiningDate().getMonthValue(),
                    1
                );
                if (periodDate.isBefore(joiningMonthStart)) {
                    continue;
                }
            }

            Payslip payslip = new Payslip();
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
            BigDecimal leaveDeduction = calculateLeaveDeduction(employee, pt.getBasicSalary(), month, year);
            payslip.setLeaveDeduction(leaveDeduction);

            // Gross salary = Basic + Allowances
            BigDecimal gross = pt.getGrossSalary();
            payslip.setGrossSalary(gross);

            // Net salary = Gross - Deductions - Leave Deduction
            BigDecimal totalDeductions = pt.getTotalDeductions().add(leaveDeduction);
            BigDecimal net = gross.subtract(totalDeductions);
            if (net.compareTo(BigDecimal.ZERO) < 0) {
                net = BigDecimal.ZERO;
            }
            payslip.setNetSalary(net);

            payslip.setPaymentMonth(month);
            payslip.setPaymentYear(year);
            payslip.setBankAccount(pt.getBankAccount());
            payslip.setCreatedAt(LocalDateTime.now());

            payslipRepository.save(payslip);
            generatedCount++;
        }
        return generatedCount;
    }

    @Transactional
    public void generateAllPayslips(int month, int year) {
        List<User> users = userRepository.findAll();
        List<String> tenants = users.stream()
                .map(this::getTenantSegmentFromUser)
                .filter(t -> !t.isEmpty() && !"crm".equalsIgnoreCase(t))
                .distinct()
                .toList();

        for (String tenant : tenants) {
            generatePayslipsForTenant(tenant, month, year);
        }
    }

    private String getTenantSegmentFromUser(User u) {
        if (u == null || u.getEmail() == null) {
            return "";
        }
        String email = u.getEmail();
        int atIdx = email.indexOf('@');
        if (atIdx == -1) {
            return "";
        }
        String domain = email.substring(atIdx + 1);
        int dotIdx = domain.indexOf('.');
        if (dotIdx == -1) {
            return domain;
        }
        return domain.substring(0, dotIdx);
    }
}
