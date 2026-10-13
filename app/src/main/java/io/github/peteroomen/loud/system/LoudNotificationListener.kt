package io.github.peteroomen.loud.system

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Never retain notification text, titles or PendingIntents. */
object NotificationStateSource {
    private val mutableState = MutableStateFlow(NotificationState())
    val state: StateFlow<NotificationState> = mutableState.asStateFlow()
    internal fun publish(value: NotificationState) { mutableState.value = value }
    fun clear() { mutableState.value = NotificationState(); MediaState.detach() }
}
class LoudNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { publish(); MediaState.attach(this) }
    override fun onListenerDisconnected() = NotificationStateSource.clear()
    override fun onDestroy() { NotificationStateSource.clear(); super.onDestroy() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = publish()
    private fun publish() {
        val active = runCatching { activeNotifications.toList() }.getOrElse { NotificationStateSource.clear(); return }
        val ranking = runCatching { currentRanking }.getOrNull()
        val counts = notificationCounts(active.map { sbn ->
            val r = Ranking()
            NotificationCandidate(sbn.packageName, sbn.groupKey, sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0, sbn.isClearable,
                ranking == null || !ranking.getRanking(sbn.key, r) || r.canShowBadge())
        })
        NotificationStateSource.publish(NotificationState(true, counts))
        MediaState.setNotificationArt(active.filter { it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) }.mapNotNull { sbn -> artOf(sbn.notification)?.let { sbn.packageName to it } }.toMap())
    }
    private fun artOf(notification: Notification): Bitmap? = runCatching { notification.getLargeIcon()?.loadDrawable(this)?.let { (it as? BitmapDrawable)?.bitmap ?: it.toBitmap(256, 256) } }.getOrNull()
}
