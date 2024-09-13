package onebrc.java;

import java.nio.ByteBuffer;

public class ChunkReader {
    private static final int NAME_MAX_LEN = 100;
    private static final int TEMP_MAX_LEN = 5;
    private static final int LINE_MAX_LEN = NAME_MAX_LEN + TEMP_MAX_LEN + 2; // +2 for newline and semicolon

    private final ByteBuffer nameBuffer = ByteBuffer.allocate(LINE_MAX_LEN);
    private final byte[] tempBuffer = new byte[Long.BYTES];

    public ByteBuffer readName(final ByteBuffer chunk) {
        nameBuffer.clear();
        for (byte b = chunk.get(); b != ';'; b = chunk.get()) {
            nameBuffer.put(b);
        }
        nameBuffer.flip();
        return nameBuffer;
    }

    public ByteBuffer readNameBatched(final ByteBuffer chunk) {
        nameBuffer.clear();

        final byte[] nameBufArr = nameBuffer.array();
        int nameBufPos = nameBuffer.position();

        while (true) {
            chunk.get(nameBufArr, nameBufPos, Integer.BYTES);
            final int nameBytes = (nameBufArr[nameBufPos] << 24)
                    | (nameBufArr[nameBufPos + 1] << 16)
                    | (nameBufArr[nameBufPos + 2] << 8)
                    | nameBufArr[nameBufPos + 3];

            final int separatorPos = BitwiseHelpers.findBytePosition(nameBytes, (byte) ';');

            if (separatorPos == 0) {
                nameBufPos += Integer.BYTES;
            } else {
                nameBufPos += separatorPos - 1;
                nameBuffer.position(nameBufPos);
                nameBuffer.flip();

                final int nReturnBytes = Integer.BYTES - separatorPos;
                chunk.position(chunk.position() - nReturnBytes);
                break;
            }
        }

        return nameBuffer;
    }

    public int readTemp(final ByteBuffer chunk) {
        int sign = 1;
        int temp = 0;
        for (byte b = chunk.get(); b != '\n'; b = chunk.get()) {
            switch (b) {
                case '-':
                    sign = -1;
                    break;
                case '.':
                    break;
                default:
                    temp = (temp * 10) + (b - '0');
                    break;
            }
        }
        return temp * sign;
    }

    public int readTempBatched(final ByteBuffer chunk) {
        // Eliminate a branch inside of .remaining() method
        final int chunkRemaining = chunk.limit() - chunk.position();

        // Read up to 8 bytes in a batch
        final int nReadBytes = BitwiseHelpers.branchlessMin(tempBuffer.length, chunkRemaining);
        chunk.get(tempBuffer, 0, nReadBytes);

        // Sign detection
        final int isNegative = BitwiseHelpers.isEqual(tempBuffer[0], (byte) '-');
        final int isPositive = BitwiseHelpers.logicalNot(isNegative);

        // Decimal point detection
        final int isDecimalPointAtByte2 = BitwiseHelpers.isEqual(tempBuffer[1], (byte) '.');
        final int isDecimalPointAtByte3 = BitwiseHelpers.isEqual(tempBuffer[2], (byte) '.');
        final int isDecimalPointAtByte4 = BitwiseHelpers.isEqual(tempBuffer[3], (byte) '.');

        // Case 1: Positive 2 digit number (e.g. 0.1, 2.5, 9.9, etc.)
        final int positiveTwoDigitComponent1 = isDecimalPointAtByte2 * (tempBuffer[0] - '0') * 10;
        final int positiveTwoDigitComponent2 = isDecimalPointAtByte2 * (tempBuffer[2] - '0');
        final int positiveTwoDigitNumber = positiveTwoDigitComponent1 + positiveTwoDigitComponent2;

        // Case 2: Positive 3 digit number (e.g. 10.1, 23.5, 91.9, etc.)
        final int positiveThreeDigitComponent1 = isPositive * isDecimalPointAtByte3 * (tempBuffer[0] - '0') * 100;
        final int positiveThreeDigitComponent2 = isPositive * isDecimalPointAtByte3 * (tempBuffer[1] - '0') * 10;
        final int positiveThreeDigitComponent3 = isPositive * isDecimalPointAtByte3 * (tempBuffer[3] - '0');
        final int positiveThreeDigitNumber = positiveThreeDigitComponent1
                + positiveThreeDigitComponent2
                + positiveThreeDigitComponent3;

        // Case 3: Negative 2 digit number (e.g. -0.1, -2.5, -9.9, etc.)
        final int negativeTwoDigitComponent1 = isNegative * isDecimalPointAtByte3 * (tempBuffer[1] - '0') * -10;
        final int negativeTwoDigitComponent2 = isNegative * isDecimalPointAtByte3 * (tempBuffer[3] - '0') * -1;
        final int negativeTwoDigitNumber = negativeTwoDigitComponent1 + negativeTwoDigitComponent2;

        // Case 4: Negative 3 digit number (e.g. -10.1, -23.5, -91.9, etc.)
        final int negativeThreeDigitComponent1 = isDecimalPointAtByte4 * (tempBuffer[1] - '0') * -100;
        final int negativeThreeDigitComponent2 = isDecimalPointAtByte4 * (tempBuffer[2] - '0') * -10;
        final int negativeThreeDigitComponent3 = isDecimalPointAtByte4 * (tempBuffer[4] - '0') * -1;
        final int negativeThreeDigitNumber = negativeThreeDigitComponent1
                + negativeThreeDigitComponent2
                + negativeThreeDigitComponent3;

        // Combine all cases into a single value
        final int temp = positiveTwoDigitNumber
                + positiveThreeDigitNumber
                + negativeTwoDigitNumber
                + negativeThreeDigitNumber;

        // Calculate the number of parsed bytes
        final int nParsedBytes1 = isDecimalPointAtByte2 * 4;
        final int nParsedBytes2 = isDecimalPointAtByte3 * 5;
        final int nParsedBytes3 = isDecimalPointAtByte4 * 6;
        final int nParsedBytes = nParsedBytes1 + nParsedBytes2 + nParsedBytes3;

        // Adjust chunk position to account for unparsed bytes
        final int nReturnBytes = nReadBytes - nParsedBytes;
        chunk.position(chunk.position() - nReturnBytes);

        return temp;
    }
}
