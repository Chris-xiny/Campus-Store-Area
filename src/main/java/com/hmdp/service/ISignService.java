package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Sign;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ISignService extends IService<Sign> {

    /** 签到（当天） */
    Result sign();

    /** 查询本月签到天数 */
    Result querySignCount();

    /** 查询从今天起的连续签到天数 */
    Result queryConsecutiveSignCount();

    /** 查询本月所有签到记录（返回哪些天签了） */
    Result querySignRecords();
}
