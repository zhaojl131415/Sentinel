/*
 * Copyright 1999-2019 Alibaba Group Holding Ltd.
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
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * 响应时间断路器
 * @author Eric Zhao
 * @since 1.8.0
 */
public class ResponseTimeCircuitBreaker extends AbstractCircuitBreaker {

    private static final double SLOW_REQUEST_RATIO_MAX_VALUE = 1.0d;

    /**
     * Sentinel 控制台, 熔断配置 最大 RT
     */
    private final long maxAllowedRt;
    /**
     * Sentinel 控制台, 熔断配置 比例阈值
     */
    private final double maxSlowRequestRatio;
    /**
     * Sentinel 控制台, 熔断配置 最小请求数
     */
    private final int minRequestAmount;

    private final LeapArray<SlowRequestCounter> slidingCounter;

    public ResponseTimeCircuitBreaker(DegradeRule rule) {
        this(rule, new SlowRequestLeapArray(1, rule.getStatIntervalMs()));
    }

    ResponseTimeCircuitBreaker(DegradeRule rule, LeapArray<SlowRequestCounter> stat) {
        super(rule);
        AssertUtil.isTrue(rule.getGrade() == RuleConstant.DEGRADE_GRADE_RT, "rule metric type should be RT");
        AssertUtil.notNull(stat, "stat cannot be null");
        this.maxAllowedRt = Math.round(rule.getCount());
        this.maxSlowRequestRatio = rule.getSlowRatioThreshold();
        this.minRequestAmount = rule.getMinRequestAmount();
        this.slidingCounter = stat;
    }

    @Override
    public void resetStat() {
        // Reset current bucket (bucket count = 1).
        slidingCounter.currentWindow().value().reset();
    }

    @Override
    public void onRequestComplete(Context context) {
        // 获取当前滑动时间窗口的计数器
        SlowRequestCounter counter = slidingCounter.currentWindow().value();
        Entry entry = context.getCurEntry();
        if (entry == null) {
            return;
        }
        // 当前业务请求的完成时间
        long completeTime = entry.getCompleteTimestamp();
        if (completeTime <= 0) {
            completeTime = TimeUtil.currentTimeMillis();
        }
        // 计算业务请求的响应时长: 业务请求的完成时间 - 业务请求发起的时间
        long rt = completeTime - entry.getCreateTimestamp();
        // 如果业务请求的响应时长 > 慢调用比例中配置的最大RT
        if (rt > maxAllowedRt) {
            // 表示为慢调用, 滑动时间窗口计数器 慢调用数量累加1
            counter.slowCount.add(1);
        }
        // 滑动时间窗口计数器 总调用数累加1
        counter.totalCount.add(1);
        // 根据 业务请求响应时长 处理 断路器状态变更
        handleStateChangeWhenThresholdExceeded(rt);
    }

    /**
     * 根据 业务请求响应时长 处理 断路器状态变更
     * @param rt
     */
    private void handleStateChangeWhenThresholdExceeded(long rt) {
        // 开启
        if (currentState.get() == State.OPEN) {
            return;
        }
        // 半开
        if (currentState.get() == State.HALF_OPEN) {
            // In detecting request
            // TODO: improve logic for half-open recovery
            // 如果业务请求的响应时长 > 慢调用比例中配置的最大RT, 则将断路器状态变更为全开, 否则关闭断路器, 请求正常访问.
            if (rt > maxAllowedRt) {
                // 半开 -> 全开
                fromHalfOpenToOpen(1.0d);
            } else {
                // 半开 -> 关闭
                fromHalfOpenToClose();
            }
            return;
        }
        // 断路器关闭状态

        // 滑动时间窗口
        List<SlowRequestCounter> counters = slidingCounter.values();
        long slowCount = 0;
        // 用于存储时间窗口内的请求数量
        long totalCount = 0;
        // 遍历计算慢调用数量和总调用数量
        for (SlowRequestCounter counter : counters) {
            slowCount += counter.slowCount.sum();
            totalCount += counter.totalCount.sum();
        }
        // 如果请求数量 小于 最小请求数量
        if (totalCount < minRequestAmount) {
            return;
        }
        // 计算慢调用比例
        double currentRatio = slowCount * 1.0d / totalCount;
        // 如果慢调用的比例高于设置的最大慢调用比例, 则将断路器的状态改为 全开状态
        if (currentRatio > maxSlowRequestRatio) {
            // 将断路器状态修改为开启状态
            transformToOpen(currentRatio);
        }
        //
        if (Double.compare(currentRatio, maxSlowRequestRatio) == 0 &&
                Double.compare(maxSlowRequestRatio, SLOW_REQUEST_RATIO_MAX_VALUE) == 0) {
            // 将断路器状态修改为开启状态
            transformToOpen(currentRatio);
        }
    }

    static class SlowRequestCounter {
        private LongAdder slowCount;
        private LongAdder totalCount;

        public SlowRequestCounter() {
            this.slowCount = new LongAdder();
            this.totalCount = new LongAdder();
        }

        public LongAdder getSlowCount() {
            return slowCount;
        }

        public LongAdder getTotalCount() {
            return totalCount;
        }

        /**
         * 重置数据: 慢调用数量/总调用数量
         * @return
         */
        public SlowRequestCounter reset() {
            slowCount.reset();
            totalCount.reset();
            return this;
        }

        @Override
        public String toString() {
            return "SlowRequestCounter{" +
                "slowCount=" + slowCount +
                ", totalCount=" + totalCount +
                '}';
        }
    }

    static class SlowRequestLeapArray extends LeapArray<SlowRequestCounter> {

        public SlowRequestLeapArray(int sampleCount, int intervalInMs) {
            super(sampleCount, intervalInMs);
        }

        @Override
        public SlowRequestCounter newEmptyBucket(long timeMillis) {
            return new SlowRequestCounter();
        }

        @Override
        protected WindowWrap<SlowRequestCounter> resetWindowTo(WindowWrap<SlowRequestCounter> w, long startTime) {
            w.resetTo(startTime);
            w.value().reset();
            return w;
        }
    }
}
