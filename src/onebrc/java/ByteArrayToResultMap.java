package onebrc.java;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

// Based on the implementation from Sam Pullara
// https://github.com/gunnarmorling/1brc/blob/db064194be375edc02d6dbcd21268ad40f7e2869/src/main/java/dev/morling/onebrc/CalculateAverage_spullara.java#L174
public class ByteArrayToResultMap {
    private static final int MAX_SIZE = 1024 * 128;
    private final Result[] slots = new Result[MAX_SIZE];
    private final byte[][] keys = new byte[MAX_SIZE][];
    private final List<SimpleEntry<String, Result>> allResults = new LinkedList<>();

    public void upsert(final byte[] key, final int keyLength, final double temp) {
        final int hash = djb2(key, keyLength);
        int slotIndex = hash & (slots.length - 1);
        Result slotValue = slots[slotIndex];

        while (true) {
            if (slotValue == null) {
                break;
            }

            final byte[] existingKey = keys[slotIndex];
            final boolean isKeyMatch = existingKey.length == keyLength &&
                    Arrays.equals(key, 0, keyLength, existingKey, 0, keyLength);

            if (isKeyMatch) {
                break;
            }

            slotIndex = (slotIndex + 1) & (slots.length - 1);
            slotValue = slots[slotIndex];
        }

        if (slotValue == null) {
            final Result newValue = new Result(temp);
            slots[slotIndex] = newValue;

            final byte[] newKey = new byte[keyLength];
            System.arraycopy(key, 0, newKey, 0, keyLength);
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

    private int djb2(final byte[] key, final int keyLength) {
        int hash = 5381;
        for (int i = 0; i < keyLength; i++) {
            hash = hash * 33 + key[i];
        }
        return hash;
    }
}
