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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProtobufVarint32LengthFieldPrependerTest {

    private EmbeddedChannel ch;

    @BeforeEach
    public void setUp() {
        ch = new EmbeddedChannel(new ProtobufVarint32LengthFieldPrepender());
    }

    @Test
    public void testSize1Varint() {
        final int size = 1;
        final int num = 10;
        assertThat(ProtobufVarint32LengthFieldPrepender.computeRawVarint32Size(num)).isEqualTo(size);
        final byte[] buf = new byte[size + num];
        //0000 1010
        buf[0] = 0x0A;
        for (int i = size; i < num + size; ++i) {
            buf[i] = 1;
        }
        Buffer buffer = ch.bufferAllocator().allocate(buf.length - size);
        assertTrue(ch.writeOutbound(buffer.writeBytes(buf, size, buf.length - size)));

        try (Buffer expected = ch.bufferAllocator().copyOf(buf);
             Buffer actual = ch.readOutbound()) {
            assertThat(expected).isEqualTo(actual);
        }
        assertFalse(ch.finish());
    }

    @Test
    public void testSize2Varint() {
        final int size = 2;
        final int num = 266;
        assertThat(ProtobufVarint32LengthFieldPrepender.computeRawVarint32Size(num)).isEqualTo(size);
        final byte[] buf = new byte[size + num];
        /*
          8    A    0    2
          1000 1010 0000 0010
          0000 1010 0000 0010
          0000 0010 0000 1010
           000 0010  000 1010

           0000 0001 0000 1010
           0    1    0    A
          266
         */

        buf[0] = (byte) (0x8A & 0xFF);
        buf[1] = 0x02;
        for (int i = size; i < num + size; ++i) {
            buf[i] = 1;
        }
        Buffer buffer = ch.bufferAllocator().allocate(buf.length - size);
        assertTrue(ch.writeOutbound(buffer.writeBytes(buf, size, buf.length - size)));

        try (Buffer expected = ch.bufferAllocator().copyOf(buf);
        Buffer actual = ch.readOutbound()) {
            assertThat(actual).isEqualTo(expected);
        }
        assertFalse(ch.finish());
    }

    @Test
    public void testSize3Varint() {
        final int size = 3;
        final int num = 0x4000;
        assertThat(ProtobufVarint32LengthFieldPrepender.computeRawVarint32Size(num)).isEqualTo(size);
        final byte[] buf = new byte[size + num];
        /*
          8    0    8    0    0    1
          1000 0000 1000 0000 0000 0001
          0000 0000 0000 0000 0000 0001
          0000 0001 0000 0000 0000 0000
           000 0001  000 0000  000 0000

             0 0000 0100 0000 0000 0000
             0    0    4    0    0    0

         */

        buf[0] = (byte) (0x80 & 0xFF);
        buf[1] = (byte) (0x80 & 0xFF);
        buf[2] = 0x01;
        for (int i = size; i < num + size; ++i) {
            buf[i] = 1;
        }
        Buffer buffer = ch.bufferAllocator().allocate(buf.length - size);
        assertTrue(ch.writeOutbound(buffer.writeBytes(buf, size, buf.length - size)));

        try (Buffer expected = ch.bufferAllocator().copyOf(buf);
        Buffer actual = ch.readOutbound()) {
            assertThat(expected).isEqualTo(actual);
        }
        assertFalse(ch.finish());
    }

    @Test
    public void testSize4Varint() {
        final int size = 4;
        final int num = 0x200000;
        assertThat(ProtobufVarint32LengthFieldPrepender.computeRawVarint32Size(num)).isEqualTo(size);
        final byte[] buf = new byte[size + num];
        /*
          8    0    8    0    8    0    0    1
          1000 0000 1000 0000 1000 0000 0000 0001
          0000 0000 0000 0000 0000 0000 0000 0001
          0000 0001 0000 0000 0000 0000 0000 0000
           000 0001  000 0000  000 0000  000 0000

             0000 0010 0000 0000 0000 0000 0000
             0    2    0    0    0    0    0

         */

        buf[0] = (byte) (0x80 & 0xFF);
        buf[1] = (byte) (0x80 & 0xFF);
        buf[2] = (byte) (0x80 & 0xFF);
        buf[3] = 0x01;
        for (int i = size; i < num + size; ++i) {
            buf[i] = 1;
        }
        Buffer buffer = ch.bufferAllocator().allocate(buf.length - size);
        assertTrue(ch.writeOutbound(buffer.writeBytes(buf, size, buf.length - size)));

        try (Buffer expected = ch.bufferAllocator().copyOf(buf);
        Buffer actual = ch.readOutbound()) {
            assertThat(actual).isEqualTo(expected);
        }
        assertFalse(ch.finish());
    }

    @Test
    public void testTinyEncode() {
        byte[] b = {4, 1, 1, 1, 1};
        Buffer buffer = ch.bufferAllocator().allocate(b.length - 1);
        assertTrue(ch.writeOutbound(buffer.writeBytes(b, 1, b.length - 1)));

        try (Buffer expected = ch.bufferAllocator().copyOf(b);
        Buffer actual = ch.readOutbound()) {
            assertThat(actual).isEqualTo(expected);
        }
        assertFalse(ch.finish());
    }

    @Test
    public void testRegularDecode() {
        byte[] b = new byte[2048];
        for (int i = 2; i < 2048; i++) {
            b[i] = 1;
        }
        b[0] = -2;
        b[1] = 15;
        Buffer buffer = ch.bufferAllocator().allocate(b.length - 2);
        assertTrue(ch.writeOutbound(buffer.writeBytes(b, 2, b.length - 2)));

        try (Buffer expected = ch.bufferAllocator().copyOf(b);
        Buffer actual = ch.readOutbound()) {
            assertThat(actual).isEqualTo(expected);
        }
        assertFalse(ch.finish());
    }
}
