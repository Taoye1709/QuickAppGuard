#!/usr/bin/env bash
# 应急方案：不装 App，直接用 adb 一次性停用常见快应用引擎。
# 用途：给父母手机做临时处理，或在本 App 激活 Device Owner 前先行止血。
# 恢复：adb shell pm enable <包名> 或 adb shell pm unsuspend <包名>
set -euo pipefail

PKGS=(
  com.miui.hybrid
  com.miui.hybrid.accessory
  com.nearme.instant.platform
  com.oppo.hybrid
  com.oplus.hybrid
  com.nearme.hybrid
  com.vivo.hybrid
  com.bbk.hybrid
  com.meizu.flyme.hybrid
  com.zte.hybrid
  com.zui.hybrid
)

echo "== 净屏守护 · adb 应急停用 =="
adb wait-for-device
echo "已连接: $(adb shell getprop ro.product.brand) $(adb shell getprop ro.product.model)"

for pkg in "${PKGS[@]}"; do
  if adb shell pm path "$pkg" >/dev/null 2>&1; then
    echo "-- 发现 $pkg，停用..."
    # 部分机型（如 HyperOS）禁止 disable，退级为 suspend（参考 FxxkMIUIAd 实测）
    if ! adb shell pm disable-user --user 0 "$pkg"; then
      if ! adb shell pm suspend --user 0 "$pkg"; then
        echo "!! disable 与 suspend 均失败（可能为关键组件），可忽略"
      fi
    fi
  fi
done

echo "== 完成。本机实际存在的快应用引擎可用以下命令排查 =="
echo "adb shell pm list packages | grep -iE 'hybrid|hap|quick|instant'"
