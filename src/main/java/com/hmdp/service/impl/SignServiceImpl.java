package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Sign;
import com.hmdp.mapper.SignMapper;
import com.hmdp.service.ISignService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.USER_SIGN_KEY;

@Service
public class SignServiceImpl extends ServiceImpl<SignMapper, Sign> implements ISignService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();

        String key = buildSignKey(userId, now);
        int dayOfMonth = now.getDayOfMonth();
        int offset = dayOfMonth - 1;

        // 检查今天是否已经签到
        Boolean isSigned = stringRedisTemplate.opsForValue().getBit(key, offset);
        if (Boolean.TRUE.equals(isSigned)) {
            return Result.fail("今天已经签到过了");
        }

        // 签到：SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, offset, true);

        return Result.ok();
    }

    @Override
    public Result querySignCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = buildSignKey(userId, now);

        // BITCOUNT 统计本月签到天数
        Long count = stringRedisTemplate.execute(
                (RedisCallback<Long>) connection ->
                        connection.bitCount(key.getBytes())
        );

        return Result.ok(count == null ? 0 : count.intValue());
    }

    @Override
    public Result queryConsecutiveSignCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = buildSignKey(userId, now);

        int dayOfMonth = now.getDayOfMonth();

        // BITFIELD 一次性取回第 1 天到今天的 bit 值
        List<Long> bits = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );

        if (bits == null || bits.isEmpty()) {
            return Result.ok(0);
        }

        long bitValue = bits.get(0);
        int consecutiveCount = 0;

        // 从今天（最高位）往回数，遇到 0 停止
        for (int i = dayOfMonth - 1; i >= 0; i--) {
            if (((bitValue >> i) & 1) == 1) {
                consecutiveCount++;
            } else {
                break;
            }
        }

        return Result.ok(consecutiveCount);
    }

    @Override
    public Result querySignRecords() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String key = buildSignKey(userId, now);

        // 获取本月总天数
        int daysInMonth = YearMonth.from(now).lengthOfMonth();

        // BITFIELD 取回整月的 bit 值
        List<Long> bits = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(daysInMonth)).valueAt(0)
        );

        if (bits == null || bits.isEmpty()) {
            return Result.ok(new ArrayList<>());
        }

        long bitValue = bits.get(0);
        List<Integer> signedDays = new ArrayList<>();

        // 遍历每一天，找出签到的日期
        for (int day = 1; day <= daysInMonth; day++) {
            int bitPos = daysInMonth - day;
            if (((bitValue >> bitPos) & 1) == 1) {
                signedDays.add(day);
            }
        }

        return Result.ok(signedDays);
    }

    /**
     * 构建签到 key: sign:{userId}:{yyyy:MM}
     */
    private String buildSignKey(Long userId, LocalDateTime date) {
        String yearMonth = date.getYear() + ":" + String.format("%02d", date.getMonthValue());
        return USER_SIGN_KEY + userId + ":" + yearMonth;
    }
}
