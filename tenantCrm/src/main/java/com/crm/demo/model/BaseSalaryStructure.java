package com.crm.demo.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Data;
import java.math.BigDecimal;

/**
 * Base class containing common salary structure fields for both PayrollTemplate and Payslip.
 */
@MappedSuperclass
@Data
public abstract class BaseSalaryStructure {

    /** Job title / designation. */
    protected String designation;

    /** Department name. */
    protected String department;

    /** Basic monthly salary. */
    @Column(precision = 12, scale = 2)
    protected BigDecimal basicSalary = BigDecimal.ZERO;

    /** House Rent Allowance. */
    @Column(precision = 12, scale = 2)
    protected BigDecimal hra = BigDecimal.ZERO;

    /** Transport / conveyance allowance. */
    @Column(precision = 12, scale = 2)
    protected BigDecimal transportAllowance = BigDecimal.ZERO;

    /** Any other allowance. */
    @Column(precision = 12, scale = 2)
    protected BigDecimal otherAllowance = BigDecimal.ZERO;

    /** Tax deduction. */
    @Column(precision = 12, scale = 2)
    protected BigDecimal taxDeduction = BigDecimal.ZERO;

    /** Provident fund deduction. */
    @Column(precision = 12, scale = 2)
    protected BigDecimal pfDeduction = BigDecimal.ZERO;

    /** Any other deduction. */
    @Column(precision = 12, scale = 2)
    protected BigDecimal otherDeduction = BigDecimal.ZERO;

    /** Bank account number. */
    protected String bankAccount;
}
