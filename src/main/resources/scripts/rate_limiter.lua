local key = KEYS[1]
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- Fetch current bucket state
local bucket = redis.call('HMGET', key, 'tokens', 'last_time')
local tokens = tonumber(bucket[1])
local last_time = tonumber(bucket[2])

-- First request: initialize bucket
if tokens == nil then
    tokens = capacity
    last_time = now
end

-- Refill tokens based on elapsed time (milliseconds precision)
local delta = math.max(0, now - last_time)
local filled_tokens = math.min(capacity, tokens + (delta * rate / 1000))

local allowed = 0
local remaining = 0
local retry_after = 0

if filled_tokens >= 1 then
    allowed = 1
    remaining = filled_tokens - 1
else
    remaining = filled_tokens
    retry_after = math.ceil((1 - filled_tokens) / rate)
end

-- Save updated state
redis.call('HMSET', key, 'tokens', remaining, 'last_time', now)
redis.call('EXPIRE', key, math.ceil(capacity / rate) + 10)

return {allowed, remaining, retry_after}