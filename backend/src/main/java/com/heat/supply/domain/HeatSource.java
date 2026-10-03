package com.heat.supply.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.FetchProfile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 热源厂：装机容量、燃料类型、运行状态，下辖若干换热站。
 * 抓取档案 {@link #FETCH_WITH_STATIONS} 把 stations 改成 JOIN 抓取。
 */
@Entity
@Table(name = "heat_source")
@Getter
@Setter
@FetchProfile(name = HeatSource.FETCH_WITH_STATIONS,
        fetchOverrides = @FetchProfile.FetchOverride(entity = HeatSource.class,
                association = "stations", mode = FetchMode.JOIN))
public class HeatSource {

    /** 命名抓取档案：查热源时把换热站集合改成 JOIN 抓取 */
    public static final String FETCH_WITH_STATIONS = "with-stations";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_name", nullable = false, unique = true, length = 64)
    private String name;

    /** 装机容量（MW） */
    @Column(name = "capacity_mw", precision = 10, scale = 2)
    private BigDecimal capacityMw;

    /** 燃料类型：燃煤 / 燃气 / 热电联产 */
    @Column(name = "fuel_type", length = 16)
    private String fuelType;

    /** 状态：运行 / 检修 / 停用 */
    @Column(name = "source_status", length = 16)
    private String status;

    @Column(name = "manager", length = 32)
    private String manager;

    @Column(name = "address", length = 128)
    private String address;

    @Column(name = "commission_date")
    private LocalDate commissionDate;

    /** 出厂管接入一次网的根井号（拓扑试算的注水起点），为空表示该热源不直接注入管网 */
    @Column(name = "root_well_no", length = 32)
    private String rootWellNo;

    @OneToMany(mappedBy = "heatSource", fetch = FetchType.LAZY)
    private List<HeatStation> stations = new ArrayList<>();
}
