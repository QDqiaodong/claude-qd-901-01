package com.heat.supply.domain;

import java.math.BigDecimal;
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
 * 管网段：起止井号、管径、长度、敷设方式、投运年份、保温状况，以及所属阀门井。
 * 抓取档案 {@link #FETCH_WITH_VALVES} 把 valves 改成 JOIN 抓取。
 */
@Entity
@Table(name = "pipe_segment")
@Getter
@Setter
@FetchProfile(name = PipeSegment.FETCH_WITH_VALVES,
        fetchOverrides = @FetchProfile.FetchOverride(entity = PipeSegment.class,
                association = "valves", mode = FetchMode.JOIN))
public class PipeSegment {

    /** 命名抓取档案：查管段时把阀门井集合改成 JOIN 抓取 */
    public static final String FETCH_WITH_VALVES = "with-valves";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 管段编号 */
    @Column(name = "seg_code", nullable = false, unique = true, length = 32)
    private String segCode;

    /** 起点井号 */
    @Column(name = "start_well_no", length = 32)
    private String startWellNo;

    /** 终点井号 */
    @Column(name = "end_well_no", length = 32)
    private String endWellNo;

    /** 管径 DN */
    @Column(name = "diameter_dn")
    private Integer diameterDn;

    /** 长度（m） */
    @Column(name = "length_m", precision = 10, scale = 1)
    private BigDecimal lengthM;

    /** 敷设方式：直埋 / 架空 / 管沟 */
    @Column(name = "laying_type", length = 16)
    private String layingType;

    /** 投运年份 */
    @Column(name = "commission_year")
    private Integer commissionYear;

    /** 保温状况：完好 / 破损 */
    @Column(name = "insulation_status", length = 16)
    private String insulationStatus;

    /** 管段状态：运行 / 检修 / 停用 */
    @Column(name = "segment_status", length = 16)
    private String segmentStatus;

    /** 平面示意图折线坐标，格式 "x1,y1 x2,y2 ..." */
    @Column(name = "map_points", length = 255)
    private String mapPoints;

    @Column(name = "remark", length = 128)
    private String remark;

    @OneToMany(mappedBy = "segment", fetch = FetchType.LAZY)
    private List<ValveWell> valves = new ArrayList<>();
}
