package org.mariotaku.pickncrop.library;

import android.os.Parcel;
import android.os.Parcelable;

import com.hannesdorfmann.parcelableplease.annotation.ParcelablePlease;
import com.hannesdorfmann.parcelableplease.annotation.ParcelableThisPlease;

/**
 * Created by mariotaku on 15/6/18.
 */
@ParcelablePlease
public final class ExtraEntry implements Parcelable {

    public static final Creator<ExtraEntry> CREATOR = new Creator<ExtraEntry>() {
        @Override
        public ExtraEntry createFromParcel(final Parcel source) {
            return new ExtraEntry(source);
        }

        @Override
        public ExtraEntry[] newArray(final int size) {
            return new ExtraEntry[0];
        }
    };

    @ParcelableThisPlease
    public String name;
    @ParcelableThisPlease
    public String value;
    @ParcelableThisPlease
    public int result;

    public ExtraEntry(final String name, final String value, final int result) {
        this.name = name;
        this.value = value;
        this.result = result;
    }

    public ExtraEntry(final Parcel source) {
        ExtraEntryParcelablePlease.readFromParcel(this, source);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        ExtraEntryParcelablePlease.writeToParcel(this, dest, flags);
    }
}
