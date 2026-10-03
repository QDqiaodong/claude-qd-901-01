package com.heat.supply.domain;

import java.math.BigDecimal;
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
 * 室温抽测记录：抽测日期、室温、是否达标、住户姓名。
 * 抓取档案 {@link #FETCH_WITH_RESIDENT} 把 resident 改成 JOIN 抓取。
 */
@Entity
@Table(name = "room_temp_sample")
@Getter
@Setter
@FetchProfile(name = RoomTempSample.FETCH_WITH_RESIDENT,
        fetchOverrides = @FetchProfile.FetchOverride(entity = RoomTempSample.class,
                association = "resident", mode = FetchMode.JOIN))
public class RoomTempSample {

    /** 命名抓取档案：查抽测记录时把所属热用户改成 JOIN 抓取 */
    public static final String FETCH_WITH_RESIDENT = "with-resident";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resident_id", nullable = false)
    private Resident resident;

    /** 抽测日期 */
    @Column(name = "sample_date")
    private LocalDate sampleDate;

    /** 室温（℃） */
    @Column(name = "temperature", precision = 4, scale = 1)
    private BigDecimal temperature;

    /** 是否达标 */
    @Column(name = "qualified")
    private Boolean qualified;

    /** 住户姓名 */
    @Column(name = "household_name", length = 32)
    private String householdName;

    /** 抽测人 */
    @Column(name = "surveyor", length = 32)
    private String surveyor;

    @Column(name = "remark", length = 128)
    private String remark;
}
