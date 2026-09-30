
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
) ENGINE=InnoDB AUTO_INCREMENT=76 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'admin','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','系统管理员','admin@academic.edu','13800000000',4,1,'2025-12-22 17:28:15','2025-12-22 17:28:15',NULL),(16,'adm001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','夏志浩','admin@test.com','13800000003',4,1,'2025-12-22 17:28:15','2026-09-16 14:23:16',NULL),(18,'teacher001','$2a$10$1KndpdRVs6Bl/VWJtJaa7OA1JH25YEHR7PyGpk57rMZWjUTHWteGq','刘教授','teacher001@test.com','13800000010',2,1,'2025-12-22 18:18:22','2026-09-16 14:23:16',NULL),(19,'teacher002','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王老师','teacher002@test.com','13800000011',2,1,'2025-12-29 23:09:40','2025-12-29 23:09:40',NULL),(22,'teacher003','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李老师','teacher003@test.com','13800000012',2,1,'2025-12-29 23:10:00','2025-12-29 23:10:00',NULL),(27,'2023020616','$2a$10$xVUHdBmYoIsFINpc8qpFn.RVt.Vj2GWL77wkJMhO5ZudIX2tCHnue','司明','2023020616@stu.academic.edu','15200000001',1,1,'2026-03-04 02:43:20','2026-09-16 14:23:16',NULL),(28,'teacher005','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','赵振江','23467675@qq.com','16744343245',2,1,'2026-03-04 03:56:54','2026-09-16 14:23:16',NULL),(29,'10001','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','于凤海','13397583736@qq.com','18845894565',2,1,'2026-03-04 03:58:09','2026-06-23 07:47:58',NULL),(30,'2023020615','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','邱琳紫','2023020615@stu.academic.edu','15200000002',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(31,'2023020617','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','宋新鹏','2023020617@stu.academic.edu','15200000003',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(32,'2022020137','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','赵琦','2022020137@stu.academic.edu','15200000004',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(33,'2023020619','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','苏红阳','2023020619@stu.academic.edu','15200000005',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(34,'2023020620','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','唐浩然','2023020620@stu.academic.edu','15200000006',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(35,'2023020621','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王宏民','2023020621@stu.academic.edu','15200000007',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(36,'2023020624','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王玉蛟','2023020624@stu.academic.edu','15200000008',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(37,'2023020625','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','吴雨航','2023020625@stu.academic.edu','15200000009',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(38,'2023020626','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','徐瑞瑶','2023020626@stu.academic.edu','15200000010',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(39,'2023020607','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','韩坤','2023020607@stu.academic.edu','15200000011',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(40,'2023020629','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张博巍','2023020629@stu.academic.edu','15200000012',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(41,'2023020504','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','程启頔','2023020504@stu.academic.edu','15200000013',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(42,'2023020505','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','邓雨轩','2023020505@stu.academic.edu','15200000014',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(43,'2023020506','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','高菲','2023020506@stu.academic.edu','15200000015',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(44,'2023020507','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','胡钰','2023020507@stu.academic.edu','15200000016',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(45,'2023020509','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李佳芮','2023020509@stu.academic.edu','15200000017',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(46,'2023020510','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李爽','2023020510@stu.academic.edu','15200000018',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(47,'2023020512','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','梁莹静','2023020512@stu.academic.edu','15200000019',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(48,'2023020515','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','孟阳','2023020515@stu.academic.edu','15200000020',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(49,'2023020519','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王瑾泰','2023020519@stu.academic.edu','15200000021',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(50,'2023020520','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','王平洋','2023020520@stu.academic.edu','15200000022',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(51,'2023020521','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','夏志浩','2023020521@stu.academic.edu','15200000023',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(52,'2023020524','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张健','2023020524@stu.academic.edu','15200000024',1,1,'2026-03-05 03:00:29','2026-09-16 14:23:16',NULL),(53,'1000000001','$2a$10$VweR8hEuOzcQixprDhxgN.jxWnAYq0Yiy9A9gOnHdSWAsy.hr0xHO','司明','counselor001@test.com','13800000014',3,1,'2026-05-14 02:22:05','2026-09-21 08:34:21',NULL),(55,'teacher006','$2a$10$YN5bO.E.tdnGFT7.tPOvk.kMnDr1pq/6.jBSHDmwJ7S4vi2Nowmbi','陈志强','teacher006@test.com','13800000013',2,1,'2026-06-23 08:22:47','2026-09-16 14:23:16',NULL),(56,'collegeadmin2','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','李明远','limy@academic.edu','13900000002',5,1,'2026-06-26 01:46:01','2026-09-16 14:23:16',NULL),(57,'collegeadmin1','$2a$10$O1AmnqNe50/knConTInm6Owvk/BkiYTYAviRZk5mz0uQfpT99Oija','张建国','zhangjg@academic.edu','13900000001',5,1,'2026-06-26 01:47:52','2026-09-16 14:23:16',NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `teacher_profile` WRITE;
/*!40000 ALTER TABLE `teacher_profile` DISABLE KEYS */;
INSERT INTO `teacher_profile` VALUES (1,29,'于凤海',1,NULL,NULL,'2026-05-21 09:27:04','2026-05-21 09:27:04'),(2,19,'王老师',1,'副教授','计算机科学','2025-12-29 23:09:40','2025-12-29 23:09:40'),(3,22,'李老师',1,'讲师','数据科学','2025-12-29 23:10:00','2025-12-29 23:10:00'),(5,28,'赵振江',1,'讲师','软件工程','2026-03-04 03:56:54','2026-06-26 03:29:09'),(7,55,'陈志强',2,NULL,NULL,'2026-06-23 08:22:47','2026-06-26 03:29:18'),(8,18,'刘教授',1,'教授','软件工程','2026-06-27 00:31:06','2026-06-27 00:31:06');
/*!40000 ALTER TABLE `teacher_profile` ENABLE KEYS */;
UNLOCK TABLES;
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

LOCK TABLES `messages` WRITE;
/*!40000 ALTER TABLE `messages` DISABLE KEYS */;
/*!40000 ALTER TABLE `messages` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

