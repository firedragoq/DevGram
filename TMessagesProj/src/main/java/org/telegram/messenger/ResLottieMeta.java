package org.telegram.messenger;

// DevGram: в апстриме 12.10.0 этот класс ГЕНЕРИРУЕТСЯ build-time плагином LottieMeta
// (buildSrc) — предпосчитывает frameCount/fps/mono для встроенных R.raw lottie-анимаций,
// чтобы не парсить их в рантайме. Мы этот build-плагин не подключаем (тянет лишние
// gradle-зависимости), поэтому даём рабочую заглушку: find() всегда возвращает NOT_FOUND,
// и RLottieDrawable уходит в обычный runtime-парсинг (readRes + createFromRawJson) —
// чуть медленнее для встроенных анимаций, но полностью корректно.
public final class ResLottieMeta {

    public static final long NOT_FOUND = -1L;

    private ResLottieMeta() {
    }

    public static long find(int rawRes) {
        return NOT_FOUND;
    }

    public static boolean isMonoColorOf(long value) {
        return false;
    }

    public static int frameCountOf(long value) {
        return 0;
    }

    public static int fpsOf(long value) {
        return 0;
    }
}
