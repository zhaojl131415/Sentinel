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

import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.util.function.BiConsumer;

/**
 * @author Eric Zhao
 * @since 1.8.0
 */
public abstract class AbstractCircuitBreaker implements CircuitBreaker {

    protected final DegradeRule rule;
    protected final int recoveryTimeoutMs;

    private final EventObserverRegistry observerRegistry;

    protected final AtomicReference<State> currentState = new AtomicReference<>(State.CLOSED);
    protected volatile long nextRetryTimestamp;

    public AbstractCircuitBreaker(DegradeRule rule) {
        this(rule, EventObserverRegistry.getInstance());
    }

    AbstractCircuitBreaker(DegradeRule rule, EventObserverRegistry observerRegistry) {
        AssertUtil.notNull(observerRegistry, "observerRegistry cannot be null");
        if (!DegradeRuleManager.isValidRule(rule)) {
            throw new IllegalArgumentException("Invalid DegradeRule: " + rule);
        }
        this.observerRegistry = observerRegistry;
        this.rule = rule;
        this.recoveryTimeoutMs = rule.getTimeWindow() * 1000;
    }

    @Override
    public DegradeRule getRule() {
        return rule;
    }

    @Override
    public State currentState() {
        return currentState.get();
    }

    @Override
    public boolean tryPass(Context context) {
        // Template implementation.
        // 如果当前熔断器的状态为关闭状态, 表示不需要熔断, 请求可以通过
        if (currentState.get() == State.CLOSED) {
            return true;
        }
        // 如果当前熔断器的状态为开启状态, 表示熔断开启, 请求不可以通过, 尝试半开状态访问
        if (currentState.get() == State.OPEN) {
            // For half-open state we allow a request for probing. 对于半开状态，我们允许探测请求。
            /**
             * 重试超时到达: 表示当前时间 大于 上次熔断时间 + 配置的熔断时长,
             * 如果超过了熔断时长, 则断路器从打开状态切换至半开状态, 尝试放行一次请求.
             */
            return retryTimeoutArrived() && fromOpenToHalfOpen(context);
        }
        return false;
    }

    /**
     * Reset the statistic data.
     */
    abstract void resetStat();

    /**
     * 重试超时到达
     * @return
     */
    protected boolean retryTimeoutArrived() {
        // 当前时间戳大于等于下次重试时间戳, 表示请求已经超过熔断时长
        return TimeUtil.currentTimeMillis() >= nextRetryTimestamp;
    }

    /**
     * 当断路器切换至全开状态时, 会调用此方法, 更新下次请求重试的时间戳
     */
    protected void updateNextRetryTimestamp() {
        // 下次重试时间戳 = 请求失败断路器切换至全开状态时的时间 + 配置的熔断时长
        this.nextRetryTimestamp = TimeUtil.currentTimeMillis() + recoveryTimeoutMs;
    }

    /**
     * 断路器从关闭状态到开启状态
     * @param snapshotValue
     * @return
     */
    protected boolean fromCloseToOpen(double snapshotValue) {
        State prev = State.CLOSED;
        // CAS修改当前断路器状态: 从关闭状态到开启状态
        if (currentState.compareAndSet(prev, State.OPEN)) {
            // 更新下次请求重试的时间戳
            updateNextRetryTimestamp();

            notifyObservers(prev, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    /**
     * 断路器从开启状态到半开状态
     * @param context
     * @return
     */
    protected boolean fromOpenToHalfOpen(Context context) {
        // CAS修改当前断路器状态: 从开启状态到半开状态
        if (currentState.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            // 观察者: 空方法
            notifyObservers(State.OPEN, State.HALF_OPEN, null);
            Entry entry = context.getCurEntry();
            entry.whenTerminate(new BiConsumer<Context, Entry>() {
                @Override
                public void accept(Context context, Entry entry) {
                    // Note: This works as a temporary workaround for https://github.com/alibaba/Sentinel/issues/1638
                    // Without the hook, the circuit breaker won't recover from half-open state in some circumstances
                    // when the request is actually blocked by upcoming rules (not only degrade rules).
                    if (entry.getBlockError() != null) {
                        // Fallback to OPEN due to detecting request is blocked
                        currentState.compareAndSet(State.HALF_OPEN, State.OPEN);
                        notifyObservers(State.HALF_OPEN, State.OPEN, 1.0d);
                    }
                }
            });
            return true;
        }
        return false;
    }
    
    private void notifyObservers(CircuitBreaker.State prevState, CircuitBreaker.State newState, Double snapshotValue) {
        for (CircuitBreakerStateChangeObserver observer : observerRegistry.getStateChangeObservers()) {
            observer.onStateChange(prevState, newState, rule, snapshotValue);
        }
    }

    protected boolean fromHalfOpenToOpen(double snapshotValue) {
        // CAS变更断路器状态: 半开 -> 全开
        if (currentState.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            // 更新下次请求重试的时间戳
            updateNextRetryTimestamp();
            notifyObservers(State.HALF_OPEN, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }

    protected boolean fromHalfOpenToClose() {
        // CAS变更断路器状态: 半开 -> 关闭
        if (currentState.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            /**
             * 重置统计数据
             *
             * 响应时间断路器重置数据: 慢调用数量/总调用数量
             * @see ResponseTimeCircuitBreaker#resetStat()
             *
             * 异常断路器重置数据: 错误调用数量/总调用数量
             * @see ExceptionCircuitBreaker#resetStat()
             */
            resetStat();
            notifyObservers(State.HALF_OPEN, State.CLOSED, null);
            return true;
        }
        return false;
    }

    /**
     * 将断路器状态修改为开启状态
     * @param triggerValue
     */
    protected void transformToOpen(double triggerValue) {
        State cs = currentState.get();
        switch (cs) {
            case CLOSED:
                fromCloseToOpen(triggerValue);
                break;
            case HALF_OPEN:
                fromHalfOpenToOpen(triggerValue);
                break;
            default:
                break;
        }
    }
}
