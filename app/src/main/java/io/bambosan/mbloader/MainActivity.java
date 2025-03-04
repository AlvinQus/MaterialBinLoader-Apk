package io.bambosan.mbloader;

import org.jetbrains.annotations.NotNull;
import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.provider.Settings;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import android.os.Environment;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private static final String MC_PACKAGE_NAME = "com.mojang.minecraftpe";
    private static final int PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
	    
        setContentView(R.layout.activity_main);

	checkAndRequestPermissions();
        
        TextView listener = findViewById(R.id.listener);
        TextView mcPkgName = findViewById(R.id.mc_pkgname);
        Button  mbl2_button = findViewById(R.id.mbl2_load);
        Button draco_button = findViewById(R.id.draco_load);
	Button modmenu_button = findViewById(R.id.modmenu_load);
	Button modmenu_button2 = findViewById(R.id.modmenu2_load);

        Handler handler = new Handler(Looper.getMainLooper());
        mcPkgName.setText(MC_PACKAGE_NAME);
        mbl2_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            { 
                startLauncher(handler, listener, "launcher_mbl2.dex", mcPkgName.getText().toString());    
            }
        });
        
        draco_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {        
                startLauncher(handler, listener, "launcher_draco.dex", mcPkgName.getText().toString());    
            }
        });

	modmenu_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {        
                exportStorage(handler, listener);
            }
        });
	modmenu_button2.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {        
                importStorage(handler, listener);
            }
        });
}
    private void startLauncher(Handler handler, TextView listener, String launcherDexName, String mcPackageName) {    
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File cacheDexDir = new File(getCodeCacheDir(), "dex");
                handleCacheCleaning(cacheDexDir, handler, listener);
                ApplicationInfo mcInfo = getPackageManager().getApplicationInfo(mcPackageName, PackageManager.GET_META_DATA);
                Object pathList = getPathList(getClassLoader());
                processDexFiles(mcInfo, cacheDexDir, pathList, handler, listener, launcherDexName);
                processNativeLibraries(mcInfo, pathList, handler, listener);
                launchMinecraft(mcInfo);
            } catch (Exception e) {
                //Intent fallbackActivity = new Intent(this, Fallback.class);
                //handleException(e, fallbackActivity);
String logMessage = e.getCause() != null ? e.getCause().toString() : e.toString();                
            handler.post(() -> listener.setText("Launching failed: " + logMessage));                
            }
        });    
    }
    @SuppressLint("SetTextI18n")
    private void handleCacheCleaning(@NotNull File cacheDexDir, Handler handler, TextView listener) {
        if (cacheDexDir.exists() && cacheDexDir.isDirectory()) {
            handler.post(() -> listener.setText("-> " + cacheDexDir.getAbsolutePath() + " not empty, do cleaning"));
            for (File file : Objects.requireNonNull(cacheDexDir.listFiles())) {
                if (file.delete()) {
                    handler.post(() -> listener.append("\n-> " + file.getName() + " deleted"));
                }
            }
        } else {
            handler.post(() -> listener.setText("-> " + cacheDexDir.getAbsolutePath() + " is empty, skip cleaning"));
        }
    }

    private Object getPathList(@NotNull ClassLoader classLoader) throws Exception {
        Field pathListField = Objects.requireNonNull(classLoader.getClass().getSuperclass()).getDeclaredField("pathList");
        pathListField.setAccessible(true);
        return pathListField.get(classLoader);
    }

    private void processDexFiles(ApplicationInfo mcInfo, File cacheDexDir, @NotNull Object pathList, @NotNull Handler handler, TextView listener, String launcherDexName) throws Exception {
        Method addDexPath = pathList.getClass().getDeclaredMethod("addDexPath", String.class, File.class);
        File launcherDex = new File(cacheDexDir, launcherDexName);

        copyFile(getAssets().open(launcherDexName), launcherDex);
        handler.post(() -> listener.append("\n-> " + launcherDexName + " copied to " + launcherDex.getAbsolutePath()));

        if (launcherDex.setReadOnly()) {
            addDexPath.invoke(pathList, launcherDex.getAbsolutePath(), null);
            handler.post(() -> listener.append("\n-> " + launcherDexName + " added to dex path list"));
        }

        try (ZipFile zipFile = new ZipFile(mcInfo.sourceDir)) {
            for (int i = 10; i >= 0; i--) {
                String dexName = "classes" + (i == 0 ? "" : i) + ".dex";
                ZipEntry dexFile = zipFile.getEntry(dexName);
                if (dexFile != null) {
                    File mcDex = new File(cacheDexDir, dexName);
                    copyFile(zipFile.getInputStream(dexFile), mcDex);
                    handler.post(() -> listener.append("\n-> " + mcInfo.sourceDir + "/" + dexName + " copied to " + mcDex.getAbsolutePath()));
                    if (mcDex.setReadOnly()) {
                        addDexPath.invoke(pathList, mcDex.getAbsolutePath(), null);
                        handler.post(() -> listener.append("\n-> " + dexName + " added to dex path list"));
                    }
                }
            }
        }
    }

    private void processNativeLibraries(@NotNull ApplicationInfo mcInfo, @NotNull Object pathList, @NotNull Handler handler, TextView listener) throws Exception {
        Method addNativePath = pathList.getClass().getDeclaredMethod("addNativePath", Collection.class);
        ArrayList<String> libDirList = new ArrayList<>();
        File libdir = new File(mcInfo.nativeLibraryDir);
			  if (libdir.list() == null || libdir.list().length == 0 
			  || (mcInfo.flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) {
				    loadUnextractedLibs(mcInfo);
				    libDirList.add(getApplicationInfo().dataDir + "/libs/");
			  }
        libDirList.add(mcInfo.nativeLibraryDir);
        addNativePath.invoke(pathList, libDirList);
        handler.post(() -> listener.append("\n-> " + mcInfo.nativeLibraryDir + " added to native library directory path"));
    }
    private void loadUnextractedLibs(ApplicationInfo appInfo) throws Exception {
		    FileInputStream inStream = new FileInputStream(getApkWithLibs(appInfo));
		    BufferedInputStream bufInStream = new BufferedInputStream(inStream);
		    ZipInputStream inZipStream = new ZipInputStream(bufInStream);
		    String zipPath = "lib/" + Build.SUPPORTED_ABIS[0] + "/";
		    String outPath = getApplicationInfo().dataDir + "/libs/";
		    File dir = new File(outPath);
		    dir.mkdir();
		    extractDir(appInfo, inZipStream, zipPath, outPath);
	  }
	  public String getApkWithLibs(ApplicationInfo pkg) throws PackageManager.NameNotFoundException
	{
		// get installed split's Names
		String[] sn=pkg.splitSourceDirs;

		// check whether if it's really split or not
		if (sn != null && sn.length > 0)
		{
			String cur_abi = Build.SUPPORTED_ABIS[0].replace('-','_');
			// search installed splits
			for(String n:sn){
				
				 //check whether is the one required
				if(n.contains(cur_abi)){
				// yes, it's installed!
					return n;
				}
			}
		}
		// couldn't find!
		return pkg.sourceDir;
	}
	private static void extractDir(ApplicationInfo mcInfo, ZipInputStream zip, String zip_folder, String out_folder ) throws Exception{
        ZipEntry ze = null;
        while ((ze = zip.getNextEntry()) != null) {
            if (ze.getName().startsWith(zip_folder)) {
				String strippedName = ze.getName().substring(zip_folder.length());
				String path = out_folder + strippedName;
				OutputStream out = new FileOutputStream(path);
				BufferedOutputStream outBuf = new BufferedOutputStream(out);
                byte[] buffer = new byte[9000];
                int len;
                while ((len = zip.read(buffer)) != -1) {
                    outBuf.write(buffer, 0, len);
                }
                outBuf.close();
            }
        }
        zip.close();
    }
    private void launchMinecraft(@NotNull ApplicationInfo mcInfo) throws ClassNotFoundException {
        Class<?> launcherClass = getClassLoader().loadClass("com.mojang.minecraftpe.Launcher");
        Intent mcActivity = new Intent(this, launcherClass);
        mcActivity.putExtra("MC_SRC", mcInfo.sourceDir);

        if (mcInfo.splitSourceDirs != null) {
            ArrayList<String> listSrcSplit = new ArrayList<>();
            Collections.addAll(listSrcSplit, mcInfo.splitSourceDirs);
            mcActivity.putExtra("MC_SPLIT_SRC", listSrcSplit);
        }
        startActivity(mcActivity);
        finish();
    }

    private void handleException(@NotNull Exception e, @NotNull Intent fallbackActivity) {
        String logMessage = e.getCause() != null ? e.getCause().toString() : e.toString();
        fallbackActivity.putExtra("LOG_STR", logMessage);
        startActivity(fallbackActivity);
        finish();
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        } else {
            Toast.makeText(this, "Izin akses penyimpanan diberikan", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2296) {
            // Handle result from app settings
            Toast.makeText(this, "Izin akses semua file diberikan", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Izin akses semua file ditolak", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Izin akses penyimpanan diberikan", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Izin akses penyimpanan ditolak", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static void copyFile(InputStream from, @NotNull File to) throws IOException {
        File parentDir = to.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directories");
        }
        if (!to.exists() && !to.createNewFile()) {
            throw new IOException("Failed to create new file");
        }
        try (BufferedInputStream input = new BufferedInputStream(from);
             BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(to.toPath()))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }

public void copyFolder(File source, File destination) throws IOException {
        if (!source.exists()) {
             source.mkdirs();
        }
	if (!destination.exists()) {
             destination.mkdirs();
	}
	if (source.isDirectory()) {

            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    File sourceFile = new File(source, file);
                    File destinationFile = new File(destination, file);
                    copyFolder(sourceFile, destinationFile);
                }
            }
        } else {
            FileInputStream in = new FileInputStream(source);
            FileOutputStream out = new FileOutputStream(destination);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }

            in.close();
            out.close();
        }
}

private void importStorage(Handler handler, TextView listener) {
    File sourceFile = new File(Environment.getExternalStorageDirectory(), "games/io.bambosan.mbloader");
    File destinationFile = new File(Environment.getExternalStorageDirectory(), "Android/data/io.bambosan.mbloader");
    try {
        copyFolder(sourceFile, destinationFile);
	handler.post(() -> listener.append("\n-> Done"));
    } catch (IOException e) {
        e.printStackTrace();
	handler.post(() -> listener.append("\n-> " + e + " added to error"));
        // Penanganan error
    }
}

private void exportStorage(Handler handler, TextView listener) {
    File sourceFile = new File(Environment.getExternalStorageDirectory(), "Android/data/io.bambosan.mbloader");
    File destinationFile = new File(Environment.getExternalStorageDirectory(), "games/io.bambosan.mbloader");
    try {
        copyFolder(sourceFile, destinationFile);
	handler.post(() -> listener.append("\n-> Done"));
    } catch (IOException e) {
        e.printStackTrace();
	    handler.post(() -> listener.append("\n-> " + e + " added to error"));
        // Penanganan error
    }
}
}
