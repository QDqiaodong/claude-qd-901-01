package com.heat.supply.dto;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 归并预检的疑似重复候选行（同址在档档案） */
@Data
@NoArgsConstructor
public class MergeCandidateVO {

    private String community;
    private String buildingNo;
    private String unitNo;
    private String roomNo;
    private String fullAddress;
    private List<Profile> profiles;

    /** 该地址在档档案数（>=2 才是疑似重复） */
    public int getCount() {
        return profiles == null ? 0 : profiles.size();
    }

    @Data
    @NoArgsConstructor
    public static class Profile {
        private Long residentId;
        private String meterNo;
        private BigDecimal areaM2;
        private String stationCode;
        private String stationName;
        private String supplyStatus;
        private String contactPhone;
        private int sampleCount;
        private int orderCount;
        private int feeCount;
    }
}
