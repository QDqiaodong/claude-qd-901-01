package com.heat.supply.domain;

import java.time.LocalDate;

import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.FetchProfile;

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
 * 阀门井：井号、位置、阀门口径、开关状态、检修状态，挂接所属管段。
 * 抓取档案 {@link #FETCH_WITH_SEGMENT} 把 segment 改成 JOIN 抓取。
 */
@Entity
@Table(name = "valve_well")
@Getter
@Setter
@FetchProfile(name = ValveWell.FETCH_WITH_SEGMENT,
        fetchOverrides = @FetchProfile.FetchOverride(entity = ValveWell.class,
                association = "segment", mode = FetchMode.JOIN))
public class ValveWell {

    /** 命名抓取档案：查阀门井时把所属管段改成 JOIN 抓取 */
    public static final String FETCH_WITH_SEGMENT = "with-segment";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 井号 */
    @Column(name = "well_no", nullable = false, unique = true, length = 32)
    private String wellNo;

    @Column(name = "well_location", length = 128)
    private String location;

    /** 阀门口径 DN */
    @Column(name = "valve_caliber_dn")
    private Integer valveCaliberDn;

    /** 开关状态：全开 / 半开 / 关闭 */
    @Column(name = "open_state", length = 16)
    private String openState;

    /** 检修状态：正常 / 检修中 */
    @Column(name = "repair_status", length = 16)
    private String repairStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id")
    private PipeSegment segment;

    @Column(name = "last_check_date")
    private LocalDate lastCheckDate;

    @Column(name = "remark", length = 128)
    private String remark;

    /** 平面示意图坐标 */
    @Column(name = "map_x")
    private Integer mapX;

    @Column(name = "map_y")
    private Integer mapY;

    /** 现场不可操作：检修卡涩 / 被占压 / 处于泄漏区，隔离试算不得列入计划关断 */
    @Column(name = "inoperable_flag")
    private Boolean inoperableFlag;

    /** 人员撤离安全门：关断序列里必须最后关闭、复供最先开启 */
    @Column(name = "egress_guard")
    private Boolean egressGuard;
}
