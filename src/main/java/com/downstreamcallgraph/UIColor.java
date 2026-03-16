package com.downstreamcallgraph;

public final class UIColor {
    private final String mainColor;
    private final String darkColor;
    private final String textColor;

    private static int colorIndex = 0;

    public UIColor(String mainColor, String darkColor, String textColor) {
        this.mainColor = mainColor;
        this.darkColor = darkColor;
        this.textColor = textColor;
    }

    public String getMainColor() {
        return mainColor;
    }

    public String getDarkColor() {
        return darkColor;
    }

    public String getTextColor() {
        return textColor;
    }

    private static final UIColor[] COLORS = {
        new UIColor("#E53935", "#B71C1C", "#FFFFFF"),
        new UIColor("#FB8C00", "#E65100", "#000000"),
        new UIColor("#FFC107", "#FFA000", "#000000"),
        new UIColor("#43A047", "#2E7D32", "#FFFFFF"),
        new UIColor("#00897B", "#00695C", "#FFFFFF"),
        new UIColor("#039BE5", "#0277BD", "#FFFFFF"),
        new UIColor("#1E88E5", "#1565C0", "#FFFFFF"),
        new UIColor("#3949AB", "#283593", "#FFFFFF"),
        new UIColor("#8E24AA", "#6A1B9A", "#FFFFFF"),
        new UIColor("#D81B60", "#AD1457", "#FFFFFF"),
        new UIColor("#F4511E", "#BF360C", "#FFFFFF"),
        new UIColor("#6D4C41", "#4E342E", "#FFFFFF"),
        new UIColor("#546E7A", "#37474F", "#FFFFFF"),
        new UIColor("#00ACC1", "#00838F", "#000000"),
        new UIColor("#7CB342", "#558B2F", "#000000"),
        new UIColor("#5E35B1", "#4527A0", "#FFFFFF"),
        new UIColor("#8BC34A", "#689F38", "#000000"),
        new UIColor("#FDD835", "#FBC02D", "#000000"),
        new UIColor("#FFB300", "#FF8F00", "#000000")
    };

    public static UIColor getNextColor() {
        return COLORS[colorIndex++ % COLORS.length];
    }
}
