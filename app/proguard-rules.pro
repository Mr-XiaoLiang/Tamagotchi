# ============================================================
# Tamagotchi · release 混淆 / 资源压缩规则
# ============================================================
# 说明：本项目存档使用 org.json 手写编解码（JSON 键为字面量，
# 非 Gson/Moshi/kotlinx.serialization 反射式序列化），数据类
# 字段名混淆不影响存档；Activity 由 manifest 经 aapt 规则保留。
# 因此无需为业务 model 补 keep 规则。下方仅放通用加固项。

# 保留 Kotlin 元数据与注解属性，避免 R8 误伤 Compose / 协程
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlin.Metadata { *; }

# Parcelable 的 CREATOR 由系统经反射构造，兜底保留（默认规则已含）
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 若后续引入反射式序列化 / 动态资源加载，请在此补充对应 keep 规则。
