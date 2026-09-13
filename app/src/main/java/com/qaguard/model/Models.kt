package com.qaguard.model

/**
 * 快应用引擎特征条目。
 *
 * @param packageName 引擎包名
 * @param vendor      所属厂商/系统
 * @param coupled     与其他系统应用耦合（如内置于应用市场），包级禁用会带来副作用，
 *                    只引导用户走官方关闭开关
 * @param verified    特征库置信度：true = 已被多方实测确认；false = 待机型实测的推测包名
 */
data class EngineEntry(
    val packageName: String,
    val vendor: String,
    val coupled: Boolean,
    val verified: Boolean,
    val note: String = "",
    /** 厂商藏在系统里的官方关闭入口，作为零权限兜底路径 */
    val officialToggle: String = ""
)

/** 一次扫描中在本机发现的一个快应用框架实例。 */
data class DetectedEngine(
    /** 特征库条目；为 null 表示由 hap:// scheme 启发式发现、特征库尚未收录 */
    val entry: EngineEntry?,
    val packageName: String,
    val byHeuristic: Boolean
)
