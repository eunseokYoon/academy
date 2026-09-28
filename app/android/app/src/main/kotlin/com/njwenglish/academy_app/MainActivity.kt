package com.njwenglish.academy_app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
    }

    /**
     * 서버 알림이 들어갈 채널. 매니페스트의 default_notification_channel_id 와 id 가 같아야 한다.
     * 채널은 하나뿐이다 — 종류별 알림 토글을 만들지 않기로 했다(끄기는 앱의 「알림 받기」 하나).
     * 이미 있으면 다시 만들어도 사용자가 바꾼 설정은 그대로다.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            "academy_default",
            "학원 알림",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "공지·숙제·성적·출결 알림"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
