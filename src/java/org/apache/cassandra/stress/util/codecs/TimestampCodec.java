package org.apache.cassandra.stress.util.codecs;

import java.nio.ByteBuffer;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import shaded.com.datastax.oss.driver.api.core.ProtocolVersion;
import shaded.com.datastax.oss.driver.api.core.type.DataType;
import shaded.com.datastax.oss.driver.api.core.type.DataTypes;
import shaded.com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import shaded.com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import shaded.com.datastax.oss.driver.api.core.type.reflect.GenericType;
import shaded.com.datastax.oss.driver.internal.core.util.Strings;
import shaded.com.datastax.oss.driver.shaded.netty.util.concurrent.FastThreadLocal;

public class TimestampCodec implements TypeCodec<Date> {
  private static GenericType<Date> DATE_TYPE = GenericType.of(Date.class);

  private static final String[] DATE_STRING_PATTERNS = new String[]{"yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mmX", "yyyy-MM-dd'T'HH:mmXX", "yyyy-MM-dd'T'HH:mmXXX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mm:ssXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ss.SSSXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm z", "yyyy-MM-dd'T'HH:mm:ss z", "yyyy-MM-dd'T'HH:mm:ss.SSS z", "yyyy-MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mmX", "yyyy-MM-dd HH:mmXX", "yyyy-MM-dd HH:mmXXX", "yyyy-MM-dd HH:mm:ssX", "yyyy-MM-dd HH:mm:ssXX", "yyyy-MM-dd HH:mm:ssXXX", "yyyy-MM-dd HH:mm:ss.SSSX", "yyyy-MM-dd HH:mm:ss.SSSXX", "yyyy-MM-dd HH:mm:ss.SSSXXX", "yyyy-MM-dd HH:mm z", "yyyy-MM-dd HH:mm:ss z", "yyyy-MM-dd HH:mm:ss.SSS z", "yyyy-MM-dd", "yyyy-MM-ddX", "yyyy-MM-ddXX", "yyyy-MM-ddXXX", "yyyy-MM-dd z"};
  private final FastThreadLocal<SimpleDateFormat> parser;
  private final FastThreadLocal<SimpleDateFormat> formatter;

  public TimestampCodec() {
    this(ZoneId.systemDefault());
  }

  public TimestampCodec(final ZoneId defaultZoneId) {
    this.parser = new FastThreadLocal<SimpleDateFormat>() {
      protected SimpleDateFormat initialValue() {
        SimpleDateFormat parser = new SimpleDateFormat();
        parser.setLenient(false);
        parser.setTimeZone(TimeZone.getTimeZone(defaultZoneId));
        return parser;
      }
    };
    this.formatter = new FastThreadLocal<SimpleDateFormat>() {
      protected SimpleDateFormat initialValue() {
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        parser.setTimeZone(TimeZone.getTimeZone(defaultZoneId));
        return parser;
      }
    };
  }

  public GenericType<Date> getJavaType() {
    return DATE_TYPE;
  }

  public DataType getCqlType() {
    return DataTypes.TIMESTAMP;
  }

  public boolean accepts(Object value) {
    return value instanceof Date;
  }

  public boolean accepts(Class<?> javaClass) {
    return javaClass == Date.class;
  }

  public ByteBuffer encode(Date value, ProtocolVersion protocolVersion) {
    return value == null ? null : TypeCodecs.BIGINT.encodePrimitive(value.getTime(), protocolVersion);
  }

  
  public Date decode( ByteBuffer bytes,  ProtocolVersion protocolVersion) {
    return bytes != null && bytes.remaining() != 0 ? new Date(TypeCodecs.BIGINT.decodePrimitive(bytes, protocolVersion)) : null;
  }

  
  public String format( Date value) {
    return value == null ? "NULL" : Strings.quote(this.formatter.get().format(value));
  }

  
  public Date parse( String value) {
    if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("NULL")) {
      String unquoted = Strings.unquote(value);
      if (Strings.isLongLiteral(unquoted)) {
        try {
          return new Date(Long.parseLong(unquoted));
        } catch (NumberFormatException var15) {
          throw new IllegalArgumentException(String.format("Cannot parse timestamp value from \"%s\"", value));
        }
      } else if (!Strings.isQuoted(value)) {
        throw new IllegalArgumentException(String.format("Alphanumeric timestamp literal must be quoted: \"%s\"", value));
      } else {
        SimpleDateFormat parser = (SimpleDateFormat)this.parser.get();
        TimeZone timeZone = parser.getTimeZone();
        ParsePosition pos = new ParsePosition(0);

        for(String pattern : DATE_STRING_PATTERNS) {
          parser.applyPattern(pattern);
          pos.setIndex(0);

          Date date = parser.parse(unquoted, pos);
          if (date == null || pos.getIndex() != unquoted.length()) {
            continue;
          }

          return date;
        }

        throw new IllegalArgumentException(String.format("Cannot parse timestamp value from \"%s\"", value));
      }
    } else {
      return null;
    }
  }

  
  public Optional<Integer> serializedSize() {
    return Optional.of(8);
  }
}
