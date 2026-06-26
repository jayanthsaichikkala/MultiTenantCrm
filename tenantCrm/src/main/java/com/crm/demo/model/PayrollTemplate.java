package com.crm.demo.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Payroll template — stores salary structure for each employee.
 * One record per employee. HR can create / edit; no actual payslip processing.
 */
@Entity
@Table(name = "payroll_template")
public class PayrollTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The employee this template belongs to. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    /** Tenant segment (email-derived) for multi-tenant isolation. */
    @Column(nullable = false)
    private String tenantSegment;

    /** Job title / designation shown on payslip. */
    private String designation;

    /** Department name. */
    private String department;

    /** Basic monthly salary. */
    @Column(precision = 12, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    /** House Rent Allowance. */
    @Column(precision = 12, scale = 2)
    private BigDecimal hra = BigDecimal.ZERO;

    /** Transport / conveyance allowance. */
    @Column(precision = 12, scale = 2)
    private BigDecimal transportAllowance = BigDecimal.ZERO;

    /** Any other allowance. */
    @Column(precision = 12, scale = 2)
    private BigDecimal otherAllowance = BigDecimal.ZERO;

    /** Tax deduction. */
    @Column(precision = 12, scale = 2)
    private BigDecimal taxDeduction = BigDecimal.ZERO;

    /** Provident fund deduction. */
    @Column(precision = 12, scale = 2)
    private BigDecimal pfDeduction = BigDecimal.ZERO;

    /** Any other deduction. */
    @Column(precision = 12, scale = 2)
    private BigDecimal otherDeduction = BigDecimal.ZERO;

    /** Bank account number (optional). */
    private String bankAccount;

    /** Payment month for which this was last computed (1-12). */
    private Integer paymentMonth;

    /** Payment year. */
    private Integer paymentYear;

    @Column(nullable = false, columnDefinition = "VARCHAR(20) DEFAULT 'active'")
    private String status = "active";   // active | inactive

    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Derived helpers ────────────────────────────────────────────────────

    public BigDecimal getGrossSalary() {
        BigDecimal gross = safeVal(basicSalary)
                .add(safeVal(hra))
                .add(safeVal(transportAllowance))
                .add(safeVal(otherAllowance));
        return gross;
    }

    public BigDecimal getTotalDeductions() {
        return safeVal(taxDeduction).add(safeVal(pfDeduction)).add(safeVal(otherDeduction));
    }

    public BigDecimal getNetSalary() {
        return getGrossSalary().subtract(getTotalDeductions());
    }

    private BigDecimal safeVal(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getEmployee() { return employee; }
    public void setEmployee(User employee) { this.employee = employee; }

    public String getTenantSegment() { return tenantSegment; }
    public void setTenantSegment(String tenantSegment) { this.tenantSegment = tenantSegment; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public BigDecimal getBasicSalary() { return basicSalary != null ? basicSalary : BigDecimal.ZERO; }
    public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = basicSalary; }

    public BigDecimal getHra() { return hra != null ? hra : BigDecimal.ZERO; }
    public void setHra(BigDecimal hra) { this.hra = hra; }

    public BigDecimal getTransportAllowance() { return transportAllowance != null ? transportAllowance : BigDecimal.ZERO; }
    public void setTransportAllowance(BigDecimal transportAllowance) { this.transportAllowance = transportAllowance; }

    public BigDecimal getOtherAllowance() { return otherAllowance != null ? otherAllowance : BigDecimal.ZERO; }
    public void setOtherAllowance(BigDecimal otherAllowance) { this.otherAllowance = otherAllowance; }

    public BigDecimal getTaxDeduction() { return taxDeduction != null ? taxDeduction : BigDecimal.ZERO; }
    public void setTaxDeduction(BigDecimal taxDeduction) { this.taxDeduction = taxDeduction; }

    public BigDecimal getPfDeduction() { return pfDeduction != null ? pfDeduction : BigDecimal.ZERO; }
    public void setPfDeduction(BigDecimal pfDeduction) { this.pfDeduction = pfDeduction; }

    public BigDecimal getOtherDeduction() { return otherDeduction != null ? otherDeduction : BigDecimal.ZERO; }
    public void setOtherDeduction(BigDecimal otherDeduction) { this.otherDeduction = otherDeduction; }

    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

    public Integer getPaymentMonth() { return paymentMonth; }
    public void setPaymentMonth(Integer paymentMonth) { this.paymentMonth = paymentMonth; }

    public Integer getPaymentYear() { return paymentYear; }
    public void setPaymentYear(Integer paymentYear) { this.paymentYear = paymentYear; }

    public String getStatus() { return status != null ? status : "active"; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
