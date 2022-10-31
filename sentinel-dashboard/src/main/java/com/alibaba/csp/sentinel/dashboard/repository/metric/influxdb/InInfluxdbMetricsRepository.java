
package com.alibaba.csp.sentinel.dashboard.repository.metric.influxdb;

import com.alibaba.csp.sentinel.dashboard.datasource.entity.MetricEntity;
import com.alibaba.csp.sentinel.dashboard.repository.metric.MetricsRepository;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * 仓库类
 *
 * @author zhaojinliang
 * @version 1.0.0
 * @since 2022/10/31
 */
@Repository("inInfluxDbMetricsRepository")
public class InInfluxdbMetricsRepository implements MetricsRepository<MetricEntity> {

    @Autowired
    private InfluxDbProperties influxDbProperties;

    @Autowired
    public WriteApiBlocking writeApiBlocking;

    @Autowired
    public InfluxDBClient influxDBClient;

    @Autowired
    public QueryApi queryApi;

    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    @Override
    public void save(MetricEntity entity) {
        if (entity == null || StringUtil.isBlank(entity.getApp())) {
            return;
        }
        readWriteLock.writeLock().lock();
        try {
            InfluxDbMetricEntity influxDbMetricEntity = new InfluxDbMetricEntity();

            BeanUtils.copyProperties(entity, influxDbMetricEntity, new String[]{"gmtCreate", "gmtModified", "timestamp"});
            influxDbMetricEntity.setGmtCreate(entity.getGmtCreate().getTime());
            influxDbMetricEntity.setGmtModified(entity.getGmtModified().getTime());
            influxDbMetricEntity.setTimestamp(entity.getTimestamp().getTime());
            influxDbMetricEntity.setTime(Instant.now());
            writeApiBlocking.writeMeasurement(WritePrecision.MS, influxDbMetricEntity);
        } finally {
            readWriteLock.writeLock().unlock();
        }


    }

    @Override
    public void saveAll(Iterable<MetricEntity> metrics) {
        if (metrics == null) {
            return;
        }
        readWriteLock.writeLock().lock();
        try {
            metrics.forEach(this::save);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }


    @Override
    public List<MetricEntity> queryByAppAndResourceBetween(String app, String resource, long startTime, long endTime) {
        List<MetricEntity> results = new ArrayList<>();
        if (StringUtil.isBlank(app)) {
            return results;
        }
        readWriteLock.readLock().lock();
        try {
            // 根据APP和RESOURCE查询时间范围内的数据
            String command = String.format("from(bucket:\"%s\") |> range(start: %s, stop: %s)"
                            + " |> filter(fn: (r) => (r[\"_measurement\"] == \"sentinelMetric\" and r[\"app\"] == \"%s\") and r[\"resource\"] == \"%s\")",
                    influxDbProperties.getBucket(), startTime, endTime, app, resource);
            QueryApi queryApi = influxDBClient.getQueryApi();
            List<FluxTable> tables = queryApi.query(command);
            for (FluxTable fluxTable : tables) {
                List<FluxRecord> records = fluxTable.getRecords();
                for (FluxRecord fluxRecord : records) {
                    MetricEntity metricEntity = MetricEntity.copyOf(fluxRecord);
                    results.add(metricEntity);
                }
            }
            return results;
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public List<String> listResourcesOfApp(String app) {
        List<String> results = new ArrayList<>();
        if (StringUtil.isBlank(app)) {
            return results;
        }

        readWriteLock.readLock().lock();
        try {
            //查询最近5分钟的指标(实时数据)
            String command1 = String.format("from(bucket:\"%s\") |> range(start: -5m)\n" +
                    "  |> filter(fn: (r) => r[\"_measurement\"] == \"sentinelMetric\")\n" +
                    "  |> filter(fn: (r) => r[\"app\"] == \"" + app + "\")",influxDbProperties.getBucket());
            QueryApi queryApi = influxDBClient.getQueryApi();

            //查询
            List<FluxTable> tables = queryApi.query(command1);
            List<MetricEntity> influxResults = new ArrayList<>();
            for (FluxTable fluxTable : tables) {
                List<FluxRecord> records = fluxTable.getRecords();
                for (FluxRecord fluxRecord : records) {
                    MetricEntity metricEntity = MetricEntity.copyOf(fluxRecord);
                    influxResults.add(metricEntity);
                }
            }

            if (CollectionUtils.isEmpty(influxResults)) {
                return results;
            }
            Map<String, MetricEntity> resourceCount = new HashMap<>(32);
            for (MetricEntity metricEntity : influxResults) {
                String resource = metricEntity.getResource();
                if (resourceCount.containsKey(resource)) {
                    //累加统计
                    MetricEntity oldEntity = resourceCount.get(resource);
                    oldEntity.addPassQps(metricEntity.getPassQps());
                    oldEntity.addRtAndSuccessQps(metricEntity.getRt(), metricEntity.getSuccessQps());
                    oldEntity.addBlockQps(metricEntity.getBlockQps());
                    oldEntity.addExceptionQps(metricEntity.getExceptionQps());
                    oldEntity.addCount(1);
                } else {
                    resourceCount.put(resource, metricEntity);
                }
            }
            //排序
            results = resourceCount.entrySet()
                    .stream()
                    .sorted((o1, o2) -> {
                        MetricEntity e1 = o1.getValue();
                        MetricEntity e2 = o2.getValue();
                        int t = e2.getBlockQps().compareTo(e1.getBlockQps());
                        if (t != 0) {
                            return t;
                        }
                        return e2.getPassQps().compareTo(e1.getPassQps());
                    })
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            return results;
        } finally {
            readWriteLock.readLock().unlock();
        }

    }
}