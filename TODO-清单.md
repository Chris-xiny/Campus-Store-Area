## 前后端 API 对照与 TODO 清单

生成时间：2026-06-26

---

### 一、前端 API 调用总览（25 个端点，39 处调用）

| # | Method | 端点 | 调用页面 | 后端状态 |
|---|--------|------|----------|----------|
| 1 | GET | `/blog/{id}` | blog-detail, index, info | ✅ 已实现 |
| 2 | GET | `/blog/hot` | index | ✅ 已实现 |
| 3 | GET | `/blog/likes/{id}` | blog-detail | ✅ 已实现 |
| 4 | GET | `/blog/of/follow` | info | ✅ 已实现 |
| 5 | GET | `/blog/of/me` | info | ✅ 已实现 |
| 6 | GET | `/blog/of/user` | other-info | ✅ 已实现 |
| 7 | PUT | `/blog/like/{id}` | blog-detail, index, info | ✅ 已实现 |
| 8 | POST | `/blog` | blog-edit | ✅ 已实现 |
| 9 | GET | `/follow/common/{userId}` | other-info | ✅ 已实现 |
| 10 | GET | `/follow/or/not/{userId}` | blog-detail, other-info | ✅ 已实现 |
| 11 | PUT | `/follow/{userId}/{isFollow}` | blog-detail, other-info | ✅ 已实现 |
| 12 | GET | `/shop/{shopId}` | blog-detail, shop-detail | ✅ 已实现 |
| 13 | GET | `/shop/of/name` | blog-edit | ✅ 已实现 |
| 14 | GET | `/shop/of/type` | shop-list | ⚠️ 参数缺失 |
| 15 | GET | `/shop-type/list` | index, shop-list | ✅ 已实现 |
| 16 | GET | `/upload/blog/delete` | blog-edit | ✅ 已实现 |
| 17 | POST | `/upload/blog` | blog-edit | ✅ 已实现 |
| 18 | GET | `/user/{id}` | other-info | ✅ 已实现 |
| 19 | GET | `/user/info/{id}` | info, other-info | ✅ 已实现 |
| 20 | GET | `/user/me` | blog-detail, blog-edit, info, info-edit, other-info | ✅ 已实现 |
| 21 | POST | `/user/code` | login | ✅ 已实现 |
| 22 | POST | `/user/login` | login, login2 | ✅ 已实现 |
| 23 | POST | `/user/logout` | info | ✅ 已实现 |
| 24 | GET | `/voucher/list/{shopId}` | shop-detail | ✅ 已实现 |
| 25 | POST | `/voucher-order/seckill/{id}` | shop-detail | ✅ 已实现 |

**结论：前端 25 个 API 端点全部有对应后端实现，无 404 风险。**

---

### 二、后端额外端点（前端未调用）

| # | Method | 端点 | Controller | 说明 |
|---|--------|------|-----------|------|
| 1 | GET | `/follow/count/{id}` | FollowController | 关注数查询，前端未用但可作为数据接口 |
| 2 | POST | `/sign` | SignController | 签到（BitMap），前端无签到页面 |
| 3 | GET | `/sign/count` | SignController | 签到天数统计 |
| 4 | GET | `/sign/consecutive` | SignController | 连续签到天数 |
| 5 | GET | `/sign/records` | SignController | 月度签到记录 |
| 6 | POST | `/shop` | ShopController | 新增商铺（管理接口） |
| 7 | PUT | `/shop` | ShopController | 更新商铺（管理接口） |
| 8 | POST | `/voucher` | VoucherController | 新增普通优惠券（管理接口） |
| 9 | POST | `/voucher/seckill` | VoucherController | 新增秒杀券（管理接口） |

这些端点已实现，后续开发签到页或管理后台时可直接对接。

---

### 三、⚠️ 参数不匹配

#### `GET /shop/of/type` — GEO 附近商铺（未实现）

**前端发送参数：**

```
typeId, current, sortBy ("", "comments", "score"), x (经度), y (纬度)
```

**后端当前只接收：**

```
typeId, current
```

**影响：**
- `sortBy` 被忽略 → 距离排序、人气排序、评分排序均无效
- `x`, `y` 被忽略 → 无法计算距离，前端模板 `s.distance` 始终为 undefined
- 当前只做简单 DB 分页 `eq("type_id", typeId)`

**需要实现（D5 GEO 附近商铺）：**
1. 商铺数据写入 Redis GEO（`GEOADD shop:geo:type:{typeId} lng lat shopId`）
2. 按距离排序：`GEORADIUS` + `WITHDIST` + `ASC`
3. 按人气/评分排序：Redis ZSET 或 DB `ORDER BY`
4. 响应中携带 `distance` 字段

---

### 四、🔧 代码级 TODO（已实现但有瑕疵）

| 优先级 | 位置 | 问题 | 修复建议 |
|--------|------|------|----------|
| 中 | `UserController.java:41` | `// TODO 发送短信验证码并保存验证码`，当前仅打印日志 | 接入阿里云/腾讯云短信 SDK |
| 中 | `VoucherOrderProducer.java:55` | `// TODO 生产环境：写入 mq_failed_order 表`，MQ 发送失败时直接抛异常 | 实现死信补偿表 + 定时重试任务 |
| 低 | `ShopTypeServiceImpl.java:45` | DB 为空时返回 `null`，前端收到 `Result.ok(null)` | 改为返回空列表 `Collections.emptyList()` |

---

### 五、🚫 空模块（完全未实现）

| 模块 | 状态 | 说明 |
|------|------|------|
| **BlogCommentsController** | 控制器/Service/Mapper 均为空 | 笔记评论功能，前端暂无对应页面，属于后续扩展 |

---

### 六、📋 后续开发 TODO（按两周计划排列）

#### D5 — GEO 附近商铺 ✅
- [x] `GET /shop/of/type` 支持 `sortBy`、`x`、`y` 参数（Controller → Service 委托）
- [x] 商铺坐标写入 Redis GEO（`@PostConstruct loadShopGeoData` + `saveShop`/`update` 同步）
- [x] 按距离排序：`GEORADIUS` 分页 + `includeDistance` + `sortAscending`
- [x] 按人气排序：DB `ORDER BY comments DESC` + Haversine 距离计算
- [x] 按评分排序：DB `ORDER BY score DESC` + Haversine 距离计算
- [x] 响应中携带 `distance` 字段（复用 `Shop.distance` 虚拟字段）

#### D6 — 商铺缓存进阶
- [x] 缓存穿透：空值缓存 + 短 TTL（`queryWithMutex` 和 `queryWithLogicExpire` 均已处理）
- [x] 缓存击穿 — 互斥锁方案：`queryWithMutex`（SETNX + DoubleCheck + 异步重建），已实现备用
- [x] 缓存击穿 — 逻辑过期方案：`queryWithLogicExpire`（`RedisData.expireTime` + 异步线程池重建），**当前生产使用**
- [x] 缓存雪崩 — 随机化 TTL：`CacheClient.randomTtl()` 在基准 TTL 上叠加 ±20% 随机偏移，已应用于 `queryWithMutex`、`rebuildCacheWithExpireSeconds`、`rebuildCacheAsync` 三处
- [ ] 缓存雪崩 — 多级缓存（本地 Caffeine + Redis）
- [ ] 数据库与缓存一致性：延迟双删或 Canal binlog 监听（当前 `update` 方法仅做先更新 DB 再删缓存）

#### D7 — Redis 持久化 + AOF
- [ ] RDB + AOF 混合持久化配置
- [ ] AOF 重写策略调优
- [ ] 数据备份与恢复演练

#### D8 — Redis 集群
- [ ] 搭建 Redis 主从 + 哨兵模式
- [ ] 或搭建 Redis Cluster（3 主 3 从）
- [ ] 项目中 Lettuce 客户端连接集群配置
- [ ] 集群环境下 key 的 slot 分布规划

#### D9 — 商户独立登录
- [ ] 商户表（`tb_shop_user`）设计
- [ ] `POST /shop-user/login` 商户登录接口
- [ ] 商户 Token 与用户 Token 分离（Redis key 前缀不同）
- [ ] 商户端前端页面（商铺管理后台）
- [ ] 商户只能管理自己的商铺和优惠券

#### D10 — 消息通知
- [ ] WebSocket 或 SSE 实现实时通知
- [ ] 关注通知：有人关注了你
- [ ] 点赞通知：有人赞了你的笔记
- [ ] 系统通知：秒杀成功等
- [ ] 通知列表页面 + 未读红点

#### D11-D12 — 统计面板
- [ ] 商户后台数据统计：日/周/月销售额、订单量
- [ ] 用户端数据看板：签到日历、笔记数、粉丝数
- [ ] 数据按 Redis HyperLogLog 做 UV 统计
- [ ] ECharts 可视化图表集成

#### D13-D14 — IM 在线客服
- [ ] WebSocket 双向通信
- [ ] 用户-商户一对一聊天
- [ ] 消息持久化（MySQL）
- [ ] 在线状态（Redis BitMap 或 SET）
- [ ] 历史消息分页加载

---

### 七、Redis Key 现状

| Key 模式 | 类型 | 用途 | 已实现 |
|----------|------|------|--------|
| `login:token:{token}` | Hash | 用户登录信息 | ✅ |
| `login:code:{phone}` | String | 短信验证码 | ✅ |
| `sign:{userId}:{yyyy:MM}` | BitMap | 签到记录 | ✅ |
| `follows:{userId}` | SET | 关注列表 | ✅ |
| `blog:liked:{blogId}` | SET | 点赞用户集合 | ✅ |
| `shop:type:list` | String(JSON) | 商铺类型缓存 | ✅ |
| `cache:shop:{id}` | String(JSON) | 商铺详情缓存 | ✅ |
| `lock:shop:{id}` | String | 商铺缓存互斥锁 | ✅ |
| `shop:geo:type:{typeId}` | GEO | 商铺地理位置 | ❌ D5 |
| `shop:comments:{shopId}` | ZSET | 商铺人气/评分排行 | ❌ D5 |
| `seckill:stock:{voucherId}` | String | 秒杀库存 | ✅ |
| `seckill:order:{voucherId}` | SET | 秒杀一人一单 | ✅ |
| `user:info:{userId}` | — | 用户详情缓存 | ❌ 未做 |
