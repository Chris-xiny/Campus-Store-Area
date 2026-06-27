-- 通用业务异常日志表
CREATE TABLE IF NOT EXISTS `error_log` (
  `id`              bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `create_time`     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '异常发生时间',
  `biz_module`      varchar(50) NOT NULL COMMENT '业务模块，如秒杀订单、商铺缓存',
  `error_method`    varchar(200) NOT NULL COMMENT '出错的类名.方法名',
  `error_message`   varchar(500) DEFAULT NULL COMMENT '错误摘要',
  `context`         text DEFAULT NULL COMMENT '异常上下文（JSON格式，记录关键参数）',
  `stack_trace`     text DEFAULT NULL COMMENT '异常堆栈（截取前2000字符）',
  PRIMARY KEY (`id`),
  INDEX `idx_biz_module` (`biz_module`) COMMENT '按业务模块查询',
  INDEX `idx_create_time` (`create_time`) COMMENT '按时间范围查询'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '通用业务异常日志表';
