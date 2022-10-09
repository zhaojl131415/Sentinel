/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
package com.alibaba.csp.sentinel.slots.block.flow.controller;

import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.csp.sentinel.slots.block.flow.TrafficShapingController;

import com.alibaba.csp.sentinel.util.TimeUtil;
import com.alibaba.csp.sentinel.node.Node;

/**
 * @author jialiang.linjl
 */
public class RateLimiterController implements TrafficShapingController {

    private final int maxQueueingTimeMs;
    private final double count;

    private final AtomicLong latestPassedTime = new AtomicLong(-1);

    public RateLimiterController(int timeOut, double count) {
        this.maxQueueingTimeMs = timeOut;
        this.count = count;
    }

    @Override
    public boolean canPass(Node node, int acquireCount) {
        return canPass(node, acquireCount, false);
    }

    @Override
    public boolean canPass(Node node, int acquireCount, boolean prioritized) {
        // Pass when acquire count is less or equal than 0.
        if (acquireCount <= 0) {
            return true;
        }
        // Reject when count is less or equal than 0.
        // Otherwise,the costTime will be max of long and waitTime will overflow in some cases.
        if (count <= 0) {
            return false;
        }

        long currentTime = TimeUtil.currentTimeMillis();
        // Calculate the interval between every two requests. 计算每两个请求之间的间隔时间, 也就是说每个请求的平均花费时长.
        long costTime = Math.round(1.0 * (acquireCount) / count * 1000);

        // Expected pass time of this request. 根据上一个请求的通过时间 + 平均花费时长 计算 请求的预期通过时间
        long expectedTime = costTime + latestPassedTime.get();
        // 此处因为没有加锁, 可能存在线程不安全. 如果说在此时同时并发来了多个请求, 都满足当前时间 > 预期时间, 则可能都通过.
        if (expectedTime <= currentTime) {
            // 如果预期的通过时间 小于等于 当前时间, 表示当前请求可以通过, 将上一个请求的通过时间更新为当前时间后返回true, 放行请求
            // Contention may exist here, but it's okay.
            latestPassedTime.set(currentTime);
            return true;
        } else {
            /**
             * 漏桶算法: 匀速执行.
             * 表示当前请求还没到预期请求的时间, 需要等待执行.
             */
            // Calculate the time to wait.计算等待时间, 当前请求的预期通过时间 - 当前时间戳
            long waitTime = costTime + latestPassedTime.get() - TimeUtil.currentTimeMillis();
            if (waitTime > maxQueueingTimeMs) {
                // 如果等待时长 > 最大的排队等待时长, 等待超时, 则返回fasle
                return false;
            } else {
                // 更新latestPassedTime, 并获取更新后的时间, 实际上就是上面的预期通过时间.
                // 感觉这里的几个时间计算有点冗余, 有点像双重检验的意思, 可能是并发考虑, 不过感觉还是线程不安全的
                long oldTime = latestPassedTime.addAndGet(costTime);
                try {
                    // 计算等待时间
                    waitTime = oldTime - TimeUtil.currentTimeMillis();
                    // 如果等待时长 > 最大的排队等待时长, 等待超时
                    if (waitTime > maxQueueingTimeMs) {
                        // 回滚上一个请求的通过时间, 并返回false
                        latestPassedTime.addAndGet(-costTime);
                        return false;
                    }
                    // in race condition waitTime may <= 0 在竞争条件下 waitTime 可能 <= 0
                    if (waitTime > 0) {
                        // 睡眠等待
                        Thread.sleep(waitTime);
                    }
                    // 等待时间到后, 放行请求
                    return true;
                } catch (InterruptedException e) {
                }
            }
        }
        return false;
    }

}
