if (redis.call("hexists", KEYS[1], ARGV[1]) == 0) then
    return nil;
end
local value = redis.call("hincrby", KEYS[1], ARGV[1], -1);
if (value > 0) then
    redis.call("expire", KEYS[1], ARGV[2]);
    return nil;
else
    redis.call("del", KEYS[1]);
    return nil;
end