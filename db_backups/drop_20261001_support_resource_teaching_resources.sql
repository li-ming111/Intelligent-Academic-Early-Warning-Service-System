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
INSERT INTO `support_resource` VALUES (1,'高等数学学习指南','高等数学核心知识点总结与习题讲解，帮助挂科学生系统复习','document','数学','高等数学包含极限、导数、积分、微分方程等核心内容...','https://example.com/math-guide',NULL,NULL,NULL,'于凤海',150,45,1,'2026-05-27 02:00:00','2026-05-27 02:00:00'),(2,'计算机网络学习指南','针对计算机网络挂科学生的补习资料，涵盖 OSI 模型、TCP/IP 协议栈、子网划分与路由配置','video','计算机','OSI 七层模型、TCP/IP 协议栈、IP 地址与子网划分、常用网络命令与故障排查...','https://example.com/network-guide',NULL,NULL,NULL,'于凤海',200,60,1,'2026-05-27 02:00:00','2026-09-16 14:23:16'),(3,'学习时间管理方法','帮助学业困难学生改善学习习惯，提高学习效率','article','学习方法','番茄工作法、费曼学习法、康奈尔笔记法等高效学习方法介绍',NULL,NULL,NULL,NULL,'司明',80,30,1,'2026-05-27 02:00:00','2026-09-16 12:52:15'),(4,'计算机基础精讲','计算机基础重点难点解析，含计算机组成、进制转换与操作系统基础','document','计算机','计算机组成与工作原理、数据表示与进制转换、操作系统基础、计算机网络基础...','https://example.com/computer-basics',NULL,NULL,NULL,'于凤海',120,38,1,'2026-05-27 02:00:00','2026-09-16 14:23:16');
/*!40000 ALTER TABLE `support_resource` ENABLE KEYS */;
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
INSERT INTO `teaching_resources` VALUES (1,1,'高等数学-第一至四章课件','高等数学课程配套课件，包含函数极限、导数与微分、不定积分、定积分等章节','pdf','https://example.com/math-slides',NULL,NULL,NULL,320,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(2,1,'高等数学习题集','配套习题集，含答案与解析，共200题覆盖全部章节','pdf','https://example.com/math-exercises',NULL,NULL,NULL,280,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(3,2,'大学英语综合教程','大学英语读写译综合教程，含课文精讲与课后练习','pdf','https://example.com/english-textbook',NULL,NULL,NULL,350,1,'王老师','2026-05-27 02:00:00',NULL,'2026-09-16 14:23:16'),(4,3,'计算机基础-核心知识点梳理','计算机基础课程配套课件，涵盖计算机组成、数据表示与进制转换、操作系统与网络基础','pdf','https://example.com/computer-basics',NULL,NULL,NULL,195,1,'李老师','2026-05-27 02:00:00',NULL,'2026-09-16 14:23:16'),(5,4,'数据结构-算法分析视频','数据结构算法讲解视频系列，包含排序、查找、树、图等核心算法','video','https://example.com/ds-video',NULL,NULL,NULL,410,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(6,5,'计算机网络-实验指导书','计算机网络课程实验指导书，涵盖组网配置、抓包分析、子网划分与路由实验','pdf','https://example.com/network-lab',NULL,NULL,NULL,365,1,'李老师','2026-05-27 02:00:00',NULL,'2026-09-16 14:23:16'),(7,5,'网络编程实战项目源码','基于 Socket 的网络通信程序完整源码，适合课程设计参考','zip','https://example.com/network-project',NULL,NULL,NULL,250,1,'李老师','2026-05-27 02:00:00',NULL,'2026-09-16 14:23:16'),(8,6,'操作系统-实验教程','操作系统实验教程，包括进程管理、内存管理、文件系统与并发控制实验','pdf','https://example.com/os-lab',NULL,NULL,NULL,178,1,'刘教授','2026-05-27 02:00:00',NULL,'2026-09-16 14:23:16'),(9,4,'数据结构习题答案','数据结构教材课后习题参考答案，含详细解题过程','pdf','https://example.com/ds-answers',NULL,NULL,NULL,305,1,'于凤海','2026-05-27 02:00:00',NULL,'2026-05-27 02:00:00'),(10,6,'操作系统期末复习大纲','操作系统期末考试复习提纲，含重点知识梳理与模拟题','doc','https://example.com/os-review',NULL,NULL,NULL,420,1,'刘教授','2026-05-27 02:00:00',NULL,'2026-09-16 14:23:16');
/*!40000 ALTER TABLE `teaching_resources` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-01 18:07:01
