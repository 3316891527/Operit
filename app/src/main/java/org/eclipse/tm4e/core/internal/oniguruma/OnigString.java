/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Initial code from https://github.com/atom/node-oniguruma
 * Initial copyright Copyright (c) 2013 GitHub Inc.
 * Initial license: MIT
 *
 * Contributors:
 * - GitHub Inc.: Initial code, written in JavaScript, licensed under MIT license
 * - Angelo Zerr <angelo.zerr@gmail.com> - translation and adaptation to Java
 * - Fabio Zadrozny <fabiofz@gmail.com> - Utilities to convert between utf-8 and utf-16
 */
package org.eclipse.tm4e.core.internal.oniguruma;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.jcodings.specific.UTF8Encoding;

/**
 * Sora TextMate 0.23.6 的局部兼容修正，保留上游类的二进制接口。
 * 字节映射直接对应 UTF-16，确保匹配、捕获重分词和动态结束规则使用同一坐标。
 */
public abstract class OnigString {
    static final class MultiByteString extends OnigString {
        private int[] byteToCharOffsets;

        private MultiByteString(String content, byte[] bytes) {
            super(content, bytes);
        }

        private int[] offsets() {
            if (byteToCharOffsets == null) {
                int[] offsets = new int[bytesCount];
                int byteIndex = 0;
                int charIndex = 0;
                while (byteIndex < bytesCount) {
                    int byteLength = UTF8Encoding.INSTANCE.length(bytesUTF8, byteIndex, bytesCount);
                    Arrays.fill(offsets, byteIndex, byteIndex + byteLength, charIndex);
                    byteIndex += byteLength;
                    // 补充字符占两个 UTF-16 单元，孤立代理项仍占一个单元。
                    charIndex += Character.charCount(content.codePointAt(charIndex));
                }
                byteToCharOffsets = offsets;
            }
            return byteToCharOffsets;
        }

        @Override
        int getByteIndexOfChar(int charIndex) {
            if (charIndex == content.length()) return bytesCount;
            if (charIndex < 0 || charIndex >= content.length()) {
                throwOutOfBoundsException("Char", charIndex, 0, content.length());
            }
            // 行扫描可能推进一个单元，位于代理对中间时仍使用该码点的字节起点。
            if (charIndex > 0 && Character.isLowSurrogate(content.charAt(charIndex))
                    && Character.isHighSurrogate(content.charAt(charIndex - 1))) {
                charIndex--;
            }
            int[] offsets = offsets();
            int byteIndex = Arrays.binarySearch(offsets, charIndex);
            while (byteIndex > 0 && offsets[byteIndex - 1] == charIndex) byteIndex--;
            return byteIndex;
        }

        @Override
        int getCharIndexOfByte(int byteIndex) {
            if (byteIndex == bytesCount) return content.length();
            if (byteIndex < 0 || byteIndex >= bytesCount) {
                throwOutOfBoundsException("Byte", byteIndex, 0, bytesCount);
            }
            return offsets()[byteIndex];
        }
    }

    static final class SingleByteString extends OnigString {
        private SingleByteString(String content, byte[] bytes) {
            super(content, bytes);
        }

        @Override
        int getByteIndexOfChar(int charIndex) {
            if (charIndex < 0 || charIndex > bytesCount) {
                throwOutOfBoundsException("Char", charIndex, 0, bytesCount);
            }
            return charIndex;
        }

        @Override
        int getCharIndexOfByte(int byteIndex) {
            if (byteIndex < 0 || byteIndex > bytesCount) {
                throwOutOfBoundsException("Byte", byteIndex, 0, bytesCount);
            }
            return byteIndex;
        }
    }

    public static OnigString of(String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        return bytes.length == content.length()
                ? new SingleByteString(content, bytes)
                : new MultiByteString(content, bytes);
    }

    public final String content;
    public final int bytesCount;
    final byte[] bytesUTF8;

    private OnigString(String content, byte[] bytes) {
        this.content = content;
        bytesUTF8 = bytes;
        bytesCount = bytes.length;
    }

    protected final void throwOutOfBoundsException(String name, int index, int min, int max) {
        throw new ArrayIndexOutOfBoundsException(name + " index " + index + " is out of range " + min + ".." + max);
    }

    abstract int getByteIndexOfChar(int charIndex);
    abstract int getCharIndexOfByte(int byteIndex);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[string=\"" + content + "\"]";
    }
}