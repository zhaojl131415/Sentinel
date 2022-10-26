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
package com.zhao.sentinel.extension.file.pull;

import com.alibaba.csp.sentinel.command.handler.ModifyParamFlowRulesCommandHandler;
import com.alibaba.csp.sentinel.datasource.FileRefreshableDataSource;
import com.alibaba.csp.sentinel.datasource.FileWritableDataSource;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.WritableDataSource;
import com.alibaba.csp.sentinel.init.InitFunc;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRule;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityRuleManager;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRule;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.csp.sentinel.transport.util.WritableDataSourceRegistry;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * InitFunc实现类，处理dataSource初始化逻辑
 *
 * @author zhaojinliang
 */
public class FileDataSourceInit implements InitFunc {

    @Override
    public void init() throws Exception {
        //创建文件存储目录
        RuleFileUtils.mkdirIfNotExits(PersistenceRuleConstant.storePath);

        //创建规则文件
        RuleFileUtils.createFileIfNotExits(PersistenceRuleConstant.rulesMap);

        //处理流控规则逻辑  配置读写数据源
        dealFlowRules();
        // 处理降级规则
        dealDegradeRules();
        // 处理系统规则
        dealSystemRules();
        // 处理热点参数规则
        dealParamFlowRules();
        // 处理授权规则
        dealAuthRules();
    }


    private void dealFlowRules() throws FileNotFoundException {
        String ruleFilePath = PersistenceRuleConstant.rulesMap.get(PersistenceRuleConstant.FLOW_RULE_PATH).toString();

        //创建流控规则的可读数据源
        ReadableDataSource<String, List<FlowRule>> flowRuleRDS = new FileRefreshableDataSource(
                ruleFilePath, RuleListConverterUtils.flowRuleListParser
        );

        // 将可读数据源注册至FlowRuleManager 这样当规则文件发生变化时，就会更新规则到内存
        FlowRuleManager.register2Property(flowRuleRDS.getProperty());


        WritableDataSource<List<FlowRule>> flowRuleWDS = new FileWritableDataSource<List<FlowRule>>(
                ruleFilePath, RuleListConverterUtils.flowFuleEnCoding
        );

        // 将可写数据源注册至 transport 模块的 WritableDataSourceRegistry 中.
        // 这样收到控制台推送的规则时，Sentinel 会先更新到内存，然后将规则写入到文件中.
        WritableDataSourceRegistry.registerFlowDataSource(flowRuleWDS);
    }

    private void dealDegradeRules() throws FileNotFoundException {
        //获取规则文件路径
        String degradeRuleFilePath = PersistenceRuleConstant.rulesMap.get(PersistenceRuleConstant.DEGRAGE_RULE_PATH).toString();

        //创建流控规则的可读数据源
        ReadableDataSource<String, List<DegradeRule>> degradeRuleRDS = new FileRefreshableDataSource(
                degradeRuleFilePath, RuleListConverterUtils.degradeRuleListParse
        );

        // 将可读数据源注册至FlowRuleManager 这样当规则文件发生变化时，就会更新规则到内存
        DegradeRuleManager.register2Property(degradeRuleRDS.getProperty());


        WritableDataSource<List<DegradeRule>> degradeRuleWDS = new FileWritableDataSource<>(
                degradeRuleFilePath, RuleListConverterUtils.degradeRuleEnCoding
        );

        // 将可写数据源注册至 transport 模块的 WritableDataSourceRegistry 中.
        // 这样收到控制台推送的规则时，Sentinel 会先更新到内存，然后将规则写入到文件中.
        WritableDataSourceRegistry.registerDegradeDataSource(degradeRuleWDS);
    }

    private void dealSystemRules() throws FileNotFoundException {
        //获取规则文件路径
        String systemRuleFilePath = PersistenceRuleConstant.rulesMap.get(PersistenceRuleConstant.SYSTEM_RULE_PATH).toString();

        //创建流控规则的可读数据源
        ReadableDataSource<String, List<SystemRule>> systemRuleRDS = new FileRefreshableDataSource(
                systemRuleFilePath, RuleListConverterUtils.sysRuleListParse
        );

        // 将可读数据源注册至FlowRuleManager 这样当规则文件发生变化时，就会更新规则到内存
        SystemRuleManager.register2Property(systemRuleRDS.getProperty());


        WritableDataSource<List<SystemRule>> systemRuleWDS = new FileWritableDataSource<>(
                systemRuleFilePath, RuleListConverterUtils.sysRuleEnCoding
        );

        // 将可写数据源注册至 transport 模块的 WritableDataSourceRegistry 中.
        // 这样收到控制台推送的规则时，Sentinel 会先更新到内存，然后将规则写入到文件中.
        WritableDataSourceRegistry.registerSystemDataSource(systemRuleWDS);
    }


    private void dealParamFlowRules() throws FileNotFoundException {
        //获取规则文件路径
        String paramFlowRuleFilePath = PersistenceRuleConstant.rulesMap.get(PersistenceRuleConstant.HOT_PARAM_RULE).toString();

        //创建流控规则的可读数据源
        ReadableDataSource<String, List<ParamFlowRule>> paramFlowRuleRDS = new FileRefreshableDataSource(
                paramFlowRuleFilePath, RuleListConverterUtils.paramFlowRuleListParse
        );

        // 将可读数据源注册至FlowRuleManager 这样当规则文件发生变化时，就会更新规则到内存
        ParamFlowRuleManager.register2Property(paramFlowRuleRDS.getProperty());


        WritableDataSource<List<ParamFlowRule>> paramFlowRuleWDS = new FileWritableDataSource<>(
                paramFlowRuleFilePath, RuleListConverterUtils.paramRuleEnCoding
        );

        // 将可写数据源注册至 transport 模块的 WritableDataSourceRegistry 中.
        // 这样收到控制台推送的规则时，Sentinel 会先更新到内存，然后将规则写入到文件中.
        ModifyParamFlowRulesCommandHandler.setWritableDataSource(paramFlowRuleWDS);
    }

    private void dealAuthRules() throws FileNotFoundException {
        //获取规则文件路径
        String authFilePath = PersistenceRuleConstant.rulesMap.get(PersistenceRuleConstant.AUTH_RULE_PATH).toString();

        //创建流控规则的可读数据源
        ReadableDataSource<String, List<AuthorityRule>> authRuleRDS = new FileRefreshableDataSource(
                authFilePath, RuleListConverterUtils.authorityRuleParse
        );

        // 将可读数据源注册至FlowRuleManager 这样当规则文件发生变化时，就会更新规则到内存
        AuthorityRuleManager.register2Property(authRuleRDS.getProperty());

        //创建流控规则的写数据源
        WritableDataSource<List<AuthorityRule>> authRuleWDS = new FileWritableDataSource<>(
                authFilePath, RuleListConverterUtils.authorityEncoding
        );

        // 将可写数据源注册至 transport 模块的 WritableDataSourceRegistry 中.
        // 这样收到控制台推送的规则时，Sentinel 会先更新到内存，然后将规则写入到文件中.
        WritableDataSourceRegistry.registerAuthorityDataSource(authRuleWDS);
    }

}
