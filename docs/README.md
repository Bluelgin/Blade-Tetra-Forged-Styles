# 文档导航

当前源码版本：**1.6.5**。这里区分现行功能说明、开发交付记录和历史资料；源码版本不表示已经创建正式 Release。

[返回项目首页](../README.md)

## 按用途浏览

- [功能与使用说明](guides/README.md)
- [开发与维护](development/README.md)
- [设计与后续计划](plans/README.md)
- [历史与交付记录](history/README.md)
- [发布页面草稿](publishing/README.md)
- [历次版本更新日志](releases/README.md)
- [千门剑谱美术源图与导出](custom-style-art/README.md)

## 玩家与整合包作者

- [战斗与连招指南](guides/COMBAT_GUIDE.zh-CN.md)
- [千门剑谱：操作、三档刀身与导入导出](guides/CUSTOM_STYLE_FINALIZATION.md#玩家操作)
- [离锋：研习、触发条件与剑气画质](guides/LIFENG_SWORD_AURA.md)
- [配置总览](../CONFIGURATION.md) / [战斗平衡倍率](guides/COMBAT_BALANCE_CONFIG.md)
- [御影当前开放策略：普通挑战暂时封印](guides/MIKAGE_CHALLENGE_SUSPENSION.md)
- [名刀映锻](guides/NAMED_BLADE_IMPRINTING.md) / [附属兼容](guides/ADDON_COMPATIBILITY.md)
- [镶嵌、裹柄与涂层](guides/SWORD_ATTACHMENTS.md) / [解除死念](guides/DEAD_THOUGHT_REMOVAL.md)
- [隐藏传承 Wiki（含折叠剧透）](legacy-wiki/README.md)

## 开发与美术维护

- [架构与扩展约束](development/ARCHITECTURE_GUARDRAILS.md)
- [千门剑谱录制与存储约定](development/CUSTOM_STYLE_IMPLEMENTATION.md)
- [现行离线分享码协议](development/CUSTOM_STYLE_COMMUNITY_PROTOCOL.md)
- [美术源图、导出和校验](custom-style-art/README.md)
- [客户端美术交付记录与待接接口](custom-style-art/CLIENT_INTEGRATION.md)
- [材料提供方效果兼容](development/PROVIDER_EFFECT_COMPATIBILITY.md)
- [御影响应式战斗设计记录](development/MIKAGE_REACTIVE_ENCOUNTER.md)
- [第三方资源与许可](../THIRD_PARTY_NOTICES.md)

仓库目录：`src/main` 是运行时代码和资源，`src/test` 是测试；`docs/custom-style-art/sources` 保存千门剑谱源图，`art`、`art-previews` 与 `tools` 保存其他已跟踪的美术源文件、预览和工具。这些美术文件各有用途，不按“未被 Java 引用”判断为可删文件。

本地 `build`、`.gradle`、`run`、`logs` 已由 `.gitignore` 排除。不要提交测试存档、配置、构建产物或本机启动指针。JAR 中的资源来自 `src/main/resources`；文档归档不改变资源路径。

## 历史与发布资料

- [原首页历史：Alpha 至 1.5.2、旧目标与边界](history/README_HISTORY.md)
- [CurseForge 中文说明草稿](publishing/CURSEFORGE-ZH.md) / [English listing draft](publishing/CURSEFORGE.md)

历次更新日志统一收纳在 [版本日志目录](releases/README.md)。它们描述对应版本，不应覆盖现行功能说明；功能 PR 的交付文档也可能保留当时的验证环境与基线。
