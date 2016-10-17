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

package org.mariotaku.pickncrop;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import org.mariotaku.pickncrop.library.MediaPickerActivity;


public class MainActivity extends Activity {

    private static final int REQUEST_PICK_MEDIA = 101;
    private ImageView mImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.pick_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickImage();
            }
        });
        findViewById(R.id.take_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
    }

    @Override
    public void onContentChanged() {
        super.onContentChanged();
        mImageView = (ImageView) findViewById(R.id.image);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_PICK_MEDIA: {
                if (resultCode == RESULT_OK) {
                    mImageView.setImageURI(data.getData());
                }
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void takePhoto() {
        startActivityForResult(MediaPickerActivity.with(this).takePhoto().build(), REQUEST_PICK_MEDIA);
    }

    private void pickImage() {
        startActivityForResult(MediaPickerActivity.with(this).pickImage().build(), REQUEST_PICK_MEDIA);
    }

}
