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
package com.alibaba.csp.sentinel.dashboard.rule.nacos.degrade;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.DegradeRuleEntity;
import com.alibaba.csp.sentinel.dashboard.discovery.AppManagement;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineInfo;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.nacos.NacosConfigUtil;
import com.alibaba.csp.sentinel.datasource.Converter;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 熔断降级规则 Nacos 配置中心提供者
 *
 * @author zhaojinliang
 * @version 1.0.0
 * @since 2022/10/31
 */
@Component("degradeRuleNacosProvider")
public class DegradeRuleNacosProvider implements DynamicRuleProvider<List<DegradeRuleEntity>> {
    @Autowired
    private ConfigService configService;
//    @Autowired
//    private Converter<String, List<DegradeRuleEntity>> converter;
    @Autowired
    private AppManagement appManagement;

    
    @Override
    public List<DegradeRuleEntity> getRules(String appName) throws Exception {
        String rules = configService.getConfig(appName + NacosConfigUtil.DEGRADE_DATA_ID_POSTFIX,
                NacosConfigUtil.GROUP_ID, NacosConfigUtil.READ_TIMEOUT);
        if (StringUtil.isEmpty(rules)) {
            return new ArrayList<>();
        }
        // 转化成FlowRuleEntity集合
//        return converter.convert(rules);

//        List<DegradeRule> list = JSON.parseArray(rules, DegradeRule.class);
//        return list.stream().map(rule ->
//                DegradeRuleEntity.fromDegradeRule(appName, ip, port, rule))
//                .collect(Collectors.toList());

        List<MachineInfo> list = appManagement.getDetailApp(appName).getMachines()
                .stream()
                .filter(MachineInfo::isHealthy)
                .sorted((e1, e2) -> Long.compare(e2.getLastHeartbeat(), e1.getLastHeartbeat())).collect(Collectors.toList());
        if (list.isEmpty()) {
            return new ArrayList<>();
        } else {
            MachineInfo machine = list.get(0);

            // 解析json获取到 List<DegradeRule>
            List<DegradeRule> ruleList = JSON.parseArray(rules, DegradeRule.class);
            // DegradeRule------->DegradeRuleEntity
            return ruleList.stream().map(rule ->
                            DegradeRuleEntity.fromDegradeRule(machine.getApp(), machine.getIp(), machine.getPort(),rule))
                    .collect(Collectors.toList());
        }
    }
}