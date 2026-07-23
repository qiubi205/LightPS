package com.lightps;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.lightps.engine.brush.Brush;
import com.lightps.engine.color.ColorSpace;
import com.lightps.engine.layer.PixelLayer;
import com.lightps.model.Document;
import com.lightps.ui.CanvasView;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Main editor activity for LightPS.
 */
public class LightPSActivity extends Activity {

    private static final int REQUEST_OPEN = 100;
    private static final int REQUEST_SAVE = 101;

    private CanvasView canvasView;
    private Document document;
    private Brush currentBrush;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        canvasView = findViewById(R.id.canvas);

        // Initialize with a new empty document
        createNewDocument(1920, 1080);

        // Tools
        setupToolbar();
    }

    // ── Document management ────────────────────────────

    private void createNewDocument(int width, int height) {
        document = new Document(width, height);

        // Add a white background layer
        PixelLayer bg = new PixelLayer("Background", width, height);
        bg.setPixels(createSolidBitmap(width, height, Color.WHITE));
        document.getLayerManager().addLayer(bg);

        // Add a transparent layer for drawing
        PixelLayer drawLayer = new PixelLayer("Layer 1", width, height);
        document.getLayerManager().addLayer(drawLayer);
        document.getLayerManager().setActiveLayer(drawLayer);

        canvasView.setLayerManager(document.getLayerManager());
        updateTitle();

        // Initialize brush
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
        // Color button
        findViewById(R.id.btn_color).setOnClickListener(v -> showColorPicker());

        // Size slider (simplified: increment/decrement)
        findViewById(R.id.btn_size_up).setOnClickListener(v -> {
            if (currentBrush != null) {
                currentBrush.setSize(currentBrush.getSize() + 5);
                showToast("Brush size: " + (int) currentBrush.getSize());
            }
        });

        findViewById(R.id.btn_size_down).setOnClickListener(v -> {
            if (currentBrush != null) {
                currentBrush.setSize(Math.max(1, currentBrush.getSize() - 5));
                showToast("Brush size: " + (int) currentBrush.getSize());
            }
        });

        // New document
        findViewById(R.id.btn_new).setOnClickListener(v -> showNewDocDialog());

        // Open file
        findViewById(R.id.btn_open).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            startActivityForResult(i, REQUEST_OPEN);
        });

        // Save
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            if (document.getFile() != null) {
                saveDocument();
            } else {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/png");
                i.putExtra(Intent.EXTRA_TITLE, "drawing.png");
                startActivityForResult(i, REQUEST_SAVE);
            }
        });

        // Save as PSD
        findViewById(R.id.btn_save_psd).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/vnd.adobe.photoshop");
            i.putExtra(Intent.EXTRA_TITLE, "drawing.psd");
            startActivityForResult(i, REQUEST_SAVE);
        });
    }

    private void showNewDocDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("New Document");

        View view = getLayoutInflater().inflate(R.layout.dialog_new_doc, null);
        builder.setView(view);

        builder.setPositiveButton("Create", (dialog, which) -> {
            // Simplified: always 1920x1080
            createNewDocument(1920, 1080);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showColorPicker() {
        // Simplified color picker — show preset colors
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
            showToast("Saved");
        } catch (Exception e) {
            showToast("Save failed: " + e.getMessage());
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
                case REQUEST_OPEN: {
                    // Open image as new document
                    android.graphics.Bitmap bmp =
                            android.provider.MediaStore.Images.Media.getBitmap(
                                    getContentResolver(), uri);
                    Document doc = new Document(bmp.getWidth(), bmp.getHeight());
                    PixelLayer layer = new PixelLayer("Imported", bmp);
                    doc.getLayerManager().addLayer(layer);
                    document = doc;
                    canvasView.setLayerManager(document.getLayerManager());
                    updateTitle();
                    break;
                }
                case REQUEST_SAVE:
                    // Save to URI
                    if (document != null) {
                        java.io.InputStream in = getContentResolver().openInputStream(uri);
                        if (in != null) {
                            in.close();
                        }
                        java.io.OutputStream out = getContentResolver().openOutputStream(uri);
                        if (out != null) {
                            android.graphics.Bitmap flat = document.getLayerManager().flatten();
                            flat.compress(android.graphics.Bitmap.CompressFormat.PNG, 100,
                                    new java.io.BufferedOutputStream(out));
                            out.close();
                            showToast("Exported");
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            showToast("Error: " + e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────

    private android.graphics.Bitmap createSolidBitmap(int w, int h, int color) {
        android.graphics.Bitmap bmp =
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        bmp.eraseColor(color);
        return bmp;
    }

    private void showToast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
