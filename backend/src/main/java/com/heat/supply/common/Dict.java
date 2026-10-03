package com.heat.supply.common;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 城市集中供热业务词典与运行参数表：热源类型、管网敷设/保温、阀门开关、故障类型、
 * 工单状态、缴费状态、室温达标阈值、设计温差、热价等。规则集中在此，便于统一调整。
 */
public final class Dict {

    private Dict() {
    }

    // ---------------- 热源厂 ----------------
    public static final List<String> FUEL_TYPES = List.of("燃煤", "燃气", "热电联产");

    public static final List<String> SOURCE_STATUS = List.of("运行", "检修", "停用");

    /** 各燃料类型的参考供热单价（元/㎡·采暖季） */
    private static final Map<String, BigDecimal> UNIT_PRICE_BY_FUEL = Map.of(
            "燃煤", BigDecimal.valueOf(22.50),
            "燃气", BigDecimal.valueOf(28.60),
            "热电联产", BigDecimal.valueOf(25.40));

    // ---------------- 换热站状态 ----------------
    public static final List<String> STATION_STATUS = List.of("运行", "检修", "停用");

    // ---------------- 管网 ----------------
    public static final List<String> LAYING_TYPES = List.of("直埋", "架空", "管沟");

    public static final List<String> INSULATION_STATUS = List.of("完好", "破损");

    /** 管段自身运行状态 */
    public static final List<String> SEGMENT_STATUS = List.of("运行", "检修", "停用");

    public static final List<String> VALVE_OPEN_STATE = List.of("全开", "半开", "关闭");

    public static final List<String> VALVE_REPAIR_STATUS = List.of("正常", "检修中");

    /** 管径筛选常用档位 */
    public static final List<Integer> DN_OPTIONS = List.of(200, 300, 400, 500, 600, 800);

    // ---------------- 管网隔离方案 ----------------
    /** 方案阶段 */
    public static final List<String> ISO_PHASES = List.of(
            "试算中", "待执行", "执行中", "已隔离", "已暂停", "已失败", "已复供", "已废止");

    /** 取舍策略 */
    public static final List<String> ISO_STRATEGIES = List.of("最小隔离", "扩大隔离", "拒绝启用");

    /** 步骤阶段 */
    public static final String STAGE_ISOLATE = "隔离";
    public static final String STAGE_RESTORE = "复供";

    /** 阀门动作类别 */
    public static final String KIND_PLAN_CLOSE = "计划关闭";
    public static final String KIND_ALREADY_CLOSED = "原本关闭";
    public static final String KIND_MUST_OPEN = "必须开启";
    public static final String KIND_PLAN_OPEN = "计划开启";

    public static final List<String> ISO_ACTION_KINDS =
            List.of(KIND_PLAN_CLOSE, KIND_ALREADY_CLOSED, KIND_MUST_OPEN, KIND_PLAN_OPEN);

    /** 步骤状态 */
    public static final List<String> ISO_STEP_STATUS =
            List.of("待执行", "已完成", "跳过", "偏差", "冲突", "暂停");

    public static boolean isValveClosed(String openState) {
        return "关闭".equals(openState);
    }

    public static boolean isValidIsoPhase(String value) {
        return value != null && ISO_PHASES.contains(value);
    }

    public static boolean isValidIsoStrategy(String value) {
        return value != null && ISO_STRATEGIES.contains(value);
    }

    // ---------------- 室温抽测 ----------------
    /** 室温达标下限（℃），低于该值判不达标 */
    public static final BigDecimal MIN_QUALIFIED_TEMP = BigDecimal.valueOf(18.0);

    /** 舒适上限（℃），高于该值判过热 */
    public static final BigDecimal MAX_COMFORT_TEMP = BigDecimal.valueOf(22.0);

    // ---------------- 运行参数 ----------------
    /** 一次侧设计供回温差（℃），负荷率 = 实际温差 / 设计温差 */
    public static final BigDecimal DESIGN_DELTA_T = BigDecimal.valueOf(25.0);

    /** 负荷率低于该值视为低负荷（环蓝） */
    public static final BigDecimal LOAD_LOW = BigDecimal.valueOf(0.60);

    /** 负荷率高于该值视为超负荷（环红） */
    public static final BigDecimal LOAD_HIGH = BigDecimal.valueOf(1.00);

    /** 二次侧供回温差允许波动上限（℃），超出在登记时给出提示但放行 */
    public static final BigDecimal SECONDARY_DELTA_LIMIT = BigDecimal.valueOf(15.0);

    // ---------------- 报修工单 ----------------
    public static final List<String> FAULT_TYPES = List.of("不热", "漏水", "异响", "气堵");

    public static final List<String> ORDER_STATUS = List.of("待派工", "处理中", "已闭环");

    /** 工单状态推进顺序 */
    public static final List<String> ORDER_FLOW = List.of("待派工", "处理中", "已闭环");

    /** 室温不达标一键生成的工单类型 */
    public static final String FAULT_FROM_TEMP = "不热";

    /** 允许跨相邻状态推进的兜底提示 */
    public static final String ORDER_ASSIGNED_STATUS = "处理中";

    // ---------------- 缴费 ----------------
    public static final List<String> PAY_STATUS = List.of("已缴", "部分", "欠费");

    /** 用户供热状态（报停 / 恢复） */
    public static final List<String> SUPPLY_STATUS = List.of("正常供热", "已报停");

    /** 欠费零点阈值（元），小于等于该值视为已缴清 */
    public static final BigDecimal ARREARS_EPSILON = BigDecimal.valueOf(0.01);

    /** Redis 缓存：欠费排行 */
    public static final String CACHE_ARREARS_RANK = "heat:supply:cache:arrears:rank";

    /** Redis 缓存：运行总览 */
    public static final String CACHE_OVERVIEW = "heat:supply:cache:overview";

    private static final Set<String> CLOSED_EXEMPT = Set.of("已闭环");

    // ---------------- 取值方法 ----------------

    public static boolean isValidFuelType(String value) {
        return value != null && FUEL_TYPES.contains(value);
    }

    public static boolean isValidSourceStatus(String value) {
        return value != null && SOURCE_STATUS.contains(value);
    }

    public static boolean isValidStationStatus(String value) {
        return value != null && STATION_STATUS.contains(value);
    }

    public static boolean isValidSegmentStatus(String value) {
        return value != null && SEGMENT_STATUS.contains(value);
    }

    public static boolean isValidInsulation(String value) {
        return value != null && INSULATION_STATUS.contains(value);
    }

    public static boolean isValidLayingType(String value) {
        return value != null && LAYING_TYPES.contains(value);
    }

    public static boolean isValidOpenState(String value) {
        return value != null && VALVE_OPEN_STATE.contains(value);
    }

    public static boolean isValidValveRepairStatus(String value) {
        return value != null && VALVE_REPAIR_STATUS.contains(value);
    }

    public static boolean isValidFaultType(String value) {
        return value != null && FAULT_TYPES.contains(value);
    }

    public static boolean isValidOrderStatus(String value) {
        return value != null && ORDER_STATUS.contains(value);
    }

    public static boolean isValidPayStatus(String value) {
        return value != null && PAY_STATUS.contains(value);
    }

    public static boolean isValidSupplyStatus(String value) {
        return value != null && SUPPLY_STATUS.contains(value);
    }

    /** 是否已闭环 */
    public static boolean isOrderClosed(String status) {
        return status != null && CLOSED_EXEMPT.contains(status);
    }

    /** 按燃料类型给出参考热价 */
    public static BigDecimal unitPriceOf(String fuelType) {
        return UNIT_PRICE_BY_FUEL.getOrDefault(fuelType, BigDecimal.valueOf(24.00));
    }

    /** 室温是否达标：低于 18℃ 判不达标 */
    public static boolean isTempQualified(BigDecimal temperature) {
        return temperature != null && temperature.compareTo(MIN_QUALIFIED_TEMP) >= 0;
    }

    /** 室温是否过热：高于 22℃ */
    public static boolean isTempOverheated(BigDecimal temperature) {
        return temperature != null && temperature.compareTo(MAX_COMFORT_TEMP) > 0;
    }

    /** 应收金额 = 计费面积 × 单价（保留两位） */
    public static BigDecimal calcReceivable(BigDecimal area, BigDecimal unitPrice) {
        if (area == null || unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return area.multiply(unitPrice).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 按实收金额推导缴费状态 */
    public static String derivePayStatus(BigDecimal receivable, BigDecimal paid) {
        BigDecimal safeReceivable = receivable == null ? BigDecimal.ZERO : receivable;
        BigDecimal safePaid = paid == null ? BigDecimal.ZERO : paid;
        if (safePaid.compareTo(BigDecimal.ZERO) <= 0) {
            return "欠费";
        }
        if (safePaid.compareTo(safeReceivable) >= 0) {
            return "已缴";
        }
        return "部分";
    }
}
