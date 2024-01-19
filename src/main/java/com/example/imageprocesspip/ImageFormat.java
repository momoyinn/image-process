package com.example.imageprocesspip;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ImageFormat {

    WEBP("WEBP");

    final String value;

    ImageFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static List<String> getAllValues() {
        return Arrays.stream(ImageFormat.values())
                .map(ImageFormat::getValue)
                .collect(Collectors.toList());
    }
}
