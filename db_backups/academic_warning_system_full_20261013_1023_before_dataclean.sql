-- MySQL dump 10.13  Distrib 8.0.44, for Win64 (x86_64)
--
-- Host: localhost    Database: academic_warning_system
-- ------------------------------------------------------
-- Server version	8.0.44

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `academic_warning`
--

DROP TABLE IF EXISTS `academic_warning`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `academic_warning` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rule_id` bigint DEFAULT NULL,
  `fail_count` int DEFAULT NULL COMMENT '生成该预警时该生的挂科门数（ScoreCaliber 口径）；判重键的一半，加列前的历史行按正文回填',
  `type` int DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `warning_level` int NOT NULL,
  `warning_message` text COLLATE utf8mb4_unicode_ci,
  `trigger_score` decimal(5,2) DEFAULT NULL,
  `gpa_before` decimal(5,2) DEFAULT NULL,
  `gpa_after` decimal(5,2) DEFAULT NULL,
  `status` int DEFAULT '0',
  `appeal_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'none',
  `handled_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handle_result` text COLLATE utf8mb4_unicode_ci,
  `handled_at` timestamp NULL DEFAULT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_academic_warning_rule_id` (`rule_id`),
  KEY `idx_academic_warning_student_id` (`student_id`),
  CONSTRAINT `fk_academic_warning_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=406 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `academic_warning`
--

LOCK TABLES `academic_warning` WRITE;
/*!40000 ALTER TABLE `academic_warning` DISABLE KEYS */;
INSERT INTO `academic_warning` VALUES (395,3,'司明',2,3,1,'中级预警','司明同学已累计3门课程不及格（操作系统 40分、计算机基础 47分、高等数学 56.5分），触发预警规则「中级预警」，系统自动生成预警。',2,'司明同学已累计3门课程不及格（操作系统 40分、计算机基础 47分、高等数学 56.5分），触发预警规则「中级预警」，系统自动生成预警。',40.00,NULL,NULL,0,'none',NULL,NULL,NULL,NULL,'2026-09-21 06:09:49',NULL,'2026-10-03 17:19:36'),(396,20,'李爽',2,3,1,'中级预警','李爽同学已累计3门课程不及格（计算机网络 47分、计算机基础 52分、高等数学 54分），触发预警规则「中级预警」，系统自动生成预警。',2,'李爽同学已累计3门课程不及格（计算机网络 47分、计算机基础 52分、高等数学 54分），触发预警规则「中级预警」，系统自动生成预警。',47.00,NULL,NULL,0,'none',NULL,NULL,NULL,NULL,'2026-09-21 06:09:50',NULL,'2026-09-30 13:18:00'),(397,26,'张健',2,3,1,'中级预警','张健同学已累计3门课程不及格（计算机基础 48分、计算机网络 49分、高等数学 52分），触发预警规则「中级预警」，系统自动生成预警。',2,'张健同学已累计3门课程不及格（计算机基础 48分、计算机网络 49分、高等数学 52分），触发预警规则「中级预警」，系统自动生成预警。',48.00,NULL,NULL,0,'none',NULL,NULL,NULL,NULL,'2026-09-21 06:09:50',NULL,'2026-09-30 13:18:00'),(398,13,'韩坤',1,2,1,'低级预警','韩坤同学已累计2门课程不及格（操作系统 54分、数据结构 59分），触发预警规则「低级预警」，系统自动生成预警。',1,'韩坤同学已累计2门课程不及格（操作系统 54分、数据结构 59分），触发预警规则「低级预警」，系统自动生成预警。',54.00,NULL,NULL,0,'none',NULL,NULL,NULL,NULL,'2026-09-21 06:09:50',NULL,'2026-10-01 06:36:09');
/*!40000 ALTER TABLE `academic_warning` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `academic_warning_bak_20260916`
--

DROP TABLE IF EXISTS `academic_warning_bak_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `academic_warning_bak_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `student_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rule_id` bigint DEFAULT NULL,
  `type` int DEFAULT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `warning_level` int NOT NULL,
  `warning_message` text COLLATE utf8mb4_unicode_ci,
  `trigger_score` decimal(5,2) DEFAULT NULL,
  `gpa_before` decimal(5,2) DEFAULT NULL,
  `gpa_after` decimal(5,2) DEFAULT NULL,
  `status` int DEFAULT '0',
  `appeal_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'none',
  `handled_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `handle_result` text COLLATE utf8mb4_unicode_ci,
  `handled_at` timestamp NULL DEFAULT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `academic_warning_bak_20260916`
--

LOCK TABLES `academic_warning_bak_20260916` WRITE;
/*!40000 ALTER TABLE `academic_warning_bak_20260916` DISABLE KEYS */;
INSERT INTO `academic_warning_bak_20260916` VALUES (301,3,'司明',NULL,1,'成绩下滑','第4学期大学英语不及格(51分)，线性代数不及格(59分)，较第3学期成绩明显下滑。',2,NULL,NULL,NULL,NULL,1,'none',NULL,NULL,NULL,NULL,'2025-01-16 00:30:00',NULL,'2026-06-04 09:38:10'),(302,3,'司明',NULL,1,'多门挂科','第4学期计算机组成原理成绩严重不及格(40分)，建议立即关注。已累计3门课程不及格。',3,NULL,NULL,NULL,NULL,0,'none',NULL,NULL,NULL,NULL,'2025-01-16 00:30:00',NULL,'2026-06-04 09:38:10'),(303,3,'司明',NULL,1,'持续预警','第5学期高等数学56.5分(不及格)，线性代数47分(不及格)。连续两个学期出现多门挂科，GPA呈持续下滑趋势，学风存在严重问题。建议辅导员约谈。',3,NULL,NULL,NULL,NULL,0,'none',NULL,NULL,NULL,NULL,'2025-06-21 00:30:00',NULL,'2026-06-04 09:38:10');
/*!40000 ALTER TABLE `academic_warning_bak_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `academic_warning_course`
--

DROP TABLE IF EXISTS `academic_warning_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `academic_warning_course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `warning_id` bigint NOT NULL COMMENT '关联 academic_warning.id',
  `course_id` bigint NOT NULL COMMENT '关联 courses.id（不加外键，见说明）',
  `course_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成时的课程名快照',
  `score_total` decimal(5,2) DEFAULT NULL COMMENT '生成预警时的总评成绩',
  `semester` int DEFAULT NULL COMMENT '该门挂科成绩所属学期',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_awc_warning_course` (`warning_id`,`course_id`),
  KEY `idx_awc_course_id` (`course_id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警关联挂科课程明细表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `academic_warning_course`
--

LOCK TABLES `academic_warning_course` WRITE;
/*!40000 ALTER TABLE `academic_warning_course` DISABLE KEYS */;
INSERT INTO `academic_warning_course` VALUES (1,395,1,'高等数学',56.50,5,'2026-10-12 02:08:35'),(2,395,3,'计算机基础',47.00,5,'2026-10-12 02:08:35'),(3,395,6,'操作系统',40.00,4,'2026-10-12 02:08:35'),(4,398,4,'数据结构',59.00,2,'2026-10-12 02:08:35'),(5,398,6,'操作系统',54.00,2,'2026-10-12 02:08:35'),(6,396,1,'高等数学',54.00,2,'2026-10-12 02:08:35'),(7,396,3,'计算机基础',52.00,2,'2026-10-12 02:08:35'),(8,396,5,'计算机网络',47.00,2,'2026-10-12 02:08:35'),(9,397,1,'高等数学',52.00,2,'2026-10-12 02:08:35'),(10,397,3,'计算机基础',48.00,2,'2026-10-12 02:08:35'),(11,397,5,'计算机网络',49.00,2,'2026-10-12 02:08:35');
/*!40000 ALTER TABLE `academic_warning_course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `admin_profile`
--

DROP TABLE IF EXISTS `admin_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_profile_user_id` (`user_id`),
  CONSTRAINT `fk_admin_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin_profile`
--

LOCK TABLES `admin_profile` WRITE;
/*!40000 ALTER TABLE `admin_profile` DISABLE KEYS */;
INSERT INTO `admin_profile` VALUES (1,1,'系统管理员','13800000000','admin@academic.edu','信息化管理中心','2025-12-22 17:28:15','2025-12-22 17:28:15'),(2,16,'夏志浩','13800000003','admin@test.com','信息化管理中心','2025-12-22 17:28:15','2025-12-22 17:28:15');
/*!40000 ALTER TABLE `admin_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `admin_tasks`
--

DROP TABLE IF EXISTS `admin_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_tasks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `priority` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'medium',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending',
  `created_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_admin_tasks_status` (`status`),
  KEY `idx_admin_tasks_created_by` (`created_by`),
  CONSTRAINT `fk_admin_tasks_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `admin_tasks`
--

LOCK TABLES `admin_tasks` WRITE;
/*!40000 ALTER TABLE `admin_tasks` DISABLE KEYS */;
/*!40000 ALTER TABLE `admin_tasks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assistance_evaluations`
--

DROP TABLE IF EXISTS `assistance_evaluations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assistance_evaluations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `plan_id` bigint NOT NULL,
  `counselor_id` bigint DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `score` int DEFAULT NULL,
  `effectiveness` int DEFAULT NULL COMMENT '帮扶有效性评分 1-5（学生对"这次帮扶是否真的有用"的独立打分）；加列前的历史行为 NULL',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_assistance_evaluations_plan_id` (`plan_id`),
  KEY `idx_assistance_evaluations_counselor_id` (`counselor_id`),
  CONSTRAINT `fk_assistance_evaluations_plan_id` FOREIGN KEY (`plan_id`) REFERENCES `assistance_plans` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assistance_evaluations`
--

LOCK TABLES `assistance_evaluations` WRITE;
/*!40000 ALTER TABLE `assistance_evaluations` DISABLE KEYS */;
/*!40000 ALTER TABLE `assistance_evaluations` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assistance_plans`
--

DROP TABLE IF EXISTS `assistance_plans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assistance_plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `counselor_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `status` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `progress` tinyint NOT NULL DEFAULT '0' COMMENT '完成进度百分比 0-100',
  `progress_note` text COLLATE utf8mb4_unicode_ci COMMENT '学生更新进度时填写的完成情况说明（最近一次）',
  PRIMARY KEY (`id`),
  KEY `idx_assistance_plans_counselor_id` (`counselor_id`),
  KEY `idx_assistance_plans_student_id` (`student_id`),
  CONSTRAINT `fk_assistance_plans_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1002 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assistance_plans`
--

LOCK TABLES `assistance_plans` WRITE;
/*!40000 ALTER TABLE `assistance_plans` DISABLE KEYS */;
INSERT INTO `assistance_plans` VALUES (1,53,13,'针对韩坤同学的学业帮扶计划：1.安排学习小组辅导数据结构；2.每周一次答疑；3.定期检查学习进度',1,'2026-05-27 02:00:00','2026-09-16 11:51:21',50,NULL),(2,53,20,'针对李爽同学的学业帮扶计划：1.重点补习计算机网络、计算机基础和高等数学基础；2.安排学伴互助；3.每两周向辅导员汇报进度；4.已安排补考',1,'2026-05-27 02:00:00','2026-09-16 12:43:52',50,NULL),(3,53,26,'针对张健同学的学业帮扶计划：1.重点补习数学和编程基础；2.每周安排两次额外辅导；3.建立学习档案跟踪进度',0,'2026-05-27 02:00:00','2026-05-27 02:00:00',0,NULL),(1001,53,3,'针对司明同学的学业帮扶计划：1.重点补习操作系统、计算机基础和高等数学三门挂科课程；2.安排学习小组每周两次辅导答疑；3.每两周检查一次学习进度并反馈辅导员；4.目标：本学期末补考全部通过。',1,'2026-09-02 06:36:09','2026-10-09 07:12:10',60,'学会了');
/*!40000 ALTER TABLE `assistance_plans` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint DEFAULT NULL,
  `action_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_id` bigint DEFAULT NULL,
  `details` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_logs_action_type` (`action_type`),
  KEY `idx_audit_logs_user_id` (`user_id`),
  CONSTRAINT `fk_audit_logs_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=84 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_logs`
--

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
INSERT INTO `audit_logs` VALUES (1,1,'CREATE','WARNING_RULE',5,'新建预警规则 probe-audit-20261002','2026-10-02 03:30:00'),(2,1,'DELETE','WARNING_RULE',5,'删除预警规则 id=5','2026-10-02 03:30:00'),(3,1,'CREATE','ADMIN_TASK',1,'创建任务「probe-任务A」','2026-10-02 05:00:43'),(4,1,'CREATE','ADMIN_TASK',2,'创建任务「probe-任务B」','2026-10-02 05:00:43'),(5,1,'UPDATE','ADMIN_TASK',1,'任务状态改为 in_progress','2026-10-02 05:00:44'),(6,1,'UPDATE','ADMIN_TASK',2,'任务状态改为 pending','2026-10-02 05:00:44'),(7,1,'UPDATE','ADMIN_TASK',2,'任务状态改为 completed','2026-10-02 05:00:44'),(8,1,'DELETE','ADMIN_TASK',1,'删除任务 id=1','2026-10-02 05:00:44'),(9,1,'DELETE','ADMIN_TASK',2,'删除任务 id=2','2026-10-02 05:00:44'),(10,1,'CREATE','ADMIN_TASK',3,'创建任务「probe-任务A」','2026-10-02 05:02:20'),(11,1,'CREATE','ADMIN_TASK',4,'创建任务「probe-任务B」','2026-10-02 05:02:20'),(12,1,'UPDATE','ADMIN_TASK',3,'任务状态改为 in_progress','2026-10-02 05:02:21'),(13,1,'UPDATE','ADMIN_TASK',4,'任务状态改为 pending','2026-10-02 05:02:21'),(14,1,'UPDATE','ADMIN_TASK',4,'任务状态改为 completed','2026-10-02 05:02:21'),(15,1,'DELETE','ADMIN_TASK',3,'删除任务 id=3','2026-10-02 05:02:21'),(16,1,'DELETE','ADMIN_TASK',4,'删除任务 id=4','2026-10-02 05:02:21'),(17,1,'CREATE','ADMIN_TASK',5,'创建任务「probe-任务A」','2026-10-02 05:03:37'),(18,1,'CREATE','ADMIN_TASK',6,'创建任务「probe-任务B」','2026-10-02 05:03:37'),(19,1,'UPDATE','ADMIN_TASK',5,'任务状态改为 in_progress','2026-10-02 05:03:38'),(20,1,'UPDATE','ADMIN_TASK',6,'任务状态改为 pending','2026-10-02 05:03:38'),(21,1,'UPDATE','ADMIN_TASK',6,'任务状态改为 completed','2026-10-02 05:03:38'),(22,1,'DELETE','ADMIN_TASK',5,'删除任务 id=5','2026-10-02 05:03:38'),(23,1,'DELETE','ADMIN_TASK',6,'删除任务 id=6','2026-10-02 05:03:38'),(24,1,'CREATE','ADMIN_TASK',7,'创建任务「probe-端到端任务A」','2026-10-02 05:08:51'),(25,1,'DELETE','ADMIN_TASK',7,'删除任务 id=7','2026-10-02 05:09:31'),(26,1,'CREATE','ADMIN_TASK',8,'创建任务「probe-端到端任务A」','2026-10-02 05:10:34'),(27,1,'UPDATE','ADMIN_TASK',8,'任务状态改为 in_progress','2026-10-02 05:10:59'),(28,1,'DELETE','ADMIN_TASK',8,'删除任务 id=8','2026-10-02 05:11:02'),(29,1,'CREATE','ADMIN_TASK',9,'创建任务「probe-端到端任务A」','2026-10-02 05:12:59'),(30,1,'DELETE','ADMIN_TASK',9,'删除任务 id=9','2026-10-02 05:14:22'),(31,1,'CREATE','ADMIN_TASK',10,'创建任务「probe-端到端任务A」','2026-10-02 05:16:07'),(32,1,'UPDATE','ADMIN_TASK',10,'任务状态改为 in_progress','2026-10-02 05:16:32'),(33,1,'DELETE','ADMIN_TASK',10,'删除任务 id=10','2026-10-02 05:16:35'),(34,1,'CREATE','ADMIN_TASK',11,'创建任务「probe-任务A」','2026-10-02 05:16:39'),(35,1,'CREATE','ADMIN_TASK',12,'创建任务「probe-任务B」','2026-10-02 05:16:39'),(36,1,'UPDATE','ADMIN_TASK',11,'任务状态改为 in_progress','2026-10-02 05:16:40'),(37,1,'UPDATE','ADMIN_TASK',12,'任务状态改为 pending','2026-10-02 05:16:40'),(38,1,'UPDATE','ADMIN_TASK',12,'任务状态改为 completed','2026-10-02 05:16:40'),(39,1,'DELETE','ADMIN_TASK',11,'删除任务 id=11','2026-10-02 05:16:40'),(40,1,'DELETE','ADMIN_TASK',12,'删除任务 id=12','2026-10-02 05:16:40'),(41,1,'REVIEW','APPEAL',8,'通过成绩申诉（课程：大学英语）','2026-10-02 10:52:28'),(42,1,'DELETE','USER',82,'删除账号 ZZTEST911（角色 学生）','2026-10-03 14:14:34'),(43,1,'DELETE','USER',84,'删除账号 ZZTEST911（角色 学生）','2026-10-03 14:15:38'),(44,1,'CREATE','USER',86,'新建账号 uiprobetmp（角色 教师）','2026-10-03 14:19:32'),(45,1,'UPDATE','USER',86,'修改账号 null','2026-10-03 14:19:32'),(46,1,'UPDATE','USER',86,'修改账号 null（含重置口令）','2026-10-03 14:19:32'),(47,1,'RESET_PASSWORD','USER',86,'重置账号 uiprobetmp 的登录口令','2026-10-03 14:19:32'),(48,1,'TOGGLE_STATUS','USER',86,'停用账号 uiprobetmp','2026-10-03 14:19:32'),(49,1,'TOGGLE_STATUS','USER',86,'启用账号 uiprobetmp','2026-10-03 14:19:32'),(50,1,'APPROVE','USER',86,'审批通过账号 uiprobetmp','2026-10-03 14:19:33'),(51,1,'DELETE','USER',86,'删除账号 uiprobetmp（角色 教师）','2026-10-03 14:19:33'),(52,1,'CREATE','USER',87,'新建账号 uiprobeadm5（角色 学院管理员）','2026-10-03 14:21:08'),(53,1,'DELETE','USER',87,'删除账号 uiprobeadm5（角色 学院管理员）','2026-10-03 14:21:08'),(54,1,'CREATE','ADMIN_TASK',13,'创建任务「probe-端到端任务A」','2026-10-03 14:23:38'),(55,1,'UPDATE','ADMIN_TASK',13,'任务状态改为 in_progress','2026-10-03 14:24:03'),(56,1,'DELETE','ADMIN_TASK',13,'删除任务 id=13','2026-10-03 14:24:06'),(57,1,'HANDLE','WARNING',406,'处理学业预警：已处理（学生 PROBE）','2026-10-03 14:26:24'),(58,1,'HANDLE','WARNING',406,'处理学业预警：已处理（学生 PROBE）','2026-10-03 14:28:05'),(59,16,'REVIEW','APPEAL',6,'驳回成绩申诉（课程：高等数学）','2026-10-09 02:46:07'),(60,1,'CREATE','USER',92,'新建账号 W32PRBxcx1（角色 学院管理员）','2026-10-09 04:10:06'),(61,1,'CREATE','USER',93,'新建账号 W32PRBnocol1（角色 学院管理员）','2026-10-09 04:10:06'),(62,1,'CREATE','USER',95,'新建账号 W32PRBxcx1（角色 学院管理员）','2026-10-09 04:20:46'),(63,1,'CREATE','USER',96,'新建账号 W32PRBnocol1（角色 学院管理员）','2026-10-09 04:20:46'),(64,1,'CREATE','USER',98,'新建账号 W32PRBxcx1（角色 学院管理员）','2026-10-09 04:26:44'),(65,1,'CREATE','USER',99,'新建账号 W32PRBnocol1（角色 学院管理员）','2026-10-09 04:26:44'),(66,1,'CREATE','USER',101,'新建账号 W32PRBxcx1（角色 学院管理员）','2026-10-09 04:30:31'),(67,1,'CREATE','USER',102,'新建账号 W32PRBnocol1（角色 学院管理员）','2026-10-09 04:30:32'),(68,1,'UPDATE','USER',103,'调整账号 W33PRB_login 的角色：学生 → 学院管理员（所属学院：软件学院）','2026-10-09 08:34:18'),(69,1,'UPDATE','USER',103,'调整账号 W33PRB_login 的角色：学院管理员 → 学生','2026-10-09 08:34:19'),(70,1,'UPDATE','USER',103,'调整账号 W33PRB_login 的角色：学生 → 学院管理员（所属学院：电子工程学院）','2026-10-09 08:34:19'),(71,1,'UPDATE','USER',103,'调整账号 W33PRB_login 的角色：学院管理员 → 教师','2026-10-09 08:34:19'),(72,1,'UPDATE','USER',104,'调整账号 W33PRB_login 的角色：学生 → 学院管理员（所属学院：软件学院）','2026-10-09 08:40:26'),(73,1,'UPDATE','USER',104,'调整账号 W33PRB_login 的角色：学院管理员 → 学生','2026-10-09 08:40:27'),(74,1,'UPDATE','USER',104,'调整账号 W33PRB_login 的角色：学生 → 学院管理员（所属学院：电子工程学院）','2026-10-09 08:40:28'),(75,1,'UPDATE','USER',104,'调整账号 W33PRB_login 的角色：学院管理员 → 教师','2026-10-09 08:40:28'),(76,1,'UPDATE','USER',105,'调整账号 W33PRB_login 的角色：学生 → 学院管理员（所属学院：软件学院）','2026-10-09 08:47:20'),(77,1,'UPDATE','USER',105,'调整账号 W33PRB_login 的角色：学院管理员 → 学生','2026-10-09 08:47:21'),(78,1,'UPDATE','USER',105,'调整账号 W33PRB_login 的角色：学生 → 学院管理员（所属学院：电子工程学院）','2026-10-09 08:47:22'),(79,1,'UPDATE','USER',105,'调整账号 W33PRB_login 的角色：学院管理员 → 教师','2026-10-09 08:47:22'),(80,1,'UPDATE','USER',108,'调整账号 W33UI_probe 的角色：学生 → 学院管理员（所属学院：软件学院）','2026-10-09 09:00:32'),(81,1,'UPDATE','USER',108,'调整账号 W33UI_probe 的角色：学院管理员 → 学生','2026-10-09 09:00:35'),(82,1,'UPDATE','USER',109,'调整账号 W33UI_probe 的角色：学生 → 学院管理员（所属学院：软件学院）','2026-10-09 09:03:55'),(83,1,'UPDATE','USER',109,'调整账号 W33UI_probe 的角色：学院管理员 → 学生','2026-10-09 09:03:59');
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix10_classes`
--

DROP TABLE IF EXISTS `bak_fix10_classes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix10_classes` (
  `id` bigint NOT NULL DEFAULT '0',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `college_id` bigint NOT NULL,
  `major_id` bigint DEFAULT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `counselor_id` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix10_classes`
--

LOCK TABLES `bak_fix10_classes` WRITE;
/*!40000 ALTER TABLE `bak_fix10_classes` DISABLE KEYS */;
INSERT INTO `bak_fix10_classes` VALUES (1,'计科2306班',1,1,29,53,'2025-12-22 17:28:15','2026-06-25 00:35:16'),(5,'软工2306班',1,2,NULL,53,'2026-03-04 02:43:20','2026-06-24 01:47:21'),(30,'计算机2101班',1,1,29,53,'2026-05-14 10:40:58','2026-06-25 00:35:16'),(31,'计算机2102班',1,1,18,53,'2026-05-14 10:40:58','2026-06-24 01:47:21'),(32,'软件工程2101班',1,2,18,53,'2026-05-14 10:40:58','2026-06-25 00:35:16'),(33,'软件工程2102班',1,2,19,53,'2026-05-14 10:40:58','2026-06-24 01:47:21'),(34,'人工智能2101班',1,3,19,53,'2026-05-14 10:40:58','2026-06-24 01:47:21'),(35,'人工智能2102班',1,3,22,53,'2026-05-14 10:40:58','2026-06-24 01:47:21');
/*!40000 ALTER TABLE `bak_fix10_classes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix10_colleges`
--

DROP TABLE IF EXISTS `bak_fix10_colleges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix10_colleges` (
  `id` bigint NOT NULL DEFAULT '0',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '1',
  `student_count` int DEFAULT '0',
  `teacher_count` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix10_colleges`
--

LOCK TABLES `bak_fix10_colleges` WRITE;
/*!40000 ALTER TABLE `bak_fix10_colleges` DISABLE KEYS */;
INSERT INTO `bak_fix10_colleges` VALUES (1,'软件学院','SOFT','软件学院',1,0,0,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(2,'电子工程学院','EE','电子工程学院',1,0,0,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(3,'商学院','BUS','商学院',1,0,0,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(4,'艺术设计学院','ART','艺术设计学院',1,0,0,'2025-12-22 17:28:15','2025-12-22 17:28:15');
/*!40000 ALTER TABLE `bak_fix10_colleges` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix10_counselor_profiles`
--

DROP TABLE IF EXISTS `bak_fix10_counselor_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix10_counselor_profiles` (
  `id` bigint NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `college_id` bigint DEFAULT NULL COMMENT '学院ID',
  `counselor_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '辅导员编号',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名',
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部门',
  `title` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职称',
  `education` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学历',
  `introduction` text COLLATE utf8mb4_unicode_ci COMMENT '个人简介',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `college_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学院名称',
  `staff_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '员工编号',
  `office_location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公地点',
  `duty_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '值班电话',
  `class_count` int DEFAULT '0' COMMENT '管理班级数',
  `student_count` int DEFAULT '0' COMMENT '管理学生数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix10_counselor_profiles`
--

LOCK TABLES `bak_fix10_counselor_profiles` WRITE;
/*!40000 ALTER TABLE `bak_fix10_counselor_profiles` DISABLE KEYS */;
INSERT INTO `bak_fix10_counselor_profiles` VALUES (1,53,1,'C53','司明',NULL,NULL,NULL,'学生工作办公室',NULL,NULL,NULL,1,'2026-05-27 13:34:10','2026-08-03 15:32:05','计算机学院','1000000001',NULL,NULL,0,0);
/*!40000 ALTER TABLE `bak_fix10_counselor_profiles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix10_course_plans`
--

DROP TABLE IF EXISTS `bak_fix10_course_plans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix10_course_plans` (
  `id` bigint NOT NULL DEFAULT '0',
  `course_id` bigint NOT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `year` int DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `class_time` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `max_students` int DEFAULT '50',
  `status` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix10_course_plans`
--

LOCK TABLES `bak_fix10_course_plans` WRITE;
/*!40000 ALTER TABLE `bak_fix10_course_plans` DISABLE KEYS */;
INSERT INTO `bak_fix10_course_plans` VALUES (1,1,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周一 1-2节','教学楼A301',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,2,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周一 3-4节','教学楼A302',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,3,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周二 1-2节','教学楼A303',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,4,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周二 3-4节','实训楼B101',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(5,5,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周三 1-2节','实训楼B102',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(6,6,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周三 3-4节','教学楼A304',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(7,7,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周四 5-6节','教学楼A401',80,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(8,8,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周四 7-8节','教学楼A402',80,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(9,9,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周五 5-6节','艺术楼C201',50,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(10,10,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周五 7-8节','教学楼A403',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `bak_fix10_course_plans` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix10_courses`
--

DROP TABLE IF EXISTS `bak_fix10_courses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix10_courses` (
  `id` bigint NOT NULL DEFAULT '0',
  `code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `credits` decimal(5,2) NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `module_id` bigint DEFAULT NULL,
  `score_template` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix10_courses`
--

LOCK TABLES `bak_fix10_courses` WRITE;
/*!40000 ALTER TABLE `bak_fix10_courses` DISABLE KEYS */;
INSERT INTO `bak_fix10_courses` VALUES (1,'MATH001','高等数学',4.00,'必修课','高等数学基础课程',NULL,NULL,29,'2026-05-27 02:00:00','2026-09-16 12:43:52'),(2,'CS102','大学英语',3.00,'必修课',NULL,1,NULL,19,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(3,'CS103','计算机基础',3.00,'必修课',NULL,1,NULL,22,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(4,'CS201','数据结构',4.00,'必修课',NULL,2,NULL,29,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(5,'CS202','计算机网络',3.00,'必修课',NULL,2,NULL,22,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(6,'CS203','操作系统',4.00,'必修课',NULL,2,NULL,23,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(7,'CS301','人工智能导论',3.00,'选修课',NULL,3,NULL,23,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(8,'CS302','机器学习',4.00,'选修课',NULL,3,NULL,23,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(9,'CS303','深度学习',3.00,'选修课',NULL,3,NULL,28,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(10,'CULT001','中国传统文化',1.00,'选修课','中国传统文化概论',4,NULL,29,'2026-05-27 02:00:00','2026-09-16 12:43:52');
/*!40000 ALTER TABLE `bak_fix10_courses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix10_majors`
--

DROP TABLE IF EXISTS `bak_fix10_majors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix10_majors` (
  `id` bigint NOT NULL DEFAULT '0',
  `college_id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `short_name` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专业简称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix10_majors`
--

LOCK TABLES `bak_fix10_majors` WRITE;
/*!40000 ALTER TABLE `bak_fix10_majors` DISABLE KEYS */;
INSERT INTO `bak_fix10_majors` VALUES (1,1,'软件工程','01','软工','软件工程专业',1,'2025-12-22 17:28:15','2026-06-24 00:53:26'),(2,1,'计算机科学与技术','02','计科','计算机科学与技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:53:26'),(3,1,'电子信息工程','03','大数据','数据科学与大数据技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:53:26'),(4,1,'工商管理','04','智科','智能科学与技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(5,1,'视觉传达设计','05','VR','虚拟现实技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(6,2,'电子信息工程','06','电信','电子信息工程专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(7,2,'自动化','07','自动化','自动化专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(8,2,'人工智能','08','AI','人工智能专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(9,3,'电子商务','09','电商','电子商务专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(10,3,'物流管理','10','物流','物流管理专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(11,3,'财务管理','11','财管','财务管理专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(12,4,'网络与新媒体','12','网媒','网络与新媒体专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(13,4,'环境设计','13','环设','环境设计专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(14,4,'视觉传达设计','14','视传','视觉传达设计专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07');
/*!40000 ALTER TABLE `bak_fix10_majors` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix10_score_audit_log`
--

DROP TABLE IF EXISTS `bak_fix10_score_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix10_score_audit_log` (
  `id` bigint NOT NULL DEFAULT '0',
  `student_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `course_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `teacher_id` bigint NOT NULL,
  `old_score` decimal(5,2) DEFAULT NULL,
  `new_score` decimal(5,2) DEFAULT NULL,
  `difference` decimal(5,2) DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix10_score_audit_log`
--

LOCK TABLES `bak_fix10_score_audit_log` WRITE;
/*!40000 ALTER TABLE `bak_fix10_score_audit_log` DISABLE KEYS */;
INSERT INTO `bak_fix10_score_audit_log` VALUES (1,3,'司明',1,'高等数学',29,55.00,65.00,10.00,'试卷复核发现加错分','于凤海','2026-06-25 01:38:17');
/*!40000 ALTER TABLE `bak_fix10_score_audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix10_score_record`
--

DROP TABLE IF EXISTS `bak_fix10_score_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix10_score_record` (
  `id` bigint NOT NULL DEFAULT '0',
  `course_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `score` decimal(5,2) DEFAULT NULL,
  `score_level` int DEFAULT NULL,
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学期',
  `year` int DEFAULT NULL COMMENT '学年',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) DEFAULT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(3,2) DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix10_score_record`
--

LOCK TABLES `bak_fix10_score_record` WRITE;
/*!40000 ALTER TABLE `bak_fix10_score_record` DISABLE KEYS */;
INSERT INTO `bak_fix10_score_record` VALUES (139,1,4,22,'邱琳紫','计科2306班',94.00,1,'2',2023,'思维敏捷，解题能力强','2026-05-27 10:00:00','2026-09-16 10:48:22',92.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(140,2,4,22,'邱琳紫','计科2306班',89.00,2,'2',2023,'口语表达流畅','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(141,3,4,22,'邱琳紫','计科2306班',87.00,2,'2',2023,'数制转换准确','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(142,4,4,22,'邱琳紫','计科2306班',80.00,2,'2',2023,'算法掌握扎实','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(143,5,4,22,'邱琳紫','计科2306班',91.00,1,'2',2023,'组网实验能力强，配置规范','2026-05-27 10:00:00','2026-09-16 10:48:22',90.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(144,6,4,22,'邱琳紫','计科2306班',84.00,2,'2',2023,'理论理解深入','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(145,1,5,22,'宋新鹏','计科2306班',90.00,1,'2',2023,'学习积极主动','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(146,2,5,22,'宋新鹏','计科2306班',87.00,2,'2',2023,'阅读能力较强','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(147,3,5,22,'宋新鹏','计科2306班',84.00,2,'2',2023,'计算机体系结构理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(148,4,5,22,'宋新鹏','计科2306班',80.00,2,'2',2023,'项目完成出色','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(149,5,5,22,'宋新鹏','计科2306班',92.00,1,'2',2023,'网络拓扑设计合理','2026-05-27 10:00:00','2026-09-16 10:48:22',90.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(150,6,5,22,'宋新鹏','计科2306班',82.00,2,'2',2023,'实验报告完整','2026-05-27 10:00:00','2026-09-16 10:48:22',80.00,84.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(151,1,6,22,'赵琦','计科2306班',79.00,3,'2',2022,'基础扎实，解题规范','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(152,2,6,22,'赵琦','计科2306班',83.00,2,'2',2022,'听力需加强','2026-05-27 10:00:00','2026-09-16 10:48:22',80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(153,3,6,22,'赵琦','计科2306班',74.00,3,'2',2022,'部分概念模糊','2026-05-27 10:00:00','2026-09-16 10:48:22',70.00,78.00,74.00,'B',2.30,NULL,NULL,NULL,NULL),(154,4,6,22,'赵琦','计科2306班',87.00,2,'2',2022,'递归理解较好','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(155,5,6,22,'赵琦','计科2306班',75.00,3,'2',2022,'网络协议掌握良好','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(156,6,6,22,'赵琦','计科2306班',80.00,2,'2',2022,'需加强实践','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(157,1,7,22,'苏红阳','计科2306班',84.00,2,'2',2023,'课堂参与积极','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(158,2,7,22,'苏红阳','计科2306班',80.00,2,'2',2023,'写作能力突出','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(159,3,7,22,'苏红阳','计科2306班',87.00,2,'2',2023,'操作步骤完整','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(160,4,7,22,'苏红阳','计科2306班',90.00,1,'2',2023,'树结构掌握较好','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(161,5,7,22,'苏红阳','计科2306班',78.00,3,'2',2023,'多路复用理解深入','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(162,6,7,22,'苏红阳','计科2306班',75.00,3,'2',2023,'进程调度理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(163,1,8,22,'唐浩然','计科2306班',88.00,2,'2',2023,'解题思路清晰','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(164,2,8,22,'唐浩然','计科2306班',80.00,2,'2',2023,'词汇量丰富','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(165,3,8,22,'唐浩然','计科2306班',75.00,3,'2',2023,'办公软件操作熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(166,4,8,22,'唐浩然','计科2306班',84.00,2,'2',2023,'图算法待加强','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(167,5,8,22,'唐浩然','计科2306班',90.00,1,'2',2023,'网络命令熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(168,6,8,22,'唐浩然','计科2306班',81.00,2,'2',2023,'进程管理理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(169,1,9,22,'王宏民','计科2306班',66.00,4,'2',2023,'计算能力一般','2026-05-27 10:00:00','2026-09-16 10:48:22',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(170,2,9,22,'王宏民','计科2306班',72.00,3,'2',2023,'语法基础尚可','2026-05-27 10:00:00','2026-09-16 10:48:22',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(171,3,9,22,'王宏民','计科2306班',69.00,4,'2',2023,'数制概念需巩固','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(172,4,9,22,'王宏民','计科2306班',75.00,3,'2',2023,'排序算法掌握','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(173,5,9,22,'王宏民','计科2306班',73.00,3,'2',2023,'差错控制需加强','2026-05-27 10:00:00','2026-09-16 10:48:22',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(174,6,9,22,'王宏民','计科2306班',78.00,3,'2',2023,'内存管理理解一般','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(175,1,10,22,'王玉蛟','计科2306班',81.00,2,'2',2023,'学习态度认真','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(176,2,10,22,'王玉蛟','计科2306班',84.00,2,'2',2023,'翻译能力较强','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(177,3,10,22,'王玉蛟','计科2306班',75.00,3,'2',2023,'二进制运算需加强','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(178,4,10,22,'王玉蛟','计科2306班',89.00,2,'2',2023,'图论部分表现好','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(179,5,10,22,'王玉蛟','计科2306班',78.00,3,'2',2023,'组网方案有创意','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(180,6,10,22,'王玉蛟','计科2306班',80.00,2,'2',2023,'设备管理理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(181,1,11,22,'吴雨航','计科2306班',64.00,4,'2',2023,'积分计算有困难','2026-05-27 10:00:00','2026-09-16 10:48:22',60.00,68.00,64.00,'C',1.50,NULL,NULL,NULL,NULL),(182,2,11,22,'吴雨航','计科2306班',69.00,4,'2',2023,'词汇量不足','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(183,3,11,22,'吴雨航','计科2306班',65.00,4,'2',2023,'需要多练习','2026-05-27 10:00:00','2026-09-16 10:48:22',62.00,68.00,65.00,'C',1.50,NULL,NULL,NULL,NULL),(184,4,11,22,'吴雨航','计科2306班',72.00,3,'2',2023,'链表操作不熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(185,5,11,22,'吴雨航','计科2306班',80.00,2,'2',2023,'网络基础过关','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(186,6,11,22,'吴雨航','计科2306班',73.00,3,'2',2023,'文件系统需复习','2026-05-27 10:00:00','2026-09-16 10:48:22',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(187,1,12,22,'徐瑞瑶','计科2306班',87.00,2,'2',2023,'数学思维好','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(188,2,12,22,'徐瑞瑶','计科2306班',84.00,2,'2',2023,'听力理解优秀','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(189,3,12,22,'徐瑞瑶','计科2306班',81.00,2,'2',2023,'存储体系理解深入','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(190,4,12,22,'徐瑞瑶','计科2306班',90.00,1,'2',2023,'哈希表掌握好','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(191,5,12,22,'徐瑞瑶','计科2306班',88.00,2,'2',2023,'分层模型理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(192,6,12,22,'徐瑞瑶','计科2306班',82.00,2,'2',2023,'并发控制理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,85.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(193,1,13,23,'韩坤','计科2306班',63.00,4,'2',2023,'基础薄弱需加强','2026-05-27 10:00:00','2026-09-16 10:48:22',60.00,65.00,63.00,'C',1.00,NULL,NULL,NULL,NULL),(194,2,13,23,'韩坤','计科2306班',75.00,3,'2',2023,'语法错误较多','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(195,3,13,23,'韩坤','计科2306班',70.00,3,'2',2023,'进制换算常出错','2026-05-27 10:00:00','2026-09-16 10:48:22',68.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(196,4,13,23,'韩坤','计科2306班',59.00,5,'2',2023,'栈和队列混淆','2026-05-27 10:00:00','2026-09-16 10:48:22',55.00,62.00,59.00,'D',0.00,NULL,NULL,NULL,NULL),(197,5,13,23,'韩坤','计科2306班',68.00,4,'2',2023,'路由算法需巩固','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,70.00,68.00,'C',2.00,NULL,NULL,NULL,NULL),(198,6,13,23,'韩坤','计科2306班',54.00,5,'2',2023,'基本概念模糊','2026-05-27 10:00:00','2026-09-16 10:48:22',50.00,58.00,54.00,'D',0.00,NULL,NULL,NULL,NULL),(199,1,14,23,'张博巍','计科2306班',93.00,1,'2',2023,'微积分掌握扎实','2026-05-27 10:00:00','2026-09-16 10:48:22',90.00,95.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(200,2,14,23,'张博巍','计科2306班',90.00,1,'2',2023,'口语发音标准','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(201,3,14,23,'张博巍','计科2306班',88.00,2,'2',2023,'计算机组成理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(202,4,14,23,'张博巍','计科2306班',94.00,1,'2',2023,'动态规划掌握好','2026-05-27 10:00:00','2026-09-16 10:48:22',92.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(203,5,14,23,'张博巍','计科2306班',80.00,2,'2',2023,'组网实践能力强','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(204,6,14,23,'张博巍','计科2306班',90.00,1,'2',2023,'内核结构掌握好','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(205,1,15,23,'程启頔','计科2306班',63.00,4,'2',2023,'上课注意力不集中','2026-05-27 10:00:00','2026-09-16 10:48:22',58.00,68.00,63.00,'C',1.00,NULL,NULL,NULL,NULL),(206,2,15,23,'程启頔','计科2306班',69.00,4,'2',2023,'听说能力待提高','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(207,3,15,23,'程启頔','计科2306班',64.00,4,'2',2023,'算法流程图困难','2026-05-27 10:00:00','2026-09-16 10:48:22',60.00,68.00,64.00,'C',1.50,NULL,NULL,NULL,NULL),(208,4,15,23,'程启頔','计科2306班',66.00,4,'2',2023,'查找算法需练习','2026-05-27 10:00:00','2026-09-16 10:48:22',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(209,5,15,23,'程启頔','计科2306班',81.00,2,'2',2023,'子网划分基本正确','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(210,6,15,23,'程启頔','计科2306班',73.00,3,'2',2023,'系统调用需加强','2026-05-27 10:00:00','2026-09-16 10:48:22',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(211,1,16,23,'邓雨轩','计科2306班',81.00,2,'2',2023,'数列极限理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(212,2,16,23,'邓雨轩','计科2306班',85.00,2,'2',2023,'作文表达流畅','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(213,3,16,23,'邓雨轩','计科2306班',79.00,3,'2',2023,'文件管理操作准确','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(214,4,16,23,'邓雨轩','计科2306班',89.00,2,'2',2023,'二叉树遍历掌握','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(215,5,16,23,'邓雨轩','计科2306班',75.00,3,'2',2023,'接口配置规范','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(216,6,16,23,'邓雨轩','计科2306班',81.00,2,'2',2023,'信号量机制理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(217,1,17,23,'高菲','计科2306班',88.00,2,'2',2023,'导数应用熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(218,2,17,23,'高菲','计科2306班',80.00,2,'2',2023,'阅读理解准确','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(219,3,17,23,'高菲','计科2306班',84.00,2,'2',2023,'表格数据处理掌握','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(220,4,17,23,'高菲','计科2306班',90.00,1,'2',2023,'B树B+树理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(221,5,17,23,'高菲','计科2306班',78.00,3,'2',2023,'数据流分析熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(222,6,17,23,'高菲','计科2306班',85.00,2,'2',2023,'页面置换算法理解','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(223,1,18,23,'胡钰','计科2306班',69.00,4,'2',2023,'不定积分有进步','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(224,2,18,23,'胡钰','计科2306班',73.00,3,'2',2023,'语法有进步','2026-05-27 10:00:00','2026-09-16 10:48:22',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(225,3,18,23,'胡钰','计科2306班',66.00,4,'2',2023,'硬件组成理解不足','2026-05-27 10:00:00','2026-09-16 10:48:22',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(226,4,18,23,'胡钰','计科2306班',79.00,3,'2',2023,'代码实现有困难','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(227,5,18,23,'胡钰','计科2306班',83.00,2,'2',2023,'Socket编程需加强','2026-05-27 10:00:00','2026-09-16 10:48:22',80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(228,6,18,23,'胡钰','计科2306班',75.00,3,'2',2023,'缓冲区管理概念模糊','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(229,1,19,23,'李佳芮','计科2306班',94.00,1,'2',2023,'空间解析几何好','2026-05-27 10:00:00','2026-09-16 10:48:22',92.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(230,2,19,23,'李佳芮','计科2306班',90.00,1,'2',2023,'演讲能力出色','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(231,3,19,23,'李佳芮','计科2306班',93.00,1,'2',2023,'网络基础知识理解深','2026-05-27 10:00:00','2026-09-16 10:48:22',90.00,95.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(232,4,19,23,'李佳芮','计科2306班',88.00,2,'2',2023,'最短路径算法优秀','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(233,5,19,23,'李佳芮','计科2306班',80.00,2,'2',2023,'交换原理掌握好','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(234,6,19,23,'李佳芮','计科2306班',94.00,1,'2',2023,'存储管理理解深','2026-05-27 10:00:00','2026-09-16 10:48:22',92.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(235,1,20,23,'李爽','计科2306班',54.00,5,'2',2023,'基础不扎实','2026-05-27 10:00:00','2026-09-16 10:48:22',50.00,58.00,54.00,'D',0.00,NULL,NULL,NULL,NULL),(236,2,20,23,'李爽','计科2306班',73.00,3,'2',2023,'词汇积累不够','2026-05-27 10:00:00','2026-09-16 10:48:22',70.00,75.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(237,3,20,23,'李爽','计科2306班',52.00,5,'2',2023,'故障判断困难','2026-05-27 10:00:00','2026-09-16 10:48:22',48.00,55.00,52.00,'D',0.00,NULL,NULL,NULL,NULL),(238,4,20,23,'李爽','计科2306班',70.00,3,'2',2023,'遍历算法需复习','2026-05-27 10:00:00','2026-09-16 10:48:22',68.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(239,5,20,23,'李爽','计科2306班',47.00,5,'2',2023,'协议层次混淆','2026-05-27 10:00:00','2026-09-16 10:48:22',42.00,52.00,47.00,'D',0.00,NULL,NULL,NULL,NULL),(240,6,20,23,'李爽','计科2306班',69.00,4,'2',2023,'进程通信理解一般','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(241,1,21,23,'梁莹静','计科2306班',84.00,2,'2',2023,'多元函数掌握好','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(242,2,21,23,'梁莹静','计科2306班',81.00,2,'2',2023,'跨文化交际能力好','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(243,3,21,23,'梁莹静','计科2306班',87.00,2,'2',2023,'操作系统概念清晰','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(244,4,21,23,'梁莹静','计科2306班',83.00,2,'2',2023,'堆排序理解好','2026-05-27 10:00:00','2026-09-16 10:48:22',80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(245,5,21,23,'梁莹静','计科2306班',90.00,1,'2',2023,'网线制作规范','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(246,6,21,23,'梁莹静','计科2306班',79.00,3,'2',2023,'进程同步掌握好','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(247,1,22,23,'孟阳','计科2306班',75.00,3,'2',2023,'函数连续性需加强','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(248,2,22,23,'孟阳','计科2306班',72.00,3,'2',2023,'长难句理解困难','2026-05-27 10:00:00','2026-09-16 10:48:22',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(249,3,22,23,'孟阳','计科2306班',73.00,3,'2',2023,'数据编码理解不足','2026-05-27 10:00:00','2026-09-16 10:48:22',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(250,4,22,23,'孟阳','计科2306班',78.00,3,'2',2023,'快速排序基本掌握','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(251,5,22,23,'孟阳','计科2306班',69.00,4,'2',2023,'抓包分析有进步','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(252,6,22,23,'孟阳','计科2306班',81.00,2,'2',2023,'调度算法理解','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(253,1,23,23,'王瑾泰','计科2306班',81.00,2,'2',2023,'定积分应用熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(254,2,23,23,'王瑾泰','计科2306班',79.00,3,'2',2023,'完形填空准确','2026-05-27 10:00:00','2026-09-16 10:48:22',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(255,3,23,23,'王瑾泰','计科2306班',75.00,3,'2',2023,'信息安全意识需巩固','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(256,4,23,23,'王瑾泰','计科2306班',85.00,2,'2',2023,'AVL树掌握好','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(257,5,23,28,'王瑾泰','计科2306班',80.00,2,'2',2023,'IP地址规划熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(258,6,23,28,'王瑾泰','计科2306班',75.00,3,'2',2023,'虚拟内存理解','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(259,1,24,28,'王平洋','计科2306班',69.00,4,'2',2023,'级数概念模糊','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(260,2,24,28,'王平洋','计科2306班',73.00,3,'2',2023,'翻译题薄弱','2026-05-27 10:00:00','2026-09-16 10:48:22',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(261,3,24,28,'王平洋','计科2306班',66.00,4,'2',2023,'数据库基础理解一般','2026-05-27 10:00:00','2026-09-16 10:48:22',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(262,4,24,28,'王平洋','计科2306班',80.00,2,'2',2023,'并查集基本掌握','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(263,5,24,28,'王平洋','计科2306班',75.00,3,'2',2023,'子网掩码计算生疏','2026-05-27 10:00:00','2026-09-16 10:48:22',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(264,6,24,28,'王平洋','计科2306班',72.00,3,'2',2023,'死锁处理需复习','2026-05-27 10:00:00','2026-09-16 10:48:22',68.00,76.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(265,1,25,28,'夏志浩','计科2306班',90.00,1,'2',2023,'数学基础雄厚','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(266,2,25,28,'夏志浩','计科2306班',88.00,2,'2',2023,'学术写作优秀','2026-05-27 10:00:00','2026-09-16 10:48:22',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(267,3,25,28,'夏志浩','计科2306班',81.00,2,'2',2023,'动手操作能力强','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(268,4,25,28,'夏志浩','计科2306班',85.00,2,'2',2023,'算法竞赛获奖','2026-05-27 10:00:00','2026-09-16 10:48:22',82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(269,5,25,28,'夏志浩','计科2306班',92.00,1,'2',2023,'精通网络排障','2026-05-27 10:00:00','2026-09-16 10:48:22',88.00,95.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(270,6,25,28,'夏志浩','计科2306班',82.00,2,'2',2023,'多线程调度理解深','2026-05-27 10:00:00','2026-09-16 10:48:22',78.00,86.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(271,1,26,28,'张健','计科2306班',52.00,5,'2',2023,'多处知识漏洞','2026-05-27 10:00:00','2026-09-16 10:48:22',48.00,55.00,52.00,'D',0.00,NULL,NULL,NULL,NULL),(272,2,26,28,'张健','计科2306班',72.00,3,'2',2023,'听力严重薄弱','2026-05-27 10:00:00','2026-09-16 10:48:22',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(273,3,26,28,'张健','计科2306班',48.00,5,'2',2023,'快捷键使用不熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',45.00,50.00,48.00,'D',0.00,NULL,NULL,NULL,NULL),(274,4,26,28,'张健','计科2306班',69.00,4,'2',2023,'基本操作不熟练','2026-05-27 10:00:00','2026-09-16 10:48:22',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(275,5,26,28,'张健','计科2306班',49.00,5,'2',2023,'网络分层理解不足','2026-05-27 10:00:00','2026-09-16 10:48:22',42.00,55.00,49.00,'D',0.00,NULL,NULL,NULL,NULL),(276,6,26,28,'张健','计科2306班',66.00,4,'2',2023,'原语概念模糊','2026-05-27 10:00:00','2026-09-16 10:48:22',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(277,1,3,28,'司明','计科2306班',87.00,2,'3',2024,'第3学期高等数学','2024-06-20 00:00:00','2026-09-16 10:48:22',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(278,2,3,28,'司明','计科2306班',80.50,2,'3',2024,'第3学期大学英语','2024-06-20 00:00:00','2026-09-16 10:48:22',78.00,82.00,80.50,'A-',3.00,NULL,NULL,NULL,NULL),(279,3,3,28,'司明','计科2306班',70.00,3,'3',2024,'第3学期计算机基础','2024-06-20 00:00:00','2026-09-16 10:48:22',72.00,68.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(280,4,3,28,'司明','计科2306班',91.00,1,'3',2024,'第3学期数据结构优秀','2024-06-20 00:00:00','2026-09-16 10:48:22',90.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(281,5,3,28,'司明','计科2306班',89.00,2,'3',2024,'第3学期计算机网络','2024-06-20 00:00:00','2026-09-16 10:48:22',88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(282,6,3,28,'司明','计科2306班',62.00,4,'3',2024,'第3学期操作系统','2024-06-20 00:00:00','2026-09-16 10:48:22',65.00,60.00,62.00,'C',1.00,NULL,NULL,NULL,NULL),(283,1,3,28,'司明','计科2306班',71.50,3,'4',2024,'第4学期高等数学下滑','2025-01-15 00:00:00','2026-09-16 10:48:22',70.00,72.00,71.50,'B',2.00,NULL,NULL,NULL,NULL),(284,2,3,28,'司明','计科2306班',51.00,5,'4',2024,'第4学期大学英语不及格','2025-01-15 00:00:00','2026-09-16 10:48:22',55.00,48.00,51.00,'D',0.00,NULL,NULL,NULL,NULL),(285,3,3,28,'司明','计科2306班',59.00,5,'4',2024,'第4学期计算机基础不及格','2025-01-15 00:00:00','2026-09-16 10:48:22',60.00,58.00,59.00,'D',0.00,NULL,NULL,NULL,NULL),(286,4,3,28,'司明','计科2306班',84.00,2,'4',2024,'第4学期数据结构','2025-01-15 00:00:00','2026-09-16 10:48:22',82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(287,6,3,28,'司明','计科2306班',40.00,5,'4',2024,'第4学期操作系统严重不及格','2025-01-15 00:00:00','2026-09-16 10:48:22',42.00,38.00,40.00,'D',0.00,NULL,NULL,NULL,NULL),(288,1,3,28,'司明','计科2306班',56.50,5,'5',2025,'第5学期高等数学持续下滑','2025-06-20 00:00:00','2026-09-16 10:48:22',58.00,55.00,56.50,'D',0.00,NULL,NULL,NULL,NULL),(289,5,3,28,'司明','计科2306班',65.00,4,'5',2025,'第5学期计算机网络勉强及格','2025-06-20 00:00:00','2026-09-16 10:48:22',66.00,64.00,65.00,'C',1.50,NULL,NULL,NULL,NULL),(290,3,3,28,'司明','计科2306班',47.00,5,'5',2025,'第5学期计算机基础补考仍不及格','2025-06-20 00:00:00','2026-09-16 10:48:22',50.00,45.00,47.00,'D',0.00,NULL,NULL,NULL,NULL),(291,7,3,28,'司明','计科2306班',86.00,2,'6',2025,'算法流程梳理清晰','2026-06-25 09:58:16','2026-09-16 10:48:22',88.00,85.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(294,8,3,28,'司明','计科2306班',91.00,1,'6',2025,'模型训练过程理解深','2026-06-25 09:58:16','2026-09-16 10:48:22',90.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(296,7,4,28,'邱琳紫','计科2306班',90.00,1,'6',2025,'博弈树分析准确','2026-05-27 10:00:00','2026-09-16 10:44:18',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(297,7,5,28,'宋新鹏','计科2306班',88.00,2,'6',2025,'课堂讨论积极，概念清楚','2026-05-27 10:00:00','2026-09-16 10:44:18',93.00,83.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(298,7,6,28,'赵琦','计科2306班',85.00,2,'6',2024,'模糊推理掌握较好','2026-05-27 10:00:00','2026-09-16 10:44:18',88.00,82.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(299,7,7,28,'苏红阳','计科2306班',92.00,1,'6',2025,'启发式搜索理解透彻','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,97.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(300,7,8,28,'唐浩然','计科2306班',80.00,2,'6',2025,'智能体设计基本掌握','2026-05-27 10:00:00','2026-09-16 10:44:18',80.00,79.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(301,7,9,28,'王宏民','计科2306班',78.00,3,'6',2025,'搜索策略选择需练习','2026-05-27 10:00:00','2026-09-16 10:44:18',73.00,83.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(302,7,10,28,'王玉蛟','计科2306班',86.00,2,'6',2025,'遗传算法理解到位','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,87.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(303,7,11,28,'吴雨航','计科2306班',75.00,3,'6',2025,'理论学习尚可，实验偏弱','2026-05-27 10:00:00','2026-09-16 10:44:18',72.00,77.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(304,7,12,28,'徐瑞瑶','计科2306班',88.00,2,'6',2025,'专家系统案例完成好','2026-05-27 10:00:00','2026-09-16 10:44:18',82.00,94.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(305,7,13,28,'韩坤','计科2306班',72.00,3,'6',2025,'基础概念模糊，需巩固','2026-05-27 10:00:00','2026-09-16 10:44:18',70.00,74.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(306,7,14,28,'张博巍','计科2306班',94.00,1,'6',2025,'搜索算法掌握扎实','2026-05-27 10:00:00','2026-09-16 10:44:18',100.00,88.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(307,7,15,28,'程启頔','计科2306班',70.00,3,'6',2025,'上课注意力不集中','2026-05-27 10:00:00','2026-09-16 10:44:18',65.00,75.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(308,7,16,28,'邓雨轩','计科2306班',83.00,2,'6',2025,'知识图谱作业完成认真','2026-05-27 10:00:00','2026-09-16 10:44:18',81.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(309,7,17,28,'高菲','计科2306班',89.00,2,'6',2025,'谓词逻辑推导熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,91.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(310,7,18,28,'胡钰','计科2306班',76.00,3,'6',2025,'逻辑推理部分有进步','2026-05-27 10:00:00','2026-09-16 10:44:18',71.00,80.00,76.00,'B',2.70,NULL,NULL,NULL,NULL),(311,7,19,28,'李佳芮','计科2306班',91.00,1,'6',2025,'知识表示章节表现优秀','2026-05-27 10:00:00','2026-09-16 10:44:18',92.00,89.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(312,7,20,28,'李爽','计科2306班',68.00,4,'6',2025,'搜索算法理解吃力','2026-05-27 10:00:00','2026-09-16 10:44:18',67.00,69.00,68.00,'C',2.00,NULL,NULL,NULL,NULL),(313,7,21,28,'梁莹静','计科2306班',87.00,2,'6',2025,'状态空间建模规范','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,90.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(314,7,22,28,'孟阳','计科2306班',74.00,3,'6',2025,'状态空间建模需加强','2026-05-27 10:00:00','2026-09-16 10:44:18',75.00,72.00,74.00,'B',2.30,NULL,NULL,NULL,NULL),(315,7,23,28,'王瑾泰','计科2306班',82.00,2,'6',2025,'基本概念清晰，实践偏少','2026-05-27 10:00:00','2026-09-16 10:44:18',81.00,82.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(316,7,24,28,'王平洋','计科2306班',73.00,3,'6',2025,'算法伪代码书写待规范','2026-05-27 10:00:00','2026-09-16 10:44:18',77.00,68.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(317,7,25,28,'夏志浩','计科2306班',95.00,1,'6',2025,'智能体建模思路清晰','2026-05-27 10:00:00','2026-09-16 10:44:18',96.00,93.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(318,7,26,28,'张健','计科2306班',65.00,4,'6',2025,'基础薄弱，多处知识漏洞','2026-05-27 10:00:00','2026-09-16 10:44:18',61.00,69.00,65.00,'C',1.50,NULL,NULL,NULL,NULL),(319,8,4,28,'邱琳紫','计科2306班',88.00,2,'6',2025,'决策树与剪枝理解好','2026-05-27 10:00:00','2026-09-16 10:44:18',86.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(320,8,5,28,'宋新鹏','计科2306班',90.00,1,'6',2025,'交叉验证运用熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',97.00,83.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(321,8,6,18,'赵琦','计科2306班',82.00,2,'6',2024,'梯度下降理解正确','2026-05-27 10:00:00','2026-09-16 10:44:18',75.00,89.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(322,8,7,18,'苏红阳','计科2306班',86.00,2,'6',2025,'聚类算法实验完成好','2026-05-27 10:00:00','2026-09-16 10:44:18',88.00,84.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(323,8,8,18,'唐浩然','计科2306班',84.00,2,'6',2025,'线性回归实现规范','2026-05-27 10:00:00','2026-09-16 10:44:18',79.00,88.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(324,8,9,18,'王宏民','计科2306班',76.00,3,'6',2025,'调参经验不足需多练','2026-05-27 10:00:00','2026-09-16 10:44:18',70.00,81.00,76.00,'B',2.70,NULL,NULL,NULL,NULL),(325,8,10,18,'王玉蛟','计科2306班',83.00,2,'6',2025,'朴素贝叶斯应用熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',85.00,80.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(326,8,11,18,'吴雨航','计科2306班',74.00,3,'6',2025,'损失函数推导需加强','2026-05-27 10:00:00','2026-09-16 10:44:18',73.00,75.00,74.00,'B',2.30,NULL,NULL,NULL,NULL),(327,8,12,18,'徐瑞瑶','计科2306班',85.00,2,'6',2025,'模型评估指标理解到位','2026-05-27 10:00:00','2026-09-16 10:44:18',86.00,84.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(328,8,13,18,'韩坤','计科2306班',70.00,3,'6',2025,'实验报告较粗糙','2026-05-27 10:00:00','2026-09-16 10:44:18',66.00,74.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(329,8,14,18,'张博巍','计科2306班',92.00,1,'6',2025,'集成学习掌握扎实','2026-05-27 10:00:00','2026-09-16 10:44:18',91.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(330,8,15,18,'程启頔','计科2306班',68.00,4,'6',2025,'模型原理理解不透','2026-05-27 10:00:00','2026-09-16 10:44:18',72.00,63.00,68.00,'C',2.00,NULL,NULL,NULL,NULL),(331,8,16,18,'邓雨轩','计科2306班',81.00,2,'6',2025,'KNN分类实验认真','2026-05-27 10:00:00','2026-09-16 10:44:18',81.00,80.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(332,8,17,18,'高菲','计科2306班',87.00,2,'6',2025,'支持向量机推导清晰','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,87.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(333,8,18,18,'胡钰','计科2306班',78.00,3,'6',2025,'过拟合问题理解一般','2026-05-27 10:00:00','2026-09-16 10:44:18',79.00,77.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(334,8,19,18,'李佳芮','计科2306班',93.00,1,'6',2025,'特征工程做得细致','2026-05-27 10:00:00','2026-09-16 10:44:18',92.00,94.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(335,8,20,18,'李爽','计科2306班',66.00,4,'6',2025,'公式推导常出错','2026-05-27 10:00:00','2026-09-16 10:44:18',66.00,66.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(336,8,21,18,'梁莹静','计科2306班',84.00,2,'6',2025,'降维方法掌握较好','2026-05-27 10:00:00','2026-09-16 10:44:18',83.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(337,8,22,18,'孟阳','计科2306班',72.00,3,'6',2025,'数学推导基础较弱','2026-05-27 10:00:00','2026-09-16 10:44:18',69.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(338,8,23,18,'王瑾泰','计科2306班',80.00,2,'6',2025,'基本算法能独立实现','2026-05-27 10:00:00','2026-09-16 10:44:18',85.00,75.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(339,8,24,18,'王平洋','计科2306班',71.00,3,'6',2025,'特征选择方法需复习','2026-05-27 10:00:00','2026-09-16 10:44:18',65.00,77.00,71.00,'B',2.00,NULL,NULL,NULL,NULL),(340,8,25,18,'夏志浩','计科2306班',96.00,1,'6',2025,'模型调优能力突出','2026-05-27 10:00:00','2026-09-16 10:44:18',96.00,95.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(341,8,26,18,'张健','计科2306班',63.00,4,'6',2025,'基础不牢，需系统复习','2026-05-27 10:00:00','2026-09-16 10:44:18',61.00,65.00,63.00,'C',1.00,NULL,NULL,NULL,NULL),(342,9,4,18,'邱琳紫','计科2306班',85.00,2,'6',2025,'张量运算熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,83.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(343,9,5,18,'宋新鹏','计科2306班',87.00,2,'6',2025,'优化器选择有依据','2026-05-27 10:00:00','2026-09-16 10:44:18',92.00,81.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(344,9,6,18,'赵琦','计科2306班',83.00,2,'6',2024,'激活函数对比分析好','2026-05-27 10:00:00','2026-09-16 10:44:18',82.00,84.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(345,9,7,18,'苏红阳','计科2306班',90.00,1,'6',2025,'卷积网络实验优秀','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,93.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(346,9,8,18,'唐浩然','计科2306班',79.00,3,'6',2025,'生成模型概念尚可','2026-05-27 10:00:00','2026-09-16 10:44:18',82.00,76.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(347,9,9,18,'王宏民','计科2306班',74.00,3,'6',2025,'链式求导需巩固','2026-05-27 10:00:00','2026-09-16 10:44:18',71.00,77.00,74.00,'B',2.30,NULL,NULL,NULL,NULL),(348,9,10,18,'王玉蛟','计科2306班',88.00,2,'6',2025,'迁移学习应用得当','2026-05-27 10:00:00','2026-09-16 10:44:18',90.00,85.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(349,9,11,18,'吴雨航','计科2306班',72.00,3,'6',2025,'代码实现有困难','2026-05-27 10:00:00','2026-09-16 10:44:18',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(350,9,12,18,'徐瑞瑶','计科2306班',86.00,2,'6',2025,'批归一化理解到位','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,88.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(351,9,13,18,'韩坤','计科2306班',68.00,4,'6',2025,'梯度问题理解模糊','2026-05-27 10:00:00','2026-09-16 10:44:18',73.00,62.00,68.00,'C',2.00,NULL,NULL,NULL,NULL),(352,9,14,18,'张博巍','计科2306班',89.00,2,'6',2025,'循环网络掌握扎实','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(353,9,15,18,'程启頔','计科2306班',66.00,4,'6',2025,'基础概念掌握不牢','2026-05-27 10:00:00','2026-09-16 10:44:18',64.00,68.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(354,9,16,18,'邓雨轩','计科2306班',80.00,2,'6',2025,'池化操作理解正确','2026-05-27 10:00:00','2026-09-16 10:44:18',75.00,85.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(355,9,17,18,'高菲','计科2306班',91.00,1,'6',2025,'反向传播推导熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(356,9,18,18,'胡钰','计科2306班',75.00,3,'6',2025,'网络训练调参较弱','2026-05-27 10:00:00','2026-09-16 10:44:18',76.00,73.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(357,9,19,18,'李佳芮','计科2306班',94.00,1,'6',2025,'注意力机制理解深入','2026-05-27 10:00:00','2026-09-16 10:44:18',96.00,92.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(358,9,20,18,'李爽','计科2306班',64.00,4,'6',2025,'推导过程错误较多','2026-05-27 10:00:00','2026-09-16 10:44:18',67.00,61.00,64.00,'C',1.50,NULL,NULL,NULL,NULL),(359,9,21,18,'梁莹静','计科2306班',82.00,2,'6',2025,'模型搭建基本规范','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,80.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(360,9,22,18,'孟阳','计科2306班',70.00,3,'6',2025,'理论理解较浅','2026-05-27 10:00:00','2026-09-16 10:44:18',67.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(361,9,23,18,'王瑾泰','计科2306班',78.00,3,'6',2025,'正则化方法需多练','2026-05-27 10:00:00','2026-09-16 10:44:18',78.00,78.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(362,9,24,18,'王平洋','计科2306班',69.00,4,'6',2025,'实验复现不完整','2026-05-27 10:00:00','2026-09-16 10:44:18',74.00,64.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(363,9,25,18,'夏志浩','计科2306班',92.00,1,'6',2025,'网络结构设计有想法','2026-05-27 10:00:00','2026-09-16 10:44:18',92.00,91.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(364,9,26,18,'张健','计科2306班',61.00,4,'6',2025,'基础薄弱，需从头复习','2026-05-27 10:00:00','2026-09-16 10:44:18',63.00,58.00,61.00,'C',1.00,NULL,NULL,NULL,NULL),(365,10,4,18,'邱琳紫','计科2306班',92.00,1,'6',2025,'经典诵读积累丰富','2026-05-27 10:00:00','2026-09-16 10:44:18',90.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(366,10,5,18,'宋新鹏','计科2306班',86.00,2,'6',2025,'课堂分享积极，见解独到','2026-05-27 10:00:00','2026-09-16 10:44:18',91.00,81.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(367,10,6,18,'赵琦','计科2306班',84.00,2,'6',2024,'古典建筑理解较好','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,81.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(368,10,7,18,'苏红阳','计科2306班',88.00,2,'6',2025,'传统节日民俗熟悉','2026-05-27 10:00:00','2026-09-16 10:44:18',86.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(369,10,8,18,'唐浩然','计科2306班',81.00,2,'6',2025,'中医文化了解较多','2026-05-27 10:00:00','2026-09-16 10:44:18',77.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(370,10,9,18,'王宏民','计科2306班',77.00,3,'6',2025,'论文选题思路一般','2026-05-27 10:00:00','2026-09-16 10:44:18',81.00,72.00,77.00,'B',2.70,NULL,NULL,NULL,NULL),(371,10,10,18,'王玉蛟','计科2306班',90.00,1,'6',2025,'礼乐制度理解到位','2026-05-27 10:00:00','2026-09-16 10:44:18',93.00,87.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(372,10,11,18,'吴雨航','计科2306班',73.00,3,'6',2025,'经典原文理解吃力','2026-05-27 10:00:00','2026-09-16 10:44:18',77.00,69.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(373,10,12,18,'徐瑞瑶','计科2306班',87.00,2,'6',2025,'戏曲艺术理解深入','2026-05-27 10:00:00','2026-09-16 10:44:18',88.00,85.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(374,10,13,18,'韩坤','计科2306班',69.00,4,'6',2025,'上课参与度不高','2026-05-27 10:00:00','2026-09-16 10:44:18',75.00,62.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(375,10,14,18,'张博巍','计科2306班',91.00,1,'6',2025,'史学脉络梳理清晰','2026-05-27 10:00:00','2026-09-16 10:44:18',88.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(376,10,15,18,'程启頔','计科2306班',67.00,4,'6',2025,'基础知识掌握不牢','2026-05-27 10:00:00','2026-09-16 10:44:18',65.00,69.00,67.00,'C',1.50,NULL,NULL,NULL,NULL),(377,10,16,18,'邓雨轩','计科2306班',82.00,2,'6',2025,'绘画流派辨识准确','2026-05-27 10:00:00','2026-09-16 10:44:18',79.00,85.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(378,10,17,18,'高菲','计科2306班',89.00,2,'6',2025,'书法史论掌握较好','2026-05-27 10:00:00','2026-09-16 10:44:18',90.00,87.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(379,10,18,18,'胡钰','计科2306班',77.00,3,'6',2025,'史料阅读需加强','2026-05-27 10:00:00','2026-09-16 10:44:18',79.00,75.00,77.00,'B',2.70,NULL,NULL,NULL,NULL),(380,10,19,18,'李佳芮','计科2306班',90.00,1,'6',2025,'诗词赏析功底扎实','2026-05-27 10:00:00','2026-09-16 10:44:18',90.00,89.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(381,10,20,18,'李爽','计科2306班',65.00,4,'6',2025,'作业完成质量偏低','2026-05-27 10:00:00','2026-09-16 10:44:18',69.00,61.00,65.00,'C',1.50,NULL,NULL,NULL,NULL),(382,10,21,18,'梁莹静','计科2306班',85.00,2,'6',2025,'陶瓷工艺知识丰富','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,85.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(383,10,22,18,'孟阳','计科2306班',71.00,3,'6',2025,'背诵积累不足','2026-05-27 10:00:00','2026-09-16 10:44:18',70.00,71.00,71.00,'B',2.00,NULL,NULL,NULL,NULL),(384,10,23,18,'王瑾泰','计科2306班',79.00,3,'6',2025,'文化自信主题完成认真','2026-05-27 10:00:00','2026-09-16 10:44:18',78.00,79.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(385,10,24,29,'王平洋','计科2306班',70.00,3,'6',2025,'观点表达待组织','2026-05-27 10:00:00','2026-09-16 10:44:18',64.00,75.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(386,10,25,29,'夏志浩','计科2306班',93.00,1,'6',2025,'诸子百家思想理解深','2026-05-27 10:00:00','2026-09-16 10:44:18',85.00,100.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(387,10,26,29,'张健','计科2306班',62.00,4,'6',2025,'多处知识漏洞','2026-05-27 10:00:00','2026-09-16 10:44:18',56.00,68.00,62.00,'C',1.00,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `bak_fix10_score_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_communication_logs`
--

DROP TABLE IF EXISTS `bak_fix11_communication_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_communication_logs` (
  `id` bigint NOT NULL DEFAULT '0',
  `counselor_id` bigint DEFAULT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `student_id` bigint DEFAULT NULL,
  `warning_id` bigint DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '0',
  `reply` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_communication_logs`
--

LOCK TABLES `bak_fix11_communication_logs` WRITE;
/*!40000 ALTER TABLE `bak_fix11_communication_logs` DISABLE KEYS */;
INSERT INTO `bak_fix11_communication_logs` VALUES (2001,53,NULL,3,301,'就本学期操作系统(40分)、计算机基础(47分)两门挂科课程约谈司明, 了解其学习困难集中在程序设计基础薄弱与上机练习不足, 已建议参加操作系统学习小组并预约每周两次辅导答疑。','预警约谈',1,'学生表示已加入学习小组, 承诺每周完成课后上机练习并在两周内提交复习计划。','2026-08-29 02:30:00','2026-08-29 02:30:00'),(2002,53,NULL,3,302,'跟踪帮扶计划(计划1001)执行情况: 司明已参加操作系统学习小组第2次辅导, 上机作业提交正常; 继续观察大学英语、高等数学复习进度。','帮扶跟进',0,NULL,'2026-09-01 07:00:00','2026-09-01 07:00:00'),(2003,53,NULL,3,NULL,'期中学习状态沟通: 反馈近期因课程冲突缺席两次小组辅导, 已协调补课时间, 提醒关注补考报名安排。','日常关怀',1,'已确认补课时间并完成补考报名, 学习状态趋于稳定。','2026-09-02 01:00:00','2026-09-02 01:00:00');
/*!40000 ALTER TABLE `bak_fix11_communication_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_communication_record`
--

DROP TABLE IF EXISTS `bak_fix11_communication_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_communication_record` (
  `id` bigint NOT NULL DEFAULT '0',
  `teacher_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_communication_record`
--

LOCK TABLES `bak_fix11_communication_record` WRITE;
/*!40000 ALTER TABLE `bak_fix11_communication_record` DISABLE KEYS */;
INSERT INTO `bak_fix11_communication_record` VALUES (1,29,39,'关于数据结构课程成绩不理想，与学生沟通了学习方法，建议多做练习','成绩沟通','韩坤','2026-05-27 10:00:00'),(2,29,46,'学生三门课程不及格，与辅导员司明联合约谈，制定补习计划','预警沟通','李爽','2026-05-27 10:00:00'),(3,29,52,'学生数学基础薄弱导致多科挂科，建议参加课外辅导班','成绩沟通','张健','2026-05-27 10:00:00'),(4,29,13,'数据结构补考准备情况跟进，目前掌握较好','辅导记录','韩坤','2026-05-27 10:00:00'),(5,29,0,'范德萨','notify_counselor','邱琳紫','2026-06-23 16:52:41');
/*!40000 ALTER TABLE `bak_fix11_communication_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_counselor_class_application`
--

DROP TABLE IF EXISTS `bak_fix11_counselor_class_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_counselor_class_application` (
  `id` bigint NOT NULL DEFAULT '0',
  `counselor_id` bigint NOT NULL,
  `class_id` bigint NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `status` int DEFAULT '0' COMMENT '0-待审核, 1-通过, 2-拒绝',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `counselor_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `application_type` int DEFAULT '0' COMMENT '申请类型：0-申请管理，1-解除管理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_counselor_class_application`
--

LOCK TABLES `bak_fix11_counselor_class_application` WRITE;
/*!40000 ALTER TABLE `bak_fix11_counselor_class_application` DISABLE KEYS */;
INSERT INTO `bak_fix11_counselor_class_application` VALUES (1,53,30,'希望负责计算机2101班的日常管理工作',2,'2026-05-18 12:25:12','2026-09-16 09:52:15','计算机2101班','司明',0),(2,53,31,'希望同时管理计算机2102班',2,'2026-05-18 12:25:12','2026-09-16 09:52:15','计算机2102班','司明',0),(5,53,1,'范德萨',1,'2026-06-08 21:47:38','2026-06-08 21:47:38','计科2306班','辅导员',0);
/*!40000 ALTER TABLE `bak_fix11_counselor_class_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_counselor_class_management`
--

DROP TABLE IF EXISTS `bak_fix11_counselor_class_management`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_counselor_class_management` (
  `id` bigint NOT NULL DEFAULT '0',
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '班级名称',
  `status` int DEFAULT '1' COMMENT '状态: 0-解除管理, 1-管理中',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_counselor_class_management`
--

LOCK TABLES `bak_fix11_counselor_class_management` WRITE;
/*!40000 ALTER TABLE `bak_fix11_counselor_class_management` DISABLE KEYS */;
INSERT INTO `bak_fix11_counselor_class_management` VALUES (1,53,1,'计科2306班',1,'2025-12-23 01:28:15','2025-12-23 01:28:15');
/*!40000 ALTER TABLE `bak_fix11_counselor_class_management` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_counselor_profiles`
--

DROP TABLE IF EXISTS `bak_fix11_counselor_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_counselor_profiles` (
  `id` bigint NOT NULL DEFAULT '0',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `college_id` bigint DEFAULT NULL COMMENT '学院ID',
  `counselor_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '辅导员编号',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名',
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部门',
  `title` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职称',
  `education` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学历',
  `introduction` text COLLATE utf8mb4_unicode_ci COMMENT '个人简介',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `college_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学院名称',
  `staff_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '员工编号',
  `office_location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公地点',
  `duty_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '值班电话',
  `class_count` int DEFAULT '0' COMMENT '管理班级数',
  `student_count` int DEFAULT '0' COMMENT '管理学生数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_counselor_profiles`
--

LOCK TABLES `bak_fix11_counselor_profiles` WRITE;
/*!40000 ALTER TABLE `bak_fix11_counselor_profiles` DISABLE KEYS */;
INSERT INTO `bak_fix11_counselor_profiles` VALUES (1,53,1,'C53','司明',NULL,NULL,NULL,'学生工作办公室',NULL,NULL,NULL,1,'2026-05-27 13:34:10','2026-09-16 11:07:32','计算机学院','1000000001',NULL,NULL,8,24);
/*!40000 ALTER TABLE `bak_fix11_counselor_profiles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_feedback`
--

DROP TABLE IF EXISTS `bak_fix11_feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_feedback` (
  `id` bigint NOT NULL DEFAULT '0',
  `teacher_id` bigint NOT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `reply` text COLLATE utf8mb4_unicode_ci,
  `status` int DEFAULT '0' COMMENT '0-未回复, 1-已回复',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_feedback`
--

LOCK TABLES `bak_fix11_feedback` WRITE;
/*!40000 ALTER TABLE `bak_fix11_feedback` DISABLE KEYS */;
INSERT INTO `bak_fix11_feedback` VALUES (1,29,'教学建议','司明：高等数学课程进度有点快，希望老师能放慢一些',NULL,0,'2026-06-25 09:53:16','2026-06-25 09:53:16'),(2,29,'学习困难','张浩：数据结构的课后作业太难了，能否提供一些参考示例',NULL,0,'2026-06-25 09:53:16','2026-06-25 09:53:16');
/*!40000 ALTER TABLE `bak_fix11_feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_support_resource`
--

DROP TABLE IF EXISTS `bak_fix11_support_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_support_resource` (
  `id` bigint NOT NULL DEFAULT '0',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `author` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `views` int DEFAULT '0',
  `downloads` int DEFAULT '0',
  `status` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_support_resource`
--

LOCK TABLES `bak_fix11_support_resource` WRITE;
/*!40000 ALTER TABLE `bak_fix11_support_resource` DISABLE KEYS */;
INSERT INTO `bak_fix11_support_resource` VALUES (1,'高等数学学习指南','高等数学核心知识点总结与习题讲解，帮助挂科学生系统复习','document','数学','高等数学包含极限、导数、积分、微分方程等核心内容...','https://example.com/math-guide',NULL,NULL,NULL,'于凤海',150,45,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,'Java编程入门教程','针对Java程序设计挂科学生的补习资料，涵盖面向对象、集合、IO流等','video','编程','Java基础语法、面向对象编程、异常处理、多线程...','https://example.com/java-tutorial',NULL,NULL,NULL,'于凤海',200,60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,'学习时间管理方法','帮助学业困难学生改善学习习惯，提高学习效率','article','学习方法','番茄工作法、费曼学习法、康奈尔笔记法等高效学习方法介绍',NULL,NULL,NULL,NULL,'司明',80,30,1,'2026-05-27 02:00:00','2026-09-16 12:52:15'),(4,'线性代数精讲','线性代数重点难点解析，含大量练习题和答案','document','数学','矩阵运算、行列式、向量空间、特征值与特征向量...','https://example.com/linear-algebra',NULL,NULL,NULL,'于凤海',120,38,1,'2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `bak_fix11_support_resource` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_teacher_class_application`
--

DROP TABLE IF EXISTS `bak_fix11_teacher_class_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_teacher_class_application` (
  `id` bigint NOT NULL DEFAULT '0',
  `teacher_id` bigint NOT NULL,
  `class_id` bigint NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `status` int DEFAULT '0' COMMENT '0-待审核, 1-通过, 2-拒绝',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `teacher_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `teacher_username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `application_type` int DEFAULT '0' COMMENT '申请类型：0-申请管理，1-解除管理',
  `reject_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拒绝原因'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_teacher_class_application`
--

LOCK TABLES `bak_fix11_teacher_class_application` WRITE;
/*!40000 ALTER TABLE `bak_fix11_teacher_class_application` DISABLE KEYS */;
INSERT INTO `bak_fix11_teacher_class_application` VALUES (1,29,5,'法撒旦',3,'2026-05-18 12:25:45','2026-05-18 13:48:18','软工2306班','于凤海','10001',0,NULL),(2,29,5,'放大',2,'2026-05-18 13:56:53','2026-05-18 13:57:05','软工2306班','于凤海','10001',0,'fds a'),(3,29,5,'是否都',3,'2026-05-18 14:12:50','2026-05-18 14:13:03','软工2306班','于凤海','10001',0,NULL),(4,29,5,'发的',3,'2026-05-18 14:16:49','2026-05-18 14:16:59','软工2306班','于凤海','10001',0,NULL),(5,29,30,'法国大使',3,'2026-05-18 14:22:57','2026-05-18 14:23:07','计算机2101班','于凤海','10001',0,NULL),(6,29,5,'a发的',3,'2026-05-18 14:24:03','2026-05-18 14:24:22','软工2306班','于凤海','10001',0,NULL),(7,29,5,'安抚的',3,'2026-05-18 14:24:41','2026-05-18 14:24:51','软工2306班','于凤海','10001',0,NULL),(8,29,5,'法撒旦 ',3,'2026-05-18 14:38:20','2026-05-18 14:38:28','软工2306班','于凤海','10001',0,NULL),(9,29,5,'的发生',1,'2026-05-18 15:05:04','2026-05-18 15:07:19','软工2306班','于凤海','10001',0,NULL),(10,29,5,'大师傅',1,'2026-05-18 15:34:01','2026-05-18 15:34:11','软工2306班','于凤海','10001',1,NULL),(11,29,30,'飞洒',1,'2026-05-18 17:11:05','2026-05-18 17:11:18','计算机2101班','于凤海','10001',0,NULL),(12,29,30,'fdas ',1,'2026-05-20 09:48:40','2026-05-20 09:48:54','计算机2101班','于凤海','10001',1,NULL),(13,29,32,'fda ',1,'2026-05-20 09:49:02','2026-05-20 09:49:08','软件工程2101班','于凤海','10001',0,NULL),(14,29,32,'fad ',1,'2026-05-20 09:49:14','2026-05-20 09:58:02','软件工程2101班','于凤海','10001',1,NULL);
/*!40000 ALTER TABLE `bak_fix11_teacher_class_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_teaching_resources`
--

DROP TABLE IF EXISTS `bak_fix11_teaching_resources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_teaching_resources` (
  `id` bigint NOT NULL DEFAULT '0',
  `course_id` bigint DEFAULT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `downloads` int DEFAULT '0',
  `status` int DEFAULT '1',
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_teaching_resources`
--

LOCK TABLES `bak_fix11_teaching_resources` WRITE;
/*!40000 ALTER TABLE `bak_fix11_teaching_resources` DISABLE KEYS */;
INSERT INTO `bak_fix11_teaching_resources` VALUES (1,1,'高等数学-第一至四章课件','高等数学课程配套课件，包含函数极限、导数与微分、不定积分、定积分等章节','pdf','https://example.com/math-slides',NULL,NULL,NULL,320,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(2,1,'高等数学习题集','配套习题集，含答案与解析，共200题覆盖全部章节','pdf','https://example.com/math-exercises',NULL,NULL,NULL,280,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(3,2,'大学英语综合教程','大学英语读写译综合教程，含课文精讲与课后练习','pdf','https://example.com/english-textbook',NULL,NULL,NULL,350,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(4,3,'线性代数-矩阵与行列式','线性代数重点章节精讲，包括矩阵运算、行列式、线性方程组','pdf','https://example.com/linear-algebra',NULL,NULL,NULL,195,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(5,4,'数据结构-算法分析视频','数据结构算法讲解视频系列，包含排序、查找、树、图等核心算法','video','https://example.com/ds-video',NULL,NULL,NULL,410,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(6,5,'Java程序设计-实验指导书','Java课程实验指导书，涵盖面向对象编程、异常处理、集合框架、IO流等实验','pdf','https://example.com/java-lab',NULL,NULL,NULL,365,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(7,5,'Java编程实战项目源码','学生管理系统完整项目源码，适合课程设计参考','zip','https://example.com/java-project',NULL,NULL,NULL,250,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(8,6,'计算机组成原理-实验教程','计组实验教程，包括运算器、存储器、控制器等实验指导','pdf','https://example.com/co-lab',NULL,NULL,NULL,178,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(9,4,'数据结构习题答案','数据结构教材课后习题参考答案，含详细解题过程','pdf','https://example.com/ds-answers',NULL,NULL,NULL,305,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(10,6,'计组期末复习大纲','计算机组成原理期末考试复习提纲，含重点知识梳理与模拟题','doc','https://example.com/co-review',NULL,NULL,NULL,420,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00');
/*!40000 ALTER TABLE `bak_fix11_teaching_resources` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bak_fix11_users`
--

DROP TABLE IF EXISTS `bak_fix11_users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bak_fix11_users` (
  `id` bigint NOT NULL DEFAULT '0',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` tinyint(1) NOT NULL COMMENT '1-学生, 2-教师, 3-辅导员, 4-管理员',
  `status` tinyint(1) DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bak_fix11_users`
--

LOCK TABLES `bak_fix11_users` WRITE;
/*!40000 ALTER TABLE `bak_fix11_users` DISABLE KEYS */;
INSERT INTO `bak_fix11_users` VALUES (1,'admin','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','系统管理员','admin@academic.edu','13800000000',4,1,'2025-12-22 17:28:15','2025-12-22 17:28:15',NULL),(16,'adm001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','司明','admin@test.com的发生','13800000003',4,1,'2025-12-22 17:28:15','2026-06-23 07:44:14',NULL),(18,'teacher001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张老师','teacher001@test.com','13800000010',2,1,'2025-12-22 18:18:22','2026-06-23 08:32:12',NULL),(19,'teacher002','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王老师','teacher002@test.com','13800000011',2,1,'2025-12-29 23:09:40','2025-12-29 23:09:40',NULL),(22,'teacher003','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李老师','teacher003@test.com','13800000012',2,1,'2025-12-29 23:10:00','2025-12-29 23:10:00',NULL),(27,'2023020616','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','司明','2318986782@qq.com','15245508389',1,1,'2026-03-04 02:43:20','2026-06-23 07:47:58',NULL),(28,'teacher005','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','发生的官方','23467675@qq.com','16744343245',2,1,'2026-03-04 03:56:54','2026-06-26 03:29:18',NULL),(29,'10001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','于凤海','13397583736@qq.com','18845894565',2,1,'2026-03-04 03:58:09','2026-06-23 07:47:58',NULL),(30,'2023020615','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','邱琳紫','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(31,'2023020617','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','宋新鹏','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-05-28 02:33:34',NULL),(32,'2022020137','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','赵琦','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(33,'2023020619','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','苏红阳','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(34,'2023020620','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','唐浩然','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(35,'2023020621','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王宏民','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(36,'2023020624','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王玉蛟','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(37,'2023020625','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','吴雨航','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(38,'2023020626','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','徐瑞瑶','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(39,'2023020607','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','韩坤','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(40,'2023020629','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张博巍','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(41,'2023020504','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','程启頔','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-05-28 02:33:34',NULL),(42,'2023020505','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','邓雨轩','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-05-28 02:33:34',NULL),(43,'2023020506','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','高菲','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-05-28 02:33:34',NULL),(44,'2023020507','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','胡钰','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(45,'2023020509','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李佳芮','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(46,'2023020510','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李爽','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(47,'2023020512','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','梁莹静','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(48,'2023020515','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','孟阳','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(49,'2023020519','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王瑾泰','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(50,'2023020520','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王平洋','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(51,'2023020521','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','夏志浩','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(52,'2023020524','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张健','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(53,'1000000001','$2a$10$Zt/haHt8zaNI0J7N7gx2teHL0gOkqKjKq45YooxvdmFBUhe4tGJLq','司明','2318986782@qq.com','15245508389',3,1,'2026-05-14 02:22:05','2026-06-23 07:47:58',NULL),(55,'teacher006','$2a$10$YN5bO.E.tdnGFT7.tPOvk.kMnDr1pq/6.jBSHDmwJ7S4vi2Nowmbi','发送','adm001','24524224545',2,1,'2026-06-23 08:22:47','2026-06-26 03:29:18',NULL),(56,'collegeadmin2','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','1',NULL,NULL,5,1,'2026-06-26 01:46:01','2026-08-03 06:37:01',NULL),(57,'collegeadmin1','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','2',NULL,NULL,5,1,'2026-06-26 01:47:52','2026-08-03 06:37:01',NULL);
/*!40000 ALTER TABLE `bak_fix11_users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `benchmark_analysis`
--

DROP TABLE IF EXISTS `benchmark_analysis`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `benchmark_analysis` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `class_id` bigint DEFAULT NULL,
  `major_id` bigint DEFAULT NULL,
  `semester` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `student_gpa` decimal(5,3) DEFAULT NULL,
  `class_avg_gpa` decimal(5,3) DEFAULT NULL,
  `major_avg_gpa` decimal(5,3) DEFAULT NULL,
  `class_rank` int DEFAULT NULL,
  `class_total` int DEFAULT NULL,
  `major_rank` int DEFAULT NULL,
  `major_total` int DEFAULT NULL,
  `courses_passed` int DEFAULT '0',
  `courses_failed` int DEFAULT '0',
  `required_credits` decimal(5,2) DEFAULT NULL,
  `credits_on_track` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_benchmark_analysis_student_semester` (`student_id`,`semester`),
  KEY `idx_benchmark_analysis_student_id` (`student_id`),
  KEY `idx_benchmark_analysis_class_id` (`class_id`),
  KEY `idx_benchmark_analysis_major_id` (`major_id`),
  CONSTRAINT `fk_benchmark_analysis_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `benchmark_analysis`
--

LOCK TABLES `benchmark_analysis` WRITE;
/*!40000 ALTER TABLE `benchmark_analysis` DISABLE KEYS */;
/*!40000 ALTER TABLE `benchmark_analysis` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `benchmark_analysis_bak_20260916`
--

DROP TABLE IF EXISTS `benchmark_analysis_bak_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `benchmark_analysis_bak_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `student_id` bigint NOT NULL,
  `class_id` bigint DEFAULT NULL,
  `major_id` bigint DEFAULT NULL,
  `semester` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `student_gpa` decimal(5,3) DEFAULT NULL,
  `class_avg_gpa` decimal(5,3) DEFAULT NULL,
  `major_avg_gpa` decimal(5,3) DEFAULT NULL,
  `class_rank` int DEFAULT NULL,
  `class_total` int DEFAULT NULL,
  `major_rank` int DEFAULT NULL,
  `major_total` int DEFAULT NULL,
  `courses_passed` int DEFAULT '0',
  `courses_failed` int DEFAULT '0',
  `required_credits` decimal(5,2) DEFAULT NULL,
  `credits_on_track` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `benchmark_analysis_bak_20260916`
--

LOCK TABLES `benchmark_analysis_bak_20260916` WRITE;
/*!40000 ALTER TABLE `benchmark_analysis_bak_20260916` DISABLE KEYS */;
INSERT INTO `benchmark_analysis_bak_20260916` VALUES (1,4,1,1,'2025-2026-2',3.800,3.050,3.100,3,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,6,1,1,'2025-2026-2',3.200,3.050,3.100,10,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,7,1,1,'2025-2026-2',3.350,3.050,3.100,8,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,8,1,1,'2025-2026-2',3.350,3.050,3.100,9,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(5,9,1,1,'2025-2026-2',2.500,3.050,3.100,18,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(6,10,1,1,'2025-2026-2',3.250,3.050,3.100,11,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(7,11,1,1,'2025-2026-2',2.450,3.050,3.100,19,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(8,12,1,1,'2025-2026-2',3.420,3.050,3.100,7,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(9,13,1,1,'2025-2026-2',2.000,3.050,3.100,22,24,NULL,NULL,4,2,20.00,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(10,14,1,1,'2025-2026-2',3.820,3.050,3.100,2,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(24,5,1,1,'2025-2026-2',3.700,3.050,3.100,5,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(25,15,1,1,'2025-2026-2',2.350,3.050,3.100,20,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(26,16,1,1,'2025-2026-2',3.250,3.050,3.100,12,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(27,17,1,1,'2025-2026-2',3.400,3.050,3.100,8,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(28,18,1,1,'2025-2026-2',2.500,3.050,3.100,17,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(29,19,1,1,'2025-2026-2',3.830,3.050,3.100,1,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(30,20,1,1,'2025-2026-2',1.850,3.050,3.100,23,24,NULL,NULL,3,3,20.00,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(31,21,1,1,'2025-2026-2',3.350,3.050,3.100,9,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(32,22,1,1,'2025-2026-2',2.650,3.050,3.100,15,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(33,23,1,1,'2025-2026-2',3.100,3.050,3.100,13,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(34,24,1,1,'2025-2026-2',2.500,3.050,3.100,16,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(35,25,1,1,'2025-2026-2',3.680,3.050,3.100,6,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(36,26,1,1,'2025-2026-2',1.700,3.050,3.100,24,24,NULL,NULL,3,3,20.00,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(37,3,1,2,'3',3.430,2.950,2.820,8,45,NULL,NULL,6,0,NULL,1,'2024-06-19 16:00:00','2026-06-04 08:25:03'),(38,3,1,2,'4',1.950,2.900,2.780,38,45,NULL,NULL,2,3,NULL,1,'2025-01-14 16:00:00','2026-06-04 08:25:03'),(39,3,1,2,'5',1.530,2.850,2.750,42,45,NULL,NULL,3,3,NULL,1,'2025-06-19 16:00:00','2026-06-04 08:25:03'),(40,3,1,2,'6',0.750,2.880,2.730,43,45,NULL,NULL,1,3,NULL,1,'2025-12-19 16:00:00','2026-06-04 08:25:03');
/*!40000 ALTER TABLE `benchmark_analysis_bak_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `class_management_request`
--

DROP TABLE IF EXISTS `class_management_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class_management_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL,
  `class_id` bigint NOT NULL,
  `counselor_id` bigint DEFAULT NULL,
  `user_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'pending',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reject_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_management_request_teacher_class` (`teacher_id`,`class_id`),
  KEY `idx_class_management_request_class_id` (`class_id`),
  KEY `idx_class_management_request_status` (`status`),
  CONSTRAINT `fk_class_management_request_class_id` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
  CONSTRAINT `fk_class_management_request_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='班级管理申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `class_management_request`
--

LOCK TABLES `class_management_request` WRITE;
/*!40000 ALTER TABLE `class_management_request` DISABLE KEYS */;
/*!40000 ALTER TABLE `class_management_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `classes`
--

DROP TABLE IF EXISTS `classes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `classes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `college_id` bigint NOT NULL,
  `major_id` bigint DEFAULT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `counselor_id` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_classes_college_id` (`college_id`),
  CONSTRAINT `fk_classes_college_id` FOREIGN KEY (`college_id`) REFERENCES `colleges` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `classes`
--

LOCK TABLES `classes` WRITE;
/*!40000 ALTER TABLE `classes` DISABLE KEYS */;
INSERT INTO `classes` VALUES (1,'计科2306班',1,2,29,53,'2025-12-22 17:28:15','2026-09-16 14:07:32'),(5,'软工2306班',1,1,NULL,53,'2026-03-04 02:43:20','2026-09-16 14:07:32'),(30,'计算机2101班',1,2,29,53,'2026-05-14 10:40:58','2026-09-16 14:07:32'),(31,'计算机2102班',1,2,18,53,'2026-05-14 10:40:58','2026-09-16 14:07:32'),(32,'软件工程2101班',1,1,18,53,'2026-05-14 10:40:58','2026-09-16 14:07:32'),(33,'软件工程2102班',1,1,19,53,'2026-05-14 10:40:58','2026-09-16 14:07:32'),(34,'人工智能2101班',2,8,19,NULL,'2026-05-14 10:40:58','2026-09-29 16:40:08'),(35,'人工智能2102班',2,8,22,NULL,'2026-05-14 10:40:58','2026-09-29 16:40:08');
/*!40000 ALTER TABLE `classes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `college_admin_profile`
--

DROP TABLE IF EXISTS `college_admin_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `college_admin_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '关联 users.id',
  `name` varchar(50) NOT NULL COMMENT '姓名',
  `college_id` bigint NOT NULL COMMENT '所属学院',
  `phone` varchar(20) DEFAULT NULL,
  `email` varchar(100) DEFAULT NULL,
  `title` varchar(50) DEFAULT NULL COMMENT '职称',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_college_admin_user` (`user_id`),
  KEY `idx_college_admin_college` (`college_id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学院管理员档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `college_admin_profile`
--

LOCK TABLES `college_admin_profile` WRITE;
/*!40000 ALTER TABLE `college_admin_profile` DISABLE KEYS */;
INSERT INTO `college_admin_profile` VALUES (1,57,'张建国',1,'13900000001','zhangjg@academic.edu','软件学院院长','2026-06-26 09:46:28','2026-06-26 09:47:52'),(2,56,'李明远',2,'13900000002','limy@academic.edu','电子工程学院院长','2026-06-26 09:46:28','2026-06-26 09:46:28');
/*!40000 ALTER TABLE `college_admin_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `colleges`
--

DROP TABLE IF EXISTS `colleges`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `colleges` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_colleges_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `colleges`
--

LOCK TABLES `colleges` WRITE;
/*!40000 ALTER TABLE `colleges` DISABLE KEYS */;
INSERT INTO `colleges` VALUES (1,'软件学院','SOFT','软件学院',1,'2025-12-22 17:28:15','2026-10-01 06:40:47'),(2,'电子工程学院','EE','电子工程学院',1,'2025-12-22 17:28:15','2026-09-16 14:07:32'),(3,'商学院','BUS','商学院',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(4,'艺术设计学院','ART','艺术设计学院',1,'2025-12-22 17:28:15','2025-12-22 17:28:15');
/*!40000 ALTER TABLE `colleges` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `communication_logs`
--

DROP TABLE IF EXISTS `communication_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `counselor_id` bigint DEFAULT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `student_id` bigint DEFAULT NULL,
  `warning_id` bigint DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '0',
  `reply` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_communication_logs_counselor_id` (`counselor_id`),
  KEY `idx_communication_logs_student_id` (`student_id`),
  KEY `idx_communication_logs_warning_id` (`warning_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2005 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `communication_logs`
--

LOCK TABLES `communication_logs` WRITE;
/*!40000 ALTER TABLE `communication_logs` DISABLE KEYS */;
INSERT INTO `communication_logs` VALUES (2001,53,NULL,3,395,'就操作系统(40分)、计算机基础(47分)、高等数学(56.5分)三门挂科课程约谈司明, 了解其学习困难集中在程序设计基础薄弱与上机练习不足, 已建议参加操作系统学习小组并预约每周两次辅导答疑。','预警约谈',1,'学生表示已加入学习小组, 承诺每周完成课后上机练习并在两周内提交复习计划。','2026-08-29 02:30:00','2026-09-21 09:12:20'),(2002,53,NULL,3,NULL,'跟踪帮扶计划(计划1001)执行情况: 司明已参加操作系统学习小组第2次辅导, 上机作业提交正常; 继续观察大学英语、高等数学复习进度。','帮扶跟进',0,NULL,'2026-09-01 07:00:00','2026-09-21 09:12:20'),(2003,53,NULL,3,NULL,'期中学习状态沟通: 反馈近期因课程冲突缺席两次小组辅导, 已协调补课时间, 提醒关注补考报名安排。','日常关怀',1,'已确认补课时间并完成补考报名, 学习状态趋于稳定。','2026-09-02 01:00:00','2026-09-02 01:00:00'),(2004,53,NULL,3,NULL,'你好','normal',0,NULL,'2026-10-09 02:49:06','2026-10-09 02:49:06');
/*!40000 ALTER TABLE `communication_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `communication_record`
--

DROP TABLE IF EXISTS `communication_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `communication_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='沟通记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `communication_record`
--

LOCK TABLES `communication_record` WRITE;
/*!40000 ALTER TABLE `communication_record` DISABLE KEYS */;
INSERT INTO `communication_record` VALUES (1,29,13,'关于数据结构课程成绩不理想，与学生沟通了学习方法，建议多做练习','成绩沟通','韩坤','2026-05-27 10:00:00'),(2,29,20,'学生三门课程不及格，与辅导员司明联合约谈，制定补习计划','预警沟通','李爽','2026-05-27 10:00:00'),(3,29,26,'学生数学基础薄弱导致多科挂科，建议参加课外辅导班','成绩沟通','张健','2026-05-27 10:00:00'),(4,29,13,'数据结构补考准备情况跟进，目前掌握较好','辅导记录','韩坤','2026-05-27 10:00:00'),(5,29,4,'向辅导员同步邱琳紫同学的学习状态：本学期各科成绩均在 80 分以上，建议纳入学业优秀名单，请辅导员协同关注其后续发展','notify_counselor','邱琳紫','2026-06-23 16:52:41');
/*!40000 ALTER TABLE `communication_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `counselor_class_application`
--

DROP TABLE IF EXISTS `counselor_class_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counselor_class_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `counselor_id` bigint NOT NULL,
  `class_id` bigint NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `status` int DEFAULT '0' COMMENT '0-待审核, 1-通过, 2-拒绝',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `counselor_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `application_type` int DEFAULT '0' COMMENT '申请类型：0-申请管理，1-解除管理',
  `reject_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拒绝原因',
  PRIMARY KEY (`id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_status` (`status`),
  KEY `idx_application_type` (`application_type`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员班级管理申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `counselor_class_application`
--

LOCK TABLES `counselor_class_application` WRITE;
/*!40000 ALTER TABLE `counselor_class_application` DISABLE KEYS */;
INSERT INTO `counselor_class_application` VALUES (1,53,30,'希望负责计算机2101班的日常管理工作',1,'2026-05-18 12:25:12','2026-09-16 11:23:16','计算机2101班','司明',0,NULL),(2,53,31,'希望同时管理计算机2102班',1,'2026-05-18 12:25:12','2026-09-16 11:23:16','计算机2102班','司明',0,NULL),(5,53,1,'申请负责计科2306班的日常管理与学业帮扶工作',1,'2026-06-08 21:47:38','2026-09-16 11:23:16','计科2306班','司明',0,NULL);
/*!40000 ALTER TABLE `counselor_class_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `counselor_class_management`
--

DROP TABLE IF EXISTS `counselor_class_management`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counselor_class_management` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `counselor_id` bigint NOT NULL COMMENT '辅导员ID',
  `class_id` bigint NOT NULL COMMENT '班级ID',
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '班级名称',
  `status` int DEFAULT '1' COMMENT '状态: 0-解除管理, 1-管理中',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_counselor_class` (`counselor_id`,`class_id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员班级管理表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `counselor_class_management`
--

LOCK TABLES `counselor_class_management` WRITE;
/*!40000 ALTER TABLE `counselor_class_management` DISABLE KEYS */;
INSERT INTO `counselor_class_management` VALUES (1,53,1,'计科2306班',1,'2025-12-23 01:28:15','2025-12-23 01:28:15'),(2,53,5,'软工2306班',1,'2026-09-16 11:23:16','2026-09-16 11:23:16'),(3,53,30,'计算机2101班',1,'2026-09-16 11:23:16','2026-09-16 11:23:16'),(4,53,31,'计算机2102班',1,'2026-09-16 11:23:16','2026-09-16 11:23:16'),(5,53,32,'软件工程2101班',1,'2026-09-16 11:23:16','2026-09-16 11:23:16'),(6,53,33,'软件工程2102班',1,'2026-09-16 11:23:16','2026-09-16 11:23:16');
/*!40000 ALTER TABLE `counselor_class_management` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `counselor_profiles`
--

DROP TABLE IF EXISTS `counselor_profiles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `counselor_profiles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `college_id` bigint DEFAULT NULL COMMENT '学院ID',
  `counselor_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '辅导员编号',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名',
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `department` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部门',
  `title` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职称',
  `education` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学历',
  `introduction` text COLLATE utf8mb4_unicode_ci COMMENT '个人简介',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `college_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学院名称',
  `staff_id` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '员工编号',
  `office_location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公地点',
  `duty_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '值班电话',
  `class_count` int DEFAULT '0' COMMENT '管理班级数',
  `student_count` int DEFAULT '0' COMMENT '管理学生数',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_college_id` (`college_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员信息表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `counselor_profiles`
--

LOCK TABLES `counselor_profiles` WRITE;
/*!40000 ALTER TABLE `counselor_profiles` DISABLE KEYS */;
INSERT INTO `counselor_profiles` VALUES (1,53,1,'C53','司明',NULL,NULL,NULL,'学生工作办公室',NULL,NULL,NULL,1,'2026-05-27 13:34:10','2026-09-30 12:38:49','软件学院','1000000001',NULL,NULL,6,24);
/*!40000 ALTER TABLE `counselor_profiles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_plans`
--

DROP TABLE IF EXISTS `course_plans`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `year` int DEFAULT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `class_time` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `max_students` int DEFAULT '50',
  `status` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_plans_course_id` (`course_id`),
  KEY `idx_course_plans_semester` (`semester`),
  CONSTRAINT `fk_course_plans_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_plans`
--

LOCK TABLES `course_plans` WRITE;
/*!40000 ALTER TABLE `course_plans` DISABLE KEYS */;
INSERT INTO `course_plans` VALUES (1,1,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周一 1-2节','教学楼A301',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,2,19,'2025-2026-2',2026,'2026-02-24','2026-06-30','周一 3-4节','教学楼A302',60,1,'2026-05-27 02:00:00','2026-09-16 14:07:32'),(3,3,22,'2025-2026-2',2026,'2026-02-24','2026-06-30','周二 1-2节','教学楼A303',60,1,'2026-05-27 02:00:00','2026-09-16 14:07:32'),(4,4,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周二 3-4节','实训楼B101',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(5,5,22,'2025-2026-2',2026,'2026-02-24','2026-06-30','周三 1-2节','实训楼B102',60,1,'2026-05-27 02:00:00','2026-09-16 14:07:32'),(6,6,18,'2025-2026-2',2026,'2026-02-24','2026-06-30','周三 3-4节','教学楼A304',60,1,'2026-05-27 02:00:00','2026-09-16 14:07:32'),(7,7,28,'2025-2026-2',2026,'2026-02-24','2026-06-30','周四 5-6节','教学楼A401',80,1,'2026-05-27 02:00:00','2026-09-16 14:07:32'),(8,8,28,'2025-2026-2',2026,'2026-02-24','2026-06-30','周四 7-8节','教学楼A402',80,1,'2026-05-27 02:00:00','2026-09-16 14:07:32'),(9,9,28,'2025-2026-2',2026,'2026-02-24','2026-06-30','周五 5-6节','艺术楼C201',50,1,'2026-05-27 02:00:00','2026-09-16 14:07:32'),(10,10,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周五 7-8节','教学楼A403',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `course_plans` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_recommendation`
--

DROP TABLE IF EXISTS `course_recommendation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_recommendation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL COMMENT '教师的 users.id（与 courses.teacher_id / classes.teacher_id 同一套）',
  `course_id` bigint NOT NULL,
  `target_class_id` bigint DEFAULT NULL COMMENT 'NULL = 该教师名下所有班级',
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` int NOT NULL DEFAULT '1' COMMENT '1=有效, 0=已撤回',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_rec_teacher` (`teacher_id`),
  KEY `idx_course_rec_course` (`course_id`),
  KEY `idx_course_rec_class` (`target_class_id`),
  CONSTRAINT `fk_course_recommendation_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_recommendation`
--

LOCK TABLES `course_recommendation` WRITE;
/*!40000 ALTER TABLE `course_recommendation` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_recommendation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `courses`
--

DROP TABLE IF EXISTS `courses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `credits` decimal(5,2) NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `module_id` bigint DEFAULT NULL,
  `score_template` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `teacher_id` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_courses_code` (`code`),
  KEY `idx_courses_code` (`code`),
  KEY `idx_courses_module_id` (`module_id`),
  CONSTRAINT `fk_courses_module_id` FOREIGN KEY (`module_id`) REFERENCES `elective_modules` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `courses`
--

LOCK TABLES `courses` WRITE;
/*!40000 ALTER TABLE `courses` DISABLE KEYS */;
INSERT INTO `courses` VALUES (1,'MATH001','高等数学',4.00,'必修课','高等数学基础课程',NULL,NULL,29,'2026-05-27 02:00:00','2026-09-16 04:43:52'),(2,'CS102','大学英语',3.00,'必修课',NULL,NULL,NULL,19,'2026-05-24 08:13:53','2026-09-16 14:07:32'),(3,'CS103','计算机基础',3.00,'必修课',NULL,NULL,NULL,22,'2026-05-24 08:13:53','2026-09-16 14:07:32'),(4,'CS201','数据结构',4.00,'必修课',NULL,NULL,NULL,29,'2026-05-24 08:13:53','2026-09-16 14:07:32'),(5,'CS202','计算机网络',3.00,'必修课',NULL,NULL,NULL,22,'2026-05-24 08:13:53','2026-09-16 14:07:32'),(6,'CS203','操作系统',4.00,'必修课',NULL,NULL,NULL,18,'2026-05-24 08:13:53','2026-09-16 14:07:32'),(7,'CS301','人工智能导论',3.00,'选修课',NULL,3,NULL,28,'2026-05-24 08:13:53','2026-09-16 14:07:32'),(8,'CS302','机器学习',4.00,'选修课',NULL,3,NULL,28,'2026-05-24 08:13:53','2026-09-16 14:07:32'),(9,'CS303','深度学习',3.00,'选修课',NULL,3,NULL,28,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(10,'CULT001','中国传统文化',1.00,'选修课','中国传统文化概论',4,NULL,29,'2026-05-27 02:00:00','2026-09-16 12:43:52');
/*!40000 ALTER TABLE `courses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `elective_modules`
--

DROP TABLE IF EXISTS `elective_modules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `elective_modules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `module_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `module_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_elective_modules_module_code` (`module_code`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选修课模块表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `elective_modules`
--

LOCK TABLES `elective_modules` WRITE;
/*!40000 ALTER TABLE `elective_modules` DISABLE KEYS */;
INSERT INTO `elective_modules` VALUES (1,'HUM','Humanities and Information Literacy Module','Human science and information literacy education courses','2025-12-14 00:12:25','2025-12-14 00:12:25'),(2,'ART','Art and Aesthetics Module','Art appreciation and aesthetic education courses','2025-12-14 00:12:25','2025-12-14 00:12:25'),(3,'SCI','Natural Science and Information Technology Module','Science and technology courses','2025-12-14 00:12:25','2025-12-14 00:12:25'),(4,'SOC','Social Science Module','Social science and humanities courses','2025-12-14 00:12:25','2025-12-14 00:12:25'),(5,'LANG','Language Extension Module','Foreign language and communication courses','2025-12-14 00:12:25','2025-12-14 00:12:25');
/*!40000 ALTER TABLE `elective_modules` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `enrollments`
--

DROP TABLE IF EXISTS `enrollments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `enrollments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `class_id` bigint DEFAULT NULL,
  `status` int DEFAULT '0',
  `semester` int DEFAULT NULL,
  `year` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_enrollments_student_id` (`student_id`),
  KEY `idx_enrollments_course_id` (`course_id`),
  KEY `fk_enrollments_class_id` (`class_id`),
  CONSTRAINT `fk_enrollments_class_id` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
  CONSTRAINT `fk_enrollments_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `fk_enrollments_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=145 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `enrollments`
--

LOCK TABLES `enrollments` WRITE;
/*!40000 ALTER TABLE `enrollments` DISABLE KEYS */;
INSERT INTO `enrollments` VALUES (1,3,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,3,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,3,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,3,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(5,3,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(6,3,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(7,4,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(8,4,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(9,4,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(10,4,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(11,4,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(12,4,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(13,5,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(14,5,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(15,5,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(16,5,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(17,5,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(18,5,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(19,6,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(20,6,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(21,6,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(22,6,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(23,6,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(24,6,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(25,7,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(26,7,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(27,7,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(28,7,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(29,7,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(30,7,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(31,8,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(32,8,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(33,8,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(34,8,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(35,8,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(36,8,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(37,9,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(38,9,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(39,9,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(40,9,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(41,9,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(42,9,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(43,10,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(44,10,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(45,10,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(46,10,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(47,10,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(48,10,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(49,11,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(50,11,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(51,11,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(52,11,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(53,11,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(54,11,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(55,12,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(56,12,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(57,12,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(58,12,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(59,12,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(60,12,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(61,13,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(62,13,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(63,13,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(64,13,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(65,13,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(66,13,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(67,14,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(68,14,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(69,14,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(70,14,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(71,14,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(72,14,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(73,15,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(74,15,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(75,15,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(76,15,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(77,15,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(78,15,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(79,16,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(80,16,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(81,16,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(82,16,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(83,16,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(84,16,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(85,17,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(86,17,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(87,17,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(88,17,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(89,17,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(90,17,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(91,18,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(92,18,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(93,18,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(94,18,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(95,18,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(96,18,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(97,19,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(98,19,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(99,19,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(100,19,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(101,19,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(102,19,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(103,20,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(104,20,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(105,20,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(106,20,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(107,20,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(108,20,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(109,21,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(110,21,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(111,21,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(112,21,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(113,21,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(114,21,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(115,22,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(116,22,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(117,22,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(118,22,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(119,22,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(120,22,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(121,23,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(122,23,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(123,23,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(124,23,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(125,23,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(126,23,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(127,24,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(128,24,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(129,24,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(130,24,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(131,24,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(132,24,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(133,25,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(134,25,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(135,25,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(136,25,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(137,25,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(138,25,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(139,26,1,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(140,26,2,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(141,26,3,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(142,26,4,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(143,26,5,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(144,26,6,1,1,2,2025,'2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `enrollments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `export_history`
--

DROP TABLE IF EXISTS `export_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `export_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `data_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `record_count` int DEFAULT '0',
  `exported_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_export_history_data_type` (`data_type`),
  KEY `idx_export_history_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `export_history`
--

LOCK TABLES `export_history` WRITE;
/*!40000 ALTER TABLE `export_history` DISABLE KEYS */;
INSERT INTO `export_history` VALUES (3,'colleges','colleges_20260521_192926.xlsx',4,NULL,'2026-05-21 11:29:26'),(4,'colleges','colleges_20260521_192926.xlsx',4,NULL,'2026-05-21 11:29:26'),(5,'colleges','colleges_20260521_192926.xlsx',4,NULL,'2026-05-21 11:29:26'),(6,'colleges','colleges_20260521_192933.xlsx',4,NULL,'2026-05-21 11:29:34'),(7,'colleges','colleges_20260521_193500.xlsx',4,NULL,'2026-05-21 11:35:01'),(8,'students','students_20260525_130905.xlsx',11,NULL,'2026-05-25 05:09:09'),(9,'colleges','colleges_20260525_131816.xlsx',4,NULL,'2026-05-25 05:18:17'),(10,'colleges','colleges_20260525_132013.xlsx',4,NULL,'2026-05-25 05:20:13'),(11,'colleges','colleges_20260525_132222.xlsx',4,NULL,'2026-05-25 05:22:23'),(12,'colleges','colleges_20260525_133342.xlsx',4,NULL,'2026-05-25 05:33:42'),(13,'colleges','colleges_20260525_134035.xlsx',4,NULL,'2026-05-25 05:40:36'),(14,'students','students_20260527_093355.xlsx',11,NULL,'2026-05-27 01:33:57'),(15,'students','students_20260527_094225.xlsx',11,NULL,'2026-05-27 01:42:26'),(16,'scores','scores_20260527_094256.xlsx',0,NULL,'2026-05-27 01:42:57'),(17,'warnings','warnings_20260527_094258.xlsx',0,NULL,'2026-05-27 01:43:01'),(18,'students','students_20260527_094352.xlsx',11,NULL,'2026-05-27 01:43:52'),(19,'students','students_20260527_094804.xlsx',11,NULL,'2026-05-27 01:48:04'),(20,'students','students_20260527_095252.xlsx',11,NULL,'2026-05-27 01:52:52'),(21,'students','students_20260527_095259.xlsx',11,NULL,'2026-05-27 01:52:59'),(22,'colleges','colleges_20260527_103947.xlsx',4,NULL,'2026-05-27 02:39:48'),(23,'students','students_20260527_104004.xlsx',11,NULL,'2026-05-27 02:40:04'),(24,'warnings','warnings_20260527_104009.xlsx',0,NULL,'2026-05-27 02:40:17'),(25,'warnings','warnings_20260527_104013.xlsx',0,NULL,'2026-05-27 02:40:17'),(26,'users','users_20260527_104417.xlsx',24,NULL,'2026-05-27 02:44:17'),(27,'colleges','colleges_20261009_103026.xlsx',4,NULL,'2026-10-09 02:30:28'),(28,'colleges','colleges_20261009_103031.xlsx',4,NULL,'2026-10-09 02:30:32'),(29,'insufficient-students','insufficient_students_20261009_104615.xlsx',4,NULL,'2026-10-09 02:46:16');
/*!40000 ALTER TABLE `export_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `fail_records`
--

DROP TABLE IF EXISTS `fail_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fail_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `course_id` bigint DEFAULT NULL COMMENT '课程ID',
  `course_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程名称',
  `exam_time` datetime DEFAULT NULL COMMENT '考试时间',
  `score` decimal(5,2) DEFAULT NULL COMMENT '成绩',
  `fail_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '挂科类型：fail-不及格, absent-缺考, cheat-作弊',
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学期',
  `academic_year` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学年',
  `status` int DEFAULT '0' COMMENT '状态：0-挂科, 1-补考通过, 2-重修通过',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='挂科记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fail_records`
--

LOCK TABLES `fail_records` WRITE;
/*!40000 ALTER TABLE `fail_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `fail_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `fail_records_bak_20260916`
--

DROP TABLE IF EXISTS `fail_records_bak_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fail_records_bak_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `course_id` bigint DEFAULT NULL COMMENT '课程ID',
  `course_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程名称',
  `exam_time` datetime DEFAULT NULL COMMENT '考试时间',
  `score` decimal(5,2) DEFAULT NULL COMMENT '成绩',
  `fail_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '挂科类型：fail-不及格, absent-缺考, cheat-作弊',
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学期',
  `academic_year` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学年',
  `status` int DEFAULT '0' COMMENT '状态：0-挂科, 1-补考通过, 2-重修通过',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fail_records_bak_20260916`
--

LOCK TABLES `fail_records_bak_20260916` WRITE;
/*!40000 ALTER TABLE `fail_records_bak_20260916` DISABLE KEYS */;
INSERT INTO `fail_records_bak_20260916` VALUES (1,39,4,'数据结构','2026-06-20 09:00:00',65.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(2,39,6,'计算机组成原理','2026-06-22 09:00:00',71.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,46,1,'高等数学','2026-06-18 09:00:00',66.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,46,3,'线性代数','2026-06-20 09:00:00',69.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,46,5,'Java程序设计','2026-06-23 09:00:00',68.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,52,1,'高等数学','2026-06-18 09:00:00',64.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,52,3,'线性代数','2026-06-20 09:00:00',62.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,52,5,'Java程序设计','2026-06-23 09:00:00',65.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00');
/*!40000 ALTER TABLE `fail_records_bak_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feedback`
--

DROP TABLE IF EXISTS `feedback`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feedback` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `reply` text COLLATE utf8mb4_unicode_ci,
  `status` int DEFAULT '0' COMMENT '0-未回复, 1-已回复',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedback`
--

LOCK TABLES `feedback` WRITE;
/*!40000 ALTER TABLE `feedback` DISABLE KEYS */;
INSERT INTO `feedback` VALUES (1,29,'教学建议','司明：高等数学课程进度有点快，希望老师能放慢一些',NULL,0,'2026-06-25 09:53:16','2026-06-25 09:53:16'),(2,29,'学习困难','韩坤：数据结构的课后作业太难了，能否提供一些参考示例',NULL,0,'2026-06-25 09:53:16','2026-09-16 11:23:16');
/*!40000 ALTER TABLE `feedback` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `feedbacks`
--

DROP TABLE IF EXISTS `feedbacks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `feedbacks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rating` int DEFAULT NULL,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reply_content` text COLLATE utf8mb4_unicode_ci,
  `replied_at` timestamp NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feedbacks_teacher_id` (`teacher_id`),
  KEY `idx_feedbacks_student_id` (`student_id`),
  CONSTRAINT `fk_feedbacks_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`),
  CONSTRAINT `fk_feedbacks_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedbacks`
--

LOCK TABLES `feedbacks` WRITE;
/*!40000 ALTER TABLE `feedbacks` DISABLE KEYS */;
/*!40000 ALTER TABLE `feedbacks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `login_logs`
--

DROP TABLE IF EXISTS `login_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `login_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `login_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `ip_address` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'success',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB AUTO_INCREMENT=359 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `login_logs`
--

LOCK TABLES `login_logs` WRITE;
/*!40000 ALTER TABLE `login_logs` DISABLE KEYS */;
INSERT INTO `login_logs` VALUES (1,16,'adm001','2026-06-24 00:51:11','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(2,29,'10001','2026-06-24 00:58:05','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(3,29,'10001','2026-06-24 01:17:15','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(4,29,'10001','2026-06-24 01:25:06','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(5,29,'10001','2026-06-24 01:50:46','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(6,29,'10001','2026-06-24 05:37:19','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(7,29,'10001','2026-06-25 00:31:23','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(8,53,'1000000001','2026-06-25 03:01:55','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(9,16,'adm001','2026-06-25 03:33:03','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(10,16,'adm001','2026-06-25 06:34:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(11,16,'adm001','2026-06-25 06:36:15','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(12,53,'1000000001','2026-06-25 06:36:39','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(13,29,'10001','2026-06-25 06:37:02','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(14,29,'10001','2026-06-26 00:27:33','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(15,53,'1000000001','2026-06-26 00:44:13','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(16,16,'adm001','2026-06-26 00:48:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(17,27,'2023020616','2026-06-26 00:55:22','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(18,16,'adm001','2026-06-26 01:03:23','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(19,27,'2023020616','2026-06-26 01:23:57','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(20,53,'1000000001','2026-06-26 01:24:16','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(21,57,'collegeadmin1','2026-06-26 01:54:08','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(22,57,'collegeadmin1','2026-06-26 02:03:49','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(23,57,'collegeadmin1','2026-06-26 02:51:48','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(24,57,'collegeadmin1','2026-06-27 00:33:56','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(25,57,'collegeadmin1','2026-06-27 00:54:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(26,27,'2023020616','2026-06-27 01:23:47','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(27,57,'collegeadmin1','2026-06-27 01:24:08','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(28,16,'adm001','2026-06-27 01:25:05','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(29,57,'collegeadmin1','2026-06-27 01:25:17','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(30,16,'adm001','2026-06-27 01:34:49','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(31,57,'collegeadmin1','2026-06-27 01:39:22','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(32,29,'10001','2026-06-27 02:08:48','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(33,29,'10001','2026-06-29 02:05:30','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(34,57,'collegeadmin1','2026-06-29 07:06:57','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(35,27,'2023020616','2026-06-29 07:29:14','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(36,29,'10001','2026-06-29 07:29:50','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(37,53,'1000000001','2026-06-29 07:30:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(38,57,'collegeadmin1','2026-06-29 07:31:18','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(39,16,'adm001','2026-06-29 07:32:06','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(40,53,'1000000001','2026-06-29 07:33:29','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(41,57,'collegeadmin1','2026-06-29 07:34:41','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(42,27,'2023020616','2026-10-02 03:26:52','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(43,27,'2023020616','2026-10-02 03:26:53','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'failed'),(44,27,'2023020616','2026-10-02 03:27:25','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(45,27,'2023020616','2026-10-02 05:39:14','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(46,27,'2023020616','2026-10-02 05:39:28','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(47,27,'2023020616','2026-10-02 05:39:28','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'failed'),(48,27,'2023020616','2026-10-02 05:39:28','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(49,27,'2023020616','2026-10-02 05:39:44','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(50,27,'2023020616','2026-10-02 05:39:44','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'failed'),(51,27,'2023020616','2026-10-02 05:39:44','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(52,27,'2023020616','2026-10-02 05:47:38','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(53,27,'2023020616','2026-10-02 05:47:38','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'failed'),(54,27,'2023020616','2026-10-02 05:47:38','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(55,27,'2023020616','2026-10-02 05:51:22','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(56,57,'collegeadmin1','2026-10-02 05:56:38','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(57,56,'collegeadmin2','2026-10-02 05:56:39','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(58,57,'collegeadmin1','2026-10-02 06:13:00','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(59,56,'collegeadmin2','2026-10-02 06:13:01','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(60,27,'2023020616','2026-10-02 06:13:02','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(61,56,'collegeadmin2','2026-10-02 06:13:35','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(62,27,'2023020616','2026-10-02 06:13:36','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(63,57,'collegeadmin1','2026-10-02 06:15:09','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(64,56,'collegeadmin2','2026-10-02 06:15:10','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(65,27,'2023020616','2026-10-02 06:15:11','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(66,57,'collegeadmin1','2026-10-02 06:22:32','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(67,56,'collegeadmin2','2026-10-02 06:22:33','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(68,27,'2023020616','2026-10-02 06:22:34','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(69,57,'collegeadmin1','2026-10-02 06:25:06','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(70,56,'collegeadmin2','2026-10-02 06:25:07','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(71,27,'2023020616','2026-10-02 06:25:08','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(72,57,'collegeadmin1','2026-10-02 06:28:45','0:0:0:0:0:0:0:1','node',NULL,'success'),(73,56,'collegeadmin2','2026-10-02 06:28:46','0:0:0:0:0:0:0:1','node',NULL,'success'),(74,57,'collegeadmin1','2026-10-02 06:28:57','0:0:0:0:0:0:0:1','node',NULL,'success'),(75,56,'collegeadmin2','2026-10-02 06:28:57','0:0:0:0:0:0:0:1','node',NULL,'success'),(76,57,'collegeadmin1','2026-10-02 06:30:39','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(77,1,'admin','2026-10-02 07:21:10','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(78,1,'admin','2026-10-02 07:21:39','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(79,1,'admin','2026-10-02 07:22:34','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(80,1,'admin','2026-10-02 11:00:08','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(81,27,'2023020616','2026-10-02 11:02:18','0:0:0:0:0:0:0:1','node',NULL,'success'),(82,29,'10001','2026-10-02 11:02:18','0:0:0:0:0:0:0:1','node',NULL,'success'),(83,57,'collegeadmin1','2026-10-02 11:02:18','0:0:0:0:0:0:0:1','node',NULL,'success'),(84,1,'admin','2026-10-02 11:02:18','0:0:0:0:0:0:0:1','node',NULL,'success'),(85,27,'2023020616','2026-10-02 11:03:39','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(86,29,'10001','2026-10-02 11:03:41','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(87,57,'collegeadmin1','2026-10-02 11:03:43','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(88,27,'2023020616','2026-10-02 11:04:59','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(89,29,'10001','2026-10-02 11:05:06','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(90,1,'admin','2026-10-02 11:05:17','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(91,57,'collegeadmin1','2026-10-02 11:05:23','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(92,29,'10001','2026-10-02 11:06:00','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(93,27,'2023020616','2026-10-02 11:06:21','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(94,29,'10001','2026-10-02 11:06:27','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(95,1,'admin','2026-10-02 11:06:33','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(96,57,'collegeadmin1','2026-10-02 11:06:40','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(97,18,'teacher001','2026-10-03 11:54:47','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(98,18,'teacher001','2026-10-03 11:55:35','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(99,18,'teacher001','2026-10-03 11:56:02','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(100,18,'teacher001','2026-10-03 11:56:25','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(101,18,'teacher001','2026-10-03 11:56:48','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(102,18,'teacher001','2026-10-03 11:57:17','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(103,27,'2023020616','2026-10-03 12:10:59','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(104,27,'2023020616','2026-10-03 12:11:32','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(105,29,'10001','2026-10-03 12:11:33','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(106,53,'1000000001','2026-10-03 12:11:33','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(107,57,'collegeadmin1','2026-10-03 12:11:34','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(108,1,'admin','2026-10-03 12:11:34','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(109,27,'2023020616','2026-10-03 12:11:54','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(110,27,'2023020616','2026-10-03 12:12:02','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(111,53,'1000000001','2026-10-03 12:12:02','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(112,29,'10001','2026-10-03 12:12:02','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(113,57,'collegeadmin1','2026-10-03 12:12:03','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(114,27,'2023020616','2026-10-03 12:13:24','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(115,53,'1000000001','2026-10-03 12:13:24','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(116,29,'10001','2026-10-03 12:13:24','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(117,57,'collegeadmin1','2026-10-03 12:13:24','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(118,27,'2023020616','2026-10-03 12:13:31','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(119,53,'1000000001','2026-10-03 12:13:32','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(120,29,'10001','2026-10-03 12:13:32','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(121,57,'collegeadmin1','2026-10-03 12:13:32','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(122,27,'2023020616','2026-10-03 12:14:27','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(123,27,'2023020616','2026-10-03 12:15:22','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(124,18,'teacher001','2026-10-03 12:15:53','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(125,53,'1000000001','2026-10-03 12:16:28','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(126,1,'admin','2026-10-03 12:17:18','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(127,57,'collegeadmin1','2026-10-03 12:17:57','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(128,18,'teacher001','2026-10-03 12:18:32','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(129,53,'1000000001','2026-10-03 12:18:44','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(130,27,'2023020616','2026-10-03 12:18:59','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(131,27,'2023020616','2026-10-03 12:19:06','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(132,27,'2023020616','2026-10-03 12:19:06','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(133,27,'2023020616','2026-10-03 12:21:24','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(134,27,'2023020616','2026-10-03 12:21:33','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(135,18,'teacher001','2026-10-03 12:22:08','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(136,27,'2023020616','2026-10-03 12:23:05','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(137,27,'2023020616','2026-10-03 12:23:29','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(138,27,'2023020616','2026-10-03 12:23:43','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(139,53,'1000000001','2026-10-03 12:23:43','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(140,29,'10001','2026-10-03 12:23:43','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(141,57,'collegeadmin1','2026-10-03 12:23:43','0:0:0:0:0:0:0:1','Python-urllib/3.12',NULL,'success'),(142,27,'2023020616','2026-10-03 12:50:44','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(143,27,'2023020616','2026-10-03 12:51:37','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(144,27,'2023020616','2026-10-03 12:51:59','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(145,27,'2023020616','2026-10-03 12:52:18','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(146,27,'2023020616','2026-10-03 13:11:21','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(147,27,'2023020616','2026-10-03 13:16:29','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(148,1,'admin','2026-10-03 13:17:08','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(149,27,'2023020616','2026-10-03 13:31:52','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(150,27,'2023020616','2026-10-03 13:34:21','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(151,27,'2023020616','2026-10-03 13:36:11','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(152,27,'2023020616','2026-10-03 13:36:48','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(153,27,'2023020616','2026-10-03 13:37:15','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(154,27,'2023020616','2026-10-03 13:37:37','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(155,27,'2023020616','2026-10-03 13:37:52','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(156,27,'2023020616','2026-10-03 13:38:10','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(157,27,'2023020616','2026-10-03 13:38:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(158,27,'2023020616','2026-10-03 13:38:35','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(159,27,'2023020616','2026-10-03 13:38:57','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(160,27,'2023020616','2026-10-03 13:39:13','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(161,27,'2023020616','2026-10-03 13:39:17','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(162,18,'teacher001','2026-10-03 13:39:42','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(163,53,'1000000001','2026-10-03 13:40:22','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(164,57,'collegeadmin1','2026-10-03 13:41:00','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(165,1,'admin','2026-10-03 14:14:23','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(166,1,'admin','2026-10-03 14:15:27','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(167,1,'admin','2026-10-03 14:19:32','0:0:0:0:0:0:0:1','node',NULL,'success'),(168,86,'uiprobetmp','2026-10-03 14:19:32','0:0:0:0:0:0:0:1','node',NULL,'success'),(169,86,'uiprobetmp','2026-10-03 14:19:32','0:0:0:0:0:0:0:1','node',NULL,'success'),(170,86,'uiprobetmp','2026-10-03 14:19:32','0:0:0:0:0:0:0:1','node',NULL,'success'),(171,86,'uiprobetmp','2026-10-03 14:19:32','0:0:0:0:0:0:0:1','node',NULL,'failed'),(172,86,'uiprobetmp','2026-10-03 14:19:32','0:0:0:0:0:0:0:1','node',NULL,'success'),(173,86,'uiprobetmp','2026-10-03 14:19:32','0:0:0:0:0:0:0:1','node',NULL,'failed'),(174,86,'uiprobetmp','2026-10-03 14:19:33','0:0:0:0:0:0:0:1','node',NULL,'success'),(175,1,'admin','2026-10-03 14:21:08','0:0:0:0:0:0:0:1','node',NULL,'success'),(176,57,'collegeadmin1','2026-10-03 14:22:43','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(177,1,'admin','2026-10-03 14:23:13','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(178,18,'teacher001','2026-10-03 14:24:33','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(179,53,'1000000001','2026-10-03 14:25:48','0:0:0:0:0:0:0:1','node',NULL,'success'),(180,53,'1000000001','2026-10-03 14:25:50','0:0:0:0:0:0:0:1','node',NULL,'success'),(181,1,'admin','2026-10-03 14:26:02','0:0:0:0:0:0:0:1','node',NULL,'success'),(182,1,'admin','2026-10-03 14:26:02','0:0:0:0:0:0:0:1','node',NULL,'success'),(183,1,'admin','2026-10-03 14:26:15','0:0:0:0:0:0:0:1','node',NULL,'success'),(184,1,'admin','2026-10-03 14:26:15','0:0:0:0:0:0:0:1','node',NULL,'success'),(185,53,'1000000001','2026-10-03 14:27:30','0:0:0:0:0:0:0:1','node',NULL,'success'),(186,53,'1000000001','2026-10-03 14:27:30','0:0:0:0:0:0:0:1','node',NULL,'success'),(187,1,'admin','2026-10-03 14:27:43','0:0:0:0:0:0:0:1','node',NULL,'success'),(188,1,'admin','2026-10-03 14:27:43','0:0:0:0:0:0:0:1','node',NULL,'success'),(189,1,'admin','2026-10-03 14:27:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(190,1,'admin','2026-10-03 14:27:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(191,18,'teacher001','2026-10-03 14:31:13','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(192,53,'1000000001','2026-10-03 14:31:30','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(193,27,'2023020616','2026-10-03 14:31:50','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(194,27,'2023020616','2026-10-03 14:31:56','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(195,1,'admin','2026-10-03 14:32:26','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(196,29,'10001','2026-10-03 14:34:00','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(197,1,'admin','2026-10-03 14:35:04','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(198,29,'10001','2026-10-03 14:36:29','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(199,29,'10001','2026-10-03 14:59:16','0:0:0:0:0:0:0:1','node',NULL,'success'),(200,27,'2023020616','2026-10-03 14:59:28','0:0:0:0:0:0:0:1','node',NULL,'success'),(201,53,'1000000001','2026-10-03 14:59:28','0:0:0:0:0:0:0:1','node',NULL,'success'),(202,27,'2023020616','2026-10-03 14:59:44','0:0:0:0:0:0:0:1','node',NULL,'success'),(203,53,'1000000001','2026-10-03 14:59:44','0:0:0:0:0:0:0:1','node',NULL,'success'),(204,18,'teacher001','2026-10-03 14:59:44','0:0:0:0:0:0:0:1','node',NULL,'success'),(205,18,'teacher001','2026-10-03 14:59:59','0:0:0:0:0:0:0:1','node',NULL,'success'),(206,29,'10001','2026-10-03 14:59:59','0:0:0:0:0:0:0:1','node',NULL,'success'),(207,27,'2023020616','2026-10-03 15:00:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(208,18,'teacher001','2026-10-03 15:00:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(209,29,'10001','2026-10-03 15:00:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(210,53,'1000000001','2026-10-03 15:00:57','0:0:0:0:0:0:0:1','node',NULL,'success'),(211,27,'2023020616','2026-10-03 15:01:11','0:0:0:0:0:0:0:1','node',NULL,'success'),(212,27,'2023020616','2026-10-03 15:01:40','0:0:0:0:0:0:0:1','node',NULL,'success'),(213,27,'2023020616','2026-10-03 15:26:05','0:0:0:0:0:0:0:1','node',NULL,'success'),(214,18,'teacher001','2026-10-03 15:26:05','0:0:0:0:0:0:0:1','node',NULL,'success'),(215,53,'1000000001','2026-10-03 15:26:05','0:0:0:0:0:0:0:1','node',NULL,'success'),(216,57,'collegeadmin1','2026-10-03 15:26:05','0:0:0:0:0:0:0:1','node',NULL,'success'),(217,1,'admin','2026-10-03 15:26:05','0:0:0:0:0:0:0:1','node',NULL,'success'),(218,27,'2023020616','2026-10-03 15:45:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(219,18,'teacher001','2026-10-03 15:45:57','0:0:0:0:0:0:0:1','node',NULL,'success'),(220,29,'10001','2026-10-03 15:45:57','0:0:0:0:0:0:0:1','node',NULL,'success'),(221,53,'1000000001','2026-10-03 15:45:57','0:0:0:0:0:0:0:1','node',NULL,'success'),(222,27,'2023020616','2026-10-03 15:46:58','0:0:0:0:0:0:0:1','node',NULL,'success'),(223,30,'2023020615','2026-10-03 15:46:58','0:0:0:0:0:0:0:1','node',NULL,'success'),(224,18,'teacher001','2026-10-03 15:46:58','0:0:0:0:0:0:0:1','node',NULL,'success'),(225,29,'10001','2026-10-03 15:46:58','0:0:0:0:0:0:0:1','node',NULL,'success'),(226,27,'2023020616','2026-10-03 15:47:13','0:0:0:0:0:0:0:1','node',NULL,'success'),(227,30,'2023020615','2026-10-03 15:47:13','0:0:0:0:0:0:0:1','node',NULL,'success'),(228,18,'teacher001','2026-10-03 15:47:13','0:0:0:0:0:0:0:1','node',NULL,'success'),(229,29,'10001','2026-10-03 15:47:13','0:0:0:0:0:0:0:1','node',NULL,'success'),(230,27,'2023020616','2026-10-03 15:47:30','0:0:0:0:0:0:0:1','node',NULL,'success'),(231,30,'2023020615','2026-10-03 15:47:30','0:0:0:0:0:0:0:1','node',NULL,'success'),(232,18,'teacher001','2026-10-03 15:47:30','0:0:0:0:0:0:0:1','node',NULL,'success'),(233,29,'10001','2026-10-03 15:47:30','0:0:0:0:0:0:0:1','node',NULL,'success'),(234,27,'2023020616','2026-10-03 15:47:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(235,30,'2023020615','2026-10-03 15:47:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(236,18,'teacher001','2026-10-03 15:47:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(237,29,'10001','2026-10-03 15:47:56','0:0:0:0:0:0:0:1','node',NULL,'success'),(238,27,'2023020616','2026-10-03 15:48:09','0:0:0:0:0:0:0:1','node',NULL,'success'),(239,30,'2023020615','2026-10-03 15:48:09','0:0:0:0:0:0:0:1','node',NULL,'success'),(240,18,'teacher001','2026-10-03 15:48:09','0:0:0:0:0:0:0:1','node',NULL,'success'),(241,29,'10001','2026-10-03 15:48:09','0:0:0:0:0:0:0:1','node',NULL,'success'),(242,27,'2023020616','2026-10-03 15:48:22','0:0:0:0:0:0:0:1','node',NULL,'success'),(243,30,'2023020615','2026-10-03 15:48:23','0:0:0:0:0:0:0:1','node',NULL,'success'),(244,18,'teacher001','2026-10-03 15:48:23','0:0:0:0:0:0:0:1','node',NULL,'success'),(245,29,'10001','2026-10-03 15:48:23','0:0:0:0:0:0:0:1','node',NULL,'success'),(246,27,'2023020616','2026-10-03 15:49:19','0:0:0:0:0:0:0:1','node',NULL,'success'),(247,18,'teacher001','2026-10-03 15:49:19','0:0:0:0:0:0:0:1','node',NULL,'success'),(248,29,'10001','2026-10-03 15:49:19','0:0:0:0:0:0:0:1','node',NULL,'success'),(249,53,'1000000001','2026-10-03 15:49:19','0:0:0:0:0:0:0:1','node',NULL,'success'),(250,27,'2023020616','2026-10-03 15:49:52','0:0:0:0:0:0:0:1','node',NULL,'success'),(251,30,'2023020615','2026-10-03 15:49:52','0:0:0:0:0:0:0:1','node',NULL,'success'),(252,18,'teacher001','2026-10-03 15:49:52','0:0:0:0:0:0:0:1','node',NULL,'success'),(253,29,'10001','2026-10-03 15:49:52','0:0:0:0:0:0:0:1','node',NULL,'success'),(254,27,'2023020616','2026-10-03 15:50:02','0:0:0:0:0:0:0:1','node',NULL,'success'),(255,30,'2023020615','2026-10-03 15:50:02','0:0:0:0:0:0:0:1','node',NULL,'success'),(256,18,'teacher001','2026-10-03 15:50:02','0:0:0:0:0:0:0:1','node',NULL,'success'),(257,29,'10001','2026-10-03 15:50:02','0:0:0:0:0:0:0:1','node',NULL,'success'),(258,27,'2023020616','2026-10-03 15:50:03','0:0:0:0:0:0:0:1','node',NULL,'success'),(259,30,'2023020615','2026-10-03 15:50:04','0:0:0:0:0:0:0:1','node',NULL,'success'),(260,18,'teacher001','2026-10-03 15:50:04','0:0:0:0:0:0:0:1','node',NULL,'success'),(261,29,'10001','2026-10-03 15:50:04','0:0:0:0:0:0:0:1','node',NULL,'success'),(262,27,'2023020616','2026-10-03 15:50:11','0:0:0:0:0:0:0:1','node',NULL,'success'),(263,30,'2023020615','2026-10-03 15:50:11','0:0:0:0:0:0:0:1','node',NULL,'success'),(264,18,'teacher001','2026-10-03 15:50:11','0:0:0:0:0:0:0:1','node',NULL,'success'),(265,29,'10001','2026-10-03 15:50:11','0:0:0:0:0:0:0:1','node',NULL,'success'),(266,27,'2023020616','2026-10-03 15:51:08','0:0:0:0:0:0:0:1','node',NULL,'success'),(267,27,'2023020616','2026-10-03 16:01:25','0:0:0:0:0:0:0:1','node',NULL,'success'),(268,30,'2023020615','2026-10-03 16:01:25','0:0:0:0:0:0:0:1','node',NULL,'success'),(269,18,'teacher001','2026-10-03 16:01:25','0:0:0:0:0:0:0:1','node',NULL,'success'),(270,29,'10001','2026-10-03 16:01:25','0:0:0:0:0:0:0:1','node',NULL,'success'),(271,27,'2023020616','2026-10-03 16:02:47','0:0:0:0:0:0:0:1','node',NULL,'success'),(272,30,'2023020615','2026-10-03 16:02:47','0:0:0:0:0:0:0:1','node',NULL,'success'),(273,18,'teacher001','2026-10-03 16:02:47','0:0:0:0:0:0:0:1','node',NULL,'success'),(274,29,'10001','2026-10-03 16:02:47','0:0:0:0:0:0:0:1','node',NULL,'success'),(275,27,'2023020616','2026-10-03 16:02:48','0:0:0:0:0:0:0:1','node',NULL,'success'),(276,30,'2023020615','2026-10-03 16:02:48','0:0:0:0:0:0:0:1','node',NULL,'success'),(277,18,'teacher001','2026-10-03 16:02:48','0:0:0:0:0:0:0:1','node',NULL,'success'),(278,29,'10001','2026-10-03 16:02:48','0:0:0:0:0:0:0:1','node',NULL,'success'),(279,27,'2023020616','2026-10-03 16:02:55','0:0:0:0:0:0:0:1','node',NULL,'success'),(280,30,'2023020615','2026-10-03 16:02:55','0:0:0:0:0:0:0:1','node',NULL,'success'),(281,18,'teacher001','2026-10-03 16:02:55','0:0:0:0:0:0:0:1','node',NULL,'success'),(282,29,'10001','2026-10-03 16:02:55','0:0:0:0:0:0:0:1','node',NULL,'success'),(283,18,'teacher001','2026-10-04 00:25:15','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(284,18,'teacher001','2026-10-04 00:25:37','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(285,18,'teacher001','2026-10-04 00:25:55','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(286,18,'teacher001','2026-10-04 00:26:12','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(287,27,'2023020616','2026-10-04 00:26:46','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(288,27,'2023020616','2026-10-04 00:27:04','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(289,27,'2023020616','2026-10-04 00:27:23','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(290,27,'2023020616','2026-10-04 00:27:45','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(291,27,'2023020616','2026-10-04 00:28:03','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(292,27,'2023020616','2026-10-04 00:28:25','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(293,27,'2023020616','2026-10-04 00:28:52','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(294,27,'2023020616','2026-10-04 00:29:07','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(295,18,'teacher001','2026-10-04 00:29:18','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(296,29,'10001','2026-10-04 13:18:33','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(297,29,'10001','2026-10-04 13:18:53','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(298,29,'10001','2026-10-04 13:19:22','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(299,29,'10001','2026-10-04 13:20:22','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(300,29,'10001','2026-10-04 13:21:43','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(301,29,'10001','2026-10-04 13:22:51','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(302,29,'10001','2026-10-04 13:23:49','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(303,29,'10001','2026-10-04 13:24:24','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(304,1,'admin','2026-10-04 13:25:39','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(305,1,'admin','2026-10-04 13:26:34','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(306,1,'admin','2026-10-04 13:27:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(307,1,'admin','2026-10-04 15:41:57','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(308,1,'admin','2026-10-04 15:42:53','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(309,1,'admin','2026-10-04 15:43:28','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(310,1,'admin','2026-10-04 15:44:10','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(311,1,'admin','2026-10-04 15:44:48','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(312,1,'admin','2026-10-04 15:45:32','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(313,1,'admin','2026-10-04 15:46:55','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(314,1,'admin','2026-10-04 15:50:37','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(315,1,'admin','2026-10-04 15:51:35','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(316,1,'admin','2026-10-04 15:52:26','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(317,1,'admin','2026-10-04 15:53:10','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(318,1,'admin','2026-10-04 15:53:38','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(319,88,'probe_w26_s','2026-10-08 01:53:37','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(320,89,'probe_w26_t','2026-10-08 01:53:37','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'failed'),(321,90,'probe_w26_x','2026-10-08 01:53:38','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(322,16,'adm001','2026-10-09 02:28:54','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(323,1,'admin','2026-10-09 02:41:28','0:0:0:0:0:0:0:1','curl/8.19.0',NULL,'success'),(324,53,'1000000001','2026-10-09 02:48:30','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'failed'),(325,53,'1000000001','2026-10-09 02:48:41','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(326,27,'2023020616','2026-10-09 02:49:41','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(327,16,'adm001','2026-10-09 02:58:10','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(328,1,'admin','2026-10-09 04:10:03','0:0:0:0:0:0:0:1','node',NULL,'success'),(329,1,'admin','2026-10-09 04:20:44','0:0:0:0:0:0:0:1','node',NULL,'success'),(330,1,'admin','2026-10-09 04:26:42','0:0:0:0:0:0:0:1','node',NULL,'success'),(331,1,'admin','2026-10-09 04:30:29','0:0:0:0:0:0:0:1','node',NULL,'success'),(332,1,'admin','2026-10-09 04:32:14','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(333,1,'admin','2026-10-09 04:32:42','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(334,1,'admin','2026-10-09 04:33:06','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(335,1,'admin','2026-10-09 04:33:38','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(336,1,'admin','2026-10-09 04:34:06','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(337,1,'admin','2026-10-09 04:34:58','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(338,1,'admin','2026-10-09 04:35:57','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(339,16,'adm001','2026-10-09 07:09:37','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(340,27,'2023020616','2026-10-09 07:09:51','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(341,29,'10001','2026-10-09 07:14:34','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(342,53,'1000000001','2026-10-09 07:18:24','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(343,1,'admin','2026-10-09 08:34:13','127.0.0.1','python-requests/2.33.1',NULL,'success'),(344,103,'W33PRB_login','2026-10-09 08:34:14','127.0.0.1','python-requests/2.33.1',NULL,'success'),(345,103,'W33PRB_login','2026-10-09 08:34:18','127.0.0.1','python-requests/2.33.1',NULL,'success'),(346,1,'admin','2026-10-09 08:40:25','127.0.0.1','python-requests/2.33.1',NULL,'success'),(347,104,'W33PRB_login','2026-10-09 08:40:26','127.0.0.1','python-requests/2.33.1',NULL,'success'),(348,104,'W33PRB_login','2026-10-09 08:40:27','127.0.0.1','python-requests/2.33.1',NULL,'success'),(349,1,'admin','2026-10-09 08:47:19','127.0.0.1','python-requests/2.33.1',NULL,'success'),(350,105,'W33PRB_login','2026-10-09 08:47:19','127.0.0.1','python-requests/2.33.1',NULL,'success'),(351,105,'W33PRB_login','2026-10-09 08:47:20','127.0.0.1','python-requests/2.33.1',NULL,'success'),(352,1,'admin','2026-10-09 08:56:43','127.0.0.1','curl/8.19.0',NULL,'success'),(353,1,'admin','2026-10-09 08:58:10','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(354,1,'admin','2026-10-09 09:00:24','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(355,1,'admin','2026-10-09 09:03:47','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/154.0.0.0 Safari/537.36',NULL,'success'),(356,29,'10001','2026-10-12 01:12:27','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(357,53,'1000000001','2026-10-12 01:14:20','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success'),(358,16,'adm001','2026-10-12 01:16:02','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36',NULL,'success');
/*!40000 ALTER TABLE `login_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `majors`
--

DROP TABLE IF EXISTS `majors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `majors` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `college_id` bigint NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `short_name` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专业简称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_majors_code` (`code`),
  KEY `idx_majors_college_id` (`college_id`),
  CONSTRAINT `fk_majors_college_id` FOREIGN KEY (`college_id`) REFERENCES `colleges` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `majors`
--

LOCK TABLES `majors` WRITE;
/*!40000 ALTER TABLE `majors` DISABLE KEYS */;
INSERT INTO `majors` VALUES (1,1,'软件工程','01','软工','软件工程专业',1,'2025-12-22 17:28:15','2026-06-24 00:53:26'),(2,1,'计算机科学与技术','02','计科','计算机科学与技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:53:26'),(4,3,'工商管理','04','工商','智能科学与技术专业',1,'2025-12-22 17:28:15','2026-09-16 14:07:32'),(6,2,'电子信息工程','06','电信','电子信息工程专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(7,2,'自动化','07','自动化','自动化专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(8,2,'人工智能','08','AI','人工智能专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(9,3,'电子商务','09','电商','电子商务专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(10,3,'物流管理','10','物流','物流管理专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(11,3,'财务管理','11','财管','财务管理专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(12,4,'网络与新媒体','12','网媒','网络与新媒体专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(13,4,'环境设计','13','环设','环境设计专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(14,4,'视觉传达设计','14','视传','视觉传达设计专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07');
/*!40000 ALTER TABLE `majors` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `messages`
--

DROP TABLE IF EXISTS `messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `sender_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` int NOT NULL DEFAULT '1',
  `status` int DEFAULT '0',
  `extra` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `read_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_messages_sender_id` (`sender_id`),
  KEY `fk_messages_receiver_id` (`receiver_id`),
  CONSTRAINT `fk_messages_receiver_id` FOREIGN KEY (`receiver_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_messages_sender_id` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `messages`
--

LOCK TABLES `messages` WRITE;
/*!40000 ALTER TABLE `messages` DISABLE KEYS */;
INSERT INTO `messages` VALUES (4,1,56,'写路径验证-临时广播','这条是 P7 写路径验证用的广播，验证后会清掉。',1,0,NULL,'2026-09-29 11:53:48','2026-09-29 11:53:48',NULL),(5,1,57,'写路径验证-临时广播','这条是 P7 写路径验证用的广播，验证后会清掉。',1,0,NULL,'2026-09-29 11:53:48','2026-09-29 11:53:48',NULL),(27,1,56,'写路径验证-临时广播','这条是 P7 写路径验证用的广播，验证后会清掉。',1,0,NULL,'2026-10-03 14:19:33','2026-10-03 14:19:32',NULL),(28,1,57,'写路径验证-临时广播','这条是 P7 写路径验证用的广播，验证后会清掉。',1,0,NULL,'2026-10-03 14:19:33','2026-10-03 14:19:32',NULL);
/*!40000 ALTER TABLE `messages` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `notifications`
--

DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `warning_id` bigint DEFAULT NULL,
  `push_channel` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'app',
  `is_read` int DEFAULT '0',
  `is_deleted` int DEFAULT '0',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_user_id` (`user_id`),
  CONSTRAINT `fk_notifications_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1009 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notifications`
--

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
INSERT INTO `notifications` VALUES (1,39,'低级预警通知','韩坤同学，您的成绩单中有2门课程不及格（操作系统54分、数据结构59分），已触发低级预警。请尽快与辅导员联系。','warning',NULL,'app',1,0,'2026-05-27 02:00:00','2026-10-01 14:45:38'),(2,46,'中级预警通知','李爽同学，您的成绩单中有3门课程不及格（计算机网络47分、计算机基础52分、高等数学54分），已触发中级预警。请务必重视并尽快联系辅导员。','warning',NULL,'app',1,0,'2026-05-27 02:00:00','2026-09-16 12:43:52'),(3,52,'中级预警通知','张健同学，您的成绩单中有3门课程不及格（计算机基础48分、计算机网络49分、高等数学52分），已触发中级预警。建议及时寻求学业帮扶。','warning',NULL,'app',0,0,'2026-05-27 02:00:00','2026-09-16 12:43:52'),(4,53,'班级预警汇总','您负责的计科2306班中，有4名学生触发了学业预警：司明(中级)、韩坤(低级)、李爽(中级)、张健(中级)。请及时跟进帮扶。','system',NULL,'app',0,0,'2026-05-27 02:00:00','2026-09-16 12:43:52'),(1001,27,'中级预警通知','司明同学，您的成绩单中有3门课程不及格（操作系统40分、计算机基础47分、高等数学56.5分），已触发中级预警。请尽快联系辅导员沟通帮扶安排。','warning',395,'app',0,0,'2026-09-02 06:36:09','2026-10-01 14:55:57'),(1002,27,'帮扶计划已制定','辅导员已为您制定学业帮扶计划（进行中）：每周两次学习小组辅导答疑。请积极跟进学习进度，如有困难可随时在系统中反馈。','system',NULL,'app',0,0,'2026-09-02 06:36:09','2026-09-02 06:36:09'),(1003,27,'成绩申诉已通过','你的成绩申诉已通过：大学英语\n审核意见：已复核答题卡与登分表：第四学期期末卷面 61 分，系登分时漏记 10 分，成绩已更正为 61 分。','appeal',NULL,'app',0,0,'2026-10-02 10:52:28','2026-10-02 10:52:28'),(1007,27,'成绩申诉已驳回','你的成绩申诉未通过：高等数学','appeal',NULL,'app',0,0,'2026-10-09 02:46:07','2026-10-09 02:46:07'),(1008,27,'你好','你好','class',NULL,'app',0,0,'2026-10-09 02:49:07','2026-10-09 02:49:07');
/*!40000 ALTER TABLE `notifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `permissions`
--

DROP TABLE IF EXISTS `permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permissions_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `permissions`
--

LOCK TABLES `permissions` WRITE;
/*!40000 ALTER TABLE `permissions` DISABLE KEYS */;
INSERT INTO `permissions` VALUES (1,'查看学生信息','PERM_STUDENT_VIEW','查看学生基本信息',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(2,'管理学生信息','PERM_STUDENT_MANAGE','管理学生信息',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(3,'查看课程信息','PERM_COURSE_VIEW','查看课程信息',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(4,'管理课程信息','PERM_COURSE_MANAGE','管理课程信息',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(5,'查看预警信息','PERM_WARNING_VIEW','查看预警信息',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(6,'管理预警规则','PERM_WARNING_MANAGE','管理预警规则',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(7,'查看帮扶计划','PERM_ASSISTANCE_VIEW','查看帮扶计划',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(8,'管理帮扶计划','PERM_ASSISTANCE_MANAGE','管理帮扶计划',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(9,'系统管理','PERM_SYSTEM_MANAGE','系统管理权限',1,'2025-12-22 17:28:15','2025-12-22 17:28:15');
/*!40000 ALTER TABLE `permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `report_templates`
--

DROP TABLE IF EXISTS `report_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `report_templates` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` bigint DEFAULT '0',
  `file_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` tinyint DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `report_templates`
--

LOCK TABLES `report_templates` WRITE;
/*!40000 ALTER TABLE `report_templates` DISABLE KEYS */;
/*!40000 ALTER TABLE `report_templates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role_permissions`
--

DROP TABLE IF EXISTS `role_permissions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permissions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permissions_role_permission` (`role_id`,`permission_id`),
  KEY `idx_role_permissions_role_id` (`role_id`),
  KEY `idx_role_permissions_permission_id` (`permission_id`),
  CONSTRAINT `fk_role_permissions_permission_id` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`),
  CONSTRAINT `fk_role_permissions_role_id` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_permissions`
--

LOCK TABLES `role_permissions` WRITE;
/*!40000 ALTER TABLE `role_permissions` DISABLE KEYS */;
INSERT INTO `role_permissions` VALUES (1,1,1,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(2,1,3,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(3,1,5,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(4,1,7,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(5,2,1,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(6,2,3,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(7,2,5,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(8,3,1,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(9,3,3,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(10,3,5,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(11,3,7,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(12,3,8,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(13,4,1,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(14,4,2,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(15,4,3,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(16,4,4,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(17,4,5,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(18,4,6,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(19,4,7,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(20,4,8,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(21,4,9,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(30,5,7,'2025-12-22 17:28:15','2026-10-01 14:42:23'),(31,5,3,'2025-12-22 17:28:15','2026-10-01 14:42:23'),(32,5,1,'2025-12-22 17:28:15','2026-10-01 14:42:23'),(33,5,6,'2025-12-22 17:28:15','2026-10-01 14:42:23'),(34,5,5,'2025-12-22 17:28:15','2026-10-01 14:42:23');
/*!40000 ALTER TABLE `role_permissions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_roles_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (1,'学生','ROLE_STUDENT','学生角色',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(2,'教师','ROLE_TEACHER','教师角色',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(3,'辅导员','ROLE_COUNSELOR','辅导员角色',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(4,'管理员','ROLE_ADMIN','系统管理员（全校）',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(5,'学院管理员','ROLE_COLLEGE_ADMIN','学院管理员（仅本学院）',1,'2025-12-22 17:28:15','2025-12-22 17:28:15');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_appeals`
--

DROP TABLE IF EXISTS `score_appeals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_appeals` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `warning_id` bigint DEFAULT NULL,
  `student_id` bigint NOT NULL,
  `score_id` bigint DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '学生自己填写的申诉详细说明（自由文本）；加列前的历史行为 NULL',
  `attachments` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int DEFAULT '0',
  `reply` text COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_score_appeals_student_id` (`student_id`),
  KEY `fk_score_appeals_score_id` (`score_id`),
  CONSTRAINT `fk_score_appeals_score_id` FOREIGN KEY (`score_id`) REFERENCES `scores` (`id`),
  CONSTRAINT `fk_score_appeals_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_appeals`
--

LOCK TABLES `score_appeals` WRITE;
/*!40000 ALTER TABLE `score_appeals` DISABLE KEYS */;
INSERT INTO `score_appeals` VALUES (6,NULL,3,157,1,'entry_error',NULL,NULL,2,NULL,'2026-07-08 06:01:34','2026-10-09 02:46:06'),(8,NULL,3,152,2,'entry_error','第四学期《大学英语》期末成绩登记有误，本人试卷卷面得分应为 61 分，请老师复核答题卡与登分表。',NULL,1,'已复核答题卡与登分表：第四学期期末卷面 61 分，系登分时漏记 10 分，成绩已更正为 61 分。','2026-10-02 10:52:09','2026-10-03 12:27:00');
/*!40000 ALTER TABLE `score_appeals` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_audit_log`
--

DROP TABLE IF EXISTS `score_audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `course_id` bigint DEFAULT NULL,
  `course_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `teacher_id` bigint NOT NULL,
  `old_score` decimal(5,2) DEFAULT NULL,
  `new_score` decimal(5,2) DEFAULT NULL,
  `difference` decimal(5,2) GENERATED ALWAYS AS ((`new_score` - `old_score`)) STORED,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_audit_log`
--

LOCK TABLES `score_audit_log` WRITE;
/*!40000 ALTER TABLE `score_audit_log` DISABLE KEYS */;
INSERT INTO `score_audit_log` (`id`, `student_id`, `student_name`, `course_id`, `course_name`, `teacher_id`, `old_score`, `new_score`, `reason`, `modified_by`, `modified_at`) VALUES (1,3,'司明',1,'高等数学',29,55.00,56.50,'试卷复核发现平时分漏计，据实更正','于凤海','2026-06-25 01:38:17');
/*!40000 ALTER TABLE `score_audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_record_bak_align_20260916`
--

DROP TABLE IF EXISTS `score_record_bak_align_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_record_bak_align_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `course_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `score` decimal(5,2) DEFAULT NULL,
  `score_level` int DEFAULT NULL,
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学期',
  `year` int DEFAULT NULL COMMENT '学年',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) DEFAULT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(3,2) DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_record_bak_align_20260916`
--

LOCK TABLES `score_record_bak_align_20260916` WRITE;
/*!40000 ALTER TABLE `score_record_bak_align_20260916` DISABLE KEYS */;
INSERT INTO `score_record_bak_align_20260916` VALUES (139,1,4,22,'邱琳紫','计科2306班',95.00,1,'2',NULL,'思维敏捷，解题能力强','2026-05-27 10:00:00','2026-06-27 09:58:51',96.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(140,2,4,22,'邱琳紫','计科2306班',92.00,1,'2',NULL,'口语表达流畅','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,93.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(141,3,4,22,'邱琳紫','计科2306班',88.00,2,'2',NULL,'数制转换准确','2026-05-27 10:00:00','2026-09-16 09:52:14',80.00,94.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(142,4,4,22,'邱琳紫','计科2306班',90.00,1,'2',NULL,'算法掌握扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(143,5,4,22,'邱琳紫','计科2306班',93.00,1,'2',NULL,'组网实验能力强，配置规范','2026-05-27 10:00:00','2026-09-16 09:52:14',92.00,101.00,97.00,'A',4.00,NULL,NULL,NULL,NULL),(144,6,4,22,'邱琳紫','计科2306班',91.00,1,'2',NULL,'理论理解深入','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(145,1,5,22,'宋新鹏','计科2306班',91.00,1,'2',NULL,'学习积极主动','2026-05-27 10:00:00','2026-06-27 09:58:51',98.00,90.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(146,2,5,22,'宋新鹏','计科2306班',89.00,2,'2',NULL,'阅读能力较强','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,82.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(147,3,5,22,'宋新鹏','计科2306班',85.00,2,'2',NULL,'计算机体系结构理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(148,4,5,22,'宋新鹏','计科2306班',92.00,1,'2',NULL,'项目完成出色','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(149,5,5,22,'宋新鹏','计科2306班',88.00,2,'2',NULL,'网络拓扑设计合理','2026-05-27 10:00:00','2026-09-16 09:52:15',90.00,85.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(150,6,5,22,'宋新鹏','计科2306班',90.00,1,'2',NULL,'实验报告完整','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,91.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(151,1,6,22,'赵琦','计科2306班',85.00,2,'2',NULL,'基础扎实，解题规范','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(152,2,6,22,'赵琦','计科2306班',82.00,2,'2',NULL,'听力需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(153,3,6,22,'赵琦','计科2306班',78.00,3,'2',NULL,'部分概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(154,4,6,22,'赵琦','计科2306班',84.00,2,'2',NULL,'递归理解较好','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,78.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(155,5,6,22,'赵琦','计科2306班',86.00,2,'2',NULL,'网络协议掌握良好','2026-05-27 10:00:00','2026-09-16 09:52:15',78.00,92.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(156,6,6,22,'赵琦','计科2306班',80.00,2,'2',NULL,'需加强实践','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,87.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(157,1,7,22,'苏红阳','计科2306班',82.00,2,'2',NULL,'课堂参与积极','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(158,2,7,22,'苏红阳','计科2306班',88.00,2,'2',NULL,'写作能力突出','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(159,3,7,22,'苏红阳','计科2306班',80.00,2,'2',NULL,'操作步骤完整','2026-05-27 10:00:00','2026-09-16 09:52:14',82.00,85.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(160,4,7,22,'苏红阳','计科2306班',83.00,2,'2',NULL,'树结构掌握较好','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,78.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(161,5,7,22,'苏红阳','计科2306班',85.00,2,'2',NULL,'多路复用理解深入','2026-05-27 10:00:00','2026-09-16 09:52:14',83.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(162,6,7,22,'苏红阳','计科2306班',87.00,2,'2',NULL,'进程调度理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',90.00,83.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(163,1,8,22,'唐浩然','计科2306班',88.00,2,'2',NULL,'解题思路清晰','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,84.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(164,2,8,22,'唐浩然','计科2306班',84.00,2,'2',NULL,'词汇量丰富','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(165,3,8,22,'唐浩然','计科2306班',86.00,2,'2',NULL,'办公软件操作熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',78.00,86.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(166,4,8,22,'唐浩然','计科2306班',81.00,2,'2',NULL,'图算法待加强','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(167,5,8,22,'唐浩然','计科2306班',82.00,2,'2',NULL,'网络命令熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',84.00,83.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(168,6,8,22,'唐浩然','计科2306班',85.00,2,'2',NULL,'进程管理理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',91.00,91.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(169,1,9,22,'王宏民','计科2306班',75.00,3,'2',NULL,'计算能力一般','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(170,2,9,22,'王宏民','计科2306班',78.00,3,'2',NULL,'语法基础尚可','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,85.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(171,3,9,22,'王宏民','计科2306班',72.00,3,'2',NULL,'数制概念需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',67.00,79.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(172,4,9,22,'王宏民','计科2306班',76.00,3,'2',NULL,'排序算法掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,84.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(173,5,9,22,'王宏民','计科2306班',74.00,3,'2',NULL,'差错控制需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',68.00,69.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(174,6,9,22,'王宏民','计科2306班',77.00,3,'2',NULL,'内存管理理解一般','2026-05-27 10:00:00','2026-09-16 09:52:15',79.00,79.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(175,1,10,22,'王玉蛟','计科2306班',83.00,2,'2',NULL,'学习态度认真','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,75.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(176,2,10,22,'王玉蛟','计科2306班',85.00,2,'2',NULL,'翻译能力较强','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(177,3,10,22,'王玉蛟','计科2306班',79.00,3,'2',NULL,'二进制运算需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',85.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(178,4,10,22,'王玉蛟','计科2306班',87.00,2,'2',NULL,'图论部分表现好','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,81.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(179,5,10,22,'王玉蛟','计科2306班',84.00,2,'2',NULL,'组网方案有创意','2026-05-27 10:00:00','2026-09-16 09:52:14',82.00,86.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(180,6,10,22,'王玉蛟','计科2306班',82.00,2,'2',NULL,'设备管理理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',77.00,76.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(181,1,11,22,'吴雨航','计科2306班',72.00,3,'2',NULL,'积分计算有困难','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,71.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(182,2,11,22,'吴雨航','计科2306班',76.00,3,'2',NULL,'词汇量不足','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,82.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(183,3,11,22,'吴雨航','计科2306班',74.00,3,'2',NULL,'需要多练习','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,66.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(184,4,11,22,'吴雨航','计科2306班',71.00,3,'2',NULL,'链表操作不熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,72.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(185,5,11,22,'吴雨航','计科2306班',79.00,3,'2',NULL,'网络基础过关','2026-05-27 10:00:00','2026-09-16 09:52:14',79.00,83.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(186,6,11,22,'吴雨航','计科2306班',73.00,3,'2',NULL,'文件系统需复习','2026-05-27 10:00:00','2026-09-16 09:52:15',69.00,77.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(187,1,12,22,'徐瑞瑶','计科2306班',87.00,2,'2',NULL,'数学思维好','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,80.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(188,2,12,22,'徐瑞瑶','计科2306班',83.00,2,'2',NULL,'听力理解优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,83.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(189,3,12,22,'徐瑞瑶','计科2306班',89.00,2,'2',NULL,'存储体系理解深入','2026-05-27 10:00:00','2026-09-16 09:52:14',89.00,86.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(190,4,12,22,'徐瑞瑶','计科2306班',85.00,2,'2',NULL,'哈希表掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,84.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(191,5,12,22,'徐瑞瑶','计科2306班',86.00,2,'2',NULL,'分层模型理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(192,6,12,22,'徐瑞瑶','计科2306班',84.00,2,'2',NULL,'并发控制理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',90.00,86.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(193,1,13,23,'韩坤','计科2306班',68.00,4,'2',NULL,'基础薄弱需加强','2026-05-27 10:00:00','2026-09-16 09:36:43',61.00,75.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(194,2,13,23,'韩坤','计科2306班',72.00,3,'2',NULL,'语法错误较多','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,79.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(195,3,13,23,'韩坤','计科2306班',70.00,3,'2',NULL,'进制换算常出错','2026-05-27 10:00:00','2026-09-16 09:52:14',76.00,70.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(196,4,13,23,'韩坤','计科2306班',65.00,4,'2',NULL,'栈和队列混淆','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,70.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(197,5,13,23,'韩坤','计科2306班',75.00,3,'2',NULL,'路由算法需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',82.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(198,6,13,23,'韩坤','计科2306班',71.00,3,'2',NULL,'基本概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,73.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(199,1,14,23,'张博巍','计科2306班',93.00,1,'2',NULL,'微积分掌握扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',100.00,100.00,100.00,'A',4.00,NULL,NULL,NULL,NULL),(200,2,14,23,'张博巍','计科2306班',90.00,1,'2',NULL,'口语发音标准','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,87.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(201,3,14,23,'张博巍','计科2306班',88.00,2,'2',NULL,'计算机组成理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',91.00,83.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(202,4,14,23,'张博巍','计科2306班',91.00,1,'2',NULL,'动态规划掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,87.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(203,5,14,23,'张博巍','计科2306班',94.00,1,'2',NULL,'组网实践能力强','2026-05-27 10:00:00','2026-09-16 09:52:15',94.00,96.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(204,6,14,23,'张博巍','计科2306班',89.00,2,'2',NULL,'内核结构掌握好','2026-05-27 10:00:00','2026-09-16 09:52:15',93.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(205,1,15,23,'程启頔','计科2306班',70.00,3,'2',NULL,'上课注意力不集中','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,62.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(206,2,15,23,'程启頔','计科2306班',74.00,3,'2',NULL,'听说能力待提高','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,75.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(207,3,15,23,'程启頔','计科2306班',68.00,4,'2',NULL,'算法流程图困难','2026-05-27 10:00:00','2026-09-16 09:52:14',60.00,75.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(208,4,15,23,'程启頔','计科2306班',72.00,3,'2',NULL,'查找算法需练习','2026-05-27 10:00:00','2026-06-27 09:58:51',77.00,78.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(209,5,15,23,'程启頔','计科2306班',76.00,3,'2',NULL,'子网划分基本正确','2026-05-27 10:00:00','2026-09-16 09:52:14',74.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(210,6,15,23,'程启頔','计科2306班',70.00,3,'2',NULL,'系统调用需加强','2026-05-27 10:00:00','2026-09-16 09:52:15',71.00,66.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(211,1,16,23,'邓雨轩','计科2306班',84.00,2,'2',NULL,'数列极限理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,86.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(212,2,16,23,'邓雨轩','计科2306班',86.00,2,'2',NULL,'作文表达流畅','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,87.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(213,3,16,23,'邓雨轩','计科2306班',80.00,2,'2',NULL,'文件管理操作准确','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(214,4,16,23,'邓雨轩','计科2306班',82.00,2,'2',NULL,'二叉树遍历掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,87.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(215,5,16,23,'邓雨轩','计科2306班',85.00,2,'2',NULL,'接口配置规范','2026-05-27 10:00:00','2026-09-16 09:52:14',91.00,84.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(216,6,16,23,'邓雨轩','计科2306班',83.00,2,'2',NULL,'信号量机制理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',78.00,90.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(217,1,17,23,'高菲','计科2306班',86.00,2,'2',NULL,'导数应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,89.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(218,2,17,23,'高菲','计科2306班',88.00,2,'2',NULL,'阅读理解准确','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,87.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(219,3,17,23,'高菲','计科2306班',82.00,2,'2',NULL,'表格数据处理掌握','2026-05-27 10:00:00','2026-09-16 09:52:14',84.00,76.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(220,4,17,23,'高菲','计科2306班',84.00,2,'2',NULL,'B树B+树理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(221,5,17,23,'高菲','计科2306班',87.00,2,'2',NULL,'数据流分析熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',93.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(222,6,17,23,'高菲','计科2306班',81.00,2,'2',NULL,'页面置换算法理解','2026-05-27 10:00:00','2026-09-16 09:52:15',86.00,87.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(223,1,18,23,'胡钰','计科2306班',78.00,3,'2',NULL,'不定积分有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(224,2,18,23,'胡钰','计科2306班',80.00,2,'2',NULL,'语法有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,80.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(225,3,18,23,'胡钰','计科2306班',72.00,3,'2',NULL,'硬件组成理解不足','2026-05-27 10:00:00','2026-09-16 09:52:14',74.00,68.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(226,4,18,23,'胡钰','计科2306班',75.00,3,'2',NULL,'代码实现有困难','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,76.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(227,5,18,23,'胡钰','计科2306班',79.00,3,'2',NULL,'Socket编程需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',78.00,74.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(228,6,18,23,'胡钰','计科2306班',74.00,3,'2',NULL,'缓冲区管理概念模糊','2026-05-27 10:00:00','2026-09-16 09:52:15',69.00,73.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(229,1,19,23,'李佳芮','计科2306班',92.00,1,'2',NULL,'空间解析几何好','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(230,2,19,23,'李佳芮','计科2306班',89.00,2,'2',NULL,'演讲能力出色','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,87.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(231,3,19,23,'李佳芮','计科2306班',93.00,1,'2',NULL,'网络基础知识理解深','2026-05-27 10:00:00','2026-09-16 09:52:14',96.00,86.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(232,4,19,23,'李佳芮','计科2306班',90.00,1,'2',NULL,'最短路径算法优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,92.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(233,5,19,23,'李佳芮','计科2306班',91.00,1,'2',NULL,'交换原理掌握好','2026-05-27 10:00:00','2026-09-16 09:52:14',94.00,97.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(234,6,19,23,'李佳芮','计科2306班',90.00,1,'2',NULL,'存储管理理解深','2026-05-27 10:00:00','2026-09-16 09:52:15',92.00,94.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(235,1,20,23,'李爽','计科2306班',66.00,4,'2',NULL,'基础不扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,69.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(236,2,20,23,'李爽','计科2306班',71.00,3,'2',NULL,'词汇积累不够','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(237,3,20,23,'李爽','计科2306班',69.00,4,'2',NULL,'故障判断困难','2026-05-27 10:00:00','2026-09-16 09:52:14',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(238,4,20,23,'李爽','计科2306班',70.00,3,'2',NULL,'遍历算法需复习','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,74.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(239,5,20,23,'李爽','计科2306班',68.00,4,'2',NULL,'协议层次混淆','2026-05-27 10:00:00','2026-09-16 09:52:14',69.00,74.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(240,6,20,23,'李爽','计科2306班',72.00,3,'2',NULL,'进程通信理解一般','2026-05-27 10:00:00','2026-09-16 09:52:15',77.00,75.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(241,1,21,23,'梁莹静','计科2306班',85.00,2,'2',NULL,'多元函数掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,79.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(242,2,21,23,'梁莹静','计科2306班',87.00,2,'2',NULL,'跨文化交际能力好','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,89.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(243,3,21,23,'梁莹静','计科2306班',81.00,2,'2',NULL,'操作系统概念清晰','2026-05-27 10:00:00','2026-09-16 09:52:14',80.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(244,4,21,23,'梁莹静','计科2306班',83.00,2,'2',NULL,'堆排序理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,82.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(245,5,21,23,'梁莹静','计科2306班',86.00,2,'2',NULL,'网线制作规范','2026-05-27 10:00:00','2026-09-16 09:52:14',89.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(246,6,21,23,'梁莹静','计科2306班',84.00,2,'2',NULL,'进程同步掌握好','2026-05-27 10:00:00','2026-09-16 09:52:15',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(247,1,22,23,'孟阳','计科2306班',73.00,3,'2',NULL,'函数连续性需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(248,2,22,23,'孟阳','计科2306班',75.00,3,'2',NULL,'长难句理解困难','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,73.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(249,3,22,23,'孟阳','计科2306班',71.00,3,'2',NULL,'数据编码理解不足','2026-05-27 10:00:00','2026-09-16 09:52:14',73.00,72.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(250,4,22,23,'孟阳','计科2306班',77.00,3,'2',NULL,'快速排序基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(251,5,22,23,'孟阳','计科2306班',74.00,3,'2',NULL,'抓包分析有进步','2026-05-27 10:00:00','2026-09-16 09:52:14',81.00,67.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(252,6,22,23,'孟阳','计科2306班',76.00,3,'2',NULL,'调度算法理解','2026-05-27 10:00:00','2026-09-16 09:52:15',77.00,74.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(253,1,23,23,'王瑾泰','计科2306班',82.00,2,'2',NULL,'定积分应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,78.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(254,2,23,23,'王瑾泰','计科2306班',84.00,2,'2',NULL,'完形填空准确','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(255,3,23,23,'王瑾泰','计科2306班',78.00,3,'2',NULL,'信息安全意识需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',71.00,84.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(256,4,23,23,'王瑾泰','计科2306班',85.00,2,'2',NULL,'AVL树掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,77.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(257,5,23,28,'王瑾泰','计科2306班',83.00,2,'2',NULL,'IP地址规划熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',88.00,85.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(258,6,23,28,'王瑾泰','计科2306班',80.00,2,'2',NULL,'虚拟内存理解','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(259,1,24,28,'王平洋','计科2306班',74.00,3,'2',NULL,'级数概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,78.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(260,2,24,28,'王平洋','计科2306班',73.00,3,'2',NULL,'翻译题薄弱','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(261,3,24,28,'王平洋','计科2306班',76.00,3,'2',NULL,'数据库基础理解一般','2026-05-27 10:00:00','2026-09-16 09:52:14',77.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(262,4,24,28,'王平洋','计科2306班',79.00,3,'2',NULL,'并查集基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,77.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(263,5,24,28,'王平洋','计科2306班',71.00,3,'2',NULL,'子网掩码计算生疏','2026-05-27 10:00:00','2026-09-16 09:52:14',71.00,78.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(264,6,24,28,'王平洋','计科2306班',75.00,3,'2',NULL,'死锁处理需复习','2026-05-27 10:00:00','2026-09-16 09:52:15',74.00,72.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(265,1,25,28,'夏志浩','计科2306班',94.00,1,'2',NULL,'数学基础雄厚','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,98.00,98.00,'A',4.00,NULL,NULL,NULL,NULL),(266,2,25,28,'夏志浩','计科2306班',91.00,1,'2',NULL,'学术写作优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,93.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(267,3,25,28,'夏志浩','计科2306班',87.00,2,'2',NULL,'动手操作能力强','2026-05-27 10:00:00','2026-09-16 09:52:14',92.00,84.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(268,4,25,28,'夏志浩','计科2306班',95.00,1,'2',NULL,'算法竞赛获奖','2026-05-27 10:00:00','2026-06-27 09:58:51',100.00,96.00,98.00,'A',4.00,NULL,NULL,NULL,NULL),(269,5,25,28,'夏志浩','计科2306班',92.00,1,'2',NULL,'精通网络排障','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(270,6,25,28,'夏志浩','计科2306班',88.00,2,'2',NULL,'多线程调度理解深','2026-05-27 10:00:00','2026-09-16 09:52:15',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(271,1,26,28,'张健','计科2306班',64.00,4,'2',NULL,'多处知识漏洞','2026-05-27 10:00:00','2026-06-27 09:58:51',62.00,69.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(272,2,26,28,'张健','计科2306班',69.00,4,'2',NULL,'听力严重薄弱','2026-05-27 10:00:00','2026-09-16 09:36:43',73.00,67.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(273,3,26,28,'张健','计科2306班',62.00,4,'2',NULL,'快捷键使用不熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',59.00,70.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(274,4,26,28,'张健','计科2306班',68.00,4,'2',NULL,'基本操作不熟练','2026-05-27 10:00:00','2026-09-16 09:36:43',66.00,61.00,63.00,'D',1.30,NULL,NULL,NULL,NULL),(275,5,26,28,'张健','计科2306班',65.00,4,'2',NULL,'网络分层理解不足','2026-05-27 10:00:00','2026-09-16 09:52:15',57.00,72.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(276,6,26,28,'张健','计科2306班',71.00,3,'2',NULL,'原语概念模糊','2026-05-27 10:00:00','2026-09-16 09:52:15',70.00,74.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(277,1,3,28,'司明','计科2306班',87.00,2,'3',NULL,'第3学期高等数学','2024-06-20 00:00:00','2026-09-16 09:36:43',84.00,92.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(278,2,3,28,'司明','计科2306班',80.50,2,'3',NULL,'第3学期大学英语','2024-06-20 00:00:00','2026-09-16 09:36:43',82.00,76.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(279,3,3,28,'司明','计科2306班',70.00,3,'3',NULL,'第3学期计算机基础','2024-06-20 00:00:00','2026-09-16 09:36:43',65.00,68.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(280,4,3,28,'司明','计科2306班',91.00,1,'3',NULL,'第3学期数据结构优秀','2024-06-20 00:00:00','2026-09-16 09:36:43',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(281,5,3,28,'司明','计科2306班',89.00,2,'3',NULL,'第3学期计算机网络','2024-06-20 00:00:00','2026-09-16 09:36:43',81.00,95.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(282,6,3,28,'司明','计科2306班',62.00,4,'3',NULL,'第3学期操作系统','2024-06-20 00:00:00','2026-09-16 09:36:43',55.00,67.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(283,1,3,28,'司明','计科2306班',71.50,3,'4',NULL,'第4学期高等数学下滑','2025-01-15 00:00:00','2026-09-16 09:36:43',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(284,2,3,28,'司明','计科2306班',51.00,5,'4',NULL,'第4学期大学英语不及格','2025-01-15 00:00:00','2026-09-16 09:36:43',48.00,56.00,53.00,'F',0.30,NULL,NULL,NULL,NULL),(285,3,3,28,'司明','计科2306班',59.00,5,'4',NULL,'第4学期计算机基础不及格','2025-01-15 00:00:00','2026-09-16 09:36:43',58.00,53.00,55.00,'F',0.50,NULL,NULL,NULL,NULL),(286,4,3,28,'司明','计科2306班',84.00,2,'4',NULL,'第4学期数据结构','2025-01-15 00:00:00','2026-09-16 09:36:43',83.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(287,6,3,28,'司明','计科2306班',40.00,5,'4',NULL,'第4学期操作系统严重不及格','2025-01-15 00:00:00','2026-09-16 09:36:43',34.00,39.00,37.00,'F',0.00,NULL,NULL,NULL,NULL),(288,1,3,28,'司明','计科2306班',56.50,5,'5',NULL,'第5学期高等数学持续下滑','2025-06-20 00:00:00','2026-09-16 09:36:43',52.00,54.00,53.00,'F',0.30,NULL,NULL,NULL,NULL),(289,5,3,28,'司明','计科2306班',65.00,4,'5',NULL,'第5学期计算机网络勉强及格','2025-06-20 00:00:00','2026-09-16 09:36:43',67.00,58.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(290,3,3,28,'司明','计科2306班',47.00,5,'5',NULL,'第5学期计算机基础补考仍不及格','2025-06-20 00:00:00','2026-09-16 09:36:43',44.00,53.00,49.00,'F',0.00,NULL,NULL,NULL,NULL),(291,7,3,28,'司明','计科2306班',85.00,2,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',87.00,86.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(294,8,3,28,'司明','计科2306班',78.00,3,'6',NULL,NULL,'2026-06-25 09:58:16','2026-09-16 09:36:43',70.00,77.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(295,8,4,28,'邱琳紫','计科2306班',88.00,2,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',81.00,84.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(296,7,4,28,'邱琳紫','计科2306班',90.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(297,7,5,28,'宋新鹏','计科2306班',88.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,83.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(298,7,6,28,'赵琦','计科2306班',85.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,81.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(299,7,7,28,'苏红阳','计科2306班',92.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,94.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(300,7,8,28,'唐浩然','计科2306班',80.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,73.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(301,7,9,28,'王宏民','计科2306班',78.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,82.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(302,7,10,28,'王玉蛟','计科2306班',86.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(303,7,11,28,'吴雨航','计科2306班',75.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,79.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(304,7,12,28,'徐瑞瑶','计科2306班',88.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,93.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(305,7,13,28,'韩坤','计科2306班',72.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(306,7,14,28,'张博巍','计科2306班',94.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',98.00,86.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(307,7,15,28,'程启頔','计科2306班',70.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',65.00,75.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(308,7,16,28,'邓雨轩','计科2306班',83.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,82.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(309,7,17,28,'高菲','计科2306班',89.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(310,7,18,28,'胡钰','计科2306班',76.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,82.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(311,7,19,28,'李佳芮','计科2306班',91.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',96.00,93.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(312,7,20,28,'李爽','计科2306班',68.00,4,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,74.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(313,7,21,28,'梁莹静','计科2306班',87.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,94.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(314,7,22,28,'孟阳','计科2306班',74.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,68.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(315,7,23,28,'王瑾泰','计科2306班',82.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(316,7,24,28,'王平洋','计科2306班',73.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,69.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(317,7,25,28,'夏志浩','计科2306班',95.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,92.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(318,7,26,28,'张健','计科2306班',65.00,4,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,67.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(319,8,4,28,'邱琳紫','计科2306班',88.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(320,8,5,28,'宋新鹏','计科2306班',90.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,83.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(321,8,6,18,'赵琦','计科2306班',82.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,90.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(322,8,7,18,'苏红阳','计科2306班',86.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,89.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(323,8,8,18,'唐浩然','计科2306班',84.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,87.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(324,8,9,18,'王宏民','计科2306班',76.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,80.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(325,8,10,18,'王玉蛟','计科2306班',83.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,82.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(326,8,11,18,'吴雨航','计科2306班',74.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(327,8,12,18,'徐瑞瑶','计科2306班',85.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,85.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(328,8,13,18,'韩坤','计科2306班',70.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,71.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(329,8,14,18,'张博巍','计科2306班',92.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,97.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(330,8,15,18,'程启頔','计科2306班',68.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-09-16 09:36:43',70.00,61.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(331,8,16,18,'邓雨轩','计科2306班',81.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(332,8,17,18,'高菲','计科2306班',87.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,86.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(333,8,18,18,'胡钰','计科2306班',78.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,84.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(334,8,19,18,'李佳芮','计科2306班',93.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(335,8,20,18,'李爽','计科2306班',66.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(336,8,21,18,'梁莹静','计科2306班',84.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(337,8,22,18,'孟阳','计科2306班',72.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,76.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(338,8,23,18,'王瑾泰','计科2306班',80.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(339,8,24,18,'王平洋','计科2306班',71.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,75.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(340,8,25,18,'夏志浩','计科2306班',96.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(341,8,26,18,'张健','计科2306班',63.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',58.00,62.00,60.00,'D',1.00,NULL,NULL,NULL,NULL),(342,9,4,18,'邱琳紫','计科2306班',85.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,77.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(343,9,5,18,'宋新鹏','计科2306班',87.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,80.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(344,9,6,18,'赵琦','计科2306班',83.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(345,9,7,18,'苏红阳','计科2306班',90.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(346,9,8,18,'唐浩然','计科2306班',79.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,81.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(347,9,9,18,'王宏民','计科2306班',74.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,73.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(348,9,10,18,'王玉蛟','计科2306班',88.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,83.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(349,9,11,18,'吴雨航','计科2306班',72.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,79.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(350,9,12,18,'徐瑞瑶','计科2306班',86.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(351,9,13,18,'韩坤','计科2306班',68.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-09-16 09:36:43',72.00,61.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(352,9,14,18,'张博巍','计科2306班',89.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(353,9,15,18,'程启頔','计科2306班',66.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(354,9,16,18,'邓雨轩','计科2306班',80.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,84.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(355,9,17,18,'高菲','计科2306班',91.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,91.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(356,9,18,18,'胡钰','计科2306班',75.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,68.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(357,9,19,18,'李佳芮','计科2306班',94.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,90.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(358,9,20,18,'李爽','计科2306班',64.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,62.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(359,9,21,18,'梁莹静','计科2306班',82.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(360,9,22,18,'孟阳','计科2306班',70.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',62.00,67.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(361,9,23,18,'王瑾泰','计科2306班',78.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,76.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(362,9,24,18,'王平洋','计科2306班',69.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-09-16 09:36:43',77.00,67.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(363,9,25,18,'夏志浩','计科2306班',92.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,96.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(364,9,26,18,'张健','计科2306班',61.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,54.00,56.00,'F',0.60,NULL,NULL,NULL,NULL),(365,10,4,18,'邱琳紫','计科2306班',92.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(366,10,5,18,'宋新鹏','计科2306班',86.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,82.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(367,10,6,18,'赵琦','计科2306班',84.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,77.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(368,10,7,18,'苏红阳','计科2306班',88.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(369,10,8,18,'唐浩然','计科2306班',81.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,81.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(370,10,9,18,'王宏民','计科2306班',77.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,71.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(371,10,10,18,'王玉蛟','计科2306班',90.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,84.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(372,10,11,18,'吴雨航','计科2306班',73.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,72.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(373,10,12,18,'徐瑞瑶','计科2306班',87.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(374,10,13,18,'韩坤','计科2306班',69.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-09-16 09:36:43',75.00,62.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(375,10,14,18,'张博巍','计科2306班',91.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(376,10,15,18,'程启頔','计科2306班',67.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-09-16 09:36:43',60.00,64.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(377,10,16,18,'邓雨轩','计科2306班',82.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,80.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(378,10,17,18,'高菲','计科2306班',89.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,82.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(379,10,18,18,'胡钰','计科2306班',77.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,69.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(380,10,19,18,'李佳芮','计科2306班',90.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(381,10,20,18,'李爽','计科2306班',65.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,65.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(382,10,21,18,'梁莹静','计科2306班',85.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(383,10,22,18,'孟阳','计科2306班',71.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,71.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(384,10,23,18,'王瑾泰','计科2306班',79.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(385,10,24,29,'王平洋','计科2306班',70.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',62.00,73.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(386,10,25,29,'夏志浩','计科2306班',93.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',86.00,101.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(387,10,26,29,'张健','计科2306班',62.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',57.00,69.00,64.00,'D',1.40,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `score_record_bak_align_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_record_bak_dup_20260916`
--

DROP TABLE IF EXISTS `score_record_bak_dup_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_record_bak_dup_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `course_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `score` decimal(5,2) DEFAULT NULL,
  `score_level` int DEFAULT NULL,
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学期',
  `year` int DEFAULT NULL COMMENT '学年',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) DEFAULT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(3,2) DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_record_bak_dup_20260916`
--

LOCK TABLES `score_record_bak_dup_20260916` WRITE;
/*!40000 ALTER TABLE `score_record_bak_dup_20260916` DISABLE KEYS */;
INSERT INTO `score_record_bak_dup_20260916` VALUES (2,2,4,29,'邱琳紫','计科2306班',92.00,1,'2',NULL,'口语表达流畅','2026-05-27 10:00:00','2026-06-27 09:41:16',86.00,84.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(3,3,4,29,'邱琳紫','计科2306班',88.00,2,'2',NULL,'数制转换准确','2026-05-27 10:00:00','2026-09-16 09:52:14',82.00,91.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(4,4,4,29,'邱琳紫','计科2306班',90.00,1,'2',NULL,'算法掌握扎实','2026-05-27 10:00:00','2026-06-27 09:41:16',82.00,93.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(5,5,4,29,'邱琳紫','计科2306班',93.00,1,'2',NULL,'组网实验能力强，配置规范','2026-05-27 10:00:00','2026-09-16 09:52:14',97.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(6,6,4,29,'邱琳紫','计科2306班',91.00,1,'2',NULL,'理论理解深入','2026-05-27 10:00:00','2026-06-27 09:41:16',90.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(7,1,5,29,'宋新鹏','计科2306班',91.00,1,'2',NULL,'学习积极主动','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,85.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(8,2,5,29,'宋新鹏','计科2306班',89.00,2,'2',NULL,'阅读能力较强','2026-05-27 10:00:00','2026-06-27 09:41:16',82.00,94.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(9,3,5,29,'宋新鹏','计科2306班',85.00,2,'2',NULL,'计算机体系结构理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',79.00,86.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(10,4,5,29,'宋新鹏','计科2306班',92.00,1,'2',NULL,'项目完成出色','2026-05-27 10:00:00','2026-06-27 09:41:16',91.00,95.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(11,5,5,29,'宋新鹏','计科2306班',88.00,2,'2',NULL,'网络拓扑设计合理','2026-05-27 10:00:00','2026-09-16 09:52:15',88.00,96.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(12,6,5,29,'宋新鹏','计科2306班',90.00,1,'2',NULL,'实验报告完整','2026-05-27 10:00:00','2026-06-27 09:41:16',93.00,96.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(13,1,6,29,'赵琦','计科2306班',85.00,2,'2',NULL,'基础扎实，解题规范','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,89.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(14,2,6,29,'赵琦','计科2306班',82.00,2,'2',NULL,'听力需加强','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(15,3,6,29,'赵琦','计科2306班',78.00,3,'2',NULL,'部分概念模糊','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(16,4,6,29,'赵琦','计科2306班',84.00,2,'2',NULL,'递归理解较好','2026-05-27 10:00:00','2026-06-27 09:41:16',92.00,84.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(17,5,6,29,'赵琦','计科2306班',86.00,2,'2',NULL,'网络协议掌握良好','2026-05-27 10:00:00','2026-09-16 09:52:15',80.00,94.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(18,6,6,29,'赵琦','计科2306班',80.00,2,'2',NULL,'需加强实践','2026-05-27 10:00:00','2026-06-27 09:41:16',81.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(19,1,7,29,'苏红阳','计科2306班',82.00,2,'2',NULL,'课堂参与积极','2026-05-27 10:00:00','2026-06-27 09:41:16',88.00,83.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(20,2,7,29,'苏红阳','计科2306班',88.00,2,'2',NULL,'写作能力突出','2026-05-27 10:00:00','2026-06-27 09:41:16',80.00,86.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(21,3,7,29,'苏红阳','计科2306班',80.00,2,'2',NULL,'操作步骤完整','2026-05-27 10:00:00','2026-09-16 09:52:14',76.00,88.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(22,4,7,29,'苏红阳','计科2306班',83.00,2,'2',NULL,'树结构掌握较好','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,76.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(23,5,7,29,'苏红阳','计科2306班',85.00,2,'2',NULL,'多路复用理解深入','2026-05-27 10:00:00','2026-09-16 09:52:14',87.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(24,6,7,29,'苏红阳','计科2306班',87.00,2,'2',NULL,'进程调度理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',94.00,86.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(25,1,8,29,'唐浩然','计科2306班',88.00,2,'2',NULL,'解题思路清晰','2026-05-27 10:00:00','2026-06-27 09:41:16',86.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(26,2,8,29,'唐浩然','计科2306班',84.00,2,'2',NULL,'词汇量丰富','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,90.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(27,3,8,29,'唐浩然','计科2306班',86.00,2,'2',NULL,'办公软件操作熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',81.00,91.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(28,4,8,29,'唐浩然','计科2306班',81.00,2,'2',NULL,'图算法待加强','2026-05-27 10:00:00','2026-06-27 09:41:16',81.00,79.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(29,5,8,29,'唐浩然','计科2306班',82.00,2,'2',NULL,'网络命令熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',83.00,90.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(30,6,8,29,'唐浩然','计科2306班',85.00,2,'2',NULL,'进程管理理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(31,1,9,29,'王宏民','计科2306班',75.00,3,'2',NULL,'计算能力一般','2026-05-27 10:00:00','2026-06-27 09:41:16',72.00,72.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(32,2,9,29,'王宏民','计科2306班',78.00,3,'2',NULL,'语法基础尚可','2026-05-27 10:00:00','2026-06-27 09:41:16',79.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(33,3,9,29,'王宏民','计科2306班',72.00,3,'2',NULL,'数制概念需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',69.00,68.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(34,4,9,29,'王宏民','计科2306班',76.00,3,'2',NULL,'排序算法掌握','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,75.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(35,5,9,29,'王宏民','计科2306班',74.00,3,'2',NULL,'差错控制需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',72.00,81.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(36,6,9,29,'王宏民','计科2306班',77.00,3,'2',NULL,'内存管理理解一般','2026-05-27 10:00:00','2026-09-16 09:52:15',78.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(37,1,10,29,'王玉蛟','计科2306班',83.00,2,'2',NULL,'学习态度认真','2026-05-27 10:00:00','2026-06-27 09:41:16',85.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(38,2,10,29,'王玉蛟','计科2306班',85.00,2,'2',NULL,'翻译能力较强','2026-05-27 10:00:00','2026-06-27 09:41:16',87.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(39,3,10,29,'王玉蛟','计科2306班',79.00,3,'2',NULL,'二进制运算需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',74.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(40,4,10,29,'王玉蛟','计科2306班',87.00,2,'2',NULL,'图论部分表现好','2026-05-27 10:00:00','2026-06-27 09:41:16',92.00,94.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(41,5,10,29,'王玉蛟','计科2306班',84.00,2,'2',NULL,'组网方案有创意','2026-05-27 10:00:00','2026-09-16 09:52:14',83.00,84.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(42,6,10,29,'王玉蛟','计科2306班',82.00,2,'2',NULL,'设备管理理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(43,1,11,29,'吴雨航','计科2306班',72.00,3,'2',NULL,'积分计算有困难','2026-05-27 10:00:00','2026-06-27 09:41:16',74.00,77.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(44,2,11,29,'吴雨航','计科2306班',76.00,3,'2',NULL,'词汇量不足','2026-05-27 10:00:00','2026-06-27 09:41:16',82.00,75.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(45,3,11,29,'吴雨航','计科2306班',74.00,3,'2',NULL,'需要多练习','2026-05-27 10:00:00','2026-06-27 09:41:16',68.00,70.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(46,4,11,29,'吴雨航','计科2306班',71.00,3,'2',NULL,'链表操作不熟练','2026-05-27 10:00:00','2026-06-27 09:41:16',67.00,70.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(47,5,11,29,'吴雨航','计科2306班',79.00,3,'2',NULL,'网络基础过关','2026-05-27 10:00:00','2026-09-16 09:52:14',78.00,82.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(48,6,11,29,'吴雨航','计科2306班',73.00,3,'2',NULL,'文件系统需复习','2026-05-27 10:00:00','2026-09-16 09:52:15',69.00,74.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(49,1,12,29,'徐瑞瑶','计科2306班',87.00,2,'2',NULL,'数学思维好','2026-05-27 10:00:00','2026-06-27 09:41:16',87.00,93.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(50,2,12,29,'徐瑞瑶','计科2306班',83.00,2,'2',NULL,'听力理解优秀','2026-05-27 10:00:00','2026-06-27 09:41:16',89.00,90.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(51,3,12,29,'徐瑞瑶','计科2306班',89.00,2,'2',NULL,'存储体系理解深入','2026-05-27 10:00:00','2026-09-16 09:52:14',96.00,97.00,97.00,'A',4.00,NULL,NULL,NULL,NULL),(52,4,12,29,'徐瑞瑶','计科2306班',85.00,2,'2',NULL,'哈希表掌握好','2026-05-27 10:00:00','2026-06-27 09:41:16',89.00,81.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(53,5,12,29,'徐瑞瑶','计科2306班',86.00,2,'2',NULL,'分层模型理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',92.00,82.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(54,6,12,29,'徐瑞瑶','计科2306班',84.00,2,'2',NULL,'并发控制理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',78.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(55,1,13,29,'韩坤','计科2306班',68.00,4,'2',NULL,'基础薄弱需加强','2026-05-27 10:00:00','2026-09-16 09:36:43',65.00,69.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(56,2,13,29,'韩坤','计科2306班',72.00,3,'2',NULL,'语法错误较多','2026-05-27 10:00:00','2026-06-27 09:41:16',71.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(57,3,13,29,'韩坤','计科2306班',70.00,3,'2',NULL,'进制换算常出错','2026-05-27 10:00:00','2026-09-16 09:52:14',63.00,66.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(58,4,13,29,'韩坤','计科2306班',65.00,4,'2',NULL,'栈和队列混淆','2026-05-27 10:00:00','2026-06-27 09:41:16',58.00,57.00,57.00,'F',0.70,NULL,NULL,NULL,NULL),(59,5,13,29,'韩坤','计科2306班',75.00,3,'2',NULL,'路由算法需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',79.00,69.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(60,6,13,29,'韩坤','计科2306班',71.00,3,'2',NULL,'基本概念模糊','2026-05-27 10:00:00','2026-06-27 09:41:16',79.00,67.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(61,1,14,29,'张博巍','计科2306班',93.00,1,'2',NULL,'微积分掌握扎实','2026-05-27 10:00:00','2026-06-27 09:41:16',99.00,93.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(62,2,14,29,'张博巍','计科2306班',90.00,1,'2',NULL,'口语发音标准','2026-05-27 10:00:00','2026-06-27 09:41:16',92.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(63,3,14,29,'张博巍','计科2306班',88.00,2,'2',NULL,'计算机组成理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',84.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(64,4,14,29,'张博巍','计科2306班',91.00,1,'2',NULL,'动态规划掌握好','2026-05-27 10:00:00','2026-06-27 09:41:16',96.00,94.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(65,5,14,19,'张博巍','计科2306班',94.00,1,'2',NULL,'组网实践能力强','2026-05-27 10:00:00','2026-09-16 09:52:15',95.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(66,6,14,19,'张博巍','计科2306班',89.00,2,'2',NULL,'内核结构掌握好','2026-05-27 10:00:00','2026-09-16 09:52:15',82.00,95.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(67,1,15,19,'程启頔','计科2306班',70.00,3,'2',NULL,'上课注意力不集中','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,65.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(68,2,15,19,'程启頔','计科2306班',74.00,3,'2',NULL,'听说能力待提高','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,68.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(69,3,15,19,'程启頔','计科2306班',68.00,4,'2',NULL,'算法流程图困难','2026-05-27 10:00:00','2026-09-16 09:52:14',74.00,63.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(70,4,15,19,'程启頔','计科2306班',72.00,3,'2',NULL,'查找算法需练习','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,72.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(71,5,15,19,'程启頔','计科2306班',76.00,3,'2',NULL,'子网划分基本正确','2026-05-27 10:00:00','2026-09-16 09:52:14',69.00,80.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(72,6,15,19,'程启頔','计科2306班',70.00,3,'2',NULL,'系统调用需加强','2026-05-27 10:00:00','2026-09-16 09:52:15',62.00,64.00,63.00,'D',1.30,NULL,NULL,NULL,NULL),(73,1,16,19,'邓雨轩','计科2306班',84.00,2,'2',NULL,'数列极限理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,85.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(74,2,16,19,'邓雨轩','计科2306班',86.00,2,'2',NULL,'作文表达流畅','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(75,3,16,19,'邓雨轩','计科2306班',80.00,2,'2',NULL,'文件管理操作准确','2026-05-27 10:00:00','2026-09-16 09:52:14',83.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(76,4,16,19,'邓雨轩','计科2306班',82.00,2,'2',NULL,'二叉树遍历掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,75.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(77,5,16,19,'邓雨轩','计科2306班',85.00,2,'2',NULL,'接口配置规范','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,78.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(78,6,16,19,'邓雨轩','计科2306班',83.00,2,'2',NULL,'信号量机制理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',86.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(79,1,17,19,'高菲','计科2306班',86.00,2,'2',NULL,'导数应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(80,2,17,19,'高菲','计科2306班',88.00,2,'2',NULL,'阅读理解准确','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,94.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(81,3,17,19,'高菲','计科2306班',82.00,2,'2',NULL,'表格数据处理掌握','2026-05-27 10:00:00','2026-09-16 09:52:14',82.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(82,4,17,19,'高菲','计科2306班',84.00,2,'2',NULL,'B树B+树理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,85.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(83,5,17,19,'高菲','计科2306班',87.00,2,'2',NULL,'数据流分析熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,82.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(84,6,17,19,'高菲','计科2306班',81.00,2,'2',NULL,'页面置换算法理解','2026-05-27 10:00:00','2026-09-16 09:52:15',85.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(85,1,18,19,'胡钰','计科2306班',78.00,3,'2',NULL,'不定积分有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,82.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(86,2,18,19,'胡钰','计科2306班',80.00,2,'2',NULL,'语法有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,87.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(87,3,18,19,'胡钰','计科2306班',72.00,3,'2',NULL,'硬件组成理解不足','2026-05-27 10:00:00','2026-09-16 09:52:14',69.00,78.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(88,4,18,19,'胡钰','计科2306班',75.00,3,'2',NULL,'代码实现有困难','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,82.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(89,5,18,19,'胡钰','计科2306班',79.00,3,'2',NULL,'Socket编程需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',79.00,72.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(90,6,18,19,'胡钰','计科2306班',74.00,3,'2',NULL,'缓冲区管理概念模糊','2026-05-27 10:00:00','2026-09-16 09:52:15',68.00,69.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(91,1,19,19,'李佳芮','计科2306班',92.00,1,'2',NULL,'空间解析几何好','2026-05-27 10:00:00','2026-06-27 09:58:51',100.00,91.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(92,2,19,19,'李佳芮','计科2306班',89.00,2,'2',NULL,'演讲能力出色','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,88.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(93,3,19,19,'李佳芮','计科2306班',93.00,1,'2',NULL,'网络基础知识理解深','2026-05-27 10:00:00','2026-09-16 09:52:14',95.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(94,4,19,19,'李佳芮','计科2306班',90.00,1,'2',NULL,'最短路径算法优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,97.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(95,5,19,19,'李佳芮','计科2306班',91.00,1,'2',NULL,'交换原理掌握好','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,95.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(96,6,19,19,'李佳芮','计科2306班',90.00,1,'2',NULL,'存储管理理解深','2026-05-27 10:00:00','2026-09-16 09:52:15',93.00,96.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(97,1,20,19,'李爽','计科2306班',66.00,4,'2',NULL,'基础不扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,74.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(98,2,20,19,'李爽','计科2306班',71.00,3,'2',NULL,'词汇积累不够','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,69.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(99,3,20,19,'李爽','计科2306班',69.00,4,'2',NULL,'故障判断困难','2026-05-27 10:00:00','2026-09-16 09:52:14',70.00,64.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(100,4,20,19,'李爽','计科2306班',70.00,3,'2',NULL,'遍历算法需复习','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,77.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(101,5,20,19,'李爽','计科2306班',68.00,4,'2',NULL,'协议层次混淆','2026-05-27 10:00:00','2026-09-16 09:52:14',70.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(102,6,20,19,'李爽','计科2306班',72.00,3,'2',NULL,'进程通信理解一般','2026-05-27 10:00:00','2026-09-16 09:52:15',67.00,67.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(103,1,21,19,'梁莹静','计科2306班',85.00,2,'2',NULL,'多元函数掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,87.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(104,2,21,19,'梁莹静','计科2306班',87.00,2,'2',NULL,'跨文化交际能力好','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,91.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(105,3,21,19,'梁莹静','计科2306班',81.00,2,'2',NULL,'操作系统概念清晰','2026-05-27 10:00:00','2026-09-16 09:52:14',73.00,81.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(106,4,21,19,'梁莹静','计科2306班',83.00,2,'2',NULL,'堆排序理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,77.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(107,5,21,19,'梁莹静','计科2306班',86.00,2,'2',NULL,'网线制作规范','2026-05-27 10:00:00','2026-09-16 09:52:14',89.00,78.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(108,6,21,19,'梁莹静','计科2306班',84.00,2,'2',NULL,'进程同步掌握好','2026-05-27 10:00:00','2026-09-16 09:52:15',85.00,77.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(109,1,22,19,'孟阳','计科2306班',73.00,3,'2',NULL,'函数连续性需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,70.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(110,2,22,19,'孟阳','计科2306班',75.00,3,'2',NULL,'长难句理解困难','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(111,3,22,19,'孟阳','计科2306班',71.00,3,'2',NULL,'数据编码理解不足','2026-05-27 10:00:00','2026-09-16 09:52:14',69.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(112,4,22,19,'孟阳','计科2306班',77.00,3,'2',NULL,'快速排序基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,78.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(113,5,22,19,'孟阳','计科2306班',74.00,3,'2',NULL,'抓包分析有进步','2026-05-27 10:00:00','2026-09-16 09:52:14',81.00,76.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(114,6,22,19,'孟阳','计科2306班',76.00,3,'2',NULL,'调度算法理解','2026-05-27 10:00:00','2026-09-16 09:52:15',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(115,1,23,19,'王瑾泰','计科2306班',82.00,2,'2',NULL,'定积分应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,87.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(116,2,23,19,'王瑾泰','计科2306班',84.00,2,'2',NULL,'完形填空准确','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(117,3,23,19,'王瑾泰','计科2306班',78.00,3,'2',NULL,'信息安全意识需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',71.00,71.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(118,4,23,19,'王瑾泰','计科2306班',85.00,2,'2',NULL,'AVL树掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,91.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(119,5,23,19,'王瑾泰','计科2306班',83.00,2,'2',NULL,'IP地址规划熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',85.00,82.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(120,6,23,19,'王瑾泰','计科2306班',80.00,2,'2',NULL,'虚拟内存理解','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,85.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(121,1,24,19,'王平洋','计科2306班',74.00,3,'2',NULL,'级数概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,80.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(122,2,24,19,'王平洋','计科2306班',73.00,3,'2',NULL,'翻译题薄弱','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,72.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(123,3,24,19,'王平洋','计科2306班',76.00,3,'2',NULL,'数据库基础理解一般','2026-05-27 10:00:00','2026-09-16 09:52:14',69.00,75.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(124,4,24,19,'王平洋','计科2306班',79.00,3,'2',NULL,'并查集基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,82.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(125,5,24,19,'王平洋','计科2306班',71.00,3,'2',NULL,'子网掩码计算生疏','2026-05-27 10:00:00','2026-09-16 09:52:14',78.00,65.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(126,6,24,19,'王平洋','计科2306班',75.00,3,'2',NULL,'死锁处理需复习','2026-05-27 10:00:00','2026-09-16 09:52:15',74.00,74.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(127,1,25,19,'夏志浩','计科2306班',94.00,1,'2',NULL,'数学基础雄厚','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,87.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(128,2,25,19,'夏志浩','计科2306班',91.00,1,'2',NULL,'学术写作优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,98.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(129,3,25,22,'夏志浩','计科2306班',87.00,2,'2',NULL,'动手操作能力强','2026-05-27 10:00:00','2026-09-16 09:52:14',83.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(130,4,25,22,'夏志浩','计科2306班',95.00,1,'2',NULL,'算法竞赛获奖','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,101.00,98.00,'A',4.00,NULL,NULL,NULL,NULL),(131,5,25,22,'夏志浩','计科2306班',92.00,1,'2',NULL,'精通网络排障','2026-05-27 10:00:00','2026-09-16 09:52:14',95.00,99.00,97.00,'A',4.00,NULL,NULL,NULL,NULL),(132,6,25,22,'夏志浩','计科2306班',88.00,2,'2',NULL,'多线程调度理解深','2026-05-27 10:00:00','2026-09-16 09:52:15',86.00,83.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(133,1,26,22,'张健','计科2306班',64.00,4,'2',NULL,'多处知识漏洞','2026-05-27 10:00:00','2026-06-27 09:58:51',64.00,64.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(134,2,26,22,'张健','计科2306班',69.00,4,'2',NULL,'听力严重薄弱','2026-05-27 10:00:00','2026-09-16 09:36:43',69.00,64.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(135,3,26,22,'张健','计科2306班',62.00,4,'2',NULL,'快捷键使用不熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',58.00,60.00,59.00,'F',0.90,NULL,NULL,NULL,NULL),(136,4,26,22,'张健','计科2306班',68.00,4,'2',NULL,'基本操作不熟练','2026-05-27 10:00:00','2026-09-16 09:36:43',74.00,62.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(137,5,26,22,'张健','计科2306班',65.00,4,'2',NULL,'网络分层理解不足','2026-05-27 10:00:00','2026-09-16 09:52:15',61.00,64.00,63.00,'D',1.30,NULL,NULL,NULL,NULL),(138,6,26,22,'张健','计科2306班',71.00,3,'2',NULL,'原语概念模糊','2026-05-27 10:00:00','2026-09-16 09:52:15',67.00,71.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(139,1,4,22,'邱琳紫','计科2306班',95.00,1,'2',NULL,'思维敏捷，解题能力强','2026-05-27 10:00:00','2026-06-27 09:58:51',96.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(140,2,4,22,'邱琳紫','计科2306班',92.00,1,'2',NULL,'口语表达流畅','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,93.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(141,3,4,22,'邱琳紫','计科2306班',88.00,2,'2',NULL,'数制转换准确','2026-05-27 10:00:00','2026-09-16 09:52:14',80.00,94.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(142,4,4,22,'邱琳紫','计科2306班',90.00,1,'2',NULL,'算法掌握扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(143,5,4,22,'邱琳紫','计科2306班',93.00,1,'2',NULL,'组网实验能力强，配置规范','2026-05-27 10:00:00','2026-09-16 09:52:14',92.00,101.00,97.00,'A',4.00,NULL,NULL,NULL,NULL),(144,6,4,22,'邱琳紫','计科2306班',91.00,1,'2',NULL,'理论理解深入','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(145,1,5,22,'宋新鹏','计科2306班',91.00,1,'2',NULL,'学习积极主动','2026-05-27 10:00:00','2026-06-27 09:58:51',98.00,90.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(146,2,5,22,'宋新鹏','计科2306班',89.00,2,'2',NULL,'阅读能力较强','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,82.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(147,3,5,22,'宋新鹏','计科2306班',85.00,2,'2',NULL,'计算机体系结构理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(148,4,5,22,'宋新鹏','计科2306班',92.00,1,'2',NULL,'项目完成出色','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(149,5,5,22,'宋新鹏','计科2306班',88.00,2,'2',NULL,'网络拓扑设计合理','2026-05-27 10:00:00','2026-09-16 09:52:15',90.00,85.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(150,6,5,22,'宋新鹏','计科2306班',90.00,1,'2',NULL,'实验报告完整','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,91.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(151,1,6,22,'赵琦','计科2306班',85.00,2,'2',NULL,'基础扎实，解题规范','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(152,2,6,22,'赵琦','计科2306班',82.00,2,'2',NULL,'听力需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(153,3,6,22,'赵琦','计科2306班',78.00,3,'2',NULL,'部分概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(154,4,6,22,'赵琦','计科2306班',84.00,2,'2',NULL,'递归理解较好','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,78.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(155,5,6,22,'赵琦','计科2306班',86.00,2,'2',NULL,'网络协议掌握良好','2026-05-27 10:00:00','2026-09-16 09:52:15',78.00,92.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(156,6,6,22,'赵琦','计科2306班',80.00,2,'2',NULL,'需加强实践','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,87.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(157,1,7,22,'苏红阳','计科2306班',82.00,2,'2',NULL,'课堂参与积极','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(158,2,7,22,'苏红阳','计科2306班',88.00,2,'2',NULL,'写作能力突出','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(159,3,7,22,'苏红阳','计科2306班',80.00,2,'2',NULL,'操作步骤完整','2026-05-27 10:00:00','2026-09-16 09:52:14',82.00,85.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(160,4,7,22,'苏红阳','计科2306班',83.00,2,'2',NULL,'树结构掌握较好','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,78.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(161,5,7,22,'苏红阳','计科2306班',85.00,2,'2',NULL,'多路复用理解深入','2026-05-27 10:00:00','2026-09-16 09:52:14',83.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(162,6,7,22,'苏红阳','计科2306班',87.00,2,'2',NULL,'进程调度理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',90.00,83.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(163,1,8,22,'唐浩然','计科2306班',88.00,2,'2',NULL,'解题思路清晰','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,84.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(164,2,8,22,'唐浩然','计科2306班',84.00,2,'2',NULL,'词汇量丰富','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(165,3,8,22,'唐浩然','计科2306班',86.00,2,'2',NULL,'办公软件操作熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',78.00,86.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(166,4,8,22,'唐浩然','计科2306班',81.00,2,'2',NULL,'图算法待加强','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(167,5,8,22,'唐浩然','计科2306班',82.00,2,'2',NULL,'网络命令熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',84.00,83.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(168,6,8,22,'唐浩然','计科2306班',85.00,2,'2',NULL,'进程管理理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',91.00,91.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(169,1,9,22,'王宏民','计科2306班',75.00,3,'2',NULL,'计算能力一般','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(170,2,9,22,'王宏民','计科2306班',78.00,3,'2',NULL,'语法基础尚可','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,85.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(171,3,9,22,'王宏民','计科2306班',72.00,3,'2',NULL,'数制概念需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',67.00,79.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(172,4,9,22,'王宏民','计科2306班',76.00,3,'2',NULL,'排序算法掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,84.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(173,5,9,22,'王宏民','计科2306班',74.00,3,'2',NULL,'差错控制需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',68.00,69.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(174,6,9,22,'王宏民','计科2306班',77.00,3,'2',NULL,'内存管理理解一般','2026-05-27 10:00:00','2026-09-16 09:52:15',79.00,79.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(175,1,10,22,'王玉蛟','计科2306班',83.00,2,'2',NULL,'学习态度认真','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,75.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(176,2,10,22,'王玉蛟','计科2306班',85.00,2,'2',NULL,'翻译能力较强','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(177,3,10,22,'王玉蛟','计科2306班',79.00,3,'2',NULL,'二进制运算需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',85.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(178,4,10,22,'王玉蛟','计科2306班',87.00,2,'2',NULL,'图论部分表现好','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,81.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(179,5,10,22,'王玉蛟','计科2306班',84.00,2,'2',NULL,'组网方案有创意','2026-05-27 10:00:00','2026-09-16 09:52:14',82.00,86.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(180,6,10,22,'王玉蛟','计科2306班',82.00,2,'2',NULL,'设备管理理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',77.00,76.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(181,1,11,22,'吴雨航','计科2306班',72.00,3,'2',NULL,'积分计算有困难','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,71.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(182,2,11,22,'吴雨航','计科2306班',76.00,3,'2',NULL,'词汇量不足','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,82.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(183,3,11,22,'吴雨航','计科2306班',74.00,3,'2',NULL,'需要多练习','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,66.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(184,4,11,22,'吴雨航','计科2306班',71.00,3,'2',NULL,'链表操作不熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,72.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(185,5,11,22,'吴雨航','计科2306班',79.00,3,'2',NULL,'网络基础过关','2026-05-27 10:00:00','2026-09-16 09:52:14',79.00,83.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(186,6,11,22,'吴雨航','计科2306班',73.00,3,'2',NULL,'文件系统需复习','2026-05-27 10:00:00','2026-09-16 09:52:15',69.00,77.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(187,1,12,22,'徐瑞瑶','计科2306班',87.00,2,'2',NULL,'数学思维好','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,80.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(188,2,12,22,'徐瑞瑶','计科2306班',83.00,2,'2',NULL,'听力理解优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,83.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(189,3,12,22,'徐瑞瑶','计科2306班',89.00,2,'2',NULL,'存储体系理解深入','2026-05-27 10:00:00','2026-09-16 09:52:14',89.00,86.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(190,4,12,22,'徐瑞瑶','计科2306班',85.00,2,'2',NULL,'哈希表掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,84.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(191,5,12,22,'徐瑞瑶','计科2306班',86.00,2,'2',NULL,'分层模型理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(192,6,12,22,'徐瑞瑶','计科2306班',84.00,2,'2',NULL,'并发控制理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',90.00,86.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(193,1,13,23,'韩坤','计科2306班',68.00,4,'2',NULL,'基础薄弱需加强','2026-05-27 10:00:00','2026-09-16 09:36:43',61.00,75.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(194,2,13,23,'韩坤','计科2306班',72.00,3,'2',NULL,'语法错误较多','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,79.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(195,3,13,23,'韩坤','计科2306班',70.00,3,'2',NULL,'进制换算常出错','2026-05-27 10:00:00','2026-09-16 09:52:14',76.00,70.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(196,4,13,23,'韩坤','计科2306班',65.00,4,'2',NULL,'栈和队列混淆','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,70.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(197,5,13,23,'韩坤','计科2306班',75.00,3,'2',NULL,'路由算法需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',82.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(198,6,13,23,'韩坤','计科2306班',71.00,3,'2',NULL,'基本概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,73.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(199,1,14,23,'张博巍','计科2306班',93.00,1,'2',NULL,'微积分掌握扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',100.00,100.00,100.00,'A',4.00,NULL,NULL,NULL,NULL),(200,2,14,23,'张博巍','计科2306班',90.00,1,'2',NULL,'口语发音标准','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,87.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(201,3,14,23,'张博巍','计科2306班',88.00,2,'2',NULL,'计算机组成理解好','2026-05-27 10:00:00','2026-09-16 09:52:14',91.00,83.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(202,4,14,23,'张博巍','计科2306班',91.00,1,'2',NULL,'动态规划掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,87.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(203,5,14,23,'张博巍','计科2306班',94.00,1,'2',NULL,'组网实践能力强','2026-05-27 10:00:00','2026-09-16 09:52:15',94.00,96.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(204,6,14,23,'张博巍','计科2306班',89.00,2,'2',NULL,'内核结构掌握好','2026-05-27 10:00:00','2026-09-16 09:52:15',93.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(205,1,15,23,'程启頔','计科2306班',70.00,3,'2',NULL,'上课注意力不集中','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,62.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(206,2,15,23,'程启頔','计科2306班',74.00,3,'2',NULL,'听说能力待提高','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,75.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(207,3,15,23,'程启頔','计科2306班',68.00,4,'2',NULL,'算法流程图困难','2026-05-27 10:00:00','2026-09-16 09:52:14',60.00,75.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(208,4,15,23,'程启頔','计科2306班',72.00,3,'2',NULL,'查找算法需练习','2026-05-27 10:00:00','2026-06-27 09:58:51',77.00,78.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(209,5,15,23,'程启頔','计科2306班',76.00,3,'2',NULL,'子网划分基本正确','2026-05-27 10:00:00','2026-09-16 09:52:14',74.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(210,6,15,23,'程启頔','计科2306班',70.00,3,'2',NULL,'系统调用需加强','2026-05-27 10:00:00','2026-09-16 09:52:15',71.00,66.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(211,1,16,23,'邓雨轩','计科2306班',84.00,2,'2',NULL,'数列极限理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,86.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(212,2,16,23,'邓雨轩','计科2306班',86.00,2,'2',NULL,'作文表达流畅','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,87.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(213,3,16,23,'邓雨轩','计科2306班',80.00,2,'2',NULL,'文件管理操作准确','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(214,4,16,23,'邓雨轩','计科2306班',82.00,2,'2',NULL,'二叉树遍历掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,87.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(215,5,16,23,'邓雨轩','计科2306班',85.00,2,'2',NULL,'接口配置规范','2026-05-27 10:00:00','2026-09-16 09:52:14',91.00,84.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(216,6,16,23,'邓雨轩','计科2306班',83.00,2,'2',NULL,'信号量机制理解好','2026-05-27 10:00:00','2026-09-16 09:52:15',78.00,90.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(217,1,17,23,'高菲','计科2306班',86.00,2,'2',NULL,'导数应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,89.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(218,2,17,23,'高菲','计科2306班',88.00,2,'2',NULL,'阅读理解准确','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,87.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(219,3,17,23,'高菲','计科2306班',82.00,2,'2',NULL,'表格数据处理掌握','2026-05-27 10:00:00','2026-09-16 09:52:14',84.00,76.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(220,4,17,23,'高菲','计科2306班',84.00,2,'2',NULL,'B树B+树理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(221,5,17,23,'高菲','计科2306班',87.00,2,'2',NULL,'数据流分析熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',93.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(222,6,17,23,'高菲','计科2306班',81.00,2,'2',NULL,'页面置换算法理解','2026-05-27 10:00:00','2026-09-16 09:52:15',86.00,87.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(223,1,18,23,'胡钰','计科2306班',78.00,3,'2',NULL,'不定积分有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(224,2,18,23,'胡钰','计科2306班',80.00,2,'2',NULL,'语法有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,80.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(225,3,18,23,'胡钰','计科2306班',72.00,3,'2',NULL,'硬件组成理解不足','2026-05-27 10:00:00','2026-09-16 09:52:14',74.00,68.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(226,4,18,23,'胡钰','计科2306班',75.00,3,'2',NULL,'代码实现有困难','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,76.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(227,5,18,23,'胡钰','计科2306班',79.00,3,'2',NULL,'Socket编程需加强','2026-05-27 10:00:00','2026-09-16 09:52:14',78.00,74.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(228,6,18,23,'胡钰','计科2306班',74.00,3,'2',NULL,'缓冲区管理概念模糊','2026-05-27 10:00:00','2026-09-16 09:52:15',69.00,73.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(229,1,19,23,'李佳芮','计科2306班',92.00,1,'2',NULL,'空间解析几何好','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(230,2,19,23,'李佳芮','计科2306班',89.00,2,'2',NULL,'演讲能力出色','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,87.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(231,3,19,23,'李佳芮','计科2306班',93.00,1,'2',NULL,'网络基础知识理解深','2026-05-27 10:00:00','2026-09-16 09:52:14',96.00,86.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(232,4,19,23,'李佳芮','计科2306班',90.00,1,'2',NULL,'最短路径算法优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,92.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(233,5,19,23,'李佳芮','计科2306班',91.00,1,'2',NULL,'交换原理掌握好','2026-05-27 10:00:00','2026-09-16 09:52:14',94.00,97.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(234,6,19,23,'李佳芮','计科2306班',90.00,1,'2',NULL,'存储管理理解深','2026-05-27 10:00:00','2026-09-16 09:52:15',92.00,94.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(235,1,20,23,'李爽','计科2306班',66.00,4,'2',NULL,'基础不扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,69.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(236,2,20,23,'李爽','计科2306班',71.00,3,'2',NULL,'词汇积累不够','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(237,3,20,23,'李爽','计科2306班',69.00,4,'2',NULL,'故障判断困难','2026-05-27 10:00:00','2026-09-16 09:52:14',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(238,4,20,23,'李爽','计科2306班',70.00,3,'2',NULL,'遍历算法需复习','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,74.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(239,5,20,23,'李爽','计科2306班',68.00,4,'2',NULL,'协议层次混淆','2026-05-27 10:00:00','2026-09-16 09:52:14',69.00,74.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(240,6,20,23,'李爽','计科2306班',72.00,3,'2',NULL,'进程通信理解一般','2026-05-27 10:00:00','2026-09-16 09:52:15',77.00,75.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(241,1,21,23,'梁莹静','计科2306班',85.00,2,'2',NULL,'多元函数掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,79.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(242,2,21,23,'梁莹静','计科2306班',87.00,2,'2',NULL,'跨文化交际能力好','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,89.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(243,3,21,23,'梁莹静','计科2306班',81.00,2,'2',NULL,'操作系统概念清晰','2026-05-27 10:00:00','2026-09-16 09:52:14',80.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(244,4,21,23,'梁莹静','计科2306班',83.00,2,'2',NULL,'堆排序理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,82.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(245,5,21,23,'梁莹静','计科2306班',86.00,2,'2',NULL,'网线制作规范','2026-05-27 10:00:00','2026-09-16 09:52:14',89.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(246,6,21,23,'梁莹静','计科2306班',84.00,2,'2',NULL,'进程同步掌握好','2026-05-27 10:00:00','2026-09-16 09:52:15',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(247,1,22,23,'孟阳','计科2306班',73.00,3,'2',NULL,'函数连续性需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(248,2,22,23,'孟阳','计科2306班',75.00,3,'2',NULL,'长难句理解困难','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,73.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(249,3,22,23,'孟阳','计科2306班',71.00,3,'2',NULL,'数据编码理解不足','2026-05-27 10:00:00','2026-09-16 09:52:14',73.00,72.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(250,4,22,23,'孟阳','计科2306班',77.00,3,'2',NULL,'快速排序基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(251,5,22,23,'孟阳','计科2306班',74.00,3,'2',NULL,'抓包分析有进步','2026-05-27 10:00:00','2026-09-16 09:52:14',81.00,67.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(252,6,22,23,'孟阳','计科2306班',76.00,3,'2',NULL,'调度算法理解','2026-05-27 10:00:00','2026-09-16 09:52:15',77.00,74.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(253,1,23,23,'王瑾泰','计科2306班',82.00,2,'2',NULL,'定积分应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,78.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(254,2,23,23,'王瑾泰','计科2306班',84.00,2,'2',NULL,'完形填空准确','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(255,3,23,23,'王瑾泰','计科2306班',78.00,3,'2',NULL,'信息安全意识需巩固','2026-05-27 10:00:00','2026-09-16 09:52:14',71.00,84.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(256,4,23,23,'王瑾泰','计科2306班',85.00,2,'2',NULL,'AVL树掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,77.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(257,5,23,28,'王瑾泰','计科2306班',83.00,2,'2',NULL,'IP地址规划熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',88.00,85.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(258,6,23,28,'王瑾泰','计科2306班',80.00,2,'2',NULL,'虚拟内存理解','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(259,1,24,28,'王平洋','计科2306班',74.00,3,'2',NULL,'级数概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,78.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(260,2,24,28,'王平洋','计科2306班',73.00,3,'2',NULL,'翻译题薄弱','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(261,3,24,28,'王平洋','计科2306班',76.00,3,'2',NULL,'数据库基础理解一般','2026-05-27 10:00:00','2026-09-16 09:52:14',77.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(262,4,24,28,'王平洋','计科2306班',79.00,3,'2',NULL,'并查集基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,77.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(263,5,24,28,'王平洋','计科2306班',71.00,3,'2',NULL,'子网掩码计算生疏','2026-05-27 10:00:00','2026-09-16 09:52:14',71.00,78.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(264,6,24,28,'王平洋','计科2306班',75.00,3,'2',NULL,'死锁处理需复习','2026-05-27 10:00:00','2026-09-16 09:52:15',74.00,72.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(265,1,25,28,'夏志浩','计科2306班',94.00,1,'2',NULL,'数学基础雄厚','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,98.00,98.00,'A',4.00,NULL,NULL,NULL,NULL),(266,2,25,28,'夏志浩','计科2306班',91.00,1,'2',NULL,'学术写作优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,93.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(267,3,25,28,'夏志浩','计科2306班',87.00,2,'2',NULL,'动手操作能力强','2026-05-27 10:00:00','2026-09-16 09:52:14',92.00,84.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(268,4,25,28,'夏志浩','计科2306班',95.00,1,'2',NULL,'算法竞赛获奖','2026-05-27 10:00:00','2026-06-27 09:58:51',100.00,96.00,98.00,'A',4.00,NULL,NULL,NULL,NULL),(269,5,25,28,'夏志浩','计科2306班',92.00,1,'2',NULL,'精通网络排障','2026-05-27 10:00:00','2026-09-16 09:52:14',86.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(270,6,25,28,'夏志浩','计科2306班',88.00,2,'2',NULL,'多线程调度理解深','2026-05-27 10:00:00','2026-09-16 09:52:15',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(271,1,26,28,'张健','计科2306班',64.00,4,'2',NULL,'多处知识漏洞','2026-05-27 10:00:00','2026-06-27 09:58:51',62.00,69.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(272,2,26,28,'张健','计科2306班',69.00,4,'2',NULL,'听力严重薄弱','2026-05-27 10:00:00','2026-09-16 09:36:43',73.00,67.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(273,3,26,28,'张健','计科2306班',62.00,4,'2',NULL,'快捷键使用不熟练','2026-05-27 10:00:00','2026-09-16 09:52:14',59.00,70.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(274,4,26,28,'张健','计科2306班',68.00,4,'2',NULL,'基本操作不熟练','2026-05-27 10:00:00','2026-09-16 09:36:43',66.00,61.00,63.00,'D',1.30,NULL,NULL,NULL,NULL),(275,5,26,28,'张健','计科2306班',65.00,4,'2',NULL,'网络分层理解不足','2026-05-27 10:00:00','2026-09-16 09:52:15',57.00,72.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(276,6,26,28,'张健','计科2306班',71.00,3,'2',NULL,'原语概念模糊','2026-05-27 10:00:00','2026-09-16 09:52:15',70.00,74.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(277,1,3,28,'司明','计科2306班',87.00,2,'3',NULL,'第3学期高等数学','2024-06-20 00:00:00','2026-09-16 09:36:43',84.00,92.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(278,2,3,28,'司明','计科2306班',80.50,2,'3',NULL,'第3学期大学英语','2024-06-20 00:00:00','2026-09-16 09:36:43',82.00,76.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(279,3,3,28,'司明','计科2306班',70.00,3,'3',NULL,'第3学期计算机基础','2024-06-20 00:00:00','2026-09-16 09:36:43',65.00,68.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(280,4,3,28,'司明','计科2306班',91.00,1,'3',NULL,'第3学期数据结构优秀','2024-06-20 00:00:00','2026-09-16 09:36:43',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(281,5,3,28,'司明','计科2306班',89.00,2,'3',NULL,'第3学期计算机网络','2024-06-20 00:00:00','2026-09-16 09:36:43',81.00,95.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(282,6,3,28,'司明','计科2306班',62.00,4,'3',NULL,'第3学期操作系统','2024-06-20 00:00:00','2026-09-16 09:36:43',55.00,67.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(283,1,3,28,'司明','计科2306班',71.50,3,'4',NULL,'第4学期高等数学下滑','2025-01-15 00:00:00','2026-09-16 09:36:43',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(284,2,3,28,'司明','计科2306班',51.00,5,'4',NULL,'第4学期大学英语不及格','2025-01-15 00:00:00','2026-09-16 09:36:43',48.00,56.00,53.00,'F',0.30,NULL,NULL,NULL,NULL),(285,3,3,28,'司明','计科2306班',59.00,5,'4',NULL,'第4学期计算机基础不及格','2025-01-15 00:00:00','2026-09-16 09:36:43',58.00,53.00,55.00,'F',0.50,NULL,NULL,NULL,NULL),(286,4,3,28,'司明','计科2306班',84.00,2,'4',NULL,'第4学期数据结构','2025-01-15 00:00:00','2026-09-16 09:36:43',83.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(287,6,3,28,'司明','计科2306班',40.00,5,'4',NULL,'第4学期操作系统严重不及格','2025-01-15 00:00:00','2026-09-16 09:36:43',34.00,39.00,37.00,'F',0.00,NULL,NULL,NULL,NULL),(288,1,3,28,'司明','计科2306班',56.50,5,'5',NULL,'第5学期高等数学持续下滑','2025-06-20 00:00:00','2026-09-16 09:36:43',52.00,54.00,53.00,'F',0.30,NULL,NULL,NULL,NULL),(289,5,3,28,'司明','计科2306班',65.00,4,'5',NULL,'第5学期计算机网络勉强及格','2025-06-20 00:00:00','2026-09-16 09:36:43',67.00,58.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(290,3,3,28,'司明','计科2306班',47.00,5,'5',NULL,'第5学期计算机基础补考仍不及格','2025-06-20 00:00:00','2026-09-16 09:36:43',44.00,53.00,49.00,'F',0.00,NULL,NULL,NULL,NULL),(291,7,3,28,'司明','计科2306班',85.00,2,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',87.00,86.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(294,8,3,28,'司明','计科2306班',78.00,3,'6',NULL,NULL,'2026-06-25 09:58:16','2026-09-16 09:36:43',70.00,77.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(295,8,4,28,'邱琳紫','计科2306班',88.00,2,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',81.00,84.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(296,7,4,28,'邱琳紫','计科2306班',90.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(297,7,5,28,'宋新鹏','计科2306班',88.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,83.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(298,7,6,28,'赵琦','计科2306班',85.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,81.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(299,7,7,28,'苏红阳','计科2306班',92.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,94.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(300,7,8,28,'唐浩然','计科2306班',80.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,73.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(301,7,9,28,'王宏民','计科2306班',78.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,82.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(302,7,10,28,'王玉蛟','计科2306班',86.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(303,7,11,28,'吴雨航','计科2306班',75.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,79.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(304,7,12,28,'徐瑞瑶','计科2306班',88.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,93.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(305,7,13,28,'韩坤','计科2306班',72.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(306,7,14,28,'张博巍','计科2306班',94.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',98.00,86.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(307,7,15,28,'程启頔','计科2306班',70.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',65.00,75.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(308,7,16,28,'邓雨轩','计科2306班',83.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,82.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(309,7,17,28,'高菲','计科2306班',89.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(310,7,18,28,'胡钰','计科2306班',76.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,82.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(311,7,19,28,'李佳芮','计科2306班',91.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',96.00,93.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(312,7,20,28,'李爽','计科2306班',68.00,4,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,74.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(313,7,21,28,'梁莹静','计科2306班',87.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,94.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(314,7,22,28,'孟阳','计科2306班',74.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,68.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(315,7,23,28,'王瑾泰','计科2306班',82.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(316,7,24,28,'王平洋','计科2306班',73.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,69.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(317,7,25,28,'夏志浩','计科2306班',95.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,92.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(318,7,26,28,'张健','计科2306班',65.00,4,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,67.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(319,8,4,28,'邱琳紫','计科2306班',88.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(320,8,5,28,'宋新鹏','计科2306班',90.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,83.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(321,8,6,18,'赵琦','计科2306班',82.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,90.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(322,8,7,18,'苏红阳','计科2306班',86.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,89.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(323,8,8,18,'唐浩然','计科2306班',84.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,87.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(324,8,9,18,'王宏民','计科2306班',76.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,80.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(325,8,10,18,'王玉蛟','计科2306班',83.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,82.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(326,8,11,18,'吴雨航','计科2306班',74.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(327,8,12,18,'徐瑞瑶','计科2306班',85.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,85.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(328,8,13,18,'韩坤','计科2306班',70.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,71.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(329,8,14,18,'张博巍','计科2306班',92.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,97.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(330,8,15,18,'程启頔','计科2306班',68.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-09-16 09:36:43',70.00,61.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(331,8,16,18,'邓雨轩','计科2306班',81.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(332,8,17,18,'高菲','计科2306班',87.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,86.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(333,8,18,18,'胡钰','计科2306班',78.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,84.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(334,8,19,18,'李佳芮','计科2306班',93.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(335,8,20,18,'李爽','计科2306班',66.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(336,8,21,18,'梁莹静','计科2306班',84.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(337,8,22,18,'孟阳','计科2306班',72.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,76.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(338,8,23,18,'王瑾泰','计科2306班',80.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(339,8,24,18,'王平洋','计科2306班',71.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,75.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(340,8,25,18,'夏志浩','计科2306班',96.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(341,8,26,18,'张健','计科2306班',63.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',58.00,62.00,60.00,'D',1.00,NULL,NULL,NULL,NULL),(342,9,4,18,'邱琳紫','计科2306班',85.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,77.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(343,9,5,18,'宋新鹏','计科2306班',87.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,80.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(344,9,6,18,'赵琦','计科2306班',83.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(345,9,7,18,'苏红阳','计科2306班',90.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(346,9,8,18,'唐浩然','计科2306班',79.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,81.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(347,9,9,18,'王宏民','计科2306班',74.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,73.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(348,9,10,18,'王玉蛟','计科2306班',88.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,83.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(349,9,11,18,'吴雨航','计科2306班',72.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,79.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(350,9,12,18,'徐瑞瑶','计科2306班',86.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(351,9,13,18,'韩坤','计科2306班',68.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-09-16 09:36:43',72.00,61.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(352,9,14,18,'张博巍','计科2306班',89.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(353,9,15,18,'程启頔','计科2306班',66.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(354,9,16,18,'邓雨轩','计科2306班',80.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,84.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(355,9,17,18,'高菲','计科2306班',91.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,91.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(356,9,18,18,'胡钰','计科2306班',75.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,68.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(357,9,19,18,'李佳芮','计科2306班',94.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,90.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(358,9,20,18,'李爽','计科2306班',64.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,62.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(359,9,21,18,'梁莹静','计科2306班',82.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(360,9,22,18,'孟阳','计科2306班',70.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',62.00,67.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(361,9,23,18,'王瑾泰','计科2306班',78.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,76.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(362,9,24,18,'王平洋','计科2306班',69.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-09-16 09:36:43',77.00,67.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(363,9,25,18,'夏志浩','计科2306班',92.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,96.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(364,9,26,18,'张健','计科2306班',61.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,54.00,56.00,'F',0.60,NULL,NULL,NULL,NULL),(365,10,4,18,'邱琳紫','计科2306班',92.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(366,10,5,18,'宋新鹏','计科2306班',86.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,82.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(367,10,6,18,'赵琦','计科2306班',84.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,77.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(368,10,7,18,'苏红阳','计科2306班',88.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(369,10,8,18,'唐浩然','计科2306班',81.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,81.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(370,10,9,18,'王宏民','计科2306班',77.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,71.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(371,10,10,18,'王玉蛟','计科2306班',90.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,84.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(372,10,11,18,'吴雨航','计科2306班',73.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,72.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(373,10,12,18,'徐瑞瑶','计科2306班',87.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(374,10,13,18,'韩坤','计科2306班',69.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-09-16 09:36:43',75.00,62.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(375,10,14,18,'张博巍','计科2306班',91.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(376,10,15,18,'程启頔','计科2306班',67.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-09-16 09:36:43',60.00,64.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(377,10,16,18,'邓雨轩','计科2306班',82.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,80.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(378,10,17,18,'高菲','计科2306班',89.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,82.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(379,10,18,18,'胡钰','计科2306班',77.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,69.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(380,10,19,18,'李佳芮','计科2306班',90.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(381,10,20,18,'李爽','计科2306班',65.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,65.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(382,10,21,18,'梁莹静','计科2306班',85.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(383,10,22,18,'孟阳','计科2306班',71.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,71.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(384,10,23,18,'王瑾泰','计科2306班',79.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(385,10,24,29,'王平洋','计科2306班',70.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',62.00,73.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(386,10,25,29,'夏志浩','计科2306班',93.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',86.00,101.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(387,10,26,29,'张健','计科2306班',62.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',57.00,69.00,64.00,'D',1.40,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `score_record_bak_dup_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_record_bak_prefix7_20260916`
--

DROP TABLE IF EXISTS `score_record_bak_prefix7_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_record_bak_prefix7_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `course_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `score` decimal(5,2) DEFAULT NULL,
  `score_level` int DEFAULT NULL,
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学期',
  `year` int DEFAULT NULL COMMENT '学年',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) DEFAULT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(3,2) DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_record_bak_prefix7_20260916`
--

LOCK TABLES `score_record_bak_prefix7_20260916` WRITE;
/*!40000 ALTER TABLE `score_record_bak_prefix7_20260916` DISABLE KEYS */;
INSERT INTO `score_record_bak_prefix7_20260916` VALUES (295,8,4,28,'邱琳紫','计科2306班',88.00,2,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',81.00,84.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(296,7,4,28,'邱琳紫','计科2306班',90.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(297,7,5,28,'宋新鹏','计科2306班',88.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,83.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(298,7,6,28,'赵琦','计科2306班',85.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,81.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(299,7,7,28,'苏红阳','计科2306班',92.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,94.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(300,7,8,28,'唐浩然','计科2306班',80.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,73.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(301,7,9,28,'王宏民','计科2306班',78.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,82.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(302,7,10,28,'王玉蛟','计科2306班',86.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(303,7,11,28,'吴雨航','计科2306班',75.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,79.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(304,7,12,28,'徐瑞瑶','计科2306班',88.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,93.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(305,7,13,28,'韩坤','计科2306班',72.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(306,7,14,28,'张博巍','计科2306班',94.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',98.00,86.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(307,7,15,28,'程启頔','计科2306班',70.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',65.00,75.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(308,7,16,28,'邓雨轩','计科2306班',83.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,82.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(309,7,17,28,'高菲','计科2306班',89.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(310,7,18,28,'胡钰','计科2306班',76.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,82.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(311,7,19,28,'李佳芮','计科2306班',91.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',96.00,93.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(312,7,20,28,'李爽','计科2306班',68.00,4,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,74.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(313,7,21,28,'梁莹静','计科2306班',87.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,94.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(314,7,22,28,'孟阳','计科2306班',74.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,68.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(315,7,23,28,'王瑾泰','计科2306班',82.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(316,7,24,28,'王平洋','计科2306班',73.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,69.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(317,7,25,28,'夏志浩','计科2306班',95.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,92.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(318,7,26,28,'张健','计科2306班',65.00,4,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,67.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(319,8,4,28,'邱琳紫','计科2306班',88.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(320,8,5,28,'宋新鹏','计科2306班',90.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,83.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(321,8,6,18,'赵琦','计科2306班',82.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,90.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(322,8,7,18,'苏红阳','计科2306班',86.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,89.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(323,8,8,18,'唐浩然','计科2306班',84.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,87.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(324,8,9,18,'王宏民','计科2306班',76.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,80.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(325,8,10,18,'王玉蛟','计科2306班',83.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,82.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(326,8,11,18,'吴雨航','计科2306班',74.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(327,8,12,18,'徐瑞瑶','计科2306班',85.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,85.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(328,8,13,18,'韩坤','计科2306班',70.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,71.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(329,8,14,18,'张博巍','计科2306班',92.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,97.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(330,8,15,18,'程启頔','计科2306班',68.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-09-16 09:36:43',70.00,61.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(331,8,16,18,'邓雨轩','计科2306班',81.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(332,8,17,18,'高菲','计科2306班',87.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,86.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(333,8,18,18,'胡钰','计科2306班',78.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,84.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(334,8,19,18,'李佳芮','计科2306班',93.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(335,8,20,18,'李爽','计科2306班',66.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(336,8,21,18,'梁莹静','计科2306班',84.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(337,8,22,18,'孟阳','计科2306班',72.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,76.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(338,8,23,18,'王瑾泰','计科2306班',80.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(339,8,24,18,'王平洋','计科2306班',71.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,75.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(340,8,25,18,'夏志浩','计科2306班',96.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(341,8,26,18,'张健','计科2306班',63.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',58.00,62.00,60.00,'D',1.00,NULL,NULL,NULL,NULL),(342,9,4,18,'邱琳紫','计科2306班',85.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,77.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(343,9,5,18,'宋新鹏','计科2306班',87.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,80.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(344,9,6,18,'赵琦','计科2306班',83.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(345,9,7,18,'苏红阳','计科2306班',90.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(346,9,8,18,'唐浩然','计科2306班',79.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,81.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(347,9,9,18,'王宏民','计科2306班',74.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,73.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(348,9,10,18,'王玉蛟','计科2306班',88.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,83.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(349,9,11,18,'吴雨航','计科2306班',72.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,79.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(350,9,12,18,'徐瑞瑶','计科2306班',86.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(351,9,13,18,'韩坤','计科2306班',68.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-09-16 09:36:43',72.00,61.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(352,9,14,18,'张博巍','计科2306班',89.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(353,9,15,18,'程启頔','计科2306班',66.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(354,9,16,18,'邓雨轩','计科2306班',80.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,84.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(355,9,17,18,'高菲','计科2306班',91.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,91.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(356,9,18,18,'胡钰','计科2306班',75.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,68.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(357,9,19,18,'李佳芮','计科2306班',94.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,90.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(358,9,20,18,'李爽','计科2306班',64.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,62.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(359,9,21,18,'梁莹静','计科2306班',82.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(360,9,22,18,'孟阳','计科2306班',70.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',62.00,67.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(361,9,23,18,'王瑾泰','计科2306班',78.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,76.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(362,9,24,18,'王平洋','计科2306班',69.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-09-16 09:36:43',77.00,67.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(363,9,25,18,'夏志浩','计科2306班',92.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,96.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(364,9,26,18,'张健','计科2306班',61.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,54.00,56.00,'F',0.60,NULL,NULL,NULL,NULL),(365,10,4,18,'邱琳紫','计科2306班',92.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(366,10,5,18,'宋新鹏','计科2306班',86.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,82.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(367,10,6,18,'赵琦','计科2306班',84.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,77.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(368,10,7,18,'苏红阳','计科2306班',88.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(369,10,8,18,'唐浩然','计科2306班',81.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,81.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(370,10,9,18,'王宏民','计科2306班',77.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,71.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(371,10,10,18,'王玉蛟','计科2306班',90.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,84.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(372,10,11,18,'吴雨航','计科2306班',73.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,72.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(373,10,12,18,'徐瑞瑶','计科2306班',87.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(374,10,13,18,'韩坤','计科2306班',69.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-09-16 09:36:43',75.00,62.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(375,10,14,18,'张博巍','计科2306班',91.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(376,10,15,18,'程启頔','计科2306班',67.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-09-16 09:36:43',60.00,64.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(377,10,16,18,'邓雨轩','计科2306班',82.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,80.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(378,10,17,18,'高菲','计科2306班',89.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,82.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(379,10,18,18,'胡钰','计科2306班',77.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,69.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(380,10,19,18,'李佳芮','计科2306班',90.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(381,10,20,18,'李爽','计科2306班',65.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,65.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(382,10,21,18,'梁莹静','计科2306班',85.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(383,10,22,18,'孟阳','计科2306班',71.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,71.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(384,10,23,18,'王瑾泰','计科2306班',79.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(385,10,24,29,'王平洋','计科2306班',70.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',62.00,73.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(386,10,25,29,'夏志浩','计科2306班',93.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',86.00,101.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(387,10,26,29,'张健','计科2306班',62.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',57.00,69.00,64.00,'D',1.40,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `score_record_bak_prefix7_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_record_bak_sem9_20260916`
--

DROP TABLE IF EXISTS `score_record_bak_sem9_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_record_bak_sem9_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `course_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `student_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `score` decimal(5,2) DEFAULT NULL,
  `score_level` int DEFAULT NULL,
  `semester` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学期',
  `year` int DEFAULT NULL COMMENT '学年',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) DEFAULT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(3,2) DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_record_bak_sem9_20260916`
--

LOCK TABLES `score_record_bak_sem9_20260916` WRITE;
/*!40000 ALTER TABLE `score_record_bak_sem9_20260916` DISABLE KEYS */;
INSERT INTO `score_record_bak_sem9_20260916` VALUES (139,1,4,22,'邱琳紫','计科2306班',94.00,1,'2',NULL,'思维敏捷，解题能力强','2026-05-27 10:00:00','2026-09-16 10:27:12',92.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(140,2,4,22,'邱琳紫','计科2306班',89.00,2,'2',NULL,'口语表达流畅','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(141,3,4,22,'邱琳紫','计科2306班',87.00,2,'2',NULL,'数制转换准确','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(142,4,4,22,'邱琳紫','计科2306班',80.00,2,'2',NULL,'算法掌握扎实','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(143,5,4,22,'邱琳紫','计科2306班',91.00,1,'2',NULL,'组网实验能力强，配置规范','2026-05-27 10:00:00','2026-09-16 10:27:12',90.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(144,6,4,22,'邱琳紫','计科2306班',84.00,2,'2',NULL,'理论理解深入','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(145,1,5,22,'宋新鹏','计科2306班',90.00,1,'2',NULL,'学习积极主动','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(146,2,5,22,'宋新鹏','计科2306班',87.00,2,'2',NULL,'阅读能力较强','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(147,3,5,22,'宋新鹏','计科2306班',84.00,2,'2',NULL,'计算机体系结构理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(148,4,5,22,'宋新鹏','计科2306班',80.00,2,'2',NULL,'项目完成出色','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(149,5,5,22,'宋新鹏','计科2306班',92.00,1,'2',NULL,'网络拓扑设计合理','2026-05-27 10:00:00','2026-09-16 10:27:12',90.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(150,6,5,22,'宋新鹏','计科2306班',82.00,2,'2',NULL,'实验报告完整','2026-05-27 10:00:00','2026-09-16 10:27:12',80.00,84.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(151,1,6,22,'赵琦','计科2306班',79.00,3,'2',NULL,'基础扎实，解题规范','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(152,2,6,22,'赵琦','计科2306班',83.00,2,'2',NULL,'听力需加强','2026-05-27 10:00:00','2026-09-16 10:27:12',80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(153,3,6,22,'赵琦','计科2306班',74.00,3,'2',NULL,'部分概念模糊','2026-05-27 10:00:00','2026-09-16 10:27:12',70.00,78.00,74.00,'B',2.30,NULL,NULL,NULL,NULL),(154,4,6,22,'赵琦','计科2306班',87.00,2,'2',NULL,'递归理解较好','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(155,5,6,22,'赵琦','计科2306班',75.00,3,'2',NULL,'网络协议掌握良好','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(156,6,6,22,'赵琦','计科2306班',80.00,2,'2',NULL,'需加强实践','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(157,1,7,22,'苏红阳','计科2306班',84.00,2,'2',NULL,'课堂参与积极','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(158,2,7,22,'苏红阳','计科2306班',80.00,2,'2',NULL,'写作能力突出','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(159,3,7,22,'苏红阳','计科2306班',87.00,2,'2',NULL,'操作步骤完整','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(160,4,7,22,'苏红阳','计科2306班',90.00,1,'2',NULL,'树结构掌握较好','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(161,5,7,22,'苏红阳','计科2306班',78.00,3,'2',NULL,'多路复用理解深入','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(162,6,7,22,'苏红阳','计科2306班',75.00,3,'2',NULL,'进程调度理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(163,1,8,22,'唐浩然','计科2306班',88.00,2,'2',NULL,'解题思路清晰','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(164,2,8,22,'唐浩然','计科2306班',80.00,2,'2',NULL,'词汇量丰富','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(165,3,8,22,'唐浩然','计科2306班',75.00,3,'2',NULL,'办公软件操作熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(166,4,8,22,'唐浩然','计科2306班',84.00,2,'2',NULL,'图算法待加强','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(167,5,8,22,'唐浩然','计科2306班',90.00,1,'2',NULL,'网络命令熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(168,6,8,22,'唐浩然','计科2306班',81.00,2,'2',NULL,'进程管理理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(169,1,9,22,'王宏民','计科2306班',66.00,4,'2',NULL,'计算能力一般','2026-05-27 10:00:00','2026-09-16 10:27:12',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(170,2,9,22,'王宏民','计科2306班',72.00,3,'2',NULL,'语法基础尚可','2026-05-27 10:00:00','2026-09-16 10:27:12',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(171,3,9,22,'王宏民','计科2306班',69.00,4,'2',NULL,'数制概念需巩固','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(172,4,9,22,'王宏民','计科2306班',75.00,3,'2',NULL,'排序算法掌握','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(173,5,9,22,'王宏民','计科2306班',73.00,3,'2',NULL,'差错控制需加强','2026-05-27 10:00:00','2026-09-16 10:27:12',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(174,6,9,22,'王宏民','计科2306班',78.00,3,'2',NULL,'内存管理理解一般','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(175,1,10,22,'王玉蛟','计科2306班',81.00,2,'2',NULL,'学习态度认真','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(176,2,10,22,'王玉蛟','计科2306班',84.00,2,'2',NULL,'翻译能力较强','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(177,3,10,22,'王玉蛟','计科2306班',75.00,3,'2',NULL,'二进制运算需加强','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(178,4,10,22,'王玉蛟','计科2306班',89.00,2,'2',NULL,'图论部分表现好','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(179,5,10,22,'王玉蛟','计科2306班',78.00,3,'2',NULL,'组网方案有创意','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(180,6,10,22,'王玉蛟','计科2306班',80.00,2,'2',NULL,'设备管理理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(181,1,11,22,'吴雨航','计科2306班',64.00,4,'2',NULL,'积分计算有困难','2026-05-27 10:00:00','2026-09-16 10:27:12',60.00,68.00,64.00,'C',1.50,NULL,NULL,NULL,NULL),(182,2,11,22,'吴雨航','计科2306班',69.00,4,'2',NULL,'词汇量不足','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(183,3,11,22,'吴雨航','计科2306班',65.00,4,'2',NULL,'需要多练习','2026-05-27 10:00:00','2026-09-16 10:27:12',62.00,68.00,65.00,'C',1.50,NULL,NULL,NULL,NULL),(184,4,11,22,'吴雨航','计科2306班',72.00,3,'2',NULL,'链表操作不熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(185,5,11,22,'吴雨航','计科2306班',80.00,2,'2',NULL,'网络基础过关','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(186,6,11,22,'吴雨航','计科2306班',73.00,3,'2',NULL,'文件系统需复习','2026-05-27 10:00:00','2026-09-16 10:27:12',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(187,1,12,22,'徐瑞瑶','计科2306班',87.00,2,'2',NULL,'数学思维好','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(188,2,12,22,'徐瑞瑶','计科2306班',84.00,2,'2',NULL,'听力理解优秀','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(189,3,12,22,'徐瑞瑶','计科2306班',81.00,2,'2',NULL,'存储体系理解深入','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(190,4,12,22,'徐瑞瑶','计科2306班',90.00,1,'2',NULL,'哈希表掌握好','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(191,5,12,22,'徐瑞瑶','计科2306班',88.00,2,'2',NULL,'分层模型理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(192,6,12,22,'徐瑞瑶','计科2306班',82.00,2,'2',NULL,'并发控制理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,85.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(193,1,13,23,'韩坤','计科2306班',63.00,4,'2',NULL,'基础薄弱需加强','2026-05-27 10:00:00','2026-09-16 10:27:12',60.00,65.00,63.00,'C',1.00,NULL,NULL,NULL,NULL),(194,2,13,23,'韩坤','计科2306班',75.00,3,'2',NULL,'语法错误较多','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(195,3,13,23,'韩坤','计科2306班',70.00,3,'2',NULL,'进制换算常出错','2026-05-27 10:00:00','2026-09-16 10:27:12',68.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(196,4,13,23,'韩坤','计科2306班',59.00,5,'2',NULL,'栈和队列混淆','2026-05-27 10:00:00','2026-09-16 10:27:12',55.00,62.00,59.00,'D',0.00,NULL,NULL,NULL,NULL),(197,5,13,23,'韩坤','计科2306班',68.00,4,'2',NULL,'路由算法需巩固','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,70.00,68.00,'C',2.00,NULL,NULL,NULL,NULL),(198,6,13,23,'韩坤','计科2306班',54.00,5,'2',NULL,'基本概念模糊','2026-05-27 10:00:00','2026-09-16 10:27:12',50.00,58.00,54.00,'D',0.00,NULL,NULL,NULL,NULL),(199,1,14,23,'张博巍','计科2306班',93.00,1,'2',NULL,'微积分掌握扎实','2026-05-27 10:00:00','2026-09-16 10:27:12',90.00,95.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(200,2,14,23,'张博巍','计科2306班',90.00,1,'2',NULL,'口语发音标准','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(201,3,14,23,'张博巍','计科2306班',88.00,2,'2',NULL,'计算机组成理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(202,4,14,23,'张博巍','计科2306班',94.00,1,'2',NULL,'动态规划掌握好','2026-05-27 10:00:00','2026-09-16 10:27:12',92.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(203,5,14,23,'张博巍','计科2306班',80.00,2,'2',NULL,'组网实践能力强','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(204,6,14,23,'张博巍','计科2306班',90.00,1,'2',NULL,'内核结构掌握好','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(205,1,15,23,'程启頔','计科2306班',63.00,4,'2',NULL,'上课注意力不集中','2026-05-27 10:00:00','2026-09-16 10:27:12',58.00,68.00,63.00,'C',1.00,NULL,NULL,NULL,NULL),(206,2,15,23,'程启頔','计科2306班',69.00,4,'2',NULL,'听说能力待提高','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(207,3,15,23,'程启頔','计科2306班',64.00,4,'2',NULL,'算法流程图困难','2026-05-27 10:00:00','2026-09-16 10:27:12',60.00,68.00,64.00,'C',1.50,NULL,NULL,NULL,NULL),(208,4,15,23,'程启頔','计科2306班',66.00,4,'2',NULL,'查找算法需练习','2026-05-27 10:00:00','2026-09-16 10:27:12',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(209,5,15,23,'程启頔','计科2306班',81.00,2,'2',NULL,'子网划分基本正确','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(210,6,15,23,'程启頔','计科2306班',73.00,3,'2',NULL,'系统调用需加强','2026-05-27 10:00:00','2026-09-16 10:27:12',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(211,1,16,23,'邓雨轩','计科2306班',81.00,2,'2',NULL,'数列极限理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(212,2,16,23,'邓雨轩','计科2306班',85.00,2,'2',NULL,'作文表达流畅','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(213,3,16,23,'邓雨轩','计科2306班',79.00,3,'2',NULL,'文件管理操作准确','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(214,4,16,23,'邓雨轩','计科2306班',89.00,2,'2',NULL,'二叉树遍历掌握','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(215,5,16,23,'邓雨轩','计科2306班',75.00,3,'2',NULL,'接口配置规范','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(216,6,16,23,'邓雨轩','计科2306班',81.00,2,'2',NULL,'信号量机制理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(217,1,17,23,'高菲','计科2306班',88.00,2,'2',NULL,'导数应用熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(218,2,17,23,'高菲','计科2306班',80.00,2,'2',NULL,'阅读理解准确','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(219,3,17,23,'高菲','计科2306班',84.00,2,'2',NULL,'表格数据处理掌握','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(220,4,17,23,'高菲','计科2306班',90.00,1,'2',NULL,'B树B+树理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(221,5,17,23,'高菲','计科2306班',78.00,3,'2',NULL,'数据流分析熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(222,6,17,23,'高菲','计科2306班',85.00,2,'2',NULL,'页面置换算法理解','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(223,1,18,23,'胡钰','计科2306班',69.00,4,'2',NULL,'不定积分有进步','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(224,2,18,23,'胡钰','计科2306班',73.00,3,'2',NULL,'语法有进步','2026-05-27 10:00:00','2026-09-16 10:27:12',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(225,3,18,23,'胡钰','计科2306班',66.00,4,'2',NULL,'硬件组成理解不足','2026-05-27 10:00:00','2026-09-16 10:27:12',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(226,4,18,23,'胡钰','计科2306班',79.00,3,'2',NULL,'代码实现有困难','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(227,5,18,23,'胡钰','计科2306班',83.00,2,'2',NULL,'Socket编程需加强','2026-05-27 10:00:00','2026-09-16 10:27:12',80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(228,6,18,23,'胡钰','计科2306班',75.00,3,'2',NULL,'缓冲区管理概念模糊','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(229,1,19,23,'李佳芮','计科2306班',94.00,1,'2',NULL,'空间解析几何好','2026-05-27 10:00:00','2026-09-16 10:27:12',92.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(230,2,19,23,'李佳芮','计科2306班',90.00,1,'2',NULL,'演讲能力出色','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(231,3,19,23,'李佳芮','计科2306班',93.00,1,'2',NULL,'网络基础知识理解深','2026-05-27 10:00:00','2026-09-16 10:27:12',90.00,95.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(232,4,19,23,'李佳芮','计科2306班',88.00,2,'2',NULL,'最短路径算法优秀','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(233,5,19,23,'李佳芮','计科2306班',80.00,2,'2',NULL,'交换原理掌握好','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(234,6,19,23,'李佳芮','计科2306班',94.00,1,'2',NULL,'存储管理理解深','2026-05-27 10:00:00','2026-09-16 10:27:12',92.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(235,1,20,23,'李爽','计科2306班',54.00,5,'2',NULL,'基础不扎实','2026-05-27 10:00:00','2026-09-16 10:27:12',50.00,58.00,54.00,'D',0.00,NULL,NULL,NULL,NULL),(236,2,20,23,'李爽','计科2306班',73.00,3,'2',NULL,'词汇积累不够','2026-05-27 10:00:00','2026-09-16 10:27:12',70.00,75.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(237,3,20,23,'李爽','计科2306班',52.00,5,'2',NULL,'故障判断困难','2026-05-27 10:00:00','2026-09-16 10:27:12',48.00,55.00,52.00,'D',0.00,NULL,NULL,NULL,NULL),(238,4,20,23,'李爽','计科2306班',70.00,3,'2',NULL,'遍历算法需复习','2026-05-27 10:00:00','2026-09-16 10:27:12',68.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(239,5,20,23,'李爽','计科2306班',47.00,5,'2',NULL,'协议层次混淆','2026-05-27 10:00:00','2026-09-16 10:27:12',42.00,52.00,47.00,'D',0.00,NULL,NULL,NULL,NULL),(240,6,20,23,'李爽','计科2306班',69.00,4,'2',NULL,'进程通信理解一般','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(241,1,21,23,'梁莹静','计科2306班',84.00,2,'2',NULL,'多元函数掌握好','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(242,2,21,23,'梁莹静','计科2306班',81.00,2,'2',NULL,'跨文化交际能力好','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(243,3,21,23,'梁莹静','计科2306班',87.00,2,'2',NULL,'操作系统概念清晰','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(244,4,21,23,'梁莹静','计科2306班',83.00,2,'2',NULL,'堆排序理解好','2026-05-27 10:00:00','2026-09-16 10:27:12',80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(245,5,21,23,'梁莹静','计科2306班',90.00,1,'2',NULL,'网线制作规范','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(246,6,21,23,'梁莹静','计科2306班',79.00,3,'2',NULL,'进程同步掌握好','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(247,1,22,23,'孟阳','计科2306班',75.00,3,'2',NULL,'函数连续性需加强','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(248,2,22,23,'孟阳','计科2306班',72.00,3,'2',NULL,'长难句理解困难','2026-05-27 10:00:00','2026-09-16 10:27:12',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(249,3,22,23,'孟阳','计科2306班',73.00,3,'2',NULL,'数据编码理解不足','2026-05-27 10:00:00','2026-09-16 10:27:12',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(250,4,22,23,'孟阳','计科2306班',78.00,3,'2',NULL,'快速排序基本掌握','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(251,5,22,23,'孟阳','计科2306班',69.00,4,'2',NULL,'抓包分析有进步','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(252,6,22,23,'孟阳','计科2306班',81.00,2,'2',NULL,'调度算法理解','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(253,1,23,23,'王瑾泰','计科2306班',81.00,2,'2',NULL,'定积分应用熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(254,2,23,23,'王瑾泰','计科2306班',79.00,3,'2',NULL,'完形填空准确','2026-05-27 10:00:00','2026-09-16 10:27:12',75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(255,3,23,23,'王瑾泰','计科2306班',75.00,3,'2',NULL,'信息安全意识需巩固','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(256,4,23,23,'王瑾泰','计科2306班',85.00,2,'2',NULL,'AVL树掌握好','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(257,5,23,28,'王瑾泰','计科2306班',80.00,2,'2',NULL,'IP地址规划熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(258,6,23,28,'王瑾泰','计科2306班',75.00,3,'2',NULL,'虚拟内存理解','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(259,1,24,28,'王平洋','计科2306班',69.00,4,'2',NULL,'级数概念模糊','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(260,2,24,28,'王平洋','计科2306班',73.00,3,'2',NULL,'翻译题薄弱','2026-05-27 10:00:00','2026-09-16 10:27:12',70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(261,3,24,28,'王平洋','计科2306班',66.00,4,'2',NULL,'数据库基础理解一般','2026-05-27 10:00:00','2026-09-16 10:27:12',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(262,4,24,28,'王平洋','计科2306班',80.00,2,'2',NULL,'并查集基本掌握','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(263,5,24,28,'王平洋','计科2306班',75.00,3,'2',NULL,'子网掩码计算生疏','2026-05-27 10:00:00','2026-09-16 10:27:12',72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(264,6,24,28,'王平洋','计科2306班',72.00,3,'2',NULL,'死锁处理需复习','2026-05-27 10:00:00','2026-09-16 10:27:12',68.00,76.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(265,1,25,28,'夏志浩','计科2306班',90.00,1,'2',NULL,'数学基础雄厚','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(266,2,25,28,'夏志浩','计科2306班',88.00,2,'2',NULL,'学术写作优秀','2026-05-27 10:00:00','2026-09-16 10:27:12',85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(267,3,25,28,'夏志浩','计科2306班',81.00,2,'2',NULL,'动手操作能力强','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(268,4,25,28,'夏志浩','计科2306班',85.00,2,'2',NULL,'算法竞赛获奖','2026-05-27 10:00:00','2026-09-16 10:27:12',82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(269,5,25,28,'夏志浩','计科2306班',92.00,1,'2',NULL,'精通网络排障','2026-05-27 10:00:00','2026-09-16 10:27:12',88.00,95.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(270,6,25,28,'夏志浩','计科2306班',82.00,2,'2',NULL,'多线程调度理解深','2026-05-27 10:00:00','2026-09-16 10:27:12',78.00,86.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(271,1,26,28,'张健','计科2306班',52.00,5,'2',NULL,'多处知识漏洞','2026-05-27 10:00:00','2026-09-16 10:27:12',48.00,55.00,52.00,'D',0.00,NULL,NULL,NULL,NULL),(272,2,26,28,'张健','计科2306班',72.00,3,'2',NULL,'听力严重薄弱','2026-05-27 10:00:00','2026-09-16 10:27:12',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(273,3,26,28,'张健','计科2306班',48.00,5,'2',NULL,'快捷键使用不熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',45.00,50.00,48.00,'D',0.00,NULL,NULL,NULL,NULL),(274,4,26,28,'张健','计科2306班',69.00,4,'2',NULL,'基本操作不熟练','2026-05-27 10:00:00','2026-09-16 10:27:12',65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(275,5,26,28,'张健','计科2306班',49.00,5,'2',NULL,'网络分层理解不足','2026-05-27 10:00:00','2026-09-16 10:27:12',42.00,55.00,49.00,'D',0.00,NULL,NULL,NULL,NULL),(276,6,26,28,'张健','计科2306班',66.00,4,'2',NULL,'原语概念模糊','2026-05-27 10:00:00','2026-09-16 10:27:12',62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(277,1,3,28,'司明','计科2306班',87.00,2,'3',NULL,'第3学期高等数学','2024-06-20 00:00:00','2026-09-16 10:27:12',85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(278,2,3,28,'司明','计科2306班',80.50,2,'3',NULL,'第3学期大学英语','2024-06-20 00:00:00','2026-09-16 10:27:12',78.00,82.00,80.50,'A-',3.00,NULL,NULL,NULL,NULL),(279,3,3,28,'司明','计科2306班',70.00,3,'3',NULL,'第3学期计算机基础','2024-06-20 00:00:00','2026-09-16 10:27:12',72.00,68.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(280,4,3,28,'司明','计科2306班',91.00,1,'3',NULL,'第3学期数据结构优秀','2024-06-20 00:00:00','2026-09-16 10:27:12',90.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(281,5,3,28,'司明','计科2306班',89.00,2,'3',NULL,'第3学期计算机网络','2024-06-20 00:00:00','2026-09-16 10:27:12',88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(282,6,3,28,'司明','计科2306班',62.00,4,'3',NULL,'第3学期操作系统','2024-06-20 00:00:00','2026-09-16 10:27:12',65.00,60.00,62.00,'C',1.00,NULL,NULL,NULL,NULL),(283,1,3,28,'司明','计科2306班',71.50,3,'4',NULL,'第4学期高等数学下滑','2025-01-15 00:00:00','2026-09-16 10:27:12',70.00,72.00,71.50,'B',2.00,NULL,NULL,NULL,NULL),(284,2,3,28,'司明','计科2306班',51.00,5,'4',NULL,'第4学期大学英语不及格','2025-01-15 00:00:00','2026-09-16 10:27:12',55.00,48.00,51.00,'D',0.00,NULL,NULL,NULL,NULL),(285,3,3,28,'司明','计科2306班',59.00,5,'4',NULL,'第4学期计算机基础不及格','2025-01-15 00:00:00','2026-09-16 10:27:12',60.00,58.00,59.00,'D',0.00,NULL,NULL,NULL,NULL),(286,4,3,28,'司明','计科2306班',84.00,2,'4',NULL,'第4学期数据结构','2025-01-15 00:00:00','2026-09-16 10:27:12',82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(287,6,3,28,'司明','计科2306班',40.00,5,'4',NULL,'第4学期操作系统严重不及格','2025-01-15 00:00:00','2026-09-16 10:27:12',42.00,38.00,40.00,'D',0.00,NULL,NULL,NULL,NULL),(288,1,3,28,'司明','计科2306班',56.50,5,'5',NULL,'第5学期高等数学持续下滑','2025-06-20 00:00:00','2026-09-16 10:27:12',58.00,55.00,56.50,'D',0.00,NULL,NULL,NULL,NULL),(289,5,3,28,'司明','计科2306班',65.00,4,'5',NULL,'第5学期计算机网络勉强及格','2025-06-20 00:00:00','2026-09-16 10:27:12',66.00,64.00,65.00,'C',1.50,NULL,NULL,NULL,NULL),(290,3,3,28,'司明','计科2306班',47.00,5,'5',NULL,'第5学期计算机基础补考仍不及格','2025-06-20 00:00:00','2026-09-16 10:27:12',50.00,45.00,47.00,'D',0.00,NULL,NULL,NULL,NULL),(291,7,3,28,'司明','计科2306班',85.00,2,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',87.00,86.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(294,8,3,28,'司明','计科2306班',78.00,3,'6',NULL,NULL,'2026-06-25 09:58:16','2026-09-16 09:36:43',70.00,77.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(296,7,4,28,'邱琳紫','计科2306班',90.00,1,'6',2025,'博弈树分析准确','2026-05-27 10:00:00','2026-09-16 10:44:18',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(297,7,5,28,'宋新鹏','计科2306班',88.00,2,'6',2025,'课堂讨论积极，概念清楚','2026-05-27 10:00:00','2026-09-16 10:44:18',93.00,83.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(298,7,6,28,'赵琦','计科2306班',85.00,2,'6',2024,'模糊推理掌握较好','2026-05-27 10:00:00','2026-09-16 10:44:18',88.00,82.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(299,7,7,28,'苏红阳','计科2306班',92.00,1,'6',2025,'启发式搜索理解透彻','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,97.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(300,7,8,28,'唐浩然','计科2306班',80.00,2,'6',2025,'智能体设计基本掌握','2026-05-27 10:00:00','2026-09-16 10:44:18',80.00,79.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(301,7,9,28,'王宏民','计科2306班',78.00,3,'6',2025,'搜索策略选择需练习','2026-05-27 10:00:00','2026-09-16 10:44:18',73.00,83.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(302,7,10,28,'王玉蛟','计科2306班',86.00,2,'6',2025,'遗传算法理解到位','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,87.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(303,7,11,28,'吴雨航','计科2306班',75.00,3,'6',2025,'理论学习尚可，实验偏弱','2026-05-27 10:00:00','2026-09-16 10:44:18',72.00,77.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(304,7,12,28,'徐瑞瑶','计科2306班',88.00,2,'6',2025,'专家系统案例完成好','2026-05-27 10:00:00','2026-09-16 10:44:18',82.00,94.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(305,7,13,28,'韩坤','计科2306班',72.00,3,'6',2025,'基础概念模糊，需巩固','2026-05-27 10:00:00','2026-09-16 10:44:18',70.00,74.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(306,7,14,28,'张博巍','计科2306班',94.00,1,'6',2025,'搜索算法掌握扎实','2026-05-27 10:00:00','2026-09-16 10:44:18',100.00,88.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(307,7,15,28,'程启頔','计科2306班',70.00,3,'6',2025,'上课注意力不集中','2026-05-27 10:00:00','2026-09-16 10:44:18',65.00,75.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(308,7,16,28,'邓雨轩','计科2306班',83.00,2,'6',2025,'知识图谱作业完成认真','2026-05-27 10:00:00','2026-09-16 10:44:18',81.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(309,7,17,28,'高菲','计科2306班',89.00,2,'6',2025,'谓词逻辑推导熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,91.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(310,7,18,28,'胡钰','计科2306班',76.00,3,'6',2025,'逻辑推理部分有进步','2026-05-27 10:00:00','2026-09-16 10:44:18',71.00,80.00,76.00,'B',2.70,NULL,NULL,NULL,NULL),(311,7,19,28,'李佳芮','计科2306班',91.00,1,'6',2025,'知识表示章节表现优秀','2026-05-27 10:00:00','2026-09-16 10:44:18',92.00,89.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(312,7,20,28,'李爽','计科2306班',68.00,4,'6',2025,'搜索算法理解吃力','2026-05-27 10:00:00','2026-09-16 10:44:18',67.00,69.00,68.00,'C',2.00,NULL,NULL,NULL,NULL),(313,7,21,28,'梁莹静','计科2306班',87.00,2,'6',2025,'状态空间建模规范','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,90.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(314,7,22,28,'孟阳','计科2306班',74.00,3,'6',2025,'状态空间建模需加强','2026-05-27 10:00:00','2026-09-16 10:44:18',75.00,72.00,74.00,'B',2.30,NULL,NULL,NULL,NULL),(315,7,23,28,'王瑾泰','计科2306班',82.00,2,'6',2025,'基本概念清晰，实践偏少','2026-05-27 10:00:00','2026-09-16 10:44:18',81.00,82.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(316,7,24,28,'王平洋','计科2306班',73.00,3,'6',2025,'算法伪代码书写待规范','2026-05-27 10:00:00','2026-09-16 10:44:18',77.00,68.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(317,7,25,28,'夏志浩','计科2306班',95.00,1,'6',2025,'智能体建模思路清晰','2026-05-27 10:00:00','2026-09-16 10:44:18',96.00,93.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(318,7,26,28,'张健','计科2306班',65.00,4,'6',2025,'基础薄弱，多处知识漏洞','2026-05-27 10:00:00','2026-09-16 10:44:18',61.00,69.00,65.00,'C',1.50,NULL,NULL,NULL,NULL),(319,8,4,28,'邱琳紫','计科2306班',88.00,2,'6',2025,'决策树与剪枝理解好','2026-05-27 10:00:00','2026-09-16 10:44:18',86.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(320,8,5,28,'宋新鹏','计科2306班',90.00,1,'6',2025,'交叉验证运用熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',97.00,83.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(321,8,6,18,'赵琦','计科2306班',82.00,2,'6',2024,'梯度下降理解正确','2026-05-27 10:00:00','2026-09-16 10:44:18',75.00,89.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(322,8,7,18,'苏红阳','计科2306班',86.00,2,'6',2025,'聚类算法实验完成好','2026-05-27 10:00:00','2026-09-16 10:44:18',88.00,84.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(323,8,8,18,'唐浩然','计科2306班',84.00,2,'6',2025,'线性回归实现规范','2026-05-27 10:00:00','2026-09-16 10:44:18',79.00,88.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(324,8,9,18,'王宏民','计科2306班',76.00,3,'6',2025,'调参经验不足需多练','2026-05-27 10:00:00','2026-09-16 10:44:18',70.00,81.00,76.00,'B',2.70,NULL,NULL,NULL,NULL),(325,8,10,18,'王玉蛟','计科2306班',83.00,2,'6',2025,'朴素贝叶斯应用熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',85.00,80.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(326,8,11,18,'吴雨航','计科2306班',74.00,3,'6',2025,'损失函数推导需加强','2026-05-27 10:00:00','2026-09-16 10:44:18',73.00,75.00,74.00,'B',2.30,NULL,NULL,NULL,NULL),(327,8,12,18,'徐瑞瑶','计科2306班',85.00,2,'6',2025,'模型评估指标理解到位','2026-05-27 10:00:00','2026-09-16 10:44:18',86.00,84.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(328,8,13,18,'韩坤','计科2306班',70.00,3,'6',2025,'实验报告较粗糙','2026-05-27 10:00:00','2026-09-16 10:44:18',66.00,74.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(329,8,14,18,'张博巍','计科2306班',92.00,1,'6',2025,'集成学习掌握扎实','2026-05-27 10:00:00','2026-09-16 10:44:18',91.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(330,8,15,18,'程启頔','计科2306班',68.00,4,'6',2025,'模型原理理解不透','2026-05-27 10:00:00','2026-09-16 10:44:18',72.00,63.00,68.00,'C',2.00,NULL,NULL,NULL,NULL),(331,8,16,18,'邓雨轩','计科2306班',81.00,2,'6',2025,'KNN分类实验认真','2026-05-27 10:00:00','2026-09-16 10:44:18',81.00,80.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(332,8,17,18,'高菲','计科2306班',87.00,2,'6',2025,'支持向量机推导清晰','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,87.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(333,8,18,18,'胡钰','计科2306班',78.00,3,'6',2025,'过拟合问题理解一般','2026-05-27 10:00:00','2026-09-16 10:44:18',79.00,77.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(334,8,19,18,'李佳芮','计科2306班',93.00,1,'6',2025,'特征工程做得细致','2026-05-27 10:00:00','2026-09-16 10:44:18',92.00,94.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(335,8,20,18,'李爽','计科2306班',66.00,4,'6',2025,'公式推导常出错','2026-05-27 10:00:00','2026-09-16 10:44:18',66.00,66.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(336,8,21,18,'梁莹静','计科2306班',84.00,2,'6',2025,'降维方法掌握较好','2026-05-27 10:00:00','2026-09-16 10:44:18',83.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(337,8,22,18,'孟阳','计科2306班',72.00,3,'6',2025,'数学推导基础较弱','2026-05-27 10:00:00','2026-09-16 10:44:18',69.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(338,8,23,18,'王瑾泰','计科2306班',80.00,2,'6',2025,'基本算法能独立实现','2026-05-27 10:00:00','2026-09-16 10:44:18',85.00,75.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(339,8,24,18,'王平洋','计科2306班',71.00,3,'6',2025,'特征选择方法需复习','2026-05-27 10:00:00','2026-09-16 10:44:18',65.00,77.00,71.00,'B',2.00,NULL,NULL,NULL,NULL),(340,8,25,18,'夏志浩','计科2306班',96.00,1,'6',2025,'模型调优能力突出','2026-05-27 10:00:00','2026-09-16 10:44:18',96.00,95.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(341,8,26,18,'张健','计科2306班',63.00,4,'6',2025,'基础不牢，需系统复习','2026-05-27 10:00:00','2026-09-16 10:44:18',61.00,65.00,63.00,'C',1.00,NULL,NULL,NULL,NULL),(342,9,4,18,'邱琳紫','计科2306班',85.00,2,'6',2025,'张量运算熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,83.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(343,9,5,18,'宋新鹏','计科2306班',87.00,2,'6',2025,'优化器选择有依据','2026-05-27 10:00:00','2026-09-16 10:44:18',92.00,81.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(344,9,6,18,'赵琦','计科2306班',83.00,2,'6',2024,'激活函数对比分析好','2026-05-27 10:00:00','2026-09-16 10:44:18',82.00,84.00,83.00,'A-',3.30,NULL,NULL,NULL,NULL),(345,9,7,18,'苏红阳','计科2306班',90.00,1,'6',2025,'卷积网络实验优秀','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,93.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(346,9,8,18,'唐浩然','计科2306班',79.00,3,'6',2025,'生成模型概念尚可','2026-05-27 10:00:00','2026-09-16 10:44:18',82.00,76.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(347,9,9,18,'王宏民','计科2306班',74.00,3,'6',2025,'链式求导需巩固','2026-05-27 10:00:00','2026-09-16 10:44:18',71.00,77.00,74.00,'B',2.30,NULL,NULL,NULL,NULL),(348,9,10,18,'王玉蛟','计科2306班',88.00,2,'6',2025,'迁移学习应用得当','2026-05-27 10:00:00','2026-09-16 10:44:18',90.00,85.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(349,9,11,18,'吴雨航','计科2306班',72.00,3,'6',2025,'代码实现有困难','2026-05-27 10:00:00','2026-09-16 10:44:18',68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,NULL),(350,9,12,18,'徐瑞瑶','计科2306班',86.00,2,'6',2025,'批归一化理解到位','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,88.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(351,9,13,18,'韩坤','计科2306班',68.00,4,'6',2025,'梯度问题理解模糊','2026-05-27 10:00:00','2026-09-16 10:44:18',73.00,62.00,68.00,'C',2.00,NULL,NULL,NULL,NULL),(352,9,14,18,'张博巍','计科2306班',89.00,2,'6',2025,'循环网络掌握扎实','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(353,9,15,18,'程启頔','计科2306班',66.00,4,'6',2025,'基础概念掌握不牢','2026-05-27 10:00:00','2026-09-16 10:44:18',64.00,68.00,66.00,'C',1.50,NULL,NULL,NULL,NULL),(354,9,16,18,'邓雨轩','计科2306班',80.00,2,'6',2025,'池化操作理解正确','2026-05-27 10:00:00','2026-09-16 10:44:18',75.00,85.00,80.00,'A-',3.00,NULL,NULL,NULL,NULL),(355,9,17,18,'高菲','计科2306班',91.00,1,'6',2025,'反向传播推导熟练','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(356,9,18,18,'胡钰','计科2306班',75.00,3,'6',2025,'网络训练调参较弱','2026-05-27 10:00:00','2026-09-16 10:44:18',76.00,73.00,75.00,'B',2.70,NULL,NULL,NULL,NULL),(357,9,19,18,'李佳芮','计科2306班',94.00,1,'6',2025,'注意力机制理解深入','2026-05-27 10:00:00','2026-09-16 10:44:18',96.00,92.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(358,9,20,18,'李爽','计科2306班',64.00,4,'6',2025,'推导过程错误较多','2026-05-27 10:00:00','2026-09-16 10:44:18',67.00,61.00,64.00,'C',1.50,NULL,NULL,NULL,NULL),(359,9,21,18,'梁莹静','计科2306班',82.00,2,'6',2025,'模型搭建基本规范','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,80.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(360,9,22,18,'孟阳','计科2306班',70.00,3,'6',2025,'理论理解较浅','2026-05-27 10:00:00','2026-09-16 10:44:18',67.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(361,9,23,18,'王瑾泰','计科2306班',78.00,3,'6',2025,'正则化方法需多练','2026-05-27 10:00:00','2026-09-16 10:44:18',78.00,78.00,78.00,'B',3.00,NULL,NULL,NULL,NULL),(362,9,24,18,'王平洋','计科2306班',69.00,4,'6',2025,'实验复现不完整','2026-05-27 10:00:00','2026-09-16 10:44:18',74.00,64.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(363,9,25,18,'夏志浩','计科2306班',92.00,1,'6',2025,'网络结构设计有想法','2026-05-27 10:00:00','2026-09-16 10:44:18',92.00,91.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(364,9,26,18,'张健','计科2306班',61.00,4,'6',2025,'基础薄弱，需从头复习','2026-05-27 10:00:00','2026-09-16 10:44:18',63.00,58.00,61.00,'C',1.00,NULL,NULL,NULL,NULL),(365,10,4,18,'邱琳紫','计科2306班',92.00,1,'6',2025,'经典诵读积累丰富','2026-05-27 10:00:00','2026-09-16 10:44:18',90.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(366,10,5,18,'宋新鹏','计科2306班',86.00,2,'6',2025,'课堂分享积极，见解独到','2026-05-27 10:00:00','2026-09-16 10:44:18',91.00,81.00,86.00,'A-',3.70,NULL,NULL,NULL,NULL),(367,10,6,18,'赵琦','计科2306班',84.00,2,'6',2024,'古典建筑理解较好','2026-05-27 10:00:00','2026-09-16 10:44:18',87.00,81.00,84.00,'A-',3.30,NULL,NULL,NULL,NULL),(368,10,7,18,'苏红阳','计科2306班',88.00,2,'6',2025,'传统节日民俗熟悉','2026-05-27 10:00:00','2026-09-16 10:44:18',86.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,NULL),(369,10,8,18,'唐浩然','计科2306班',81.00,2,'6',2025,'中医文化了解较多','2026-05-27 10:00:00','2026-09-16 10:44:18',77.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,NULL),(370,10,9,18,'王宏民','计科2306班',77.00,3,'6',2025,'论文选题思路一般','2026-05-27 10:00:00','2026-09-16 10:44:18',81.00,72.00,77.00,'B',2.70,NULL,NULL,NULL,NULL),(371,10,10,18,'王玉蛟','计科2306班',90.00,1,'6',2025,'礼乐制度理解到位','2026-05-27 10:00:00','2026-09-16 10:44:18',93.00,87.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(372,10,11,18,'吴雨航','计科2306班',73.00,3,'6',2025,'经典原文理解吃力','2026-05-27 10:00:00','2026-09-16 10:44:18',77.00,69.00,73.00,'B',2.30,NULL,NULL,NULL,NULL),(373,10,12,18,'徐瑞瑶','计科2306班',87.00,2,'6',2025,'戏曲艺术理解深入','2026-05-27 10:00:00','2026-09-16 10:44:18',88.00,85.00,87.00,'A-',3.70,NULL,NULL,NULL,NULL),(374,10,13,18,'韩坤','计科2306班',69.00,4,'6',2025,'上课参与度不高','2026-05-27 10:00:00','2026-09-16 10:44:18',75.00,62.00,69.00,'C',2.00,NULL,NULL,NULL,NULL),(375,10,14,18,'张博巍','计科2306班',91.00,1,'6',2025,'史学脉络梳理清晰','2026-05-27 10:00:00','2026-09-16 10:44:18',88.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(376,10,15,18,'程启頔','计科2306班',67.00,4,'6',2025,'基础知识掌握不牢','2026-05-27 10:00:00','2026-09-16 10:44:18',65.00,69.00,67.00,'C',1.50,NULL,NULL,NULL,NULL),(377,10,16,18,'邓雨轩','计科2306班',82.00,2,'6',2025,'绘画流派辨识准确','2026-05-27 10:00:00','2026-09-16 10:44:18',79.00,85.00,82.00,'A-',3.30,NULL,NULL,NULL,NULL),(378,10,17,18,'高菲','计科2306班',89.00,2,'6',2025,'书法史论掌握较好','2026-05-27 10:00:00','2026-09-16 10:44:18',90.00,87.00,89.00,'A-',3.70,NULL,NULL,NULL,NULL),(379,10,18,18,'胡钰','计科2306班',77.00,3,'6',2025,'史料阅读需加强','2026-05-27 10:00:00','2026-09-16 10:44:18',79.00,75.00,77.00,'B',2.70,NULL,NULL,NULL,NULL),(380,10,19,18,'李佳芮','计科2306班',90.00,1,'6',2025,'诗词赏析功底扎实','2026-05-27 10:00:00','2026-09-16 10:44:18',90.00,89.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(381,10,20,18,'李爽','计科2306班',65.00,4,'6',2025,'作业完成质量偏低','2026-05-27 10:00:00','2026-09-16 10:44:18',69.00,61.00,65.00,'C',1.50,NULL,NULL,NULL,NULL),(382,10,21,18,'梁莹静','计科2306班',85.00,2,'6',2025,'陶瓷工艺知识丰富','2026-05-27 10:00:00','2026-09-16 10:44:18',84.00,85.00,85.00,'A-',3.70,NULL,NULL,NULL,NULL),(383,10,22,18,'孟阳','计科2306班',71.00,3,'6',2025,'背诵积累不足','2026-05-27 10:00:00','2026-09-16 10:44:18',70.00,71.00,71.00,'B',2.00,NULL,NULL,NULL,NULL),(384,10,23,18,'王瑾泰','计科2306班',79.00,3,'6',2025,'文化自信主题完成认真','2026-05-27 10:00:00','2026-09-16 10:44:18',78.00,79.00,79.00,'B',3.00,NULL,NULL,NULL,NULL),(385,10,24,29,'王平洋','计科2306班',70.00,3,'6',2025,'观点表达待组织','2026-05-27 10:00:00','2026-09-16 10:44:18',64.00,75.00,70.00,'B',2.00,NULL,NULL,NULL,NULL),(386,10,25,29,'夏志浩','计科2306班',93.00,1,'6',2025,'诸子百家思想理解深','2026-05-27 10:00:00','2026-09-16 10:44:18',85.00,100.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(387,10,26,29,'张健','计科2306班',62.00,4,'6',2025,'多处知识漏洞','2026-05-27 10:00:00','2026-09-16 10:44:18',56.00,68.00,62.00,'C',1.00,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `score_record_bak_sem9_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `scores`
--

DROP TABLE IF EXISTS `scores`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scores` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `semester` int NOT NULL,
  `year` int DEFAULT NULL,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) NOT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(5,2) DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_scores_student_id` (`student_id`),
  KEY `idx_scores_course_id` (`course_id`),
  KEY `idx_scores_semester` (`semester`),
  KEY `idx_scores_student_id_semester` (`student_id`,`semester`),
  CONSTRAINT `fk_scores_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `fk_scores_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=270 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scores`
--

LOCK TABLES `scores` WRITE;
/*!40000 ALTER TABLE `scores` DISABLE KEYS */;
INSERT INTO `scores` VALUES (1,3,1,2,2023,78.00,85.00,82.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(2,3,2,2,2023,80.00,82.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(3,3,3,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(4,3,4,2,2023,90.00,88.00,89.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(5,3,5,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(6,3,6,2,2023,70.00,78.00,74.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(7,4,1,2,2023,92.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(8,4,2,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(9,4,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(10,4,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(11,4,5,2,2023,90.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(12,4,6,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(13,5,1,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(14,5,2,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(15,5,3,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(16,5,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(17,5,5,2,2023,90.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(18,5,6,2,2023,80.00,84.00,82.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(19,6,1,2,2022,75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(20,6,2,2,2022,80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(21,6,3,2,2022,70.00,78.00,74.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(22,6,4,2,2022,85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(23,6,5,2,2022,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(24,6,6,2,2022,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(25,7,1,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(26,7,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(27,7,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(28,7,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(29,7,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(30,7,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(31,8,1,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(32,8,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(33,8,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(34,8,4,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(35,8,5,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(36,8,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(37,9,1,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(38,9,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(39,9,3,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(40,9,4,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(41,9,5,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(42,9,6,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(43,10,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(44,10,2,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(45,10,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(46,10,4,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(47,10,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(48,10,6,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(49,11,1,2,2023,60.00,68.00,64.00,'C',1.50,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(50,11,2,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(51,11,3,2,2023,62.00,68.00,65.00,'C',1.50,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(52,11,4,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(53,11,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(54,11,6,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(55,12,1,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(56,12,2,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(57,12,3,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(58,12,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(59,12,5,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(60,12,6,2,2023,78.00,85.00,82.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(61,13,1,2,2023,60.00,65.00,63.00,'C',1.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(62,13,2,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(63,13,3,2,2023,68.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(64,13,4,2,2023,55.00,62.00,59.00,'D',0.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(65,13,5,2,2023,65.00,70.00,68.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(66,13,6,2,2023,50.00,58.00,54.00,'D',0.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(67,14,1,2,2023,90.00,95.00,93.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(68,14,2,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(69,14,3,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(70,14,4,2,2023,92.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(71,14,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(72,14,6,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(73,15,1,2,2023,58.00,68.00,63.00,'C',1.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(74,15,2,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(75,15,3,2,2023,60.00,68.00,64.00,'C',1.50,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(76,15,4,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(77,15,5,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(78,15,6,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(79,16,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(80,16,2,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(81,16,3,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(82,16,4,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(83,16,5,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(84,16,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(85,17,1,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(86,17,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(87,17,3,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(88,17,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(89,17,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(90,17,6,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(91,18,1,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(92,18,2,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(93,18,3,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(94,18,4,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(95,18,5,2,2023,80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(96,18,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(97,19,1,2,2023,92.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(98,19,2,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(99,19,3,2,2023,90.00,95.00,93.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(100,19,4,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(101,19,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(102,19,6,2,2023,92.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(103,20,1,2,2023,50.00,58.00,54.00,'D',0.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(104,20,2,2,2023,70.00,75.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(105,20,3,2,2023,48.00,55.00,52.00,'D',0.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(106,20,4,2,2023,68.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(107,20,5,2,2023,42.00,52.00,47.00,'D',0.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(108,20,6,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(109,21,1,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(110,21,2,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(111,21,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(112,21,4,2,2023,80.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(113,21,5,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(114,21,6,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(115,22,1,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(116,22,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(117,22,3,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(118,22,4,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(119,22,5,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(120,22,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(121,23,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(122,23,2,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(123,23,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(124,23,4,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(125,23,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(126,23,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(127,24,1,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(128,24,2,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(129,24,3,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(130,24,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(131,24,5,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(132,24,6,2,2023,68.00,76.00,72.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(133,25,1,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(134,25,2,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(135,25,3,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(136,25,4,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(137,25,5,2,2023,88.00,95.00,92.00,'A',4.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(138,25,6,2,2023,78.00,86.00,82.00,'A-',3.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(139,26,1,2,2023,48.00,55.00,52.00,'D',0.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(140,26,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(141,26,3,2,2023,45.00,50.00,48.00,'D',0.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(142,26,4,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(143,26,5,2,2023,42.00,55.00,49.00,'D',0.00,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(144,26,6,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(145,3,1,3,2024,85.00,88.00,87.00,'A-',3.70,NULL,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(146,3,2,3,2024,78.00,82.00,80.50,'A-',3.00,NULL,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(147,3,3,3,2024,72.00,68.00,70.00,'B',2.00,NULL,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(148,3,4,3,2024,90.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(149,3,5,3,2024,88.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(150,3,6,3,2024,65.00,60.00,62.00,'C',1.00,NULL,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(151,3,1,4,2024,70.00,72.00,71.50,'B',2.00,NULL,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(152,3,2,4,2024,55.00,48.00,51.00,'D',0.00,NULL,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(153,3,3,4,2024,60.00,58.00,59.00,'D',0.00,NULL,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(154,3,4,4,2024,82.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(155,3,5,4,2024,75.00,70.00,72.00,'B',2.30,NULL,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(156,3,6,4,2024,42.00,38.00,40.00,'D',0.00,NULL,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(157,3,1,5,2025,58.00,55.00,56.50,'D',0.00,NULL,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(158,3,2,5,2025,65.00,62.00,63.00,'C',1.00,NULL,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(159,3,3,5,2025,50.00,45.00,47.00,'D',0.00,NULL,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(160,3,4,5,2025,78.00,75.00,76.00,'B',2.70,NULL,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(161,3,5,5,2025,66.00,64.00,65.00,'C',1.50,NULL,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(162,3,7,6,2025,88.00,85.00,86.00,'A-',3.70,NULL,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 13:48:22'),(163,3,8,6,2025,90.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 13:48:22'),(164,3,2,6,2025,70.00,NULL,35.00,NULL,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(165,3,3,6,2025,40.00,NULL,20.00,NULL,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(166,3,4,6,2025,85.00,NULL,42.50,NULL,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(167,3,6,6,2025,50.00,NULL,25.00,NULL,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(168,4,7,6,2025,89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(169,5,7,6,2025,93.00,83.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(170,6,7,6,2024,88.00,82.00,85.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(171,7,7,6,2025,87.00,97.00,92.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(172,8,7,6,2025,80.00,79.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(173,9,7,6,2025,73.00,83.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(174,10,7,6,2025,84.00,87.00,86.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(175,11,7,6,2025,72.00,77.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(176,12,7,6,2025,82.00,94.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(177,13,7,6,2025,70.00,74.00,72.00,'B',2.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(178,14,7,6,2025,100.00,88.00,94.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(179,15,7,6,2025,65.00,75.00,70.00,'B',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(180,16,7,6,2025,81.00,85.00,83.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(181,17,7,6,2025,87.00,91.00,89.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(182,18,7,6,2025,71.00,80.00,76.00,'B',2.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(183,19,7,6,2025,92.00,89.00,91.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(184,20,7,6,2025,67.00,69.00,68.00,'C',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(185,21,7,6,2025,84.00,90.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(186,22,7,6,2025,75.00,72.00,74.00,'B',2.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(187,23,7,6,2025,81.00,82.00,82.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(188,24,7,6,2025,77.00,68.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(189,25,7,6,2025,96.00,93.00,95.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(190,26,7,6,2025,61.00,69.00,65.00,'C',1.50,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(191,4,8,6,2025,86.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(192,5,8,6,2025,97.00,83.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(193,6,8,6,2024,75.00,89.00,82.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(194,7,8,6,2025,88.00,84.00,86.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(195,8,8,6,2025,79.00,88.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(196,9,8,6,2025,70.00,81.00,76.00,'B',2.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(197,10,8,6,2025,85.00,80.00,83.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(198,11,8,6,2025,73.00,75.00,74.00,'B',2.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(199,12,8,6,2025,86.00,84.00,85.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(200,13,8,6,2025,66.00,74.00,70.00,'B',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(201,14,8,6,2025,91.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(202,15,8,6,2025,72.00,63.00,68.00,'C',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(203,16,8,6,2025,81.00,80.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(204,17,8,6,2025,87.00,87.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(205,18,8,6,2025,79.00,77.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(206,19,8,6,2025,92.00,94.00,93.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(207,20,8,6,2025,66.00,66.00,66.00,'C',1.50,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(208,21,8,6,2025,83.00,85.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(209,22,8,6,2025,69.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(210,23,8,6,2025,85.00,75.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(211,24,8,6,2025,65.00,77.00,71.00,'B',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(212,25,8,6,2025,96.00,95.00,96.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(213,26,8,6,2025,61.00,65.00,63.00,'C',1.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(214,4,9,6,2025,87.00,83.00,85.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(215,5,9,6,2025,92.00,81.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(216,6,9,6,2024,82.00,84.00,83.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(217,7,9,6,2025,87.00,93.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(218,8,9,6,2025,82.00,76.00,79.00,'B',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(219,9,9,6,2025,71.00,77.00,74.00,'B',2.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(220,10,9,6,2025,90.00,85.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(221,11,9,6,2025,68.00,75.00,72.00,'B',2.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(222,12,9,6,2025,84.00,88.00,86.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(223,13,9,6,2025,73.00,62.00,68.00,'C',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(224,14,9,6,2025,87.00,90.00,89.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(225,15,9,6,2025,64.00,68.00,66.00,'C',1.50,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(226,16,9,6,2025,75.00,85.00,80.00,'A-',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(227,17,9,6,2025,87.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(228,18,9,6,2025,76.00,73.00,75.00,'B',2.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(229,19,9,6,2025,96.00,92.00,94.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(230,20,9,6,2025,67.00,61.00,64.00,'C',1.50,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(231,21,9,6,2025,84.00,80.00,82.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(232,22,9,6,2025,67.00,72.00,70.00,'B',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(233,23,9,6,2025,78.00,78.00,78.00,'B',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(234,24,9,6,2025,74.00,64.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(235,25,9,6,2025,92.00,91.00,92.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(236,26,9,6,2025,63.00,58.00,61.00,'C',1.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(237,4,10,6,2025,90.00,93.00,92.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(238,5,10,6,2025,91.00,81.00,86.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(239,6,10,6,2024,87.00,81.00,84.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(240,7,10,6,2025,86.00,90.00,88.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(241,8,10,6,2025,77.00,84.00,81.00,'A-',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(242,9,10,6,2025,81.00,72.00,77.00,'B',2.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(243,10,10,6,2025,93.00,87.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(244,11,10,6,2025,77.00,69.00,73.00,'B',2.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(245,12,10,6,2025,88.00,85.00,87.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(246,13,10,6,2025,75.00,62.00,69.00,'C',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(247,14,10,6,2025,88.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(248,15,10,6,2025,65.00,69.00,67.00,'C',1.50,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(249,16,10,6,2025,79.00,85.00,82.00,'A-',3.30,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(250,17,10,6,2025,90.00,87.00,89.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(251,18,10,6,2025,79.00,75.00,77.00,'B',2.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(252,19,10,6,2025,90.00,89.00,90.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(253,20,10,6,2025,69.00,61.00,65.00,'C',1.50,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(254,21,10,6,2025,84.00,85.00,85.00,'A-',3.70,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(255,22,10,6,2025,70.00,71.00,71.00,'B',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(256,23,10,6,2025,78.00,79.00,79.00,'B',3.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(257,24,10,6,2025,64.00,75.00,70.00,'B',2.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(258,25,10,6,2025,85.00,100.00,93.00,'A',4.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(259,26,10,6,2025,56.00,68.00,62.00,'C',1.00,NULL,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25');
/*!40000 ALTER TABLE `scores` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `scores_bak_elective_20260916`
--

DROP TABLE IF EXISTS `scores_bak_elective_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scores_bak_elective_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `student_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `semester` int NOT NULL,
  `year` int DEFAULT NULL,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) NOT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(5,2) DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scores_bak_elective_20260916`
--

LOCK TABLES `scores_bak_elective_20260916` WRITE;
/*!40000 ALTER TABLE `scores_bak_elective_20260916` DISABLE KEYS */;
INSERT INTO `scores_bak_elective_20260916` VALUES (1,3,1,2,2023,78.00,85.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(2,3,2,2,2023,80.00,82.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(3,3,3,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(4,3,4,2,2023,90.00,88.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(5,3,5,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(6,3,6,2,2023,70.00,78.00,74.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(7,4,1,2,2023,92.00,95.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(8,4,2,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(9,4,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(10,4,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(11,4,5,2,2023,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(12,4,6,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(13,5,1,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(14,5,2,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(15,5,3,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(16,5,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(17,5,5,2,2023,90.00,93.00,92.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(18,5,6,2,2023,80.00,84.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(19,6,1,2,2022,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(20,6,2,2,2022,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(21,6,3,2,2022,70.00,78.00,74.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(22,6,4,2,2022,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(23,6,5,2,2022,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(24,6,6,2,2022,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(25,7,1,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(26,7,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(27,7,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(28,7,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(29,7,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(30,7,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(31,8,1,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(32,8,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(33,8,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(34,8,4,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(35,8,5,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(36,8,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(37,9,1,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(38,9,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(39,9,3,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(40,9,4,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(41,9,5,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(42,9,6,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(43,10,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(44,10,2,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(45,10,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(46,10,4,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(47,10,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(48,10,6,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(49,11,1,2,2023,60.00,68.00,64.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(50,11,2,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(51,11,3,2,2023,62.00,68.00,65.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(52,11,4,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(53,11,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(54,11,6,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(55,12,1,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(56,12,2,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(57,12,3,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(58,12,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(59,12,5,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(60,12,6,2,2023,78.00,85.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(61,13,1,2,2023,60.00,65.00,63.00,'C',1.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(62,13,2,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(63,13,3,2,2023,68.00,72.00,70.00,'B',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(64,13,4,2,2023,55.00,62.00,59.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(65,13,5,2,2023,65.00,70.00,68.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(66,13,6,2,2023,50.00,58.00,54.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(67,14,1,2,2023,90.00,95.00,93.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(68,14,2,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(69,14,3,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(70,14,4,2,2023,92.00,96.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(71,14,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(72,14,6,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(73,15,1,2,2023,58.00,68.00,63.00,'C',1.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(74,15,2,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(75,15,3,2,2023,60.00,68.00,64.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(76,15,4,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(77,15,5,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(78,15,6,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(79,16,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(80,16,2,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(81,16,3,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(82,16,4,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(83,16,5,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(84,16,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(85,17,1,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(86,17,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(87,17,3,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(88,17,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(89,17,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(90,17,6,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(91,18,1,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(92,18,2,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(93,18,3,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(94,18,4,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(95,18,5,2,2023,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(96,18,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(97,19,1,2,2023,92.00,96.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(98,19,2,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(99,19,3,2,2023,90.00,95.00,93.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(100,19,4,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(101,19,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(102,19,6,2,2023,92.00,95.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(103,20,1,2,2023,50.00,58.00,54.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(104,20,2,2,2023,70.00,75.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(105,20,3,2,2023,48.00,55.00,52.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(106,20,4,2,2023,68.00,72.00,70.00,'B',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(107,20,5,2,2023,42.00,52.00,47.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(108,20,6,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(109,21,1,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(110,21,2,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(111,21,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(112,21,4,2,2023,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(113,21,5,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(114,21,6,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(115,22,1,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(116,22,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(117,22,3,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(118,22,4,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(119,22,5,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(120,22,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(121,23,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(122,23,2,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(123,23,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(124,23,4,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(125,23,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(126,23,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(127,24,1,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(128,24,2,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(129,24,3,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(130,24,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(131,24,5,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(132,24,6,2,2023,68.00,76.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(133,25,1,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(134,25,2,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(135,25,3,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(136,25,4,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(137,25,5,2,2023,88.00,95.00,92.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(138,25,6,2,2023,78.00,86.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(139,26,1,2,2023,48.00,55.00,52.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(140,26,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(141,26,3,2,2023,45.00,50.00,48.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(142,26,4,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(143,26,5,2,2023,42.00,55.00,49.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(144,26,6,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(145,3,1,3,2024,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(146,3,2,3,2024,78.00,82.00,80.50,'A-',3.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(147,3,3,3,2024,72.00,68.00,70.00,'B',2.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(148,3,4,3,2024,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(149,3,5,3,2024,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(150,3,6,3,2024,65.00,60.00,62.00,'C',1.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(151,3,1,4,2024,70.00,72.00,71.50,'B',2.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(152,3,2,4,2024,55.00,48.00,51.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(153,3,3,4,2024,60.00,58.00,59.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(154,3,4,4,2024,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(155,3,5,4,2024,75.00,70.00,72.00,'B',2.30,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(156,3,6,4,2024,42.00,38.00,40.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(157,3,1,5,2025,58.00,55.00,56.50,'D',0.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(158,3,2,5,2025,65.00,62.00,63.00,'C',1.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(159,3,3,5,2025,50.00,45.00,47.00,'D',0.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(160,3,4,5,2025,78.00,75.00,76.00,'B',2.70,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(161,3,5,5,2025,66.00,64.00,65.00,'C',1.50,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(162,3,7,5,2025,88.00,85.00,86.00,'A-',3.70,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(163,3,8,5,2025,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(164,3,2,6,2025,70.00,NULL,35.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(165,3,3,6,2025,40.00,NULL,20.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(166,3,4,6,2025,85.00,NULL,42.50,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(167,3,6,6,2025,50.00,NULL,25.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04');
/*!40000 ALTER TABLE `scores_bak_elective_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `scores_bak_prefix7_20260916`
--

DROP TABLE IF EXISTS `scores_bak_prefix7_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scores_bak_prefix7_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `student_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `semester` int NOT NULL,
  `year` int DEFAULT NULL,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) NOT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(5,2) DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scores_bak_prefix7_20260916`
--

LOCK TABLES `scores_bak_prefix7_20260916` WRITE;
/*!40000 ALTER TABLE `scores_bak_prefix7_20260916` DISABLE KEYS */;
INSERT INTO `scores_bak_prefix7_20260916` VALUES (1,3,1,2,2023,78.00,85.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(2,3,2,2,2023,80.00,82.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(3,3,3,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(4,3,4,2,2023,90.00,88.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(5,3,5,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(6,3,6,2,2023,70.00,78.00,74.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(7,4,1,2,2023,92.00,95.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(8,4,2,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(9,4,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(10,4,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(11,4,5,2,2023,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(12,4,6,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(13,5,1,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(14,5,2,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(15,5,3,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(16,5,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(17,5,5,2,2023,90.00,93.00,92.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(18,5,6,2,2023,80.00,84.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(19,6,1,2,2022,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(20,6,2,2,2022,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(21,6,3,2,2022,70.00,78.00,74.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(22,6,4,2,2022,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(23,6,5,2,2022,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(24,6,6,2,2022,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(25,7,1,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(26,7,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(27,7,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(28,7,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(29,7,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(30,7,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(31,8,1,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(32,8,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(33,8,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(34,8,4,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(35,8,5,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(36,8,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(37,9,1,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(38,9,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(39,9,3,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(40,9,4,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(41,9,5,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(42,9,6,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(43,10,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(44,10,2,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(45,10,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(46,10,4,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(47,10,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(48,10,6,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(49,11,1,2,2023,60.00,68.00,64.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(50,11,2,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(51,11,3,2,2023,62.00,68.00,65.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(52,11,4,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(53,11,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(54,11,6,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(55,12,1,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(56,12,2,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(57,12,3,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(58,12,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(59,12,5,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(60,12,6,2,2023,78.00,85.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(61,13,1,2,2023,60.00,65.00,63.00,'C',1.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(62,13,2,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(63,13,3,2,2023,68.00,72.00,70.00,'B',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(64,13,4,2,2023,55.00,62.00,59.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(65,13,5,2,2023,65.00,70.00,68.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(66,13,6,2,2023,50.00,58.00,54.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(67,14,1,2,2023,90.00,95.00,93.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(68,14,2,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(69,14,3,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(70,14,4,2,2023,92.00,96.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(71,14,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(72,14,6,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(73,15,1,2,2023,58.00,68.00,63.00,'C',1.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(74,15,2,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(75,15,3,2,2023,60.00,68.00,64.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(76,15,4,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(77,15,5,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(78,15,6,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(79,16,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(80,16,2,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(81,16,3,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(82,16,4,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(83,16,5,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(84,16,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(85,17,1,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(86,17,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(87,17,3,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(88,17,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(89,17,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(90,17,6,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(91,18,1,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(92,18,2,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(93,18,3,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(94,18,4,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(95,18,5,2,2023,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(96,18,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(97,19,1,2,2023,92.00,96.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(98,19,2,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(99,19,3,2,2023,90.00,95.00,93.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(100,19,4,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(101,19,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(102,19,6,2,2023,92.00,95.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(103,20,1,2,2023,50.00,58.00,54.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(104,20,2,2,2023,70.00,75.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(105,20,3,2,2023,48.00,55.00,52.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(106,20,4,2,2023,68.00,72.00,70.00,'B',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(107,20,5,2,2023,42.00,52.00,47.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(108,20,6,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(109,21,1,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(110,21,2,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(111,21,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(112,21,4,2,2023,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(113,21,5,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(114,21,6,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(115,22,1,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(116,22,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(117,22,3,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(118,22,4,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(119,22,5,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(120,22,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(121,23,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(122,23,2,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(123,23,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(124,23,4,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(125,23,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(126,23,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(127,24,1,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(128,24,2,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(129,24,3,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(130,24,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(131,24,5,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(132,24,6,2,2023,68.00,76.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(133,25,1,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(134,25,2,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(135,25,3,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(136,25,4,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(137,25,5,2,2023,88.00,95.00,92.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(138,25,6,2,2023,78.00,86.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(139,26,1,2,2023,48.00,55.00,52.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(140,26,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(141,26,3,2,2023,45.00,50.00,48.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(142,26,4,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(143,26,5,2,2023,42.00,55.00,49.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(144,26,6,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(145,3,1,3,2024,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(146,3,2,3,2024,78.00,82.00,80.50,'A-',3.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(147,3,3,3,2024,72.00,68.00,70.00,'B',2.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(148,3,4,3,2024,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(149,3,5,3,2024,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(150,3,6,3,2024,65.00,60.00,62.00,'C',1.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(151,3,1,4,2024,70.00,72.00,71.50,'B',2.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(152,3,2,4,2024,55.00,48.00,51.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(153,3,3,4,2024,60.00,58.00,59.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(154,3,4,4,2024,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(155,3,5,4,2024,75.00,70.00,72.00,'B',2.30,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(156,3,6,4,2024,42.00,38.00,40.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(157,3,1,5,2025,58.00,55.00,56.50,'D',0.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(158,3,2,5,2025,65.00,62.00,63.00,'C',1.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(159,3,3,5,2025,50.00,45.00,47.00,'D',0.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(160,3,4,5,2025,78.00,75.00,76.00,'B',2.70,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(161,3,5,5,2025,66.00,64.00,65.00,'C',1.50,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(162,3,7,5,2025,88.00,85.00,86.00,'A-',3.70,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(163,3,8,5,2025,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(164,3,2,6,2025,70.00,NULL,35.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(165,3,3,6,2025,40.00,NULL,20.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(166,3,4,6,2025,85.00,NULL,42.50,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(167,3,6,6,2025,50.00,NULL,25.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(168,4,7,6,2025,93.00,95.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(169,5,7,6,2025,93.00,83.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(170,6,7,6,2024,87.00,81.00,85.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(171,7,7,6,2025,84.00,94.00,92.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(172,8,7,6,2025,74.00,73.00,80.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(173,9,7,6,2025,72.00,82.00,78.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(174,10,7,6,2025,88.00,91.00,86.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(175,11,7,6,2025,74.00,79.00,75.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(176,12,7,6,2025,81.00,93.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(177,13,7,6,2025,68.00,72.00,72.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(178,14,7,6,2025,98.00,86.00,94.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(179,15,7,6,2025,65.00,75.00,70.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(180,16,7,6,2025,78.00,82.00,83.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(181,17,7,6,2025,88.00,92.00,89.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(182,18,7,6,2025,73.00,82.00,76.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(183,19,7,6,2025,96.00,93.00,91.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(184,20,7,6,2025,72.00,74.00,68.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(185,21,7,6,2025,88.00,94.00,87.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(186,22,7,6,2025,71.00,68.00,74.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(187,23,7,6,2025,88.00,89.00,82.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(188,24,7,6,2025,78.00,69.00,73.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(189,25,7,6,2025,95.00,92.00,95.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(190,26,7,6,2025,59.00,67.00,65.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(191,4,8,6,2025,84.00,88.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(192,5,8,6,2025,97.00,83.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(193,6,8,6,2024,76.00,90.00,82.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(194,7,8,6,2025,93.00,89.00,86.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(195,8,8,6,2025,78.00,87.00,84.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(196,9,8,6,2025,69.00,80.00,76.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(197,10,8,6,2025,87.00,82.00,83.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(198,11,8,6,2025,78.00,80.00,74.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(199,12,8,6,2025,87.00,85.00,85.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(200,13,8,6,2025,63.00,71.00,70.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(201,14,8,6,2025,95.00,97.00,92.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(202,15,8,6,2025,70.00,61.00,68.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(203,16,8,6,2025,82.00,81.00,81.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(204,17,8,6,2025,86.00,86.00,87.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(205,18,8,6,2025,86.00,84.00,78.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(206,19,8,6,2025,89.00,91.00,93.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(207,20,8,6,2025,70.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(208,21,8,6,2025,84.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(209,22,8,6,2025,70.00,76.00,72.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(210,23,8,6,2025,85.00,75.00,80.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(211,24,8,6,2025,63.00,75.00,71.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(212,25,8,6,2025,90.00,89.00,96.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(213,26,8,6,2025,58.00,62.00,63.00,'C',1.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(214,4,9,6,2025,81.00,77.00,85.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(215,5,9,6,2025,91.00,80.00,87.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(216,6,9,6,2024,78.00,80.00,83.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(217,7,9,6,2025,84.00,90.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(218,8,9,6,2025,87.00,81.00,79.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(219,9,9,6,2025,67.00,73.00,74.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(220,10,9,6,2025,88.00,83.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(221,11,9,6,2025,72.00,79.00,72.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(222,12,9,6,2025,86.00,90.00,86.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(223,13,9,6,2025,72.00,61.00,68.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(224,14,9,6,2025,87.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(225,15,9,6,2025,68.00,72.00,66.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(226,16,9,6,2025,74.00,84.00,80.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(227,17,9,6,2025,84.00,91.00,91.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(228,18,9,6,2025,71.00,68.00,75.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(229,19,9,6,2025,94.00,90.00,94.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(230,20,9,6,2025,68.00,62.00,64.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(231,21,9,6,2025,84.00,80.00,82.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(232,22,9,6,2025,62.00,67.00,70.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(233,23,9,6,2025,76.00,76.00,78.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(234,24,9,6,2025,77.00,67.00,69.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(235,25,9,6,2025,97.00,96.00,92.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(236,26,9,6,2025,59.00,54.00,61.00,'C',1.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(237,4,10,6,2025,88.00,91.00,92.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(238,5,10,6,2025,92.00,82.00,86.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(239,6,10,6,2024,83.00,77.00,84.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(240,7,10,6,2025,84.00,88.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(241,8,10,6,2025,74.00,81.00,81.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(242,9,10,6,2025,80.00,71.00,77.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(243,10,10,6,2025,90.00,84.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(244,11,10,6,2025,80.00,72.00,73.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(245,12,10,6,2025,83.00,80.00,87.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(246,13,10,6,2025,75.00,62.00,69.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(247,14,10,6,2025,86.00,92.00,91.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(248,15,10,6,2025,60.00,64.00,67.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(249,16,10,6,2025,74.00,80.00,82.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(250,17,10,6,2025,85.00,82.00,89.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(251,18,10,6,2025,73.00,69.00,77.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(252,19,10,6,2025,90.00,89.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(253,20,10,6,2025,73.00,65.00,65.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(254,21,10,6,2025,88.00,89.00,85.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(255,22,10,6,2025,70.00,71.00,71.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(256,23,10,6,2025,76.00,77.00,79.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(257,24,10,6,2025,62.00,73.00,70.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(258,25,10,6,2025,86.00,101.00,93.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25'),(259,26,10,6,2025,57.00,69.00,62.00,'C',1.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:41:25');
/*!40000 ALTER TABLE `scores_bak_prefix7_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `scores_bak_sem9_20260916`
--

DROP TABLE IF EXISTS `scores_bak_sem9_20260916`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scores_bak_sem9_20260916` (
  `id` bigint NOT NULL DEFAULT '0',
  `student_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `semester` int NOT NULL,
  `year` int DEFAULT NULL,
  `regular_score` decimal(5,2) DEFAULT NULL,
  `final_score` decimal(5,2) DEFAULT NULL,
  `score_total` decimal(5,2) NOT NULL,
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `grade_point` decimal(5,2) DEFAULT NULL,
  `reason_for_change` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scores_bak_sem9_20260916`
--

LOCK TABLES `scores_bak_sem9_20260916` WRITE;
/*!40000 ALTER TABLE `scores_bak_sem9_20260916` DISABLE KEYS */;
INSERT INTO `scores_bak_sem9_20260916` VALUES (1,3,1,2,2023,78.00,85.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(2,3,2,2,2023,80.00,82.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(3,3,3,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(4,3,4,2,2023,90.00,88.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(5,3,5,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(6,3,6,2,2023,70.00,78.00,74.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(7,4,1,2,2023,92.00,95.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(8,4,2,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(9,4,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(10,4,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(11,4,5,2,2023,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(12,4,6,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(13,5,1,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(14,5,2,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(15,5,3,2,2023,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(16,5,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(17,5,5,2,2023,90.00,93.00,92.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(18,5,6,2,2023,80.00,84.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(19,6,1,2,2022,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(20,6,2,2,2022,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(21,6,3,2,2022,70.00,78.00,74.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(22,6,4,2,2022,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(23,6,5,2,2022,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(24,6,6,2,2022,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(25,7,1,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(26,7,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(27,7,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(28,7,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(29,7,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(30,7,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(31,8,1,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(32,8,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(33,8,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(34,8,4,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(35,8,5,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(36,8,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(37,9,1,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(38,9,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(39,9,3,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(40,9,4,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(41,9,5,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(42,9,6,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(43,10,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(44,10,2,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(45,10,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(46,10,4,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(47,10,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(48,10,6,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(49,11,1,2,2023,60.00,68.00,64.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(50,11,2,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(51,11,3,2,2023,62.00,68.00,65.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(52,11,4,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(53,11,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(54,11,6,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(55,12,1,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(56,12,2,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(57,12,3,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(58,12,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(59,12,5,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(60,12,6,2,2023,78.00,85.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(61,13,1,2,2023,60.00,65.00,63.00,'C',1.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(62,13,2,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(63,13,3,2,2023,68.00,72.00,70.00,'B',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(64,13,4,2,2023,55.00,62.00,59.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(65,13,5,2,2023,65.00,70.00,68.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(66,13,6,2,2023,50.00,58.00,54.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(67,14,1,2,2023,90.00,95.00,93.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(68,14,2,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(69,14,3,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(70,14,4,2,2023,92.00,96.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(71,14,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(72,14,6,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(73,15,1,2,2023,58.00,68.00,63.00,'C',1.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(74,15,2,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(75,15,3,2,2023,60.00,68.00,64.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(76,15,4,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(77,15,5,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(78,15,6,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(79,16,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(80,16,2,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(81,16,3,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(82,16,4,2,2023,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(83,16,5,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(84,16,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(85,17,1,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(86,17,2,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(87,17,3,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(88,17,4,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(89,17,5,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(90,17,6,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(91,18,1,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(92,18,2,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(93,18,3,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(94,18,4,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(95,18,5,2,2023,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(96,18,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(97,19,1,2,2023,92.00,96.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(98,19,2,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(99,19,3,2,2023,90.00,95.00,93.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(100,19,4,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(101,19,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(102,19,6,2,2023,92.00,95.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(103,20,1,2,2023,50.00,58.00,54.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(104,20,2,2,2023,70.00,75.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(105,20,3,2,2023,48.00,55.00,52.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(106,20,4,2,2023,68.00,72.00,70.00,'B',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(107,20,5,2,2023,42.00,52.00,47.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(108,20,6,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(109,21,1,2,2023,82.00,86.00,84.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(110,21,2,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(111,21,3,2,2023,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(112,21,4,2,2023,80.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(113,21,5,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(114,21,6,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(115,22,1,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(116,22,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(117,22,3,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(118,22,4,2,2023,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(119,22,5,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(120,22,6,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(121,23,1,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(122,23,2,2,2023,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(123,23,3,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(124,23,4,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(125,23,5,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(126,23,6,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(127,24,1,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(128,24,2,2,2023,70.00,76.00,73.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(129,24,3,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(130,24,4,2,2023,78.00,82.00,80.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(131,24,5,2,2023,72.00,78.00,75.00,'B',2.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(132,24,6,2,2023,68.00,76.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(133,25,1,2,2023,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(134,25,2,2,2023,85.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(135,25,3,2,2023,78.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(136,25,4,2,2023,82.00,88.00,85.00,'A-',3.70,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(137,25,5,2,2023,88.00,95.00,92.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(138,25,6,2,2023,78.00,86.00,82.00,'A-',3.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(139,26,1,2,2023,48.00,55.00,52.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(140,26,2,2,2023,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(141,26,3,2,2023,45.00,50.00,48.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(142,26,4,2,2023,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(143,26,5,2,2023,42.00,55.00,49.00,'D',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(144,26,6,2,2023,62.00,70.00,66.00,'C',1.50,NULL,NULL,'2026-05-27 02:00:00','2026-09-16 12:36:43'),(145,3,1,3,2024,85.00,88.00,87.00,'A-',3.70,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(146,3,2,3,2024,78.00,82.00,80.50,'A-',3.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(147,3,3,3,2024,72.00,68.00,70.00,'B',2.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(148,3,4,3,2024,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(149,3,5,3,2024,88.00,90.00,89.00,'A-',3.70,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(150,3,6,3,2024,65.00,60.00,62.00,'C',1.00,NULL,NULL,'2024-06-20 02:00:00','2026-09-16 12:36:43'),(151,3,1,4,2024,70.00,72.00,71.50,'B',2.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(152,3,2,4,2024,55.00,48.00,51.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(153,3,3,4,2024,60.00,58.00,59.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(154,3,4,4,2024,82.00,85.00,84.00,'A-',3.30,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(155,3,5,4,2024,75.00,70.00,72.00,'B',2.30,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(156,3,6,4,2024,42.00,38.00,40.00,'D',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-09-16 12:36:43'),(157,3,1,5,2025,58.00,55.00,56.50,'D',0.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(158,3,2,5,2025,65.00,62.00,63.00,'C',1.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(159,3,3,5,2025,50.00,45.00,47.00,'D',0.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(160,3,4,5,2025,78.00,75.00,76.00,'B',2.70,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(161,3,5,5,2025,66.00,64.00,65.00,'C',1.50,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(162,3,7,5,2025,88.00,85.00,86.00,'A-',3.70,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(163,3,8,5,2025,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2025-06-19 16:00:00','2026-09-16 12:36:43'),(164,3,2,6,2025,70.00,NULL,35.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(165,3,3,6,2025,40.00,NULL,20.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(166,3,4,6,2025,85.00,NULL,42.50,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(167,3,6,6,2025,50.00,NULL,25.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(168,4,7,6,2025,89.00,91.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(169,5,7,6,2025,93.00,83.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(170,6,7,6,2024,88.00,82.00,85.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(171,7,7,6,2025,87.00,97.00,92.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(172,8,7,6,2025,80.00,79.00,80.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(173,9,7,6,2025,73.00,83.00,78.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(174,10,7,6,2025,84.00,87.00,86.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(175,11,7,6,2025,72.00,77.00,75.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(176,12,7,6,2025,82.00,94.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(177,13,7,6,2025,70.00,74.00,72.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(178,14,7,6,2025,100.00,88.00,94.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(179,15,7,6,2025,65.00,75.00,70.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(180,16,7,6,2025,81.00,85.00,83.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(181,17,7,6,2025,87.00,91.00,89.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(182,18,7,6,2025,71.00,80.00,76.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(183,19,7,6,2025,92.00,89.00,91.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(184,20,7,6,2025,67.00,69.00,68.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(185,21,7,6,2025,84.00,90.00,87.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(186,22,7,6,2025,75.00,72.00,74.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(187,23,7,6,2025,81.00,82.00,82.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(188,24,7,6,2025,77.00,68.00,73.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(189,25,7,6,2025,96.00,93.00,95.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(190,26,7,6,2025,61.00,69.00,65.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(191,4,8,6,2025,86.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(192,5,8,6,2025,97.00,83.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(193,6,8,6,2024,75.00,89.00,82.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(194,7,8,6,2025,88.00,84.00,86.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(195,8,8,6,2025,79.00,88.00,84.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(196,9,8,6,2025,70.00,81.00,76.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(197,10,8,6,2025,85.00,80.00,83.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(198,11,8,6,2025,73.00,75.00,74.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(199,12,8,6,2025,86.00,84.00,85.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(200,13,8,6,2025,66.00,74.00,70.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(201,14,8,6,2025,91.00,93.00,92.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(202,15,8,6,2025,72.00,63.00,68.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(203,16,8,6,2025,81.00,80.00,81.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(204,17,8,6,2025,87.00,87.00,87.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(205,18,8,6,2025,79.00,77.00,78.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(206,19,8,6,2025,92.00,94.00,93.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(207,20,8,6,2025,66.00,66.00,66.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(208,21,8,6,2025,83.00,85.00,84.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(209,22,8,6,2025,69.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(210,23,8,6,2025,85.00,75.00,80.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(211,24,8,6,2025,65.00,77.00,71.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(212,25,8,6,2025,96.00,95.00,96.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(213,26,8,6,2025,61.00,65.00,63.00,'C',1.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(214,4,9,6,2025,87.00,83.00,85.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(215,5,9,6,2025,92.00,81.00,87.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(216,6,9,6,2024,82.00,84.00,83.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(217,7,9,6,2025,87.00,93.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(218,8,9,6,2025,82.00,76.00,79.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(219,9,9,6,2025,71.00,77.00,74.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(220,10,9,6,2025,90.00,85.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(221,11,9,6,2025,68.00,75.00,72.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(222,12,9,6,2025,84.00,88.00,86.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(223,13,9,6,2025,73.00,62.00,68.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(224,14,9,6,2025,87.00,90.00,89.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(225,15,9,6,2025,64.00,68.00,66.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(226,16,9,6,2025,75.00,85.00,80.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(227,17,9,6,2025,87.00,94.00,91.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(228,18,9,6,2025,76.00,73.00,75.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(229,19,9,6,2025,96.00,92.00,94.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(230,20,9,6,2025,67.00,61.00,64.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(231,21,9,6,2025,84.00,80.00,82.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(232,22,9,6,2025,67.00,72.00,70.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(233,23,9,6,2025,78.00,78.00,78.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(234,24,9,6,2025,74.00,64.00,69.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(235,25,9,6,2025,92.00,91.00,92.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(236,26,9,6,2025,63.00,58.00,61.00,'C',1.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(237,4,10,6,2025,90.00,93.00,92.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(238,5,10,6,2025,91.00,81.00,86.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(239,6,10,6,2024,87.00,81.00,84.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(240,7,10,6,2025,86.00,90.00,88.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(241,8,10,6,2025,77.00,84.00,81.00,'A-',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(242,9,10,6,2025,81.00,72.00,77.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(243,10,10,6,2025,93.00,87.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(244,11,10,6,2025,77.00,69.00,73.00,'B',2.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(245,12,10,6,2025,88.00,85.00,87.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(246,13,10,6,2025,75.00,62.00,69.00,'C',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(247,14,10,6,2025,88.00,94.00,91.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(248,15,10,6,2025,65.00,69.00,67.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(249,16,10,6,2025,79.00,85.00,82.00,'A-',3.30,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(250,17,10,6,2025,90.00,87.00,89.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(251,18,10,6,2025,79.00,75.00,77.00,'B',2.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(252,19,10,6,2025,90.00,89.00,90.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(253,20,10,6,2025,69.00,61.00,65.00,'C',1.50,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(254,21,10,6,2025,84.00,85.00,85.00,'A-',3.70,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(255,22,10,6,2025,70.00,71.00,71.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:31:35'),(256,23,10,6,2025,78.00,79.00,79.00,'B',3.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(257,24,10,6,2025,64.00,75.00,70.00,'B',2.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(258,25,10,6,2025,85.00,100.00,93.00,'A',4.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25'),(259,26,10,6,2025,56.00,68.00,62.00,'C',1.00,NULL,NULL,'2026-09-16 13:31:35','2026-09-16 13:37:25');
/*!40000 ALTER TABLE `scores_bak_sem9_20260916` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `security_logs`
--

DROP TABLE IF EXISTS `security_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `security_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `ip_address` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `login_time` timestamp NULL DEFAULT NULL,
  `is_successful` tinyint(1) DEFAULT '1',
  `action` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_security_logs_user_id` (`user_id`),
  CONSTRAINT `fk_security_logs_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `security_logs`
--

LOCK TABLES `security_logs` WRITE;
/*!40000 ALTER TABLE `security_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `security_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_profile`
--

DROP TABLE IF EXISTS `student_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `student_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学号',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别',
  `college_id` bigint DEFAULT NULL,
  `major_id` bigint DEFAULT NULL,
  `class_id` bigint DEFAULT NULL,
  `class_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enrollment_year` int DEFAULT NULL COMMENT '入学年份',
  `privacy_level` tinyint(1) DEFAULT '2',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_profile_user_id` (`user_id`),
  UNIQUE KEY `uk_student_profile_student_id` (`student_id`),
  KEY `idx_student_profile_college_id` (`college_id`),
  KEY `idx_student_profile_class_id` (`class_id`),
  KEY `idx_student_profile_major_id` (`major_id`),
  CONSTRAINT `fk_student_profile_class_id` FOREIGN KEY (`class_id`) REFERENCES `classes` (`id`),
  CONSTRAINT `fk_student_profile_college_id` FOREIGN KEY (`college_id`) REFERENCES `colleges` (`id`),
  CONSTRAINT `fk_student_profile_major_id` FOREIGN KEY (`major_id`) REFERENCES `majors` (`id`),
  CONSTRAINT `fk_student_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=40 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_profile`
--

LOCK TABLES `student_profile` WRITE;
/*!40000 ALTER TABLE `student_profile` DISABLE KEYS */;
INSERT INTO `student_profile` VALUES (3,27,'2023020616','司明',NULL,1,2,1,'计科2306班',2023,0,'2026-03-04 02:43:20','2026-10-03 14:31:54'),(4,30,'2023020615','邱琳紫',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(5,31,'2023020617','宋新鹏',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(6,32,'2022020137','赵琦',NULL,1,2,1,'计科2306班',2022,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(7,33,'2023020619','苏红阳',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(8,34,'2023020620','唐浩然',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(9,35,'2023020621','王宏民',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(10,36,'2023020624','王玉蛟',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(11,37,'2023020625','吴雨航',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(12,38,'2023020626','徐瑞瑶',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(13,39,'2023020607','韩坤',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(14,40,'2023020629','张博巍',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(15,41,'2023020504','程启頔',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(16,42,'2023020505','邓雨轩',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(17,43,'2023020506','高菲',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(18,44,'2023020507','胡钰',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(19,45,'2023020509','李佳芮',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(20,46,'2023020510','李爽',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(21,47,'2023020512','梁莹静',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(22,48,'2023020515','孟阳',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(23,49,'2023020519','王瑾泰',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(24,50,'2023020520','王平洋',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(25,51,'2023020521','夏志浩',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43'),(26,52,'2023020524','张健',NULL,1,2,1,'计科2306班',2023,2,'2026-03-05 03:00:29','2026-09-16 12:36:43');
/*!40000 ALTER TABLE `student_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `student_settings`
--

DROP TABLE IF EXISTS `student_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `student_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `notification_enabled` tinyint(1) DEFAULT '1',
  `email_notifications` tinyint(1) DEFAULT '1',
  `theme` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'light',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_student_settings_student_id` (`student_id`),
  CONSTRAINT `fk_student_settings_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_settings`
--

LOCK TABLES `student_settings` WRITE;
/*!40000 ALTER TABLE `student_settings` DISABLE KEYS */;
INSERT INTO `student_settings` VALUES (3,3,1,1,'light','2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,4,1,1,'light','2026-05-27 02:00:00','2026-05-27 02:00:00'),(5,5,1,0,'dark','2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `student_settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `subscription_preferences`
--

DROP TABLE IF EXISTS `subscription_preferences`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscription_preferences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `subscribe_warnings` tinyint(1) DEFAULT '1',
  `subscribe_low` tinyint(1) DEFAULT '0',
  `subscribe_medium` tinyint(1) DEFAULT '1',
  `subscribe_high` tinyint(1) DEFAULT '1',
  `subscribe_assistance` tinyint(1) DEFAULT '1',
  `subscribe_system` tinyint(1) DEFAULT '1',
  `push_email` tinyint(1) DEFAULT '1',
  `push_sms` tinyint(1) DEFAULT '0',
  `push_app` tinyint(1) DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subscription_preferences_student_id` (`student_id`),
  CONSTRAINT `fk_subscription_preferences_student_id` FOREIGN KEY (`student_id`) REFERENCES `student_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `subscription_preferences`
--

LOCK TABLES `subscription_preferences` WRITE;
/*!40000 ALTER TABLE `subscription_preferences` DISABLE KEYS */;
INSERT INTO `subscription_preferences` VALUES (1,3,1,0,1,1,1,1,1,0,1,'2026-05-27 02:00:00','2026-10-01 14:52:00'),(2,4,1,1,1,1,1,1,1,1,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,5,1,0,1,1,1,0,1,0,1,'2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `subscription_preferences` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `teacher_class_application`
--

DROP TABLE IF EXISTS `teacher_class_application`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_class_application` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL,
  `class_id` bigint NOT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  `status` int DEFAULT '0' COMMENT '0-待审核, 1-通过, 2-拒绝',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `teacher_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `teacher_username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `application_type` int DEFAULT '0' COMMENT '申请类型：0-申请管理，1-解除管理',
  `reject_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拒绝原因',
  PRIMARY KEY (`id`),
  KEY `idx_teacher_id` (`teacher_id`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_status` (`status`),
  KEY `idx_application_type` (`application_type`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师班级管理申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `teacher_class_application`
--

LOCK TABLES `teacher_class_application` WRITE;
/*!40000 ALTER TABLE `teacher_class_application` DISABLE KEYS */;
INSERT INTO `teacher_class_application` VALUES (1,29,5,'申请信息填写有误，撤销后重新提交',3,'2026-05-18 12:25:45','2026-09-16 11:23:16','软工2306班','于凤海','10001',0,NULL),(2,29,5,'申请管理软工2306班',2,'2026-05-18 13:56:53','2026-09-16 11:23:16','软工2306班','于凤海','10001',0,'该班本学期已安排班主任，暂不受理'),(9,29,5,'本学期教学任务调整，再次申请管理软工2306班',1,'2026-05-18 15:05:04','2026-09-16 11:23:16','软工2306班','于凤海','10001',0,NULL),(10,29,5,'因教学与科研任务繁重，申请解除软工2306班的管理工作',1,'2026-05-18 15:34:01','2026-09-16 11:23:16','软工2306班','于凤海','10001',1,NULL),(11,29,30,'申请管理计算机2101班',1,'2026-05-18 17:11:05','2026-09-16 11:23:16','计算机2101班','于凤海','10001',0,NULL),(13,29,32,'申请管理软件工程2101班',2,'2026-05-20 09:49:02','2026-09-16 11:23:16','软件工程2101班','于凤海','10001',0,'该班已由刘教授担任班主任，暂不调整');
/*!40000 ALTER TABLE `teacher_class_application` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `teacher_course`
--

DROP TABLE IF EXISTS `teacher_course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_course` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `teacher_id` bigint NOT NULL,
  `course_id` bigint NOT NULL,
  `semester` int DEFAULT NULL,
  `year` int DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_course` (`teacher_id`,`course_id`),
  KEY `idx_teacher_course_teacher_id` (`teacher_id`),
  KEY `idx_teacher_course_course_id` (`course_id`),
  CONSTRAINT `fk_teacher_course_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `fk_teacher_course_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profile` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `teacher_course`
--

LOCK TABLES `teacher_course` WRITE;
/*!40000 ALTER TABLE `teacher_course` DISABLE KEYS */;
INSERT INTO `teacher_course` VALUES (3,1,1,NULL,NULL,'2026-06-25 00:45:00','2026-06-25 00:45:00'),(4,1,10,NULL,NULL,'2026-06-25 00:45:00','2026-06-25 00:45:00'),(6,2,2,NULL,NULL,'2026-06-25 00:45:43','2026-06-25 00:45:43'),(7,3,3,NULL,NULL,'2026-06-25 00:45:43','2026-06-25 00:45:43'),(8,1,4,NULL,NULL,'2026-06-25 00:45:43','2026-06-25 00:45:43'),(9,3,5,NULL,NULL,'2026-06-25 00:45:43','2026-06-25 00:45:43'),(13,5,9,NULL,NULL,'2026-06-25 00:45:43','2026-06-25 00:45:43');
/*!40000 ALTER TABLE `teacher_course` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `teacher_profile`
--

DROP TABLE IF EXISTS `teacher_profile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teacher_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `college_id` bigint NOT NULL,
  `title` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `research_areas` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_profile_user_id` (`user_id`),
  KEY `idx_teacher_profile_college_id` (`college_id`),
  CONSTRAINT `fk_teacher_profile_college_id` FOREIGN KEY (`college_id`) REFERENCES `colleges` (`id`),
  CONSTRAINT `fk_teacher_profile_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `teacher_profile`
--

LOCK TABLES `teacher_profile` WRITE;
/*!40000 ALTER TABLE `teacher_profile` DISABLE KEYS */;
INSERT INTO `teacher_profile` VALUES (1,29,'于凤海',1,NULL,NULL,'2026-05-21 09:27:04','2026-05-21 09:27:04'),(2,19,'王老师',1,'副教授','计算机科学','2025-12-29 23:09:40','2025-12-29 23:09:40'),(3,22,'李老师',1,'讲师','数据科学','2025-12-29 23:10:00','2025-12-29 23:10:00'),(5,28,'赵振江',1,'讲师','软件工程','2026-03-04 03:56:54','2026-06-26 03:29:09'),(7,55,'陈志强',2,NULL,NULL,'2026-06-23 08:22:47','2026-06-26 03:29:18'),(8,18,'刘教授',1,'教授','软件工程','2026-06-27 00:31:06','2026-06-27 00:31:06');
/*!40000 ALTER TABLE `teacher_profile` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tmp_f10_class`
--

DROP TABLE IF EXISTS `tmp_f10_class`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tmp_f10_class` (
  `class_id` bigint NOT NULL,
  `new_major` bigint NOT NULL,
  `new_college` bigint NOT NULL,
  PRIMARY KEY (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tmp_f10_class`
--

LOCK TABLES `tmp_f10_class` WRITE;
/*!40000 ALTER TABLE `tmp_f10_class` DISABLE KEYS */;
INSERT INTO `tmp_f10_class` VALUES (1,2,1),(5,1,1),(30,2,1),(31,2,1),(32,1,1),(33,1,1),(34,8,2),(35,8,2);
/*!40000 ALTER TABLE `tmp_f10_class` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tmp_f10_course_teacher`
--

DROP TABLE IF EXISTS `tmp_f10_course_teacher`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tmp_f10_course_teacher` (
  `course_id` bigint NOT NULL,
  `new_teacher` bigint NOT NULL,
  PRIMARY KEY (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tmp_f10_course_teacher`
--

LOCK TABLES `tmp_f10_course_teacher` WRITE;
/*!40000 ALTER TABLE `tmp_f10_course_teacher` DISABLE KEYS */;
INSERT INTO `tmp_f10_course_teacher` VALUES (1,29),(2,19),(3,22),(4,29),(5,22),(6,18),(7,28),(8,28),(9,28),(10,29);
/*!40000 ALTER TABLE `tmp_f10_course_teacher` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tmp_f11_user_contact`
--

DROP TABLE IF EXISTS `tmp_f11_user_contact`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tmp_f11_user_contact` (
  `user_id` bigint NOT NULL,
  `new_email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `new_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tmp_f11_user_contact`
--

LOCK TABLES `tmp_f11_user_contact` WRITE;
/*!40000 ALTER TABLE `tmp_f11_user_contact` DISABLE KEYS */;
INSERT INTO `tmp_f11_user_contact` VALUES (16,'admin@test.com','13800000003'),(27,'2023020616@stu.academic.edu','15200000001'),(30,'2023020615@stu.academic.edu','15200000002'),(31,'2023020617@stu.academic.edu','15200000003'),(32,'2022020137@stu.academic.edu','15200000004'),(33,'2023020619@stu.academic.edu','15200000005'),(34,'2023020620@stu.academic.edu','15200000006'),(35,'2023020621@stu.academic.edu','15200000007'),(36,'2023020624@stu.academic.edu','15200000008'),(37,'2023020625@stu.academic.edu','15200000009'),(38,'2023020626@stu.academic.edu','15200000010'),(39,'2023020607@stu.academic.edu','15200000011'),(40,'2023020629@stu.academic.edu','15200000012'),(41,'2023020504@stu.academic.edu','15200000013'),(42,'2023020505@stu.academic.edu','15200000014'),(43,'2023020506@stu.academic.edu','15200000015'),(44,'2023020507@stu.academic.edu','15200000016'),(45,'2023020509@stu.academic.edu','15200000017'),(46,'2023020510@stu.academic.edu','15200000018'),(47,'2023020512@stu.academic.edu','15200000019'),(48,'2023020515@stu.academic.edu','15200000020'),(49,'2023020519@stu.academic.edu','15200000021'),(50,'2023020520@stu.academic.edu','15200000022'),(51,'2023020521@stu.academic.edu','15200000023'),(52,'2023020524@stu.academic.edu','15200000024'),(53,'counselor001@test.com','13800000014'),(55,'teacher006@test.com','13800000013'),(56,'limy@academic.edu','13900000002'),(57,'zhangjg@academic.edu','13900000001');
/*!40000 ALTER TABLE `tmp_f11_user_contact` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_settings`
--

DROP TABLE IF EXISTS `user_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_settings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT 'users.id（不是 student_profile.id）',
  `warning_notification` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收预警通知',
  `system_message` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收系统消息',
  `class_notification` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收班级通知',
  `student_feedback` tinyint(1) NOT NULL DEFAULT '1' COMMENT '接收学生反馈',
  `email_reminder` tinyint(1) NOT NULL DEFAULT '0' COMMENT '邮件提醒',
  `office_location` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公地点',
  `office_phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公电话',
  `office_hours` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '办公时间',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_settings_user_id` (`user_id`),
  CONSTRAINT `fk_user_settings_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_settings`
--

LOCK TABLES `user_settings` WRITE;
/*!40000 ALTER TABLE `user_settings` DISABLE KEYS */;
INSERT INTO `user_settings` VALUES (2,53,1,1,1,1,0,'实训楼A201','','周一至周五 9:00-17:00','2026-09-21 09:54:08','2026-09-21 10:08:54'),(3,18,1,1,1,1,0,'实训楼B301','','周一至周五 9:00-17:00','2026-09-21 09:57:24','2026-09-21 10:08:44');
/*!40000 ALTER TABLE `user_settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` tinyint(1) NOT NULL COMMENT '1-学生, 2-教师, 3-辅导员, 4-管理员',
  `status` tinyint(1) DEFAULT '1',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_users_username` (`username`),
  KEY `idx_users_role` (`role`),
  KEY `idx_users_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'admin','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','系统管理员','admin@academic.edu','13800000000',4,1,'2025-12-22 17:28:15','2025-12-22 17:28:15',NULL),(16,'adm001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','夏志浩','admin@test.com','13800000003',4,1,'2025-12-22 17:28:15','2026-09-16 14:23:16',NULL),(18,'teacher001','$2a$10$W4zlhXKkn8zf/vnljhX3gegNikhnpqwVxRjUpT2vNwezbJZ8MB/YG','刘教授','teacher001@test.com','13800000010',2,1,'2025-12-22 18:18:22','2026-10-10 02:40:50',NULL),(19,'teacher002','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王老师','teacher002@test.com','13800000011',2,1,'2025-12-29 23:09:40','2025-12-29 23:09:40',NULL),(22,'teacher003','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李老师','teacher003@test.com','13800000012',2,1,'2025-12-29 23:10:00','2025-12-29 23:10:00',NULL),(27,'2023020616','$2a$10$W4zlhXKkn8zf/vnljhX3gegNikhnpqwVxRjUpT2vNwezbJZ8MB/YG','司明','2023020616@stu.academic.edu','15200000001',1,1,'2026-03-04 02:43:20','2026-09-16 14:23:16',NULL),(28,'teacher005','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','赵振江','23467675@qq.com','16744343245',2,1,'2026-03-04 03:56:54','2026-09-16 14:23:16',NULL),(29,'10001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','于凤海','13397583736@qq.com','18845894565',2,1,'2026-03-04 03:58:09','2026-06-23 07:47:58',NULL),(30,'2023020615','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','邱琳紫','2023020615@stu.academic.edu','15200000002',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(31,'2023020617','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','宋新鹏','2023020617@stu.academic.edu','15200000003',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(32,'2022020137','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','赵琦','2022020137@stu.academic.edu','15200000004',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(33,'2023020619','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','苏红阳','2023020619@stu.academic.edu','15200000005',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(34,'2023020620','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','唐浩然','2023020620@stu.academic.edu','15200000006',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(35,'2023020621','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王宏民','2023020621@stu.academic.edu','15200000007',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(36,'2023020624','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王玉蛟','2023020624@stu.academic.edu','15200000008',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(37,'2023020625','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','吴雨航','2023020625@stu.academic.edu','15200000009',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(38,'2023020626','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','徐瑞瑶','2023020626@stu.academic.edu','15200000010',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(39,'2023020607','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','韩坤','2023020607@stu.academic.edu','15200000011',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(40,'2023020629','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张博巍','2023020629@stu.academic.edu','15200000012',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(41,'2023020504','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','程启頔','2023020504@stu.academic.edu','15200000013',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(42,'2023020505','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','邓雨轩','2023020505@stu.academic.edu','15200000014',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(43,'2023020506','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','高菲','2023020506@stu.academic.edu','15200000015',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(44,'2023020507','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','胡钰','2023020507@stu.academic.edu','15200000016',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(45,'2023020509','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李佳芮','2023020509@stu.academic.edu','15200000017',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(46,'2023020510','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李爽','2023020510@stu.academic.edu','15200000018',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(47,'2023020512','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','梁莹静','2023020512@stu.academic.edu','15200000019',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(48,'2023020515','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','孟阳','2023020515@stu.academic.edu','15200000020',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(49,'2023020519','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王瑾泰','2023020519@stu.academic.edu','15200000021',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(50,'2023020520','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王平洋','2023020520@stu.academic.edu','15200000022',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(51,'2023020521','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','夏志浩','2023020521@stu.academic.edu','15200000023',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(52,'2023020524','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张健','2023020524@stu.academic.edu','15200000024',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(53,'1000000001','$2a$10$I2xb29uVTjkUBSGQnAh3n.eL/1x1M6GS4IZsTVhyUfDegg3y3JBU2','司明','counselor001@test.com','13800000014',3,1,'2026-05-14 02:22:05','2026-09-21 08:34:21',NULL),(55,'teacher006','$2a$10$YN5bO.E.tdnGFT7.tPOvk.kMnDr1pq/6.jBSHDmwJ7S4vi2Nowmbi','陈志强','teacher006@test.com','13800000013',2,1,'2026-06-23 08:22:47','2026-09-16 14:23:16',NULL),(56,'collegeadmin2','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李明远','limy@academic.edu','13900000002',5,1,'2026-06-26 01:46:01','2026-09-16 14:23:16',NULL),(57,'collegeadmin1','$2a$10$J0B4EaXkHpxjln54HwTCZOsYTYLUKrxs1R6vfJkxfpXB8JQW9mNPW','张建国','zhangjg@academic.edu','13900000001',5,1,'2026-06-26 01:47:52','2026-09-16 14:23:16',NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `warning_release_records`
--

DROP TABLE IF EXISTS `warning_release_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warning_release_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `warning_id` bigint NOT NULL COMMENT '关联预警记录ID',
  `student_id` bigint NOT NULL COMMENT '学生ID',
  `release_reason` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '解除原因：makeup_pass-补考通过, retake_pass-重修合格, other-其他',
  `release_note` text COLLATE utf8mb4_unicode_ci COMMENT '解除备注',
  `release_by` bigint DEFAULT NULL COMMENT '解除人ID',
  `release_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '解除时间',
  PRIMARY KEY (`id`),
  KEY `idx_warning_id` (`warning_id`),
  KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警解除记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `warning_release_records`
--

LOCK TABLES `warning_release_records` WRITE;
/*!40000 ALTER TABLE `warning_release_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `warning_release_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `warning_rules`
--

DROP TABLE IF EXISTS `warning_rules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warning_rules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '规则描述',
  `level` int DEFAULT '1' COMMENT '预警等级：1-低, 2-中, 3-高',
  `rule_condition` text COLLATE utf8mb4_unicode_ci COMMENT '触发条件表达式',
  `status` int DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `rule_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规则编码',
  `created_by` bigint DEFAULT NULL COMMENT '创建人ID',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人ID',
  `fail_definition` text COLLATE utf8mb4_unicode_ci COMMENT '挂科定义标准',
  `statistic_cycle` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '统计周期',
  `trigger_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '触发机制',
  `release_condition` text COLLATE utf8mb4_unicode_ci COMMENT '预警解除条件',
  `intervention_plan` text COLLATE utf8mb4_unicode_ci COMMENT '学业干预方案',
  `notification_flow` text COLLATE utf8mb4_unicode_ci COMMENT '通知流程',
  `responsible_person` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '责任人',
  `tracking_mechanism` text COLLATE utf8mb4_unicode_ci COMMENT '跟踪反馈机制',
  `archive_spec` text COLLATE utf8mb4_unicode_ci COMMENT '归档规范',
  `data_caliber` text COLLATE utf8mb4_unicode_ci COMMENT '数据统计口径',
  `department_responsibility` text COLLATE utf8mb4_unicode_ci COMMENT '部门职责分工',
  `appeal_process` text COLLATE utf8mb4_unicode_ci COMMENT '申诉复议流程',
  `exception_handling` text COLLATE utf8mb4_unicode_ci COMMENT '例外情况处理办法',
  PRIMARY KEY (`id`),
  KEY `idx_warning_level` (`level`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警规则表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `warning_rules`
--

LOCK TABLES `warning_rules` WRITE;
/*!40000 ALTER TABLE `warning_rules` DISABLE KEYS */;
INSERT INTO `warning_rules` VALUES (1,'低级预警','学生在所有学期内累计挂科次数为1-2科（含2科）',1,'fail_count >= 1 AND fail_count <= 2',1,'2026-05-20 11:14:57','2026-05-20 11:14:57','RULE001',NULL,NULL,'课程考核成绩低于60分（不含60分）视为不及格；未参加考试（缺考）视为挂科；考试作弊取消成绩视为挂科','full_program','periodic','补考通过或重修合格后自动解除预警','1. 教师谈话提醒；2. 学习计划调整建议；3. 每周学习进度跟踪','通知对象：教师、学生。流程：系统自动生成预警，发送通知给教师和学生，教师在3个工作日内约谈学生','教师','教师每周跟踪一次，记录学生学习状态变化，每月汇总上报学院','预警记录保存至学生毕业离校后5年，电子档案与纸质档案同步归档','挂科次数统计范围：学生入学以来所有学期的不及格课程（不含已通过补考/重修的课程）','1. 教务处：负责数据统计和规则维护；2. 学院：负责预警通知和学业干预；3. 教师：负责跟踪学生；4. 学生：配合干预措施','学生对预警结果有异议的，可在收到通知后5个工作日内向学院提交申诉材料，学院在10个工作日内给出复议结果','因不可抗力导致的挂科可申请特殊处理，需提供相关证明材料，经学院审核批准后可不计入挂科统计'),(2,'中级预警','学生在所有学期内累计挂科次数为3-5科（含3科和5科）',2,'fail_count >= 3 AND fail_count <= 5',1,'2026-05-20 11:14:57','2026-05-20 11:14:57','RULE002',NULL,NULL,'课程考核成绩低于60分（不含60分）视为不及格；未参加考试（缺考）视为挂科；考试作弊取消成绩视为挂科','full_program','periodic','补考通过或重修合格后自动解除预警','1. 辅导员约谈；2. 制定个性化学习方案；3. 家长沟通；4. 每周两次学习辅导','通知对象：教师、学生、辅导员。流程：系统自动生成预警，发送通知给教师、学生和辅导员，辅导员在3个工作日内约谈学生并与家长沟通','辅导员','辅导员每3天跟踪一次，学院每周召开预警学生专题会议，每月向教务处上报进展','预警记录保存至学生毕业离校后5年，电子档案与纸质档案同步归档','挂科次数统计范围：学生入学以来所有学期的不及格课程（不含已通过补考/重修的课程）','1. 教务处：负责数据统计和规则维护；2. 学院：负责预警通知和学业干预；3. 辅导员：负责跟踪学生；4. 学生：配合干预措施','学生对预警结果有异议的，可在收到通知后5个工作日内向学院提交申诉材料，学院在10个工作日内给出复议结果','因不可抗力导致的挂科可申请特殊处理，需提供相关证明材料，经学院审核批准后可不计入挂科统计'),(3,'高级预警','学生在所有学期内累计挂科次数超过5科（不含5科）',3,'fail_count > 5',1,'2026-05-20 11:14:57','2026-05-20 11:14:57','RULE003',NULL,NULL,'课程考核成绩低于60分（不含60分）视为不及格；未参加考试（缺考）视为挂科；考试作弊取消成绩视为挂科','full_program','real_time','补考通过或重修合格后自动解除预警；累计挂科超过8科建议留级处理','1. 学院院长约谈；2. 制定严格的学业改进计划；3. 家长到校沟通；4. 每日学习打卡；5. 可能建议留级','通知对象：教师、学生、辅导员、管理员。流程：系统实时生成预警，立即通知教师、学生、辅导员和管理员，学院在3个工作日内组织专项会议','学院院长','辅导员每日跟踪，学院成立专项帮扶小组，每周向教务处上报进展','预警记录保存至学生毕业离校后5年，电子档案与纸质档案同步归档','挂科次数统计范围：学生入学以来所有学期的不及格课程（不含已通过补考/重修的课程）','1. 教务处：负责数据统计和规则维护；2. 学院：负责预警通知和学业干预；3. 辅导员：负责跟踪学生；4. 学生：配合干预措施','学生对预警结果有异议的，可在收到通知后5个工作日内向学院提交申诉材料，学院在10个工作日内给出复议结果','因不可抗力导致的挂科可申请特殊处理，需提供相关证明材料，经学院审核批准后可不计入挂科统计'),(4,'GPA过低预警','学期GPA低于2.0触发预警',2,'gpa < 2.0',1,'2026-05-27 10:00:00','2026-05-27 10:00:00','RULE_GPA_LOW',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `warning_rules` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `warning_tracking_records`
--

DROP TABLE IF EXISTS `warning_tracking_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `warning_tracking_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `warning_id` bigint NOT NULL COMMENT '关联预警记录ID',
  `tracking_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '跟踪时间',
  `tracking_content` text COLLATE utf8mb4_unicode_ci COMMENT '跟踪内容',
  `tracking_by` bigint DEFAULT NULL COMMENT '跟踪人ID',
  `next_follow_time` datetime DEFAULT NULL COMMENT '下次跟进时间',
  PRIMARY KEY (`id`),
  KEY `idx_warning_id` (`warning_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警跟踪记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `warning_tracking_records`
--

LOCK TABLES `warning_tracking_records` WRITE;
/*!40000 ALTER TABLE `warning_tracking_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `warning_tracking_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'academic_warning_system'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-13 10:23:20
