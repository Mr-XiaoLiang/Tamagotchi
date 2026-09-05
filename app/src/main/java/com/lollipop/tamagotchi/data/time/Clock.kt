package com.lollipop.tamagotchi.data.time

/**
 * 时间源抽象（doc/08 §3 / Task M5.S1）：结算「现在」统一经由此处取。
 * domain 纯函数（settle/FSM）显式收 now 参数不依赖本接口；本接口服务于
 * 启动链路等「需要取真实时刻」的接线层，并允许 debug 注入假时钟做时间旅行。
 */
interface Clock {
    /** 当前墙钟 epoch millis。 */
    fun nowMillis(): Long
}

/** 真实时钟：`System.currentTimeMillis()`。 */
class SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
