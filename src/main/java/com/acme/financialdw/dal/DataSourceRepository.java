
package com.acme.financialdw.dal;

import com.acme.financialdw.domain.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class DataSourceRepository implements WarehouseRepository<DataSource, String> {

    private final MongoTemplate mongo;

    @Override
    public DataSource save(DataSource ds) {
        if (ds.getSystemDate() == null) ds.setSystemDate(Instant.now());
        return mongo.insert(ds);
    }

    @Override
    public void delete(DataSource ds) {
        mongo.insert(DataSource.builder()
                .dataSourceId(ds.getDataSourceId())
                .systemDate(Instant.now())
                .deleted(true)
                .build());
    }

    @Override
    public void deleteAll(String dataSourceId) {
        findLatest(dataSourceId).ifPresent(this::delete);
    }

    @Override
    public Optional<DataSource> findLatest(String dataSourceId) {
        Query q = Query.query(Criteria.where("dataSourceId").is(dataSourceId))
                .with(Sort.by(Sort.Direction.DESC, "systemDate"))
                .limit(1);
        DataSource result = mongo.findOne(q, DataSource.class);
        if (result == null || result.isDeleted()) return Optional.empty();
        return Optional.of(result);
    }

    @Override
    public List<DataSource> findAll(String dataSourceId) {
        Query q = Query.query(Criteria.where("dataSourceId").is(dataSourceId))
                .with(Sort.by(Sort.Direction.DESC, "systemDate"));
        return mongo.find(q, DataSource.class);
    }

    /** Distinct dataSourceIds, alphabetically sorted, with offset/limit. */
    public List<String> findAllDataSourceIds(int offset, int limit) {
        List<DataSource> all = mongo.find(
                new Query().with(Sort.by(Sort.Direction.DESC, "systemDate")),
                DataSource.class);
        return all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DataSource::getDataSourceId,
                        d -> d,
                        (a, b) -> a.getSystemDate().isAfter(b.getSystemDate()) ? a : b))
                .values().stream()
                .filter(d -> !d.isDeleted())
                .map(DataSource::getDataSourceId)
                .sorted()
                .skip(offset)
                .limit(limit)
                .toList();
    }
}
