package com.heat.supply.domain;

import java.math.BigDecimal;
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

    /** 热表编号 */
    @Column(name = "meter_no", unique = true, length = 32)
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

    @OneToMany(mappedBy = "resident", fetch = FetchType.LAZY)
    private List<RoomTempSample> samples = new ArrayList<>();
}
