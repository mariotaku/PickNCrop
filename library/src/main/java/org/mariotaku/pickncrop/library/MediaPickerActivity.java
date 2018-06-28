/*
 * Copyright (c) 2015 mariotaku
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mariotaku.pickncrop.library;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.support.annotation.WorkerThread;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageActivity;
import com.theartofdev.edmodo.cropper.CropImageView.RequestSizeOptions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import repackaged.com.github.ooxi.jdatauri.DataUri;

import static android.os.Environment.getExternalStorageState;

public class MediaPickerActivity extends Activity {

    public static final int REQUEST_GET_CONTENT = 101;
    public static final int REQUEST_TAKE_PHOTO = 102;
    public static final int REQUEST_CROP = 103;
    public static final int REQUEST_OPEN_DOCUMENT = 104;
    public static final int REQUEST_RECORD_VIDEO = 105;

    private static final String EXTRA_EXTRA_ENTRIES = "extra_entries";
    private static final String EXTRA_CROP_ACTIVITY_CLASS = "crop_activity_class";
    private static final String EXTRA_STREAM_DOWNLOADER_CLASS = "stream_downloader_class";
    private static final String EXTRA_TEMP_IMAGE_URI = "temp_image_uri";
    private static final String INTENT_PACKAGE_PREFIX = BuildConfig.APPLICATION_ID + ".";
    public static final String INTENT_ACTION_TAKE_PHOTO = INTENT_PACKAGE_PREFIX + "TAKE_PHOTO";
    public static final String INTENT_ACTION_CAPTURE_VIDEO = INTENT_PACKAGE_PREFIX + "CAPTURE_VIDEO";
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public static final String INTENT_ACTION_PICK_IMAGE = INTENT_PACKAGE_PREFIX + "PICK_IMAGE";
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public static final String INTENT_ACTION_GET_IMAGE = INTENT_PACKAGE_PREFIX + "GET_IMAGE";

    public static final String INTENT_ACTION_PICK_MEDIA = INTENT_PACKAGE_PREFIX + "PICK_MEDIA";
    public static final String INTENT_ACTION_GET_MEDIA = INTENT_PACKAGE_PREFIX + "GET_MEDIA";

    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    private static final String SCHEME_DATA = "data";
    private static final String LOGTAG = "PickNCrop";

    public static final String EXTRA_ASPECT_X = "aspect_x";
    public static final String EXTRA_ASPECT_Y = "aspect_y";
    public static final String EXTRA_MAX_WIDTH = "max_width";
    public static final String EXTRA_MAX_HEIGHT = "max_height";
    public static final String EXTRA_ALLOW_MULTIPLE = "allow_multiple";
    public static final String EXTRA_CONTAINS_VIDEO = "contains_video";
    public static final String EXTRA_VIDEO_ONLY = "video_only";
    public static final String EXTRA_VIDEO_QUALITY = "video_quality";
    public static final String EXTRA_PICK_SOURCES = "pick_sources";
    public static final String EXTRA_EXTRAS = "extra_extras";
    public static final String EXTRA_LOCAL_ONLY = "local_only";
    public static final String EXTRA_OUTPUT_FORMAT = "output_format";
    public static final String EXTRA_OUTPUT_QUALITY = "output_quality";

    public static final String SOURCE_CAMERA = "camera";
    public static final String SOURCE_CAMCORDER = "camcorder";
    public static final String SOURCE_CLIPBOARD = "clipboard";
    public static final String SOURCE_GALLERY = "gallery";

    private CopyMediaTask mTask;
    private Queue<Runnable> mResumeRunnableQueue = new LinkedList<>();
    private boolean mFragmentResumed;
    private Uri mTempImageUri;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (savedInstanceState != null) {
            mTempImageUri = savedInstanceState.getParcelable(EXTRA_TEMP_IMAGE_URI);
        } else {
            if (action == null) {
                new ExtraSourceDialogFragment().show(getFragmentManager(), "extra_sources");
                return;
            }
            switch (action) {
                case INTENT_ACTION_CAPTURE_VIDEO: {
                    openCamera(true);
                    break;
                }
                case INTENT_ACTION_TAKE_PHOTO: {
                    openCamera(false);
                    break;
                }
                //noinspection deprecation
                case INTENT_ACTION_PICK_IMAGE:
                case INTENT_ACTION_PICK_MEDIA: {
                    pickMedia();
                    break;
                }
                //noinspection deprecation
                case INTENT_ACTION_GET_IMAGE:
                case INTENT_ACTION_GET_MEDIA: {
                    final Uri[] uris = getMediaUris(intent);
                    mediaSelected(uris, true, false);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unknown action " + action);
                }
            }
        }
    }

    @Override
    protected void onPause() {
        mFragmentResumed = false;
        super.onPause();
    }


    @Override
    protected void onPostResume() {
        super.onPostResume();
        mFragmentResumed = true;
        executePending();
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode,
                                    @Nullable final Intent data) {
        final ActivityResult result = handleActivityResult(requestCode, resultCode, data);
        if (result == null) {
            Intent resultData = new Intent();
            resultData.putExtra(EXTRA_EXTRAS, getIntent().getBundleExtra(EXTRA_EXTRAS));
            setResult(RESULT_CANCELED, resultData);
            finish();
            return;
        }
        queueAfterResumed(new Runnable() {
            @Override
            public void run() {
                mediaSelected(result.src, result.needsCrop, result.deleteSource);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(EXTRA_TEMP_IMAGE_URI, mTempImageUri);
    }

    private void queueAfterResumed(Runnable runnable) {
        mResumeRunnableQueue.add(runnable);
        executePending();
    }

    private void executePending() {
        if (!mFragmentResumed) return;
        Runnable runnable;
        while ((runnable = mResumeRunnableQueue.poll()) != null) {
            runOnUiThread(runnable);
        }
    }

    private void mediaSelected(final Uri[] uris, final boolean needsCrop, final boolean deleteSource) {
        final CopyMediaTask task = mTask;
        if (task != null && task.getStatus() == AsyncTask.Status.RUNNING) return;
        mTask = new CopyMediaTask(this, uris, needsCrop, deleteSource);
        mTask.execute();
    }

    @Nullable
    private ActivityResult handleActivityResult(final int requestCode, final int resultCode,
                                                @Nullable final Intent data) {
        if (resultCode != RESULT_OK) return null;

        final boolean needsCrop, deleteSource;
        final Uri[] src;
        switch (requestCode) {
            case REQUEST_OPEN_DOCUMENT: {
                if (data == null) return null;
                needsCrop = true;
                deleteSource = false;
                src = getMediaUris(data);
                break;
            }
            case REQUEST_GET_CONTENT: {
                if (data == null) return null;
                needsCrop = true;
                deleteSource = false;
                src = getMediaUris(data);
                break;
            }
            case REQUEST_RECORD_VIDEO: {
                if (data == null) return null;
                needsCrop = false;
                deleteSource = true;
                src = new Uri[]{data.getData()};
                break;
            }
            case REQUEST_TAKE_PHOTO: {
                if (mTempImageUri == null) return null;
                needsCrop = true;
                deleteSource = false;
                src = new Uri[]{mTempImageUri};
                break;
            }
            case REQUEST_CROP: {
                CropImage.ActivityResult result = CropImage.getActivityResult(data);
                if (result == null) return null;
                needsCrop = false;
                deleteSource = true;
                src = new Uri[]{result.getUri()};
                break;
            }
            default:
                return null;
        }
        if (src == null) return null;
        return new ActivityResult(needsCrop, deleteSource, src);
    }

    private void pickMedia() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final Intent intent = getIntent();
            final boolean containsVideo = intent.getBooleanExtra(EXTRA_CONTAINS_VIDEO, false);
            final boolean videoOnly = intent.getBooleanExtra(EXTRA_VIDEO_QUALITY, false);
            final boolean allowMultiple = intent.getBooleanExtra(EXTRA_ALLOW_MULTIPLE, false);
            final boolean localOnly = intent.getBooleanExtra(EXTRA_LOCAL_ONLY, false);
            final Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
            getContentIntent.addCategory(Intent.CATEGORY_OPENABLE);
            getContentIntent.setType("*/*");
            getContentIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            final List<String> mimeTypesList = new ArrayList<>();
            boolean containsImage = true;
            if (containsVideo) {
                mimeTypesList.add("video/*");
                containsImage = !videoOnly;
            }
            if (containsImage) {
                mimeTypesList.add("image/*");
            }
            getContentIntent.putExtra(Intent.EXTRA_MIME_TYPES,
                    mimeTypesList.toArray(new String[mimeTypesList.size()]));
            getContentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
            getContentIntent.putExtra(Intent.EXTRA_LOCAL_ONLY, localOnly);
            startActivityForResult(getContentIntent, REQUEST_OPEN_DOCUMENT);
            return;
        }
        final Intent getContentIntent = new Intent(Intent.ACTION_GET_CONTENT);
        getContentIntent.setType("image/*");
        getContentIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(getContentIntent, REQUEST_GET_CONTENT);
        } catch (final ActivityNotFoundException e) {
            // Ignore
        }
    }

    private void openCamera(boolean isVideo) {
        final Intent intent = getIntent();
        final int videoQuality = intent.getIntExtra(EXTRA_VIDEO_QUALITY, -1);
        final Intent captureIntent;
        final int requestCode;
        if (isVideo) {
            requestCode = REQUEST_RECORD_VIDEO;
            captureIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (videoQuality != -1) {
                captureIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, videoQuality);
            }
        } else {
            requestCode = REQUEST_TAKE_PHOTO;
            captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            final Uri uri;
            try {
                uri = createTempMediaUri("jpg");
            } catch (IOException e) {
                Toast.makeText(this, R.string.pnc__error_cannot_open_file, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
            mTempImageUri = uri;
        }
        try {
            startActivityForResult(captureIntent, requestCode);
        } catch (final ActivityNotFoundException e) {
            // Ignore
            finish();
        }
    }

    @NonNull
    private Uri createTempMediaUri(String extension) throws IOException {
        if (extension == null) {
            extension = "tmp";
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (!getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                throw new IOException("SD card not mounted");
            }
            final File extCacheDir = getExternalCacheDir();
            final File extPickedMediaDir = new File(extCacheDir, "picked-media");
            if (!extPickedMediaDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                extPickedMediaDir.mkdirs();
            }
            final File file = randomFile(extPickedMediaDir, "pnc__picked_media_", "." + extension);
            return Uri.fromFile(file);
        }
        final File cacheDir = getCacheDir();
        final File pickedMediaDir = new File(cacheDir, "picked-media");
        if (!pickedMediaDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            pickedMediaDir.mkdirs();
        }
        final File file = randomFile(pickedMediaDir, "pnc__picked_media_", "." + extension);
        return FileProvider.getUriForFile(this, PNCUtils.getFileAuthority(this), file);
    }

    private void dismissProgressDialog(final String tag) {
        queueAfterResumed(new Runnable() {
            @Override
            public void run() {
                final Fragment f = getFragmentManager().findFragmentByTag(tag);
                if (f instanceof DialogFragment) {
                    ((DialogFragment) f).dismiss();
                }
            }
        });
    }

    private NetworkStreamDownloader createNetworkStreamDownloader() {
        final Intent intent = getIntent();
        try {
            final Class<?> downloadClass = Class.forName(intent.getStringExtra(EXTRA_STREAM_DOWNLOADER_CLASS));
            return (NetworkStreamDownloader) downloadClass.getConstructor(Context.class).newInstance(this);
        } catch (Exception e) {
            return new URLConnectionNetworkStreamDownloader(this);
        }
    }

    public static Uri[] getMediaUris(Intent fromIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ClipData clipData = fromIntent.getClipData();
            if (clipData != null && clipData.getItemCount() > 0) {
                List<Uri> uriList = new ArrayList<>();
                for (int i = 0, j = clipData.getItemCount(); i < j; i++) {
                    final Uri uri = clipData.getItemAt(i).getUri();
                    if (uri != null) {
                        uriList.add(uri);
                    }
                }
                return uriList.toArray(new Uri[uriList.size()]);
            }
        }
        final Uri data = fromIntent.getData();
        if (data == null) return null;
        return new Uri[]{data};
    }

    @SuppressWarnings("SameParameterValue")
    private static File randomFile(File directory, String prefix, String suffix) {
        File file;
        do {
            final String uuid = UUID.randomUUID().toString();
            file = new File(directory, prefix + uuid + suffix);
        } while (file.exists());
        return file;
    }

    public static class ExtraSourceDialogFragment extends DialogFragment implements OnClickListener {

        private Entry[] mEntries;
        private String mClipboardImageUrl;

        @Override
        public void onClick(final DialogInterface dialog, final int which) {
            final Activity activity = getActivity();
            if (!(activity instanceof MediaPickerActivity)) return;
            final MediaPickerActivity addImageActivity = (MediaPickerActivity) activity;
            final Entry entry = mEntries[which];
            final String source = entry.value;
            if (SOURCE_GALLERY.equals(source)) {
                addImageActivity.pickMedia();
            } else if (SOURCE_CAMERA.equals(source)) {
                addImageActivity.openCamera(false);
            } else if (SOURCE_CAMCORDER.equals(source)) {
                addImageActivity.openCamera(true);
            } else if (SOURCE_CLIPBOARD.equals(source)) {
                if (mClipboardImageUrl != null) {
                    addImageActivity.mediaSelected(new Uri[]{Uri.parse(mClipboardImageUrl)}, true,
                            false);
                }
            } else {
                Intent data = new Intent();
                data.putExtra(EXTRA_EXTRAS, getActivity().getIntent().getBundleExtra(EXTRA_EXTRAS));
                addImageActivity.setResult(entry.result, data);
                addImageActivity.finish();
            }
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Activity activity = getActivity();
            final Intent intent = activity.getIntent();
            final PackageManager pm = activity.getPackageManager();
            final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            final ArrayList<Entry> entriesList = new ArrayList<>();
            final String[] pickSources = intent.getStringArrayExtra(EXTRA_PICK_SOURCES);
            final boolean containsVideo = intent.getBooleanExtra(EXTRA_CONTAINS_VIDEO, false);
            final boolean videoOnly = intent.getBooleanExtra(EXTRA_VIDEO_QUALITY, false);
            if (pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                if (hasSource(pickSources, SOURCE_CAMERA) && (!containsVideo || !videoOnly)) {
                    entriesList.add(new Entry(getString(R.string.pnc__source_camera), SOURCE_CAMERA));
                }
                if (hasSource(pickSources, SOURCE_CAMCORDER) && containsVideo) {
                    entriesList.add(new Entry(getString(R.string.pnc__source_camcorder), SOURCE_CAMCORDER));
                }
            }
            if (hasSource(pickSources, SOURCE_GALLERY)) {
                entriesList.add(new Entry(getString(R.string.pnc__source_gallery), SOURCE_GALLERY));
            }
            mClipboardImageUrl = PNCUtils.getImageUrl(activity);
            if (hasSource(pickSources, SOURCE_CLIPBOARD) && !TextUtils.isEmpty(mClipboardImageUrl)) {
                entriesList.add(new Entry(getString(R.string.pnc__source_clipboard), SOURCE_CLIPBOARD));
            }
            final ArrayList<ExtraEntry> extraEntries = intent.getParcelableArrayListExtra(EXTRA_EXTRA_ENTRIES);
            if (extraEntries != null) {
                for (ExtraEntry extraEntry : extraEntries) {
                    entriesList.add(new Entry(extraEntry.name, extraEntry.value, extraEntry.result));
                }
            }
            mEntries = entriesList.toArray(new Entry[entriesList.size()]);
            builder.setItems(mEntries, this);
            return builder.create();
        }

        @Override
        public void onCancel(final DialogInterface dialog) {
            super.onCancel(dialog);
            final Activity a = getActivity();
            if (a != null) {
                a.finish();
            }
        }

        @Override
        public void onDismiss(final DialogInterface dialog) {
            super.onDismiss(dialog);
        }

        private boolean hasSource(@Nullable String[] pickSources, @NonNull String source) {
            if (pickSources == null) return true;
            for (String pickSource : pickSources) {
                if (source.equals(pickSource)) return true;
            }
            return false;
        }

        private static class Entry implements CharSequence {

            private final String name;
            private final String value;
            private final int result;

            Entry(@NonNull String name, @NonNull String value) {
                this(name, value, -1);
            }

            Entry(@NonNull String name, @NonNull String value, int result) {
                this.name = name;
                this.value = value;
                this.result = result;
            }

            @Override
            public CharSequence subSequence(final int start, final int end) {
                return name.subSequence(start, end);
            }

            @Override
            public char charAt(final int index) {
                return name.charAt(index);
            }

            @Override
            public int length() {
                return name.length();
            }

            @NonNull
            @Override
            public String toString() {
                return name;
            }
        }
    }

    @SuppressWarnings("unused,WeakerAccess")
    public static abstract class NetworkStreamDownloader {

        private final Context mContext;

        protected NetworkStreamDownloader(Context context) {
            mContext = context;
        }

        public final Context getContext() {
            return mContext;
        }

        @WorkerThread
        public abstract DownloadResult get(Uri uri) throws IOException;

        public static final class DownloadResult {

            private final InputStream stream;
            private final String mimeType;

            public DownloadResult(final InputStream stream, final String mimeType) {
                this.stream = stream;
                this.mimeType = mimeType;
            }

            public static DownloadResult get(InputStream stream, String mimeType) {
                return new DownloadResult(stream, mimeType);
            }

        }
    }

    public static IntentBuilder with(Context context) {
        return new IntentBuilder(context, MediaPickerActivity.class);
    }

    @SuppressWarnings("WeakerAccess")
    @StringDef({SOURCE_CAMERA, SOURCE_CAMCORDER, SOURCE_GALLERY, SOURCE_CLIPBOARD})
    @Retention(RetentionPolicy.SOURCE)
    public @interface PickSource {
    }

    @SuppressWarnings("unused,WeakerAccess,UnusedReturnValue")
    public static final class IntentBuilder {
        private final Intent intent;
        private final ArrayList<ExtraEntry> extraEntries;

        public IntentBuilder(final Context context, final Class<? extends MediaPickerActivity> cls) {
            this.intent = new Intent(context, cls);
            cropImageActivityClass(CropImageActivity.class);
            streamDownloaderClass(URLConnectionNetworkStreamDownloader.class);
            extraEntries = new ArrayList<>();
        }

        public IntentBuilder takePhoto() {
            intent.setAction(INTENT_ACTION_TAKE_PHOTO);
            return this;
        }

        public IntentBuilder captureVideo() {
            intent.setAction(INTENT_ACTION_CAPTURE_VIDEO);
            return this;
        }

        @SuppressWarnings("deprecation")
        @Deprecated
        public IntentBuilder pickImage() {
            intent.setAction(INTENT_ACTION_PICK_IMAGE);
            return this;
        }

        @SuppressWarnings("deprecation")
        @Deprecated
        public IntentBuilder getImage(@NonNull Uri uri) {
            intent.setAction(INTENT_ACTION_GET_IMAGE);
            intent.setData(uri);
            return this;
        }


        public IntentBuilder pickMedia() {
            intent.setAction(INTENT_ACTION_PICK_MEDIA);
            return this;
        }

        public IntentBuilder containsVideo(boolean containsVideo) {
            intent.putExtra(EXTRA_CONTAINS_VIDEO, containsVideo);
            return this;
        }

        public IntentBuilder videoOnly(boolean videoOnly) {
            intent.putExtra(EXTRA_VIDEO_ONLY, videoOnly);
            return this;
        }

        public IntentBuilder allowMultiple(boolean allowMultiple) {
            intent.putExtra(EXTRA_ALLOW_MULTIPLE, allowMultiple);
            return this;
        }

        public IntentBuilder localOnly(boolean localOnly) {
            intent.putExtra(EXTRA_LOCAL_ONLY, localOnly);
            return this;
        }

        public IntentBuilder videoQuality(int videoQuality) {
            intent.putExtra(EXTRA_VIDEO_QUALITY, videoQuality);
            return this;
        }

        public IntentBuilder getMedia(@NonNull Uri uri) {
            intent.setAction(INTENT_ACTION_GET_MEDIA);
            intent.setData(uri);
            return this;
        }

        public IntentBuilder aspectRatio(int x, int y) {
            intent.putExtra(EXTRA_ASPECT_X, x);
            intent.putExtra(EXTRA_ASPECT_Y, y);
            return this;
        }

        public IntentBuilder maximumSize(int w, int h) {
            intent.putExtra(EXTRA_MAX_WIDTH, w);
            intent.putExtra(EXTRA_MAX_HEIGHT, h);
            return this;
        }

        public IntentBuilder addEntry(final String name, final String value, final int result) {
            extraEntries.add(new ExtraEntry(name, value, result));
            return this;
        }

        public IntentBuilder pickSources(@PickSource String[] pickSources) {
            intent.putExtra(EXTRA_PICK_SOURCES, pickSources);
            return this;
        }

        public IntentBuilder extras(Bundle extras) {
            intent.putExtra(EXTRA_EXTRAS, extras);
            return this;
        }

        public IntentBuilder outputFormat(Bitmap.CompressFormat format) {
            intent.putExtra(EXTRA_OUTPUT_FORMAT, format != null ? format.name() : null);
            return this;
        }

        public IntentBuilder outputQuality(int quality) {
            intent.putExtra(EXTRA_OUTPUT_QUALITY, quality);
            return this;
        }

        public IntentBuilder cropImageActivityClass(Class<? extends Activity> cls) {
            intent.putExtra(EXTRA_CROP_ACTIVITY_CLASS, cls.getName());
            return this;
        }

        public IntentBuilder streamDownloaderClass(Class<? extends NetworkStreamDownloader> cls) {
            intent.putExtra(EXTRA_STREAM_DOWNLOADER_CLASS, cls.getName());
            return this;
        }

        public Intent build() {
            intent.putParcelableArrayListExtra(EXTRA_EXTRA_ENTRIES, extraEntries);
            return intent;
        }
    }

    private static class CopyMediaTask extends AsyncTask<Object, Object, Pair<CopyResult[], Exception>> {
        private static final String TAG_COPYING_IMAGE = "copying_media";
        private final WeakReference<MediaPickerActivity> mActivityRef;
        private final Uri[] mSourceUris;
        private final boolean mNeedsCrop;
        private final boolean mDeleteSource;

        CopyMediaTask(final MediaPickerActivity activity, final Uri[] sourceUris,
                      final boolean needsCrop, final boolean deleteSource) {
            mActivityRef = new WeakReference<>(activity);
            mSourceUris = sourceUris;
            mNeedsCrop = needsCrop;
            mDeleteSource = deleteSource;
        }

        @Override
        protected Pair<CopyResult[], Exception> doInBackground(final Object... params) {
            final Context context = mActivityRef.get();
            if (context == null) {
                return Pair.<CopyResult[], Exception>create(null, new InterruptedException());
            }
            final ContentResolver cr = context.getContentResolver();
            CopyResult[] copyResults = new CopyResult[mSourceUris.length];
            for (int i = 0, j = mSourceUris.length; i < j; i++) {
                Uri src = mSourceUris[i];
                try {
                    copyResults[i] = copyMedia(cr, src);
                } catch (IOException e) {
                    return Pair.<CopyResult[], Exception>create(null, e);
                } catch (SecurityException e) {
                    return Pair.<CopyResult[], Exception>create(null, e);
                } catch (InterruptedException e) {
                    return Pair.<CopyResult[], Exception>create(null, e);
                }
            }
            return Pair.create(copyResults, null);
        }

        private CopyResult copyMedia(@NonNull final ContentResolver cr, @NonNull final Uri src)
                throws IOException, InterruptedException {
            final MediaPickerActivity activity = mActivityRef.get();
            if (activity == null) {
                throw new InterruptedException();
            }
            InputStream is = null;
            OutputStream os = null;
            try {
                final String mimeType;
                final String scheme = src.getScheme();
                if (SCHEME_HTTP.equals(scheme) || SCHEME_HTTPS.equals(scheme)) {
                    final NetworkStreamDownloader downloader = activity.createNetworkStreamDownloader();
                    final NetworkStreamDownloader.DownloadResult result = downloader.get(src);
                    is = result.stream;
                    mimeType = result.mimeType;
                } else if (SCHEME_DATA.equals(scheme)) {
                    final DataUri dataUri = DataUri.parse(src.toString(), Charset.defaultCharset());
                    is = new ByteArrayInputStream(dataUri.getData());
                    mimeType = dataUri.getMime();
                } else {
                    is = cr.openInputStream(src);
                    mimeType = getMediaMimeType(src);
                }
                if (is == null) throw new IOException("InputStream is null");
                final String extension = mimeType != null ? MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) : null;
                final Uri targetUri = activity.createTempMediaUri(extension);
                os = activity.getContentResolver().openOutputStream(targetUri);
                if (os == null) throw new IOException("OutputStream is null");
                PNCUtils.copyStream(is, os);
                if (mDeleteSource) {
                    try {
                        PNCUtils.deleteMedia(activity, src);
                    } catch (SecurityException e) {
                        Log.w(LOGTAG, "WRITE_EXTERNAL_STORAGE permission is needed for deleting media", e);
                    }
                }
                return new CopyResult(targetUri, mimeType, extension);
            } finally {
                PNCUtils.closeSilently(os);
                PNCUtils.closeSilently(is);
            }
        }

        @Override
        protected void onPreExecute() {
            final MediaPickerActivity mActivity = mActivityRef.get();
            if (mActivity == null) {
                return;
            }
            final ProgressDialogFragment f = ProgressDialogFragment.show(mActivity, TAG_COPYING_IMAGE);
            f.setCancelable(false);
        }

        @Override
        protected void onPostExecute(final Pair<CopyResult[], Exception> result) {
            final MediaPickerActivity mActivity = mActivityRef.get();
            if (mActivity == null) {
                return;
            }
            mActivity.dismissProgressDialog(TAG_COPYING_IMAGE);
            if (result.first != null) {
                CopyResult[] copyResults = result.first;
                final Intent callingIntent = mActivity.getIntent();
                final boolean supportsCrop = copyResults.length == 1;
                final boolean hasCropParameters = callingIntent.hasExtra(EXTRA_ASPECT_X) && callingIntent.hasExtra(EXTRA_ASPECT_Y)
                        || callingIntent.hasExtra(EXTRA_MAX_WIDTH) && callingIntent.hasExtra(EXTRA_MAX_HEIGHT);
                if (supportsCrop && mNeedsCrop && hasCropParameters) {
                    final Uri tempImageUri;
                    try {
                        tempImageUri = mActivity.createTempMediaUri(copyResults[0].extension);
                    } catch (IOException e) {
                        Toast.makeText(mActivity, R.string.pnc__error_cannot_open_file, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CropImage.ActivityBuilder crop = CropImage.activity(copyResults[0].uri);
                    crop.setAutoZoomEnabled(true);
                    crop.setMultiTouchEnabled(true);
                    crop.setOutputUri(tempImageUri);
                    final int aspectX = callingIntent.getIntExtra(EXTRA_ASPECT_X, -1);
                    final int aspectY = callingIntent.getIntExtra(EXTRA_ASPECT_Y, -1);
                    if (aspectX > 0 && aspectY > 0) {
                        crop.setAspectRatio(aspectX, aspectY);
                    }
                    final int maxWidth = callingIntent.getIntExtra(EXTRA_MAX_WIDTH, -1);
                    final int maxHeight = callingIntent.getIntExtra(EXTRA_MAX_HEIGHT, -1);
                    if (maxWidth > 0 && maxHeight > 0) {
                        crop.setRequestedSize(maxWidth, maxHeight, RequestSizeOptions.RESIZE_FIT);
                    }
                    final String outputFormat = callingIntent.getStringExtra(EXTRA_OUTPUT_FORMAT);
                    if (outputFormat != null) {
                        crop.setOutputCompressFormat(Bitmap.CompressFormat.valueOf(outputFormat));
                    }
                    final int outputQuality = callingIntent.getIntExtra(EXTRA_OUTPUT_QUALITY, -1);
                    if (outputQuality >= 0) {
                        crop.setOutputCompressQuality(outputQuality);
                    }
                    Class<?> activityClass;
                    try {
                        activityClass = Class.forName(callingIntent.getStringExtra(EXTRA_CROP_ACTIVITY_CLASS));
                    } catch (ClassNotFoundException e) {
                        activityClass = null;
                    }
                    mActivity.startActivityForResult(crop.getIntent(mActivity, activityClass), REQUEST_CROP);
                    return;
                }
                final Intent data = new Intent();
                data.setData(copyResults[0].uri);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clipData = ClipData.newUri(mActivity.getContentResolver(),
                            mActivity.getString(R.string.pnc__label_media), copyResults[0].uri);
                    for (int i = 1, j = copyResults.length; i < j; i++) {
                        Uri dstUri = copyResults[i].uri;
                        clipData.addItem(new ClipData.Item(dstUri));
                    }
                    data.setClipData(clipData);
                }
                data.putExtra(EXTRA_EXTRAS, mActivity.getIntent().getBundleExtra(EXTRA_EXTRAS));
                mActivity.setResult(RESULT_OK, data);
            } else if (result.second != null) {
                Log.w(LOGTAG, result.second);
                Toast.makeText(mActivity, R.string.pnc__error_cannot_open_file, Toast.LENGTH_SHORT).show();
            }
            mActivity.finish();
        }

        private String getMediaMimeType(Uri src) throws InterruptedException {
            final MediaPickerActivity mActivity = mActivityRef.get();
            if (mActivity == null) {
                throw new InterruptedException();
            }
            InputStream is = null;
            try {
                final ContentResolver cr = mActivity.getContentResolver();
                String uriType = cr.getType(src);
                if (uriType != null) {
                    return uriType;
                }
                is = cr.openInputStream(src);
                final BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, opts);
                return opts.outMimeType;
            } catch (IOException e) {
                return null;
            } finally {
                PNCUtils.closeSilently(is);
            }
        }
    }

    private static class CopyResult {
        Uri uri;
        String mimeType;
        String extension;

        CopyResult(Uri uri, String mimeType, String extension) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.extension = extension;
        }
    }

    private static class ActivityResult {

        final boolean needsCrop, deleteSource;
        @NonNull
        final Uri[] src;

        ActivityResult(final boolean needsCrop, final boolean deleteSource, @NonNull final Uri[] src) {
            this.needsCrop = needsCrop;
            this.deleteSource = deleteSource;
            this.src = src;
        }
    }

}
