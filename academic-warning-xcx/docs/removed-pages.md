# 已删除的 48 个未声明页面 + 4 个旧 tab-bar

> 本文件在**删除之前**生成并提交，生成时的 HEAD = `53c7f26`。

## ⚠️ 关于"能不能取回"—— 先说结论

**下表的 sha256 只记录了"删掉的是什么"，它不等于备份：这 52 个文件的内容取不回来。**

原因是重设计把源码从根目录搬进 `src/` 的这次迁移**一直没有提交**，
所以 `src/` 下的文件全部是未跟踪的工作区文件。删除未跟踪文件 = git 里没有任何副本。
我最初在本文件里写的 `git show 53c7f26:.../src/pages/admin/logs.vue` 是**错的**，
那条命令必然报 `path does not exist in HEAD` —— 写的时候没核实，事后才验出来。

逐个核对后的实际情况（对照 HEAD 中迁移前的同名旧路径）：

| 情况 | 数量 | 说明 |
|---|---:|---|
| 与旧路径副本**逐字节相同** | 8 | 这 8 个确实还能从 git 取回原件（见下） |
| 旧路径有同名文件，但**内容不同**，且那份**也是**被 `{{` 打穿的坏文件 | 34 | 原件已丢；git 里留着的是一份同样坏的近亲 |
| 旧路径有同名文件，但**内容不同**，那份是**干净可读**的完整 SFC | 10 | 原件已丢；但 git 里那份比被删的更好 |

另外确认过：会话转写（`.jsonl`）里也没有这 52 个文件的正文 —— 它们从未被 Read 过，
按 sha256 比对 373 段疑似 SFC 正文，0 命中。

**实际影响评估**：被删的都是重设计第一版的遗留物 —— 要么是同一个生成脚本产出的坏页
（34 个 `export default {{`），要么已被重写取代、`pages.json` 里没有任何入口能到达。
计划书本身就写着这批 `pages/*/index.vue` "修 `{{ }}` 的成本 > 重写"，全部删除。
所以这里丢的不是资产，是垃圾 —— 但"丢的是垃圾"是我事后判断，不该拿它去掩盖
"本来该留备份却没留成"这件事，故原样记在这里。

### 确实还能取回的 8 个

```
git show 53c7f26:academic-warning-xcx/pages/admin/departments.vue
git show 53c7f26:academic-warning-xcx/pages/admin/logs.vue
git show 53c7f26:academic-warning-xcx/pages/admin/reports.vue
git show 53c7f26:academic-warning-xcx/pages/admin/roles.vue
git show 53c7f26:academic-warning-xcx/pages/admin/settings.vue
git show 53c7f26:academic-warning-xcx/pages/counselor/analytics/index.vue
git show 53c7f26:academic-warning-xcx/pages/counselor/notifications/index.vue
git show 53c7f26:academic-warning-xcx/pages/teacher/messages/index.vue
```

（注意路径是迁移**前**的 `academic-warning-xcx/pages/...`，不是 `src/pages/...`。）
取回后可用下表的 sha256 核对。

## 为什么删

重设计把 `pages.json` 的路径约定统一成扁平命名 `pages/<role>/<name>`，
这 48 个文件用的是旧约定 `pages/<role>/<name>/index.vue`（uni-app 不认目录 index），
且都不在 `pages.json` 里 —— 也就是**没有任何入口能到达它们**。
其中 34 个是同一个生成脚本的产物，脚本块被 `{{` 打穿（`export default {{`），
连 CSS 大括号也一起打穿，模板里还有 `\text class=\"...` 之类的垃圾。
留着它们只会让 `scripts/check-pages.mjs` 永远报「文件存在但未声明 (48)」，
掩盖真正新出现的未声明文件。

4 个旧 tab-bar 组件（`admin-tab-bar` 等）已 grep 确认**只被上述页面引用**，
删页后即成为孤儿，故一并删除；现行实现是唯一的 `app-tab-bar`。

## 清单（共 52 个文件）

| # | 路径 | 字节 | 行数 | sha256（前 16 位） |
|---:|---|---:|---:|---|
| 1 | `src/pages/admin/class-management/index.vue` | 7063 | 159 | `fa6d16b6ebc2295c` |
| 2 | `src/pages/admin/colleges/index.vue` | 7058 | 159 | `f40bb409c6024325` |
| 3 | `src/pages/admin/courses/index.vue` | 7057 | 159 | `31c8d3c41af80f22` |
| 4 | `src/pages/admin/data-export/index.vue` | 5836 | 138 | `c9f37bb133edd7cd` |
| 5 | `src/pages/admin/departments.vue` | 2476 | 53 | `a2c9dbe222bcfee5` |
| 6 | `src/pages/admin/logs.vue` | 3009 | 65 | `9e77d1cde8755e4a` |
| 7 | `src/pages/admin/majors/index.vue` | 7056 | 159 | `f7506afa161b4262` |
| 8 | `src/pages/admin/messages/index.vue` | 7058 | 159 | `0633fe07babfaabe` |
| 9 | `src/pages/admin/permissions/index.vue` | 5804 | 138 | `ce6ef19d34f8aeab` |
| 10 | `src/pages/admin/reports.vue` | 2452 | 49 | `123a9c9bede63e08` |
| 11 | `src/pages/admin/roles.vue` | 3190 | 67 | `bd6a45c4c676ad2e` |
| 12 | `src/pages/admin/rules/index.vue` | 7055 | 159 | `7f2c0a4b2f9b461a` |
| 13 | `src/pages/admin/settings.vue` | 3561 | 87 | `eb640372eaa30511` |
| 14 | `src/pages/admin/statistics/index.vue` | 7060 | 159 | `e418364bfd3edfbc` |
| 15 | `src/pages/admin/warnings/index.vue` | 7055 | 159 | `c54d9865d61a97fb` |
| 16 | `src/pages/college-admin/analysis/index.vue` | 7100 | 159 | `6c59211d2945b899` |
| 17 | `src/pages/college-admin/classes/index.vue` | 7094 | 159 | `5419081b212a6021` |
| 18 | `src/pages/college-admin/counselors/index.vue` | 7115 | 159 | `802e61c693fcaf47` |
| 19 | `src/pages/college-admin/export/index.vue` | 5837 | 138 | `5a2290ce9f54aa54` |
| 20 | `src/pages/college-admin/scores/index.vue` | 7097 | 159 | `b0385909e40e98c5` |
| 21 | `src/pages/college-admin/students/index.vue` | 7114 | 159 | `e53706956e46731b` |
| 22 | `src/pages/college-admin/teachers/index.vue` | 7116 | 159 | `bba46af8e0a3a525` |
| 23 | `src/pages/college-admin/warnings/index.vue` | 7092 | 159 | `0bde1d8bbddb4a80` |
| 24 | `src/pages/counselor/analytics/index.vue` | 6654 | 142 | `a0f28d61938e5ff5` |
| 25 | `src/pages/counselor/class-management/index.vue` | 7077 | 159 | `81475a0441210a43` |
| 26 | `src/pages/counselor/courses/index.vue` | 5820 | 138 | `8ddce8f7c1ed1409` |
| 27 | `src/pages/counselor/credit-monitor/index.vue` | 7083 | 159 | `d1b495f33d4b9aed` |
| 28 | `src/pages/counselor/notifications/index.vue` | 7112 | 146 | `9dc800be8574e255` |
| 29 | `src/pages/counselor/scores/index.vue` | 7081 | 159 | `cea461b520b44b57` |
| 30 | `src/pages/counselor/settings/index.vue` | 5817 | 138 | `037989be461061d2` |
| 31 | `src/pages/index/index.vue` | 745 | 53 | `210d4d4a05849e27` |
| 32 | `src/pages/student/analysis/index.vue` | 4152 | 100 | `614e3ce219597d85` |
| 33 | `src/pages/student/appeals/index.vue` | 5345 | 107 | `2a86b8e9a08bcdf9` |
| 34 | `src/pages/student/assistance-feedback/index.vue` | 7057 | 159 | `22483be301a83508` |
| 35 | `src/pages/student/benchmark-analysis/index.vue` | 7054 | 159 | `a85237ac075f7766` |
| 36 | `src/pages/student/class-members/index.vue` | 5800 | 138 | `9d30e34ea71f6ca5` |
| 37 | `src/pages/student/class-ranking/index.vue` | 7057 | 159 | `4fde1ba6eb38974c` |
| 38 | `src/pages/student/messages/index.vue` | 4762 | 151 | `3c2d78108d9f1b93` |
| 39 | `src/pages/student/notification-center/index.vue` | 7058 | 159 | `cd450705a84e1691` |
| 40 | `src/pages/student/settings/index.vue` | 6896 | 184 | `3d5b619039d5f810` |
| 41 | `src/pages/student/statistics/index.vue` | 7055 | 159 | `fc7dbd8d4050237b` |
| 42 | `src/pages/teacher/analysis/index.vue` | 7713 | 169 | `178d7b45b4f63f03` |
| 43 | `src/pages/teacher/audit-log/index.vue` | 5812 | 138 | `d7982ebd9d185bc2` |
| 44 | `src/pages/teacher/class-management/index.vue` | 7069 | 159 | `cf8c07e5e7599fdd` |
| 45 | `src/pages/teacher/credit-prediction/index.vue` | 7071 | 159 | `320851f071b8f543` |
| 46 | `src/pages/teacher/feedback-management/index.vue` | 7069 | 159 | `8b6358bdf4a98e2b` |
| 47 | `src/pages/teacher/messages/index.vue` | 6594 | 136 | `856389ed025eee64` |
| 48 | `src/pages/teacher/settings/index.vue` | 5807 | 138 | `e6a977e8e838420e` |
| 49 | `src/components/admin-tab-bar/admin-tab-bar.vue` | 1907 | 49 | `7dad589c4510c9fe` |
| 50 | `src/components/collegeadmin-tab-bar/collegeadmin-tab-bar.vue` | 1972 | 49 | `641acaca9453a436` |
| 51 | `src/components/counselor-tab-bar/counselor-tab-bar.vue` | 1948 | 49 | `90941905d60716f4` |
| 52 | `src/components/teacher-tab-bar/teacher-tab-bar.vue` | 2080 | 53 | `b2a59f8185a433a0` |

## 完整 sha256

```
fa6d16b6ebc2295cb962c37e1fb0257aa42d34b005d096ea0399d2eb19343477  src/pages/admin/class-management/index.vue
f40bb409c6024325bc764387789d4b18aaa09c440643f143a27505cacd7ed356  src/pages/admin/colleges/index.vue
31c8d3c41af80f224a8b94178dce7dc3afbb9506661a1b65fea70e83c943fe68  src/pages/admin/courses/index.vue
c9f37bb133edd7cd33b254648a3b8c063777eddb3a157204d5ddd3fca92fdf5c  src/pages/admin/data-export/index.vue
a2c9dbe222bcfee548c01f771689187c5cb8302105dbfb6df110ff52992928b5  src/pages/admin/departments.vue
9e77d1cde8755e4ab70509cfcce5cfe2128892b27ce38886ff7f34bee7224c70  src/pages/admin/logs.vue
f7506afa161b4262021d898f805aafded27bccca3418d874d48f1bb393e34afe  src/pages/admin/majors/index.vue
0633fe07babfaabeb4068558f9464614cfc9e82eadd47e884d7ea63f50c1a749  src/pages/admin/messages/index.vue
ce6ef19d34f8aeabbf67877f2e02288ead7179a894fabc4d720d97937636db9e  src/pages/admin/permissions/index.vue
123a9c9bede63e08b2952ef6b5e577dd3ec9e05d8adbabce4360a3483d3b6798  src/pages/admin/reports.vue
bd6a45c4c676ad2e77e812027a8c44d55c421bac045d537f212a7fd061bce392  src/pages/admin/roles.vue
7f2c0a4b2f9b461aa4970994c8f707b71f78b9ff24775756dd26b97b3d72394c  src/pages/admin/rules/index.vue
eb640372eaa3051146138595dde5b2fc6ba4ff6b0a3ce1d65b35e377dcd0c9af  src/pages/admin/settings.vue
e418364bfd3edfbca7341b41e6f62e1092a76e8900e8544de99022bbe92b4f17  src/pages/admin/statistics/index.vue
c54d9865d61a97fb49ba3e8f8bc762c12514676782157ffce799887e5319da62  src/pages/admin/warnings/index.vue
6c59211d2945b8997aee3d2ccaa1677fb845f74cbc77440c7ec66bef7aa43758  src/pages/college-admin/analysis/index.vue
5419081b212a60212b8db4da766d6401f2b77ad76eeade90835b510e55e1832a  src/pages/college-admin/classes/index.vue
802e61c693fcaf47af61ff3909751e3fd0f1f38f25cd9a9506501f7e0bb05dc9  src/pages/college-admin/counselors/index.vue
5a2290ce9f54aa543d01a4e265cbe5edccc8da40221d5b222c12baf0c1e1ba88  src/pages/college-admin/export/index.vue
b0385909e40e98c59ff563b0def3f3b251a7e931757435cb0bca9df965846cdc  src/pages/college-admin/scores/index.vue
e53706956e46731b780152390419bd185c80746859aa580bfc748ef9df9a6707  src/pages/college-admin/students/index.vue
bba46af8e0a3a5258ea65ce0841a8e6c090abe153eaf3d8516f696a332e56938  src/pages/college-admin/teachers/index.vue
0bde1d8bbddb4a802228eaac15666e6186d3e355e605ac3b4101eb5f387974d4  src/pages/college-admin/warnings/index.vue
a0f28d61938e5ff5d71a181a3e0ac99017e9fa91676a868316c79c2a269fd4e2  src/pages/counselor/analytics/index.vue
81475a0441210a438095e5504a6ef513d69302b624a2f5b65148624045dd2663  src/pages/counselor/class-management/index.vue
8ddce8f7c1ed14092b6a1673a20e8e150c8cbdce7fe144e9b3d94fc21f23e2fd  src/pages/counselor/courses/index.vue
d1b495f33d4b9aed49b15f7ed2bdf58d2eb67fdab4ab9612801fbe00aa4f39a0  src/pages/counselor/credit-monitor/index.vue
9dc800be8574e255363b4c8f974b1f34d019dbfea54dc9419613e928dd1af76d  src/pages/counselor/notifications/index.vue
cea461b520b44b573da5cd90e1fc82220ff2d7c205c803d6a222729f3df36a51  src/pages/counselor/scores/index.vue
037989be461061d296abc8f8390d1c8d714de48ff272012859d696f60fe51f6e  src/pages/counselor/settings/index.vue
210d4d4a05849e271547db2ff5fe747a116549c959eddda73ca54897f6fba84b  src/pages/index/index.vue
614e3ce219597d8510cb1c3355be2a975efe950b2aeff2db923764116587ed43  src/pages/student/analysis/index.vue
2a86b8e9a08bcdf9c243d34ce98b95774d3449dfdd532ad8c467c8cc0d1afe79  src/pages/student/appeals/index.vue
22483be301a835084f8bcfcd07a25803de1dd2e8c35b3184024123988281d5a1  src/pages/student/assistance-feedback/index.vue
a85237ac075f7766ede15648fb88065ff745f7cfcf2825d5f11e2d9025aa327c  src/pages/student/benchmark-analysis/index.vue
9d30e34ea71f6ca5b05b4d68e39b2cde3e752a41aa994973f8320602a5918b4b  src/pages/student/class-members/index.vue
4fde1ba6eb38974c3d658202647a9c001d7caec737ca7ac86647d88479c3116e  src/pages/student/class-ranking/index.vue
3c2d78108d9f1b93d6aa1ae5227d7b9f614acb6eb697930baa8f0598ea5a4132  src/pages/student/messages/index.vue
cd450705a84e1691bd8dfef1741e9d146042f46ba7700b37702146af75862ae9  src/pages/student/notification-center/index.vue
3d5b619039d5f8102ebf94aebe55494cfa9eaed602a39c3aea28ddb5de845fba  src/pages/student/settings/index.vue
fc7dbd8d4050237b747eba9cb176cec3e69fb3949fb82ffe4f35ee0f241f44af  src/pages/student/statistics/index.vue
178d7b45b4f63f03bab5c452baf08a9a29ce5c8748a82f3f8397734c6679be08  src/pages/teacher/analysis/index.vue
d7982ebd9d185bc26129408ecc903cab6b18b6a20086268b3a373ad7307967c3  src/pages/teacher/audit-log/index.vue
cf8c07e5e7599fdddade5b2da7dd422a9739c35af3aae7ba484ace57aefdaecc  src/pages/teacher/class-management/index.vue
320851f071b8f54347fdbabfd777915a86d875000df79d4db4e0948006b18608  src/pages/teacher/credit-prediction/index.vue
8b6358bdf4a98e2bb2d7027c07c55ff07004e430d2d35082c01f5a65f2d96b45  src/pages/teacher/feedback-management/index.vue
856389ed025eee64c4b73fbd14f5c922415e31d30d2f5782b4ec554b9c8c30d6  src/pages/teacher/messages/index.vue
e6a977e8e838420e9db8f77e6905f8b98b673f9ff9d505af9efea06effd8ea94  src/pages/teacher/settings/index.vue
7dad589c4510c9fed5f21e65ab5ea3fb397fc82f08b02a72904e93e019e7c497  src/components/admin-tab-bar/admin-tab-bar.vue
641acaca9453a436809761e4f484ba462b667ca89d28b834e72c26a970f97464  src/components/collegeadmin-tab-bar/collegeadmin-tab-bar.vue
90941905d60716f47101b13484ca04cd45fa46106e2106c190d88d64cb522698  src/components/counselor-tab-bar/counselor-tab-bar.vue
b2a59f8185a433a0ab46a6500b1c640a16597fb2c9eaa887118cb9b25e6268b8  src/components/teacher-tab-bar/teacher-tab-bar.vue
```
