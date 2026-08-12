const { onValueWritten } = require("firebase-functions/v2/database");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");

admin.initializeApp();

const telegramToken = defineSecret("DEVGRAM_TELEGRAM_BOT_TOKEN");
const MODERATOR_UID = "cZUBpCEJWVNAZEuChkG52gdgs0K2";
const REVIEW_WINDOW_MS = 24 * 60 * 60 * 1000;
const MAX_REVIEWS_PER_DAY = 5;
const MAX_REPORTS_PER_DAY = 20;
const PLUGIN_REPORT_THRESHOLD = 5;

function db() { return admin.database(); }
function now() { return Date.now(); }
function dayKey(ts) { return new Date(ts).toISOString().slice(0, 10); }

async function notify(text) {
  const token = telegramToken.value();
  if (!token) return;
  const snap = await db().ref("moderators").once("value");
  const ids = new Set(["7101191373"]);
  snap.forEach((child) => {
    const tg = child.child("tg").val();
    if (tg) ids.add(String(tg));
  });
  for (const chatId of ids) {
    try {
      await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ chat_id: chatId, text, disable_web_page_preview: true })
      });
    } catch (e) { console.error("Telegram notification failed", e); }
  }
}

async function rateLimit(path, actor, limit) {
  if (!actor) return false;
  const ref = db().ref(`rate_limits/${path}/${actor}/${dayKey(now())}`);
  const result = await ref.transaction((value) => {
    const next = (Number(value) || 0) + 1;
    return next > limit ? (value || 0) : next;
  });
  return result.committed && Number(result.snapshot.val()) <= limit;
}

exports.guardReviews = onValueWritten("/plugin_reviews/{pluginId}/{userId}", async (event) => {
  const after = event.data.after.val();
  if (!after) return;
  const userId = String(after.userId || event.params.userId);
  const allowed = await rateLimit("reviews", userId, MAX_REVIEWS_PER_DAY);
  if (!allowed) {
    await event.data.after.ref.remove();
    return;
  }
  const reviews = (await event.data.after.ref.parent.once("value")).val() || {};
  const values = Object.values(reviews).filter(Boolean);
  const count = values.length;
  const rating = count ? values.reduce((sum, r) => sum + Number(r.rating || 0), 0) / count : 0;
  await db().ref(`plugins_catalog/${event.params.pluginId}`).update({ rating, reviews: count });
});

exports.guardPluginReports = onValueWritten("/plugin_reports/{reportId}", async (event) => {
  const report = event.data.after.val();
  if (!report) return;
  const actor = String(report.reporterId || "");
  if (!(await rateLimit("reports", actor, MAX_REPORTS_PER_DAY))) {
    await event.data.after.ref.remove();
    return;
  }
  const reports = (await db().ref(`plugin_reports`).orderByChild("pluginId").equalTo(report.pluginId).once("value")).val() || {};
  if (Object.keys(reports).length >= PLUGIN_REPORT_THRESHOLD) {
    const plugin = await db().ref(`plugins_catalog/${report.pluginId}`).once("value");
    if (plugin.exists()) {
      await db().ref(`plugins_hidden/${report.pluginId}`).set({ hidden: true, reason: "report_threshold", date: now() });
      await db().ref(`plugins_catalog/${report.pluginId}/visible`).set(false);
      await notify(`DevGram: плагин ${report.pluginId} автоматически скрыт после ${Object.keys(reports).length} жалоб.`);
    }
  }
});

exports.guardReviewReports = onValueWritten("/plugin_review_reports/{reportId}", async (event) => {
  const report = event.data.after.val();
  if (!report) return;
  const actor = String(report.reporterId || "");
  if (!(await rateLimit("review_reports", actor, MAX_REPORTS_PER_DAY))) {
    await event.data.after.ref.remove();
    return;
  }
  await notify(`DevGram: новая жалоба на отзыв плагина ${report.pluginId}.`);
});

exports.notifyPendingPlugin = onValueWritten("/plugins_pending/{pluginId}", async (event) => {
  if (!event.data.after.exists() || event.data.before.exists()) return;
  const p = event.data.after.val() || {};
  await notify(`DevGram: новая заявка на модерацию: ${p.name || event.params.pluginId} v${p.version || ""}`);
});
