package com.lightps.engine.layer;

/**
 * PSD-compatible blend modes.
 * Maps to Krita's psd_blend_mode enum and Photoshop's 4-char keys.
 */
public enum BlendMode {
    NORMAL("norm"),
    DISSOLVE("diss"),
    DARKEN("dark"),
    MULTIPLY("mul "),
    COLOR_BURN("idiv"),
    LINEAR_BURN("lbrn"),
    DARKER_COLOR("dkCl"),
    LIGHTEN("lite"),
    SCREEN("scrn"),
    COLOR_DODGE("div "),
    LINEAR_DODGE("lddg"),
    LIGHTER_COLOR("lgCl"),
    OVERLAY("over"),
    SOFT_LIGHT("sLit"),
    HARD_LIGHT("hLit"),
    VIVID_LIGHT("vLit"),
    LINEAR_LIGHT("lLit"),
    PIN_LIGHT("pLit"),
    HARD_MIX("hMix"),
    DIFFERENCE("diff"),
    EXCLUSION("smud"),
    SUBTRACT("fsub"),
    DIVIDE("fdiv"),
    HUE("hue "),
    SATURATION("sat "),
    COLOR("colr"),
    LUMINOSITY("lum "),
    PASSTHROUGH("pass");

    private final String psdKey;

    BlendMode(String psdKey) {
        this.psdKey = psdKey;
    }

    /** 4-character PSD blend mode key. */
    public String psdKey() {
        return psdKey;
    }

    /** Lookup by PSD 4-char key. */
    public static BlendMode fromPsdKey(String key) {
        for (BlendMode bm : values()) {
            if (bm.psdKey.equals(key)) return bm;
        }
        return NORMAL;
    }
}
