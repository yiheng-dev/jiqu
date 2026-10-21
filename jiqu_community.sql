-- =====================================================
-- 集趣 · 潮流社区  数据库初始化脚本
-- 单库 jiqu_community，无分库分表
-- 保留功能：登录、潮流店铺、限量发售(令牌/限流/多级缓存/Kafka对账)、
--           发售日历、社区晒单、关注、签到、订阅候补、GEO附近、延迟提醒
-- 已移除：分库分表、会员等级(allowed_levels/min_level/credits/level)
-- =====================================================

CREATE DATABASE IF NOT EXISTS `jiqu_community` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `jiqu_community`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------
-- 用户表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_user`;
CREATE TABLE `tb_user` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `phone` varchar(11) NOT NULL COMMENT '手机号码',
  `password` varchar(128) DEFAULT '' COMMENT '密码，加密存储',
  `nick_name` varchar(32) DEFAULT '' COMMENT '昵称，默认是用户id',
  `icon` varchar(255) DEFAULT '' COMMENT '人物头像',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uniqe_key_phone` (`phone`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

INSERT INTO `tb_user` VALUES
(1987042234935279617,'13838411438','$2a$10$kWe7jD1AclGtuN/OFn/n2uD.VXOmudlchzttO0/UgiagbsHFHszXG','集趣小玩家','',NOW(),NOW()),
(1987041610793484289,'13686869696','$2a$10$9ifzWg.1v4T3P/ANMH6qhOVnW53qsPkLU7QIBsmiIIiF7E7/H8msC','潮流小黄鱼','',NOW(),NOW()),
(1987042505555968001,'13456789001','$2a$10$BIHNmw6HeqX4MK//fUHJf.JCVeQMDMEpMHARQd6RLUdeD5dpaXcdS','球鞋收藏家','',NOW(),NOW());

-- -----------------------------------------------------
-- 用户详情表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_user_info`;
CREATE TABLE `tb_user_info` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户id',
  `city` varchar(64) DEFAULT '' COMMENT '城市名称',
  `introduce` varchar(128) DEFAULT NULL COMMENT '个人介绍，不要超过128个字符',
  `fans` int unsigned DEFAULT '0' COMMENT '粉丝数量',
  `followee` int unsigned DEFAULT '0' COMMENT '关注的人的数量',
  `gender` tinyint unsigned DEFAULT '0' COMMENT '性别，0：男，1：女',
  `birthday` date DEFAULT NULL COMMENT '生日',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户详情表';

INSERT INTO `tb_user_info` VALUES
(1987042234935279618,1987042234935279617,'杭州','潮玩/手办收藏爱好者，限定款都冲！',2,3,0,'2000-01-01',NOW(),NOW()),
(1987041610793484289,1987041610793484289,'杭州','球鞋控，每周扫货不缺席',3,1,1,'1999-05-12',NOW(),NOW()),
(1987042505555968001,1987042505555968001,'上海','卡牌收藏，FC老玩家',1,0,0,'2001-08-20',NOW(),NOW());

-- -----------------------------------------------------
-- 用户手机表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_user_phone`;
CREATE TABLE `tb_user_phone` (
  `id` bigint NOT NULL COMMENT '主键id',
  `user_id` bigint NOT NULL COMMENT '用户id',
  `phone` varchar(512) NOT NULL COMMENT '手机号',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `phone_idx` (`phone`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户手机表';

INSERT INTO `tb_user_phone` VALUES
(1987042234952056833,1987042234935279617,'13838411438',NOW(),NOW()),
(1987041610910924802,1987041610793484289,'13686869696',NOW(),NOW()),
(1987042505568550913,1987042505555968001,'13456789001',NOW(),NOW());

-- -----------------------------------------------------
-- 潮流店铺分类表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_shop_type`;
CREATE TABLE `tb_shop_type` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `name` varchar(32) DEFAULT NULL COMMENT '分类名称',
  `icon` varchar(255) DEFAULT NULL COMMENT '图标',
  `sort` int unsigned DEFAULT NULL COMMENT '顺序',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='潮流店铺分类表';

INSERT INTO `tb_shop_type` VALUES
(1,'潮玩手办','',1,NOW(),NOW()),
(2,'潮流球鞋','',2,NOW(),NOW()),
(3,'潮流服饰','',3,NOW(),NOW()),
(4,'收藏卡牌','',4,NOW(),NOW()),
(5,'数码潮品','',5,NOW(),NOW()),
(6,'线下潮店','',6,NOW(),NOW()),
(7,'潮流配饰','',7,NOW(),NOW()),
(8,'限量发售','',8,NOW(),NOW());

-- -----------------------------------------------------
-- 潮流店铺表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_shop`;
CREATE TABLE `tb_shop` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `name` varchar(128) NOT NULL COMMENT '潮流店铺名称',
  `type_id` bigint unsigned NOT NULL COMMENT '潮流店铺分类id',
  `images` varchar(1024) NOT NULL COMMENT '店铺图片，多个图片以逗号隔开',
  `area` varchar(128) DEFAULT NULL COMMENT '商圈，例如陆家嘴',
  `address` varchar(255) NOT NULL COMMENT '地址',
  `x` double unsigned NOT NULL COMMENT '经度',
  `y` double unsigned NOT NULL COMMENT '纬度',
  `avg_price` bigint unsigned DEFAULT NULL COMMENT '均价，取整数',
  `sold` int(10) unsigned zerofill NOT NULL COMMENT '销量',
  `comments` int(10) unsigned zerofill NOT NULL COMMENT '评论数量',
  `score` int(2) unsigned zerofill NOT NULL COMMENT '评分，1~5分，乘10保存，避免小数',
  `open_hours` varchar(32) DEFAULT NULL COMMENT '营业时间，例如 10:00-22:00',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `foreign_key_type` (`type_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='潮流店铺表';

INSERT INTO `tb_shop` VALUES
(1,'POP MART 潮玩集合店',1,'https://qcloud.dpfile.com/pc/jiclIsCKmOI2arxKN1Uf0Hx3PucIJH8q0QSz-Z8llzcN56-_QiKuOvyio1OOxsRtFoXqu0G3iT2T27qat3WhLVEuLYk00OmSS1IdNpm8K8sG4JN9RIm2mTKcbLtc2o2vfCF2ubeXzk49OsGrXt_KYDCngOyCwZK-s3fqawWswzk.jpg','湖滨银泰','延安路292号湖滨银泰in77B区',120.160,30.255,120,0000004215,0000003035,48,'10:00-22:00',NOW(),NOW()),
(2,'SNKRS 折扣潮鞋仓',2,'https://p0.meituan.net/bbia/c1870d570e73accbc9fee90b48faca41195272.jpg','武林商圈','延安路515号国大城市广场',120.158,30.275,180,0000002160,0000001460,46,'11:00-22:00',NOW(),NOW()),
(3,'STUSSY 街头服饰',3,'https://p0.meituan.net/biztone/694233_1619500156517.jpeg','嘉里中心','延安路385号嘉里中心',120.161,30.268,260,0000012035,0000008045,47,'10:30-21:30',NOW(),NOW()),
(4,'卡游 收藏卡牌店',4,'https://img.meituan.net/msmerchant/232f8fdf09050838bd33fb24e79f30f9606056.jpg','滨江天街','江汉路1515号龙湖滨江天街',120.209,30.208,90,0000013519,0000009529,49,'10:00-22:00',NOW(),NOW()),
(5,'铁三角 数码潮品',5,'https://img.meituan.net/msmerchant/054b5de0ba0b50c18a620cc37482129a45739.jpg','湖滨银泰','延安路292号湖滨银泰in77C区',120.159,30.253,300,0000004125,0000002764,49,'10:00-22:00',NOW(),NOW()),
(6,'MOMO 潮牌集合店',6,'https://img.meituan.net/msmerchant/e71a2d0d693b3033c15522c43e03f09198239.jpg','西溪印象城','五常大道1号西溪印象城',120.043,30.274,150,0000009531,0000007324,46,'10:00-22:00',NOW(),NOW());

-- -----------------------------------------------------
-- 券表（0普通券；1限量发售券）
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_voucher`;
CREATE TABLE `tb_voucher` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `store_id` bigint unsigned DEFAULT NULL COMMENT '潮流店铺id',
  `title` varchar(255) NOT NULL COMMENT '券标题',
  `sub_title` varchar(255) DEFAULT NULL COMMENT '副标题',
  `rules` varchar(1024) DEFAULT NULL COMMENT '使用规则',
  `pay_value` bigint unsigned NOT NULL COMMENT '支付金额，单位是分。例如200代表2元',
  `actual_value` bigint NOT NULL COMMENT '抵扣金额，单位是分。例如200代表2元',
  `type` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0,普通券；1,限量发售',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '1,上架; 2,下架; 3,过期',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='券表';

INSERT INTO `tb_voucher` VALUES
(10001,1,'潮流盲盒 首单立减20元','全场手办盲盒通用','每人限购1件，凭券到店核销',2000,4000,0,1,NOW(),NOW()),
(10002,1,'限定款手办 88元券','仅限限定款手办使用','每人限购1件，售完即止',8800,10000,0,1,NOW(),NOW()),
(10003,2,'联名球鞋 满1000减150','联名/限量球鞋通用','满1000元可用，每人限购1双',100000,85000,0,1,NOW(),NOW()),
(20001,1,'【限量发售】幻彩机能风卫衣','限定300件 · 抽签发售','每人限购1件，中签后需在24h内支付',9900,12900,1,1,NOW(),NOW()),
(20002,2,'【限量发售】联名款球鞋·霓虹系列','限定200双 · 先到先得','每人限购1双，售完即止',129900,159900,1,1,NOW(),NOW()),
(20003,4,'【限量发售】宝可梦限定卡盒','限定500盒 · 先到先得','每人限购1盒，售完即止',5900,9900,1,1,NOW(),NOW());

-- -----------------------------------------------------
-- 限量发售券表（与券表一对一）
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_seckill_voucher`;
CREATE TABLE `tb_seckill_voucher` (
  `id` bigint NOT NULL,
  `drop_id` bigint unsigned NOT NULL COMMENT '关联的券的id',
  `init_stock` int NOT NULL COMMENT '初始化的库存',
  `stock` int NOT NULL COMMENT '库存',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `begin_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生效时间',
  `end_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '失效时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='限量发售券表，与券是一对一关系';

INSERT INTO `tb_seckill_voucher` VALUES
(90001,20001,300,300,NOW(),'2025-11-20 10:00:00','2025-12-31 23:59:59',NOW()),
(90002,20002,200,200,NOW(),DATE_ADD(NOW(), INTERVAL 7 DAY),DATE_ADD(NOW(), INTERVAL 30 DAY),NOW()),
(90003,20003,500,500,NOW(),DATE_ADD(NOW(), INTERVAL 14 DAY),DATE_ADD(NOW(), INTERVAL 30 DAY),NOW());

-- -----------------------------------------------------
-- 发售订单表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_voucher_order`;
CREATE TABLE `tb_voucher_order` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '下单的用户id',
  `drop_id` bigint unsigned NOT NULL COMMENT '购买的券id',
  `pay_type` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '支付方式 1：余额支付；2：支付宝；3：微信',
  `status` tinyint unsigned NOT NULL DEFAULT '1' COMMENT '订单状态，1：正常；2：已取消；',
  `reconciliation_status` int NOT NULL DEFAULT '1' COMMENT '对账状态：1待处理；2异常；3不一致；4一致',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `pay_time` timestamp NULL DEFAULT NULL COMMENT '支付时间',
  `use_time` timestamp NULL DEFAULT NULL COMMENT '核销时间',
  `refund_time` timestamp NULL DEFAULT NULL COMMENT '退款时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发售订单表';

-- -----------------------------------------------------
-- 发售对账日志表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_voucher_reconcile_log`;
CREATE TABLE `tb_voucher_reconcile_log` (
  `id` bigint NOT NULL COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单id',
  `user_id` bigint unsigned NOT NULL COMMENT '下单的用户id',
  `drop_id` bigint unsigned NOT NULL COMMENT '购买的券id',
  `message_id` varchar(64) DEFAULT NULL COMMENT 'Kafka消息uuid',
  `detail` varchar(1024) DEFAULT NULL COMMENT '差异说明',
  `before_qty` int DEFAULT NULL COMMENT '改变之前库存数量',
  `change_qty` int DEFAULT NULL COMMENT '本次改变数量',
  `after_qty` int DEFAULT NULL COMMENT '改变之后库存数量',
  `trace_id` bigint DEFAULT NULL COMMENT '追踪唯一标识',
  `log_type` int DEFAULT '-1' COMMENT '记录类型 -1:扣减 1:恢复',
  `business_type` int DEFAULT '1' COMMENT '业务类型：1创建订单成功；2创建订单超时；3创建订单失败',
  `reconciliation_status` int NOT NULL DEFAULT '1' COMMENT '对账状态：1待处理；2异常；3不一致；4一致',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order_id` (`order_id`) USING BTREE,
  KEY `idx_message_id` (`message_id`) USING BTREE,
  KEY `idx_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发售对账日志表';

-- -----------------------------------------------------
-- Redis 回滚失败日志表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_rollback_failure_log`;
CREATE TABLE `tb_rollback_failure_log` (
  `id` bigint NOT NULL COMMENT '主键',
  `drop_id` bigint unsigned NOT NULL COMMENT '券id',
  `user_id` bigint unsigned NOT NULL COMMENT '用户id',
  `order_id` bigint DEFAULT NULL COMMENT '订单id',
  `trace_id` bigint DEFAULT NULL COMMENT '追踪唯一标识',
  `detail` varchar(1024) DEFAULT NULL COMMENT '失败详情',
  `result_code` int DEFAULT NULL COMMENT 'Lua返回码(BaseCode)',
  `retry_attempts` int DEFAULT NULL COMMENT '已尝试的重试次数',
  `source` varchar(64) DEFAULT NULL COMMENT '来源组件',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_drop_user` (`drop_id`,`user_id`) USING BTREE,
  KEY `idx_trace_id` (`trace_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Redis回滚失败日志表';

-- -----------------------------------------------------
-- 社区晒单表（探店/晒物笔记）
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_blog`;
CREATE TABLE `tb_blog` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `store_id` bigint NOT NULL COMMENT '潮流店铺id',
  `user_id` bigint unsigned NOT NULL COMMENT '用户id',
  `title` varchar(255) NOT NULL COMMENT '标题',
  `images` varchar(2048) NOT NULL COMMENT '晒单的照片，最多9张，多张以","隔开',
  `content` varchar(2048) NOT NULL COMMENT '晒单的文字描述',
  `liked` int unsigned DEFAULT '0' COMMENT '点赞数量',
  `comments` int unsigned DEFAULT NULL COMMENT '评论数量',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区晒单表';

INSERT INTO `tb_blog` VALUES
(1,1,1987042234935279617,'冲到了！POP MART 限定款盲盒开箱','/imgs/blogs/blog1.jpg','排了两个小时终于抢到限定款盲盒，手感超绝，这个系列真的太香了！',120,35,NOW(),NOW()),
(2,2,1987041610793484289,'联名球鞋上脚实拍，绝绝子','/imgs/blogs/blog1.jpg','抢到 SNKRS 折扣仓的霓虹系列联名款，上脚太顶了，这个配色必须冲！',88,12,NOW(),NOW()),
(3,4,1987042505555968001,'宝可梦限定卡盒开卡实录','/imgs/blogs/blog1.jpg','运气爆棚开出隐藏款，直接回血！卡牌收藏的快乐就是开盒的一瞬间。',66,8,NOW(),NOW());

-- -----------------------------------------------------
-- 晒单评论表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_blog_comments`;
CREATE TABLE `tb_blog_comments` (
  `id` bigint unsigned NOT NULL COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户id',
  `blog_id` bigint unsigned NOT NULL COMMENT '晒单id',
  `parent_id` bigint unsigned NOT NULL COMMENT '关联的1级评论id，如果是一级评论，则值为0',
  `answer_id` bigint unsigned NOT NULL COMMENT '回复的评论id',
  `content` varchar(255) NOT NULL COMMENT '回复的内容',
  `liked` int unsigned DEFAULT NULL COMMENT '点赞数',
  `status` tinyint unsigned DEFAULT NULL COMMENT '状态，0：正常，1：被举报，2：禁止查看',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='晒单评论表';

-- -----------------------------------------------------
-- 关注关系表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_follow`;
CREATE TABLE `tb_follow` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户id',
  `follow_user_id` bigint unsigned NOT NULL COMMENT '关联的用户id',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';

-- -----------------------------------------------------
-- 签到表
-- -----------------------------------------------------
DROP TABLE IF EXISTS `tb_sign`;
CREATE TABLE `tb_sign` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint unsigned NOT NULL COMMENT '用户id',
  `year` year NOT NULL COMMENT '签到的年',
  `month` tinyint NOT NULL COMMENT '签到的月',
  `date` date NOT NULL COMMENT '签到的日期',
  `is_backup` tinyint unsigned DEFAULT NULL COMMENT '是否补签',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到表';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 完成。导入方式: mysql -u root -p < jiqu_community.sql
-- 说明: tb_seckill_voucher/tb_user_info 已移除会员等级字段
-- =====================================================
