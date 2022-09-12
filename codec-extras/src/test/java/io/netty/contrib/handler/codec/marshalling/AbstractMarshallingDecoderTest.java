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
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.MarshallingConfiguration;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class AbstractMarshallingDecoderTest extends AbstractMarshallingTest {
    @SuppressWarnings("StringOperationCanBeSimplified")
    private static final String testObject = new String("test");

    @Test
    public void testSimpleUnmarshalling() throws IOException {
        MarshallerFactory marshallerFactory = createMarshallerFactory();
        MarshallingConfiguration configuration = createMarshallingConfig();

        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(Integer.MAX_VALUE));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
        marshaller.start(Marshalling.createByteOutput(bout));
        marshaller.writeObject(testObject);
        marshaller.finish();
        marshaller.close();

        byte[] testBytes = bout.toByteArray();

        ch.writeInbound(input(testBytes));
        assertTrue(ch.finish());

        String unmarshalled = ch.readInbound();

        assertEquals(testObject, unmarshalled);

        assertNull(ch.readInbound());
    }

    protected Buffer input(byte[] input) {
        return preferredAllocator().copyOf(input);
    }

    @Test
    public void testFragmentedUnmarshalling() throws IOException {
        MarshallerFactory marshallerFactory = createMarshallerFactory();
        MarshallingConfiguration configuration = createMarshallingConfig();

        EmbeddedChannel ch = new EmbeddedChannel(createDecoder(Integer.MAX_VALUE));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
        marshaller.start(Marshalling.createByteOutput(bout));
        marshaller.writeObject(testObject);
        marshaller.finish();
        marshaller.close();

        byte[] testBytes = bout.toByteArray();

        Buffer buffer = input(testBytes);
        Buffer slice = buffer.readSplit(2);

        ch.writeInbound(slice);
        ch.writeInbound(buffer);
        assertTrue(ch.finish());

        String unmarshalled = ch.readInbound();

        assertEquals(testObject, unmarshalled);

        assertNull(ch.readInbound());
    }

    @Test
    public void testTooBigObject() throws IOException {
        MarshallerFactory marshallerFactory = createMarshallerFactory();
        MarshallingConfiguration configuration = createMarshallingConfig();

        ChannelHandler mDecoder = createDecoder(4);
        EmbeddedChannel ch = new EmbeddedChannel(mDecoder);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        Marshaller marshaller = marshallerFactory.createMarshaller(configuration);
        marshaller.start(Marshalling.createByteOutput(bout));
        marshaller.writeObject(testObject);
        marshaller.finish();
        marshaller.close();

        byte[] testBytes = bout.toByteArray();
        onTooBigFrame(ch, input(testBytes));
    }

    protected void onTooBigFrame(EmbeddedChannel ch, Buffer input) {
        ch.writeInbound(input);
        assertFalse(ch.isActive());
    }

    protected ChannelHandler createDecoder(int maxObjectSize) {
        return new MarshallingDecoder(createProvider(createMarshallerFactory(),
                createMarshallingConfig()), maxObjectSize);
    }

    protected UnmarshallerProvider createProvider(MarshallerFactory factory, MarshallingConfiguration config) {
        return new DefaultUnmarshallerProvider(factory, config);
    }

    protected abstract MarshallerFactory createMarshallerFactory();

    protected abstract MarshallingConfiguration createMarshallingConfig();

}
