package com.qaguard.admin

import android.app.admin.DeviceAdminReceiver

/** Device Owner 激活入口组件；策略仅声明 force-lock，不做擦除等高危操作。 */
class GuardAdminReceiver : DeviceAdminReceiver()
