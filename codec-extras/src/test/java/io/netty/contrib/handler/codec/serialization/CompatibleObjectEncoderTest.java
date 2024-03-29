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
package io.netty.contrib.handler.codec.serialization;

import io.netty5.buffer.BufferInputStream;
import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class CompatibleObjectEncoderTest {
    private static void testEncode(EmbeddedChannel channel, TestSerializable original)
            throws IOException, ClassNotFoundException {
        channel.writeOutbound(original);
        try (Buffer buf = channel.readOutbound();
             ObjectInputStream ois = new ObjectInputStream(new BufferInputStream(buf.send()))) {
            assertEquals(original, ois.readObject());
        }
    }

    @Test
    public void testMultipleEncodeReferenceCount() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new CompatibleObjectEncoder());
        testEncode(channel, new TestSerializable(6, 8));
        testEncode(channel, new TestSerializable(10, 5));
        testEncode(channel, new TestSerializable(1, 5));
        assertFalse(channel.finishAndReleaseAll());
    }

    private static final class TestSerializable implements Serializable {
        private static final long serialVersionUID = 2235771472534930360L;

        public final int x;
        public final int y;

        TestSerializable(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TestSerializable)) {
                return false;
            }
            TestSerializable rhs = (TestSerializable) o;
            return x == rhs.x && y == rhs.y;
        }

        @Override
        public int hashCode() {
            return 31 * (31 + x) + y;
        }
    }
}
