package com.heat.supply.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatSource;
import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.RepairOrder;
import com.heat.supply.dto.FetchProfileVO;
import com.heat.supply.dto.OverviewVO;
import com.heat.supply.dto.SourceLoadDTO;
import com.heat.supply.dto.SourceVO;
import com.heat.supply.dto.StationRuntimeRequest;
import com.heat.supply.dto.StationStatusRequest;
import com.heat.supply.dto.StationVO;
import com.heat.supply.repository.HeatSourceRepository;
import com.heat.supply.repository.HeatStationRepository;
import com.heat.supply.repository.RepairOrderRepository;

import jakarta.persistence.EntityManager;

/**
 * 模块 1：热源厂与换热站。
 * 数据访问走 @FetchProfile：{@link HeatSource#FETCH_WITH_STATIONS} 把 stations 集合改成 JOIN 抓取。
 */
@Service
public class SourceStationService {

    private final HeatSourceRepository sourceRepository;
    private final HeatStationRepository stationRepository;
    private final RepairOrderRepository repairOrderRepository;
    private final EntityManager entityManager;
    private final TransactionTemplate requiresNewTx;

    public SourceStationService(HeatSourceRepository sourceRepository,
                                HeatStationRepository stationRepository,
                                RepairOrderRepository repairOrderRepository,
                                EntityManager entityManager,
                                PlatformTransactionManager transactionManager) {
        this.sourceRepository = sourceRepository;
        this.stationRepository = stationRepository;
        this.repairOrderRepository = repairOrderRepository;
        this.entityManager = entityManager;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTx.setReadOnly(true);
    }

    // ------------------------------------------------------------------
    // @FetchProfile：启用前后对照
    // ------------------------------------------------------------------

    /**
     * 按抓取档案查询热源。profile 传入 {@link HeatSource#FETCH_WITH_STATIONS} 时启用 JOIN 抓取，
     * 返回的 associationEagerLoaded 反映「换热站集合是否随实体装载即被预取」。
     */
    @Transactional(readOnly = true)
    public FetchProfileVO loadSources(String profile) {
        boolean enabled = HeatSource.FETCH_WITH_STATIONS.equals(profile);
        Session session = entityManager.unwrap(Session.class);
        if (enabled) {
            session.enableFetchProfile(HeatSource.FETCH_WITH_STATIONS);
        }
        try {
            List<Long> ids = sourceRepository.findIdsOrderById();
            List<SourceVO> rows = new ArrayList<>();
            boolean eagerLoaded = !ids.isEmpty();
            for (Long id : ids) {
                HeatSource source = session.find(HeatSource.class, id);
                boolean initialized = Hibernate.isInitialized(source.getStations());
                if (!initialized) {
                    eagerLoaded = false;
                }
                List<String> codes = new ArrayList<>();
                int count = -1;
                if (initialized) {
                    count = source.getStations().size();
                    for (HeatStation station : source.getStations()) {
                        codes.add(station.getCode());
                    }
                }
                rows.add(new SourceVO(source.getId(), source.getName(), source.getCapacityMw(),
                        source.getFuelType(), source.getStatus(), source.getManager(), source.getAddress(),
                        source.getCommissionDate(), count, codes));
            }
            String note = enabled
                    ? "启用 @FetchProfile(with-stations)：HeatSource.stations 以 JOIN 方式随主实体一次性装载"
                    : "未启用抓取档案：HeatSource.stations 为延迟集合，需访问时再逐条触发 SQL";
            return new FetchProfileVO(enabled ? HeatSource.FETCH_WITH_STATIONS : "none", eagerLoaded,
                    "HeatSource.stations", note, rows.size(), rows);
        } finally {
            if (enabled) {
                session.disableFetchProfile(HeatSource.FETCH_WITH_STATIONS);
            }
        }
    }

    /**
     * 一对对照：分别在「不启用档案」与「启用 with-stations」两个独立事务里查同一批数据，
     * 返回两次查询的元信息，便于直接观察差异。
     */
    public List<FetchProfileVO> compareFetchProfiles() {
        FetchProfileVO plain = requiresNewTx.execute(status -> loadSources(null));
        FetchProfileVO profiled = requiresNewTx.execute(status -> loadSources(HeatSource.FETCH_WITH_STATIONS));
        return List.of(plain, profiled);
    }

    // ------------------------------------------------------------------
    // 台账
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<SourceVO> listSources() {
        List<SourceVO> result = new ArrayList<>();
        for (HeatSource source : sourceRepository.findAll()) {
            result.add(toSourceVo(source, true));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SourceVO getSource(Long id) {
        HeatSource source = sourceRepository.findById(id)
                .orElseThrow(() -> new BizException("热源厂不存在：id=" + id));
        return toSourceVo(source, true);
    }

    @Transactional(readOnly = true)
    public List<StationVO> listStations(Long sourceId) {
        List<HeatStation> stations = sourceId == null
                ? stationRepository.findAll()
                : stationRepository.findByHeatSourceIdOrderByCodeAsc(sourceId);
        List<StationVO> result = new ArrayList<>();
        for (HeatStation station : stations) {
            result.add(toStationVo(station));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public StationVO getStation(Long id) {
        HeatStation station = stationRepository.findById(id)
                .orElseThrow(() -> new BizException("换热站不存在：id=" + id));
        return toStationVo(station);
    }

    /** 手写 JPQL：按热源汇总供热面积与一次侧平均温差 */
    @Transactional(readOnly = true)
    public List<SourceLoadDTO> sourceLoadSummary() {
        return sourceRepository.summaryBySource();
    }

    /** 手写 JPQL：按换热站统计室温抽测户数/不达标次数由 {@link MonitorService} 提供，此处按热源汇总 */
    @Transactional(readOnly = true)
    public OverviewVO overview() {
        List<SourceLoadDTO> loads = sourceRepository.summaryBySource();
        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal totalCapacity = BigDecimal.ZERO;
        long stationCount = 0L;
        for (SourceLoadDTO dto : loads) {
            totalArea = totalArea.add(nvl(dto.getTotalAreaWanM2()));
            totalCapacity = totalCapacity.add(nvl(dto.getCapacityMw()));
            stationCount += dto.getStationCount() == null ? 0 : dto.getStationCount();
        }
        return new OverviewVO(
                sourceRepository.count(),
                stationCount,
                stationRepository.countByStatus("运行"),
                Long.valueOf(0L),
                repairOrderRepository.countByOrderStatusNot("已闭环"),
                totalArea.setScale(2, RoundingMode.HALF_UP),
                totalCapacity.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                loads);
    }

    // ------------------------------------------------------------------
    // 运行参数登记 / 状态变更（业务规则）
    // ------------------------------------------------------------------

    @Transactional
    public StationVO registerRuntime(Long id, StationRuntimeRequest request) {
        HeatStation station = stationRepository.findById(id)
                .orElseThrow(() -> new BizException("换热站不存在：id=" + id));

        BigDecimal primarySupply = request.getPrimarySupplyTemp() == null
                ? station.getPrimarySupplyTemp() : request.getPrimarySupplyTemp();
        BigDecimal primaryReturn = request.getPrimaryReturnTemp() == null
                ? station.getPrimaryReturnTemp() : request.getPrimaryReturnTemp();
        BigDecimal secondarySupply = request.getSecondarySupplyTemp() == null
                ? station.getSecondarySupplyTemp() : request.getSecondarySupplyTemp();
        BigDecimal secondaryReturn = request.getSecondaryReturnTemp() == null
                ? station.getSecondaryReturnTemp() : request.getSecondaryReturnTemp();

        if (primarySupply == null || primaryReturn == null) {
            throw new BizException("请填写一次侧供/回水温度");
        }
        if (primarySupply.compareTo(primaryReturn) <= 0) {
            throw new BizException("一次侧供水温度必须高于回水温度，当前 " + primarySupply + "℃ / " + primaryReturn + "℃");
        }
        if (secondarySupply != null && secondaryReturn != null) {
            if (secondarySupply.compareTo(secondaryReturn) <= 0) {
                throw new BizException("二次侧供水温度必须高于回水温度，当前 " + secondarySupply + "℃ / " + secondaryReturn + "℃");
            }
            BigDecimal secondaryDelta = secondarySupply.subtract(secondaryReturn);
            if (secondaryDelta.compareTo(Dict.SECONDARY_DELTA_LIMIT) > 0) {
                throw new BizException("二次侧供回温差 " + secondaryDelta + "℃ 超过允许上限 "
                        + Dict.SECONDARY_DELTA_LIMIT + "℃，请核对热量表与循环泵运行状况");
            }
        }
        if (request.getRunningPumpCount() != null) {
            if (request.getRunningPumpCount() < 0) {
                throw new BizException("运行循环泵台数不能为负数");
            }
            Integer installed = station.getPumpCount() == null ? 0 : station.getPumpCount();
            if (request.getRunningPumpCount() > installed) {
                throw new BizException("运行循环泵台数不能超过装机台数（" + installed + " 台）");
            }
            station.setRunningPumpCount(request.getRunningPumpCount());
        }

        station.setPrimarySupplyTemp(primarySupply);
        station.setPrimaryReturnTemp(primaryReturn);
        station.setSecondarySupplyTemp(secondarySupply);
        station.setSecondaryReturnTemp(secondaryReturn);
        if (request.getDesignDeltaT() != null) {
            if (request.getDesignDeltaT().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("设计温差必须大于 0");
            }
            station.setDesignDeltaT(request.getDesignDeltaT());
        }
        if (request.getManager() != null) {
            station.setManager(request.getManager());
        }
        if (request.getStatus() != null && !Objects.equals(request.getStatus(), station.getStatus())) {
            StationStatusRequest statusRequest = new StationStatusRequest();
            statusRequest.setStatus(request.getStatus());
            changeStatus(id, statusRequest);
        }
        return toStationVo(stationRepository.save(station));
    }

    /**
     * 换热站状态变更：存在未闭环报修工单时不允许停用。
     */
    @Transactional
    public StationVO changeStatus(Long id, StationStatusRequest request) {
        HeatStation station = stationRepository.findById(id)
                .orElseThrow(() -> new BizException("换热站不存在：id=" + id));
        String target = request.getStatus();
        if (!Dict.isValidStationStatus(target)) {
            throw new BizException("换热站状态只能是：运行 / 检修 / 停用");
        }
        if ("停用".equals(target)) {
            long openOrders = repairOrderRepository.countOpenByStation(station.getId(), "已闭环");
            if (openOrders > 0) {
                throw new BizException("换热站 " + station.getCode() + " 尚有 " + openOrders
                        + " 条未闭环报修工单，全部闭环后方可停用");
            }
            station.setRunningPumpCount(0);
        }
        if ("运行".equals(target)) {
            HeatSource source = station.getHeatSource();
            if (source != null && "停用".equals(source.getStatus())) {
                throw new BizException("所属热源厂 " + source.getName() + " 已停用，无法投运该换热站");
            }
        }
        station.setStatus(target);
        return toStationVo(stationRepository.save(station));
    }

    /** 某换热站是否存在未闭环工单 */
    @Transactional(readOnly = true)
    public boolean hasOpenOrder(HeatStation station) {
        if (station == null || station.getId() == null) {
            return false;
        }
        return repairOrderRepository.countOpenByStation(station.getId(), "已闭环") > 0;
    }

    @Transactional(readOnly = true)
    public long countOpenOrder(Long stationId) {
        return repairOrderRepository.countOpenByStation(stationId, "已闭环");
    }

    // ------------------------------------------------------------------
    // 转换
    // ------------------------------------------------------------------

    private SourceVO toSourceVo(HeatSource source, boolean includeStations) {
        List<String> codes = new ArrayList<>();
        int count = -1;
        if (includeStations && Hibernate.isInitialized(source.getStations())) {
            count = source.getStations().size();
            for (HeatStation station : source.getStations()) {
                codes.add(station.getCode());
            }
        }
        return new SourceVO(source.getId(), source.getName(), source.getCapacityMw(), source.getFuelType(),
                source.getStatus(), source.getManager(), source.getAddress(), source.getCommissionDate(),
                count, codes);
    }

    public StationVO toStationVo(HeatStation station) {
        HeatSource source = station.getHeatSource();
        BigDecimal primaryDelta = sub(station.getPrimarySupplyTemp(), station.getPrimaryReturnTemp());
        BigDecimal secondaryDelta = sub(station.getSecondarySupplyTemp(), station.getSecondaryReturnTemp());
        BigDecimal loadRate = loadRate(station, primaryDelta);
        return new StationVO(
                station.getId(),
                station.getCode(),
                station.getName(),
                source == null ? null : source.getId(),
                source == null ? null : source.getName(),
                source == null ? null : source.getFuelType(),
                station.getAreaWanM2(),
                station.getPrimarySupplyTemp(),
                station.getPrimaryReturnTemp(),
                station.getSecondarySupplyTemp(),
                station.getSecondaryReturnTemp(),
                station.getDesignDeltaT(),
                primaryDelta,
                secondaryDelta,
                loadRate,
                loadLevel(loadRate),
                station.getPumpCount(),
                station.getRunningPumpCount(),
                station.getStatus(),
                station.getManager(),
                station.getAddress(),
                station.getLastMaintainDate(),
                repairOrderRepository.countOpenByStation(station.getId(), "已闭环"));
    }

    /** 负荷率 = 一次侧实际温差 / 设计温差 */
    public static BigDecimal loadRate(HeatStation station, BigDecimal primaryDelta) {
        BigDecimal design = station.getDesignDeltaT() == null
                || station.getDesignDeltaT().compareTo(BigDecimal.ZERO) <= 0
                ? Dict.DESIGN_DELTA_T
                : station.getDesignDeltaT();
        return primaryDelta.divide(design, 4, RoundingMode.HALF_UP);
    }

    private String loadLevel(BigDecimal loadRate) {
        if (loadRate.compareTo(Dict.LOAD_HIGH) > 0) {
            return "超负荷";
        }
        if (loadRate.compareTo(Dict.LOAD_LOW) < 0) {
            return "低负荷";
        }
        return "正常";
    }

    private BigDecimal sub(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return BigDecimal.ZERO;
        }
        return a.subtract(b);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
