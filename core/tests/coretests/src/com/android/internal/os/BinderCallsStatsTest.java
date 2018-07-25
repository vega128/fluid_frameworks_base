/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.internal.os;

import android.os.Binder;
import android.platform.test.annotations.Presubmit;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.ArrayMap;
import android.util.SparseArray;

import com.android.internal.os.BinderInternal.CallSession;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class BinderCallsStatsTest {
    private static final int TEST_UID = 1;
    private static final int REQUEST_SIZE = 2;
    private static final int REPLY_SIZE = 3;

    @Test
    public void testDetailedOff() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(false);
        bcs.setSamplingInterval(5);

        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        SparseArray<BinderCallsStats.UidEntry> uidEntries = bcs.getUidEntries();
        assertEquals(1, uidEntries.size());
        BinderCallsStats.UidEntry uidEntry = uidEntries.get(TEST_UID);
        Assert.assertNotNull(uidEntry);
        List<BinderCallsStats.CallStat> callStatsList = uidEntry.getCallStatsList();
        assertEquals(1, uidEntry.callCount);
        assertEquals(1, uidEntry.recordedCallCount);
        assertEquals(10, uidEntry.cpuTimeMicros);
        assertEquals(binder.getClass().getName(), callStatsList.get(0).className);
        assertEquals(1, callStatsList.get(0).transactionCode);

        // CPU usage is sampled, should not be tracked here.
        callSession = bcs.callStarted(binder, 1);
        bcs.time += 20;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);
        assertEquals(2, uidEntry.callCount);
        assertEquals(1, uidEntry.recordedCallCount);
        assertEquals(10, uidEntry.cpuTimeMicros);
        assertEquals(1, callStatsList.size());

        callSession = bcs.callStarted(binder, 2);
        bcs.time += 50;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);
        uidEntry = bcs.getUidEntries().get(TEST_UID);
        assertEquals(3, uidEntry.callCount);
        assertEquals(1, uidEntry.recordedCallCount);
        // Still sampled even for another API.
        callStatsList = uidEntry.getCallStatsList();
        assertEquals(2, callStatsList.size());
    }

    @Test
    public void testDetailedOn() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(true);

        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        SparseArray<BinderCallsStats.UidEntry> uidEntries = bcs.getUidEntries();
        assertEquals(1, uidEntries.size());
        BinderCallsStats.UidEntry uidEntry = uidEntries.get(TEST_UID);
        Assert.assertNotNull(uidEntry);
        assertEquals(1, uidEntry.callCount);
        assertEquals(10, uidEntry.cpuTimeMicros);
        assertEquals(1, uidEntry.getCallStatsList().size());

        List<BinderCallsStats.CallStat> callStatsList = uidEntry.getCallStatsList();
        assertEquals(1, callStatsList.get(0).callCount);
        assertEquals(10, callStatsList.get(0).cpuTimeMicros);
        assertEquals(binder.getClass().getName(), callStatsList.get(0).className);
        assertEquals(1, callStatsList.get(0).transactionCode);

        callSession = bcs.callStarted(binder, 1);
        bcs.time += 20;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        uidEntry = bcs.getUidEntries().get(TEST_UID);
        assertEquals(2, uidEntry.callCount);
        assertEquals(30, uidEntry.cpuTimeMicros);
        callStatsList = uidEntry.getCallStatsList();
        assertEquals(1, callStatsList.size());

        callSession = bcs.callStarted(binder, 2);
        bcs.time += 50;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);
        uidEntry = bcs.getUidEntries().get(TEST_UID);
        assertEquals(3, uidEntry.callCount);

        // This is the first transaction of a new type, so the real CPU time will be measured
        assertEquals(80, uidEntry.cpuTimeMicros);
        callStatsList = uidEntry.getCallStatsList();
        assertEquals(2, callStatsList.size());
    }

    @Test
    public void testEnableInBetweenCall() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        Binder binder = new Binder();
        bcs.callEnded(null, REQUEST_SIZE, REPLY_SIZE);

        SparseArray<BinderCallsStats.UidEntry> uidEntries = bcs.getUidEntries();
        assertEquals(0, uidEntries.size());
    }

    @Test
    public void testInBetweenCallWhenExceptionThrown() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        Binder binder = new Binder();
        bcs.callThrewException(null, new IllegalStateException());
        bcs.callEnded(null, REQUEST_SIZE, REPLY_SIZE);

        SparseArray<BinderCallsStats.UidEntry> uidEntries = bcs.getUidEntries();
        assertEquals(0, uidEntries.size());
    }

    @Test
    public void testSampling() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(false);
        bcs.setSamplingInterval(2);

        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        callSession = bcs.callStarted(binder, 1);
        bcs.time += 1000;  // shoud be ignored.
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        callSession = bcs.callStarted(binder, 1);
        bcs.time += 50;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        SparseArray<BinderCallsStats.UidEntry> uidEntries = bcs.getUidEntries();
        assertEquals(1, uidEntries.size());
        BinderCallsStats.UidEntry uidEntry = uidEntries.get(TEST_UID);
        Assert.assertNotNull(uidEntry);
        assertEquals(3, uidEntry.callCount);
        assertEquals(60 /* 10 + 50 */, uidEntry.cpuTimeMicros);

        List<BinderCallsStats.CallStat> callStatsList = uidEntry.getCallStatsList();
        assertEquals(1, callStatsList.size());
        BinderCallsStats.CallStat callStats = callStatsList.get(0);
        assertEquals(3, callStats.callCount);
        assertEquals(2, callStats.recordedCallCount);
        assertEquals(60, callStats.cpuTimeMicros);
        assertEquals(50, callStats.maxCpuTimeMicros);
        assertEquals(0, callStats.maxRequestSizeBytes);
        assertEquals(0, callStats.maxReplySizeBytes);
    }

    @Test
    public void testSamplingWithDifferentApis() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(false);
        bcs.setSamplingInterval(2);

        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        callSession = bcs.callStarted(binder, 2 /* another method */);
        bcs.time += 1000;  // shoud be ignored.
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);


        SparseArray<BinderCallsStats.UidEntry> uidEntries = bcs.getUidEntries();
        assertEquals(1, uidEntries.size());
        BinderCallsStats.UidEntry uidEntry = uidEntries.get(TEST_UID);
        assertEquals(2, uidEntry.callCount);
        assertEquals(1, uidEntry.recordedCallCount);
        assertEquals(10, uidEntry.cpuTimeMicros);

        List<BinderCallsStats.CallStat> callStatsList = uidEntry.getCallStatsList();
        assertEquals(2, callStatsList.size());

        BinderCallsStats.CallStat callStats = callStatsList.get(0);
        assertEquals(1, callStats.callCount);
        assertEquals(1, callStats.recordedCallCount);
        assertEquals(10, callStats.cpuTimeMicros);
        assertEquals(10, callStats.maxCpuTimeMicros);

        // Only call count should is tracked, rest is sampled.
        callStats = callStatsList.get(1);
        assertEquals(1, callStats.callCount);
        assertEquals(0, callStats.recordedCallCount);
        assertEquals(0, callStats.cpuTimeMicros);
        assertEquals(0, callStats.maxCpuTimeMicros);
    }

    @Test
    public void testTransactionCodeResolved() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(true);
        Binder binder = new Binder() {
            @Override
            public String getTransactionName(int code) {
                return "resolved";
            }
        };
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        List<BinderCallsStats.CallStat> callStatsList =
                bcs.getUidEntries().get(TEST_UID).getCallStatsList();
        assertEquals(1, callStatsList.get(0).transactionCode);
        assertEquals("resolved", callStatsList.get(0).methodName);
    }

    @Test
    public void testParcelSize() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(true);
        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        List<BinderCallsStats.CallStat> callStatsList =
                bcs.getUidEntries().get(TEST_UID).getCallStatsList();

        assertEquals(REQUEST_SIZE, callStatsList.get(0).maxRequestSizeBytes);
        assertEquals(REPLY_SIZE, callStatsList.get(0).maxReplySizeBytes);
    }

    @Test
    public void testMaxCpu() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(true);
        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 50;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        List<BinderCallsStats.CallStat> callStatsList =
                bcs.getUidEntries().get(TEST_UID).getCallStatsList();

        assertEquals(50, callStatsList.get(0).maxCpuTimeMicros);
    }

    @Test
    public void testMaxLatency() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(true);
        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.elapsedTime += 5;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        callSession = bcs.callStarted(binder, 1);
        bcs.elapsedTime += 1;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        List<BinderCallsStats.CallStat> callStatsList =
                bcs.getUidEntries().get(TEST_UID).getCallStatsList();

        assertEquals(5, callStatsList.get(0).maxLatencyMicros);
    }

    @Test
    public void testGetHighestValues() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4);
        List<Integer> highestValues = BinderCallsStats
                .getHighestValues(list, value -> value, 0.8);
        assertEquals(Arrays.asList(4, 3, 2), highestValues);
    }

    @Test
    public void testExceptionCount() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(true);
        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.callThrewException(callSession, new IllegalStateException());
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        callSession = bcs.callStarted(binder, 1);
        bcs.callThrewException(callSession, new IllegalStateException());
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        callSession = bcs.callStarted(binder, 1);
        bcs.callThrewException(callSession, new RuntimeException());
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        ArrayMap<String, Integer> expected = new ArrayMap<>();
        expected.put("java.lang.IllegalStateException", 2);
        expected.put("java.lang.RuntimeException", 1);
        assertEquals(expected, bcs.getExceptionCounts());
    }

    @Test
    public void testDumpDoesNotThrowException() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(true);
        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.callThrewException(callSession, new IllegalStateException());
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        PrintWriter pw = new PrintWriter(new StringWriter());
        bcs.dump(pw, new HashMap<>(), true);
    }

    @Test
    public void testGetExportedStatsWhenDetailedTrackingDisabled() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(false);
        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        assertEquals(0, bcs.getExportedCallStats().size());
    }

    @Test
    public void testGetExportedStatsWhenDetailedTrackingEnabled() {
        TestBinderCallsStats bcs = new TestBinderCallsStats();
        bcs.setDetailedTracking(true);
        Binder binder = new Binder();
        CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.elapsedTime += 20;
        bcs.callEnded(callSession, REQUEST_SIZE, REPLY_SIZE);

        assertEquals(1, bcs.getExportedCallStats().size());
        BinderCallsStats.ExportedCallStat stat = bcs.getExportedCallStats().get(0);
        assertEquals(TEST_UID, stat.uid);
        assertEquals("android.os.Binder", stat.className);
        assertEquals("1", stat.methodName);
        assertEquals(10, stat.cpuTimeMicros);
        assertEquals(10, stat.maxCpuTimeMicros);
        assertEquals(20, stat.latencyMicros);
        assertEquals(20, stat.maxLatencyMicros);
        assertEquals(1, stat.callCount);
        assertEquals(1, stat.recordedCallCount);
        assertEquals(REQUEST_SIZE, stat.maxRequestSizeBytes);
        assertEquals(REPLY_SIZE, stat.maxReplySizeBytes);
        assertEquals(0, stat.exceptionCount);
    }

    static class TestBinderCallsStats extends BinderCallsStats {
        int callingUid = TEST_UID;
        long time = 1234;
        long elapsedTime = 0;

        TestBinderCallsStats() {
            // Make random generator not random.
            super(new Random() {
                int mCallCount = 0;

                public int nextInt() {
                    return mCallCount++;
                }
            });
        }

        @Override
        protected long getThreadTimeMicro() {
            return time;
        }

        @Override
        protected long getElapsedRealtimeMicro() {
            return elapsedTime;
        }

        @Override
        protected int getCallingUid() {
            return callingUid;
        }
    }

}
