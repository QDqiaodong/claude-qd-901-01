package com.heat.supply.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.heat.supply.common.BizException;
import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.Resident;
import com.heat.supply.domain.RepairOrder;
import com.heat.supply.domain.RoomTempSample;
import com.heat.supply.dto.RepairOrderRequest;
import com.heat.supply.dto.RepairOrderVO;
import com.heat.supply.dto.RepairStatusRequest;
import com.heat.supply.repository.HeatStationRepository;
import com.heat.supply.repository.RepairOrderRepository;
import com.heat.supply.repository.ResidentRepository;

/**
 * 模块 4：报修工单。
 * 业务规则：派工 / 闭环前置校验，状态只能按流转顺序推进，室温不达标工单重复生成要拦截。
 */
@Service
public class RepairOrderService {

    private final RepairOrderRepository repairOrderRepository;
    private final HeatStationRepository stationRepository;
    private final ResidentRepository residentRepository;

    public RepairOrderService(RepairOrderRepository repairOrderRepository,
                              HeatStationRepository stationRepository,
                              ResidentRepository residentRepository) {
        this.repairOrderRepository = repairOrderRepository;
        this.stationRepository = stationRepository;
        this.residentRepository = residentRepository;
    }

    @Transactional(readOnly = true)
    public List<RepairOrderVO> listOrders(String status) {
        List<RepairOrder> orders = status == null || status.isBlank()
                ? repairOrderRepository.findAll()
                : repairOrderRepository.findByOrderStatusOrderByReportedDateDesc(status);
        List<RepairOrderVO> result = new ArrayList<>();
        for (RepairOrder order : orders) {
            result.add(toVo(order));
        }
        result.sort(Comparator.comparing(RepairOrderVO::getId, Comparator.reverseOrder()));
        return result;
    }

    @Transactional(readOnly = true)
    public RepairOrderVO getOrder(Long id) {
        RepairOrder order = repairOrderRepository.findById(id)
                .orElseThrow(() -> new BizException("报修工单不存在：id=" + id));
        return toVo(order);
    }

    @Transactional
    public RepairOrderVO create(RepairOrderRequest request) {
        if (request.getFaultType() == null || !Dict.isValidFaultType(request.getFaultType())) {
            throw new BizException("故障类型只能是：不热 / 漏水 / 异响 / 气堵");
        }
        HeatStation station = request.getStationId() == null
                ? null
                : stationRepository.findById(request.getStationId())
                        .orElseThrow(() -> new BizException("换热站不存在：id=" + request.getStationId()));
        Resident resident = request.getResidentId() == null
                ? null
                : residentRepository.findById(request.getResidentId())
                        .orElseThrow(() -> new BizException("热用户不存在：id=" + request.getResidentId()));
        if (resident != null && resident.isArchived()) {
            throw new BizException("该档案已归并归档（只读），报修工单请到保留档案 id="
                    + (resident.getMergedInto() == null ? "" : resident.getMergedInto().getId()) + " 登记");
        }
        if (station == null && resident != null) {
            station = resident.getStation();
        }
        if (station == null && resident == null) {
            throw new BizException("报修工单必须指定换热站或热用户");
        }
        String targetStatus = request.getOrderStatus() == null ? "待派工" : request.getOrderStatus();
        if (!Dict.isValidOrderStatus(targetStatus)) {
            throw new BizException("工单状态只能是：待派工 / 处理中 / 已闭环");
        }
        if ("处理中".equals(targetStatus) && blank(request.getAssignee())) {
            throw new BizException("推进到「处理中」前必须先指定派工人");
        }
        if ("已闭环".equals(targetStatus) && blank(request.getHandleResult())) {
            throw new BizException("闭环前必须填写处理结果");
        }

        RepairOrder order = new RepairOrder();
        order.setOrderNo(nextOrderNo());
        order.setReportedDate(LocalDate.now());
        order.setStation(station);
        order.setResident(resident);
        order.setFaultType(request.getFaultType());
        order.setAssignee(request.getAssignee());
        order.setHandleResult(request.getHandleResult());
        order.setOrderStatus(targetStatus);
        if ("已闭环".equals(targetStatus)) {
            order.setClosedDate(LocalDate.now());
        }
        return toVo(repairOrderRepository.save(order));
    }

    /**
     * 由不达标室温记录生成「不热」工单：同一用户已有未闭环的同类型工单时拦截。
     */
    @Transactional
    public RepairOrderVO createFromTemp(RoomTempSample sample) {
        Resident resident = sample.getResident();
        if (resident == null) {
            throw new BizException("该室温抽测缺少关联热用户，无法生成工单");
        }
        long duplicate = repairOrderRepository.countDuplicate(resident.getId(), Dict.FAULT_FROM_TEMP, "已闭环");
        if (duplicate > 0) {
            throw new BizException("用户 " + label(resident) + " 已存在未闭环的「不热」报修工单，请先处理后再生成");
        }
        RepairOrder order = new RepairOrder();
        order.setOrderNo(nextOrderNo());
        order.setReportedDate(LocalDate.now());
        order.setStation(resident.getStation());
        order.setResident(resident);
        order.setFaultType(Dict.FAULT_FROM_TEMP);
        order.setAssignee("");
        order.setHandleResult("");
        order.setOrderStatus("待派工");
        order.setClosedDate(null);
        return toVo(repairOrderRepository.save(order));
    }

    /**
     * 看板拖拽推进：待派工 → 处理中 → 已闭环，不能跳级也不能回退。
     */
    @Transactional
    public RepairOrderVO updateStatus(Long id, RepairStatusRequest request) {
        RepairOrder order = repairOrderRepository.findById(id)
                .orElseThrow(() -> new BizException("报修工单不存在：id=" + id));
        String target = request.getOrderStatus();
        if (!Dict.isValidOrderStatus(target)) {
            throw new BizException("工单状态只能是：待派工 / 处理中 / 已闭环");
        }
        String current = order.getOrderStatus() == null ? "待派工" : order.getOrderStatus();
        int from = Dict.ORDER_FLOW.indexOf(current);
        int to = Dict.ORDER_FLOW.indexOf(target);
        if (to < from) {
            throw new BizException("工单 " + order.getOrderNo() + " 已处于「" + current + "」，不允许回退状态");
        }
        if (to - from > 1) {
            throw new BizException("工单状态需按 待派工 → 处理中 → 已闭环 顺序推进，不能跨级到「" + target + "」");
        }
        if ("处理中".equals(target)) {
            String assignee = blank(request.getAssignee()) ? order.getAssignee() : request.getAssignee();
            if (blank(assignee)) {
                throw new BizException("推进到「处理中」前必须指定派工人");
            }
            order.setAssignee(assignee);
        }
        if ("已闭环".equals(target)) {
            String result = blank(request.getHandleResult()) ? order.getHandleResult() : request.getHandleResult();
            if (blank(result)) {
                throw new BizException("闭环前必须填写处理结果");
            }
            order.setHandleResult(result);
            order.setClosedDate(LocalDate.now());
        }
        order.setOrderStatus(target);
        return toVo(repairOrderRepository.save(order));
    }

    @Transactional
    public RepairOrderVO assign(Long id, String assignee) {
        RepairOrder order = repairOrderRepository.findById(id)
                .orElseThrow(() -> new BizException("报修工单不存在：id=" + id));
        if (blank(assignee)) {
            throw new BizException("请指定派工人");
        }
        order.setAssignee(assignee);
        return toVo(repairOrderRepository.save(order));
    }

    private String nextOrderNo() {
        LocalDate today = LocalDate.now();
        long seq = repairOrderRepository.countByReportedDate(today) + 1;
        return "BX" + today.toString().replace("-", "") + String.format("%03d", seq);
    }

    public RepairOrderVO toVo(RepairOrder order) {
        HeatStation station = order.getStation();
        Resident resident = order.getResident();
        String current = order.getOrderStatus() == null ? "待派工" : order.getOrderStatus();
        int next = Dict.ORDER_FLOW.indexOf(current) + 1;
        String nextStatus = next < Dict.ORDER_FLOW.size() ? Dict.ORDER_FLOW.get(next) : "";
        return new RepairOrderVO(
                order.getId(),
                order.getOrderNo(),
                order.getReportedDate(),
                station == null ? null : station.getId(),
                station == null ? null : station.getCode(),
                resident == null ? null : resident.getId(),
                resident == null ? null : label(resident),
                order.getFaultType(),
                order.getAssignee(),
                order.getHandleResult(),
                current,
                order.getClosedDate(),
                nextStatus);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String label(Resident resident) {
        if (resident == null) {
            return "";
        }
        // 在事务内访问代理属性会触发按需加载，无需额外判断初始化状态
        return resident.getCommunity() + resident.getBuildingNo() + "号楼"
                + resident.getUnitNo() + "单元" + resident.getRoomNo();
    }
}
