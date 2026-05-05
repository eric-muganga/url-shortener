package com.eric_muganga.url_shortener.service;

import org.springframework.stereotype.Component;


@Component
public class Base62Encoder {
    private static final String BASE62_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private static final int BASE = 62;

    /**
     * Encode a long ID into a Base62 string.
     * Example: 18452 -> "4m0"
     */
    public String encode(Long id) {
        if(id < 0) return null;
        if(id == 0) return "0";

        StringBuilder result = new StringBuilder();
        while(id > 0) {
            result.append(BASE62_ALPHABET.charAt((int)(id % BASE)));
            id /= BASE;
        }
        return result.reverse().toString();
    }

    public long decode(String base62String) {
        if(base62String == null) return 0;
        long result = 0;

        for (char c : base62String.toCharArray()) {
            result = result * BASE + BASE62_ALPHABET.indexOf(c);
        }

        return result;
    }
}
