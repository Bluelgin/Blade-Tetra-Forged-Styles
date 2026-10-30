# 技术调研与许可边界

以下为源码调查，不表示本项目引入其依赖或复制代码/资源。

- [SlashBlade Resharped](https://github.com/0999312/SlashBlade_Resharped)（MIT）：`SlashBladeTEISR`、`LayerMainBlade`、`BladeFirstPersonRender`、`RenderOverrideEvent`、`BladeRenderState`、`BladeModelManager`。已核对本项目使用的1.20.1 API；使用其原有可取消事件、OBJ/RenderType缓存和渲染函数，保留Tetra材质与刀鞘关系。直接调用已声明依赖，未拷贝其模型/音效。
- [SkydeitySlash](https://github.com/SakuraSkydeity/SlashBlade-Skydeity)：调查`client/objopt/BladeRenderResourceCache.java`和`RenderSwordHologram.java`。参考资源解析缓存、稳定RenderType和空间几何分层的思路。调查时仓库API未提供可确认的开源许可，因此不复制实现或资源；其通用剑Billboard不适合本任务的原刀要求，未采用。
- [Iron's Spells 1.20.1-legacy](https://github.com/iron431/irons-spells-n-spellbooks/tree/1.20.1-legacy)：调查`entity/spells/lightning_lance/LightningLanceRenderer.java`及`LICENSE.md`。它用多向能量平面和动画纹理降低立体雷光成本；本任务改用自有分段折线，在已有图集中合批。该分支许可ALL RIGHTS RESERVED，明确禁止资产再用；没有搬运任何代码、贴图或音效。
- [L_Ender's Cataclysm 1.20.1](https://github.com/lender544/new1.20.1)：调查`client/render/entity/Boltstrike_Renderer.java`及`LICENSE.txt`。参考雷击蓄积/强度包络、短寿命几何和按对象组织渲染状态的概念。仓库标注CC BY-NC-ND 4.0，没有移植其LightningRender、LightningBoltData或任何资源。本实现无需引入Cataclysm/LionfishAPI。

新能量纹SVG、运动曲线和碎刃/闪电几何为本仓库原创，沿用项目自身许可。Minecraft原生SoundEvents仅运行时引用；未提取或打包原版音频。#40原有资源保留原归属与许可。
