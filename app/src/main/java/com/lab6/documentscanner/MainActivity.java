package com.lab6.documentscanner;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int IMAGE_REQUEST = 1;

    private ImageView imagePreview;
    private TextView emptyText;
    private Bitmap originalBitmap;
    private Bitmap currentBitmap;
    private final DocumentFilter documentFilter = new DocumentFilter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imagePreview = findViewById(R.id.imagePreview);
        emptyText = findViewById(R.id.emptyText);
        Button btnLoad = findViewById(R.id.btnLoad);
        Button btnShadow = findViewById(R.id.btnShadow);
        Button btnGray = findViewById(R.id.btnGray);
        Button btnBw = findViewById(R.id.btnBw);
        Button btnReset = findViewById(R.id.btnReset);
        Button btnSave = findViewById(R.id.btnSave);

        btnLoad.setOnClickListener(v -> openGallery());
        btnShadow.setOnClickListener(v -> applyFilter(() ->
                documentFilter.removeShadow(currentBitmap, this::showBitmap)));
        btnGray.setOnClickListener(v -> applyFilter(() ->
                documentFilter.toGray(currentBitmap, this::showBitmap)));
        btnBw.setOnClickListener(v -> applyFilter(() ->
                documentFilter.toBlackWhite(currentBitmap, this::showBitmap)));
        btnReset.setOnClickListener(v -> {
            if (originalBitmap != null) {
                showBitmap(originalBitmap);
            }
        });
        btnSave.setOnClickListener(v -> saveCurrentImage());
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn ảnh tài liệu"), IMAGE_REQUEST);
    }

    private void applyFilter(Runnable runnable) {
        if (currentBitmap == null) {
            Toast.makeText(this, "Hãy chọn ảnh trước", Toast.LENGTH_SHORT).show();
            return;
        }
        emptyText.setVisibility(View.VISIBLE);
        emptyText.setText("Đang xử lý...");
        runnable.run();
    }

    private void showBitmap(Bitmap bitmap) {
        currentBitmap = bitmap;
        imagePreview.setImageBitmap(bitmap);
        emptyText.setVisibility(View.GONE);
    }

    private void saveCurrentImage() {
        if (currentBitmap == null) {
            Toast.makeText(this, "Chưa có ảnh để lưu", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = "lab6_scan_" + System.currentTimeMillis() + ".jpg";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/Lab6Scanner");

        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            Toast.makeText(this, "Không tạo được file ảnh", Toast.LENGTH_SHORT).show();
            return;
        }

        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            currentBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            Toast.makeText(this, "Đã lưu: Pictures/Lab6Scanner/" + name, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Lưu ảnh thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                return;
            }
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                showBitmap(originalBitmap);
            } catch (IOException e) {
                Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
