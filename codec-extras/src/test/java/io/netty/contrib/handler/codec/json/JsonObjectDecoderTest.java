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
package io.netty.contrib.handler.codec.json;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.CorruptedFrameException;
import io.netty5.handler.codec.TooLongFrameException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonObjectDecoderTest {
    private static Buffer copiedBuffer(BufferAllocator allocator, String data, Charset charset) {
        return copiedBuffer(allocator, data.getBytes(charset));
    }

    private static Buffer copiedBuffer(BufferAllocator allocator, byte[] bytes) {
        return allocator.copyOf(bytes);
    }

    private static void doTestStreamJsonArrayOverMultipleWrites(int indexDataAvailable,
                                                                String[] array, String[] result) {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder(true));

        boolean dataAvailable;
        for (String part : array) {
            dataAvailable = ch.writeInbound(copiedBuffer(ch.bufferAllocator(), part, StandardCharsets.UTF_8));
            if (indexDataAvailable > 0) {
                assertFalse(dataAvailable);
            } else {
                assertTrue(dataAvailable);
            }
            indexDataAvailable--;
        }

        for (String part : result) {
            try(Buffer res = ch.readInbound()) {
                assertEquals(part, res.toString(StandardCharsets.UTF_8));
            }
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testJsonObjectOverMultipleWrites() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String objectPart1 = "{ \"firstname\": \"John";
        String objectPart2 = "\" ,\n \"surname\" :";
        String objectPart3 = "\"Doe\", age:22   \n}";

        // Test object
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(),"  \n\n  " + objectPart1, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(),objectPart2, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(),objectPart3 + "   \n\n  \n", StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals(objectPart1 + objectPart2 + objectPart3, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testMultipleJsonObjectsOverMultipleWrites() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String objectPart1 = "{\"name\":\"Jo";
        String objectPart2 = "hn\"}{\"name\":\"John\"}{\"name\":\"Jo";
        String objectPart3 = "hn\"}";

        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), objectPart1, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), objectPart2, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), objectPart3, StandardCharsets.UTF_8));

        for (int i = 0; i < 3; i++) {
            try(Buffer res = ch.readInbound()) {
                assertEquals("{\"name\":\"John\"}", res.toString(StandardCharsets.UTF_8));
            }
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testJsonArrayOverMultipleWrites() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String arrayPart1 = "[{\"test";
        String arrayPart2 = "case\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"";
        String arrayPart3 = "  }\n\n    , ";
        String arrayPart4 = "{\"testcase\" : \"Streaming string me";
        String arrayPart5 = "ssage\"} ]";

        // Test array
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "   " + arrayPart1, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), arrayPart2, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), arrayPart3, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), arrayPart4, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), arrayPart5 + "      ", StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals(arrayPart1 + arrayPart2 + arrayPart3 + arrayPart4 + arrayPart5, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testStreamJsonArrayOverMultipleWrites1() {
        String[] array = {
                "   [{\"test",
                "case\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"",
                "  }\n\n    , ",
                "{\"testcase\" : \"Streaming string me",
                "ssage\"} ]      "
        };
        String[] result = {
                "{\"testcase\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"  }",
                "{\"testcase\" : \"Streaming string message\"}"
        };
        doTestStreamJsonArrayOverMultipleWrites(2, array, result);
    }

    @Test
    public void testStreamJsonArrayOverMultipleWrites2() {
        String[] array = {
                "   [{\"test",
                "case\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"",
                "  }\n\n    , {\"test",
                "case\" : \"Streaming string me",
                "ssage\"} ]      "
        };
        String[] result = {
                "{\"testcase\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"  }",
                "{\"testcase\" : \"Streaming string message\"}"
        };
        doTestStreamJsonArrayOverMultipleWrites(2, array, result);
    }

    @Test
    public void testStreamJsonArrayOverMultipleWrites3() {
        String[] array = {
                "   [{\"test",
                "case\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"",
                "  }\n\n    , [{\"test",
                "case\" : \"Streaming string me",
                "ssage\"}] ]      "
        };
        String[] result = {
                "{\"testcase\"  : \"\\\"}]Escaped dou\\\"ble quotes \\\" in JSON str\\\"ing\"  }",
                "[{\"testcase\" : \"Streaming string message\"}]"
        };
        doTestStreamJsonArrayOverMultipleWrites(2, array, result);
    }

    @Test
    public void testSingleByteStream() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String json = "{\"foo\" : {\"bar\" : [{},{}]}}";
        for (byte c : json.getBytes(StandardCharsets.UTF_8)) {
            ch.writeInbound(copiedBuffer(ch.bufferAllocator(), new byte[]{c}));
        }

        try(Buffer res = ch.readInbound()) {
            assertEquals(json, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testBackslashInString1() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        // {"foo" : "bar\""}
        String json = "{\"foo\" : \"bar\\\"\"}";
        System.out.println(json);
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), json, StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals(json, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testBackslashInString2() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        // {"foo" : "bar\\"}
        String json = "{\"foo\" : \"bar\\\\\"}";
        System.out.println(json);
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), json, StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals(json, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testBackslashInString3() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        // {"foo" : "bar\\\""}
        String json = "{\"foo\" : \"bar\\\\\\\"\"}";
        System.out.println(json);
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), json, StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals(json, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testMultipleJsonObjectsInOneWrite() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String object1 = "{\"key\" : \"value1\"}",
                object2 = "{\"key\" : \"value2\"}",
                object3 = "{\"key\" : \"value3\"}";

        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), object1 + object2 + object3, StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals(object1, res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals(object2, res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals(object3, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testNonJsonContent1() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        try {
            assertThrows(CorruptedFrameException.class,
                    () -> ch.writeInbound(copiedBuffer(ch.bufferAllocator(),"  b [1,2,3]", StandardCharsets.UTF_8)));
        } finally {
            assertFalse(ch.finish());
        }
    }

    @Test
    public void testNonJsonContent2() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "  [1,2,3]  ", StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals("[1,2,3]", res.toString(StandardCharsets.UTF_8));
        }

        try {
            assertThrows(CorruptedFrameException.class,
                    () -> ch.writeInbound(copiedBuffer(ch.bufferAllocator(), " a {\"key\" : 10}", StandardCharsets.UTF_8)));
        } finally {
            assertFalse(ch.finish());
        }
    }

    @Test
    public void testMaxObjectLength() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder(6));
        try {
            assertThrows(TooLongFrameException.class,
                    () -> ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "[2,4,5]", StandardCharsets.UTF_8)));
        } finally {
            assertFalse(ch.finish());
        }
    }

    @Test
    public void testOneJsonObjectPerWrite() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String object1 = "{\"key\" : \"value1\"}",
                object2 = "{\"key\" : \"value2\"}",
                object3 = "{\"key\" : \"value3\"}";

        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), object1, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), object2, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), object3, StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals(object1, res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals(object2, res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals(object3, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testSpecialJsonCharsInString() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        String object = "{ \"key\" : \"[]{}}\\\"}}'}\"}";
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), object, StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals(object, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testStreamArrayElementsSimple() {
        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder(Integer.MAX_VALUE, true));

        String array = "[  12, \"bla\"  , 13.4   \t  ,{\"key0\" : [1,2], \"key1\" : 12, \"key2\" : {}} , " +
                "true, false, null, [\"bla\", {}, [1,2,3]] ]";
        String object = "{\"bla\" : \"blub\"}";
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), array, StandardCharsets.UTF_8));
        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), object, StandardCharsets.UTF_8));

        try(Buffer res = ch.readInbound()) {
            assertEquals("12", res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals("\"bla\"", res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals("13.4", res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals("{\"key0\" : [1,2], \"key1\" : 12, \"key2\" : {}}", res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals("true", res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals("false", res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals("null", res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals("[\"bla\", {}, [1,2,3]]", res.toString(StandardCharsets.UTF_8));
        }
        try(Buffer res = ch.readInbound()) {
            assertEquals(object, res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }

    @Test
    public void testCorruptedFrameException() {
        final String part1 = "{\"a\":{\"b\":{\"c\":{ \"d\":\"27301\", \"med\":\"d\", \"path\":\"27310\"} }," +
                " \"status\":\"OK\" } }{\"";
        final String part2 = "a\":{\"b\":{\"c\":{\"ory\":[{\"competi\":[{\"event\":[{" + "\"externalI\":{\"external\"" +
                ":[{\"id\":\"O\"} ]";

        EmbeddedChannel ch = new EmbeddedChannel(new JsonObjectDecoder());

        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), part1, StandardCharsets.UTF_8));
        try(Buffer res = ch.readInbound()) {
            assertEquals("{\"a\":{\"b\":{\"c\":{ \"d\":\"27301\", \"med\":\"d\", \"path\":\"27310\"} }, " +
                    "\"status\":\"OK\" } }", res.toString(StandardCharsets.UTF_8));
        }

        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), part2, StandardCharsets.UTF_8));
        try(Buffer res = ch.readInbound()) {
            assertNull(res);
        }

        ch.writeInbound(copiedBuffer(ch.bufferAllocator(), "}}]}]}]}}}}", StandardCharsets.UTF_8));
        try(Buffer res = ch.readInbound()) {
            assertEquals("{\"a\":{\"b\":{\"c\":{\"ory\":[{\"competi\":[{\"event\":[{" + "\"externalI\":{" +
                    "\"external\":[{\"id\":\"O\"} ]}}]}]}]}}}}", res.toString(StandardCharsets.UTF_8));
        }

        assertFalse(ch.finish());
    }
}
