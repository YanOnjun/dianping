-- 加鎖：判斷是否有鎖:
--true-> 判斷是否是自己：
--true -> 鎖值更新 鎖時間更新
--false -> 獲取鎖失敗 退出
--false -> 創建鎖  鎖時間更新
if (redis.call("exists", KEYS[1]) == 0) then
    redis.call("hset", KEYS[1], ARGV[1], 1);
    redis.call("expire", KEYS[1], ARGV[2]);
    return 1;
end
if (redis.call("hexists", KEYS[1], ARGV[1]) == 1) then
    redis.call("hincrby", KEYS[1], ARGV[1], 1);
    redis.call("expire", KEYS[1], ARGV[2]);
    return 1;
end
return 0;