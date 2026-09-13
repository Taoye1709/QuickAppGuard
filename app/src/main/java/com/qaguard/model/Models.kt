package com.qaguard.model

/** 特征条目类别：快应用引擎 / 厂商广告投放组件。两类都走同一处置管线。 */
enum class EngineCategory { QUICK_APP, VENDOR_AD }

/**
 * 特征条目。
 *
 * @param packageName 引擎/组件包名
 * @param vendor      所属厂商/系统
 * @param coupled     与其他系统应用耦合（如内置于应用市场），包级禁用会带来副作用，
 *                    只引导用户走官方关闭开关
 * @param verified    特征库置信度：true = 已被多方实测确认；false = 待机型实测的推测包名
 * @param category    QUICK_APP（默认）或 VENDOR_AD；VENDOR_AD 条目只在用户显式打开
 *                    「广告组件拦截」开关后才进入自动处置管线
 */
data class EngineEntry(
    val packageName: String,
    val vendor: String,
    val coupled: Boolean,
    val verified: Boolean,
    val note: String = "",
    val officialToggle: String = "",
    val category: EngineCategory = EngineCategory.QUICK_APP
)

/** 一次扫描中在本机发现的一个可处置对象。 */
data class DetectedEngine(
    /** 特征库条目；为 null 表示由 hap:// scheme 启发式发现、特征库尚未收录 */
    val entry: EngineEntry?,
    val packageName: String,
    val byHeuristic: Boolean
)
