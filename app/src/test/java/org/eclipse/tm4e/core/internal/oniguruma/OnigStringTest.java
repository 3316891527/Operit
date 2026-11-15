package org.eclipse.tm4e.core.internal.oniguruma;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class OnigStringTest {
    @Test public void asciiBoundariesMatchByteBoundaries() {
        OnigString text = OnigString.of("abc");
        for (int i = 0; i <= 3; i++) {
            assertEquals(i, text.getByteIndexOfChar(i));
            assertEquals(i, text.getCharIndexOfByte(i));
        }
    }

    @Test public void chineseAndEmojiBoundariesUseUtf16InBothDirections() {
        OnigString text = OnigString.of("中👋a𐐀尾");
        int[] chars = {0, 1, 3, 4, 6, 7};
        int[] bytes = {0, 3, 7, 8, 12, 15};
        for (int i = 0; i < chars.length; i++) {
            assertEquals(bytes[i], text.getByteIndexOfChar(chars[i]));
            assertEquals(chars[i], text.getCharIndexOfByte(bytes[i]));
        }
        assertEquals(3, text.getByteIndexOfChar(2));
        assertEquals(1, text.getCharIndexOfByte(6));
    }

    @Test public void isolatedSurrogatesDoNotShiftFollowingMatches() {
        String value = "中" + (char) 0xD800 + "x" + (char) 0xDC00 + "👋end";
        OnigString text = OnigString.of(value);
        int expectedByte = value.substring(0, value.indexOf("end")).getBytes(StandardCharsets.UTF_8).length;
        assertEquals(expectedByte, text.getByteIndexOfChar(value.indexOf("end")));
        assertEquals(value.indexOf("end"), text.getCharIndexOfByte(expectedByte));
    }

    @Test public void realScannerSearchesAndCapturesAfterAnEmoji() {
        OnigScanner scanner = new OnigScanner(java.util.Collections.singletonList("(val) (name)"));
        OnigScannerMatch result = scanner.findNextMatch(OnigString.of("👋 val name"), 3);
        assertNotNull(result);
        assertEquals(3, result.getCaptureIndices()[0].start);
        assertEquals(11, result.getCaptureIndices()[0].end);
        assertEquals(7, result.getCaptureIndices()[2].start);
        assertEquals(11, result.getCaptureIndices()[2].end);
    }

    @Test public void emptyAndInvalidOffsetsHaveExplicitBoundaries() {
        OnigString empty = OnigString.of("");
        assertEquals(0, empty.getByteIndexOfChar(0));
        assertEquals(0, empty.getCharIndexOfByte(0));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> empty.getByteIndexOfChar(1));
        OnigString text = OnigString.of("👋");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> text.getCharIndexOfByte(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> text.getByteIndexOfChar(3));
    }
}
