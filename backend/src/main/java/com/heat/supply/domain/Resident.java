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

    /**
     * 热表编号：业务上允许出现重复建档（同一热表录成两档），故这里不再加唯一约束，
     * 由归并预检/后端服务保证在档用户的热表号不重复。
     */
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

    /** 是否已归并（只读重复档案）：归并后该档案禁止一切业务登记 */
    @Column(name = "merged", nullable = false)
    private boolean merged = false;

    /** 归并落点：保留档案 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_into_id")
    private Resident mergedInto;

    /** 归并确认时间 */
    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    /** 归并单号 GB-yyyyMMdd-序号 */
    @Column(name = "merge_no", length = 32)
    private String mergeNo;

    @OneToMany(mappedBy = "resident", fetch = FetchType.LAZY)
    private List<RoomTempSample> samples = new ArrayList<>();

    /** 归并只读判断 */
    public boolean isMergedProfile() {
        return merged;
    }
}
