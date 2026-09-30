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
  `progress` tinyint NOT NULL DEFAULT '0' COMMENT '完成进度百分比 0-100',
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
INSERT INTO `assistance_plans` VALUES (1,53,13,'针对韩坤同学的学业帮扶计划：1.安排学习小组辅导数据结构；2.每周一次答疑；3.定期检查学习进度',1,'2026-05-27 02:00:00','2026-09-16 11:51:21',50),(2,53,20,'针对李爽同学的学业帮扶计划：1.重点补习计算机网络、计算机基础和高等数学基础；2.安排学伴互助；3.每两周向辅导员汇报进度；4.已安排补考',1,'2026-05-27 02:00:00','2026-09-16 12:43:52',50),(3,53,26,'针对张健同学的学业帮扶计划：1.重点补习数学和编程基础；2.每周安排两次额外辅导；3.建立学习档案跟踪进度',0,'2026-05-27 02:00:00','2026-05-27 02:00:00',0),(1001,53,3,'针对司明同学的学业帮扶计划：1.重点补习操作系统、计算机基础和高等数学三门挂科课程；2.安排学习小组每周两次辅导答疑；3.每两周检查一次学习进度并反馈辅导员；4.目标：本学期末补考全部通过。',1,'2026-09-02 06:36:09','2026-09-16 12:43:52',50);
/*!40000 ALTER TABLE `assistance_plans` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-09-29 17:24:10
