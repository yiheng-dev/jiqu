local stockKey = KEYS[1]
local dropUserKey = KEYS[2]
local traceLogKey = KEYS[3]
local dropId = ARGV[1]
local userId = (ARGV[2])
local orderId = ARGV[3]
local limitedDropOrderOperate = tonumber(ARGV[4])
local traceId = ARGV[5]
local logType = ARGV[6]
local beforeQty = tonumber(ARGV[7])
local changeQty = tonumber(ARGV[8])
local afterQty = tonumber(ARGV[9])
local stock = redis.call('get', stockKey);
if not stock then
    return 10004
end
redis.call('del', stockKey)
if limitedDropOrderOperate == 1 then
    if (redis.call('sismember', dropUserKey, userId) == 1) then
        redis.call('srem', dropUserKey, userId)
    end
end
local timeArr = redis.call('TIME')
local nowMillis = tonumber(timeArr[1]) * 1000 + math.floor(tonumber(timeArr[2]) / 1000)
local logEntry = cjson.encode({
    logType = logType,
    ts = nowMillis,
    orderId = orderId,
    traceId = traceId,
    userId = userId,
    dropId = dropId,
    beforeQty = beforeQty,
    changeQty = changeQty,
    afterQty = afterQty
})
redis.call('hset', traceLogKey, traceId, logEntry)
return 0
