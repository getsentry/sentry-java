package io.sentry.android.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Date;

import io.sentry.DateUtils;

@ApiStatus.Internal
public final class SentryPerformanceProvider extends ContentProvider {

    private static @NotNull final Date appStartTime = DateUtils.getCurrentDateTime();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        // applicationId is expected to be prepended. See AndroidManifest.xml
        if (SentryPerformanceProvider.class.getName().equals(info.authority)) {
            throw new IllegalStateException(
                    "An applicationId is required to fulfill the manifest placeholder.");
        }
        super.attachInfo(context, info);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    /**
     * Returns the App Start Up Time and if not yet initialized, the current Date and Time.
     * @return a clone of the App Start up time.
     */
    @SuppressWarnings("JavaUtilDate")
    @NotNull
    static Date getAppStartTime() {
        return (Date) appStartTime.clone();
    }
}
