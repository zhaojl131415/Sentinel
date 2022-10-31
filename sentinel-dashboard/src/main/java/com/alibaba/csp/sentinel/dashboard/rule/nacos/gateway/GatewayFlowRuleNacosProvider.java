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
package com.alibaba.csp.sentinel.dashboard.rule.nacos.gateway;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.gateway.GatewayFlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.discovery.AppManagement;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineInfo;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.nacos.NacosConfigUtil;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 授权规则 Nacos 配置中心提供者
 *
 * @author zhaojinliang
 * @version 1.0.0
 * @since 2022/10/31
 */
@Component("gatewayFlowRuleNacosProvider")
public class GatewayFlowRuleNacosProvider implements DynamicRuleProvider<List<GatewayFlowRuleEntity>> {

    @Autowired
    private ConfigService configService;
    //    @Autowired
//    private Converter<String, List<GatewayFlowRuleEntity>> converter;
    @Autowired
    private AppManagement appManagement;

    @Override
    public List<GatewayFlowRuleEntity> getRules(String appName) throws Exception {
        String rules = configService.getConfig(appName + NacosConfigUtil.GATEWAY_FLOW_DATA_ID_POSTFIX,
                NacosConfigUtil.GROUP_ID, NacosConfigUtil.READ_TIMEOUT);
        if (StringUtil.isEmpty(rules)) {
            return new ArrayList<>();
        }
//        // 转化成FlowRuleEntity集合
//        return converter.convert(rules);

//        List<GatewayFlowRule> list = JSON.parseArray(rules, GatewayFlowRule.class);
//        return list.stream().map(rule ->
//                GatewayFlowRuleEntity.fromGatewayFlowRule(appName, ip, port, rule))
//                .collect(Collectors.toList());

        List<MachineInfo> list = appManagement.getDetailApp(appName).getMachines()
                .stream()
                .filter(MachineInfo::isHealthy)
                .sorted((e1, e2) -> Long.compare(e2.getLastHeartbeat(), e1.getLastHeartbeat())).collect(Collectors.toList());
        if (list.isEmpty()) {
            return new ArrayList<>();
        } else {
            MachineInfo machine = list.get(0);

            // 解析json获取到 List<GatewayFlowRule>
            List<GatewayFlowRule> ruleList = JSON.parseArray(rules, GatewayFlowRule.class);
            // GatewayFlowRule------->GatewayFlowRuleEntity
            return ruleList.stream().map(rule ->
                            GatewayFlowRuleEntity.fromGatewayFlowRule(machine.getApp(), machine.getIp(), machine.getPort(), rule))
                    .collect(Collectors.toList());
        }
    }
}