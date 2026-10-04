package com.heat.supply.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * 热用户：小区、楼栋、单元、面积、热表编号、累计用热量，挂接所属换热站。
 * 重复档案归并后，待归并档案 {@link #archived}=true 转为只读，{@link #mergedInto} 指向保留档案；
 * meter_no 因此不能再加数据库唯一约束（归档档案与保留档案同号），唯一性由应用层对未归档档案保证。
 */
@Entity
@Table(name = "resident")
@Getter
@Setter
public class Resident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 小区 */
    @Column(name = "community", length = 64)
    private String community;

    /** 楼栋 */
    @Column(name = "building_no", length = 32)
    private String buildingNo;

    /** 单元 */
    @Column(name = "unit_no", length = 32)
    private String unitNo;

    /** 房号 */
    @Column(name = "room_no", length = 32)
    private String roomNo;

    /** 建筑面积（㎡） */
    @Column(name = "area_m2", precision = 8, scale = 2)
    private BigDecimal areaM2;

    /** 热表编号（归并后保留档案与归档档案同号，由应用层对未归档档案校验唯一） */
    @Column(name = "meter_no", length = 32)
    private String meterNo;

    /** 累计用热量（GJ） */
    @Column(name = "total_heat_gj", precision = 10, scale = 2)
    private BigDecimal totalHeatGj;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private HeatStation station;

    /** 供热状态：正常供热 / 已报停 */
    @Column(name = "supply_status", length = 16)
    private String supplyStatus;

    @Column(name = "contact_phone", length = 24)
    private String contactPhone;

    /** 是否为归并后的只读归档档案 */
    @Column(name = "archived", nullable = false)
    private boolean archived = false;

    /** 归并后跳转到的保留档案 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private Resident mergedInto;

    /** 归并时间 */
    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    /** 旧档案编号（默认 R+id，按旧编号查询也只能落到保留档案） */
    @Column(name = "legacy_code", length = 32)
    private String legacyCode;

    @OneToMany(mappedBy = "resident", fetch = FetchType.LAZY)
    private List<RoomTempSample> samples = new ArrayList<>();
}
