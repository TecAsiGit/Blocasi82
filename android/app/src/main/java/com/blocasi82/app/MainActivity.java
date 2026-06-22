package com.blocasi82.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.webkit.JavascriptInterface;
import com.getcapacitor.BridgeActivity;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        // Inyectar interfaz nativa después de un retraso
        new android.os.Handler().postDelayed(() -> {
            if (bridge != null && bridge.getWebView() != null) {
                bridge.getWebView().addJavascriptInterface(new NativeInterface(), "NativeInterface");
            }
        }, 500);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) {
                readAndSendFileContent(uri);
            }
        }
    }

    private void readAndSendFileContent(Uri uri) {
        try {
            String fileName = "Archivo";
            String[] projection = { android.provider.OpenableColumns.DISPLAY_NAME };
            android.content.CursorLoader cursorLoader = new android.content.CursorLoader(this, uri, projection, null, null, null);
            android.database.Cursor cursor = cursorLoader.loadInBackground();
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
                cursor.close();
            }

            InputStream inputStream = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                byteBuffer.write(buffer, 0, len);
            }
            inputStream.close();
            final String content = byteBuffer.toString("UTF-8");
            final String finalFileName = fileName;

            if (bridge != null && bridge.getWebView() != null) {
                bridge.getWebView().post(() -> {
                    String escapedContent = content
                        .replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
                    String js = String.format("javascript:window.loadExternalContent('%s', '%s')",
                        finalFileName.replace("'", "\\'"),
                        escapedContent);
                    bridge.getWebView().loadUrl(js);
                });
            }
        } catch (Exception e) {
            Log.e("Blocasi82", "Error leyendo archivo", e);
        }
    }

    public class NativeInterface {
        @JavascriptInterface
        public void share(String title, String text) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(shareIntent, "Compartir vía"));
        }

        @JavascriptInterface
        public void saveFile(String fileName, String content) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                OutputStream os = new FileOutputStream(file);
                os.write(content.getBytes("UTF-8"));
                os.close();
                Log.i("Blocasi82", "Archivo guardado: " + file.getAbsolutePath());
            } catch (Exception e) {
                Log.e("Blocasi82", "Error guardando archivo", e);
            }
        }
    }
}
