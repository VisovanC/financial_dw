
package com.acme.financialdw.dal;

import com.acme.financialdw.domain.TimeSeriesPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Partition key: (assetId, dataSourceId).
 */
@Repository
@RequiredArgsConstructor
public class TimeSeriesRepository
        implements WarehouseRepository<TimeSeriesPoint, TimeSeriesRepository.PartitionKey> {

    private final MongoTemplate mongo;

    public record PartitionKey(String assetId, String dataSourceId) {}

    @Override
    public TimeSeriesPoint save(TimeSeriesPoint point) {
        if (point.getSystemDate() == null) point.setSystemDate(Instant.now());
        return mongo.insert(point);
    }

    @Override
    public void delete(TimeSeriesPoint point) {
        mongo.insert(TimeSeriesPoint.builder()
                .assetId(point.getAssetId())
                .dataSourceId(point.getDataSourceId())
                .businessDate(point.getBusinessDate())
                .systemDate(Instant.now())
                .deleted(true)
                .build());
    }

    @Override
    public void deleteAll(PartitionKey key) {
        findAll(key).forEach(this::delete);
    }

    @Override
    public Optional<TimeSeriesPoint> findLatest(PartitionKey key) {
        Query q = partitionQuery(key)
                .with(Sort.by(Sort.Direction.DESC, "systemDate"))
                .limit(1);
        TimeSeriesPoint result = mongo.findOne(q, TimeSeriesPoint.class);
        if (result == null || result.isDeleted()) return Optional.empty();
        return Optional.of(result);
    }

    @Override
    public List<TimeSeriesPoint> findAll(PartitionKey key) {
        Query q = partitionQuery(key)
                .with(Sort.by(Sort.Direction.ASC, "businessDate")
                          .and(Sort.by(Sort.Direction.DESC, "systemDate")));
        return mongo.find(q, TimeSeriesPoint.class);
    }

    /**
     * Q5 / /data endpoint:
     * Returns one point per businessDate (latest systemDate), non-deleted,
     * in the range [from, to) — start inclusive, end exclusive.
     * Results are ordered by businessDate DESC (newest first) per Lab 6 spec.
     */
    public List<TimeSeriesPoint> findTimeRange(
            PartitionKey key, LocalDate from, LocalDate to) {

        Criteria c = Criteria.where("assetId").is(key.assetId())
                .and("dataSourceId").is(key.dataSourceId())
                .and("businessDate").gte(from).lt(to);   // [from, to)

        Query q = Query.query(c)
                .with(Sort.by(Sort.Direction.ASC, "businessDate")
                          .and(Sort.by(Sort.Direction.DESC, "systemDate")));

        List<TimeSeriesPoint> all = mongo.find(q, TimeSeriesPoint.class);

        // Deduplicate: keep latest systemDate per businessDate, filter deleted
        return all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        TimeSeriesPoint::getBusinessDate,
                        p -> p,
                        (existing, newer) -> existing))   // already DESC so first wins
                .values()
                .stream()
                .filter(p -> !p.isDeleted())
                // Return newest businessDate first (DESC), as per spec
                .sorted(java.util.Comparator
                        .comparing(TimeSeriesPoint::getBusinessDate).reversed())
                .toList();
    }

    /**
     * Bi-temporal as-of snapshot: what did the warehouse look like at systemTime=asOf?
     */
    public List<TimeSeriesPoint> findAsOf(PartitionKey key, Instant asOf) {
        Criteria c = Criteria.where("assetId").is(key.assetId())
                .and("dataSourceId").is(key.dataSourceId())
                .and("systemDate").lte(asOf);

        Query q = Query.query(c)
                .with(Sort.by(Sort.Direction.ASC, "businessDate")
                          .and(Sort.by(Sort.Direction.DESC, "systemDate")));

        List<TimeSeriesPoint> all = mongo.find(q, TimeSeriesPoint.class);

        return all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        TimeSeriesPoint::getBusinessDate,
                        p -> p,
                        (existing, newer) -> existing))
                .values().stream()
                .filter(p -> !p.isDeleted())
                .sorted(java.util.Comparator
                        .comparing(TimeSeriesPoint::getBusinessDate).reversed())
                .toList();
    }

    private Query partitionQuery(PartitionKey key) {
        return Query.query(Criteria.where("assetId").is(key.assetId())
                .and("dataSourceId").is(key.dataSourceId()));
    }
}
