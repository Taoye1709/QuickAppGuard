# Shizuku 用户服务通过反射跨进程创建，不能混淆
-keep class com.qaguard.shizuku.ShellService { *; }
-keep class com.qaguard.IShellService { *; }
-keep class com.qaguard.IShellService$* { *; }
