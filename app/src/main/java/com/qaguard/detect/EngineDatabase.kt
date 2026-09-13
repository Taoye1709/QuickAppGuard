package com.qaguard.detect

import com.qaguard.model.EngineEntry

/**
 * 已知快应用引擎特征库。
 *
 * 置信度说明（verified 字段）：
 *  - true：厂商官方文档或社区反复实测确认过的包名
 *    （vivo 引擎见官方 Wiki qapp-wiki.vivo.com.cn，OPPO 引擎见 OPPO 开放平台文档）
 *  - false：社区流传或按命名规律推测的候选包名，待真机实测后升级
 *
 * 特征库共建方式见 README：`adb shell pm list packages | grep -iE 'hybrid|hap|quick'`
 */
object EngineDatabase {

    val entries: List<EngineEntry> = listOf(
        // —— 小米 MIUI / 澎湃OS ——
        EngineEntry(
            "com.miui.hybrid", "小米", coupled = false, verified = true,
            note = "MIUI/澎湃OS 快应用服务；部分 HyperOS 机型禁止 disable，将自动改用挂起(suspend)",
            officialToggle = "设置 → 搜索“快应用” → 快应用服务 → 隐私设置 → 撤回同意"
        ),
        EngineEntry(
            "com.miui.hybrid.accessory", "小米", coupled = false, verified = true,
            note = "快应用辅助组件"
        ),

        // —— OPPO / 一加 / realme（ColorOS / OplusOS）——
        EngineEntry(
            "com.nearme.instant.platform", "OPPO 系", coupled = false, verified = true,
            note = "OPPO 开放平台文档记载的内置快应用引擎",
            officialToggle = "软件商店 → 快应用 → 我的 → 设置 → 连点版本号5次 → 停止快应用服务"
        ),
        EngineEntry("com.oppo.hybrid", "OPPO 系", coupled = false, verified = false, note = "社区流传候选包名，待实测"),
        EngineEntry("com.oplus.hybrid", "OPPO 系", coupled = false, verified = false, note = "社区流传候选包名，待实测"),
        EngineEntry("com.nearme.hybrid", "OPPO 系", coupled = false, verified = false, note = "旧版候选包名，待实测"),

        // —— vivo / iQOO（OriginOS / Funtouch）——
        EngineEntry(
            "com.vivo.hybrid", "vivo 系", coupled = false, verified = true,
            note = "vivo 官方文档确认的快应用引擎（预置唯一 rpk 执行环境）",
            officialToggle = "快应用中心 → 我的 → 设置 → 连点版本号 → 关闭快应用功能"
        ),
        EngineEntry("com.bbk.hybrid", "vivo 系", coupled = false, verified = false, note = "旧版候选包名，待实测"),

        // —— 其他厂商（候选，依赖启发式兜底）——
        EngineEntry("com.meizu.flyme.hybrid", "魅族", coupled = false, verified = false, note = "Flyme 候选包名，待实测"),
        EngineEntry("com.zte.hybrid", "中兴/努比亚", coupled = false, verified = false, note = "候选包名，待实测"),
        EngineEntry("com.zui.hybrid", "联想", coupled = false, verified = false, note = "ZUI 候选包名，待实测"),

        // —— 耦合型：不包级禁用，只做官方开关引导 ——
        EngineEntry(
            "com.huawei.appmarket", "华为", coupled = true, verified = true,
            note = "快应用中心内置于华为应用市场，禁用会影响正常应用安装与更新",
            officialToggle = "应用市场 → 我的 → 设置 → 快应用管理 → 关闭"
        ),
        EngineEntry(
            "com.hihonor.appmarket", "荣耀", coupled = true, verified = true,
            note = "同华为，快应用中心耦合在荣耀应用市场内",
            officialToggle = "应用市场 → 我的 → 设置 → 快应用管理 → 关闭"
        )
    )

    fun find(packageName: String): EngineEntry? =
        entries.firstOrNull { it.packageName == packageName }
}
