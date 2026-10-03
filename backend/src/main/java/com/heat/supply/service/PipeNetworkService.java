package com.heat.supply.service;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.PipeSegment;
import com.heat.supply.domain.ValveWell;
import com.heat.supply.dto.SegmentVO;
import com.heat.supply.dto.ValveWellUpdateRequest;
import com.heat.supply.dto.ValveWellVO;
import com.heat.supply.repository.PipeSegmentRepository;
import com.heat.supply.repository.ValveWellRepository;

import jakarta.persistence.EntityManager;

/**
 * 模块 2：管网段与阀门井。
 * 数据访问走 @FetchProfile：
 * {@link PipeSegment#FETCH_WITH_VALVES} 把管段的阀门井集合改成 JOIN 抓取，
 * {@link ValveWell#FETCH_WITH_SEGMENT} 把阀门井的所属管段改成 JOIN 抓取。
 */
@Service
public class PipeNetworkService {

    private final PipeSegmentRepository segmentRepository;
    private final ValveWellRepository valveWellRepository;
    private final EntityManager entityManager;

    public PipeNetworkService(PipeSegmentRepository segmentRepository,
                              ValveWellRepository valveWellRepository,
                              EntityManager entityManager) {
        this.segmentRepository = segmentRepository;
        this.valveWellRepository = valveWellRepository;
        this.entityManager = entityManager;
    }

    /**
     * 管段清单。profile 传 {@link PipeSegment#FETCH_WITH_VALVES} 时阀门井集合随主表 JOIN 抓取。
     */
    @Transactional(readOnly = true)
    public List<SegmentVO> listSegments(Integer diameterDn, String profile) {
        boolean enabled = PipeSegment.FETCH_WITH_VALVES.equals(profile);
        Session session = entityManager.unwrap(Session.class);
        if (enabled) {
            session.enableFetchProfile(PipeSegment.FETCH_WITH_VALVES);
        }
        try {
            List<Long> ids = diameterDn == null
                    ? segmentRepository.findIdsOrderByDn()
                    : segmentRepository.findIdsByDiameterDnOrderBySegCodeAsc(diameterDn);
            List<SegmentVO> result = new ArrayList<>();
            for (Long id : ids) {
                PipeSegment segment = session.find(PipeSegment.class, id);
                result.add(toSegmentVo(segment, Hibernate.isInitialized(segment.getValves())));
            }
            return result;
        } finally {
            if (enabled) {
                session.disableFetchProfile(PipeSegment.FETCH_WITH_VALVES);
            }
        }
    }

    /**
     * 阀门井清单。profile 传 {@link ValveWell#FETCH_WITH_SEGMENT} 时所属管段 JOIN 抓取。
     */
    @Transactional(readOnly = true)
    public List<ValveWellVO> listValveWells(Long segmentId, String profile) {
        boolean enabled = ValveWell.FETCH_WITH_SEGMENT.equals(profile);
        Session session = entityManager.unwrap(Session.class);
        if (enabled) {
            session.enableFetchProfile(ValveWell.FETCH_WITH_SEGMENT);
        }
        try {
            List<Long> ids = segmentId == null
                    ? valveWellRepository.findIdsOrderByWellNo()
                    : valveWellRepository.findIdsBySegmentIdOrderByWellNoAsc(segmentId);
            List<ValveWellVO> result = new ArrayList<>();
            for (Long id : ids) {
                ValveWell well = session.find(ValveWell.class, id);
                boolean initialized = Hibernate.isInitialized(well.getSegment());
                PipeSegment segment = initialized ? well.getSegment() : null;
                result.add(toWellVo(well, segment, initialized));
            }
            return result;
        } finally {
            if (enabled) {
                session.disableFetchProfile(ValveWell.FETCH_WITH_SEGMENT);
            }
        }
    }

    @Transactional(readOnly = true)
    public ValveWellVO getValveWell(Long id) {
        ValveWell well = valveWellRepository.findById(id)
                .orElseThrow(() -> new BizException("阀门井不存在：id=" + id));
        return toWellVo(well, well.getSegment(), true);
    }

    @Transactional(readOnly = true)
    public List<Integer> listDiameters() {
        List<Integer> result = new ArrayList<>();
        for (PipeSegment segment : segmentRepository.findAll()) {
            if (segment.getDiameterDn() != null && !result.contains(segment.getDiameterDn())) {
                result.add(segment.getDiameterDn());
            }
        }
        result.sort(null);
        return result;
    }

    /**
     * 阀门井开关 / 检修状态变更。
     * 业务规则：所属管段处于检修状态时，禁止把阀门改为全开。
     */
    @Transactional
    public ValveWellVO updateValveWell(Long id, ValveWellUpdateRequest request) {
        ValveWell well = valveWellRepository.findById(id)
                .orElseThrow(() -> new BizException("阀门井不存在：id=" + id));

        if (request.getOpenState() != null) {
            if (!Dict.isValidOpenState(request.getOpenState())) {
                throw new BizException("开关状态只能是：全开 / 半开 / 关闭");
            }
            PipeSegment segment = well.getSegment();
            boolean segmentInRepair = segment != null && "检修".equals(segment.getSegmentStatus());
            if ("全开".equals(request.getOpenState()) && segmentInRepair) {
                throw new BizException("管段 " + segment.getSegCode() + "（" + segment.getStartWellNo()
                        + "→" + segment.getEndWellNo() + "）正处于检修状态，禁止将阀门井 "
                        + well.getWellNo() + " 改为全开，请先下调至半开或关闭");
            }
            well.setOpenState(request.getOpenState());
        }
        if (request.getRepairStatus() != null) {
            if (!Dict.isValidValveRepairStatus(request.getRepairStatus())) {
                throw new BizException("检修状态只能是：正常 / 检修中");
            }
            well.setRepairStatus(request.getRepairStatus());
        }
        if (request.getRemark() != null) {
            well.setRemark(request.getRemark());
        }
        ValveWell saved = valveWellRepository.save(well);
        return toWellVo(saved, saved.getSegment(), true);
    }

    /** 管段检修状态变更：置为检修时，把该管段下的阀门井强制下调（不允许保持全开） */
    @Transactional
    public SegmentVO changeSegmentStatus(Long id, String status) {
        if (!Dict.isValidSegmentStatus(status)) {
            throw new BizException("管段状态只能是：运行 / 检修 / 停用");
        }
        PipeSegment segment = segmentRepository.findById(id)
                .orElseThrow(() -> new BizException("管段不存在：id=" + id));
        segment.setSegmentStatus(status);
        if ("检修".equals(status)) {
            for (ValveWell well : valveWellRepository.findBySegmentIdOrderByWellNoAsc(segment.getId())) {
                if ("全开".equals(well.getOpenState())) {
                    well.setOpenState("半开");
                    well.setRepairStatus("检修中");
                    valveWellRepository.save(well);
                }
            }
        }
        PipeSegment saved = segmentRepository.save(segment);
        return toSegmentVo(saved, true);
    }

    // ------------------------------------------------------------------

    private SegmentVO toSegmentVo(PipeSegment segment, boolean valvesInitialized) {
        List<String> wellNos = new ArrayList<>();
        int valveCount = -1;
        if (valvesInitialized) {
            valveCount = segment.getValves().size();
            for (ValveWell well : segment.getValves()) {
                wellNos.add(well.getWellNo());
            }
        }
        return new SegmentVO(segment.getId(), segment.getSegCode(), segment.getStartWellNo(), segment.getEndWellNo(),
                segment.getDiameterDn(), segment.getLengthM(), segment.getLayingType(), segment.getCommissionYear(),
                segment.getInsulationStatus(), segment.getSegmentStatus(), segment.getMapPoints(), segment.getRemark(),
                valveCount, wellNos, valvesInitialized);
    }

    private ValveWellVO toWellVo(ValveWell well, PipeSegment segment, boolean segmentInitialized) {
        return new ValveWellVO(well.getId(), well.getWellNo(), well.getLocation(), well.getValveCaliberDn(),
                well.getOpenState(), well.getRepairStatus(),
                segment == null ? null : segment.getId(),
                segment == null ? null : segment.getSegCode(),
                segment == null ? null : segment.getSegmentStatus(),
                well.getLastCheckDate(), well.getRemark(), well.getMapX(), well.getMapY(),
                well.getInoperableFlag(), well.getEgressGuard(), segmentInitialized);
    }
}
