// Birthday & calendar-event reminders. Invoked once each morning by pg_cron.
//
// For every family member with notifications enabled, fires a push for each birthday/event
// in their family that is either today or exactly `notify_days_before` days away — the same
// rule the retired Android NotificationWorker used, now evaluated server-side per recipient.
//
// Date helpers below mirror NotificationWorker.kt's daysUntilRecurring / daysUntilOneTime.
// See supabase/functions/README.md for the cron wiring.
import { serviceClient } from "../_shared/client.ts";
import { sendPushToTokens } from "../_shared/fcm.ts";

const MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];

function parseMonthDay(dateStr: string): { month: number; day: number } | null {
  const s = dateStr.trim();
  const iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(s);
  if (iso) return { month: +iso[2], day: +iso[3] };
  // "d MMM" e.g. "24 Dec"
  const m = /^(\d{1,2})\s+([A-Za-z]{3})/.exec(s);
  if (m) {
    const idx = MONTHS.indexOf(m[2].toLowerCase());
    if (idx >= 0) return { month: idx + 1, day: +m[1] };
  }
  return null;
}

function startOfUTCDay(d: Date): Date {
  return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate()));
}

function daysBetween(a: Date, b: Date): number {
  return Math.round((b.getTime() - a.getTime()) / 86_400_000);
}

/** Annual recurrence (birthdays): next occurrence this year, else next year. */
function daysUntilRecurring(dateStr: string, today: Date): number | null {
  const md = parseMonthDay(dateStr);
  if (!md) return null;
  const todayUTC = startOfUTCDay(today);
  let target = new Date(Date.UTC(todayUTC.getUTCFullYear(), md.month - 1, md.day));
  if (target < todayUTC) target = new Date(Date.UTC(todayUTC.getUTCFullYear() + 1, md.month - 1, md.day));
  return daysBetween(todayUTC, target);
}

/** One-off date (calendar events): null if already in the past. */
function daysUntilOneTime(dateStr: string, today: Date): number | null {
  const todayUTC = startOfUTCDay(today);
  const iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(dateStr.trim());
  let target: Date | null = null;
  if (iso) {
    target = new Date(Date.UTC(+iso[1], +iso[2] - 1, +iso[3]));
  } else {
    const md = parseMonthDay(dateStr);
    if (md) target = new Date(Date.UTC(todayUTC.getUTCFullYear(), md.month - 1, md.day));
  }
  if (!target) return null;
  const days = daysBetween(todayUTC, target);
  return days < 0 ? null : days;
}

function shouldNotify(daysUntil: number, lead: number): boolean {
  return daysUntil === 0 || (lead > 0 && daysUntil === lead);
}

Deno.serve(async (_req) => {
  try {
    const supabase = serviceClient();
    const today = new Date();

    const { data: users } = await supabase
      .from("users")
      .select("id, family_id, notifications_enabled, notify_days_before");
    const recipients = (users ?? []).filter((u) => u.notifications_enabled !== false && u.family_id);
    if (recipients.length === 0) return new Response(JSON.stringify({ sent: 0 }), { status: 200 });

    const userIds = recipients.map((u) => u.id);
    const familyIds = [...new Set(recipients.map((u) => u.family_id))];

    const [{ data: tokenRows }, { data: birthdays }, { data: events }] = await Promise.all([
      supabase.from("device_push_tokens").select("user_id, token").in("user_id", userIds),
      supabase.from("birthdays").select("name, date, family_id").in("family_id", familyIds),
      supabase.from("calendar_events").select("activity, date_from, family_id").in("family_id", familyIds),
    ]);

    const tokensByUser = new Map<string, string[]>();
    for (const r of tokenRows ?? []) {
      const arr = tokensByUser.get(r.user_id) ?? [];
      arr.push(r.token);
      tokensByUser.set(r.user_id, arr);
    }

    let sent = 0;
    for (const user of recipients) {
      const tokens = tokensByUser.get(user.id) ?? [];
      if (tokens.length === 0) continue;
      const lead = user.notify_days_before ?? 1;

      for (const b of (birthdays ?? []).filter((x) => x.family_id === user.family_id)) {
        const d = daysUntilRecurring(b.date ?? "", today);
        if (d !== null && shouldNotify(d, lead)) {
          await sendPushToTokens(supabase, tokens, { type: "birthday", name: b.name ?? "", daysUntil: String(d) });
          sent++;
        }
      }
      for (const e of (events ?? []).filter((x) => x.family_id === user.family_id)) {
        const d = daysUntilOneTime(e.date_from ?? "", today);
        if (d !== null && shouldNotify(d, lead)) {
          await sendPushToTokens(supabase, tokens, { type: "event", activity: e.activity ?? "", daysUntil: String(d) });
          sent++;
        }
      }
    }

    return new Response(JSON.stringify({ sent }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error("daily-reminders error", e);
    return new Response("error", { status: 500 });
  }
});
