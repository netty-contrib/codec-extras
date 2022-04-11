/*
 * Copyright 2021-2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.contrib.handler.codec.json;

import io.netty5.buffer.ByteBufUtil;
import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.ByteToMessageDecoderForBuffer;
import io.netty5.handler.codec.CorruptedFrameException;
import io.netty5.handler.codec.TooLongFrameException;

import static io.netty5.util.internal.ObjectUtil.checkPositive;

/**
 * Splits a byte stream of JSON objects and arrays into individual objects/arrays and passes them up the
 * {@link ChannelPipeline}.
 * <p>
 * The byte stream is expected to be in UTF-8 character encoding or ASCII. The current implementation
 * uses direct {@code byte} to {@code char} cast and then compares that {@code char} to a few low range
 * ASCII characters like {@code '{'}, {@code '['} or {@code '"'}. UTF-8 is not using low range [0..0x7F]
 * byte values for multibyte codepoint representations therefore fully supported by this implementation.
 * <p>
 * This class does not do any real parsing or validation. A sequence of bytes is considered a JSON object/array
 * if it contains a matching number of opening and closing braces/brackets. It's up to a subsequent
 * {@link ChannelHandler} to parse the JSON text into a more usable form i.e. a POJO.
 */
public class JsonObjectDecoder extends ByteToMessageDecoderForBuffer {

    private static final int ST_CORRUPTED = -1;
    private static final int ST_INIT = 0;
    private static final int ST_DECODING_NORMAL = 1;
    private static final int ST_DECODING_ARRAY_STREAM = 2;
    private final int maxObjectLength;
    private final boolean streamArrayElements;
    private int openBraces;
    private int idx; // current scan position
    private int lastReaderOffset;
    private int state;
    private boolean insideString;

    public JsonObjectDecoder() {
        // 1 MB
        this(1024 * 1024);
    }

    public JsonObjectDecoder(int maxObjectLength) {
        this(maxObjectLength, false);
    }

    public JsonObjectDecoder(boolean streamArrayElements) {
        this(1024 * 1024, streamArrayElements);
    }

    /**
     * @param maxObjectLength     maximum number of bytes a JSON object/array may use (including braces and all).
     *                            Objects exceeding this length are dropped and an {@link TooLongFrameException}
     *                            is thrown.
     * @param streamArrayElements if set to true and the "top level" JSON object is an array, each of its entries
     *                            is passed through the pipeline individually and immediately after it was fully
     *                            received, allowing for arrays with "infinitely" many elements.
     */
    public JsonObjectDecoder(int maxObjectLength, boolean streamArrayElements) {
        this.maxObjectLength = checkPositive(maxObjectLength, "maxObjectLength");
        this.streamArrayElements = streamArrayElements;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
        if (state == ST_CORRUPTED) {
            in.skipReadable(in.readableBytes());
            return;
        }

        if (idx > in.readerOffset() && lastReaderOffset != in.readerOffset()) {
            idx = in.readerOffset() + idx - lastReaderOffset;
        }

        // index of next byte to process.
        int idx = this.idx;
        int wrtOffset = in.writerOffset();

        if (wrtOffset > maxObjectLength) {
            // buffer size exceeded maxObjectLength; discarding the complete buffer.
            in.skipReadable(in.readableBytes());
            reset();
            throw new TooLongFrameException(
                    "object length exceeds " + maxObjectLength + ": " + wrtOffset + " bytes discarded");
        }

        for (/* use current idx */; idx < wrtOffset; idx++) {
            byte c = in.getByte(idx);
            if (state == ST_DECODING_NORMAL) {
                decodeByte(c, in, idx);

                // All opening braces/brackets have been closed. That's enough to conclude
                // that the JSON object/array is complete.
                if (openBraces == 0) {
                    Buffer json = extractObject(ctx, in, in.readerOffset(), idx + 1 - in.readerOffset());
                    if (json != null) {
                        ctx.fireChannelRead(json);
                    }

                    int newWrtOffset = in.writerOffset();
                    if (wrtOffset > newWrtOffset) {
                        // The JSON object/array was extracted using split or readSplit ==>
                        // reset current scan index so next iteration will correspond to first position.
                        // Also reset input buffer writer offset.
                        idx = -1;
                        wrtOffset = newWrtOffset;
                    } else {
                        // The JSON object/array was extracted not using split/readSplit ==>
                        // discard the bytes from the input buffer.
                        in.readerOffset(idx + 1);
                    }

                    // Reset the object state to get ready for the next JSON object/text
                    // coming along the byte stream.
                    reset();
                }
            } else if (state == ST_DECODING_ARRAY_STREAM) {
                decodeByte(c, in, idx);

                if (!insideString && (openBraces == 1 && c == ',' || openBraces == 0 && c == ']')) {
                    // skip leading spaces. No range check is needed and the loop will terminate
                    // because the byte at position idx is not a whitespace.
                    for (int i = in.readerOffset(); Character.isWhitespace(in.getByte(i)); i++) {
                        in.skipReadable(1);
                    }

                    // skip trailing spaces.
                    int idxNoSpaces = idx - 1;
                    while (idxNoSpaces >= in.readerOffset() && Character.isWhitespace(in.getByte(idxNoSpaces))) {
                        idxNoSpaces--;
                    }

                    Buffer json = extractObject(ctx, in, in.readerOffset(), idxNoSpaces + 1 - in.readerOffset());
                    if (json != null) {
                        ctx.fireChannelRead(json);
                    }

                    int newWrtOffset = in.writerOffset();
                    if (wrtOffset > newWrtOffset) {
                        // The JSON object/array was extracted using split or readSplit ==>
                        // reset current scan index accordingly, as well as the input buffer writer offset.
                        idx = idx - idxNoSpaces - 1;
                        in.readerOffset(idx + 1);
                        wrtOffset = newWrtOffset;
                    } else {
                        // The JSON object/array was extracted not using split or readSplit ==>
                        // discard the bytes from the input buffer.
                        in.readerOffset(idx + 1);
                    }

                    if (c == ']') {
                        reset();
                    }
                }
                // JSON object/array detected. Accumulate bytes until all braces/brackets are closed.
            } else if (c == '{' || c == '[') {
                initDecoding(c);

                if (state == ST_DECODING_ARRAY_STREAM) {
                    // Discard the array bracket
                    in.skipReadable(1);
                }
                // Discard leading spaces in front of a JSON object/array.
            } else if (Character.isWhitespace(c)) {
                in.skipReadable(1);
            } else {
                state = ST_CORRUPTED;
                throw new CorruptedFrameException(
                        "invalid JSON received at byte position " + idx + ": " + ByteBufUtil.hexDump(in));
            }
        }

        if (in.readableBytes() == 0) {
            this.idx = 0;
        } else {
            this.idx = idx;
        }
        lastReaderOffset = in.readerOffset();
    }

    /**
     * Override this method if you want to filter the json objects/arrays that get passed through the pipeline.
     */
    @SuppressWarnings("UnusedParameters")
    protected Buffer extractObject(ChannelHandlerContext ctx, Buffer buffer, int index, int length) {
        return buffer.readSplit(length);
    }

    private void decodeByte(byte c, Buffer in, int idx) {
        if ((c == '{' || c == '[') && !insideString) {
            openBraces++;
        } else if ((c == '}' || c == ']') && !insideString) {
            openBraces--;
        } else if (c == '"') {
            // start of a new JSON string. It's necessary to detect strings as they may
            // also contain braces/brackets and that could lead to incorrect results.
            if (!insideString) {
                insideString = true;
            } else {
                int backslashCount = 0;
                idx--;
                while (idx >= 0) {
                    if (in.getByte(idx) == '\\') {
                        backslashCount++;
                        idx--;
                    } else {
                        break;
                    }
                }
                // The double quote isn't escaped only if there are even "\"s.
                if (backslashCount % 2 == 0) {
                    // Since the double quote isn't escaped then this is the end of a string.
                    insideString = false;
                }
            }
        }
    }

    private void initDecoding(byte openingBrace) {
        openBraces = 1;
        if (openingBrace == '[' && streamArrayElements) {
            state = ST_DECODING_ARRAY_STREAM;
        } else {
            state = ST_DECODING_NORMAL;
        }
    }

    private void reset() {
        insideString = false;
        state = ST_INIT;
        openBraces = 0;
    }
}
