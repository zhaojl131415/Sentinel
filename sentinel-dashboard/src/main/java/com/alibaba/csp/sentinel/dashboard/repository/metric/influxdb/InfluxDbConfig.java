package com.alibaba.csp.sentinel.dashboard.repository.metric.influxdb;


import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据库配置
 *
 * @author zhaojinliang
 * @version 1.0.0
 * @since 2022/10/31
 */
@Configuration
@EnableConfigurationProperties(InfluxDbProperties.class)
public class InfluxDbConfig {


    @Autowired(required = false)
    private InfluxDbProperties influxDbProperties;

    /**
     * 初始化influx client
     * @return
     */
    @Bean
    public InfluxDBClient influxDBClient() {
        InfluxDBClient influxDBClient = InfluxDBClientFactory.create(
                influxDbProperties.getUrl(),
                influxDbProperties.getToken().toCharArray(),
                influxDbProperties.getOrg(),influxDbProperties.getBucket());
        return influxDBClient;
    }

    /**
     * 保存数据
     * @param influxDBClient
     * @return
     */
    @Bean
    public WriteApiBlocking write(InfluxDBClient influxDBClient) {
        WriteApiBlocking writeApiBlocking = influxDBClient.getWriteApiBlocking();
        return writeApiBlocking;
    }

    /**
     * 查询数据
     * @param influxDBClient
     * @return
     */
    @Bean
    public QueryApi query(InfluxDBClient influxDBClient) {
        QueryApi queryApi = influxDBClient.getQueryApi();
        return queryApi;
    }

}