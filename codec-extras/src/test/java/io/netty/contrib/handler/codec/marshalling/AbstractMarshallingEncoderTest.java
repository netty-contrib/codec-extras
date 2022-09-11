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
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.jboss.marshalling.Unmarshaller;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractMarshallingEncoderTest extends AbstractMarshallingTest {

    @Test
    public void testMarshalling() throws Exception {
        @SuppressWarnings("StringOperationCanBeSimplified")
        String testObject = new String("test");

        final MarshallerFactory marshallerFactory = createMarshallerFactory();
        final MarshallingConfiguration configuration = createMarshallingConfig();

        EmbeddedChannel ch = new EmbeddedChannel(createEncoder());

        ch.writeOutbound(testObject);
        assertTrue(ch.finish());

        try (Buffer buffer = ch.readOutbound()) {

            Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(configuration);
            try (Buffer truncatedBuffer = truncate(buffer)) {
                int readableBytes = truncatedBuffer.readableBytes();
                ByteBuffer copy = truncatedBuffer.isDirect() ? ByteBuffer.allocateDirect(readableBytes) :
                        ByteBuffer.allocate(readableBytes);
                truncatedBuffer.copyInto(truncatedBuffer.readerOffset(), copy, 0, readableBytes);
                unmarshaller.start(Marshalling.createByteInput(copy));
                String read = (String) unmarshaller.readObject();
                assertEquals(testObject, read);

                assertEquals(-1, unmarshaller.read());
            }

            assertNull(ch.readOutbound());

            unmarshaller.finish();
            unmarshaller.close();
        }
    }

    protected Buffer truncate(Buffer buf) {
        return buf.split();
    }

    protected ChannelHandler createEncoder() {
        return new MarshallingEncoder(createProvider());
    }

    protected MarshallerProvider createProvider() {
        return new DefaultMarshallerProvider(createMarshallerFactory(), createMarshallingConfig());
    }

    protected abstract MarshallerFactory createMarshallerFactory();

    protected abstract MarshallingConfiguration createMarshallingConfig();

}
