package com.racks.parentalcontrol.parent.fragments;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.racks.parentalcontrol.parent.R;
import com.racks.parentalcontrol.parent.databinding.FragmentFullScreenSnapBinding;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class FullScreenSnapFragment extends Fragment {

    private FragmentFullScreenSnapBinding binding;
    private String snap_url;
    private boolean isDownloading = false;


    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentFullScreenSnapBinding.inflate(inflater, container, false);
        if (getArguments()!=null){
            FullScreenSnapFragmentArgs args = FullScreenSnapFragmentArgs.fromBundle(getArguments());
            snap_url = args.getSnapUrl();
        }
        binding.imgFullSnap.setZoomable(false);
        binding.tvLoadingSnap.setVisibility(View.VISIBLE);

        Glide.with(requireContext())
                .load(snap_url)
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .placeholder(R.drawable.ic_image_placeholder)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                        binding.imgFullSnap.setImageDrawable(resource);

                        binding.tvLoadingSnap.setVisibility(View.GONE);
                        binding.imgFullSnap.setZoomable(true);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        binding.tvLoadingSnap.setVisibility(View.GONE);
                    }
                });


        binding.imgSnapDownloadBtn.setOnClickListener(view -> {
            if (isDownloading) return;
            isDownloading = true;
            binding.imgSnapDownloadBtn.setEnabled(false);
            Toast.makeText(requireContext(), "Downloading please wait...", Toast.LENGTH_SHORT).show();
            downloadImageToDownloads(requireContext(), snap_url);
        });
        return binding.getRoot();
    }

    public void downloadImageToDownloads(Context context, String imageUrl) {
        Glide.with(context)
                .asBitmap()
                .load(imageUrl)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        saveToDownloads(context, resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void saveToDownloads(Context context, Bitmap bitmap) {
        String fileName = "IMG_" + System.currentTimeMillis() + ".jpg";
        OutputStream fos;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    Toast.makeText(context, "Failed to create file URI", Toast.LENGTH_SHORT).show();
                    return;
                }
                fos = context.getContentResolver().openOutputStream(uri);
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File image = new File(downloadsDir, fileName);
                fos = new FileOutputStream(image);

                Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                scanIntent.setData(Uri.fromFile(image));
                context.sendBroadcast(scanIntent);
            }

            if (fos == null) {
                Toast.makeText(context, "Output stream is null", Toast.LENGTH_SHORT).show();
                return;
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();

            Toast.makeText(context, "Image saved to Downloads", Toast.LENGTH_SHORT).show();
            resetDownloadButton();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show();
            resetDownloadButton();
        }
    }
    private void resetDownloadButton() {
        isDownloading = false;
        if (binding != null) {
            binding.imgSnapDownloadBtn.setEnabled(true);
        }
    }


}