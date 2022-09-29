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
package com.alibaba.csp.sentinel.slots;

import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.slotchain.AbstractLinkedProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.DefaultProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlot;
import com.alibaba.csp.sentinel.slotchain.ProcessorSlotChain;
import com.alibaba.csp.sentinel.slotchain.SlotChainBuilder;
import com.alibaba.csp.sentinel.spi.Spi;
import com.alibaba.csp.sentinel.spi.SpiLoader;

import java.util.List;

/**
 * Builder for a default {@link ProcessorSlotChain}.
 *
 * @author qinan.qn
 * @author leyou
 */
@Spi(isDefault = true)
public class DefaultSlotChainBuilder implements SlotChainBuilder {

    @Override
    public ProcessorSlotChain build() {
        ProcessorSlotChain chain = new DefaultProcessorSlotChain();
        /**
         * SPI加载处理链ProcessorSlot
         * SPI读取文件:{@link META-INF/services/com.alibaba.csp.sentinel.slotchain.ProcessorSlot}
         * 遍历文件中指定的ProcessorSlot并实例化, 加入到链中: 参考责任链
         * 链执行顺序
         *
         * first
         * @see com.alibaba.csp.sentinel.slots.nodeselector.NodeSelectorSlot
         * @see com.alibaba.csp.sentinel.slots.clusterbuilder.ClusterBuilderSlot
         * @see com.alibaba.csp.sentinel.slots.logger.LogSlot
         * @see com.alibaba.csp.sentinel.slots.statistic.StatisticSlot
         * @see com.alibaba.csp.sentinel.slots.block.authority.AuthoritySlot
         * @see com.alibaba.csp.sentinel.slots.system.SystemSlot
         * @see com.alibaba.csp.sentinel.slots.block.flow.FlowSlot
         * @see com.alibaba.csp.sentinel.slots.block.degrade.DegradeSlot
         * end
         */
        List<ProcessorSlot> sortedSlotList = SpiLoader.of(ProcessorSlot.class).loadInstanceListSorted();
        for (ProcessorSlot slot : sortedSlotList) {
            if (!(slot instanceof AbstractLinkedProcessorSlot)) {
                RecordLog.warn("The ProcessorSlot(" + slot.getClass().getCanonicalName() + ") is not an instance of AbstractLinkedProcessorSlot, can't be added into ProcessorSlotChain");
                continue;
            }

            chain.addLast((AbstractLinkedProcessorSlot<?>) slot);
        }
        // 返回链
        return chain;
    }
}
