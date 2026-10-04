import axios from 'axios'

// 统一走相对路径 /api：容器里由 nginx 反代到 backend:8080，本地开发由 vite proxy 转发
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

/** 剥掉 ApiResponse 外壳，失败时抛出后端 message 交给页面提示 */
function unwrap(promise) {
  return promise.then((res) => {
    const body = res.data
    if (body && body.ok === false) {
      throw new Error(body.message || '请求失败')
    }
    return body && body.data !== undefined ? body.data : body
  })
}

export const sourcesApi = {
  /** @FetchProfile：?profile=with-stations 启用命名抓取档案 JOIN 抓取换热站 */
  list: (profile) => unwrap(http.get('/sources', { params: { profile } })),
  compareProfile: () => unwrap(http.get('/sources/profile-compare')),
  listAll: () => unwrap(http.get('/sources/list')),
  loadSummary: () => unwrap(http.get('/sources/load-summary')),
  dict: () => unwrap(http.get('/sources/dict')),
  stations: (sourceId) => unwrap(http.get('/sources/stations', { params: { sourceId } })),
  station: (id) => unwrap(http.get(`/sources/stations/${id}`)),
  registerRuntime: (id, payload) => unwrap(http.put(`/sources/stations/${id}/runtime`, payload)),
  changeStatus: (id, status) => unwrap(http.put(`/sources/stations/${id}/status`, { status }))
}

export const pipeApi = {
  /** ?profile=with-valves 启用管段 → 阀门井 JOIN 抓取档案 */
  segments: (dn, profile) => unwrap(http.get('/pipe/segments', { params: { dn, profile } })),
  diameters: () => unwrap(http.get('/pipe/segments/diameters')),
  changeSegmentStatus: (id, status) => unwrap(http.put(`/pipe/segments/${id}/status`, { status })),
  /** ?profile=with-segment 启用阀门井 → 管段 JOIN 抓取档案 */
  valveWells: (segmentId, profile) =>
    unwrap(http.get('/pipe/valve-wells', { params: { segmentId, profile } })),
  updateValveWell: (id, payload) => unwrap(http.put(`/pipe/valve-wells/${id}`, payload)),
  dict: () => unwrap(http.get('/pipe/dict'))
}

export const monitorApi = {
  samples: (profile) => unwrap(http.get('/monitor/room-temps', { params: { profile } })),
  grid: (community) => unwrap(http.get('/monitor/room-temps/grid', { params: { community } })),
  stationStat: () => unwrap(http.get('/monitor/room-temps/station-stat')),
  record: (payload) => unwrap(http.post('/monitor/room-temps', payload)),
  createOrder: (id) => unwrap(http.post(`/monitor/room-temps/${id}/create-order`)),
  residents: (stationId, community) =>
    unwrap(http.get('/monitor/residents', { params: { stationId, community } })),
  communities: () => unwrap(http.get('/monitor/residents/communities')),
  changeSupply: (id, supplyStatus) =>
    unwrap(http.put(`/monitor/residents/${id}/supply-status`, { supplyStatus })),
  dict: () => unwrap(http.get('/monitor/dict'))
}

export const orderApi = {
  list: (status) => unwrap(http.get('/repair-orders', { params: { status } })),
  create: (payload) => unwrap(http.post('/repair-orders', payload)),
  updateStatus: (id, payload) => unwrap(http.put(`/repair-orders/${id}/status`, payload)),
  assign: (id, assignee) => unwrap(http.put(`/repair-orders/${id}/assignee`, { assignee })),
  dict: () => unwrap(http.get('/repair-orders/dict'))
}

export const feeApi = {
  list: (year, includeVoided) =>
    unwrap(http.get('/fees', { params: { year, includeVoided } })),
  arrearsRank: () => unwrap(http.get('/fees/arrears-rank')),
  years: () => unwrap(http.get('/fees/years')),
  pay: (id, payload) => unwrap(http.post(`/fees/${id}/pay`, payload)),
  /** 归并后待财务复核台账 */
  reviews: () => unwrap(http.get('/fees/reviews')),
  /** 财务复核完成：双份实收收口为一条，重复行作废 */
  resolveReview: (payload) => unwrap(http.post('/fees/reviews/resolve', payload))
}

export const residentMergeApi = {
  /** 疑似重复候选（同址在档档案） */
  candidates: () => unwrap(http.get('/residents/merge/candidates')),
  /** 归并预检 */
  preview: (keeperId, mergedId) =>
    unwrap(http.get('/residents/merge/preview', { params: { keeperId, mergedId } })),
  /** 确认归并（状态不一致时带 chosenSupplyStatus） */
  confirm: (payload) => unwrap(http.post('/residents/merge/confirm', payload)),
  /** 按地址 / 热表号 / 旧档案编号查询，已归并的自动跳到保留档案 */
  resolve: (keyword) => unwrap(http.get('/residents/merge/resolve', { params: { keyword } })),
  /** 归并审计 */
  audits: () => unwrap(http.get('/residents/merge/audits'))
}

export const statsApi = {
  overview: () => unwrap(http.get('/stats/overview')),
  tempRisk: () => unwrap(http.get('/stats/temp-risk')),
  arrearsRank: () => unwrap(http.get('/stats/arrears-rank'))
}

export const isolationApi = {
  /** 拓扑试算：故障管段 + 现场阀态覆盖 + 取舍策略 */
  trial: (payload) => unwrap(http.post('/isolation/trial', payload)),
  plans: (active) => unwrap(http.get('/isolation/plans', { params: { active } })),
  plan: (id) => unwrap(http.get(`/isolation/plans/${id}`)),
  activate: (id) => unwrap(http.post(`/isolation/plans/${id}/activate`)),
  start: (id) => unwrap(http.post(`/isolation/plans/${id}/start`)),
  confirmStep: (id, stepId, payload) =>
    unwrap(http.post(`/isolation/plans/${id}/steps/${stepId}/confirm`, payload)),
  finishFailure: (id, payload) => unwrap(http.post(`/isolation/plans/${id}/finish-failure`, payload)),
  planRestore: (id) => unwrap(http.post(`/isolation/plans/${id}/restore/plan`)),
  confirmRestoreStep: (id, stepId, payload) =>
    unwrap(http.post(`/isolation/plans/${id}/restore/steps/${stepId}/confirm`, payload)),
  topology: () => unwrap(http.get('/isolation/topology')),
  dict: () => unwrap(http.get('/isolation/dict'))
}

export default http
