package com.heat.supply.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * 旧标识 → 保留档案 别名：归并后按地址、热表号、旧档案编号查询都只能落到同一户。
 */
@Entity
@Table(name = "resident_alias",
        uniqueConstraints = @UniqueConstraint(name = "uk_alias_type_value",
                columnNames = {"alias_type", "alias_value"}))
@Getter
@Setter
public class ResidentAlias {

    /** 旧档案编号 */
    public static final String TYPE_OLD_ID = "OLD_ID";
    /** 热表号 */
    public static final String TYPE_METER_NO = "METER_NO";
    /** 归一化地址 */
    public static final String TYPE_ADDRESS = "ADDRESS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** METER_NO / OLD_ID / ADDRESS */
    @Column(name = "alias_type", nullable = false, length = 16)
    private String aliasType;

    /** 旧标识值（地址为归一化全址） */
    @Column(name = "alias_value", nullable = false, length = 160)
    private String aliasValue;

    /** 落点：保留档案 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    /** 该标识原属的重复档案 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_resident_id", nullable = false)
    private Resident sourceResident;

    @Column(name = "merge_no", nullable = false, length = 32)
    private String mergeNo;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
