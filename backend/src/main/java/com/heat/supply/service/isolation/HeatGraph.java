package com.heat.supply.service.isolation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.heat.supply.common.Dict;
import com.heat.supply.domain.HeatSource;
import com.heat.supply.domain.HeatStation;
import com.heat.supply.domain.PipeSegment;
import com.heat.supply.domain.ValveWell;

/**
 * 管网拓扑图：阀门井为节点、管段为无向边，阀门在井位上阻断/连通，热源是注水根节点。
 * 边仅在管段「运行/检修」（停用边移除）且两端阀均未关闭时导通；
 * 阀的开闭态以试算请求的现场覆盖（人工调整 / 现场不可操作）优先，否则取库内实况。
 */
public class HeatGraph {

    /** 边（管段） */
    public static class Edge {
        public final PipeSegment segment;
        public final String a;
        public final String b;

        Edge(PipeSegment segment, String a, String b) {
            this.segment = segment;
            this.a = a;
            this.b = b;
        }

        String other(String wellNo) {
            return a.equals(wellNo) ? b : a;
        }
    }

    private final Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
    private final Map<String, ValveWell> wells = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<String, HeatSource> roots = new LinkedHashMap<>();
    private final List<HeatStation> stations = new ArrayList<>();

    /** 建图原始入参（供「移除故障边」后重建证明图） */
    private List<PipeSegment> rawSegments = new ArrayList<>();
    private List<ValveWell> rawWells = new ArrayList<>();
    private List<HeatSource> rawSources = new ArrayList<>();
    private List<HeatStation> rawStations = new ArrayList<>();
    private Map<String, String> rawOverrides = new LinkedHashMap<>();

    /** 试算采用的阀态（库内实况叠加现场覆盖后） */
    private final Map<String, String> effectiveState = new LinkedHashMap<>();

    public static HeatGraph build(List<PipeSegment> segments,
                                  List<ValveWell> valveWells,
                                  List<HeatSource> sources,
                                  List<HeatStation> heatStations,
                                  Map<String, String> stateOverrides,
                                  boolean faultRemoved) {
        Map<String, String> overrides = stateOverrides == null ? Map.of() : stateOverrides;
        HeatGraph graph = new HeatGraph();
        graph.rawSegments = new ArrayList<>(segments);
        graph.rawWells = new ArrayList<>(valveWells);
        graph.rawSources = new ArrayList<>(sources);
        graph.rawStations = new ArrayList<>(heatStations);
        graph.rawOverrides = new LinkedHashMap<>(overrides);
        for (ValveWell well : valveWells) {
            graph.wells.put(well.getWellNo(), well);
            graph.adjacency.putIfAbsent(well.getWellNo(), new ArrayList<>());
            String state = overrides.getOrDefault(well.getWellNo(), well.getOpenState());
            graph.effectiveState.put(well.getWellNo(), state);
        }
        for (HeatSource source : sources) {
            if (source.getRootWellNo() != null && graph.wells.containsKey(source.getRootWellNo())) {
                graph.roots.put(source.getRootWellNo(), source);
                graph.adjacency.putIfAbsent(source.getRootWellNo(), new ArrayList<>());
            }
        }
        for (HeatStation station : heatStations) {
            if (station.getTapWellNo() != null) {
                graph.adjacency.putIfAbsent(station.getTapWellNo(), new ArrayList<>());
                graph.stations.add(station);
            }
        }
        for (PipeSegment segment : segments) {
            if (segment.getStartWellNo() == null || segment.getEndWellNo() == null) {
                continue;
            }
            if ("停用".equals(segment.getSegmentStatus())) {
                continue;
            }
            if (faultRemoved) {
                // 证明图：调用方用 faultSegmentId 另行过滤（build 时拿不到 id 语义差异，由 buildWithout 完成）
                continue;
            }
            String a = segment.getStartWellNo();
            String b = segment.getEndWellNo();
            graph.adjacency.putIfAbsent(a, new ArrayList<>());
            graph.adjacency.putIfAbsent(b, new ArrayList<>());
            Edge edge = new Edge(segment, a, b);
            graph.edges.add(edge);
            graph.adjacency.get(a).add(edge);
            graph.adjacency.get(b).add(edge);
        }
        return graph;
    }

    /** 用当前图的原始入参重建，并把指定故障边排除在导通图外 */
    public HeatGraph rebuildWithoutFault(Long faultSegmentId) {
        List<PipeSegment> kept = new ArrayList<>();
        for (PipeSegment segment : rawSegments) {
            if (segment.getId() != null && segment.getId().equals(faultSegmentId)) {
                continue;
            }
            kept.add(segment);
        }
        return build(kept, rawWells, rawSources, rawStations, rawOverrides, false);
    }

    /** 用新的阀态覆盖重建（故障边仍移除） */
    public HeatGraph rebuildProof(Long faultSegmentId, Map<String, String> overrides) {
        List<PipeSegment> kept = new ArrayList<>();
        for (PipeSegment segment : rawSegments) {
            if (segment.getId() != null && segment.getId().equals(faultSegmentId)) {
                continue;
            }
            kept.add(segment);
        }
        return build(kept, rawWells, rawSources, rawStations, overrides, false);
    }

    /** 当前图建图时使用的阀态覆盖（供在其上叠加「必须开启」试算） */
    public Map<String, String> overrides() {
        return rawOverrides;
    }

    public ValveWell well(String wellNo) {
        return wells.get(wellNo);
    }

    public List<ValveWell> allWells() {
        return new ArrayList<>(wells.values());
    }

    public List<HeatStation> stations() {
        return stations;
    }

    public String stateOf(String wellNo) {
        return effectiveState.get(wellNo);
    }

    /** 该井在本次试算里是否不可操作：现场覆盖里标 inoperable，或库内本身不可操作/检修中 */
    public boolean isInoperable(String wellNo, Set<String> forcedInoperable) {
        if (forcedInoperable.contains(wellNo)) {
            return true;
        }
        ValveWell well = wells.get(wellNo);
        return well != null && Boolean.TRUE.equals(well.getInoperableFlag());
    }

    /** 边在给定「已关阀集合」下是否导通：只有阀门全关才阻断，半开仍可过流（保供/隔离判定） */
    public boolean conducts(Edge edge, Set<String> closedWells) {
        return !closedWells.contains(edge.a) && !closedWells.contains(edge.b);
    }

    private List<Edge> edgesOf(String wellNo) {
        return adjacency.getOrDefault(wellNo, Collections.emptyList());
    }

    /**
     * 从运行热源根井出发做 BFS。阻断集合 = 显式传入的 closedWells（方案边界）
     * ∪ 现场实况已关闭的井（半开仍过流，全关才阻断）。返回可达井集合。
     */
    public Set<String> reachableFromSources(Set<String> closedWells) {
        Set<String> blocked = new LinkedHashSet<>(closedWells);
        for (Map.Entry<String, String> entry : effectiveState.entrySet()) {
            if (Dict.isValveClosed(entry.getValue())) {
                blocked.add(entry.getKey());
            }
        }
        Set<String> visited = new LinkedHashSet<>();
        List<String> queue = new ArrayList<>();
        for (String root : roots.keySet()) {
            if (!blocked.contains(root)) {
                visited.add(root);
                queue.add(root);
            }
        }
        int head = 0;
        while (head < queue.size()) {
            String cur = queue.get(head++);
            for (Edge edge : edgesOf(cur)) {
                if (!conducts(edge, blocked)) {
                    continue;
                }
                String next = edge.other(cur);
                // 关阀井本身即阻断点：即使它连着另一条开边，也不允许当作可达节点，
                // 否则挂在该井的换热站会被误判为「仍有供热路径」。
                if (blocked.contains(next)) {
                    continue;
                }
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    public List<Edge> incidentEdges(String wellNo) {
        return edgesOf(wellNo);
    }
}
