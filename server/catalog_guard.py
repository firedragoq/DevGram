#!/usr/bin/env python3
import json
import logging
import os
import sqlite3
import time
import urllib.parse
import urllib.request

ENV_PATH = os.getenv("DEVGRAM_ENV_FILE", os.path.join(os.path.dirname(__file__), "catalog_guard.env"))
if os.path.exists(ENV_PATH):
    with open(ENV_PATH, encoding="utf-8") as env_file:
        for env_line in env_file:
            env_line = env_line.strip()
            if not env_line or env_line.startswith("#") or "=" not in env_line: continue
            env_key, env_value = env_line.split("=", 1)
            os.environ[env_key.strip()] = env_value.strip().strip('"').strip("'")

RTDB = os.getenv("DEVGRAM_FIREBASE_URL", "https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app").rstrip("/")
API_KEY = os.getenv("DEVGRAM_FIREBASE_API_KEY", "")
EMAIL = os.getenv("DEVGRAM_FIREBASE_EMAIL", "")
PASSWORD = os.getenv("DEVGRAM_FIREBASE_PASSWORD", "")
BOT_TOKEN = os.environ["DEVGRAM_TELEGRAM_BOT_TOKEN"]
OWNER_CHAT_ID = os.getenv("DEVGRAM_OWNER_CHAT_ID", "7101191373")
POLL_SECONDS = max(10, int(os.getenv("DEVGRAM_POLL_SECONDS", "30")))
REPORT_THRESHOLD = max(2, int(os.getenv("DEVGRAM_REPORT_THRESHOLD", "5")))
MAX_REPORTS_DAY = max(1, int(os.getenv("DEVGRAM_MAX_REPORTS_DAY", "20")))
MAX_REVIEWS_DAY = max(1, int(os.getenv("DEVGRAM_MAX_REVIEWS_DAY", "5")))
SUMMARY_HOURS = max(0, float(os.getenv("DEVGRAM_SUMMARY_HOURS", "24")))
STATE_PATH = os.getenv("DEVGRAM_GUARD_STATE", os.path.join(os.path.dirname(__file__), "catalog_guard.sqlite3"))

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def request(method, url, body=None, timeout=20):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as response:
        raw = response.read().decode().strip()
        return None if not raw or raw == "null" else json.loads(raw)


def sign_in():
    url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + urllib.parse.quote(API_KEY)
    result = request("POST", url, {"email": EMAIL, "password": PASSWORD, "returnSecureToken": True})
    return result["idToken"], time.time() + int(result.get("expiresIn", 3600)) - 120


class Guard:
    def __init__(self):
        self.token = None
        self.token_expires = 0
        self.db = sqlite3.connect(STATE_PATH)
        self.db.execute("create table if not exists seen(kind text, item_key text, primary key(kind,item_key))")
        self.db.execute("create table if not exists rate(kind text, actor text, day text, count integer, primary key(kind,actor,day))")
        self.db.commit()

    def auth(self):
        if not API_KEY or not EMAIL or not PASSWORD or EMAIL == "moderator@example.com":
            raise RuntimeError("Firebase moderator credentials are not configured")
        if not self.token or time.time() >= self.token_expires:
            self.token, self.token_expires = sign_in()
        return self.token

    def firebase(self, method, path, body=None, authenticated=False):
        suffix = "?auth=" + urllib.parse.quote(self.auth()) if authenticated else ""
        return request(method, f"{RTDB}/{path}.json{suffix}", body)

    def notify(self, text):
        request("POST", f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage", {
            "chat_id": OWNER_CHAT_ID, "text": text, "disable_web_page_preview": True
        })

    @staticmethod
    def fmt_ts(value):
        try: return time.strftime("%Y-%m-%d %H:%M", time.localtime(int(value) / 1000))
        except Exception: return "?"

    def telemetry(self):
        installs = self.firebase("GET", "analytics/installs", authenticated=True) or {}
        crashes = self.firebase("GET", "crashes", authenticated=True) or {}
        for iid, item in installs.items():
            if not isinstance(item, dict) or not self.unseen("install", iid): continue
            self.notify("🆕 Новая установка DevGram\n\n"
                        f"Версия: {item.get('ver', '?')} ({item.get('build', '?')})\n"
                        f"Android: SDK {item.get('android', '?')} · {item.get('model', '?')}\n"
                        f"Язык: {item.get('lang', '?')}\nОткрытий: {item.get('opens', '?')}\nID: {iid}")
        for iid, per_install in crashes.items():
            if not isinstance(per_install, dict): continue
            for ts, item in per_install.items():
                key = f"{iid}/{ts}"
                if not isinstance(item, dict) or not self.unseen("crash", key): continue
                trace = str(item.get("trace", ""))[:3000]
                self.notify("💥 Краш DevGram\n\n"
                            f"Версия: {item.get('ver', '?')} ({item.get('build', '?')})\n"
                            f"Android: SDK {item.get('android', '?')} · {item.get('model', '?')}\n"
                            f"Поток: {item.get('thread', '?')}\nВремя: {self.fmt_ts(item.get('ts', ts))}\n"
                            f"ID: {iid}\n\n{trace}")
        if SUMMARY_HOURS > 0:
            row = self.db.execute("select item_key from seen where kind='last_summary'").fetchone()
            last = float(row[0]) if row else 0
            if time.time() - last >= SUMMARY_HOURS * 3600:
                versions, opens = {}, 0
                for item in installs.values():
                    if not isinstance(item, dict): continue
                    version = str(item.get("ver", "?")); versions[version] = versions.get(version, 0) + 1
                    try: opens += int(item.get("opens", 0))
                    except Exception: pass
                top = ", ".join(f"{k}×{v}" for k, v in sorted(versions.items(), key=lambda x: -x[1])[:5]) or "—"
                self.notify(f"📊 Сводка DevGram\n\nУстановок: {len(installs)}\nВсего открытий: {opens}\nВерсии: {top}")
                self.db.execute("delete from seen where kind='last_summary'")
                self.db.execute("insert into seen(kind,item_key) values('last_summary',?)", (str(time.time()),)); self.db.commit()

    def unseen(self, kind, key):
        row = self.db.execute("select 1 from seen where kind=? and item_key=?", (kind, key)).fetchone()
        if row: return False
        self.db.execute("insert or ignore into seen(kind,item_key) values(?,?)", (kind, key)); self.db.commit()
        return True

    def allowed(self, kind, actor, limit):
        day = time.strftime("%Y-%m-%d", time.gmtime())
        row = self.db.execute("select count from rate where kind=? and actor=? and day=?", (kind, actor, day)).fetchone()
        count = (row[0] if row else 0) + 1
        self.db.execute("insert into rate(kind,actor,day,count) values(?,?,?,?) on conflict(kind,actor,day) do update set count=excluded.count", (kind, actor, day, count)); self.db.commit()
        return count <= limit

    def pending(self):
        for key, plugin in (self.firebase("GET", "plugins_pending") or {}).items():
            if self.unseen("pending", key):
                self.notify(f"DevGram: новая заявка — {plugin.get('name', key)} v{plugin.get('version', '')}")

    def reports(self):
        try: reports = self.firebase("GET", "plugin_reports", authenticated=True) or {}
        except RuntimeError:
            logging.warning("Reports skipped: Firebase moderator credentials are not configured")
            return
        grouped = {}
        for key, report in reports.items():
            actor = str(report.get("reporterId", ""))
            if self.unseen("report", key):
                if not self.allowed("report", actor, MAX_REPORTS_DAY):
                    self.firebase("DELETE", f"plugin_reports/{key}", authenticated=True)
                    continue
                self.notify(f"DevGram: жалоба на {report.get('pluginId', 'плагин')} — {report.get('reason', 'без причины')}")
            grouped.setdefault(report.get("pluginId", ""), set()).add(actor)
        for plugin_id, actors in grouped.items():
            if plugin_id and len(actors) >= REPORT_THRESHOLD:
                hidden = self.firebase("GET", f"plugins_hidden/{plugin_id}", authenticated=True)
                if not hidden:
                    self.firebase("PUT", f"plugins_hidden/{plugin_id}", {"hidden": True, "reason": "report_threshold", "date": int(time.time() * 1000), "reports": len(actors)}, True)
                    self.firebase("PATCH", f"plugins_catalog/{plugin_id}", {"visible": False}, True)
                    self.notify(f"DevGram: плагин {plugin_id} скрыт после {len(actors)} уникальных жалоб.")

    def review_reports(self):
        reports = self.firebase("GET", "plugin_review_reports", authenticated=True) or {}
        for key, report in reports.items():
            if not self.unseen("review_report", key): continue
            actor = str(report.get("reporterId", ""))
            if not self.allowed("review_report", actor, MAX_REPORTS_DAY):
                self.firebase("DELETE", f"plugin_review_reports/{key}", authenticated=True)
                continue
            self.notify("DevGram: жалоба на отзыв\n"
                        f"Плагин: {report.get('pluginId', '?')}\n"
                        f"Автор отзыва: {report.get('reviewUserId', '?')}\n"
                        f"Причина: {report.get('reason', 'не указана')}")

    def reviews(self):
        plugins = self.firebase("GET", "plugin_reviews") or {}
        for plugin_id, reviews in plugins.items():
            if not isinstance(reviews, dict): continue
            valid = []
            for key, review in list(reviews.items()):
                if not isinstance(review, dict): continue
                event_key = f"{plugin_id}/{key}/{review.get('date', '')}"
                if self.unseen("review", event_key):
                    actor = str(review.get("userId", key))
                    if not self.allowed("review", actor, MAX_REVIEWS_DAY):
                        self.firebase("DELETE", f"plugin_reviews/{plugin_id}/{key}", authenticated=True)
                        continue
                valid.append(review)
            count = len(valid)
            rating = sum(float(r.get("rating", 0)) for r in valid) / count if count else 0
            self.firebase("PATCH", f"plugins_catalog/{plugin_id}", {"rating": rating, "reviews": count}, authenticated=True)

    def run(self):
        logging.info("DevGram Catalog Guard started")
        while True:
            for name, job in (("telemetry", self.telemetry), ("pending", self.pending), ("reviews", self.reviews), ("reports", self.reports), ("review_reports", self.review_reports)):
                try: job()
                except Exception: logging.exception("%s cycle failed", name)
            time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    Guard().run()
