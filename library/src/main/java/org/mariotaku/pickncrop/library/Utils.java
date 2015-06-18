package org.mariotaku.pickncrop.library;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.Spanned;
import android.text.style.ImageSpan;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by mariotaku on 15/6/18.
 */
public class Utils {

    @Nullable
    public static String getImageUrl(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return null;
        return UtilsAPI16.getImageUrl(context);
    }

    public static boolean closeSilently(final Closeable c) {
        if (c == null) return false;
        try {
            c.close();
        } catch (final IOException e) {
            return false;
        }
        return true;
    }

    public static void copyStream(final InputStream is, final OutputStream os) throws IOException {
        final int bufferSize = 8192;
        final byte[] bytes = new byte[bufferSize];
        int count = is.read(bytes, 0, bufferSize);
        while (count != -1) {
            os.write(bytes, 0, count);
            count = is.read(bytes, 0, bufferSize);
        }
    }


    private static class UtilsAPI16 {

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        public static String getImageUrl(final Context context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return null;
            final ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            final ClipData primaryClip = cm.getPrimaryClip();
            if (primaryClip.getItemCount() > 0) {
                final ClipData.Item item = primaryClip.getItemAt(0);
                final CharSequence styledText = item.coerceToStyledText(context);
                if (styledText instanceof Spanned) {
                    final Spanned spanned = (Spanned) styledText;
                    final ImageSpan[] imageSpans = spanned.getSpans(0, spanned.length(), ImageSpan.class);
                    if (imageSpans.length == 1) return imageSpans[0].getSource();
                }
            }
            return null;
        }
    }
}
