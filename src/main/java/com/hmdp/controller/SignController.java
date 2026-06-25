package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.ISignService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/sign")
public class SignController {

    @Resource
    private ISignService signService;

    /** 签到（当天） */
    @PostMapping
    public Result sign() {
        return signService.sign();
    }

    /** 查询本月签到天数 */
    @GetMapping("/count")
    public Result querySignCount() {
        return signService.querySignCount();
    }

    /** 查询从今天起的连续签到天数 */
    @GetMapping("/consecutive")
    public Result queryConsecutiveSignCount() {
        return signService.queryConsecutiveSignCount();
    }

    /** 查询本月所有签到记录（返回签到的日期列表） */
    @GetMapping("/records")
    public Result querySignRecords() {
        return signService.querySignRecords();
    }
}
