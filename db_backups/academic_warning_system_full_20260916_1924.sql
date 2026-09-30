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
) ENGINE=InnoDB AUTO_INCREMENT=395 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `academic_warning`
--

LOCK TABLES `academic_warning` WRITE;
/*!40000 ALTER TABLE `academic_warning` DISABLE KEYS */;
INSERT INTO `academic_warning` VALUES (301,3,'司明',NULL,1,'成绩下滑','第4学期大学英语不及格(51分)，线性代数不及格(59分)，较第3学期成绩明显下滑。',2,NULL,NULL,NULL,NULL,1,'none',NULL,NULL,NULL,NULL,'2025-01-16 00:30:00',NULL,'2026-06-04 09:38:10'),(302,3,'司明',NULL,1,'多门挂科','第4学期计算机组成原理成绩严重不及格(40分)，建议立即关注。已累计3门课程不及格。',3,NULL,NULL,NULL,NULL,0,'none',NULL,NULL,NULL,NULL,'2025-01-16 00:30:00',NULL,'2026-06-04 09:38:10'),(303,3,'司明',NULL,1,'持续预警','第5学期高等数学56.5分(不及格)，线性代数47分(不及格)。连续两个学期出现多门挂科，GPA呈持续下滑趋势，学风存在严重问题。建议辅导员约谈。',3,NULL,NULL,NULL,NULL,0,'none',NULL,NULL,NULL,NULL,'2025-06-21 00:30:00',NULL,'2026-06-04 09:38:10');
/*!40000 ALTER TABLE `academic_warning` ENABLE KEYS */;
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
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_assistance_evaluations_plan_id` (`plan_id`),
  KEY `idx_assistance_evaluations_counselor_id` (`counselor_id`),
  CONSTRAINT `fk_assistance_evaluations_plan_id` FOREIGN KEY (`plan_id`) REFERENCES `assistance_plans` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
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
INSERT INTO `assistance_plans` VALUES (1,53,13,'针对韩坤同学的学业帮扶计划：1.安排学习小组辅导数据结构；2.每周一次答疑；3.定期检查学习进度',1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,53,20,'针对李爽同学的学业帮扶计划：1.重点补习高等数学和线性代数基础；2.安排学伴互助；3.每两周向辅导员汇报进度；4.已安排补考',1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,53,26,'针对张健同学的学业帮扶计划：1.重点补习数学和编程基础；2.每周安排两次额外辅导；3.建立学习档案跟踪进度',0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(1001,53,3,'针对司明同学的学业帮扶计划：1.重点补习操作系统、计算机基础、大学英语、高等数学四门挂科课程；2.安排学习小组每周两次辅导答疑；3.每两周检查一次学习进度并反馈辅导员；4.目标：本学期末补考全部通过。',1,'2026-09-02 06:36:09','2026-09-02 06:36:09');
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_logs`
--

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
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
INSERT INTO `benchmark_analysis` VALUES (1,4,1,1,'2025-2026-2',3.800,3.050,3.100,3,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,6,1,1,'2025-2026-2',3.200,3.050,3.100,10,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,7,1,1,'2025-2026-2',3.350,3.050,3.100,8,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,8,1,1,'2025-2026-2',3.350,3.050,3.100,9,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(5,9,1,1,'2025-2026-2',2.500,3.050,3.100,18,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(6,10,1,1,'2025-2026-2',3.250,3.050,3.100,11,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(7,11,1,1,'2025-2026-2',2.450,3.050,3.100,19,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(8,12,1,1,'2025-2026-2',3.420,3.050,3.100,7,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(9,13,1,1,'2025-2026-2',2.000,3.050,3.100,22,24,NULL,NULL,4,2,20.00,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(10,14,1,1,'2025-2026-2',3.820,3.050,3.100,2,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(24,5,1,1,'2025-2026-2',3.700,3.050,3.100,5,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(25,15,1,1,'2025-2026-2',2.350,3.050,3.100,20,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(26,16,1,1,'2025-2026-2',3.250,3.050,3.100,12,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(27,17,1,1,'2025-2026-2',3.400,3.050,3.100,8,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(28,18,1,1,'2025-2026-2',2.500,3.050,3.100,17,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(29,19,1,1,'2025-2026-2',3.830,3.050,3.100,1,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(30,20,1,1,'2025-2026-2',1.850,3.050,3.100,23,24,NULL,NULL,3,3,20.00,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(31,21,1,1,'2025-2026-2',3.350,3.050,3.100,9,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(32,22,1,1,'2025-2026-2',2.650,3.050,3.100,15,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(33,23,1,1,'2025-2026-2',3.100,3.050,3.100,13,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(34,24,1,1,'2025-2026-2',2.500,3.050,3.100,16,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(35,25,1,1,'2025-2026-2',3.680,3.050,3.100,6,24,NULL,NULL,6,0,20.00,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(36,26,1,1,'2025-2026-2',1.700,3.050,3.100,24,24,NULL,NULL,3,3,20.00,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(37,3,1,2,'3',3.430,2.950,2.820,8,45,NULL,NULL,6,0,NULL,1,'2024-06-19 16:00:00','2026-06-04 08:25:03'),(38,3,1,2,'4',1.950,2.900,2.780,38,45,NULL,NULL,2,3,NULL,1,'2025-01-14 16:00:00','2026-06-04 08:25:03'),(39,3,1,2,'5',1.530,2.850,2.750,42,45,NULL,NULL,3,3,NULL,1,'2025-06-19 16:00:00','2026-06-04 08:25:03'),(40,3,1,2,'6',0.750,2.880,2.730,43,45,NULL,NULL,1,3,NULL,1,'2025-12-19 16:00:00','2026-06-04 08:25:03');
/*!40000 ALTER TABLE `benchmark_analysis` ENABLE KEYS */;
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
INSERT INTO `classes` VALUES (1,'计科2306班',1,1,29,53,'2025-12-22 17:28:15','2026-06-25 00:35:16'),(5,'软工2306班',1,2,NULL,53,'2026-03-04 02:43:20','2026-06-24 01:47:21'),(30,'计算机2101班',1,1,29,53,'2026-05-14 10:40:58','2026-06-25 00:35:16'),(31,'计算机2102班',1,1,18,53,'2026-05-14 10:40:58','2026-06-24 01:47:21'),(32,'软件工程2101班',1,2,18,53,'2026-05-14 10:40:58','2026-06-25 00:35:16'),(33,'软件工程2102班',1,2,19,53,'2026-05-14 10:40:58','2026-06-24 01:47:21'),(34,'人工智能2101班',1,3,19,53,'2026-05-14 10:40:58','2026-06-24 01:47:21'),(35,'人工智能2102班',1,3,22,53,'2026-05-14 10:40:58','2026-06-24 01:47:21');
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学院管理员档案';
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
  `student_count` int DEFAULT '0',
  `teacher_count` int DEFAULT '0',
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
INSERT INTO `colleges` VALUES (1,'软件学院','SOFT','软件学院',1,0,0,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(2,'电子工程学院','EE','电子工程学院',1,0,0,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(3,'商学院','BUS','商学院',1,0,0,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(4,'艺术设计学院','ART','艺术设计学院',1,0,0,'2025-12-22 17:28:15','2025-12-22 17:28:15');
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
) ENGINE=InnoDB AUTO_INCREMENT=2004 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `communication_logs`
--

LOCK TABLES `communication_logs` WRITE;
/*!40000 ALTER TABLE `communication_logs` DISABLE KEYS */;
INSERT INTO `communication_logs` VALUES (2001,53,NULL,3,301,'就本学期操作系统(40分)、计算机基础(47分)两门挂科课程约谈司明, 了解其学习困难集中在程序设计基础薄弱与上机练习不足, 已建议参加操作系统学习小组并预约每周两次辅导答疑。','预警约谈',1,'学生表示已加入学习小组, 承诺每周完成课后上机练习并在两周内提交复习计划。','2026-08-29 02:30:00','2026-08-29 02:30:00'),(2002,53,NULL,3,302,'跟踪帮扶计划(计划1001)执行情况: 司明已参加操作系统学习小组第2次辅导, 上机作业提交正常; 继续观察大学英语、高等数学复习进度。','帮扶跟进',0,NULL,'2026-09-01 07:00:00','2026-09-01 07:00:00'),(2003,53,NULL,3,NULL,'期中学习状态沟通: 反馈近期因课程冲突缺席两次小组辅导, 已协调补课时间, 提醒关注补考报名安排。','日常关怀',1,'已确认补课时间并完成补考报名, 学习状态趋于稳定。','2026-09-02 01:00:00','2026-09-02 01:00:00');
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
INSERT INTO `communication_record` VALUES (1,29,39,'关于数据结构课程成绩不理想，与学生沟通了学习方法，建议多做练习','成绩沟通','韩坤','2026-05-27 10:00:00'),(2,29,46,'学生三门课程不及格，与辅导员张明联合约谈，制定补习计划','预警沟通','李爽','2026-05-27 10:00:00'),(3,29,52,'学生数学基础薄弱导致多科挂科，建议参加课外辅导班','成绩沟通','张健','2026-05-27 10:00:00'),(4,29,13,'数据结构补考准备情况跟进，目前掌握较好','辅导记录','韩坤','2026-05-27 10:00:00'),(5,29,0,'范德萨','notify_counselor','邱琳紫','2026-06-23 16:52:41');
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
  PRIMARY KEY (`id`),
  KEY `idx_counselor_id` (`counselor_id`),
  KEY `idx_class_id` (`class_id`),
  KEY `idx_status` (`status`),
  KEY `idx_application_type` (`application_type`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员班级管理申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `counselor_class_application`
--

LOCK TABLES `counselor_class_application` WRITE;
/*!40000 ALTER TABLE `counselor_class_application` DISABLE KEYS */;
INSERT INTO `counselor_class_application` VALUES (1,1,1,'希望负责计算机2101班的日常管理工作',2,'2026-05-18 12:25:12','2026-05-18 12:25:12','计算机2101班','张明',0),(2,1,2,'希望同时管理计算机2102班',2,'2026-05-18 12:25:12','2026-05-18 12:25:12','计算机2102班','张明',0),(5,53,1,'范德萨',1,'2026-06-08 21:47:38','2026-06-08 21:47:38','计科2306班','辅导员',0);
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
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='辅导员班级管理表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `counselor_class_management`
--

LOCK TABLES `counselor_class_management` WRITE;
/*!40000 ALTER TABLE `counselor_class_management` DISABLE KEYS */;
INSERT INTO `counselor_class_management` VALUES (1,53,1,'计科2306班',1,'2025-12-23 01:28:15','2025-12-23 01:28:15');
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
INSERT INTO `counselor_profiles` VALUES (1,53,1,'C53','司明',NULL,NULL,NULL,'学生工作办公室',NULL,NULL,NULL,1,'2026-05-27 13:34:10','2026-08-03 15:32:05','计算机学院','1000000001',NULL,NULL,0,0);
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
INSERT INTO `course_plans` VALUES (1,1,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周一 1-2节','教学楼A301',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,2,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周一 3-4节','教学楼A302',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,3,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周二 1-2节','教学楼A303',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,4,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周二 3-4节','实训楼B101',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(5,5,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周三 1-2节','实训楼B102',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(6,6,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周三 3-4节','教学楼A304',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(7,7,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周四 5-6节','教学楼A401',80,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(8,8,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周四 7-8节','教学楼A402',80,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(9,9,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周五 5-6节','艺术楼C201',50,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(10,10,29,'2025-2026-2',2026,'2026-02-24','2026-06-30','周五 7-8节','教学楼A403',60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `course_plans` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `courses`
--

LOCK TABLES `courses` WRITE;
/*!40000 ALTER TABLE `courses` DISABLE KEYS */;
INSERT INTO `courses` VALUES (1,'MATH001','高等数学',4.00,'必修','高等数学基础课程',NULL,NULL,29,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,'CS102','大学英语',3.00,'必修课',NULL,1,NULL,19,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(3,'CS103','计算机基础',3.00,'必修课',NULL,1,NULL,22,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(4,'CS201','数据结构',4.00,'必修课',NULL,2,NULL,29,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(5,'CS202','计算机网络',3.00,'必修课',NULL,2,NULL,22,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(6,'CS203','操作系统',4.00,'必修课',NULL,2,NULL,23,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(7,'CS301','人工智能导论',3.00,'选修课',NULL,3,NULL,23,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(8,'CS302','机器学习',4.00,'选修课',NULL,3,NULL,23,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(9,'CS303','深度学习',3.00,'选修课',NULL,3,NULL,28,'2026-05-24 08:13:53','2026-06-25 00:45:43'),(10,'CULT001','中国传统文化',1.00,'选修','中国传统文化概论',4,NULL,29,'2026-05-27 02:00:00','2026-05-27 02:00:00');
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
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `export_history`
--

LOCK TABLES `export_history` WRITE;
/*!40000 ALTER TABLE `export_history` DISABLE KEYS */;
INSERT INTO `export_history` VALUES (3,'colleges','colleges_20260521_192926.xlsx',4,NULL,'2026-05-21 11:29:26'),(4,'colleges','colleges_20260521_192926.xlsx',4,NULL,'2026-05-21 11:29:26'),(5,'colleges','colleges_20260521_192926.xlsx',4,NULL,'2026-05-21 11:29:26'),(6,'colleges','colleges_20260521_192933.xlsx',4,NULL,'2026-05-21 11:29:34'),(7,'colleges','colleges_20260521_193500.xlsx',4,NULL,'2026-05-21 11:35:01'),(8,'students','students_20260525_130905.xlsx',11,NULL,'2026-05-25 05:09:09'),(9,'colleges','colleges_20260525_131816.xlsx',4,NULL,'2026-05-25 05:18:17'),(10,'colleges','colleges_20260525_132013.xlsx',4,NULL,'2026-05-25 05:20:13'),(11,'colleges','colleges_20260525_132222.xlsx',4,NULL,'2026-05-25 05:22:23'),(12,'colleges','colleges_20260525_133342.xlsx',4,NULL,'2026-05-25 05:33:42'),(13,'colleges','colleges_20260525_134035.xlsx',4,NULL,'2026-05-25 05:40:36'),(14,'students','students_20260527_093355.xlsx',11,NULL,'2026-05-27 01:33:57'),(15,'students','students_20260527_094225.xlsx',11,NULL,'2026-05-27 01:42:26'),(16,'scores','scores_20260527_094256.xlsx',0,NULL,'2026-05-27 01:42:57'),(17,'warnings','warnings_20260527_094258.xlsx',0,NULL,'2026-05-27 01:43:01'),(18,'students','students_20260527_094352.xlsx',11,NULL,'2026-05-27 01:43:52'),(19,'students','students_20260527_094804.xlsx',11,NULL,'2026-05-27 01:48:04'),(20,'students','students_20260527_095252.xlsx',11,NULL,'2026-05-27 01:52:52'),(21,'students','students_20260527_095259.xlsx',11,NULL,'2026-05-27 01:52:59'),(22,'colleges','colleges_20260527_103947.xlsx',4,NULL,'2026-05-27 02:39:48'),(23,'students','students_20260527_104004.xlsx',11,NULL,'2026-05-27 02:40:04'),(24,'warnings','warnings_20260527_104009.xlsx',0,NULL,'2026-05-27 02:40:17'),(25,'warnings','warnings_20260527_104013.xlsx',0,NULL,'2026-05-27 02:40:17'),(26,'users','users_20260527_104417.xlsx',24,NULL,'2026-05-27 02:44:17');
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
INSERT INTO `fail_records` VALUES (1,39,4,'数据结构','2026-06-20 09:00:00',65.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(2,39,6,'计算机组成原理','2026-06-22 09:00:00',71.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(3,46,1,'高等数学','2026-06-18 09:00:00',66.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(4,46,3,'线性代数','2026-06-20 09:00:00',69.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(5,46,5,'Java程序设计','2026-06-23 09:00:00',68.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(6,52,1,'高等数学','2026-06-18 09:00:00',64.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(7,52,3,'线性代数','2026-06-20 09:00:00',62.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00'),(8,52,5,'Java程序设计','2026-06-23 09:00:00',65.00,'fail','2025-2026-2','2025-2026',0,'2026-05-27 10:00:00','2026-05-27 10:00:00');
/*!40000 ALTER TABLE `fail_records` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反馈表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feedback`
--

LOCK TABLES `feedback` WRITE;
/*!40000 ALTER TABLE `feedback` DISABLE KEYS */;
INSERT INTO `feedback` VALUES (1,29,'教学建议','司明：高等数学课程进度有点快，希望老师能放慢一些',NULL,0,'2026-06-25 09:53:16','2026-06-25 09:53:16'),(2,29,'学习困难','张浩：数据结构的课后作业太难了，能否提供一些参考示例',NULL,0,'2026-06-25 09:53:16','2026-06-25 09:53:16');
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
) ENGINE=InnoDB AUTO_INCREMENT=42 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `login_logs`
--

LOCK TABLES `login_logs` WRITE;
/*!40000 ALTER TABLE `login_logs` DISABLE KEYS */;
INSERT INTO `login_logs` VALUES (1,16,'adm001','2026-06-24 00:51:11','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(2,29,'10001','2026-06-24 00:58:05','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(3,29,'10001','2026-06-24 01:17:15','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(4,29,'10001','2026-06-24 01:25:06','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(5,29,'10001','2026-06-24 01:50:46','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(6,29,'10001','2026-06-24 05:37:19','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(7,29,'10001','2026-06-25 00:31:23','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(8,53,'1000000001','2026-06-25 03:01:55','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(9,16,'adm001','2026-06-25 03:33:03','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(10,16,'adm001','2026-06-25 06:34:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(11,16,'adm001','2026-06-25 06:36:15','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(12,53,'1000000001','2026-06-25 06:36:39','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(13,29,'10001','2026-06-25 06:37:02','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(14,29,'10001','2026-06-26 00:27:33','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(15,53,'1000000001','2026-06-26 00:44:13','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(16,16,'adm001','2026-06-26 00:48:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(17,27,'2023020616','2026-06-26 00:55:22','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(18,16,'adm001','2026-06-26 01:03:23','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(19,27,'2023020616','2026-06-26 01:23:57','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(20,53,'1000000001','2026-06-26 01:24:16','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(21,57,'collegeadmin1','2026-06-26 01:54:08','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(22,57,'collegeadmin1','2026-06-26 02:03:49','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(23,57,'collegeadmin1','2026-06-26 02:51:48','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(24,57,'collegeadmin1','2026-06-27 00:33:56','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(25,57,'collegeadmin1','2026-06-27 00:54:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(26,27,'2023020616','2026-06-27 01:23:47','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(27,57,'collegeadmin1','2026-06-27 01:24:08','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(28,16,'adm001','2026-06-27 01:25:05','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(29,57,'collegeadmin1','2026-06-27 01:25:17','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(30,16,'adm001','2026-06-27 01:34:49','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(31,57,'collegeadmin1','2026-06-27 01:39:22','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(32,29,'10001','2026-06-27 02:08:48','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(33,29,'10001','2026-06-29 02:05:30','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(34,57,'collegeadmin1','2026-06-29 07:06:57','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(35,27,'2023020616','2026-06-29 07:29:14','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(36,29,'10001','2026-06-29 07:29:50','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(37,53,'1000000001','2026-06-29 07:30:31','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(38,57,'collegeadmin1','2026-06-29 07:31:18','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(39,16,'adm001','2026-06-29 07:32:06','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(40,53,'1000000001','2026-06-29 07:33:29','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success'),(41,57,'collegeadmin1','2026-06-29 07:34:41','0:0:0:0:0:0:0:1','Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36 Edg/149.0.0.0','校园网','success');
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
INSERT INTO `majors` VALUES (1,1,'软件工程','01','软工','软件工程专业',1,'2025-12-22 17:28:15','2026-06-24 00:53:26'),(2,1,'计算机科学与技术','02','计科','计算机科学与技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:53:26'),(3,1,'电子信息工程','03','大数据','数据科学与大数据技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:53:26'),(4,1,'工商管理','04','智科','智能科学与技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(5,1,'视觉传达设计','05','VR','虚拟现实技术专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(6,2,'电子信息工程','06','电信','电子信息工程专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(7,2,'自动化','07','自动化','自动化专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(8,2,'人工智能','08','AI','人工智能专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(9,3,'电子商务','09','电商','电子商务专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(10,3,'物流管理','10','物流','物流管理专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(11,3,'财务管理','11','财管','财务管理专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(12,4,'网络与新媒体','12','网媒','网络与新媒体专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(13,4,'环境设计','13','环设','环境设计专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07'),(14,4,'视觉传达设计','14','视传','视觉传达设计专业',1,'2025-12-22 17:28:15','2026-06-24 00:56:07');
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `messages`
--

LOCK TABLES `messages` WRITE;
/*!40000 ALTER TABLE `messages` DISABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=1003 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `notifications`
--

LOCK TABLES `notifications` WRITE;
/*!40000 ALTER TABLE `notifications` DISABLE KEYS */;
INSERT INTO `notifications` VALUES (1,39,'学业预警通知','韩坤同学，您本学期有2门课程成绩不达标（数据结构65分、计算机组成原理71分），已被列入学业预警名单。请尽快与辅导员联系。','warning',1,'app',0,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,46,'高度学业预警通知','李爽同学，您本学期有3门课程不及格（高等数学66分、线性代数69分、Java程序设计68分），已触发高度预警。请务必重视并尽快联系辅导员张明老师。','warning',2,'app',1,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,52,'高度学业预警通知','张健同学，您本学期有3门课程不及格（高等数学64分、线性代数62分、Java程序设计65分），已触发高度预警。建议及时寻求学业帮扶。','warning',3,'app',0,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,53,'班级预警汇总','您负责的计科2306班中，有3名学生触发了学业预警：韩坤(中度)、李爽(高度)、张健(高度)。请及时跟进帮扶。','system',NULL,'app',0,0,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(1001,27,'学业预警通知','司明同学，您本学期有5门课程成绩不及格（操作系统40分、计算机基础47分、大学英语51分、高等数学56.5分、计算机基础59分），已触发学业预警。请尽快联系辅导员沟通帮扶安排。','warning',301,'app',0,0,'2026-09-02 06:36:09','2026-09-02 06:36:09'),(1002,27,'帮扶计划已制定','辅导员已为您制定学业帮扶计划（进行中）：每周两次学习小组辅导答疑。请积极跟进学习进度，如有困难可随时在系统中反馈。','system',NULL,'app',0,0,'2026-09-02 06:36:09','2026-09-02 06:36:09');
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
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
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
) ENGINE=InnoDB AUTO_INCREMENT=23 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `role_permissions`
--

LOCK TABLES `role_permissions` WRITE;
/*!40000 ALTER TABLE `role_permissions` DISABLE KEYS */;
INSERT INTO `role_permissions` VALUES (1,1,1,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(2,1,3,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(3,1,5,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(4,1,7,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(5,2,1,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(6,2,3,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(7,2,5,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(8,3,1,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(9,3,3,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(10,3,5,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(11,3,7,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(12,3,8,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(13,4,1,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(14,4,2,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(15,4,3,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(16,4,4,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(17,4,5,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(18,4,6,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(19,4,7,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(20,4,8,'2025-12-22 17:28:15','2026-05-12 12:46:40'),(21,4,9,'2025-12-22 17:28:15','2026-05-12 12:46:40');
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (1,'学生','ROLE_STUDENT','学生角色',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(2,'教师','ROLE_TEACHER','教师角色',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(3,'辅导员','ROLE_COUNSELOR','辅导员角色',1,'2025-12-22 17:28:15','2025-12-22 17:28:15'),(4,'管理员','ROLE_ADMIN','系统管理员',1,'2025-12-22 17:28:15','2025-12-22 17:28:15');
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
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_appeals`
--

LOCK TABLES `score_appeals` WRITE;
/*!40000 ALTER TABLE `score_appeals` DISABLE KEYS */;
INSERT INTO `score_appeals` VALUES (6,NULL,3,NULL,1,'entry_error',NULL,0,NULL,'2026-07-08 06:01:34','2026-07-08 06:01:34');
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_audit_log`
--

LOCK TABLES `score_audit_log` WRITE;
/*!40000 ALTER TABLE `score_audit_log` DISABLE KEYS */;
INSERT INTO `score_audit_log` (`id`, `student_id`, `student_name`, `course_id`, `course_name`, `teacher_id`, `old_score`, `new_score`, `reason`, `modified_by`, `modified_at`) VALUES (1,3,'司明',1,'高等数学',29,55.00,65.00,'试卷复核发现加错分','于凤海','2026-06-25 01:38:17');
/*!40000 ALTER TABLE `score_audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_record`
--

DROP TABLE IF EXISTS `score_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_student_id` (`student_id`),
  KEY `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB AUTO_INCREMENT=388 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成绩记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_record`
--

LOCK TABLES `score_record` WRITE;
/*!40000 ALTER TABLE `score_record` DISABLE KEYS */;
INSERT INTO `score_record` VALUES (2,2,4,29,'邱琳紫','计科2306班',92.00,1,'2',NULL,'口语表达流畅','2026-05-27 10:00:00','2026-06-27 09:41:16',86.00,84.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(3,3,4,29,'邱琳紫','计科2306班',88.00,2,'2',NULL,'行列式计算准确','2026-05-27 10:00:00','2026-06-27 09:41:16',82.00,91.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(4,4,4,29,'邱琳紫','计科2306班',90.00,1,'2',NULL,'算法掌握扎实','2026-05-27 10:00:00','2026-06-27 09:41:16',82.00,93.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(5,5,4,29,'邱琳紫','计科2306班',93.00,1,'2',NULL,'编程能力强，代码规范','2026-05-27 10:00:00','2026-06-27 09:41:16',97.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(6,6,4,29,'邱琳紫','计科2306班',91.00,1,'2',NULL,'理论理解深入','2026-05-27 10:00:00','2026-06-27 09:41:16',90.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(7,1,5,29,'宋新鹏','计科2306班',91.00,1,'2',NULL,'学习积极主动','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,85.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(8,2,5,29,'宋新鹏','计科2306班',89.00,2,'2',NULL,'阅读能力较强','2026-05-27 10:00:00','2026-06-27 09:41:16',82.00,94.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(9,3,5,29,'宋新鹏','计科2306班',85.00,2,'2',NULL,'向量空间理解好','2026-05-27 10:00:00','2026-06-27 09:41:16',79.00,86.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(10,4,5,29,'宋新鹏','计科2306班',92.00,1,'2',NULL,'项目完成出色','2026-05-27 10:00:00','2026-06-27 09:41:16',91.00,95.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(11,5,5,29,'宋新鹏','计科2306班',88.00,2,'2',NULL,'面向对象设计合理','2026-05-27 10:00:00','2026-06-27 09:41:16',88.00,96.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(12,6,5,29,'宋新鹏','计科2306班',90.00,1,'2',NULL,'实验报告完整','2026-05-27 10:00:00','2026-06-27 09:41:16',93.00,96.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(13,1,6,29,'赵琦','计科2306班',85.00,2,'2',NULL,'基础扎实，解题规范','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,89.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(14,2,6,29,'赵琦','计科2306班',82.00,2,'2',NULL,'听力需加强','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(15,3,6,29,'赵琦','计科2306班',78.00,3,'2',NULL,'部分概念模糊','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(16,4,6,29,'赵琦','计科2306班',84.00,2,'2',NULL,'递归理解较好','2026-05-27 10:00:00','2026-06-27 09:41:16',92.00,84.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(17,5,6,29,'赵琦','计科2306班',86.00,2,'2',NULL,'面向对象掌握良好','2026-05-27 10:00:00','2026-06-27 09:41:16',80.00,94.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(18,6,6,29,'赵琦','计科2306班',80.00,2,'2',NULL,'需加强实践','2026-05-27 10:00:00','2026-06-27 09:41:16',81.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(19,1,7,29,'苏红阳','计科2306班',82.00,2,'2',NULL,'课堂参与积极','2026-05-27 10:00:00','2026-06-27 09:41:16',88.00,83.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(20,2,7,29,'苏红阳','计科2306班',88.00,2,'2',NULL,'写作能力突出','2026-05-27 10:00:00','2026-06-27 09:41:16',80.00,86.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(21,3,7,29,'苏红阳','计科2306班',80.00,2,'2',NULL,'推导过程完整','2026-05-27 10:00:00','2026-06-27 09:41:16',76.00,88.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(22,4,7,29,'苏红阳','计科2306班',83.00,2,'2',NULL,'树结构掌握较好','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,76.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(23,5,7,29,'苏红阳','计科2306班',85.00,2,'2',NULL,'多线程理解深入','2026-05-27 10:00:00','2026-06-27 09:41:16',87.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(24,6,7,29,'苏红阳','计科2306班',87.00,2,'2',NULL,'硬件原理理解好','2026-05-27 10:00:00','2026-06-27 09:41:16',94.00,86.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(25,1,8,29,'唐浩然','计科2306班',88.00,2,'2',NULL,'解题思路清晰','2026-05-27 10:00:00','2026-06-27 09:41:16',86.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(26,2,8,29,'唐浩然','计科2306班',84.00,2,'2',NULL,'词汇量丰富','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,90.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(27,3,8,29,'唐浩然','计科2306班',86.00,2,'2',NULL,'矩阵运算熟练','2026-05-27 10:00:00','2026-06-27 09:41:16',81.00,91.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(28,4,8,29,'唐浩然','计科2306班',81.00,2,'2',NULL,'图算法待加强','2026-05-27 10:00:00','2026-06-27 09:41:16',81.00,79.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(29,5,8,29,'唐浩然','计科2306班',82.00,2,'2',NULL,'集合框架熟练','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,90.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(30,6,8,29,'唐浩然','计科2306班',85.00,2,'2',NULL,'CPU原理理解好','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(31,1,9,29,'王宏民','计科2306班',75.00,3,'2',NULL,'计算能力一般','2026-05-27 10:00:00','2026-06-27 09:41:16',72.00,72.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(32,2,9,29,'王宏民','计科2306班',78.00,3,'2',NULL,'语法基础尚可','2026-05-27 10:00:00','2026-06-27 09:41:16',79.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(33,3,9,29,'王宏民','计科2306班',72.00,3,'2',NULL,'向量概念需巩固','2026-05-27 10:00:00','2026-06-27 09:41:16',69.00,68.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(34,4,9,29,'王宏民','计科2306班',76.00,3,'2',NULL,'排序算法掌握','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,75.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(35,5,9,29,'王宏民','计科2306班',74.00,3,'2',NULL,'异常处理需加强','2026-05-27 10:00:00','2026-06-27 09:41:16',72.00,81.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(36,6,9,29,'王宏民','计科2306班',77.00,3,'2',NULL,'存储器理解一般','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(37,1,10,29,'王玉蛟','计科2306班',83.00,2,'2',NULL,'学习态度认真','2026-05-27 10:00:00','2026-06-27 09:41:16',85.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(38,2,10,29,'王玉蛟','计科2306班',85.00,2,'2',NULL,'翻译能力较强','2026-05-27 10:00:00','2026-06-27 09:41:16',87.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(39,3,10,29,'王玉蛟','计科2306班',79.00,3,'2',NULL,'特征值计算需加强','2026-05-27 10:00:00','2026-06-27 09:41:16',74.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(40,4,10,29,'王玉蛟','计科2306班',87.00,2,'2',NULL,'图论部分表现好','2026-05-27 10:00:00','2026-06-27 09:41:16',92.00,94.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(41,5,10,29,'王玉蛟','计科2306班',84.00,2,'2',NULL,'GUI编程有创意','2026-05-27 10:00:00','2026-06-27 09:41:16',83.00,84.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(42,6,10,29,'王玉蛟','计科2306班',82.00,2,'2',NULL,'IO设备理解好','2026-05-27 10:00:00','2026-06-27 09:41:16',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(43,1,11,29,'吴雨航','计科2306班',72.00,3,'2',NULL,'积分计算有困难','2026-05-27 10:00:00','2026-06-27 09:41:16',74.00,77.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(44,2,11,29,'吴雨航','计科2306班',76.00,3,'2',NULL,'词汇量不足','2026-05-27 10:00:00','2026-06-27 09:41:16',82.00,75.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(45,3,11,29,'吴雨航','计科2306班',74.00,3,'2',NULL,'需要多练习','2026-05-27 10:00:00','2026-06-27 09:41:16',68.00,70.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(46,4,11,29,'吴雨航','计科2306班',71.00,3,'2',NULL,'链表操作不熟练','2026-05-27 10:00:00','2026-06-27 09:41:16',67.00,70.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(47,5,11,29,'吴雨航','计科2306班',79.00,3,'2',NULL,'基础语法过关','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,82.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(48,6,11,29,'吴雨航','计科2306班',73.00,3,'2',NULL,'指令系统需复习','2026-05-27 10:00:00','2026-06-27 09:41:16',69.00,74.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(49,1,12,29,'徐瑞瑶','计科2306班',87.00,2,'2',NULL,'数学思维好','2026-05-27 10:00:00','2026-06-27 09:41:16',87.00,93.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(50,2,12,29,'徐瑞瑶','计科2306班',83.00,2,'2',NULL,'听力理解优秀','2026-05-27 10:00:00','2026-06-27 09:41:16',89.00,90.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(51,3,12,29,'徐瑞瑶','计科2306班',89.00,2,'2',NULL,'线性变换理解深入','2026-05-27 10:00:00','2026-06-27 09:41:16',96.00,97.00,97.00,'A',4.00,NULL,NULL,NULL,NULL),(52,4,12,29,'徐瑞瑶','计科2306班',85.00,2,'2',NULL,'哈希表掌握好','2026-05-27 10:00:00','2026-06-27 09:41:16',89.00,81.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(53,5,12,29,'徐瑞瑶','计科2306班',86.00,2,'2',NULL,'设计模式理解好','2026-05-27 10:00:00','2026-06-27 09:41:16',92.00,82.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(54,6,12,29,'徐瑞瑶','计科2306班',84.00,2,'2',NULL,'总线设计理解好','2026-05-27 10:00:00','2026-06-27 09:41:16',78.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(55,1,13,29,'韩坤','计科2306班',68.00,3,'2',NULL,'基础薄弱需加强','2026-05-27 10:00:00','2026-06-27 09:41:16',65.00,69.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(56,2,13,29,'韩坤','计科2306班',72.00,3,'2',NULL,'语法错误较多','2026-05-27 10:00:00','2026-06-27 09:41:16',71.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(57,3,13,29,'韩坤','计科2306班',70.00,3,'2',NULL,'矩阵乘法常出错','2026-05-27 10:00:00','2026-06-27 09:41:16',63.00,66.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(58,4,13,29,'韩坤','计科2306班',65.00,4,'2',NULL,'栈和队列混淆','2026-05-27 10:00:00','2026-06-27 09:41:16',58.00,57.00,57.00,'F',0.70,NULL,NULL,NULL,NULL),(59,5,13,29,'韩坤','计科2306班',75.00,3,'2',NULL,'循环逻辑需巩固','2026-05-27 10:00:00','2026-06-27 09:41:16',79.00,69.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(60,6,13,29,'韩坤','计科2306班',71.00,3,'2',NULL,'基本概念模糊','2026-05-27 10:00:00','2026-06-27 09:41:16',79.00,67.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(61,1,14,29,'张博巍','计科2306班',93.00,1,'2',NULL,'微积分掌握扎实','2026-05-27 10:00:00','2026-06-27 09:41:16',99.00,93.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(62,2,14,29,'张博巍','计科2306班',90.00,1,'2',NULL,'口语发音标准','2026-05-27 10:00:00','2026-06-27 09:41:16',92.00,96.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(63,3,14,29,'张博巍','计科2306班',88.00,2,'2',NULL,'二次型理解好','2026-05-27 10:00:00','2026-06-27 09:41:16',84.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(64,4,14,29,'张博巍','计科2306班',91.00,1,'2',NULL,'动态规划掌握好','2026-05-27 10:00:00','2026-06-27 09:41:16',96.00,94.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(65,5,14,19,'张博巍','计科2306班',94.00,1,'2',NULL,'项目开发能力强','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(66,6,14,19,'张博巍','计科2306班',89.00,2,'2',NULL,'体系结构掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,95.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(67,1,15,19,'程启頔','计科2306班',70.00,3,'2',NULL,'上课注意力不集中','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,65.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(68,2,15,19,'程启頔','计科2306班',74.00,3,'2',NULL,'听说能力待提高','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,68.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(69,3,15,19,'程启頔','计科2306班',68.00,3,'2',NULL,'证明题困难','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,63.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(70,4,15,19,'程启頔','计科2306班',72.00,3,'2',NULL,'查找算法需练习','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,72.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(71,5,15,19,'程启頔','计科2306班',76.00,3,'2',NULL,'集合使用基本正确','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,80.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(72,6,15,19,'程启頔','计科2306班',70.00,3,'2',NULL,'汇编基础需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',62.00,64.00,63.00,'D',1.30,NULL,NULL,NULL,NULL),(73,1,16,19,'邓雨轩','计科2306班',84.00,2,'2',NULL,'数列极限理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,85.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(74,2,16,19,'邓雨轩','计科2306班',86.00,2,'2',NULL,'作文表达流畅','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(75,3,16,19,'邓雨轩','计科2306班',80.00,2,'2',NULL,'方程组消元准确','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(76,4,16,19,'邓雨轩','计科2306班',82.00,2,'2',NULL,'二叉树遍历掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,75.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(77,5,16,19,'邓雨轩','计科2306班',85.00,2,'2',NULL,'接口设计规范','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,78.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(78,6,16,19,'邓雨轩','计科2306班',83.00,2,'2',NULL,'中断处理理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(79,1,17,19,'高菲','计科2306班',86.00,2,'2',NULL,'导数应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(80,2,17,19,'高菲','计科2306班',88.00,2,'2',NULL,'阅读理解准确','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,94.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(81,3,17,19,'高菲','计科2306班',82.00,2,'2',NULL,'矩阵对角化掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(82,4,17,19,'高菲','计科2306班',84.00,2,'2',NULL,'B树B+树理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,85.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(83,5,17,19,'高菲','计科2306班',87.00,2,'2',NULL,'IO流操作熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,82.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(84,6,17,19,'高菲','计科2306班',81.00,2,'2',NULL,'流水线技术理解','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(85,1,18,19,'胡钰','计科2306班',78.00,3,'2',NULL,'不定积分有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,82.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(86,2,18,19,'胡钰','计科2306班',80.00,2,'2',NULL,'语法有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,87.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(87,3,18,19,'胡钰','计科2306班',72.00,3,'2',NULL,'几何意义理解不足','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,78.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(88,4,18,19,'胡钰','计科2306班',75.00,3,'2',NULL,'代码实现有困难','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,82.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(89,5,18,19,'胡钰','计科2306班',79.00,3,'2',NULL,'网络编程需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,72.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(90,6,18,19,'胡钰','计科2306班',74.00,3,'2',NULL,'缓存概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,69.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(91,1,19,19,'李佳芮','计科2306班',92.00,1,'2',NULL,'空间解析几何好','2026-05-27 10:00:00','2026-06-27 09:58:51',100.00,91.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(92,2,19,19,'李佳芮','计科2306班',89.00,2,'2',NULL,'演讲能力出色','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,88.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(93,3,19,19,'李佳芮','计科2306班',93.00,1,'2',NULL,'秩与线性相关理解深','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(94,4,19,19,'李佳芮','计科2306班',90.00,1,'2',NULL,'最短路径算法优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,97.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(95,5,19,19,'李佳芮','计科2306班',91.00,1,'2',NULL,'反射机制掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,95.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(96,6,19,19,'李佳芮','计科2306班',90.00,1,'2',NULL,'存储层次理解深','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,96.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(97,1,20,19,'李爽','计科2306班',66.00,4,'2',NULL,'基础不扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,74.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(98,2,20,19,'李爽','计科2306班',71.00,3,'2',NULL,'词汇积累不够','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,69.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(99,3,20,19,'李爽','计科2306班',69.00,3,'2',NULL,'秩判断困难','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,64.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(100,4,20,19,'李爽','计科2306班',70.00,3,'2',NULL,'遍历算法需复习','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,77.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(101,5,20,19,'李爽','计科2306班',68.00,3,'2',NULL,'继承多态混淆','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(102,6,20,19,'李爽','计科2306班',72.00,3,'2',NULL,'数值表示理解一般','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,67.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(103,1,21,19,'梁莹静','计科2306班',85.00,2,'2',NULL,'多元函数掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,87.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(104,2,21,19,'梁莹静','计科2306班',87.00,2,'2',NULL,'跨文化交际能力好','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,91.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(105,3,21,19,'梁莹静','计科2306班',81.00,2,'2',NULL,'线性空间概念清晰','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,81.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(106,4,21,19,'梁莹静','计科2306班',83.00,2,'2',NULL,'堆排序理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,77.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(107,5,21,19,'梁莹静','计科2306班',86.00,2,'2',NULL,'泛型使用规范','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,78.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(108,6,21,19,'梁莹静','计科2306班',84.00,2,'2',NULL,'指令流水掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,77.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(109,1,22,19,'孟阳','计科2306班',73.00,3,'2',NULL,'函数连续性需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,70.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(110,2,22,19,'孟阳','计科2306班',75.00,3,'2',NULL,'长难句理解困难','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(111,3,22,19,'孟阳','计科2306班',71.00,3,'2',NULL,'基变换理解不足','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(112,4,22,19,'孟阳','计科2306班',77.00,3,'2',NULL,'快速排序基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,78.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(113,5,22,19,'孟阳','计科2306班',74.00,3,'2',NULL,'多线程有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,76.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(114,6,22,19,'孟阳','计科2306班',76.00,3,'2',NULL,'微程序控制器理解','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(115,1,23,19,'王瑾泰','计科2306班',82.00,2,'2',NULL,'定积分应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,87.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(116,2,23,19,'王瑾泰','计科2306班',84.00,2,'2',NULL,'完形填空准确','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(117,3,23,19,'王瑾泰','计科2306班',78.00,3,'2',NULL,'正交变换需巩固','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,71.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(118,4,23,19,'王瑾泰','计科2306班',85.00,2,'2',NULL,'AVL树掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,91.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(119,5,23,19,'王瑾泰','计科2306班',83.00,2,'2',NULL,'JDBC操作熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,82.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(120,6,23,19,'王瑾泰','计科2306班',80.00,2,'2',NULL,'虚拟内存理解','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,85.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(121,1,24,19,'王平洋','计科2306班',74.00,3,'2',NULL,'级数概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,80.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(122,2,24,19,'王平洋','计科2306班',73.00,3,'2',NULL,'翻译题薄弱','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,72.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(123,3,24,19,'王平洋','计科2306班',76.00,3,'2',NULL,'合同关系理解一般','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,75.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(124,4,24,19,'王平洋','计科2306班',79.00,3,'2',NULL,'并查集基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,82.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(125,5,24,19,'王平洋','计科2306班',71.00,3,'2',NULL,'Lambda表达式生疏','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,65.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(126,6,24,19,'王平洋','计科2306班',75.00,3,'2',NULL,'DMA方式需复习','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,74.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(127,1,25,19,'夏志浩','计科2306班',94.00,1,'2',NULL,'数学基础雄厚','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,87.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(128,2,25,19,'夏志浩','计科2306班',91.00,1,'2',NULL,'学术写作优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,98.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(129,3,25,22,'夏志浩','计科2306班',87.00,2,'2',NULL,'抽象思维能力强','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(130,4,25,22,'夏志浩','计科2306班',95.00,1,'2',NULL,'算法竞赛获奖','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,101.00,98.00,'A',4.00,NULL,NULL,NULL,NULL),(131,5,25,22,'夏志浩','计科2306班',92.00,1,'2',NULL,'精通JVM调优','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,99.00,97.00,'A',4.00,NULL,NULL,NULL,NULL),(132,6,25,22,'夏志浩','计科2306班',88.00,2,'2',NULL,'多核处理器理解深','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,83.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(133,1,26,22,'张健','计科2306班',64.00,4,'2',NULL,'多处知识漏洞','2026-05-27 10:00:00','2026-06-27 09:58:51',64.00,64.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(134,2,26,22,'张健','计科2306班',69.00,3,'2',NULL,'听力严重薄弱','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,64.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(135,3,26,22,'张健','计科2306班',62.00,4,'2',NULL,'高斯消元不熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',58.00,60.00,59.00,'F',0.90,NULL,NULL,NULL,NULL),(136,4,26,22,'张健','计科2306班',68.00,3,'2',NULL,'基本操作不熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,62.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(137,5,26,22,'张健','计科2306班',65.00,4,'2',NULL,'面向对象理解不足','2026-05-27 10:00:00','2026-06-27 09:58:51',61.00,64.00,63.00,'D',1.30,NULL,NULL,NULL,NULL),(138,6,26,22,'张健','计科2306班',71.00,3,'2',NULL,'运算器概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,71.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(139,1,4,22,'邱琳紫','计科2306班',95.00,1,'2',NULL,'思维敏捷，解题能力强','2026-05-27 10:00:00','2026-06-27 09:58:51',96.00,91.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(140,2,4,22,'邱琳紫','计科2306班',92.00,1,'2',NULL,'口语表达流畅','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,93.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(141,3,4,22,'邱琳紫','计科2306班',88.00,2,'2',NULL,'行列式计算准确','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,94.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(142,4,4,22,'邱琳紫','计科2306班',90.00,1,'2',NULL,'算法掌握扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,94.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(143,5,4,22,'邱琳紫','计科2306班',93.00,1,'2',NULL,'编程能力强，代码规范','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,101.00,97.00,'A',4.00,NULL,NULL,NULL,NULL),(144,6,4,22,'邱琳紫','计科2306班',91.00,1,'2',NULL,'理论理解深入','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(145,1,5,22,'宋新鹏','计科2306班',91.00,1,'2',NULL,'学习积极主动','2026-05-27 10:00:00','2026-06-27 09:58:51',98.00,90.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(146,2,5,22,'宋新鹏','计科2306班',89.00,2,'2',NULL,'阅读能力较强','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,82.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(147,3,5,22,'宋新鹏','计科2306班',85.00,2,'2',NULL,'向量空间理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(148,4,5,22,'宋新鹏','计科2306班',92.00,1,'2',NULL,'项目完成出色','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(149,5,5,22,'宋新鹏','计科2306班',88.00,2,'2',NULL,'面向对象设计合理','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,85.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(150,6,5,22,'宋新鹏','计科2306班',90.00,1,'2',NULL,'实验报告完整','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,91.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(151,1,6,22,'赵琦','计科2306班',85.00,2,'2',NULL,'基础扎实，解题规范','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(152,2,6,22,'赵琦','计科2306班',82.00,2,'2',NULL,'听力需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(153,3,6,22,'赵琦','计科2306班',78.00,3,'2',NULL,'部分概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(154,4,6,22,'赵琦','计科2306班',84.00,2,'2',NULL,'递归理解较好','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,78.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(155,5,6,22,'赵琦','计科2306班',86.00,2,'2',NULL,'面向对象掌握良好','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,92.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(156,6,6,22,'赵琦','计科2306班',80.00,2,'2',NULL,'需加强实践','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,87.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(157,1,7,22,'苏红阳','计科2306班',82.00,2,'2',NULL,'课堂参与积极','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(158,2,7,22,'苏红阳','计科2306班',88.00,2,'2',NULL,'写作能力突出','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(159,3,7,22,'苏红阳','计科2306班',80.00,2,'2',NULL,'推导过程完整','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,85.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(160,4,7,22,'苏红阳','计科2306班',83.00,2,'2',NULL,'树结构掌握较好','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,78.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(161,5,7,22,'苏红阳','计科2306班',85.00,2,'2',NULL,'多线程理解深入','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(162,6,7,22,'苏红阳','计科2306班',87.00,2,'2',NULL,'硬件原理理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,83.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(163,1,8,22,'唐浩然','计科2306班',88.00,2,'2',NULL,'解题思路清晰','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,84.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(164,2,8,22,'唐浩然','计科2306班',84.00,2,'2',NULL,'词汇量丰富','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,79.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(165,3,8,22,'唐浩然','计科2306班',86.00,2,'2',NULL,'矩阵运算熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,86.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(166,4,8,22,'唐浩然','计科2306班',81.00,2,'2',NULL,'图算法待加强','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(167,5,8,22,'唐浩然','计科2306班',82.00,2,'2',NULL,'集合框架熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,83.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(168,6,8,22,'唐浩然','计科2306班',85.00,2,'2',NULL,'CPU原理理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,91.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(169,1,9,22,'王宏民','计科2306班',75.00,3,'2',NULL,'计算能力一般','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(170,2,9,22,'王宏民','计科2306班',78.00,3,'2',NULL,'语法基础尚可','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,85.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(171,3,9,22,'王宏民','计科2306班',72.00,3,'2',NULL,'向量概念需巩固','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,79.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(172,4,9,22,'王宏民','计科2306班',76.00,3,'2',NULL,'排序算法掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,84.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(173,5,9,22,'王宏民','计科2306班',74.00,3,'2',NULL,'异常处理需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,69.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(174,6,9,22,'王宏民','计科2306班',77.00,3,'2',NULL,'存储器理解一般','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,79.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(175,1,10,22,'王玉蛟','计科2306班',83.00,2,'2',NULL,'学习态度认真','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,75.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(176,2,10,22,'王玉蛟','计科2306班',85.00,2,'2',NULL,'翻译能力较强','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(177,3,10,22,'王玉蛟','计科2306班',79.00,3,'2',NULL,'特征值计算需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(178,4,10,22,'王玉蛟','计科2306班',87.00,2,'2',NULL,'图论部分表现好','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,81.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(179,5,10,22,'王玉蛟','计科2306班',84.00,2,'2',NULL,'GUI编程有创意','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,86.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(180,6,10,22,'王玉蛟','计科2306班',82.00,2,'2',NULL,'IO设备理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',77.00,76.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(181,1,11,22,'吴雨航','计科2306班',72.00,3,'2',NULL,'积分计算有困难','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,71.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(182,2,11,22,'吴雨航','计科2306班',76.00,3,'2',NULL,'词汇量不足','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,82.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(183,3,11,22,'吴雨航','计科2306班',74.00,3,'2',NULL,'需要多练习','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,66.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(184,4,11,22,'吴雨航','计科2306班',71.00,3,'2',NULL,'链表操作不熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,72.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(185,5,11,22,'吴雨航','计科2306班',79.00,3,'2',NULL,'基础语法过关','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,83.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(186,6,11,22,'吴雨航','计科2306班',73.00,3,'2',NULL,'指令系统需复习','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,77.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(187,1,12,22,'徐瑞瑶','计科2306班',87.00,2,'2',NULL,'数学思维好','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,80.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(188,2,12,22,'徐瑞瑶','计科2306班',83.00,2,'2',NULL,'听力理解优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,83.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(189,3,12,22,'徐瑞瑶','计科2306班',89.00,2,'2',NULL,'线性变换理解深入','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,86.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(190,4,12,22,'徐瑞瑶','计科2306班',85.00,2,'2',NULL,'哈希表掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,84.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(191,5,12,22,'徐瑞瑶','计科2306班',86.00,2,'2',NULL,'设计模式理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(192,6,12,22,'徐瑞瑶','计科2306班',84.00,2,'2',NULL,'总线设计理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,86.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(193,1,13,23,'韩坤','计科2306班',68.00,3,'2',NULL,'基础薄弱需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',61.00,75.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(194,2,13,23,'韩坤','计科2306班',72.00,3,'2',NULL,'语法错误较多','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,79.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(195,3,13,23,'韩坤','计科2306班',70.00,3,'2',NULL,'矩阵乘法常出错','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,70.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(196,4,13,23,'韩坤','计科2306班',65.00,4,'2',NULL,'栈和队列混淆','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,70.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(197,5,13,23,'韩坤','计科2306班',75.00,3,'2',NULL,'循环逻辑需巩固','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,82.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(198,6,13,23,'韩坤','计科2306班',71.00,3,'2',NULL,'基本概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,73.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(199,1,14,23,'张博巍','计科2306班',93.00,1,'2',NULL,'微积分掌握扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',100.00,100.00,100.00,'A',4.00,NULL,NULL,NULL,NULL),(200,2,14,23,'张博巍','计科2306班',90.00,1,'2',NULL,'口语发音标准','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,87.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(201,3,14,23,'张博巍','计科2306班',88.00,2,'2',NULL,'二次型理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,83.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(202,4,14,23,'张博巍','计科2306班',91.00,1,'2',NULL,'动态规划掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,87.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(203,5,14,23,'张博巍','计科2306班',94.00,1,'2',NULL,'项目开发能力强','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,96.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(204,6,14,23,'张博巍','计科2306班',89.00,2,'2',NULL,'体系结构掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(205,1,15,23,'程启頔','计科2306班',70.00,3,'2',NULL,'上课注意力不集中','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,62.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(206,2,15,23,'程启頔','计科2306班',74.00,3,'2',NULL,'听说能力待提高','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,75.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(207,3,15,23,'程启頔','计科2306班',68.00,3,'2',NULL,'证明题困难','2026-05-27 10:00:00','2026-06-27 09:58:51',60.00,75.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(208,4,15,23,'程启頔','计科2306班',72.00,3,'2',NULL,'查找算法需练习','2026-05-27 10:00:00','2026-06-27 09:58:51',77.00,78.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(209,5,15,23,'程启頔','计科2306班',76.00,3,'2',NULL,'集合使用基本正确','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(210,6,15,23,'程启頔','计科2306班',70.00,3,'2',NULL,'汇编基础需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,66.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(211,1,16,23,'邓雨轩','计科2306班',84.00,2,'2',NULL,'数列极限理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,86.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(212,2,16,23,'邓雨轩','计科2306班',86.00,2,'2',NULL,'作文表达流畅','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,87.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(213,3,16,23,'邓雨轩','计科2306班',80.00,2,'2',NULL,'方程组消元准确','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,76.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(214,4,16,23,'邓雨轩','计科2306班',82.00,2,'2',NULL,'二叉树遍历掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,87.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(215,5,16,23,'邓雨轩','计科2306班',85.00,2,'2',NULL,'接口设计规范','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,84.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(216,6,16,23,'邓雨轩','计科2306班',83.00,2,'2',NULL,'中断处理理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,90.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(217,1,17,23,'高菲','计科2306班',86.00,2,'2',NULL,'导数应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,89.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(218,2,17,23,'高菲','计科2306班',88.00,2,'2',NULL,'阅读理解准确','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,87.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(219,3,17,23,'高菲','计科2306班',82.00,2,'2',NULL,'矩阵对角化掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,76.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(220,4,17,23,'高菲','计科2306班',84.00,2,'2',NULL,'B树B+树理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(221,5,17,23,'高菲','计科2306班',87.00,2,'2',NULL,'IO流操作熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,94.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(222,6,17,23,'高菲','计科2306班',81.00,2,'2',NULL,'流水线技术理解','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,87.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(223,1,18,23,'胡钰','计科2306班',78.00,3,'2',NULL,'不定积分有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(224,2,18,23,'胡钰','计科2306班',80.00,2,'2',NULL,'语法有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,80.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(225,3,18,23,'胡钰','计科2306班',72.00,3,'2',NULL,'几何意义理解不足','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,68.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(226,4,18,23,'胡钰','计科2306班',75.00,3,'2',NULL,'代码实现有困难','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,76.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(227,5,18,23,'胡钰','计科2306班',79.00,3,'2',NULL,'网络编程需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,74.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(228,6,18,23,'胡钰','计科2306班',74.00,3,'2',NULL,'缓存概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,73.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(229,1,19,23,'李佳芮','计科2306班',92.00,1,'2',NULL,'空间解析几何好','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(230,2,19,23,'李佳芮','计科2306班',89.00,2,'2',NULL,'演讲能力出色','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,87.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(231,3,19,23,'李佳芮','计科2306班',93.00,1,'2',NULL,'秩与线性相关理解深','2026-05-27 10:00:00','2026-06-27 09:58:51',96.00,86.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(232,4,19,23,'李佳芮','计科2306班',90.00,1,'2',NULL,'最短路径算法优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,92.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(233,5,19,23,'李佳芮','计科2306班',91.00,1,'2',NULL,'反射机制掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,97.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(234,6,19,23,'李佳芮','计科2306班',90.00,1,'2',NULL,'存储层次理解深','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,94.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(235,1,20,23,'李爽','计科2306班',66.00,4,'2',NULL,'基础不扎实','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,69.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(236,2,20,23,'李爽','计科2306班',71.00,3,'2',NULL,'词汇积累不够','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(237,3,20,23,'李爽','计科2306班',69.00,3,'2',NULL,'秩判断困难','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(238,4,20,23,'李爽','计科2306班',70.00,3,'2',NULL,'遍历算法需复习','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,74.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(239,5,20,23,'李爽','计科2306班',68.00,3,'2',NULL,'继承多态混淆','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,74.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(240,6,20,23,'李爽','计科2306班',72.00,3,'2',NULL,'数值表示理解一般','2026-05-27 10:00:00','2026-06-27 09:58:51',77.00,75.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(241,1,21,23,'梁莹静','计科2306班',85.00,2,'2',NULL,'多元函数掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,79.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(242,2,21,23,'梁莹静','计科2306班',87.00,2,'2',NULL,'跨文化交际能力好','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,89.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(243,3,21,23,'梁莹静','计科2306班',81.00,2,'2',NULL,'线性空间概念清晰','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(244,4,21,23,'梁莹静','计科2306班',83.00,2,'2',NULL,'堆排序理解好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,82.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(245,5,21,23,'梁莹静','计科2306班',86.00,2,'2',NULL,'泛型使用规范','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,92.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(246,6,21,23,'梁莹静','计科2306班',84.00,2,'2',NULL,'指令流水掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(247,1,22,23,'孟阳','计科2306班',73.00,3,'2',NULL,'函数连续性需加强','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(248,2,22,23,'孟阳','计科2306班',75.00,3,'2',NULL,'长难句理解困难','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,73.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(249,3,22,23,'孟阳','计科2306班',71.00,3,'2',NULL,'基变换理解不足','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,72.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(250,4,22,23,'孟阳','计科2306班',77.00,3,'2',NULL,'快速排序基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(251,5,22,23,'孟阳','计科2306班',74.00,3,'2',NULL,'多线程有进步','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,67.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(252,6,22,23,'孟阳','计科2306班',76.00,3,'2',NULL,'微程序控制器理解','2026-05-27 10:00:00','2026-06-27 09:58:51',77.00,74.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(253,1,23,23,'王瑾泰','计科2306班',82.00,2,'2',NULL,'定积分应用熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,78.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(254,2,23,23,'王瑾泰','计科2306班',84.00,2,'2',NULL,'完形填空准确','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,81.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(255,3,23,23,'王瑾泰','计科2306班',78.00,3,'2',NULL,'正交变换需巩固','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,84.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(256,4,23,23,'王瑾泰','计科2306班',85.00,2,'2',NULL,'AVL树掌握好','2026-05-27 10:00:00','2026-06-27 09:58:51',79.00,77.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(257,5,23,28,'王瑾泰','计科2306班',83.00,2,'2',NULL,'JDBC操作熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,85.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(258,6,23,28,'王瑾泰','计科2306班',80.00,2,'2',NULL,'虚拟内存理解','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(259,1,24,28,'王平洋','计科2306班',74.00,3,'2',NULL,'级数概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,78.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(260,2,24,28,'王平洋','计科2306班',73.00,3,'2',NULL,'翻译题薄弱','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,75.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(261,3,24,28,'王平洋','计科2306班',76.00,3,'2',NULL,'合同关系理解一般','2026-05-27 10:00:00','2026-06-27 09:58:51',77.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(262,4,24,28,'王平洋','计科2306班',79.00,3,'2',NULL,'并查集基本掌握','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,77.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(263,5,24,28,'王平洋','计科2306班',71.00,3,'2',NULL,'Lambda表达式生疏','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,78.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(264,6,24,28,'王平洋','计科2306班',75.00,3,'2',NULL,'DMA方式需复习','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,72.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(265,1,25,28,'夏志浩','计科2306班',94.00,1,'2',NULL,'数学基础雄厚','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,98.00,98.00,'A',4.00,NULL,NULL,NULL,NULL),(266,2,25,28,'夏志浩','计科2306班',91.00,1,'2',NULL,'学术写作优秀','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,93.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(267,3,25,28,'夏志浩','计科2306班',87.00,2,'2',NULL,'抽象思维能力强','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,84.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(268,4,25,28,'夏志浩','计科2306班',95.00,1,'2',NULL,'算法竞赛获奖','2026-05-27 10:00:00','2026-06-27 09:58:51',100.00,96.00,98.00,'A',4.00,NULL,NULL,NULL,NULL),(269,5,25,28,'夏志浩','计科2306班',92.00,1,'2',NULL,'精通JVM调优','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(270,6,25,28,'夏志浩','计科2306班',88.00,2,'2',NULL,'多核处理器理解深','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(271,1,26,28,'张健','计科2306班',64.00,4,'2',NULL,'多处知识漏洞','2026-05-27 10:00:00','2026-06-27 09:58:51',62.00,69.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(272,2,26,28,'张健','计科2306班',69.00,3,'2',NULL,'听力严重薄弱','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,67.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(273,3,26,28,'张健','计科2306班',62.00,4,'2',NULL,'高斯消元不熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,70.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(274,4,26,28,'张健','计科2306班',68.00,3,'2',NULL,'基本操作不熟练','2026-05-27 10:00:00','2026-06-27 09:58:51',66.00,61.00,63.00,'D',1.30,NULL,NULL,NULL,NULL),(275,5,26,28,'张健','计科2306班',65.00,4,'2',NULL,'面向对象理解不足','2026-05-27 10:00:00','2026-06-27 09:58:51',57.00,72.00,66.00,'D',1.60,NULL,NULL,NULL,NULL),(276,6,26,28,'张健','计科2306班',71.00,3,'2',NULL,'运算器概念模糊','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,74.00,72.00,'C',2.20,NULL,NULL,NULL,NULL),(277,1,3,28,'司明','计科2306班',87.00,3,'3',NULL,'第3学期高等数学','2024-06-20 00:00:00','2026-06-27 09:58:51',84.00,92.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(278,2,3,28,'司明','计科2306班',80.50,3,'3',NULL,'第3学期大学英语','2024-06-20 00:00:00','2026-06-27 09:58:51',82.00,76.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(279,3,3,28,'司明','计科2306班',70.00,2,'3',NULL,'第3学期计算机基础','2024-06-20 00:00:00','2026-06-27 09:58:51',65.00,68.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(280,4,3,28,'司明','计科2306班',91.00,4,'3',NULL,'第3学期数据结构优秀','2024-06-20 00:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(281,5,3,28,'司明','计科2306班',89.00,4,'3',NULL,'第3学期计算机网络','2024-06-20 00:00:00','2026-06-27 09:58:51',81.00,95.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(282,6,3,28,'司明','计科2306班',62.00,1,'3',NULL,'第3学期操作系统','2024-06-20 00:00:00','2026-06-27 09:58:51',55.00,67.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(283,1,3,28,'司明','计科2306班',71.50,2,'4',NULL,'第4学期高等数学下滑','2025-01-15 00:00:00','2026-06-27 09:58:51',74.00,78.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(284,2,3,28,'司明','计科2306班',51.00,0,'4',NULL,'第4学期大学英语不及格','2025-01-15 00:00:00','2026-06-27 09:58:51',48.00,56.00,53.00,'F',0.30,NULL,NULL,NULL,NULL),(285,3,3,28,'司明','计科2306班',59.00,0,'4',NULL,'第4学期计算机基础不及格','2025-01-15 00:00:00','2026-06-27 09:58:51',58.00,53.00,55.00,'F',0.50,NULL,NULL,NULL,NULL),(286,4,3,28,'司明','计科2306班',84.00,3,'4',NULL,'第4学期数据结构','2025-01-15 00:00:00','2026-06-27 09:58:51',83.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(287,6,3,28,'司明','计科2306班',40.00,0,'4',NULL,'第4学期操作系统严重不及格','2025-01-15 00:00:00','2026-06-27 09:58:51',34.00,39.00,37.00,'F',0.00,NULL,NULL,NULL,NULL),(288,1,3,28,'司明','计科2306班',56.50,0,'5',NULL,'第5学期高等数学持续下滑','2025-06-20 00:00:00','2026-06-27 09:58:51',52.00,54.00,53.00,'F',0.30,NULL,NULL,NULL,NULL),(289,5,3,28,'司明','计科2306班',65.00,1,'5',NULL,'第5学期计算机网络勉强及格','2025-06-20 00:00:00','2026-06-27 09:58:51',67.00,58.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(290,3,3,28,'司明','计科2306班',47.00,0,'5',NULL,'第5学期计算机基础补考仍不及格','2025-06-20 00:00:00','2026-06-27 09:58:51',44.00,53.00,49.00,'F',0.00,NULL,NULL,NULL,NULL),(291,7,3,28,'司明','计科2306班',85.00,2,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',87.00,86.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(294,8,3,28,'司明','计科2306班',78.00,4,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',70.00,77.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(295,8,4,28,'邱琳紫','计科2306班',88.00,2,'6',NULL,NULL,'2026-06-25 09:58:16','2026-06-27 09:58:51',81.00,84.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(296,7,4,28,'邱琳紫','计科2306班',90.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,95.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(297,7,5,28,'宋新鹏','计科2306班',88.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,83.00,87.00,'B',3.70,NULL,NULL,NULL,NULL),(298,7,6,28,'赵琦','计科2306班',85.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,81.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(299,7,7,28,'苏红阳','计科2306班',92.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,94.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(300,7,8,28,'唐浩然','计科2306班',80.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,73.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(301,7,9,28,'王宏民','计科2306班',78.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,82.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(302,7,10,28,'王玉蛟','计科2306班',86.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(303,7,11,28,'吴雨航','计科2306班',75.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,79.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(304,7,12,28,'徐瑞瑶','计科2306班',88.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,93.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(305,7,13,28,'韩坤','计科2306班',72.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(306,7,14,28,'张博巍','计科2306班',94.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',98.00,86.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(307,7,15,28,'程启頔','计科2306班',70.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',65.00,75.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(308,7,16,28,'邓雨轩','计科2306班',83.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,82.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(309,7,17,28,'高菲','计科2306班',89.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(310,7,18,28,'胡钰','计科2306班',76.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,82.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(311,7,19,28,'李佳芮','计科2306班',91.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',96.00,93.00,94.00,'A',4.00,NULL,NULL,NULL,NULL),(312,7,20,28,'李爽','计科2306班',68.00,4,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,74.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(313,7,21,28,'梁莹静','计科2306班',87.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,94.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(314,7,22,28,'孟阳','计科2306班',74.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,68.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(315,7,23,28,'王瑾泰','计科2306班',82.00,2,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(316,7,24,28,'王平洋','计科2306班',73.00,3,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,69.00,73.00,'C',2.30,NULL,NULL,NULL,NULL),(317,7,25,28,'夏志浩','计科2306班',95.00,1,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,92.00,93.00,'A',4.00,NULL,NULL,NULL,NULL),(318,7,26,28,'张健','计科2306班',65.00,4,NULL,NULL,'心理健康','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,67.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(319,8,4,28,'邱琳紫','计科2306班',88.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(320,8,5,28,'宋新鹏','计科2306班',90.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,83.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(321,8,6,18,'赵琦','计科2306班',82.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,90.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(322,8,7,18,'苏红阳','计科2306班',86.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',93.00,89.00,91.00,'A',4.00,NULL,NULL,NULL,NULL),(323,8,8,18,'唐浩然','计科2306班',84.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,87.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(324,8,9,18,'王宏民','计科2306班',76.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',69.00,80.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(325,8,10,18,'王玉蛟','计科2306班',83.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,82.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(326,8,11,18,'吴雨航','计科2306班',74.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(327,8,12,18,'徐瑞瑶','计科2306班',85.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,85.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(328,8,13,18,'韩坤','计科2306班',70.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,71.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(329,8,14,18,'张博巍','计科2306班',92.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',95.00,97.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(330,8,15,18,'程启頔','计科2306班',68.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,61.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(331,8,16,18,'邓雨轩','计科2306班',81.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',82.00,81.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(332,8,17,18,'高菲','计科2306班',87.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,86.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(333,8,18,18,'胡钰','计科2306班',78.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,84.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(334,8,19,18,'李佳芮','计科2306班',93.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',89.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(335,8,20,18,'李爽','计科2306班',66.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,70.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(336,8,21,18,'梁莹静','计科2306班',84.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,86.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(337,8,22,18,'孟阳','计科2306班',72.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,76.00,74.00,'C',2.40,NULL,NULL,NULL,NULL),(338,8,23,18,'王瑾泰','计科2306班',80.00,2,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,75.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(339,8,24,18,'王平洋','计科2306班',71.00,3,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',63.00,75.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(340,8,25,18,'夏志浩','计科2306班',96.00,1,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(341,8,26,18,'张健','计科2306班',63.00,4,NULL,NULL,'职业规划','2026-05-27 10:00:00','2026-06-27 09:58:51',58.00,62.00,60.00,'D',1.00,NULL,NULL,NULL,NULL),(342,9,4,18,'邱琳紫','计科2306班',85.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',81.00,77.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(343,9,5,18,'宋新鹏','计科2306班',87.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',91.00,80.00,84.00,'B',3.40,NULL,NULL,NULL,NULL),(344,9,6,18,'赵琦','计科2306班',83.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',78.00,80.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(345,9,7,18,'苏红阳','计科2306班',90.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(346,9,8,18,'唐浩然','计科2306班',79.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,81.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(347,9,9,18,'王宏民','计科2306班',74.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',67.00,73.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(348,9,10,18,'王玉蛟','计科2306班',88.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,83.00,85.00,'B',3.50,NULL,NULL,NULL,NULL),(349,9,11,18,'吴雨航','计科2306班',72.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,79.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(350,9,12,18,'徐瑞瑶','计科2306班',86.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,90.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(351,9,13,18,'韩坤','计科2306班',68.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',72.00,61.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(352,9,14,18,'张博巍','计科2306班',89.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',87.00,90.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(353,9,15,18,'程启頔','计科2306班',66.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,72.00,70.00,'C',2.00,NULL,NULL,NULL,NULL),(354,9,16,18,'邓雨轩','计科2306班',80.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,84.00,80.00,'B',3.00,NULL,NULL,NULL,NULL),(355,9,17,18,'高菲','计科2306班',91.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,91.00,88.00,'B',3.80,NULL,NULL,NULL,NULL),(356,9,18,18,'胡钰','计科2306班',75.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',71.00,68.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(357,9,19,18,'李佳芮','计科2306班',94.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',94.00,90.00,92.00,'A',4.00,NULL,NULL,NULL,NULL),(358,9,20,18,'李爽','计科2306班',64.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',68.00,62.00,64.00,'D',1.40,NULL,NULL,NULL,NULL),(359,9,21,18,'梁莹静','计科2306班',82.00,2,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,80.00,82.00,'B',3.20,NULL,NULL,NULL,NULL),(360,9,22,18,'孟阳','计科2306班',70.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',62.00,67.00,65.00,'D',1.50,NULL,NULL,NULL,NULL),(361,9,23,18,'王瑾泰','计科2306班',78.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,76.00,76.00,'C',2.60,NULL,NULL,NULL,NULL),(362,9,24,18,'王平洋','计科2306班',69.00,3,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',77.00,67.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(363,9,25,18,'夏志浩','计科2306班',92.00,1,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',97.00,96.00,96.00,'A',4.00,NULL,NULL,NULL,NULL),(364,9,26,18,'张健','计科2306班',61.00,4,NULL,NULL,'艺术鉴赏','2026-05-27 10:00:00','2026-06-27 09:58:51',59.00,54.00,56.00,'F',0.60,NULL,NULL,NULL,NULL),(365,10,4,18,'邱琳紫','计科2306班',92.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,91.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(366,10,5,18,'宋新鹏','计科2306班',86.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',92.00,82.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(367,10,6,18,'赵琦','计科2306班',84.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,77.00,79.00,'C',2.90,NULL,NULL,NULL,NULL),(368,10,7,18,'苏红阳','计科2306班',88.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',84.00,88.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(369,10,8,18,'唐浩然','计科2306班',81.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,81.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(370,10,9,18,'王宏民','计科2306班',77.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,71.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(371,10,10,18,'王玉蛟','计科2306班',90.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,84.00,86.00,'B',3.60,NULL,NULL,NULL,NULL),(372,10,11,18,'吴雨航','计科2306班',73.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',80.00,72.00,75.00,'C',2.50,NULL,NULL,NULL,NULL),(373,10,12,18,'徐瑞瑶','计科2306班',87.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',83.00,80.00,81.00,'B',3.10,NULL,NULL,NULL,NULL),(374,10,13,18,'韩坤','计科2306班',69.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',75.00,62.00,67.00,'D',1.70,NULL,NULL,NULL,NULL),(375,10,14,18,'张博巍','计科2306班',91.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',86.00,92.00,90.00,'A',4.00,NULL,NULL,NULL,NULL),(376,10,15,18,'程启頔','计科2306班',67.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',60.00,64.00,62.00,'D',1.20,NULL,NULL,NULL,NULL),(377,10,16,18,'邓雨轩','计科2306班',82.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',74.00,80.00,78.00,'C',2.80,NULL,NULL,NULL,NULL),(378,10,17,18,'高菲','计科2306班',89.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',85.00,82.00,83.00,'B',3.30,NULL,NULL,NULL,NULL),(379,10,18,18,'胡钰','计科2306班',77.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,69.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(380,10,19,18,'李佳芮','计科2306班',90.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',90.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(381,10,20,18,'李爽','计科2306班',65.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',73.00,65.00,68.00,'D',1.80,NULL,NULL,NULL,NULL),(382,10,21,18,'梁莹静','计科2306班',85.00,2,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',88.00,89.00,89.00,'B',3.90,NULL,NULL,NULL,NULL),(383,10,22,18,'孟阳','计科2306班',71.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',70.00,71.00,71.00,'C',2.10,NULL,NULL,NULL,NULL),(384,10,23,18,'王瑾泰','计科2306班',79.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:58:51',76.00,77.00,77.00,'C',2.70,NULL,NULL,NULL,NULL),(385,10,24,29,'王平洋','计科2306班',70.00,3,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',62.00,73.00,69.00,'D',1.90,NULL,NULL,NULL,NULL),(386,10,25,29,'夏志浩','计科2306班',93.00,1,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',86.00,101.00,95.00,'A',4.00,NULL,NULL,NULL,NULL),(387,10,26,29,'张健','计科2306班',62.00,4,NULL,NULL,'传统文化','2026-05-27 10:00:00','2026-06-27 09:41:16',57.00,69.00,64.00,'D',1.40,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `score_record` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=168 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `scores`
--

LOCK TABLES `scores` WRITE;
/*!40000 ALTER TABLE `scores` DISABLE KEYS */;
INSERT INTO `scores` VALUES (1,3,1,2,2025,78.00,85.00,82.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,3,2,2,2025,80.00,82.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,3,3,2,2025,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,3,4,2,2025,90.00,88.00,89.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(5,3,5,2,2025,82.00,85.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(6,3,6,2,2025,70.00,78.00,74.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(7,4,1,2,2025,92.00,95.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(8,4,2,2,2025,88.00,90.00,89.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(9,4,3,2,2025,85.00,88.00,87.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(10,4,4,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(11,4,5,2,2025,90.00,92.00,91.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(12,4,6,2,2025,82.00,85.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(13,5,1,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(14,5,2,2,2025,85.00,88.00,87.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(15,5,3,2,2025,82.00,85.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(16,5,4,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(17,5,5,2,2025,90.00,93.00,92.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(18,5,6,2,2025,80.00,84.00,82.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(19,6,1,2,2025,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(20,6,2,2,2025,80.00,85.00,83.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(21,6,3,2,2025,70.00,78.00,74.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(22,6,4,2,2025,85.00,88.00,87.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(23,6,5,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(24,6,6,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(25,7,1,2,2025,82.00,86.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(26,7,2,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(27,7,3,2,2025,85.00,88.00,87.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(28,7,4,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(29,7,5,2,2025,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(30,7,6,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(31,8,1,2,2025,85.00,90.00,88.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(32,8,2,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(33,8,3,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(34,8,4,2,2025,82.00,86.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(35,8,5,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(36,8,6,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(37,9,1,2,2025,62.00,70.00,66.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(38,9,2,2,2025,68.00,75.00,72.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(39,9,3,2,2025,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(40,9,4,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(41,9,5,2,2025,70.00,76.00,73.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(42,9,6,2,2025,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(43,10,1,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(44,10,2,2,2025,82.00,86.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(45,10,3,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(46,10,4,2,2025,88.00,90.00,89.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(47,10,5,2,2025,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(48,10,6,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(49,11,1,2,2025,60.00,68.00,64.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(50,11,2,2,2025,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(51,11,3,2,2025,62.00,68.00,65.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(52,11,4,2,2025,68.00,75.00,72.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(53,11,5,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(54,11,6,2,2025,70.00,76.00,73.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(55,12,1,2,2025,85.00,88.00,87.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(56,12,2,2,2025,82.00,86.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(57,12,3,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(58,12,4,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(59,12,5,2,2025,85.00,90.00,88.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(60,12,6,2,2025,78.00,85.00,82.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(61,13,1,2,2025,60.00,65.00,63.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(62,13,2,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(63,13,3,2,2025,68.00,72.00,70.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(64,13,4,2,2025,55.00,62.00,59.00,'F',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(65,13,5,2,2025,65.00,70.00,68.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(66,13,6,2,2025,50.00,58.00,54.00,'F',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(67,14,1,2,2025,90.00,95.00,93.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(68,14,2,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(69,14,3,2,2025,85.00,90.00,88.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(70,14,4,2,2025,92.00,96.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(71,14,5,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(72,14,6,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(73,15,1,2,2025,58.00,68.00,63.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(74,15,2,2,2025,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(75,15,3,2,2025,60.00,68.00,64.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(76,15,4,2,2025,62.00,70.00,66.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(77,15,5,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(78,15,6,2,2025,70.00,76.00,73.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(79,16,1,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(80,16,2,2,2025,82.00,88.00,85.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(81,16,3,2,2025,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(82,16,4,2,2025,88.00,90.00,89.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(83,16,5,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(84,16,6,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(85,17,1,2,2025,85.00,90.00,88.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(86,17,2,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(87,17,3,2,2025,82.00,86.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(88,17,4,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(89,17,5,2,2025,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(90,17,6,2,2025,82.00,88.00,85.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(91,18,1,2,2025,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(92,18,2,2,2025,70.00,76.00,73.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(93,18,3,2,2025,62.00,70.00,66.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(94,18,4,2,2025,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(95,18,5,2,2025,80.00,85.00,83.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(96,18,6,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(97,19,1,2,2025,92.00,96.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(98,19,2,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(99,19,3,2,2025,90.00,95.00,93.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(100,19,4,2,2025,85.00,90.00,88.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(101,19,5,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(102,19,6,2,2025,92.00,95.00,94.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(103,20,1,2,2025,50.00,58.00,54.00,'F',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(104,20,2,2,2025,70.00,75.00,73.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(105,20,3,2,2025,48.00,55.00,52.00,'F',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(106,20,4,2,2025,68.00,72.00,70.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(107,20,5,2,2025,42.00,52.00,47.00,'F',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(108,20,6,2,2025,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(109,21,1,2,2025,82.00,86.00,84.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(110,21,2,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(111,21,3,2,2025,85.00,88.00,87.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(112,21,4,2,2025,80.00,85.00,83.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(113,21,5,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(114,21,6,2,2025,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(115,22,1,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(116,22,2,2,2025,68.00,75.00,72.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(117,22,3,2,2025,70.00,76.00,73.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(118,22,4,2,2025,75.00,80.00,78.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(119,22,5,2,2025,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(120,22,6,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(121,23,1,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(122,23,2,2,2025,75.00,82.00,79.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(123,23,3,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(124,23,4,2,2025,82.00,88.00,85.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(125,23,5,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(126,23,6,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(127,24,1,2,2025,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(128,24,2,2,2025,70.00,76.00,73.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(129,24,3,2,2025,62.00,70.00,66.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(130,24,4,2,2025,78.00,82.00,80.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(131,24,5,2,2025,72.00,78.00,75.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(132,24,6,2,2025,68.00,76.00,72.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(133,25,1,2,2025,88.00,92.00,90.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(134,25,2,2,2025,85.00,90.00,88.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(135,25,3,2,2025,78.00,84.00,81.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(136,25,4,2,2025,82.00,88.00,85.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(137,25,5,2,2025,88.00,95.00,92.00,'A',4.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(138,25,6,2,2025,78.00,86.00,82.00,'B',3.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(139,26,1,2,2025,48.00,55.00,52.00,'F',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(140,26,2,2,2025,68.00,75.00,72.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(141,26,3,2,2025,45.00,50.00,48.00,'F',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(142,26,4,2,2025,65.00,72.00,69.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(143,26,5,2,2025,42.00,55.00,49.00,'F',0.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(144,26,6,2,2025,62.00,70.00,66.00,'C',2.00,NULL,NULL,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(145,3,1,3,2024,85.00,88.00,87.00,'良好',3.30,NULL,NULL,'2024-06-20 02:00:00','2026-06-04 07:06:27'),(146,3,2,3,2024,78.00,82.00,80.50,'良好',3.00,NULL,NULL,'2024-06-20 02:00:00','2026-06-04 07:06:27'),(147,3,3,3,2024,72.00,68.00,70.00,'中等',2.30,NULL,NULL,'2024-06-20 02:00:00','2026-06-04 07:06:27'),(148,3,4,3,2024,90.00,92.00,91.00,'优秀',4.00,NULL,NULL,'2024-06-20 02:00:00','2026-06-04 07:06:27'),(149,3,5,3,2024,88.00,90.00,89.00,'良好',3.70,NULL,NULL,'2024-06-20 02:00:00','2026-06-04 07:06:27'),(150,3,6,3,2024,65.00,60.00,62.00,'及格',1.30,NULL,NULL,'2024-06-20 02:00:00','2026-06-04 07:06:27'),(151,3,1,4,2025,70.00,72.00,71.50,'中等',2.30,NULL,NULL,'2025-01-14 16:00:00','2026-06-04 07:06:51'),(152,3,2,4,2025,55.00,48.00,51.00,'不及格',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-06-04 07:06:51'),(153,3,3,4,2025,60.00,58.00,59.00,'不及格',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-06-04 07:06:51'),(154,3,4,4,2025,82.00,85.00,84.00,'良好',3.30,NULL,NULL,'2025-01-14 16:00:00','2026-06-04 07:06:51'),(155,3,5,4,2025,75.00,70.00,72.00,'中等',2.30,NULL,NULL,'2025-01-14 16:00:00','2026-06-04 07:06:51'),(156,3,6,4,2025,42.00,38.00,40.00,'不及格',0.00,NULL,NULL,'2025-01-14 16:00:00','2026-06-04 07:06:51'),(157,3,1,5,2025,58.00,55.00,56.50,'不及格',0.00,NULL,NULL,'2025-06-19 16:00:00','2026-06-04 07:06:58'),(158,3,2,5,2025,65.00,62.00,63.00,'及格',1.30,NULL,NULL,'2025-06-19 16:00:00','2026-06-04 07:06:58'),(159,3,3,5,2025,50.00,45.00,47.00,'不及格',0.00,NULL,NULL,'2025-06-19 16:00:00','2026-06-04 07:06:58'),(160,3,4,5,2025,78.00,75.00,76.00,'中等',2.30,NULL,NULL,'2025-06-19 16:00:00','2026-06-04 07:06:58'),(161,3,5,5,2025,66.00,64.00,65.00,'及格',1.30,NULL,NULL,'2025-06-19 16:00:00','2026-06-04 07:06:58'),(162,3,7,5,2025,88.00,85.00,86.00,'良好',3.30,NULL,NULL,'2025-06-19 16:00:00','2026-06-04 07:06:58'),(163,3,8,5,2025,90.00,92.00,91.00,'优秀',4.00,NULL,NULL,'2025-06-19 16:00:00','2026-06-04 07:06:58'),(164,3,2,6,2025,70.00,NULL,35.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(165,3,3,6,2025,40.00,NULL,20.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(166,3,4,6,2025,85.00,NULL,42.50,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04'),(167,3,6,6,2025,50.00,NULL,25.00,NULL,NULL,NULL,NULL,'2025-12-19 16:00:00','2026-06-04 07:07:04');
/*!40000 ALTER TABLE `scores` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=27 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `student_profile`
--

LOCK TABLES `student_profile` WRITE;
/*!40000 ALTER TABLE `student_profile` DISABLE KEYS */;
INSERT INTO `student_profile` VALUES (3,27,'2023020616','司明',NULL,1,2,1,'计科2306班',NULL,0,'2026-03-04 02:43:20','2026-06-04 07:20:39'),(4,30,'2023020615','邱琳紫',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(5,31,'2023020617','宋新鹏',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(6,32,'2022020137','赵琦',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(7,33,'2023020619','苏红阳',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(8,34,'2023020620','唐浩然',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(9,35,'2023020621','王宏民',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(10,36,'2023020624','王玉蛟',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(11,37,'2023020625','吴雨航',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(12,38,'2023020626','徐瑞瑶',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(13,39,'2023020607','韩坤',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(14,40,'2023020629','张博巍',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(15,41,'2023020504','程启頔',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(16,42,'2023020505','邓雨轩',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(17,43,'2023020506','高菲',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(18,44,'2023020507','胡钰',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(19,45,'2023020509','李佳芮',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(20,46,'2023020510','李爽',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(21,47,'2023020512','梁莹静',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(22,48,'2023020515','孟阳',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(23,49,'2023020519','王瑾泰',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(24,50,'2023020520','王平洋',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40'),(25,51,'2023020521','夏志浩',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:39'),(26,52,'2023020524','张健',NULL,1,2,1,'计科2306班',NULL,2,'2026-03-05 03:00:29','2026-08-03 06:15:40');
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
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
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
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `subscription_preferences`
--

LOCK TABLES `subscription_preferences` WRITE;
/*!40000 ALTER TABLE `subscription_preferences` DISABLE KEYS */;
INSERT INTO `subscription_preferences` VALUES (1,3,1,0,1,1,1,1,1,0,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,4,1,1,1,1,1,1,1,1,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,5,1,0,1,1,1,0,1,0,1,'2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `subscription_preferences` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `support_resource`
--

DROP TABLE IF EXISTS `support_resource`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `support_resource` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_support_resource_type` (`type`),
  KEY `idx_support_resource_category` (`category`),
  KEY `idx_support_resource_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `support_resource`
--

LOCK TABLES `support_resource` WRITE;
/*!40000 ALTER TABLE `support_resource` DISABLE KEYS */;
INSERT INTO `support_resource` VALUES (1,'高等数学学习指南','高等数学核心知识点总结与习题讲解，帮助挂科学生系统复习','document','数学','高等数学包含极限、导数、积分、微分方程等核心内容...','https://example.com/math-guide',NULL,NULL,NULL,'于凤海',150,45,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,'Java编程入门教程','针对Java程序设计挂科学生的补习资料，涵盖面向对象、集合、IO流等','video','编程','Java基础语法、面向对象编程、异常处理、多线程...','https://example.com/java-tutorial',NULL,NULL,NULL,'于凤海',200,60,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(3,'学习时间管理方法','帮助学业困难学生改善学习习惯，提高学习效率','article','学习方法','番茄工作法、费曼学习法、康奈尔笔记法等高效学习方法介绍',NULL,NULL,NULL,NULL,'张明',80,30,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(4,'线性代数精讲','线性代数重点难点解析，含大量练习题和答案','document','数学','矩阵运算、行列式、向量空间、特征值与特征向量...','https://example.com/linear-algebra',NULL,NULL,NULL,'于凤海',120,38,1,'2026-05-27 02:00:00','2026-05-27 02:00:00');
/*!40000 ALTER TABLE `support_resource` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='教师班级管理申请表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `teacher_class_application`
--

LOCK TABLES `teacher_class_application` WRITE;
/*!40000 ALTER TABLE `teacher_class_application` DISABLE KEYS */;
INSERT INTO `teacher_class_application` VALUES (1,29,5,'法撒旦',3,'2026-05-18 12:25:45','2026-05-18 13:48:18','软工2306班','于凤海','10001',0,NULL),(2,29,5,'放大',2,'2026-05-18 13:56:53','2026-05-18 13:57:05','软工2306班','于凤海','10001',0,'fds a'),(3,29,5,'是否都',3,'2026-05-18 14:12:50','2026-05-18 14:13:03','软工2306班','于凤海','10001',0,NULL),(4,29,5,'发的',3,'2026-05-18 14:16:49','2026-05-18 14:16:59','软工2306班','于凤海','10001',0,NULL),(5,29,30,'法国大使',3,'2026-05-18 14:22:57','2026-05-18 14:23:07','计算机2101班','于凤海','10001',0,NULL),(6,29,5,'a发的',3,'2026-05-18 14:24:03','2026-05-18 14:24:22','软工2306班','于凤海','10001',0,NULL),(7,29,5,'安抚的',3,'2026-05-18 14:24:41','2026-05-18 14:24:51','软工2306班','于凤海','10001',0,NULL),(8,29,5,'法撒旦 ',3,'2026-05-18 14:38:20','2026-05-18 14:38:28','软工2306班','于凤海','10001',0,NULL),(9,29,5,'的发生',1,'2026-05-18 15:05:04','2026-05-18 15:07:19','软工2306班','于凤海','10001',0,NULL),(10,29,5,'大师傅',1,'2026-05-18 15:34:01','2026-05-18 15:34:11','软工2306班','于凤海','10001',1,NULL),(11,29,30,'飞洒',1,'2026-05-18 17:11:05','2026-05-18 17:11:18','计算机2101班','于凤海','10001',0,NULL),(12,29,30,'fdas ',1,'2026-05-20 09:48:40','2026-05-20 09:48:54','计算机2101班','于凤海','10001',1,NULL),(13,29,32,'fda ',1,'2026-05-20 09:49:02','2026-05-20 09:49:08','软件工程2101班','于凤海','10001',0,NULL),(14,29,32,'fad ',1,'2026-05-20 09:49:14','2026-05-20 09:58:02','软件工程2101班','于凤海','10001',1,NULL);
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
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
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
-- Table structure for table `teaching_resources`
--

DROP TABLE IF EXISTS `teaching_resources`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `teaching_resources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
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
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_teaching_resources_course_id` (`course_id`),
  KEY `idx_teaching_resources_uploader_id` (`created_by`),
  CONSTRAINT `fk_teaching_resources_course_id` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `teaching_resources`
--

LOCK TABLES `teaching_resources` WRITE;
/*!40000 ALTER TABLE `teaching_resources` DISABLE KEYS */;
INSERT INTO `teaching_resources` VALUES (1,1,'高等数学-第一至四章课件','高等数学课程配套课件，包含函数极限、导数与微分、不定积分、定积分等章节','pdf','https://example.com/math-slides',NULL,NULL,NULL,320,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(2,1,'高等数学习题集','配套习题集，含答案与解析，共200题覆盖全部章节','pdf','https://example.com/math-exercises',NULL,NULL,NULL,280,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(3,2,'大学英语综合教程','大学英语读写译综合教程，含课文精讲与课后练习','pdf','https://example.com/english-textbook',NULL,NULL,NULL,350,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(4,3,'线性代数-矩阵与行列式','线性代数重点章节精讲，包括矩阵运算、行列式、线性方程组','pdf','https://example.com/linear-algebra',NULL,NULL,NULL,195,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(5,4,'数据结构-算法分析视频','数据结构算法讲解视频系列，包含排序、查找、树、图等核心算法','video','https://example.com/ds-video',NULL,NULL,NULL,410,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(6,5,'Java程序设计-实验指导书','Java课程实验指导书，涵盖面向对象编程、异常处理、集合框架、IO流等实验','pdf','https://example.com/java-lab',NULL,NULL,NULL,365,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(7,5,'Java编程实战项目源码','学生管理系统完整项目源码，适合课程设计参考','zip','https://example.com/java-project',NULL,NULL,NULL,250,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(8,6,'计算机组成原理-实验教程','计组实验教程，包括运算器、存储器、控制器等实验指导','pdf','https://example.com/co-lab',NULL,NULL,NULL,178,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(9,4,'数据结构习题答案','数据结构教材课后习题参考答案，含详细解题过程','pdf','https://example.com/ds-answers',NULL,NULL,NULL,305,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(10,6,'计组期末复习大纲','计算机组成原理期末考试复习提纲，含重点知识梳理与模拟题','doc','https://example.com/co-review',NULL,NULL,NULL,420,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00');
/*!40000 ALTER TABLE `teaching_resources` ENABLE KEYS */;
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
) ENGINE=InnoDB AUTO_INCREMENT=58 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'admin','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','系统管理员','admin@academic.edu','13800000000',4,1,'2025-12-22 17:28:15','2025-12-22 17:28:15',NULL),(16,'adm001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','司明','admin@test.com的发生','13800000003',4,1,'2025-12-22 17:28:15','2026-06-23 07:44:14',NULL),(18,'teacher001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张老师','teacher001@test.com','13800000010',2,1,'2025-12-22 18:18:22','2026-06-23 08:32:12',NULL),(19,'teacher002','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王老师','teacher002@test.com','13800000011',2,1,'2025-12-29 23:09:40','2025-12-29 23:09:40',NULL),(22,'teacher003','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李老师','teacher003@test.com','13800000012',2,1,'2025-12-29 23:10:00','2025-12-29 23:10:00',NULL),(27,'2023020616','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','司明','2318986782@qq.com','15245508389',1,1,'2026-03-04 02:43:20','2026-06-23 07:47:58',NULL),(28,'teacher005','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','发生的官方','23467675@qq.com','16744343245',2,1,'2026-03-04 03:56:54','2026-06-26 03:29:18',NULL),(29,'10001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','于凤海','13397583736@qq.com','18845894565',2,1,'2026-03-04 03:58:09','2026-06-23 07:47:58',NULL),(30,'2023020615','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','邱琳紫','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(31,'2023020617','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','宋新鹏','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-05-28 02:33:34',NULL),(32,'2022020137','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','赵琦','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(33,'2023020619','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','苏红阳','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(34,'2023020620','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','唐浩然','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(35,'2023020621','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王宏民','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(36,'2023020624','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王玉蛟','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(37,'2023020625','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','吴雨航','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(38,'2023020626','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','徐瑞瑶','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(39,'2023020607','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','韩坤','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(40,'2023020629','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张博巍','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(41,'2023020504','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','程启頔','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-05-28 02:33:34',NULL),(42,'2023020505','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','邓雨轩','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-05-28 02:33:34',NULL),(43,'2023020506','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','高菲','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-05-28 02:33:34',NULL),(44,'2023020507','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','胡钰','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(45,'2023020509','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李佳芮','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(46,'2023020510','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李爽','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(47,'2023020512','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','梁莹静','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(48,'2023020515','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','孟阳','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(49,'2023020519','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王瑾泰','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(50,'2023020520','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王平洋','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(51,'2023020521','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','夏志浩','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(52,'2023020524','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张健','2318986782@qq.com','15245508389',1,1,'2026-03-05 03:00:29','2026-06-23 07:47:58',NULL),(53,'1000000001','$2a$10$Zt/haHt8zaNI0J7N7gx2teHL0gOkqKjKq45YooxvdmFBUhe4tGJLq','司明','2318986782@qq.com','15245508389',3,1,'2026-05-14 02:22:05','2026-06-23 07:47:58',NULL),(55,'teacher006','$2a$10$YN5bO.E.tdnGFT7.tPOvk.kMnDr1pq/6.jBSHDmwJ7S4vi2Nowmbi','发送','adm001','24524224545',2,1,'2026-06-23 08:22:47','2026-06-26 03:29:18',NULL),(56,'collegeadmin2','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','1',NULL,NULL,5,1,'2026-06-26 01:46:01','2026-08-03 06:37:01',NULL),(57,'collegeadmin1','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','2',NULL,NULL,5,1,'2026-06-26 01:47:52','2026-08-03 06:37:01',NULL);
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
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预警规则表';
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
-- Dumping events for database 'academic_warning_system'
--

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

-- Dump completed on 2026-09-16 19:24:07
