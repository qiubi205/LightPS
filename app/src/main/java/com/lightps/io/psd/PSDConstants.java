package com.lightps.io.psd;

/**
 * PSD file format constants.
 * Reference: Adobe Photoshop SDK docs and Krita's psd.h.
 */
public final class PSDConstants {

    public static final String SIGNATURE = "8BPS";
    public static final int VERSION_PSD = 1;
    public static final int VERSION_PSB = 2;

    // Color modes
    public static final short COLOR_MODE_BITMAP       = 0;
    public static final short COLOR_MODE_GRAYSCALE    = 1;
    public static final short COLOR_MODE_INDEXED      = 2;
    public static final short COLOR_MODE_RGB          = 3;
    public static final short COLOR_MODE_CMYK         = 4;
    public static final short COLOR_MODE_MULTICHANNEL = 7;
    public static final short COLOR_MODE_DUOTONE      = 8;
    public static final short COLOR_MODE_LAB          = 9;

    // Channel IDs
    public static final short CHANNEL_RED   = 0;
    public static final short CHANNEL_GREEN = 1;
    public static final short CHANNEL_BLUE  = 2;
    public static final short CHANNEL_ALPHA = -1;
    public static final short CHANNEL_MASK  = -2;

    // Compression types
    public static final short COMPRESS_RAW         = 0;
    public static final short COMPRESS_RLE         = 1;
    public static final short COMPRESS_ZIP         = 2;
    public static final short COMPRESS_ZIP_PREDICT = 3;

    // Blend mode keys (4 chars)
    public static final String BLEND_NORMAL           = "norm";
    public static final String BLEND_DISSOLVE         = "diss";
    public static final String BLEND_DARKEN           = "dark";
    public static final String BLEND_MULTIPLY         = "mul ";
    public static final String BLEND_COLOR_BURN       = "idiv";
    public static final String BLEND_LINEAR_BURN      = "lbrn";
    public static final String BLEND_DARKER_COLOR     = "dkCl";
    public static final String BLEND_LIGHTEN          = "lite";
    public static final String BLEND_SCREEN           = "scrn";
    public static final String BLEND_COLOR_DODGE      = "div ";
    public static final String BLEND_LINEAR_DODGE     = "lddg";
    public static final String BLEND_LIGHTER_COLOR    = "lgCl";
    public static final String BLEND_OVERLAY          = "over";
    public static final String BLEND_SOFT_LIGHT       = "sLit";
    public static final String BLEND_HARD_LIGHT       = "hLit";
    public static final String BLEND_VIVID_LIGHT      = "vLit";
    public static final String BLEND_LINEAR_LIGHT     = "lLit";
    public static final String BLEND_PIN_LIGHT        = "pLit";
    public static final String BLEND_HARD_MIX         = "hMix";
    public static final String BLEND_DIFFERENCE       = "diff";
    public static final String BLEND_EXCLUSION        = "smud";
    public static final String BLEND_SUBTRACT         = "fsub";
    public static final String BLEND_DIVIDE           = "fdiv";
    public static final String BLEND_HUE              = "hue ";
    public static final String BLEND_SATURATION       = "sat ";
    public static final String BLEND_COLOR            = "colr";
    public static final String BLEND_LUMINOSITY       = "lum ";
    public static final String BLEND_PASSTHROUGH      = "pass";

    // Section tags (layer mask info section)
    public static final String TAG_LAYER_INFO = "Layr";
    public static final String TAG_LAYER_MASK = "LMsk";

    // Additional layer info keys
    public static final String KEY_LAYER_NAME        = "luni";
    public static final String KEY_LAYER_NAME_LEGACY = "lyid";
    public static final String KEY_SECTION_DIVIDER   = "lsct";
    public static final String KEY_LAYER_ID          = "lyid";
    public static final String KEY_UNICODE_NAME      = "luni";
    public static final String KEY_LAYER_MASK_AS_GB  = "LMsk";

    // Section divider types
    public static final int SECTION_GROUP_OPEN   = 1;
    public static final int SECTION_GROUP_CLOSED = 2;
    public static final int SECTION_DIVIDER      = 3;

    private PSDConstants() {}
}
