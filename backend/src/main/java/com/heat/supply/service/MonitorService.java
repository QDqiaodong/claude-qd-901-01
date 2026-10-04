package com.heat.supply.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.Resident;
import com.heat.supply.domain.RoomTempSample;
import com.heat.supply.dto.LatestTempDTO;
import com.heat.supply.dto.RepairOrderVO;
import com.heat.supply.dto.ResidentVO;
import com.heat.supply.dto.RoomTempRequest;
import com.heat.supply.dto.RoomTempVO;
import com.heat.supply.dto.StationTempStatDTO;
import com.heat.supply.dto.SupplyChangeRequest;
import com.heat.supply.dto.TempCellVO;
import com.heat.supply.dto.TempGridVO;
import com.heat.supply.repository.HeatStationRepository;
import com.heat.supply.repository.HeatingFeeRepository;
import com.heat.supply.repository.ResidentRepository;
import com.heat.supply.repository.RoomTempSampleRepository;

import jakarta.persistence.EntityManager;

/**
 * 模块 3：热用户与室温抽测。
 * 数据访问走 @FetchProfile：{@link RoomTempSample#FETCH_WITH_RESIDENT} 把 resident 改成 JOIN 抓取。
 */
@Service
public class MonitorService {

    private final ResidentRepository residentRepository;
    private final RoomTempSampleRepository sampleRepository;
    private final HeatStationRepository stationRepository;
    private final HeatingFeeRepository feeRepository;
    private final RepairOrderService repairOrderService;
    private final EntityManager entityManager;

    public MonitorService(ResidentRepository residentRepository,
                          RoomTempSampleRepository sampleRepository,
                          HeatStationRepository stationRepository,
                          HeatingFeeRepository feeRepository,
                          RepairOrderService repairOrderService,
                          EntityManager entityManager) {
        this.residentRepository = residentRepository;
        this.sampleRepository = sampleRepository;
        this.stationRepository = stationRepository;
        this.feeRepository = feeRepository;
        this.repairOrderService = repairOrderService;
        this.entityManager = entityManager;
    }

    /** 室温抽测记录。profile 传 with-resident 时热用户 JOIN 抓取 */
    @Transactional(readOnly = true)
    public List<RoomTempVO> listSamples(String profile) {
        boolean enabled = RoomTempSample.FETCH_WITH_RESIDENT.equals(profile);
        Session session = entityManager.unwrap(Session.class);
        if (enabled) {
            session.enableFetchProfile(RoomTempSample.FETCH_WITH_RESIDENT);
        }
        try {
            List<Long> ids = sampleRepository.findIdsOrderBySampleDateDesc();
            List<RoomTempVO> result = new ArrayList<>();
            for (Long id : ids) {
                RoomTempSample sample = session.find(RoomTempSample.class, id);
                boolean initialized = Hibernate.isInitialized(sample.getResident());
                Resident resident = initialized ? sample.getResident() : null;
                result.add(toVo(sample, resident, initialized));
            }
            return result;
        } finally {
            if (enabled) {
                session.disableFetchProfile(RoomTempSample.FETCH_WITH_RESIDENT);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ResidentVO> listResidents(Long stationId, String community) {
        List<Resident> residents;
        if (stationId != null) {
            residents = residentRepository.findByStationIdOrderByBuildingNoAscUnitNoAsc(stationId);
        } else if (community != null && !community.isBlank()) {
            residents = residentRepository.findByCommunityOrderByBuildingNoAscUnitNoAscRoomNoAsc(community);
        } else {
            residents = residentRepository.findAll();
        }
        List<ResidentVO> result = new ArrayList<>();
        for (Resident resident : residents) {
            // 归档（归并后只读）档案不出现在用户档案列表，统一从保留档案入口查看
            if (resident.isArchived()) {
                continue;
            }
            result.add(toResidentVo(resident));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<String> listCommunities() {
        return residentRepository.findAllCommunities();
    }

    /**
     * 楼栋 × 单元 热力格子矩阵 + 不达标率汇总。
     */
    @Transactional(readOnly = true)
    public TempGridVO buildGrid(String community) {
        String target = (community == null || community.isBlank())
                ? residentRepository.findAllCommunities().stream().findFirst().orElse("")
                : community;
        List<Resident> residents = residentRepository.findByCommunityOrderByBuildingNoAscUnitNoAscRoomNoAsc(target)
                .stream().filter(r -> !r.isArchived()).toList();

        Map<Long, LatestTempDTO> latestMap = new HashMap<>();
        for (LatestTempDTO dto : sampleRepository.findLatestPerResident()) {
            latestMap.put(dto.getResidentId(), dto);
        }

        Map<String, List<Resident>> grouped = new LinkedHashMap<>();
        TreeSet<String> buildings = new TreeSet<>();
        TreeSet<String> units = new TreeSet<>();
        for (Resident resident : residents) {
            buildings.add(nvl(resident.getBuildingNo()));
            units.add(nvl(resident.getUnitNo()));
            String key = nvl(resident.getBuildingNo()) + "#" + nvl(resident.getUnitNo());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(resident);
        }

        List<TempCellVO> cells = new ArrayList<>();
        int qualifiedCount = 0;
        int unqualifiedCount = 0;
        int sampled = 0;
        for (Map.Entry<String, List<Resident>> entry : grouped.entrySet()) {
            List<Resident> group = entry.getValue();
            List<Long> residentIds = new ArrayList<>();
            List<String> households = new ArrayList<>();
            BigDecimal latestTemp = null;
            LocalDate latestDate = null;
            Long latestSampleId = null;
            int cellUnqualified = 0;
            int cellSamples = 0;
            for (Resident resident : group) {
                residentIds.add(resident.getId());
                LatestTempDTO dto = latestMap.get(resident.getId());
                if (dto == null) {
                    continue;
                }
                cellSamples++;
                sampled++;
                households.add(dto.getHouseholdName() == null ? resident.getRoomNo() : dto.getHouseholdName());
                boolean qualified = Boolean.TRUE.equals(dto.getQualified());
                if (qualified) {
                    qualifiedCount++;
                } else {
                    unqualifiedCount++;
                    cellUnqualified++;
                }
                if (latestDate == null || dto.getSampleDate() == null
                        || (latestDate != null && dto.getSampleDate().isAfter(latestDate))) {
                    latestDate = dto.getSampleDate();
                    latestTemp = dto.getTemperature();
                    latestSampleId = dto.getSampleId();
                }
            }
            boolean cellQualified = cellUnqualified == 0 && latestTemp != null;
            cells.add(new TempCellVO(
                    group.get(0).getBuildingNo(),
                    group.get(0).getUnitNo(),
                    residentIds,
                    group.size(),
                    cellSamples,
                    latestTemp,
                    latestDate,
                    latestTemp == null ? null : cellQualified,
                    Dict.isTempOverheated(latestTemp),
                    cellUnqualified,
                    latestSampleId,
                    households));
        }

        BigDecimal rate = sampled == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(unqualifiedCount * 100L)
                        .divide(BigDecimal.valueOf(sampled), 2, RoundingMode.HALF_UP);

        return new TempGridVO(target, new ArrayList<>(buildings), new ArrayList<>(units), cells,
                residents.size(), sampled, qualifiedCount, unqualifiedCount, rate);
    }

    /** 手写 JPQL：按换热站统计不达标室温户数 */
    @Transactional(readOnly = true)
    public List<StationTempStatDTO> stationTempStat() {
        List<StationTempStatDTO> list = new ArrayList<>(
                stationRepository.tempStatGroupByStation(Dict.MIN_QUALIFIED_TEMP));
        list.sort(Comparator.comparing(StationTempStatDTO::getUnqualifiedCount,
                Comparator.reverseOrder()).thenComparing(StationTempStatDTO::getStationCode));
        return list;
    }

    /**
     * 录入室温抽测：低于 18℃ 判不达标，可选一键生成报修工单。
     */
    @Transactional
    public RoomTempVO recordSample(RoomTempRequest request) {
        Resident resident = residentRepository.findById(request.getResidentId())
                .orElseThrow(() -> new BizException("热用户不存在：id=" + request.getResidentId()));
        if (resident.isArchived()) {
            throw new BizException("该档案已归并归档（只读），请到保留档案 id="
                    + (resident.getMergedInto() == null ? "" : resident.getMergedInto().getId())
                    + " 录入室温抽测");
        }
        BigDecimal temperature = request.getTemperature();
        if (temperature == null) {
            throw new BizException("请填写抽测室温（℃）");
        }
        if (temperature.compareTo(BigDecimal.valueOf(-30)) < 0 || temperature.compareTo(BigDecimal.valueOf(50)) > 0) {
            throw new BizException("室温应在 -30℃ ~ 50℃ 之间，当前 " + temperature + "℃");
        }
        if ("已报停".equals(resident.getSupplyStatus())) {
            throw new BizException("该用户已办理报停，无法录入室温抽测，请先恢复供热");
        }

        RoomTempSample sample = new RoomTempSample();
        sample.setResident(resident);
        sample.setSampleDate(request.getSampleDate() == null ? LocalDate.now() : request.getSampleDate());
        sample.setTemperature(temperature.setScale(1, RoundingMode.HALF_UP));
        sample.setQualified(Dict.isTempQualified(temperature));
        sample.setHouseholdName(request.getHouseholdName());
        sample.setSurveyor(request.getSurveyor());
        sample.setRemark(request.getRemark());
        RoomTempSample saved = sampleRepository.save(sample);

        RoomTempVO vo = toVo(saved, resident, true);
        if (!Boolean.TRUE.equals(saved.getQualified()) && Boolean.TRUE.equals(request.getAutoOrder())) {
            try {
                RepairOrderVO order = repairOrderService.createFromTemp(saved);
                vo.setLinkedOrderNo(order.getOrderNo());
            } catch (BizException ignored) {
                vo.setRemark("已存在同类未闭环工单，未重复生成");
            }
        }
        return vo;
    }

    /**
     * 不达标记录一键生成报修工单：重复生成要拦截。
     */
    @Transactional
    public RepairOrderVO createOrderFromSample(Long sampleId) {
        RoomTempSample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new BizException("室温抽测记录不存在：id=" + sampleId));
        if (Boolean.TRUE.equals(sample.getQualified())) {
            throw new BizException("该次抽测室温 " + sample.getTemperature() + "℃ 已达标，无需生成报修工单");
        }
        return repairOrderService.createFromTemp(sample);
    }

    /**
     * 报停 / 恢复供热：欠费用户不允许办理。
     */
    @Transactional
    public ResidentVO changeSupplyStatus(Long residentId, SupplyChangeRequest request) {
        Resident resident = residentRepository.findById(residentId)
                .orElseThrow(() -> new BizException("热用户不存在：id=" + residentId));
        if (resident.isArchived()) {
            throw new BizException("该档案已归并归档（只读），供热状态请到保留档案办理");
        }
        String target = request.getSupplyStatus();
        if (!Dict.isValidSupplyStatus(target)) {
            throw new BizException("供热状态只能是：正常供热 / 已报停");
        }
        BigDecimal arrears = feeRepository.sumArrearsByResident(residentId);
        if (nvl(arrears).compareTo(Dict.ARREARS_EPSILON) >= 0) {
            String action = "已报停".equals(target) ? "报停" : "恢复供热";
            throw new BizException("用户 " + describe(resident) + " 尚有采暖费欠费 "
                    + nvl(arrears).setScale(2, RoundingMode.HALF_UP) + " 元，缴清后方可办理" + action);
        }
        resident.setSupplyStatus(target);
        return toResidentVo(residentRepository.save(resident));
    }

    // ------------------------------------------------------------------

    public String describe(Resident resident) {
        return resident.getCommunity() + resident.getBuildingNo() + "号楼"
                + resident.getUnitNo() + "单元" + resident.getRoomNo();
    }

    private RoomTempVO toVo(RoomTempSample sample, Resident resident, boolean residentInitialized) {
        HeatStation station = resident == null ? null : resident.getStation();
        if (resident != null && station != null && !Hibernate.isInitialized(station)) {
            station = null;
        }
        return new RoomTempVO(
                sample.getId(),
                resident == null ? null : resident.getId(),
                resident == null ? null : resident.getCommunity(),
                resident == null ? null : resident.getBuildingNo(),
                resident == null ? null : resident.getUnitNo(),
                resident == null ? null : resident.getRoomNo(),
                station == null ? null : station.getId(),
                station == null ? null : station.getCode(),
                sample.getSampleDate(),
                sample.getTemperature(),
                sample.getQualified(),
                Dict.isTempOverheated(sample.getTemperature()),
                sample.getHouseholdName(),
                sample.getSurveyor(),
                sample.getRemark(),
                residentInitialized);
    }

    private ResidentVO toResidentVo(Resident resident) {
        HeatStation station = resident.getStation();
        ResidentVO vo = new ResidentVO(
                resident.getId(),
                resident.getCommunity(),
                resident.getBuildingNo(),
                resident.getUnitNo(),
                resident.getRoomNo(),
                resident.getAreaM2(),
                resident.getMeterNo(),
                resident.getTotalHeatGj(),
                station == null ? null : station.getId(),
                station == null ? null : station.getCode(),
                station == null ? null : station.getName(),
                resident.getSupplyStatus(),
                resident.getContactPhone(),
                feeRepository.sumArrearsByResident(resident.getId()));
        vo.setArchived(resident.isArchived());
        vo.setMergedIntoId(resident.getMergedInto() == null ? null : resident.getMergedInto().getId());
        vo.setLegacyCode(resident.getLegacyCode() == null ? "R" + resident.getId() : resident.getLegacyCode());
        return vo;
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
