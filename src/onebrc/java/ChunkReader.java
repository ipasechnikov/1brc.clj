package onebrc.java;

import java.nio.ByteBuffer;

public class ChunkReader {
    private static final int NAME_MAX_LEN = 100;
    private static final int NAME_EXT_LEN = Long.BYTES;

    private final ByteBuffer nameBuffer = ByteBuffer.allocate(NAME_MAX_LEN + NAME_EXT_LEN);

    public ByteBuffer readName(final ByteBuffer chunk) {
        nameBuffer.clear();

        int nameLength = 0;
        while (true) {
            final int nReadBytes;
            final int separatorPos;

            // Avoid extra condition check from a standard .remaining() method
            final int remaining = chunk.limit() - chunk.position();

            if (remaining >= Long.BYTES) {
                final long nameBytes = chunk.getLong();
                nameBuffer.putLong(nameLength, nameBytes);
                separatorPos = BitwiseHelpers.indexOf(nameBytes, (byte) ';');
                nReadBytes = Long.BYTES;
            } else {
                final int nameBytes = chunk.getInt();
                nameBuffer.putInt(nameLength, nameBytes);
                separatorPos = BitwiseHelpers.indexOf(nameBytes, (byte) ';');
                nReadBytes = Integer.BYTES;
            }

            if (separatorPos != -1) {
                final int nReturnBytes = nReadBytes - separatorPos - 1;
                chunk.position(chunk.position() - nReturnBytes);
                nameLength += separatorPos;
                break;
            }

            nameLength += nReadBytes;
        }

        return nameBuffer.position(nameLength).flip();
    }

    public int readTemp(final ByteBuffer chunk) {
        final byte byte1 = chunk.get();
        final boolean isNegative = byte1 == '-';

        int temp = 0;
        final int sign;

        if (isNegative) {
            final int digit1 = chunk.get() - '0';
            temp = digit1;
            sign = -1;
        } else {
            final int digit1 = byte1 - '0';
            temp = digit1;
            sign = 1;
        }

        final int byte2 = chunk.get();
        if (byte2 != '.') {
            final int digit2 = byte2 - '0';
            temp = (temp * 10) + digit2;

            // Next char is a decimal point
            chunk.get();
        }

        final int digit3 = chunk.get() - '0';
        temp = (temp * 10) + digit3;

        // The last byte is always a newline
        chunk.get();

        return temp * sign;
    }
}
