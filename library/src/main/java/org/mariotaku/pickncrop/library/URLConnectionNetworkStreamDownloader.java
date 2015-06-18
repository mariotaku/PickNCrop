package org.mariotaku.pickncrop.library;

import android.content.Context;
import android.net.Uri;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by mariotaku on 15/6/18.
 */
public class URLConnectionNetworkStreamDownloader extends ImagePickerActivity.NetworkStreamDownloader {
    public URLConnectionNetworkStreamDownloader(final Context context) {
        super(context);
    }

    @Override
    public DownloadResult get(final Uri uri) throws IOException {
        final URLConnection urlConnection = new URL(uri.toString()).openConnection();
        return new DownloadResult(urlConnection.getInputStream(), urlConnection.getContentType());
    }
}
