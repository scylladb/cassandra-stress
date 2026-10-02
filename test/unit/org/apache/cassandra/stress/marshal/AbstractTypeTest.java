package org.apache.cassandra.stress.marshal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

import org.junit.Test;

import org.apache.cassandra.stress.util.UUIDGen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AbstractTypeTest
{
    private static final class Sample
    {
        final AbstractType<Object> type;
        final Object value;

        @SuppressWarnings("unchecked")
        Sample(AbstractType<?> type, Object value)
        {
            this.type = (AbstractType<Object>) type;
            this.value = value;
        }
    }

    private static Map<String, Sample> samples() throws IOException
    {
        Map<String, Sample> samples = new LinkedHashMap<>();
        samples.put("ascii", new Sample(AsciiType.instance, "stress-1"));
        samples.put("text", new Sample(UTF8Type.instance, "zażółć ✓"));
        samples.put("blob", new Sample(BytesType.instance, ByteBuffer.wrap(new byte[]{ 0, (byte) 0xff, 0x10 })));
        samples.put("boolean_true", new Sample(BooleanType.instance, true));
        samples.put("boolean_false", new Sample(BooleanType.instance, false));
        samples.put("tinyint", new Sample(ByteType.instance, (byte) -7));
        samples.put("smallint", new Sample(ShortType.instance, (short) 1234));
        samples.put("int", new Sample(Int32Type.instance, -123456));
        samples.put("bigint", new Sample(LongType.instance, 1234567890123L));
        samples.put("float", new Sample(FloatType.instance, 3.25f));
        samples.put("double", new Sample(DoubleType.instance, -2.5e10));
        samples.put("decimal", new Sample(DecimalType.instance, new BigDecimal("12345.6789")));
        samples.put("varint", new Sample(IntegerType.instance, new BigInteger("-98765432109876543210")));
        samples.put("inet4", new Sample(InetAddressType.instance, InetAddress.getByName("192.168.1.10")));
        samples.put("inet6", new Sample(InetAddressType.instance, InetAddress.getByName("::1")));
        samples.put("uuid", new Sample(UUIDType.instance, UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301")));
        samples.put("timeuuid", new Sample(TimeUUIDType.instance, UUIDGen.getTimeUUID(1700000000123L, 0L, 0x8000123456789abcL)));
        samples.put("timestamp", new Sample(DateType.instance, new Date(1700000000123L)));
        samples.put("date", new Sample(SimpleDateType.instance, Integer.MIN_VALUE + 19675));
        samples.put("time", new Sample(TimeType.instance, 45296789000000L));
        samples.put("list_int", new Sample(ListType.getInstance(Int32Type.instance, true), Arrays.asList(1, 2, 3)));
        samples.put("set_text", new Sample(SetType.getInstance(UTF8Type.instance, true), new LinkedHashSet<>(Arrays.asList("a", "bc"))));
        samples.put("set_int", new Sample(SetType.getInstance(Int32Type.instance, true), new LinkedHashSet<>(Arrays.asList(3, -1, 2, 300))));
        samples.put("set_bigint", new Sample(SetType.getInstance(LongType.instance, true), new LinkedHashSet<>(Arrays.asList(5L, -9000000000L, 7L))));
        samples.put("set_smallint", new Sample(SetType.getInstance(ShortType.instance, true), new LinkedHashSet<>(Arrays.asList((short) 300, (short) -2, (short) 1))));
        samples.put("set_tinyint", new Sample(SetType.getInstance(ByteType.instance, true), new LinkedHashSet<>(Arrays.asList((byte) 9, (byte) -9, (byte) 0))));
        samples.put("set_text_unsorted", new Sample(SetType.getInstance(UTF8Type.instance, true), new LinkedHashSet<>(Arrays.asList("zz", "\u00e9", "a"))));
        samples.put("set_boolean", new Sample(SetType.getInstance(BooleanType.instance, true), new LinkedHashSet<>(Arrays.asList(true, false))));
        samples.put("set_float", new Sample(SetType.getInstance(FloatType.instance, true), new LinkedHashSet<>(Arrays.asList(2.5f, -1.0f, 0.25f))));
        samples.put("set_double", new Sample(SetType.getInstance(DoubleType.instance, true), new LinkedHashSet<>(Arrays.asList(1e10, -3.5, 0.0))));
        samples.put("set_decimal", new Sample(SetType.getInstance(DecimalType.instance, true), new LinkedHashSet<>(Arrays.asList(new BigDecimal("10.5"), new BigDecimal("-2"), new BigDecimal("3.14159")))));
        samples.put("set_varint", new Sample(SetType.getInstance(IntegerType.instance, true), new LinkedHashSet<>(Arrays.asList(new BigInteger("100000000000000000000"), new BigInteger("-5"), new BigInteger("127"), new BigInteger("128")))));
        samples.put("set_uuid", new Sample(SetType.getInstance(UUIDType.instance, true), new LinkedHashSet<>(Arrays.asList(UUID.fromString("ffffffff-ffff-4fff-bfff-ffffffffffff"), UUIDGen.getTimeUUID(1700000000999L, 0L, 0x8000123456789abcL), UUID.fromString("00000000-0000-4000-8000-000000000001"), UUIDGen.getTimeUUID(1600000000000L, 0L, 0x8000123456789abcL)))));
        samples.put("set_timeuuid", new Sample(SetType.getInstance(TimeUUIDType.instance, true), new LinkedHashSet<>(Arrays.asList(UUIDGen.getTimeUUID(1700000000999L, 0L, 0x8000123456789abcL), UUIDGen.getTimeUUID(1600000000000L, 0L, 0x8000123456789abcL), UUIDGen.getTimeUUID(1600000000000L, 0L, 0x80ff123456789abcL)))));
        samples.put("set_inet", new Sample(SetType.getInstance(InetAddressType.instance, true), new LinkedHashSet<>(Arrays.asList(InetAddress.getByName("10.0.0.2"), InetAddress.getByName("10.0.0.1")))));
        samples.put("set_timestamp", new Sample(SetType.getInstance(DateType.instance, true), new LinkedHashSet<>(Arrays.asList(new Date(2000L), new Date(1000L)))));
        samples.put("list_text", new Sample(ListType.getInstance(UTF8Type.instance, true), Arrays.asList("b", "a")));
        return samples;
    }

    private static Map<String, String> fixture(String name) throws IOException
    {
        Map<String, String> expected = new LinkedHashMap<>();
        try (InputStream in = AbstractTypeTest.class.getClassLoader().getResourceAsStream("stress/marshal/" + name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.isBlank())
                    continue;
                String[] parts = line.split(" ");
                expected.put(parts[0], parts[1]);
            }
        }
        return expected;
    }

    private static String hex(ByteBuffer bytes)
    {
        byte[] array = new byte[bytes.remaining()];
        bytes.duplicate().get(array);
        return HexFormat.of().formatHex(array);
    }

    @Test
    public void decomposeMatchesMasterBytes() throws IOException
    {
        Map<String, Sample> samples = samples();
        Map<String, String> expected = fixture("master.txt");
        assertEquals(samples.keySet(), expected.keySet());
        for (Map.Entry<String, Sample> e : samples.entrySet())
            assertEquals(e.getKey(), expected.get(e.getKey()), hex(e.getValue().type.decompose(e.getValue().value)));
    }

    @Test
    public void composeReadsMasterBytes() throws IOException
    {
        Map<String, Sample> samples = samples();
        for (Map.Entry<String, String> e : fixture("master.txt").entrySet())
        {
            Sample sample = samples.get(e.getKey());
            ByteBuffer bytes = ByteBuffer.wrap(HexFormat.of().parseHex(e.getValue()));
            Object composed = sample.type.compose(bytes);
            if (sample.value instanceof ByteBuffer)
                assertEquals(e.getKey(), hex((ByteBuffer) sample.value), hex((ByteBuffer) composed));
            else if (sample.value instanceof LinkedHashSet)
                assertEquals(e.getKey(), sample.value, composed);
            else
                assertEquals(e.getKey(), sample.value, composed);
        }
    }

    @Test
    public void bytesPrintAsLowerCaseHex()
    {
        assertEquals("00ff10", BytesType.instance.getString(ByteBuffer.wrap(new byte[]{ 0, (byte) 0xff, 0x10 })));
    }
}
