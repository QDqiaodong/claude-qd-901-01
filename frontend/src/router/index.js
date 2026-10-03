/**
 * 本仓 main.js 由模板固定（不注册 vue-router），因此这里以「路由表 + 手动哈希导航」的方式驱动页面切换：
 * App.vue 直接消费 routes 里的 view 组件，nav 里的阀门节点写入 #/xxx 完成跳转。
 * 好处：不依赖 app.use(router)，零安装副作用，哈希地址可直接分享/刷新。
 */
import StationLoadView from '../views/StationLoadView.vue'
import PipeNetworkView from '../views/PipeNetworkView.vue'
import RoomTempView from '../views/RoomTempView.vue'
import OrderFeeView from '../views/OrderFeeView.vue'
import IsolationView from '../views/IsolationView.vue'

export const routes = [
  { path: '/stations', view: StationLoadView, title: '换热站负载环阵列' },
  { path: '/network', view: PipeNetworkView, title: '管网平面示意图' },
  { path: '/isolation', view: IsolationView, title: '管网隔离方案' },
  { path: '/temperature', view: RoomTempView, title: '室温抽测热力格' },
  { path: '/service', view: OrderFeeView, title: '报修工单与收费台账' }
]

/** 五个模块的阀门节点 / 子功能项 */
export const navNodes = [
  {
    key: 'source',
    label: '热源厂与换热站',
    desc: '热源 → 换热站',
    children: [
      { label: '换热站负载环阵列', hash: '#/stations', tab: 'rings' },
      { label: '热源厂台账', hash: '#/stations?tab=sources', tab: 'sources' }
    ]
  },
  {
    key: 'pipe',
    label: '管网段与阀门井',
    desc: '管网 → 阀门',
    children: [
      { label: '管网平面示意图', hash: '#/network', tab: 'map' },
      { label: '阀门井清单', hash: '#/network?tab=wells', tab: 'wells' },
      { label: '管网隔离方案', hash: '#/isolation', tab: 'isolation' }
    ]
  },
  {
    key: 'resident',
    label: '用户与室温抽测',
    desc: '用户 → 室温',
    children: [
      { label: '室温抽测热力格', hash: '#/temperature', tab: 'grid' },
      { label: '用户档案与报停', hash: '#/temperature?tab=residents', tab: 'residents' }
    ]
  },
  {
    key: 'order',
    label: '报修工单',
    desc: '受理 → 闭环',
    children: [{ label: '工单看板', hash: '#/service?tab=board', tab: 'board' }]
  },
  {
    key: 'fee',
    label: '采暖费收缴',
    desc: '应收 → 实收',
    children: [{ label: '收费台账', hash: '#/service?tab=fees', tab: 'fees' }]
  }
]

/** 解析当前哈希：返回 { path, query, view, title } */
export function resolveHash(hash = window.location.hash) {
  const raw = hash.replace(/^#/, '') || '/stations'
  const [path, search = ''] = raw.split('?')
  const query = Object.fromEntries(new URLSearchParams(search))
  const matched = routes.find((r) => r.path === path) || routes[0]
  return { path: matched.path, query, view: matched.view, title: matched.title }
}

export function defaultHash() {
  return '#/stations'
}
