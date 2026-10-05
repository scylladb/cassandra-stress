package org.apache.cassandra.stress.marshal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.cassandra.stress.util.UUIDGen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AbstractTypeTest {
    private record Sample(AbstractType<Object> type, Object value) {}

    private static final Map<String, Sample> SAMPLES;
    private static final Map<String, String> MASTER;

    static {
        try {
            SAMPLES = samples();
            MASTER = fixture("master.txt");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Sample sample(AbstractType<?> type, Object value) {
        return new Sample((AbstractType<Object>) type, value);
    }

    private static Map<String, Sample> samples() throws IOException {
        Map<String, Sample> samples = new LinkedHashMap<>();
        samples.put("ascii", sample(AsciiType.instance, "stress-1"));
        samples.put("text", sample(UTF8Type.instance, "zażółć ✓"));
        samples.put("blob", sample(BytesType.instance, ByteBuffer.wrap(new byte[] {0, (byte) 0xff, 0x10})));
        samples.put("boolean_true", sample(BooleanType.instance, true));
        samples.put("boolean_false", sample(BooleanType.instance, false));
        samples.put("tinyint", sample(ByteType.instance, (byte) -7));
        samples.put("smallint", sample(ShortType.instance, (short) 1234));
        samples.put("int", sample(Int32Type.instance, -123456));
        samples.put("bigint", sample(LongType.instance, 1234567890123L));
        samples.put("float", sample(FloatType.instance, 3.25f));
        samples.put("double", sample(DoubleType.instance, -2.5e10));
        samples.put("decimal", sample(DecimalType.instance, new BigDecimal("12345.6789")));
        samples.put("varint", sample(IntegerType.instance, new BigInteger("-98765432109876543210")));
        samples.put("inet4", sample(InetAddressType.instance, InetAddress.getByName("192.168.1.10")));
        samples.put("inet6", sample(InetAddressType.instance, InetAddress.getByName("::1")));
        samples.put("uuid", sample(UUIDType.instance, UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301")));
        samples.put(
                "timeuuid",
                sample(TimeUUIDType.instance, UUIDGen.getTimeUUID(1700000000123L, 0L, 0x8000123456789abcL)));
        samples.put("timestamp", sample(DateType.instance, new Date(1700000000123L)));
        samples.put("date", sample(SimpleDateType.instance, Integer.MIN_VALUE + 19675));
        samples.put("time", sample(TimeType.instance, 45296789000000L));
        samples.put("list_int", sample(ListType.getInstance(Int32Type.instance, true), Arrays.asList(1, 2, 3)));
        samples.put(
                "set_text",
                sample(SetType.getInstance(UTF8Type.instance, true), new LinkedHashSet<>(Arrays.asList("a", "bc"))));
        samples.put(
                "set_int",
                sample(
                        SetType.getInstance(Int32Type.instance, true),
                        new LinkedHashSet<>(Arrays.asList(3, -1, 2, 300))));
        samples.put(
                "set_bigint",
                sample(
                        SetType.getInstance(LongType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(5L, -9000000000L, 7L))));
        samples.put(
                "set_smallint",
                sample(
                        SetType.getInstance(ShortType.instance, true),
                        new LinkedHashSet<>(Arrays.asList((short) 300, (short) -2, (short) 1))));
        samples.put(
                "set_tinyint",
                sample(
                        SetType.getInstance(ByteType.instance, true),
                        new LinkedHashSet<>(Arrays.asList((byte) 9, (byte) -9, (byte) 0))));
        samples.put(
                "set_text_unsorted",
                sample(
                        SetType.getInstance(UTF8Type.instance, true),
                        new LinkedHashSet<>(Arrays.asList("zz", "\u00e9", "a"))));
        samples.put(
                "set_boolean",
                sample(
                        SetType.getInstance(BooleanType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(true, false))));
        samples.put(
                "set_float",
                sample(
                        SetType.getInstance(FloatType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(2.5f, -1.0f, 0.25f))));
        samples.put(
                "set_double",
                sample(
                        SetType.getInstance(DoubleType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(1e10, -3.5, 0.0))));
        samples.put(
                "set_decimal",
                sample(
                        SetType.getInstance(DecimalType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(
                                new BigDecimal("10.5"), new BigDecimal("-2"), new BigDecimal("3.14159")))));
        samples.put(
                "set_varint",
                sample(
                        SetType.getInstance(IntegerType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(
                                new BigInteger("100000000000000000000"),
                                new BigInteger("-5"),
                                new BigInteger("127"),
                                new BigInteger("128")))));
        samples.put(
                "set_uuid",
                sample(
                        SetType.getInstance(UUIDType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(
                                UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff"),
                                UUIDGen.getTimeUUID(1700000000999L, 0L, 0x8000123456789abcL),
                                UUID.fromString("00000000-0000-4000-8000-000000000001"),
                                UUIDGen.getTimeUUID(1600000000000L, 0L, 0x8000123456789abcL)))));
        samples.put(
                "set_timeuuid",
                sample(
                        SetType.getInstance(TimeUUIDType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(
                                UUIDGen.getTimeUUID(1700000000999L, 0L, 0x8000123456789abcL),
                                UUIDGen.getTimeUUID(1600000000000L, 0L, 0x8000123456789abcL),
                                UUIDGen.getTimeUUID(1600000000000L, 0L, 0x80ff123456789abcL)))));
        samples.put(
                "set_inet",
                sample(
                        SetType.getInstance(InetAddressType.instance, true),
                        new LinkedHashSet<>(
                                Arrays.asList(InetAddress.getByName("10.0.0.2"), InetAddress.getByName("10.0.0.1")))));
        samples.put(
                "set_timestamp",
                sample(
                        SetType.getInstance(DateType.instance, true),
                        new LinkedHashSet<>(Arrays.asList(new Date(2000L), new Date(1000L)))));
        samples.put("list_text", sample(ListType.getInstance(UTF8Type.instance, true), Arrays.asList("b", "a")));
        return samples;
    }

    private static Map<String, String> fixture(String name) throws IOException {
        Map<String, String> expected = new LinkedHashMap<>();
        try (InputStream in = AbstractTypeTest.class.getClassLoader().getResourceAsStream("stress/marshal/" + name);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(" ");
                expected.put(parts[0], parts[1]);
            }
        }
        return expected;
    }

    private static String hex(ByteBuffer bytes) {
        byte[] array = new byte[bytes.remaining()];
        bytes.duplicate().get(array);
        return HexFormat.of().formatHex(array);
    }

    static Stream<String> sampleNames() {
        return SAMPLES.keySet().stream();
    }

    @Test
    void fixtureCoversEverySample() {
        assertEquals(SAMPLES.keySet(), MASTER.keySet());
    }

    @ParameterizedTest
    @MethodSource("sampleNames")
    void decomposeMatchesMasterBytes(String name) {
        Sample sample = SAMPLES.get(name);
        assertEquals(MASTER.get(name), hex(sample.type().decompose(sample.value())));
    }

    @ParameterizedTest
    @MethodSource("sampleNames")
    void composeReadsMasterBytes(String name) {
        Sample sample = SAMPLES.get(name);
        Object composed = sample.type().compose(ByteBuffer.wrap(HexFormat.of().parseHex(MASTER.get(name))));
        if (sample.value() instanceof ByteBuffer expected) assertEquals(hex(expected), hex((ByteBuffer) composed));
        else assertEquals(sample.value(), composed);
    }

    @Test
    void bytesPrintAsLowerCaseHex() {
        assertEquals("00ff10", BytesType.instance.getString(ByteBuffer.wrap(new byte[] {0, (byte) 0xff, 0x10})));
    }
}
