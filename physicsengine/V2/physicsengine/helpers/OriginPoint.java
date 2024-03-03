package com.bosonshiggs.physicsengine.helpers;

import com.google.appinventor.components.common.OptionList;

import java.util.HashMap;
import java.util.Map;

public enum OriginPoint implements OptionList<String> {
    TOP_LEFT("TopLeft"),
    TOP_RIGHT("TopRight"),
    BOTTOM_RIGHT("BottomRight"),
    BOTTOM_LEFT("BottomLeft"),
    CENTER("Center"),
    CUSTOM("Custom");

    private String pointCode;

    OriginPoint(String code) {
        this.pointCode = code;
    }

    @Override
    public String toUnderlyingValue() {
        return pointCode;
    }

    private static final Map<String, OriginPoint> lookup = new HashMap<>();

    static {
        for (OriginPoint point : OriginPoint.values()) {
            lookup.put(point.toUnderlyingValue(), point);
        }
    }

    public static OriginPoint fromUnderlyingValue(String code) {
        return lookup.get(code);
    }
}
