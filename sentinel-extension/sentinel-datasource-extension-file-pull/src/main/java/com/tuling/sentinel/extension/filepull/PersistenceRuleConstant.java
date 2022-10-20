package com.tuling.sentinel.extension.filepull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Sentinel 规则持久化 常量配置类
 *
 * @author Fox
 */
public class PersistenceRuleConstant {

    /**
     * 存储文件路径
     */
    public static final String storePath = System.getProperty("user.home") + File.separator + "sentinel" + File.separator + "rules";

    /**
     * 各种存储sentinel规则映射map
     */
    public static final Map rulesMap = new HashMap<String,String>();

    //流控规则文件
    public static final String FLOW_RULE_PATH = "flowRulePath";

    //降级规则文件
    public static final String DEGRAGE_RULE_PATH = "degradeRulePath";

    //授权规则文件
    public static final String AUTH_RULE_PATH = "authRulePath";

    //系统规则文件
    public static final String SYSTEM_RULE_PATH = "systemRulePath";

    //热点参数文件
    public static final String HOT_PARAM_RULE = "hotParamRulePath";

    static {
        rulesMap.put(FLOW_RULE_PATH,storePath+ File.separator +"flowRule.json");
        rulesMap.put(DEGRAGE_RULE_PATH,storePath+File.separator +"degradeRule.json");
        rulesMap.put(SYSTEM_RULE_PATH,storePath+File.separator +"systemRule.json");
        rulesMap.put(AUTH_RULE_PATH,storePath+File.separator +"authRule.json");
        rulesMap.put(HOT_PARAM_RULE,storePath+File.separator +"hotParamRule.json");
    }
}