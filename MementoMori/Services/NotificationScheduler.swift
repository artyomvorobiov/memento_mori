import Foundation
import UserNotifications

/// Локальные уведомления. Расписание строит ReminderPlanner (Shared/Core),
/// здесь — только работа с UNUserNotificationCenter.
final class NotificationScheduler {
    private var center: UNUserNotificationCenter { UNUserNotificationCenter.current() }

    func requestAuthorization() async -> Bool {
        do {
            return try await center.requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        await center.notificationSettings().authorizationStatus
    }

    func reschedule(with data: AppData) async {
        center.removeAllPendingNotificationRequests()
        guard data.reminders.isEnabled, data.profile != nil else { return }
        let status = await authorizationStatus()
        guard status == .authorized || status == .provisional || status == .ephemeral else { return }

        let calendar = Calendar.memento
        for item in ReminderPlanner.plan(data: data, now: Date(), calendar: calendar) {
            let content = UNMutableNotificationContent()
            content.title = item.title
            content.body = item.body
            content.sound = .default
            let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: item.date)
            let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
            let request = UNNotificationRequest(identifier: item.id, content: content, trigger: trigger)
            try? await center.add(request)
        }
    }

    /// Пробное уведомление через 5 секунд.
    func sendTest(data: AppData) async {
        let message: ReminderMessage
        if let profile = data.profile {
            message = ReminderComposer.daily(date: Date(), profile: profile, goals: data.goals)
        } else {
            message = ReminderComposer.quote(for: Date())
        }
        let content = UNMutableNotificationContent()
        content.title = message.title
        content.body = message.body
        content.sound = .default
        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 5, repeats: false)
        let request = UNNotificationRequest(identifier: "memento.test", content: content, trigger: trigger)
        try? await center.add(request)
    }
}
