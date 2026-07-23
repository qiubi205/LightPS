package com.lightps.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.lightps.engine.layer.Layer;
import com.lightps.engine.layer.LayerManager;

/**
 * Layers panel showing the layer stack with thumbnails and controls.
 */
public class LayersPanel extends ListView {

    private LayerManager layerManager;
    private LayerAdapter adapter;

    public LayersPanel(Context context) {
        this(context, null);
    }

    public LayersPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDivider(new ColorDrawable(Color.parseColor("#FF2A2A4A")));
        setDividerHeight(1);
        setBackgroundColor(Color.parseColor("#FF1A1A2E"));
    }

    public void setLayerManager(LayerManager lm) {
        this.layerManager = lm;
        this.adapter = new LayerAdapter();
        setAdapter(adapter);

        setOnItemClickListener((parent, view, position, id) -> {
            if (layerManager != null) {
                int idx = layerManager.layerCount() - 1 - position; // reverse order
                Layer layer = layerManager.getLayer(idx);
                if (layer != null) {
                    layerManager.setActiveLayer(layer);
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }

    public void refresh() {
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private class LayerAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return layerManager != null ? layerManager.layerCount() : 0;
        }

        @Override
        public Object getItem(int i) {
            // Reverse: top layer first in list
            int idx = layerManager.layerCount() - 1 - i;
            return layerManager.getLayer(idx);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View convertView, ViewGroup parent) {
            ViewHolder h;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(android.R.layout.simple_list_item_1, parent, false);
                h = new ViewHolder();
                h.text = convertView.findViewById(android.R.id.text1);
                convertView.setTag(h);
            } else {
                h = (ViewHolder) convertView.getTag();
            }

            int idx = layerManager.layerCount() - 1 - i;
            Layer layer = layerManager.getLayer(idx);
            Layer active = layerManager.getActiveLayer();

            String name = layer.getName();
            String info = name + "  " + (int) (layer.getOpacity() * 100) + "%";
            if (layer == active) info += "  ◀";
            if (!layer.isVisible()) info += "  👁‍🗨";

            h.text.setText(info);
            h.text.setTextColor(layer == active ? Color.rgb(64, 200, 255) : Color.LTGRAY);
            h.text.setTextSize(14);

            convertView.setBackgroundColor(layer == active
                    ? Color.parseColor("#FF0F3460")
                    : Color.parseColor("#FF1A1A2E"));

            return convertView;
        }
    }

    private static class ViewHolder {
        TextView text;
    }
}
