-- 秒杀 Lua 脚本（精简版：只做库存预扣和一人一单校验）
-- 消息投递交给 RabbitMQ，不再由 Lua 写 Redis Stream
--
-- 参数：ARGV[1]=voucherId, ARGV[2]=userId
-- 返回值：
--   0 = 预扣成功（上层负责发 MQ 消息）
--   1 = 库存不足
--   2 = 重复下单（一人一单校验失败）

local voucherId = ARGV[1]
local userId    = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

-- 1. 判断库存
local stock = tonumber(redis.call('get', stockKey) or '0')
if stock <= 0 then
    return 1
end

-- 2. 判断是否重复下单
if redis.call('sismember', orderKey, userId) == 1 then
    return 2
end

-- 3. 扣减库存
redis.call('incrby', stockKey, -1)

-- 4. 记录该用户已购买
redis.call('sadd', orderKey, userId)

return 0
