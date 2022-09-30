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
package com.alibaba.csp.sentinel.annotation.aspectj;

import com.alibaba.csp.sentinel.*;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.DefaultProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthoritySlot;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeSlot;
import com.alibaba.csp.sentinel.slots.block.flow.FlowSlot;
import com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot;
import com.alibaba.csp.sentinel.slots.logger.LogSlot;
import com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot;
import com.alibaba.csp.sentinel.slots.statistic.StatisticSlot;
import com.alibaba.csp.sentinel.slots.system.SystemSlot;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import java.lang.reflect.Method;

/**
 * Aspect for methods with {@link SentinelResource} annotation.
 *
 * @author Eric Zhao
 */
@Aspect
public class SentinelResourceAspect extends AbstractSentinelAspectSupport {

    /**
     * 切点: 所有带有SentinelResource注解的方法
     */
    @Pointcut("@annotation(com.alibaba.csp.sentinel.annotation.SentinelResource)")
    public void sentinelResourceAnnotationPointcut() {
    }

    @Around("sentinelResourceAnnotationPointcut()")
    public Object invokeResourceWithSentinel(ProceedingJoinPoint pjp) throws Throwable {
        Method originMethod = resolveMethod(pjp);
        // 获取注解
        SentinelResource annotation = originMethod.getAnnotation(SentinelResource.class);
        if (annotation == null) {
            // Should not go through here.
            throw new IllegalStateException("Wrong state for SentinelResource annotation");
        }
        // 根据注解获取资源名
        String resourceName = getResourceName(annotation.value(), originMethod);
        EntryType entryType = annotation.entryType();
        int resourceType = annotation.resourceType();
        Entry entry = null;
        try {
            /**
             * 进入资源保护逻辑
             *
             * 底层实现
             * @see CtSph#entryWithPriority(ResourceWrapper, int, boolean, Object...)
             */
            entry = SphU.entry(resourceName, resourceType, entryType, pjp.getArgs());
            // 回调业务逻辑方法
            return pjp.proceed();
        } catch (BlockException ex) {
            // 处理阻塞异常: 执行注解上blockHandler指定的方法
            return handleBlockException(pjp, annotation, ex);
        } catch (Throwable ex) {
            Class<? extends Throwable>[] exceptionsToIgnore = annotation.exceptionsToIgnore();
            // The ignore list will be checked first.
            if (exceptionsToIgnore.length > 0 && exceptionBelongsTo(ex, exceptionsToIgnore)) {
                throw ex;
            }
            if (exceptionBelongsTo(ex, annotation.exceptionsToTrace())) {
                traceException(ex);
                // 处理异常降级回调: 执行注解上fallback指定的方法
                return handleFallback(pjp, annotation, ex);
            }

            // No fallback function can handle the exception, so throw it out.
            throw ex;
        } finally {
            if (entry != null) {
                /**
                 * 执行exit
                 * @see CtEntry#exit(int, Object...)
                 *
                 * @see DefaultProcessorSlotChain#exit(Context, ResourceWrapper, int, Object...)
                 * @see NodeSelectorSlot#exit(Context, ResourceWrapper, int, Object...)
                 * @see ClusterBuilderSlot#exit(Context, ResourceWrapper, int, Object...)
                 * @see LogSlot#exit(Context, ResourceWrapper, int, Object...)
                 * @see StatisticSlot#exit(Context, ResourceWrapper, int, Object...)
                 * @see AuthoritySlot#exit(Context, ResourceWrapper, int, Object...)
                 * @see SystemSlot#exit(Context, ResourceWrapper, int, Object...)
                 * @see FlowSlot#exit(Context, ResourceWrapper, int, Object...)
                 * @see DegradeSlot#exit(Context, ResourceWrapper, int, Object...)
                 */
                entry.exit(1, pjp.getArgs());
            }
        }
    }
    }
