package onebrc.java;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

// Based on the implementation from Sam Pullara
// https://github.com/gunnarmorling/1brc/blob/db064194be375edc02d6dbcd21268ad40f7e2869/src/main/java/dev/morling/onebrc/CalculateAverage_spullara.java#L174
public class ByteArrayToResultMap {
    private static final int DEFAULT_CAPACITY = 1024 * 16;

    private final Result[] slots;
    private final byte[][] keys;
    private final int[] hashCache;
    private final List<SimpleEntry<String, Result>> allResults = new LinkedList<>();

    public ByteArrayToResultMap() {
        this(DEFAULT_CAPACITY);
    }

    public ByteArrayToResultMap(int capacity) {
        slots = new Result[capacity];
        keys = new byte[capacity][];
        hashCache = new int[capacity];
    }

    public void upsert(final ByteBuffer key, final int temp) {
        final int keyLength = key.limit();
        final int hash = djb2(key);
        int slotIndex = hash & (slots.length - 1);
        Result slotValue = slots[slotIndex];

        while (slotValue != null) {
            final int existingKeyHash = hashCache[slotIndex];
            if (hash == existingKeyHash) {
                final byte[] existingKey = keys[slotIndex];
                final boolean isKeyMatch = existingKey.length == keyLength &&
                        Arrays.equals(key.array(), 0, keyLength, existingKey, 0, existingKey.length);

                if (isKeyMatch) {
                    break;
                }
            }

            slotIndex = (slotIndex + 1) & (slots.length - 1);
            slotValue = slots[slotIndex];
        }

        if (slotValue == null) {
            hashCache[slotIndex] = hash;

            final Result newValue = new Result(temp);
            slots[slotIndex] = newValue;

            final byte[] newKey = new byte[keyLength];
            key.rewind().get(newKey);
            keys[slotIndex] = newKey;

            final String newKeyStr = new String(newKey, StandardCharsets.UTF_8);
            allResults.add(new SimpleEntry<>(newKeyStr, newValue));
        } else {
            slotValue.add(temp);
        }
    }

    public List<SimpleEntry<String, Result>> getAllResults() {
        return allResults;
    }

    private int djb2(final ByteBuffer key) {
        final byte[] keyArr = key.array();
        final int keyLen = key.limit();

        int hash = 5381;
        for (int i = 0; i < keyLen; i++) {
            hash = (hash * 33) + keyArr[i];
        }

        return hash;
    }
}
