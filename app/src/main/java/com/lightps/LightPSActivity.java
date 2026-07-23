package com.lightps;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.lightps.engine.brush.Brush;
import com.lightps.engine.layer.PixelLayer;
import com.lightps.model.Document;
import com.lightps.ui.CanvasView;
import com.lightps.ui.LayersPanel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * 主编辑器 Activity - 全中文界面
 */
public class LightPSActivity extends Activity {

    private static final int REQUEST_OPEN = 100;
    private static final int REQUEST_SAVE = 101;

    private CanvasView canvasView;
    private LayersPanel layersPanel;
    private Document document;
    private Brush currentBrush;
    private boolean layersVisible = false;
    private boolean eraserMode = false;
    private boolean panMode = false;
    private Button eraserButton, panButton, layersButton;
    private TextView sizeLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        canvasView = findViewById(R.id.canvas);
        layersPanel = findViewById(R.id.layersPanel);
        sizeLabel = findViewById(R.id.sizeLabel);

        createNewDocument(1920, 1080);
        setupToolbar();
    }

    // ── 文档管理 ────────────────────────────────────────

    private void createNewDocument(int width, int height) {
        document = new Document(width, height);
        PixelLayer bg = new PixelLayer("Background", width, height);
        bg.setPixels(createSolidBitmap(width, height, Color.WHITE));
        document.getLayerManager().addLayer(bg);

        PixelLayer drawLayer = new PixelLayer("Layer 1", width, height);
        document.getLayerManager().addLayer(drawLayer);
        document.getLayerManager().setActiveLayer(drawLayer);

        canvasView.setLayerManager(document.getLayerManager());
        layersPanel.setLayerManager(document.getLayerManager());
        updateTitle();

        currentBrush = new Brush();
        currentBrush.setSize(20f);
        currentBrush.setHardness(0.8f);
        currentBrush.setColor(Color.BLACK);
        canvasView.setBrush(currentBrush);
        updateSizeLabel();
    }

    private void updateTitle() {
        String title = document.getTitle();
        if (document.isModified()) title += " *";
        setTitle(title);
    }

    private void updateSizeLabel() {
        if (sizeLabel != null && currentBrush != null) {
            sizeLabel.setText(String.valueOf((int) currentBrush.getSize()));
        }
    }

    // ── 工具栏 ──────────────────────────────────────────

    private void setupToolbar() {
        // 移动工具
        panButton = findViewById(R.id.btn_pan);
        panButton.setOnClickListener(v -> {
            panMode = !panMode;
            canvasView.setPanMode(panMode);
            if (panMode) {
                eraserMode = false;
                eraserButton.setText("橡皮");
                eraserButton.setBackgroundColor(Color.rgb(15, 52, 96));
            }
            panButton.setBackgroundColor(panMode ? Color.rgb(180, 120, 30) : Color.rgb(15, 52, 96));
            panButton.setText(panMode ? "画笔" : "移动");
        });

        // 橡皮
        eraserButton = findViewById(R.id.btn_eraser);
        eraserButton.setOnClickListener(v -> {
            eraserMode = !eraserMode;
            canvasView.setEraserMode(eraserMode);
            if (eraserMode) {
                panMode = false;
                panButton.setText("移动");
                panButton.setBackgroundColor(Color.rgb(15, 52, 96));
            }
            eraserButton.setText(eraserMode ? "画笔" : "橡皮");
            eraserButton.setBackgroundColor(eraserMode ? Color.rgb(180, 40, 40) : Color.rgb(15, 52, 96));
        });

        // 撤销
        findViewById(R.id.btn_undo).setOnClickListener(v -> {
            if (canvasView.undo()) {
                document.setModified(true);
                updateTitle();
            }
        });

        // 画笔大小
        findViewById(R.id.btn_size_up).setOnClickListener(v -> {
            if (currentBrush != null) {
                int step = currentBrush.getSize() < 50 ? 5 : 20;
                currentBrush.setSize(currentBrush.getSize() + step);
                updateSizeLabel();
            }
        });
        findViewById(R.id.btn_size_down).setOnClickListener(v -> {
            if (currentBrush != null) {
                int step = currentBrush.getSize() <= 50 ? 5 : 20;
                currentBrush.setSize(Math.max(1, currentBrush.getSize() - step));
                updateSizeLabel();
            }
        });

        // 颜色
        findViewById(R.id.btn_color).setOnClickListener(v -> showColorPicker());

        // 图层面板
        layersButton = findViewById(R.id.btn_layers);
        layersButton.setOnClickListener(v -> {
            layersVisible = !layersVisible;
            layersPanel.setVisibility(layersVisible ? View.VISIBLE : View.GONE);
            layersPanel.refresh();
        });

        findViewById(R.id.btn_new_layer).setOnClickListener(v -> {
            PixelLayer nl = new PixelLayer("图层 " + (document.getLayerManager().layerCount()),
                    document.getWidth(), document.getHeight());
            document.getLayerManager().addLayer(nl);
            document.getLayerManager().setActiveLayer(nl);
            canvasView.invalidateFlat();
            layersPanel.refresh();
            document.setModified(true);
            updateTitle();
        });

        findViewById(R.id.btn_del_layer).setOnClickListener(v -> {
            com.lightps.engine.layer.Layer active = document.getLayerManager().getActiveLayer();
            if (active != null && document.getLayerManager().layerCount() > 1) {
                document.getLayerManager().removeLayer(active);
                canvasView.invalidateFlat();
                layersPanel.refresh();
                document.setModified(true);
                updateTitle();
            }
        });

        // 打开 / 新建 / 保存
        findViewById(R.id.btn_open).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"image/png", "image/jpeg", "image/webp", "image/bmp", "application/octet-stream"});
            startActivityForResult(i, REQUEST_OPEN);
        });

        findViewById(R.id.btn_new).setOnClickListener(v -> showNewDocDialog());

        findViewById(R.id.btn_save).setOnClickListener(v -> {
            if (document.getFile() != null) {
                saveDocument();
            } else {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_TITLE, "drawing.psd");
                startActivityForResult(i, REQUEST_SAVE);
            }
        });
    }

    private void showNewDocDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建文档");
        View view = getLayoutInflater().inflate(R.layout.dialog_new_doc, null);
        builder.setView(view);
        builder.setPositiveButton("创建", (dialog, which) -> createNewDocument(1920, 1080));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showColorPicker() {
        final int[] colors = {
                Color.BLACK, Color.WHITE, Color.RED, Color.GREEN,
                Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA,
                Color.GRAY, Color.DKGRAY, 0xFF8B4513, 0xFFD2B48C
        };
        final String[] names = {
                "黑色", "白色", "红色", "绿色", "蓝色",
                "黄色", "青色", "品红", "灰色", "深灰", "棕色", "米色"
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择颜色");
        builder.setItems(names, (dialog, which) -> {
            if (currentBrush != null) currentBrush.setColor(colors[which]);
        });
        builder.show();
    }

    private void saveDocument() {
        try {
            document.save();
            document.setModified(false);
            updateTitle();
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "保存失败: " + e.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ── Activity 结果 ───────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            if (requestCode == REQUEST_OPEN) openFile(uri);
            else if (requestCode == REQUEST_SAVE) saveToUri(uri);
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "错误: " + e.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void openFile(Uri uri) throws Exception {
        String name = uri.getLastPathSegment();
        if (name != null && name.toLowerCase().endsWith(".psd")) {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return;
            FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
            com.lightps.io.psd.PSDLoader loader = new com.lightps.io.psd.PSDLoader();
            com.lightps.engine.layer.LayerManager lm = loader.load(fis);
            fis.close();
            pfd.close();
            document = new Document(lm.getWidth(), lm.getHeight());
            for (int i = 0; i < lm.layerCount(); i++)
                document.getLayerManager().addLayer(lm.getLayer(i));
        } else {
            Bitmap bmp = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            document = new Document(bmp.getWidth(), bmp.getHeight());
            PixelLayer layer = new PixelLayer("导入 " + (name != null ? name : "图片"), bmp);
            document.getLayerManager().addLayer(layer);
        }
        canvasView.setLayerManager(document.getLayerManager());
        layersPanel.setLayerManager(document.getLayerManager());
        layersPanel.refresh();
        updateTitle();
    }

    private void saveToUri(Uri uri) throws Exception {
        String name = uri.getLastPathSegment();
        if (name != null && name.toLowerCase().endsWith(".psd")) {
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
            if (pfd == null) return;
            FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
            com.lightps.io.psd.PSDSaver saver = new com.lightps.io.psd.PSDSaver();
            saver.save(document.getLayerManager(), fos);
            fos.close();
            pfd.close();
            document.setModified(false);
            updateTitle();
        } else {
            java.io.OutputStream out = getContentResolver().openOutputStream(uri);
            if (out != null) {
                Bitmap flat = document.getLayerManager().flatten();
                flat.compress(Bitmap.CompressFormat.PNG, 100, new java.io.BufferedOutputStream(out));
                out.close();
            }
        }
    }

    // ── 工具 ────────────────────────────────────────────

    private Bitmap createSolidBitmap(int w, int h, int color) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(color);
        return bmp;
    }
}
