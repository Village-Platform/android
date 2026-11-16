# R8 rules for release builds.
#
# Retrofit, OkHttp, kotlinx.serialization, Coil, Firebase and AndroidX ship their own consumer
# rules, which cover the @Serializable DTOs, navigation routes and the Retrofit service interface.
# Add a rule here only when a release build fails at runtime and the cause is confirmed.

# Retrofit reads each API method's generic return type at runtime (ItemResponse<FirmDto>) to pick
# a kotlinx serializer. R8 full mode drops a class's generic signature, or the class itself, when
# nothing references it directly — Retrofit then sees ItemResponse<Object> and the app crashes on
# launch ("Serializer for class 'Any' is not found"). Keep our @Serializable classes (envelopes,
# DTOs, routes) and their signatures; names are still obfuscated.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep,allowobfuscation @kotlinx.serialization.Serializable class com.aghourservices.** { <fields>; }

# Readable Crashlytics stack traces: keep line numbers, hide the original source file name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
