// SPDX-License-Identifier: Apache-2.0
package org.apache.cassandra.stress.marshal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class UTF8Serializer extends AbstractTextSerializer
{
    public static final UTF8Serializer instance = new UTF8Serializer();

    private UTF8Serializer()
    {
        super(StandardCharsets.UTF_8);
    }

    public void validate(ByteBuffer bytes) throws MarshalException
    {
        if (!UTF8Validator.validate(bytes))
            throw new MarshalException("String didn't validate.");
    }

    static class UTF8Validator
    {
        enum State
        {
            START,
            TWO,
            TWO_80,
            THREE_a0bf,
            THREE_80bf_1,
            THREE_80bf_2,
            FOUR_90bf,
            FOUR_80bf_3,
        };

        static boolean validate(ByteBuffer buf)
        {
            if (buf == null)
                return false;

            buf = buf.slice();
            int b = 0;
            State state = State.START;
            while (buf.remaining() > 0)
            {
                b = buf.get();
                switch (state)
                {
                    case START:
                        if (b >= 0)
                        {
                            if (b > 127)
                                return false;
                        }
                        else if ((b >> 5) == -2)
                        {
                            if (b == (byte) 0xc0)

                                state = State.TWO_80;
                            else if ((b & 0x1e) == 0)
                                return false;
                            else
                                state = State.TWO;
                        }
                        else if ((b >> 4) == -2)
                        {
                            if (b == (byte)0xe0)
                                state = State.THREE_a0bf;
                            else
                                state = State.THREE_80bf_2;
                            break;
                        }
                        else if ((b >> 3) == -2)
                        {
                            if (b == (byte)0xf0)

                                state = State.FOUR_90bf;
                            else

                                state = State.FOUR_80bf_3;
                            break;
                        }
                        else
                            return false;
                        break;
                    case TWO:

                        if ((b & 0xc0) != 0x80)
                            return false;
                        state = State.START;
                        break;
                    case TWO_80:
                        if (b != (byte)0x80)
                            return false;
                        state = State.START;
                        break;
                    case THREE_a0bf:
                        if ((b & 0xe0) == 0x80)
                            return false;
                        state = State.THREE_80bf_1;
                        break;
                    case THREE_80bf_1:

                        if ((b & 0xc0) != 0x80)
                            return false;
                        state = State.START;
                        break;
                    case THREE_80bf_2:

                        if ((b & 0xc0) != 0x80)
                            return false;
                        state = State.THREE_80bf_1;
                        break;
                    case FOUR_90bf:

                        if ((b & 0x30) == 0)
                            return false;
                        state = State.THREE_80bf_2;
                        break;
                    case FOUR_80bf_3:

                        if ((b & 0xc0) != 0x80)
                            return false;
                        state = State.THREE_80bf_2;
                        break;
                    default:
                        return false;
                }
            }

            return state == State.START;
        }
    }
}
