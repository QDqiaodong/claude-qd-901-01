package com.heat.supply.dto;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/** @FetchProfile 抓取档案查询结果的元信息 */
@Data
@NoArgsConstructor
public class FetchProfileVO {

    /** 本次启用的抓取档案名，未启用为 "none" */
    private String profile;
    /** 关联集合是否在实体装载时就被 JOIN 抓取 */
    private Boolean associationEagerLoaded;
    /** 关联集合类型 */
    private String association;
    /** 说明 */
    private String note;
    private Integer rowCount;
    private List<SourceVO> rows;

    public FetchProfileVO(String profile, Boolean associationEagerLoaded, String association,
                          String note, Integer rowCount, List<SourceVO> rows) {
        this.profile = profile;
        this.associationEagerLoaded = associationEagerLoaded;
        this.association = association;
        this.note = note;
        this.rowCount = rowCount;
        this.rows = rows;
    }
}
