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

@Entity
@Data
@Table(name = "payslips")
public class Payslip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Column(nullable = false)
    private String tenantSegment;

    private String designation;

    private String department;

    @Column(precision = 12, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal hra = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal transportAllowance = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal otherAllowance = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal taxDeduction = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal pfDeduction = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal otherDeduction = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal leaveDeduction = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer paymentMonth;

    @Column(nullable = false)
    private Integer paymentYear;

    private String bankAccount;

    private LocalDateTime createdAt = LocalDateTime.now();
}
