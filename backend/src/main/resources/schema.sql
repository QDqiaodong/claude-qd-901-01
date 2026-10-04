-- 城市集中供热 · 热源与管网调度管理
-- 库名 heat_supply；application.yml 里 ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 关键：镜像构建期跑 initdb 时 mysql 客户端默认 latin1，会把 UTF-8 中文存成双编码，
--       因此每个 .sql 文件的第一句必须是 SET NAMES utf8mb4。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

CREATE DATABASE IF NOT EXISTS heat_supply
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE heat_supply;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS resident_merge_log;
DROP TABLE IF EXISTS isolation_step;
DROP TABLE IF EXISTS isolation_plan;
DROP TABLE IF EXISTS heating_fee;
DROP TABLE IF EXISTS repair_order;
DROP TABLE IF EXISTS room_temp_sample;
DROP TABLE IF EXISTS resident;
DROP TABLE IF EXISTS valve_well;
DROP TABLE IF EXISTS pipe_segment;
DROP TABLE IF EXISTS heat_station;
DROP TABLE IF EXISTS heat_source;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 热源厂 ----------------
CREATE TABLE heat_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_name VARCHAR(64) NOT NULL UNIQUE,
    capacity_mw DECIMAL(10, 2) COMMENT '装机容量 MW',
    fuel_type VARCHAR(16) COMMENT '燃煤 / 燃气 / 热电联产',
    source_status VARCHAR(16) COMMENT '运行 / 检修 / 停用',
    manager VARCHAR(32),
    address VARCHAR(128),
    commission_date DATE,
    root_well_no VARCHAR(32) COMMENT '热源出厂管接入管网的根井号（拓扑试算的注水起点）'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 换热站 ----------------
CREATE TABLE heat_station (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    station_code VARCHAR(32) NOT NULL UNIQUE,
    station_name VARCHAR(64),
    heat_source_id BIGINT,
    area_wan_m2 DECIMAL(10, 2) COMMENT '供热面积 万㎡',
    primary_supply_temp DECIMAL(5, 1) COMMENT '一次侧供水温度 ℃',
    primary_return_temp DECIMAL(5, 1) COMMENT '一次侧回水温度 ℃',
    secondary_supply_temp DECIMAL(5, 1) COMMENT '二次侧供水温度 ℃',
    secondary_return_temp DECIMAL(5, 1) COMMENT '二次侧回水温度 ℃',
    design_delta_t DECIMAL(5, 1) DEFAULT 25.0 COMMENT '一次侧设计供回温差 ℃',
    pump_count INT COMMENT '循环泵装机台数',
    running_pump_count INT COMMENT '当前运行循环泵台数',
    station_status VARCHAR(16) COMMENT '运行 / 检修 / 停用',
    manager VARCHAR(32),
    address VARCHAR(128),
    last_maintain_date DATE,
    tap_well_no VARCHAR(32) COMMENT '一次侧并网阀门井号：该井在拓扑上不可达热源即判停暖',
    KEY idx_station_source (heat_source_id),
    CONSTRAINT fk_station_source FOREIGN KEY (heat_source_id) REFERENCES heat_source (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 管网段 ----------------
CREATE TABLE pipe_segment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seg_code VARCHAR(32) NOT NULL UNIQUE,
    start_well_no VARCHAR(32) COMMENT '起点井号',
    end_well_no VARCHAR(32) COMMENT '终点井号',
    diameter_dn INT COMMENT '管径 DN',
    length_m DECIMAL(10, 1) COMMENT '长度 m',
    laying_type VARCHAR(16) COMMENT '直埋 / 架空 / 管沟',
    commission_year INT COMMENT '投运年份',
    insulation_status VARCHAR(16) COMMENT '完好 / 破损',
    segment_status VARCHAR(16) COMMENT '运行 / 检修 / 停用',
    map_points VARCHAR(255) COMMENT '平面示意图折线坐标',
    remark VARCHAR(128)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 阀门井 ----------------
CREATE TABLE valve_well (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    well_no VARCHAR(32) NOT NULL UNIQUE,
    well_location VARCHAR(128),
    valve_caliber_dn INT COMMENT '阀门口径 DN',
    open_state VARCHAR(16) COMMENT '全开 / 半开 / 关闭',
    repair_status VARCHAR(16) COMMENT '正常 / 检修中',
    segment_id BIGINT,
    last_check_date DATE,
    remark VARCHAR(128),
    map_x INT COMMENT '平面示意图横坐标',
    map_y INT COMMENT '平面示意图纵坐标',
    inoperable_flag TINYINT(1) DEFAULT 0 COMMENT '现场不可操作：检修卡涩/被占压/泄漏区，试算不得列入计划关断',
    egress_guard TINYINT(1) DEFAULT 0 COMMENT '人员撤离安全门：关断序列里必须最后关闭、复供最先开启',
    KEY idx_well_segment (segment_id),
    CONSTRAINT fk_well_segment FOREIGN KEY (segment_id) REFERENCES pipe_segment (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 热用户 ----------------
-- meter_no 不再加数据库唯一约束：重复档案归并后待归并档案转为只读归档，同一热表号会同时出现在
-- 保留档案（有效）与归档档案上；唯一性由应用层对「未归档」档案保证。
CREATE TABLE resident (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community VARCHAR(64) COMMENT '小区',
    building_no VARCHAR(32) COMMENT '楼栋',
    unit_no VARCHAR(32) COMMENT '单元',
    room_no VARCHAR(32) COMMENT '房号',
    area_m2 DECIMAL(8, 2) COMMENT '建筑面积 ㎡',
    meter_no VARCHAR(32) COMMENT '热表编号（归并后保留档案与归档档案同号，由应用层对未归档档案校验唯一）',
    total_heat_gj DECIMAL(10, 2) COMMENT '累计用热量 GJ',
    station_id BIGINT,
    supply_status VARCHAR(16) COMMENT '正常供热 / 已报停',
    contact_phone VARCHAR(24),
    archived TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为归并后的只读归档档案：1=只读',
    merged_into_id BIGINT COMMENT '归并后跳转到的保留档案 id',
    merged_at DATETIME COMMENT '归并时间',
    legacy_code VARCHAR(32) COMMENT '旧档案编号（默认 R+id，可按旧编号检索落到保留档案）',
    KEY idx_resident_station (station_id),
    KEY idx_resident_merged (archived, merged_into_id),
    KEY idx_resident_legacy (legacy_code),
    KEY idx_resident_meter (meter_no),
    CONSTRAINT fk_resident_station FOREIGN KEY (station_id) REFERENCES heat_station (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 室温抽测 ----------------
CREATE TABLE room_temp_sample (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resident_id BIGINT NOT NULL,
    sample_date DATE COMMENT '抽测日期',
    temperature DECIMAL(4, 1) COMMENT '室温 ℃',
    qualified TINYINT(1) COMMENT '是否达标：低于 18℃ 判不达标',
    household_name VARCHAR(32) COMMENT '住户姓名',
    surveyor VARCHAR(32) COMMENT '抽测人',
    remark VARCHAR(128),
    KEY idx_sample_resident (resident_id),
    CONSTRAINT fk_sample_resident FOREIGN KEY (resident_id) REFERENCES resident (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 报修工单 ----------------
CREATE TABLE repair_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    reported_date DATE,
    station_id BIGINT,
    resident_id BIGINT,
    fault_type VARCHAR(16) COMMENT '不热 / 漏水 / 异响 / 气堵',
    assignee VARCHAR(32) COMMENT '派工人',
    handle_result VARCHAR(255) COMMENT '处理结果',
    order_status VARCHAR(16) COMMENT '待派工 / 处理中 / 已闭环',
    closed_date DATE,
    KEY idx_order_station (station_id),
    KEY idx_order_resident (resident_id),
    CONSTRAINT fk_order_station FOREIGN KEY (station_id) REFERENCES heat_station (id),
    CONSTRAINT fk_order_resident FOREIGN KEY (resident_id) REFERENCES resident (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 采暖费收缴 ----------------
CREATE TABLE heating_fee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fee_year INT COMMENT '收费年度',
    resident_id BIGINT NOT NULL,
    billing_area DECIMAL(8, 2) COMMENT '计费面积 ㎡',
    unit_price DECIMAL(6, 2) COMMENT '单价 元/㎡',
    receivable DECIMAL(12, 2) COMMENT '应收金额',
    paid DECIMAL(12, 2) COMMENT '实收金额',
    arrears DECIMAL(12, 2) COMMENT '欠费金额',
    pay_status VARCHAR(16) COMMENT '已缴 / 部分 / 欠费（待财务复核期间保留原值）',
    last_pay_date DATE,
    remark VARCHAR(128),
    review_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '待财务复核：同年度两边记录且任一方已有实收时置 1',
    reviewed TINYINT(1) NOT NULL DEFAULT 0 COMMENT '财务是否已复核：复核前禁止登记该年度缴费',
    merge_year_dup TINYINT(1) NOT NULL DEFAULT 0 COMMENT '归并产生的同年度并挂记录（原待归并档案台账）',
    merge_log_id BIGINT COMMENT '由哪次归并产生/改动',
    KEY idx_fee_resident (resident_id),
    KEY idx_fee_review (review_required, reviewed),
    CONSTRAINT fk_fee_resident FOREIGN KEY (resident_id) REFERENCES resident (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 重复热用户档案归并审计 ----------------
CREATE TABLE resident_merge_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    merge_no VARCHAR(32) NOT NULL UNIQUE COMMENT '归并单号 MG-yyyyMMdd-序号',
    kept_resident_id BIGINT COMMENT '保留档案 id（失败发生在取档之前可空）',
    away_resident_id BIGINT COMMENT '待归并（归档）档案 id（失败发生在取档之前可空）',
    kept_snapshot VARCHAR(255) COMMENT '保留档案归并前快照：地址/热表号/换热站/供热状态',
    away_snapshot VARCHAR(255) COMMENT '待归并档案归并前快照',
    chosen_supply_status VARCHAR(16) COMMENT '供热状态不一致时工作人员明确采用的状态',
    fee_plan_json TEXT COMMENT '分年度处置方案：重算/保留待复核/原样迁移',
    sample_count INT NOT NULL DEFAULT 0 COMMENT '改挂的室温抽测条数',
    order_count INT NOT NULL DEFAULT 0 COMMENT '改挂的报修记录条数',
    fee_count INT NOT NULL DEFAULT 0 COMMENT '迁移/处置的收费记录条数',
    status VARCHAR(16) NOT NULL DEFAULT '已归并' COMMENT '已归并 / 失败回退',
    fail_reason VARCHAR(255) COMMENT '失败回退原因（失败记录由独立事务写入）',
    operator VARCHAR(32) COMMENT '经办人',
    created_at DATETIME NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 管网隔离方案 ----------------
CREATE TABLE isolation_plan (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_no VARCHAR(32) NOT NULL UNIQUE COMMENT '方案编号 ISO-yyyyMMdd-序号',
    fault_segment_id BIGINT NOT NULL COMMENT '故障管段',
    fault_reason VARCHAR(255) COMMENT '故障现象 / 填报原因',
    phase VARCHAR(16) NOT NULL COMMENT '试算中/待执行/执行中/已隔离/已暂停/已失败/已复供/已废止',
    strategy VARCHAR(16) NOT NULL DEFAULT '最小隔离' COMMENT '最小隔离 / 扩大隔离 / 拒绝启用',
    isolated_proven TINYINT(1) DEFAULT 0 COMMENT '最近一次证明结果：故障段是否已与运行热源侧隔开',
    proof_note VARCHAR(255) COMMENT '证明说明：阻断边界 / 失败原因',
    blocked_well_nos VARCHAR(512) COMMENT '试算阻断边界阀门井号，逗号分隔',
    enclosed_segment_codes VARCHAR(512) COMMENT '被围进隔离区的管段编号，逗号分隔',
    forced_open_well_nos VARCHAR(512) COMMENT '必须开启的阀门井号，逗号分隔',
    affected_station_codes VARCHAR(512) COMMENT '受影响换热站编号快照，逗号分隔',
    affected_station_count INT DEFAULT 0 COMMENT '受影响换热站数量快照',
    affected_area_wan_m2 DECIMAL(10, 2) DEFAULT 0 COMMENT '受影响供热面积 万㎡ 快照',
    affected_households INT DEFAULT 0 COMMENT '受影响居民户数快照',
    maintainable_station_codes VARCHAR(512) COMMENT '仍有保供路径的换热站编号，逗号分隔',
    blocked_choice_note VARCHAR(512) COMMENT '扩大停暖 vs 拒绝启用 的取舍说明',
    option_expand TINYINT(1) DEFAULT 0 COMMENT '是否存在「扩大隔离」可选结果',
    option_reject TINYINT(1) DEFAULT 0 COMMENT '是否存在「拒绝启用等待替代路径」可选结果',
    trial_note VARCHAR(1024) COMMENT '最近一次拓扑试算结论与人工调整记录',
    network_options_json TEXT COMMENT '本次试算采用的现场阀门状态覆盖（人工调整/现场不可操作）JSON',
    finish_note VARCHAR(1024) COMMENT '收口记录：完成情况、偏差原因、处置结果',
    restored_proven TINYINT(1) COMMENT '复供后证明：仍不可达清单是否已列出',
    created_at DATETIME,
    activated_at DATETIME,
    isolated_at DATETIME,
    finished_at DATETIME
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------- 隔离方案阀门步骤 ----------------
CREATE TABLE isolation_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    step_no INT NOT NULL COMMENT '执行顺序，从 1 开始',
    stage VARCHAR(16) NOT NULL COMMENT '隔离 / 复供',
    valve_well_id BIGINT NOT NULL,
    well_no VARCHAR(32) NOT NULL,
    action_kind VARCHAR(24) NOT NULL COMMENT '计划关闭/原本关闭/必须开启/计划开启',
    planned_state VARCHAR(16) NOT NULL COMMENT '计划动作后状态：全开/半开/关闭',
    from_state VARCHAR(16) COMMENT '方案生成时的现场基线状态',
    egress_guard TINYINT(1) DEFAULT 0 COMMENT '人员撤离安全门步骤',
    step_status VARCHAR(16) NOT NULL DEFAULT '待执行' COMMENT '待执行/已完成/跳过/偏差/冲突/暂停',
    actual_state VARCHAR(16) COMMENT '现场回报实际状态',
    confirmed_by VARCHAR(32) COMMENT '确认人',
    confirmed_at DATETIME,
    deviation_reason VARCHAR(255) COMMENT '偏差原因：未关闭/他人改动/现场不可操作',
    note VARCHAR(255),
    UNIQUE KEY uk_plan_stage_no (plan_id, stage, step_no),
    KEY idx_step_well (valve_well_id),
    CONSTRAINT fk_step_plan FOREIGN KEY (plan_id) REFERENCES isolation_plan (id),
    CONSTRAINT fk_step_well FOREIGN KEY (valve_well_id) REFERENCES valve_well (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- 种子数据
-- =====================================================================

-- 热源厂 5 座（root_well_no 为出厂管接入一次网的根井，拓扑试算的注水起点；仅城东热源直接注入一次网）
INSERT INTO heat_source (id, source_name, capacity_mw, fuel_type, source_status, manager, address, commission_date, root_well_no) VALUES
    (1, '城东热电厂', 1450.00, '热电联产', '运行', '陈国栋', '东环路 118 号', '1998-11-15', 'J101'),
    (2, '城北热源厂', 860.00, '燃煤', '运行', '李柏林', '北三环路 26 号', '2004-10-28', NULL),
    (3, '西城燃气热源厂', 420.00, '燃气', '运行', '王海涛', '西山路 9 号', '2012-11-01', NULL),
    (4, '南岭调峰热源厂', 320.00, '燃气', '检修', '赵鹏', '南岭工业区 3 号', '2016-12-20', NULL),
    (5, '向阳路备用热源', 180.00, '燃煤', '停用', '孙立军', '向阳路 47 号', '2001-10-15', NULL);

-- 换热站 10 座（一次侧温差 / 设计温差 = 负荷率，覆盖低负荷、正常、超负荷；tap_well_no 为一次侧并网井）
INSERT INTO heat_station (id, station_code, station_name, heat_source_id, area_wan_m2, primary_supply_temp,
                          primary_return_temp, secondary_supply_temp, secondary_return_temp, design_delta_t,
                          pump_count, running_pump_count, station_status, manager, address, last_maintain_date,
                          tap_well_no) VALUES
    (1,  'HS-01', '朝阳里换热站',   1, 26.50, 94.0, 68.0, 52.0, 42.0, 25.0, 4, 3, '运行', '张涛', '朝阳里小区东门南侧', '2025-10-08', 'J101'),
    (2,  'HS-02', '和平新村换热站', 1, 18.20, 90.0, 71.0, 48.0, 40.0, 25.0, 3, 2, '运行', '刘敏', '和平新村 5 号楼旁', '2025-09-22', 'J102'),
    (3,  'HS-03', '滨河家园换热站', 1, 21.60, 86.0, 73.0, 46.0, 39.0, 25.0, 3, 2, '运行', '周正阳', '滨河家园地下车库旁', '2025-11-03', 'J106'),
    (4,  'HS-04', '兴安里换热站',   2, 15.80, 92.0, 70.0, 50.0, 41.0, 25.0, 3, 3, '运行', '吴国庆', '兴安里小区西配房', '2025-10-19', 'J105'),
    (5,  'HS-05', '北苑小区换热站', 2, 12.40, 78.0, 76.0, 41.0, 38.0, 25.0, 2, 1, '检修', '郑立群', '北苑小区锅炉房旧址', '2025-12-11', 'J110'),
    (6,  'HS-06', '站前广场换热站', 2,  9.60,  0.0,  0.0,  0.0,  0.0, 25.0, 2, 0, '停用', '马晓东', '站前广场地下夹层', '2025-08-30', 'J111'),
    (7,  'HS-07', '加州花园换热站', 3, 23.30, 91.0, 67.0, 51.0, 42.0, 25.0, 4, 3, '运行', '李成志', '加州花园 12 号楼后', '2025-10-25', 'J114'),
    (8,  'HS-08', '西港新居换热站', 3, 17.90, 85.0, 69.0, 47.0, 40.0, 25.0, 3, 2, '运行', '何雅丽', '西港新居泵房', '2025-11-17', 'J108'),
    (9,  'HS-09', '南岭公建换热站', 4, 14.20, 80.0, 72.5, 43.0, 38.5, 25.0, 2, 1, '检修', '赵鹏', '南岭公建裙楼一层', '2025-12-28', 'J109'),
    (10, 'HS-10', '向阳街换热站',   5,  8.30,  0.0,  0.0,  0.0,  0.0, 25.0, 2, 0, '停用', '孙立军', '向阳街 47 号院内', '2025-07-14', 'J110');

-- 管网段 13 段（11~13 为环网联络线，与主干 PS-01~07 构成 J101→J102→…→J108→J114→J112→J102 环网；含 map_points 供平面示意图绘制）
INSERT INTO pipe_segment (id, seg_code, start_well_no, end_well_no, diameter_dn, length_m, laying_type,
                          commission_year, insulation_status, segment_status, map_points, remark) VALUES
    (1,  'PS-01', 'J101', 'J102', 800, 486.0, '直埋', 2010, '完好', '运行', '80,120 320,120', '城东电厂出厂主干线，双管敷设'),
    (2,  'PS-02', 'J102', 'J103', 800, 512.0, '直埋', 2010, '完好', '运行', '320,120 560,120', '沿东环路北侧绿化带敷设'),
    (3,  'PS-03', 'J103', 'J104', 600, 438.0, '架空', 2013, '破损', '运行', '560,120 800,120', '架空段保温层外护破损待修'),
    (4,  'PS-04', 'J104', 'J105', 600, 356.0, '管沟', 2013, '完好', '运行', '800,120 960,120', '环网顶臂，2026 年计划大修已完成'),
    (5,  'PS-05', 'J105', 'J106', 500, 402.0, '直埋', 2016, '完好', '运行', '960,120 960,300', '向南转输支线'),
    (6,  'PS-06', 'J106', 'J107', 500, 465.0, '直埋', 2016, '破损', '运行', '960,300 720,300', '破损段已列入年度大修'),
    (7,  'PS-07', 'J107', 'J108', 400, 428.0, '管沟', 2018, '完好', '运行', '720,300 480,300', '西城老旧管网改造段'),
    (8,  'PS-08', 'J108', 'J109', 300, 396.0, '直埋', 2018, '完好', '运行', '480,300 240,300', '二次管网环网联络线'),
    (9,  'PS-09', 'J109', 'J110', 300, 288.0, '架空', 2019, '完好', '停用', '240,300 80,300', '停用待并网'),
    (10, 'PS-10', 'J110', 'J111', 200, 352.0, '直埋', 2021, '破损', '运行', '80,300 80,440 300,440', '末端支线，DN200 接加州花园'),
    (11, 'PS-11', 'J102', 'J112', 500, 612.0, '直埋', 2019, '完好', '运行', '320,120 320,240 560,240', '东环-西城环网联络线（北环臂）'),
    (12, 'PS-12', 'J112', 'J114', 500, 388.0, '管沟', 2019, '完好', '运行', '560,240 520,270', '环网西线，与 PS-06/07 互为备用'),
    (13, 'PS-13', 'J114', 'J108', 400, 246.0, '管沟', 2019, '完好', '运行', '520,270 480,300', 'J114 汇接 J108 的短接臂');

-- 阀门井 13 座（坐标落在管段折线端点；inoperable_flag=现场不可操作，egress_guard=人员撤离安全门）
INSERT INTO valve_well (id, well_no, well_location, valve_caliber_dn, open_state, repair_status, segment_id,
                        last_check_date, remark, map_x, map_y, inoperable_flag, egress_guard) VALUES
    (1,  'J101', '东环路与朝阳路交口东北角', 800, '全开',   '正常',   1,  '2026-02-05', '出厂总阀门，调节一次侧总流量',   80,  120, 0, 0),
    (2,  'J102', '东环路中段绿化带',         800, '全开',   '正常',   2,  '2026-02-05', '主干分段阀兼环网汇接节点',      320, 120, 0, 0),
    (3,  'J103', '东环路南段人行道',         600, '全开',   '正常',   3,  '2026-02-08', '架空管补偿器前阀门',            560, 120, 0, 0),
    (4,  'J104', '兴安里街北侧',             600, '全开',   '正常',   4,  '2026-02-12', '环网顶臂分段阀',                800, 120, 0, 0),
    (5,  'J105', '南岭工业区入口',           600, '全开',   '正常',   4,  '2026-02-12', '顶臂末端转输阀',                960, 120, 0, 0),
    (6,  'J106', '西山路与南环路交口',       500, '全开',   '正常',   5,  '2026-02-09', '转输支线控制阀',                960, 300, 0, 0),
    (7,  'J107', '西山路中段消防通道',       500, '全开',   '正常',   6,  '2026-02-15', '消防通道撤离路线上的安全门',    720, 300, 0, 1),
    (8,  'J108', '西港新居停车场入口',       400, '全开',   '正常',   7,  '2026-02-10', '西区环网汇接节点',              480, 300, 0, 0),
    (9,  'J109', '和平新村南侧巷道',         300, '全开',   '正常',   8,  '2026-02-10', '低区回水调节',                  240, 300, 0, 0),
    (10, 'J110', '向阳街西侧便道',           300, '关闭',   '正常',   9,  '2026-01-28', '停用管段隔离阀',                80,  300, 0, 0),
    (11, 'J111', '加州花园 12 号楼前',       200, '半开',   '正常',   10, '2026-02-18', '末端支线调节，防止远端不热',    300, 440, 0, 0),
    (12, 'J112', '东环路南匝道绿化岛',       500, '关闭',   '正常',   11, '2026-02-11', '北环联络阀，保供时必须开启',    560, 240, 0, 0),
    (13, 'J114', '南环路抢修占道区',         500, '半开',   '检修中', 12, '2026-02-20', '井室被占压，现场无法操作',      520, 270, 1, 0);

-- 热用户 15 户（朝阳里小区 3 栋 × 4 单元构成 12 格矩阵）
INSERT INTO resident (id, community, building_no, unit_no, room_no, area_m2, meter_no, total_heat_gj,
                      station_id, supply_status, contact_phone) VALUES
    (1,  '朝阳里小区', '1', '1', '101', 89.60,  'RB20260001', 42.35, 1, '正常供热', '13805310001'),
    (2,  '朝阳里小区', '1', '2', '101', 68.40,  'RB20260002', 33.18, 1, '正常供热', '13805310002'),
    (3,  '朝阳里小区', '1', '3', '101', 112.50, 'RB20260003', 55.62, 1, '正常供热', '13805310003'),
    (4,  '朝阳里小区', '1', '4', '101', 76.20,  'RB20260004', 37.90, 1, '正常供热', '13805310004'),
    (5,  '朝阳里小区', '2', '1', '102', 94.80,  'RB20260005', 46.12, 1, '正常供热', '13805310005'),
    (6,  '朝阳里小区', '2', '2', '102', 130.00, 'RB20260006', 61.44, 1, '正常供热', '13805310006'),
    (7,  '朝阳里小区', '2', '3', '102', 105.00, 'RB20260007', 52.08, 2, '正常供热', '13805310007'),
    (8,  '朝阳里小区', '2', '4', '102', 82.50,  'RB20260008', 40.66, 2, '正常供热', '13805310008'),
    (9,  '朝阳里小区', '3', '1', '201', 77.30,  'RB20260009', 36.22, 2, '正常供热', '13805310009'),
    (10, '朝阳里小区', '3', '2', '201', 120.00, 'RB20260010', 58.90, 2, '正常供热', '13805310010'),
    (11, '朝阳里小区', '3', '3', '201', 65.00,  'RB20260011', 31.05, 2, '正常供热', '13805310011'),
    (12, '朝阳里小区', '3', '4', '201', 99.20,  'RB20260012', 48.77, 2, '正常供热', '13805310012'),
    (13, '滨河家园',   '5', '1', '301', 88.00,  'RB20260013', 41.20, 3, '正常供热', '13805310013'),
    (14, '滨河家园',   '5', '1', '401', 71.60,  'RB20260014', 34.55, 3, '正常供热', '13805310014'),
    (15, '滨河家园',   '6', '1', '302', 108.40, 'RB20260015', 52.31, 3, '已报停',   '13805310015');

-- 室温抽测 15 条（含低于 18℃ 的不达标记录与高于 22℃ 的过热记录）
INSERT INTO room_temp_sample (id, resident_id, sample_date, temperature, qualified, household_name, surveyor, remark) VALUES
    (1,  1,  '2026-02-05', 21.4, 1, '张建平', '刘敏', NULL),
    (2,  1,  '2026-01-22', 20.8, 1, '张建平', '刘敏', NULL),
    (3,  2,  '2026-02-05', 19.6, 1, '刘桂芳', '刘敏', NULL),
    (4,  3,  '2026-02-05', 17.2, 0, '王志远', '张涛', '末端用户，二次侧流量偏低'),
    (5,  3,  '2026-01-22', 18.9, 1, '王志远', '张涛', NULL),
    (6,  4,  '2026-02-05', 23.1, 1, '陈淑兰', '张涛', '顶楼层，室温偏高'),
    (7,  5,  '2026-02-05', 16.8, 0, '李国强', '刘敏', '回水温度偏低'),
    (8,  6,  '2026-02-05', 20.2, 1, '赵秀英', '刘敏', NULL),
    (9,  7,  '2026-02-05', 17.5, 0, '孙明海', '周正阳', '疑似气堵'),
    (10, 7,  '2026-01-22', 19.1, 1, '孙明海', '周正阳', NULL),
    (11, 8,  '2026-02-05', 22.4, 1, '周玉梅', '周正阳', '阳光房，散热量偏大'),
    (12, 9,  '2026-02-05', 19.0, 1, '吴德明', '吴国庆', NULL),
    (13, 10, '2026-02-05', 21.8, 1, '郑丽华', '吴国庆', NULL),
    (14, 11, '2026-02-05', 18.4, 1, '马春生', '郑立群', NULL),
    (15, 12, '2026-02-05', 17.9, 0, '胡永康', '郑立群', '入户过滤器堵塞待清');

-- 报修工单 9 条（待派工 / 处理中 / 已闭环 各若干，HS-01 留有未闭环工单）
INSERT INTO repair_order (id, order_no, reported_date, station_id, resident_id, fault_type, assignee,
                          handle_result, order_status, closed_date) VALUES
    (1, 'BX20260202001', '2026-02-02', 1, NULL, '漏水', '',       '',                                 '待派工', NULL),
    (2, 'BX20260203001', '2026-02-03', 1, 4,    '气堵', '牟继伟', '',                                 '处理中', NULL),
    (3, 'BX20260205001', '2026-02-05', 1, 6,    '异响', '孙鹏',   '紧固循环泵地脚螺栓，异响消失',     '已闭环', '2026-02-06'),
    (4, 'BX20260206001', '2026-02-06', 2, 8,    '不热', '赵立春', '',                                 '处理中', NULL),
    (5, 'BX20260208001', '2026-02-08', 2, NULL, '漏水', '',       '',                                 '待派工', NULL),
    (6, 'BX20260210001', '2026-02-10', 3, 11,   '气堵', '孙鹏',   '顶层排气阀放气后恢复',             '已闭环', '2026-02-11'),
    (7, 'BX20260212001', '2026-02-12', 4, NULL, '异响', '牟继伟', '',                                 '待派工', NULL),
    (8, 'BX20260215001', '2026-02-15', 7, 13,   '不热', '赵立春', '清洗除污器，二次侧流量回升',       '已闭环', '2026-02-16'),
    (9, 'BX20260218001', '2026-02-18', 7, 14,   '不热', '',       '',                                 '待派工', NULL);

-- 采暖费收缴 12 条（2026 收费年度，含已缴 / 部分 / 欠费）
INSERT INTO heating_fee (id, fee_year, resident_id, billing_area, unit_price, receivable, paid, arrears,
                         pay_status, last_pay_date, remark) VALUES
    (1,  2026, 1,  89.60,  25.40, 2275.84, 2275.84,    0.00, '已缴', '2026-01-12', NULL),
    (2,  2026, 2,  68.40,  25.40, 1737.36,  800.00,  937.36, '部分', '2026-02-03', '用户承诺采暖季结清'),
    (3,  2026, 3,  112.50, 25.40, 2857.50,    0.00, 2857.50, '欠费', NULL,         '拒缴：认为室温不达标'),
    (4,  2026, 4,  76.20,  25.40, 1935.48, 1935.48,    0.00, '已缴', '2026-01-08', NULL),
    (5,  2026, 5,  94.80,  25.40, 2407.92, 1000.00, 1407.92, '部分', '2026-01-25', NULL),
    (6,  2026, 6,  130.00, 25.40, 3302.00,    0.00, 3302.00, '欠费', NULL,         '长期空置，催缴中'),
    (7,  2026, 7,  105.00, 25.40, 2667.00,  500.00, 2167.00, '部分', '2026-02-06', NULL),
    (8,  2026, 8,  82.50,  25.40, 2095.50, 2095.50,    0.00, '已缴', '2026-01-16', NULL),
    (9,  2026, 9,  77.30,  25.40, 1963.42, 1500.00,  463.42, '部分', '2026-02-09', NULL),
    (10, 2026, 10, 120.00, 25.40, 3048.00,    0.00, 3048.00, '欠费', NULL,         '产权纠纷待核实'),
    (11, 2026, 11, 65.00,  25.40, 1651.00, 1651.00,    0.00, '已缴', '2026-01-20', NULL),
    (12, 2026, 12, 99.20,  25.40, 2519.68, 2519.68,    0.00, '已缴', '2026-01-27', NULL);

-- 重复热用户档案 5 户（用于「重复热用户档案归并」预检/阻断/处置演示，初始均为未归档状态）：
--   16 / 17：滨河家园 6-1-302 同地址、同热表号 RB20260016、同换热站 HS-03，供热状态冲突（正常供热 vs 已报停）；
--            两边 2026 年度都有台账且都未缴费 → 按保留档案计费面积重算；17 另有 2025 年度已缴台账 → 原样迁移。
--   18 与 13：滨河家园 5-1-301 同地址、同热表号 RB20260013、同换热站；13 的 2026 已缴清、18 的 2026 已收 600
--            → 两边原始金额都保留并挂保留档案，标待财务复核，复核前禁止登记该年度缴费。
--   19：地址与 1 相同（朝阳里 1-1-101）但热表号不同 → 热表冲突，必须停止归并。
--   20：地址与 7 相同（朝阳里 2-3-102）但所属换热站不同（HS-04 vs HS-02）→ 换热站冲突，必须停止归并。
INSERT INTO resident (id, community, building_no, unit_no, room_no, area_m2, meter_no, total_heat_gj,
                      station_id, supply_status, contact_phone) VALUES
    (16, '滨河家园', '6', '1', '302', 108.40, 'RB20260016', 49.80, 3, '正常供热', '13805310016'),
    (17, '滨河家园', '6', '1', '302', 98.00,  'RB20260016', 51.20, 3, '已报停',   '13805310016'),
    (18, '滨河家园', '5', '1', '301', 80.00,  'RB20260013', 39.60, 3, '正常供热', '13805310018'),
    (19, '朝阳里小区', '1', '1', '101', 89.60,  'RB20260099', 40.00, 1, '正常供热', '13905310019'),
    (20, '朝阳里小区', '2', '3', '102', 105.00, 'RB20260007', 50.00, 4, '正常供热', '13905310020');

UPDATE resident SET legacy_code = CONCAT('R', id) WHERE legacy_code IS NULL;

-- 重复档案相关室温抽测（归并后必须整体改挂到保留档案，业务编号/时间/结果不变）
INSERT INTO room_temp_sample (id, resident_id, sample_date, temperature, qualified, household_name, surveyor, remark) VALUES
    (16, 16, '2026-02-18', 20.5, 1, '陈立民', '周正阳', '新户号抽测'),
    (17, 17, '2026-01-20', 19.2, 1, '陈立民', '周正阳', '报停前最后一次抽测'),
    (18, 18, '2026-02-12', 17.6, 0, '马晓燕', '周正阳', '旧户名抽测，疑似同一住房重复建档');

-- 重复档案相关报修工单（17 号档案上有一条未闭环「不热」工单）
INSERT INTO repair_order (id, order_no, reported_date, station_id, resident_id, fault_type, assignee,
                          handle_result, order_status, closed_date) VALUES
    (10, 'BX20260220001', '2026-02-20', 3, 17, '不热', '', '', '待派工', NULL);

-- 重复档案相关采暖费台账（13~16）
INSERT INTO heating_fee (id, fee_year, resident_id, billing_area, unit_price, receivable, paid, arrears,
                         pay_status, last_pay_date, remark) VALUES
    (13, 2026, 16, 108.40, 25.40, 2753.36,    0.00, 2753.36, '欠费', NULL,         '新系统建档，未缴费'),
    (14, 2026, 17, 98.00,  25.40, 2489.20,    0.00, 2489.20, '欠费', NULL,         '旧台账手工录入，未缴费'),
    (15, 2026, 18, 80.00,  25.40, 2032.00,  600.00, 1432.00, '部分', '2026-02-10', '旧户头已收 600 元，待与正式户头核对'),
    (16, 2025, 17, 98.00,  24.00, 2352.00, 2352.00,    0.00, '已缴', '2025-12-30', '报停前年度已缴清');
