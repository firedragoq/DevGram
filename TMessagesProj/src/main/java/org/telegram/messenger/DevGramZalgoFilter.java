package org.telegram.messenger;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;

import java.util.ArrayList;

/** Bounds pathological combining-mark runs while preserving the string length and spans. */
public final class DevGramZalgoFilter {
    private DevGramZalgoFilter() {}

    public static CharSequence filterSpannable(CharSequence text) {
        if (!DevGramGeneralConfig.isFilterZalgo() || TextUtils.isEmpty(text)) return text;
        ArrayList<int[]> ranges = findRanges(text);
        if (ranges == null) return text;
        String filtered = replace(text.toString(), ranges);
        if (!(text instanceof Spannable)) return filtered;
        Spannable source = (Spannable) text;
        SpannableString result = new SpannableString(filtered);
        for (Object span : source.getSpans(0, source.length(), Object.class)) {
            int start = Math.max(0, Math.min(source.getSpanStart(span), result.length()));
            int end = Math.max(start, Math.min(source.getSpanEnd(span), result.length()));
            result.setSpan(span, start, end, source.getSpanFlags(span));
        }
        return result;
    }

    private static String replace(String source, ArrayList<int[]> ranges) {
        StringBuilder out = new StringBuilder(source.length());
        int last = 0;
        for (int[] range : ranges) {
            out.append(source, last, range[0]);
            for (int i = range[0]; i < range[1]; i++) out.append('\u2060');
            last = range[1];
        }
        return out.append(source, last, source.length()).toString();
    }

    private static ArrayList<int[]> findRanges(CharSequence text) {
        ArrayList<int[]> ranges = null;
        int sequenceStart = -1, marks = 0, allowed = 4;
        for (int offset = 0; offset < text.length();) {
            int cp = Character.codePointAt(text, offset);
            int count = Character.charCount(cp);
            int markLimit = allowedMarks(cp);
            if (markLimit > 0) {
                if (sequenceStart < 0) {
                    sequenceStart = offset;
                    marks = 0;
                    allowed = markLimit;
                } else {
                    allowed = Math.min(allowed, markLimit);
                }
                marks++;
            } else {
                if (sequenceStart >= 0 && marks > allowed) {
                    if (ranges == null) ranges = new ArrayList<>();
                    ranges.add(new int[]{sequenceStart, offset});
                }
                if (isDirectionControl(cp)) {
                    if (ranges == null) ranges = new ArrayList<>();
                    ranges.add(new int[]{offset, offset + count});
                }
                sequenceStart = -1;
                marks = 0;
                allowed = 4;
            }
            offset += count;
        }
        if (sequenceStart >= 0 && marks > allowed) {
            if (ranges == null) ranges = new ArrayList<>();
            ranges.add(new int[]{sequenceStart, text.length()});
        }
        return ranges;
    }

    private static int allowedMarks(int cp) {
        if (cp < 768) return 0;
        int type = Character.getType(cp);
        if (type != Character.NON_SPACING_MARK && type != Character.ENCLOSING_MARK) return 0;
        return isZalgoRange(cp) ? 2 : 4;
    }

    private static boolean isZalgoRange(int cp) {
        return cp >= 768 && cp <= 879 || cp >= 6832 && cp <= 6911 || cp >= 7616 && cp <= 7679
                || cp >= 8400 && cp <= 8447 || cp >= 65056 && cp <= 65071;
    }

    private static boolean isDirectionControl(int cp) {
        return cp == 1564 || cp == 8206 || cp == 8207 || cp >= 8234 && cp <= 8238 || cp >= 8294 && cp <= 8297;
    }
}
