package com.alibaba.csp.sentinel.dashboard.repository.metric.influxdb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库属性配置
 *
 * @author zhaojinliang
 * @version 1.0.0
 * @since 2022/10/31
 */
@ConfigurationProperties(prefix = "spring.influxdb")
public class InfluxDbProperties {

    @Value("${spring.influxdb.url:''}")
    private String url;

    @Value("${spring.influxdb.org:''}")
    private String org;

    @Value("${spring.influxdb.bucket:''}")
    private String bucket;

    @Value("${spring.influxdb.token:''}")
    private String token;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOrg() {
        return org;
    }

    public void setOrg(String org) {
        this.org = org;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
