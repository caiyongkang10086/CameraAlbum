package com.cyk.cameraphoto;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;

public class MainActivity extends AppCompatActivity {
    public static final int CHOOSE_PHOTO = 2;
    private static final String TAG = "MainActivity";

    private int takePhoto = 1;
    private Uri imageUri;
    private File outputImage;
    private Button btnTakePhoto;
    private Button choosePhoto;
    private ImageView imageView;
    private ActivityResultLauncher<Intent> launcher;
    private ActivityResultLauncher<Intent> albumLauncher;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //获取组件：Button、ImageView
        btnTakePhoto = findViewById(R.id.takePhoto);
        imageView = findViewById(R.id.image);
        choosePhoto = findViewById(R.id.choosePhotofromAlbum);
        albumLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            assert result.getData() != null;
                            handleImageOnKitKat(result.getData());
                        }
                    }
                });
        launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == RESULT_OK) {
                            try {
                                //Bitmap 用来描述一张图片的长、宽、颜色等信息
                                Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver()
                                        .openInputStream(imageUri));
                                //View设置Bitmap
                                imageView.setImageBitmap(bitmap);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                });
        //  拍照按钮
        btnTakePhoto.setOnClickListener(view -> {
            //  创建File对象，用于存储拍照后的图片,命名为output_image.jdp
            //  存放在手机SD卡的应用关联缓存目录下
            outputImage = new File(getExternalCacheDir(), "output_image.jpg");
            if (outputImage.exists()) {
                outputImage.delete();
            }
            try {
                outputImage.createNewFile();
                //  如果运行设备的系统高于Android 7.0
                //  就调用FileProvider的getUriForFile()方法将File对象转换成一个封装过的Uri对象。
                //  该方法接收3个参数：Context对象， 任意唯一的字符串， 创建的File对象。
                //  这样做的原因：Android 7.0 开始，直接使用本地真实路径的Uri是被认为是不安全的，会抛出FileUriExposedException异常；
                //      而FileProvider是一种特殊的ContentProvider，他使用了和ContentProvider类似的机制对数据进行保护，可以选择性地将封装过的Uri共享给外部。
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    imageUri = FileProvider.getUriForFile(this, "com.example.permissiontest.fileprovider", outputImage);
                } else {
                    //  否则，就调用Uri的fromFile()方法将File对象转换成Uri对象
                    imageUri = Uri.fromFile(outputImage);
                }
                //  启动相机
                Intent intent = new Intent("android.media.action.IMAGE_CAPTURE");
                //  指定图片的输出地址,这样拍下的照片会被输出到output_image.jpg中。
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                launcher.launch(intent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        choosePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "申请权限");
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE }, CHOOSE_PHOTO);
                } else {
                    openAlum();
                }
            }
        });
    }

    private void openAlum() {
        Log.d(TAG, "openAlum");
        Intent intent = new Intent("android.intent.action.GET_CONTENT");
        intent.setType("image/*");
        albumLauncher.launch(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CHOOSE_PHOTO:
                if (grantResults.length > 0 && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED) {
                    openAlum();
                } else {
                    Toast.makeText(this, "You denied the permission", Toast.LENGTH_SHORT).show();
                }
                break;
            default:
        }
    }

    private void handleImageOnKitKat(Intent data) {
        String imagePath = null;
        Uri uri = data.getData();
        if (DocumentsContract.isDocumentUri(this, uri)) {
            String docId = DocumentsContract.getDocumentId(uri);
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"),
                        Long.valueOf(docId));
                imagePath = getImagePath(contentUri, null);
            }
        } else if("content".equalsIgnoreCase(uri.getScheme())) {
            imagePath = getImagePath(uri, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            imagePath = uri.getPath();
        }
        displayImage(imagePath);
    }

    private void handleImageBeforeKitKat(Intent data) {
        Uri uri = data.getData();
        String imagePath = getImagePath(uri, null);
        displayImage(imagePath);
    }

    @SuppressLint("Range")
    private String getImagePath(Uri uri, String selection) {
        String path = null;
        Cursor cursor = getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
            }
            cursor.close();
        }
        return path;
    }

    private void displayImage(String imagePath) {
        if (imagePath != null) {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            imageView.setImageBitmap(bitmap);
        } else {
            Toast.makeText(this, "failed to get image", Toast.LENGTH_SHORT).show();
        }
    }
}


