# Reminders are inexact work, not exact alarms

The daily reminder is a WorkManager job scheduled one day at a time, not an `AlarmManager` alarm. It is allowed to arrive late — Doze can hold it until the device is next awake, and the app makes no attempt to stop that. The Settings copy says "arrives around this time" because that is what is on offer.

Android has a stricter and more accurate tool available. `setExactAndAllowWhileIdle` fires to the minute through Doze, and on Android 13 and above an app can hold `USE_EXACT_ALARM` and get it without asking anyone. We are not taking it. `USE_EXACT_ALARM` is a Play Store policy-reviewed permission reserved for alarm clocks and calendar events — things the user has scheduled and would be harmed by missing — and a study nudge is not one of those. `SCHEDULE_EXACT_ALARM`, the version the user can grant, costs a second permission prompt in a flow that already has one. Either way, an app that insists on waking the device at exactly eight for a reminder that says "two minutes?" has misjudged its own importance.

WorkManager also already does the two things that would otherwise be work: it re-registers pending work after a reboot, so there is no `BOOT_COMPLETED` receiver to write and keep correct, and it holds one job under one unique name, so changing the reminder time replaces rather than accumulates.

## Considered Options

- **`AlarmManager` with an exact alarm and a `BOOT_COMPLETED` receiver.** Minute-accurate, and the only option if the reminder were a commitment the user made. Rejected: the accuracy is not worth a policy-reviewed permission or a second prompt, and the reboot receiver is code we would own for ever.
- **A `PeriodicWorkRequest` every 24 hours.** Less code — WorkManager repeats it without the worker being involved. Rejected because a fixed 24-hour interval is not a time of day: it drifts an hour twice a year when the clocks change, and the reminder would slowly walk away from the hour the user picked.
- **One-time work that schedules the next one.** What we do. Each run recomputes the delay from the wall clock, so deferral does not accumulate and daylight saving is handled by asking what time it is rather than by adding a day.

## Consequences

- Delivery is approximate. On a device in deep Doze the reminder can be an hour or more late, and the app neither knows nor reports this.
- The reminder chain is only as alive as its last run. A run that neither posts nor reschedules — reminders switched off — ends it, which is intended; anything that ends it unintentionally is repaired on the next app launch, because `ReminderCoordinator` re-enqueues from the stored setting every time the process starts.
- Each run appends the next one to the same chain rather than replacing it, because a run cannot replace itself without asking WorkManager to cancel it mid-notify. The chain therefore grows by one completed node a day on a device the app is never opened on; opening the app collapses it, since the coordinator re-enqueues with `REPLACE`. A few hundred rows in WorkManager's own database is the worst case, and it is not one worth more machinery.
- WorkManager is initialised by the app rather than by its own startup provider, because the worker is Hilt-injected. That is wiring in `FlashcardsApplication` and a `tools:node="remove"` in the manifest, and it has to stay in step if a second worker is ever added.
- Nothing here is allowed to carry a number. See ADR 0001: there is no backlog, so the reminder has nothing to count, and the notification copy is asserted against that.
