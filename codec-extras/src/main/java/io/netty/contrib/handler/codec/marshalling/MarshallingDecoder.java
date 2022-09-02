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

import io.netty5.buffer.api.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty5.handler.codec.TooLongFrameException;
import org.jboss.marshalling.ByteInput;
import org.jboss.marshalling.Unmarshaller;

import java.io.StreamCorruptedException;

/**
 * Decoder which MUST be used with {@link MarshallingEncoder}.
 * <p>
 * A {@link LengthFieldBasedFrameDecoder} which use an {@link Unmarshaller} to read the Object out
 * of the {@link Buffer}.
 */
public class MarshallingDecoder extends LengthFieldBasedFrameDecoder {

    private final UnmarshallerProvider provider;

    /**
     * Creates a new decoder whose maximum object size is {@code 1048576}
     * bytes.  If the size of the received object is greater than
     * {@code 1048576} bytes, a {@link StreamCorruptedException} will be
     * raised.
     */
    public MarshallingDecoder(UnmarshallerProvider provider) {
        this(provider, 1048576);
    }

    /**
     * Creates a new decoder with the specified maximum object size.
     *
     * @param maxObjectSize the maximum byte length of the serialized object.
     *                      if the length of the received object is greater
     *                      than this value, {@link TooLongFrameException}
     *                      will be raised.
     */
    public MarshallingDecoder(UnmarshallerProvider provider, int maxObjectSize) {
        super(maxObjectSize, 0, 4, 0, 4);
        this.provider = provider;
    }

    @Override
    protected Object decode0(ChannelHandlerContext ctx, Buffer in) throws Exception {
        Buffer frame = (Buffer) super.decode0(ctx, in);
        if (frame == null) {
            return null;
        }

        // Call close in a finally block as the ReplayingDecoder will throw an Error if not enough bytes are
        // readable. This helps to be sure that we do not leak resource
        try (frame;
             Unmarshaller unmarshaller = provider.getUnmarshaller(ctx)) {
            ByteInput input = new ChannelBufferByteInput(frame);
            unmarshaller.start(input);
            Object obj = unmarshaller.readObject();
            unmarshaller.finish();
            return obj;
        }
    }
}
