package com.heat.supply.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 换热站：编号、所属热源、供热面积、一次侧/二次侧供回温、循环泵台数、运行状态。
 */
@Entity
@Table(name = "heat_station")
@Getter
@Setter
public class HeatStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 换热站编号 */
    @Column(name = "station_code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "station_name", length = 64)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "heat_source_id")
    private HeatSource heatSource;

    /** 供热面积（万㎡） */
    @Column(name = "area_wan_m2", precision = 10, scale = 2)
    private BigDecimal areaWanM2;

    /** 一次侧供水温度（℃） */
    @Column(name = "primary_supply_temp", precision = 5, scale = 1)
    private BigDecimal primarySupplyTemp;

    /** 一次侧回水温度（℃） */
    @Column(name = "primary_return_temp", precision = 5, scale = 1)
    private BigDecimal primaryReturnTemp;

    /** 二次侧供水温度（℃） */
    @Column(name = "secondary_supply_temp", precision = 5, scale = 1)
    private BigDecimal secondarySupplyTemp;

    /** 二次侧回水温度（℃） */
    @Column(name = "secondary_return_temp", precision = 5, scale = 1)
    private BigDecimal secondaryReturnTemp;

    /** 一次侧设计供回温差（℃） */
    @Column(name = "design_delta_t", precision = 5, scale = 1)
    private BigDecimal designDeltaT;

    /** 循环泵台数 */
    @Column(name = "pump_count")
    private Integer pumpCount;

    /** 当前运行循环泵台数 */
    @Column(name = "running_pump_count")
    private Integer runningPumpCount;

    /** 状态：运行 / 检修 / 停用 */
    @Column(name = "station_status", length = 16)
    private String status;

    @Column(name = "manager", length = 32)
    private String manager;

    @Column(name = "address", length = 128)
    private String address;

    @Column(name = "last_maintain_date")
    private LocalDate lastMaintainDate;

    /** 一次侧并网阀门井号：该井在拓扑上不可达任一运行热源即判停暖 */
    @Column(name = "tap_well_no", length = 32)
    private String tapWellNo;

    /** 该换热站带供热的用户 */
    @OneToMany(mappedBy = "station", fetch = FetchType.LAZY)
    private List<Resident> residents = new ArrayList<>();
}
