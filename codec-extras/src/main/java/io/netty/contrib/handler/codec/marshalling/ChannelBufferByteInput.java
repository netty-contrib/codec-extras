/*
 * Copyright 2021 The Netty Project
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
package io.netty.contrib.handler.codec.marshalling;

import io.netty5.buffer.Buffer;
import org.jboss.marshalling.ByteInput;

import java.io.IOException;

/**
 * {@link ByteInput} implementation which reads its data from a {@link Buffer}
 */
class ChannelBufferByteInput implements ByteInput {

    private final Buffer buffer;

    ChannelBufferByteInput(Buffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public void close() throws IOException {
        // nothing to do
    }

    @Override
    public int available() {
        return buffer.readableBytes();
    }

    @Override
    public int read() {
        if (buffer.readableBytes() > 0) {
            return buffer.readByte() & 0xff;
        }
        return -1;
    }

    @Override
    public int read(byte[] array) {
        return read(array, 0, array.length);
    }

    @Override
    public int read(byte[] dst, int dstIndex, int length) {
        int available = available();
        if (available == 0) {
            return -1;
        }

        length = Math.min(available, length);
        buffer.readBytes(dst, dstIndex, length);
        return length;
    }

    @Override
    public long skip(long bytes) {
        int readable = buffer.readableBytes();
        if (readable < bytes) {
            bytes = readable;
        }
        buffer.readerOffset((int) (buffer.readerOffset() + bytes));
        return bytes;
    }

}
