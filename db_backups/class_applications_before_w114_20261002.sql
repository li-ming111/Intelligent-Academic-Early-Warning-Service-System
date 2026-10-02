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
INSERT INTO `teacher_class_application` VALUES (1,29,5,'申请信息填写有误，撤销后重新提交',3,'2026-05-18 12:25:45','2026-09-16 11:23:16','软工2306班','于凤海','10001',0,NULL),(2,29,5,'申请管理软工2306班',2,'2026-05-18 13:56:53','2026-09-16 11:23:16','软工2306班','于凤海','10001',0,'该班本学期已安排班主任，暂不受理'),(9,29,5,'本学期教学任务调整，再次申请管理软工2306班',1,'2026-05-18 15:05:04','2026-09-16 11:23:16','软工2306班','于凤海','10001',0,NULL),(10,29,5,'因教学与科研任务繁重，申请解除软工2306班的管理工作',1,'2026-05-18 15:34:01','2026-09-16 11:23:16','软工2306班','于凤海','10001',1,NULL),(11,29,30,'申请管理计算机2101班',1,'2026-05-18 17:11:05','2026-09-16 11:23:16','计算机2101班','于凤海','10001',0,NULL),(13,29,32,'申请管理软件工程2101班',2,'2026-05-20 09:49:02','2026-09-16 11:23:16','软件工程2101班','于凤海','10001',0,'该班已由刘教授担任班主任，暂不调整');
/*!40000 ALTER TABLE `teacher_class_application` ENABLE KEYS */;
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
INSERT INTO `counselor_class_application` VALUES (1,53,30,'希望负责计算机2101班的日常管理工作',1,'2026-05-18 12:25:12','2026-09-16 11:23:16','计算机2101班','司明',0),(2,53,31,'希望同时管理计算机2102班',1,'2026-05-18 12:25:12','2026-09-16 11:23:16','计算机2102班','司明',0),(5,53,1,'申请负责计科2306班的日常管理与学业帮扶工作',1,'2026-06-08 21:47:38','2026-09-16 11:23:16','计科2306班','司明',0);
/*!40000 ALTER TABLE `counselor_class_application` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-02 14:42:36
