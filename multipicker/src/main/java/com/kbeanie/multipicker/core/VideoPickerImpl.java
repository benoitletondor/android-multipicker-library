package com.kbeanie.multipicker.core;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.kbeanie.multipicker.api.Picker;
import com.kbeanie.multipicker.api.callbacks.VideoPickerCallback;
import com.kbeanie.multipicker.api.entity.ChosenVideo;
import com.kbeanie.multipicker.api.exceptions.PickerException;
import com.kbeanie.multipicker.core.threads.VideoProcessorThread;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by kbibek on 2/18/16.
 */
public abstract class VideoPickerImpl extends PickerManager {
    private final static String TAG = VideoPickerImpl.class.getSimpleName();

    private String path;

    private VideoPickerCallback callback;
    private boolean generatePreviewImages = true;
    private boolean generateMetadata = true;

    public VideoPickerImpl(Activity activity, int pickerType) {
        super(activity, pickerType);
    }

    public VideoPickerImpl(Fragment fragment, int pickerType) {
        super(fragment, pickerType);
    }

    public VideoPickerImpl(android.app.Fragment appFragment, int pickerType) {
        super(appFragment, pickerType);
    }

    public void reinitialize(String path) {
        this.path = path;
    }

    public void setVideoPickerCallback(VideoPickerCallback callback) {
        this.callback = callback;
    }

    public void shouldGeneratePreviewImages(boolean generatePreviewImages) {
        this.generatePreviewImages = generatePreviewImages;
    }

    public void shouldGenerateMetadata(boolean generateMetadata) {
        this.generateMetadata = generateMetadata;
    }

    @Override
    protected String pick() throws PickerException {
        if (callback == null) {
            throw new PickerException("VideoPickerCallback null!!! Please set one");
        }
        if (pickerType == Picker.PICK_VIDEO_DEVICE) {
            return pickLocalVideo();
        } else if (pickerType == Picker.PICK_VIDEO_CAMERA) {
            path = takeVideoWithCamera();
            return path;
        }
        return null;
    }

    protected String takeVideoWithCamera() {
        String tempFilePath = buildFilePath("mp4", Environment.DIRECTORY_MOVIES);
        Uri uri = Uri.fromFile(new File(tempFilePath));
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        if (extras != null) {
            intent.putExtras(extras);
        }
        Log.d(TAG, "Temp Path for Camera capture: " + tempFilePath);
        pickInternal(intent, Picker.PICK_VIDEO_CAMERA);
        return tempFilePath;
    }

    protected String pickLocalVideo() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("video/*");
        if (extras != null) {
            intent.putExtras(extras);
        }
        // For reading from external storage (Content Providers)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        pickInternal(intent, Picker.PICK_VIDEO_DEVICE);
        return null;
    }

    @Override
    public void submit(int requestCode, int resultCode, Intent data) {
        if (requestCode != pickerType) {
            onError("onActivityResult requestCode is different from the type the chooser was initialized with.");
        } else {
            if (pickerType == Picker.PICK_VIDEO_CAMERA) {
                handleCameraData();
            } else if (pickerType == Picker.PICK_VIDEO_DEVICE) {
                handleGalleryData(data);
            }
        }
    }

    private void handleCameraData() {
        Log.d(TAG, "handleCameraData: " + path);
        if (path == null || path.isEmpty()) {
            onError("Path is null. You will need to re-initialize the object with proper path");
        } else {
            List<String> uris = new ArrayList<>();
            uris.add(Uri.fromFile(new File(path)).toString());
            processVideos(uris);
        }
    }

    private void handleGalleryData(Intent intent) {
        List<String> uris = new ArrayList<>();
        if (intent != null) {
            if (intent.getDataString() != null) {
                String uri = intent.getDataString();
                Log.d(TAG, "handleGalleryData: " + uri);
                uris.add(uri);
            } else if (intent.getClipData() != null) {
                ClipData clipData = intent.getClipData();
                Log.d(TAG, "handleGalleryData: Multiple videos with ClipData");
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    ClipData.Item item = clipData.getItemAt(i);
                    Log.d(TAG, "Item [" + i + "]: " + item.getUri().toString());
                    uris.add(item.getUri().toString());
                }
            }

            processVideos(uris);
        }
    }

    private void processVideos(List<String> uris) {
        VideoProcessorThread thread = new VideoProcessorThread(getContext(), getVideoObjects(uris), cacheLocation);
        thread.setShouldGeneratePreviewImages(generatePreviewImages);
        thread.setShouldGenerateMetadata(generateMetadata);
        thread.setVideoPickerCallback(callback);
        thread.start();
    }

    private List<ChosenVideo> getVideoObjects(List<String> uris) {
        List<ChosenVideo> videos = new ArrayList<>();
        for (String uri : uris) {
            ChosenVideo video = new ChosenVideo();
            video.setQueryUri(uri);
            video.setDirectoryType(Environment.DIRECTORY_MOVIES);
            video.setType("video");
            videos.add(video);
        }
        return videos;
    }

    private void onError(final String errorMessage) {
        if (callback != null) {
            ((Activity) getContext()).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onError(errorMessage);
                }
            });
        }
    }
}