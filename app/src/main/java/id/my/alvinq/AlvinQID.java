package id.my.alvinq;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.widget.Toast;

public class AlvinQID {

    private static final int PERMISSION_REQUEST_CODE = 100;

    public static void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (R) dan lebih tinggi
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s",getApplicationContext().getPackageName())));
                    startActivityForResult(intent, 2296);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getApplicationContext().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            } else {
                // Izin sudah diberikan
                Toast.makeText(this, "Izin akses semua file diberikan", Toast.LENGTH_SHORT).show();
                // Lanjutkan dengan operasi file
            }
        } else {
            // Android 10 dan lebih rendah
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            } else {
                // Izin sudah diberikan
                Toast.makeText(this, "Izin akses penyimpanan diberikan", Toast.LENGTH_SHORT).show();
                // Lanjutkan dengan operasi file
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2296) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // Izin diberikan
                    Toast.makeText(this, "Izin akses semua file diberikan", Toast.LENGTH_SHORT).show();
                    // Lanjutkan dengan operasi file
                } else {
                    Toast.makeText(this, "Izin akses semua file ditolak", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Izin diberikan
                Toast.makeText(this, "Izin akses penyimpanan diberikan", Toast.LENGTH_SHORT).show();
                // Lanjutkan dengan operasi file
            } else {
                Toast.makeText(this, "Izin akses penyimpanan ditolak", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
