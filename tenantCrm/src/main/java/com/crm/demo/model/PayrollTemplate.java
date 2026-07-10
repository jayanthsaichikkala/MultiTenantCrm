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
import lombok.Data;

/**
 * Payroll template — stores salary structure for each employee.
 * One record per employee. HR can create / edit; no actual payslip processing.
 */
@Entity
@Data
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
        return safeVal(basicSalary)
                .add(safeVal(hra))
                .add(safeVal(transportAllowance))
                .add(safeVal(otherAllowance));
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
}
