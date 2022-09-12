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
package io.netty.contrib.handler.codec.protobuf;

import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtobufVarint32FrameDecoderTest {

    private EmbeddedChannel ch;

    @BeforeEach
    public void setUp() {
        ch = new EmbeddedChannel(new ProtobufVarint32FrameDecoder());
    }

    @Test
    public void testTinyDecode() {
        byte[] b = {4, 1, 1, 1, 1};
        Buffer buffer1 = ch.bufferAllocator().allocate(1);
        assertFalse(ch.writeInbound(buffer1.writeBytes(b, 0, 1)));
        assertNull(ch.readInbound());
        Buffer buffer2 = ch.bufferAllocator().allocate(2);
        assertFalse(ch.writeInbound(buffer2.writeBytes(b, 1, 2)));
        assertNull(ch.readInbound());
        Buffer buffer3 = ch.bufferAllocator().allocate(b.length - 3);
        assertTrue(ch.writeInbound(buffer3.writeBytes(b, 3, b.length - 3)));

        try (Buffer expected = ch.bufferAllocator().copyOf(new byte[]{1, 1, 1, 1});
             Buffer actual = ch.readInbound()) {

            assertThat(expected).isEqualTo(actual);
            assertFalse(ch.finish());
        }
    }

    @Test
    public void testRegularDecode() {
        byte[] b = new byte[2048];
        for (int i = 2; i < 2048; i++) {
            b[i] = 1;
        }
        b[0] = -2;
        b[1] = 15;
        Buffer buffer1 = ch.bufferAllocator().allocate(1);
        assertFalse(ch.writeInbound(buffer1.writeBytes(b, 0, 1)));
        assertNull(ch.readInbound());
        Buffer buffer2 = ch.bufferAllocator().allocate(127);
        assertFalse(ch.writeInbound(buffer2.writeBytes(b, 1, 127)));
        assertNull(ch.readInbound());
        Buffer buffer3 = ch.bufferAllocator().allocate(600);
        assertFalse(ch.writeInbound(buffer3.writeBytes(b, 127, 600)));
        assertNull(ch.readInbound());
        Buffer buffer4 = ch.bufferAllocator().allocate(b.length - 727);
        assertTrue(ch.writeInbound(buffer4.writeBytes(b, 727, b.length - 727)));

        Buffer buffer5 = ch.bufferAllocator().allocate(b.length - 2);
        try (Buffer expected = buffer5.writeBytes(b, 2, b.length - 2);
             Buffer actual = ch.readInbound()) {
            assertThat(expected).isEqualTo(actual);
            assertFalse(ch.finish());
        }
    }
}
