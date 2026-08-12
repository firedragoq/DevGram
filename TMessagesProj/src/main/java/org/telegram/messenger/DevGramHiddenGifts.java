/*
 * DevGram: скрытые подарки Telegram.
 *
 * Telegram убирает событийные подарки (мишки 14 февраля, 1 апреля и т.п.) из витрины
 * getStarGifts, но отправить их по прямому gift_id всё ещё можно. Список таких подарков
 * ведётся в репозитории (gifts.json): gift_id + цена + номер стикера в стикерпаке.
 * Клиент грузит список, подтягивает стикерпак и показывает подарки в окне «Подарить
 * подарок» сверху, как обычные. Схема повторяет плагин exteraGram.
 */

package org.telegram.messenger;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;

public class DevGramHiddenGifts {

    private static final String LIST_URL = "https://raw.githubusercontent.com/firedragoq/devgram-gifts/master/gifts.json";
    private static final String DEFAULT_PACK = "gifts_1_by_gifts_changes_bot";
    private static final long CACHE_TTL = 10 * 60 * 1000L; // список перечитываем не чаще раза в 10 минут

    private static class Spec {
        long id;
        long stars;
        int stickerNumber;
        String pack;
    }

    private static final ArrayList<Spec> specs = new ArrayList<>();
    private static final ArrayList<TL_stars.StarGift> gifts = new ArrayList<>();
    private static final HashMap<String, ArrayList<TLRPC.Document>> packDocs = new HashMap<>();
    private static long lastLoad;
    private static boolean loading;

    // Готовые подарки; пустой список, пока не загрузились.
    public static ArrayList<TL_stars.StarGift> get() {
        return gifts;
    }

    public static boolean isHidden(long giftId) {
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).id == giftId) {
                return true;
            }
        }
        return false;
    }

    // Загрузить список и стикеры. whenDone — на UI-потоке, может вызваться дважды
    // (сначала со списком без стикеров, потом со стикерами).
    public static void load(int account, @Nullable Runnable whenDone) {
        if (loading || System.currentTimeMillis() - lastLoad < CACHE_TTL && !gifts.isEmpty()) {
            return;
        }
        loading = true;
        Utilities.globalQueue.postRunnable(() -> {
            final ArrayList<Spec> parsed = new ArrayList<>();
            try {
                JSONObject root = new JSONObject(download(LIST_URL));
                String commonPack = root.optString("pack", DEFAULT_PACK);
                JSONArray arr = root.getJSONArray("gifts");
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject g = arr.getJSONObject(i);
                    Spec s = new Spec();
                    s.id = g.optLong("id", 0);
                    s.stars = g.optLong("stars", 50);
                    s.stickerNumber = g.optInt("sticker_number", 0);
                    s.pack = g.optString("pack", commonPack);
                    if (s.id != 0) {
                        parsed.add(s);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                lastLoad = System.currentTimeMillis();
                specs.clear();
                specs.addAll(parsed);
                rebuild();
                if (whenDone != null) {
                    whenDone.run();
                }
                loadPacks(account, whenDone);
            });
        });
    }

    // Собрать объекты подарков. Уже созданные обновляем на месте: ссылка на подарок
    // может быть в открытом окне отправки, и пересоздание оставило бы его без стикера.
    private static void rebuild() {
        final ArrayList<TL_stars.StarGift> old = new ArrayList<>(gifts);
        gifts.clear();
        for (int i = 0; i < specs.size(); i++) {
            final Spec s = specs.get(i);
            TL_stars.StarGift existing = null;
            for (int j = 0; j < old.size(); j++) {
                if (old.get(j).id == s.id) {
                    existing = old.get(j);
                    break;
                }
            }
            TL_stars.StarGift gift = existing != null ? existing : new TL_stars.TL_starGift();
            gift.id = s.id;
            gift.stars = s.stars;
            gift.sticker = findSticker(s);
            // подарок должен выглядеть обычным и доступным
            gift.limited = false;
            gift.sold_out = false;
            gift.availability_remains = 0;
            gift.availability_total = 0;
            gifts.add(gift);
        }
    }

    @Nullable
    private static TLRPC.Document findSticker(Spec s) {
        ArrayList<TLRPC.Document> docs = packDocs.get(s.pack);
        if (docs == null || docs.isEmpty()) {
            return null;
        }
        int n = s.stickerNumber;
        return n >= 0 && n < docs.size() ? docs.get(n) : docs.get(0);
    }

    private static void loadPacks(int account, @Nullable Runnable whenDone) {
        for (int i = 0; i < specs.size(); i++) {
            final String pack = specs.get(i).pack;
            if (pack == null || packDocs.containsKey(pack)) {
                continue;
            }
            packDocs.put(pack, new ArrayList<>()); // помечаем как запрошенный
            requestPack(account, pack, whenDone);
        }
    }

    private static void requestPack(int account, String pack, @Nullable Runnable whenDone) {
        // сначала пробуем кэш стикеров
        TLRPC.TL_messages_stickerSet cached = MediaDataController.getInstance(account).getStickerSetByName(pack);
        if (cached != null && !cached.documents.isEmpty()) {
            packDocs.put(pack, new ArrayList<>(cached.documents));
            rebuild();
            if (whenDone != null) {
                whenDone.run();
            }
            return;
        }
        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        TLRPC.TL_inputStickerSetShortName input = new TLRPC.TL_inputStickerSetShortName();
        input.short_name = pack;
        req.stickerset = input;
        req.hash = 0;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (res instanceof TLRPC.TL_messages_stickerSet) {
                TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) res;
                packDocs.put(pack, new ArrayList<>(set.documents));
                rebuild();
                if (whenDone != null) {
                    whenDone.run();
                }
            }
        }));
    }

    // Перечитать список принудительно (например, после правки репозитория).
    // Кэш стикерпаков тоже сбрасываем: в списке мог смениться пак.
    public static void invalidate() {
        lastLoad = 0;
        loading = false;
        packDocs.clear();
    }

    private static String download(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("User-Agent", "DevGram");
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }
}
