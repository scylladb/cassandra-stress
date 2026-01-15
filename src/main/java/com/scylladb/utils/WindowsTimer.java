/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.scylladb.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public final class WindowsTimer {
    private static final Logger logger = LoggerFactory.getLogger(WindowsTimer.class);

    private static boolean available;

    private static final Arena ARENA = Arena.ofShared();

    private static MethodHandle TIME_BEGIN_PERIOD;
    private static MethodHandle TIME_END_PERIOD;

    static {
        try {
            final Linker linker = Linker.nativeLinker();
            final SymbolLookup winmm = SymbolLookup.libraryLookup("winmm", ARENA);

            // MMRESULT timeBeginPeriod(UINT uPeriod);
            final FunctionDescriptor fd = FunctionDescriptor.of(JAVA_INT, JAVA_INT);

            TIME_BEGIN_PERIOD = linker.downcallHandle(
                    winmm.find("timeBeginPeriod").orElseThrow(),
                    fd
            );

            // MMRESULT timeEndPeriod(UINT uPeriod);
            TIME_END_PERIOD = linker.downcallHandle(
                    winmm.find("timeEndPeriod").orElseThrow(),
                    fd
            );

            available = true;
        } catch (NoClassDefFoundError e) {
            logger.warn("FFM API (Project Panama) not available. winmm.dll cannot be linked. Performance will be negatively impacted on this node.");
            available = false;
        } catch (UnsatisfiedLinkError e) {
            logger.error("Failed to link the winmm.dll library via FFM. Performance will be negatively impacted on this node.", e);
            available = false;
        } catch (Throwable e) {
            logger.error("Failed to resolve winmm.dll symbols via FFM. Performance will be negatively impacted on this node.", e);
            available = false;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static int timeBeginPeriod(int period) {
        if (!available) return -1;
        try {
            // 0 == TIMERR_NOERROR
            return (int) TIME_BEGIN_PERIOD.invokeExact(period);
        } catch (Throwable t) {
            logger.error("timeBeginPeriod({}) failed via FFM.", period, t);
            return -1;
        }
    }

    public static int timeEndPeriod(int period) {
        if (!available) return -1;
        try {
            return (int) TIME_END_PERIOD.invokeExact(period);
        } catch (Throwable t) {
            logger.error("timeEndPeriod({}) failed via FFM.", period, t);
            return -1;
        }
    }

    public static void startTimerPeriod(int period)
    {
        if (period == 0)
            return;
        assert(period > 0);
        if (!available)
            return;
        if (timeBeginPeriod(period) != 0)
            logger.warn("Failed to set timer to : {}. Performance will be degraded.", period);
    }

    public static void endTimerPeriod(int period)
    {
        if (period == 0)
            return;
        assert(period > 0);
        if (!available)
            return;
        if (timeEndPeriod(period) != 0)
            logger.warn("Failed to end accelerated timer period. System timer will remain set to: {} ms.", period);
    }
}
