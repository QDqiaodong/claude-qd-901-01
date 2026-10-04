package com.heat.supply.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 采暖费收缴台账：收费年度、用户、计费面积、单价、应收/实收/欠费、缴费状态。
 */
@Entity
@Table(name = "heating_fee")
@Getter
@Setter
public class HeatingFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 收费年度 */
    @Column(name = "fee_year")
    private Integer feeYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    /** 计费面积（㎡） */
    @Column(name = "billing_area", precision = 8, scale = 2)
    private BigDecimal billingArea;

    /** 单价（元/㎡） */
    @Column(name = "unit_price", precision = 6, scale = 2)
    private BigDecimal unitPrice;

    /** 应收金额（元） */
    @Column(name = "receivable", precision = 12, scale = 2)
    private BigDecimal receivable;

    /** 实收金额（元） */
    @Column(name = "paid", precision = 12, scale = 2)
    private BigDecimal paid;

    /** 欠费金额（元） */
    @Column(name = "arrears", precision = 12, scale = 2)
    private BigDecimal arrears;

    /** 缴费状态：已缴 / 部分 / 欠费 */
    @Column(name = "pay_status", length = 16)
    private String payStatus;

    @Column(name = "last_pay_date")
    private LocalDate lastPayDate;

    @Column(name = "remark", length = 128)
    private String remark;

    /** 待财务复核：同一年度两边都有实收时置 1，复核完成前禁止登记该年度缴费 */
    @Column(name = "review_flag", nullable = false)
    private boolean reviewFlag = false;

    /** 归并作废：原重复档案台账行物理保留、金额冻结，不进台账/欠费统计 */
    @Column(name = "voided", nullable = false)
    private boolean voided = false;

    /** 作废原因（含归并单号） */
    @Column(name = "void_reason", length = 255)
    private String voidReason;
}
