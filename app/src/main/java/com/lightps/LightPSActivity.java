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
 * Main editor activity for LightPS.
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
    private Button eraserButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        canvasView = findViewById(R.id.canvas);
        layersPanel = findViewById(R.id.layersPanel);

        createNewDocument(1920, 1080);
        setupToolbar();
    }

    // ── Document management ────────────────────────────

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
    }

    private void updateTitle() {
        String title = document.getTitle();
        if (document.isModified()) title += " *";
        setTitle(title);
    }

    // ── Toolbar ────────────────────────────────────────

    private void setupToolbar() {
        findViewById(R.id.btn_layers).setOnClickListener(v -> {
            layersVisible = !layersVisible;
            layersPanel.setVisibility(layersVisible ? View.VISIBLE : View.GONE);
            layersPanel.refresh();
        });

        findViewById(R.id.btn_new_layer).setOnClickListener(v -> {
            PixelLayer newLayer = new PixelLayer(
                    "Layer " + (document.getLayerManager().layerCount() + 1),
                    document.getWidth(), document.getHeight());
            document.getLayerManager().addLayer(newLayer);
            document.getLayerManager().setActiveLayer(newLayer);
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

        findViewById(R.id.btn_undo).setOnClickListener(v -> {
            if (canvasView.undo()) {
                document.setModified(true);
                updateTitle();
            }
        });

        // ── Eraser ──
        eraserButton = findViewById(R.id.btn_eraser);
        eraserButton.setOnClickListener(v -> {
            eraserMode = !eraserMode;
            canvasView.setEraserMode(eraserMode);
            eraserButton.setText(eraserMode ? "Brush" : "Erase");
            eraserButton.setBackgroundColor(eraserMode
                    ? Color.rgb(180, 40, 40)
                    : Color.rgb(15, 52, 96));
        });

        // ── Brush controls ──
        findViewById(R.id.btn_color).setOnClickListener(v -> showColorPicker());

        findViewById(R.id.btn_size_up).setOnClickListener(v -> {
            if (currentBrush != null) {
                int step = currentBrush.getSize() < 50 ? 5 : 20;
                currentBrush.setSize(currentBrush.getSize() + step);
            }
        });

        findViewById(R.id.btn_size_down).setOnClickListener(v -> {
            if (currentBrush != null) {
                int step = currentBrush.getSize() <= 50 ? 5 : 20;
                currentBrush.setSize(Math.max(1, currentBrush.getSize() - step));
            }
        });

        // ── Open / New / Save ──
        findViewById(R.id.btn_open).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            String[] mimeTypes = {"image/png", "image/jpeg", "image/webp", "image/bmp", "application/octet-stream"};
            i.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
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
        builder.setTitle("New Document");

        View view = getLayoutInflater().inflate(R.layout.dialog_new_doc, null);
        builder.setView(view);

        builder.setPositiveButton("Create", (dialog, which) -> {
            createNewDocument(1920, 1080);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showColorPicker() {
        final int[] colors = {
                Color.BLACK, Color.WHITE, Color.RED, Color.GREEN,
                Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA,
                Color.GRAY, Color.DKGRAY, 0xFF8B4513, 0xFFD2B48C
        };
        final String[] names = {
                "Black", "White", "Red", "Green", "Blue",
                "Yellow", "Cyan", "Magenta", "Gray", "Dark Gray",
                "Brown", "Tan"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pick Color");
        builder.setItems(names, (dialog, which) -> {
            if (currentBrush != null) {
                currentBrush.setColor(colors[which]);
            }
        });
        builder.show();
    }

    private void saveDocument() {
        try {
            document.save();
            document.setModified(false);
            updateTitle();
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Save failed: " + e.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ── Activity results ───────────────────────────────

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK || data == null) return;

        Uri uri = data.getData();
        if (uri == null) return;

        try {
            switch (requestCode) {
                case REQUEST_OPEN:
                    openFile(uri);
                    break;
                case REQUEST_SAVE:
                    saveToUri(uri);
                    break;
            }
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "Error: " + e.getMessage(),
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void openFile(Uri uri) throws Exception {
        String name = uri.getLastPathSegment();
        boolean isPsd = name != null && name.toLowerCase().endsWith(".psd");

        if (isPsd) {
            // Open as PSD
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) return;
            FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
            com.lightps.io.psd.PSDLoader loader = new com.lightps.io.psd.PSDLoader();
            com.lightps.engine.layer.LayerManager lm = loader.load(fis);
            fis.close();
            pfd.close();

            document = new Document(lm.getWidth(), lm.getHeight());
            // Copy layers
            for (int i = 0; i < lm.layerCount(); i++) {
                document.getLayerManager().addLayer(lm.getLayer(i));
            }
            // Save the URI for re-save
            document.setFile(new File(uri.getPath()));
        } else {
            // Open as bitmap (PNG, JPEG, etc.)
            Bitmap bmp = android.provider.MediaStore.Images.Media.getBitmap(
                    getContentResolver(), uri);
            document = new Document(bmp.getWidth(), bmp.getHeight());
            PixelLayer layer = new PixelLayer("Imported", bmp);
            document.getLayerManager().addLayer(layer);
        }

        canvasView.setLayerManager(document.getLayerManager());
        layersPanel.setLayerManager(document.getLayerManager());
        layersPanel.refresh();
        updateTitle();
    }

    private void saveToUri(Uri uri) throws Exception {
        String name = uri.getLastPathSegment();
        boolean isPsd = name != null && name.toLowerCase().endsWith(".psd");

        if (isPsd) {
            // Save as PSD
            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "w");
            if (pfd == null) return;
            FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
            com.lightps.io.psd.PSDSaver saver = new com.lightps.io.psd.PSDSaver();
            saver.save(document.getLayerManager(), fos);
            fos.close();
            pfd.close();
            document.setModified(false);
            document.setFile(new File(uri.getPath()));
            updateTitle();
        } else {
            // Export as PNG
            java.io.OutputStream out = getContentResolver().openOutputStream(uri);
            if (out != null) {
                Bitmap flat = document.getLayerManager().flatten();
                flat.compress(Bitmap.CompressFormat.PNG, 100,
                        new java.io.BufferedOutputStream(out));
                out.close();
            }
        }
    }

    // ── Helpers ────────────────────────────────────────

    private Bitmap createSolidBitmap(int w, int h, int color) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.eraseColor(color);
        return bmp;
    }
}
