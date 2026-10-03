package com.heat.supply.domain;

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
 * 报修工单：工单号、报修换热站/用户、故障类型、派工人、处理结果、状态。
 */
@Entity
@Table(name = "repair_order")
@Getter
@Setter
public class RepairOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 工单号 */
    @Column(name = "order_no", nullable = false, unique = true, length = 32)
    private String orderNo;

    /** 报修日期 */
    @Column(name = "reported_date")
    private LocalDate reportedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private HeatStation station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id")
    private Resident resident;

    /** 故障类型：不热 / 漏水 / 异响 / 气堵 */
    @Column(name = "fault_type", length = 16)
    private String faultType;

    /** 派工人 */
    @Column(name = "assignee", length = 32)
    private String assignee;

    /** 处理结果 */
    @Column(name = "handle_result", length = 255)
    private String handleResult;

    /** 状态：待派工 / 处理中 / 已闭环 */
    @Column(name = "order_status", length = 16)
    private String orderStatus;

    @Column(name = "closed_date")
    private LocalDate closedDate;
}
