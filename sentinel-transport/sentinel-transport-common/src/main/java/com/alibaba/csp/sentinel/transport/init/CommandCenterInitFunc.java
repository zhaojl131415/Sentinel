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
package com.alibaba.csp.sentinel.transport.init;

import com.alibaba.csp.sentinel.command.CommandCenterProvider;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.init.InitOrder;
import com.alibaba.csp.sentinel.log.RecordLog;
import com.alibaba.csp.sentinel.transport.CommandCenter;

/**
 * @author Eric Zhao
 */
@InitOrder(-1)
public class CommandCenterInitFunc implements InitFunc {

    @Override
    public void init() throws Exception {
        CommandCenter commandCenter = CommandCenterProvider.getCommandCenter();

        if (commandCenter == null) {
            RecordLog.warn("[CommandCenterInitFunc] Cannot resolve CommandCenter");
            return;
        }

        /**
         * 服务端: sentinel服务端/控制台
         * @see com.alibaba.csp.sentinel.transport.command.NettyHttpCommandCenter#beforeStart()
         * 客户端: 业务微服务
         * @see com.alibaba.csp.sentinel.transport.command.SimpleHttpCommandCenter#beforeStart()
         */
        commandCenter.beforeStart();

        /**
         * 服务端: sentinel服务端/控制台
         * @see com.alibaba.csp.sentinel.transport.command.NettyHttpCommandCenter#start()
         * 客户端: 业务微服务
         * @see com.alibaba.csp.sentinel.transport.command.SimpleHttpCommandCenter#start()
         */
        commandCenter.start();
        RecordLog.info("[CommandCenterInit] Starting command center: "
                + commandCenter.getClass().getCanonicalName());
    }
}
