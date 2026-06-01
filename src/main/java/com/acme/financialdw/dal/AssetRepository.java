
package com.acme.financialdw.dal;

import com.acme.financialdw.domain.AssetClass;
import com.acme.financialdw.domain.FinancialAsset;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB implementation for {@link FinancialAsset}.
 * Partition key: assetId (String).
 */
@Repository
@RequiredArgsConstructor
public class AssetRepository implements WarehouseRepository<FinancialAsset, String> {

    private final MongoTemplate mongo;

    @Override
    public FinancialAsset save(FinancialAsset asset) {
        if (asset.getSystemDate() == null) {
            asset.setSystemDate(Instant.now());
        }
        return mongo.insert(asset);
    }

    @Override
    public void delete(FinancialAsset asset) {
        FinancialAsset marker = FinancialAsset.builder()
                .assetId(asset.getAssetId())
                .systemDate(Instant.now())
                .deleted(true)
                .build();
        mongo.insert(marker);
    }

    @Override
    public void deleteAll(String assetId) {
        findLatest(assetId).ifPresent(this::delete);
    }

    @Override
    public Optional<FinancialAsset> findLatest(String assetId) {
        Query q = Query.query(Criteria.where("assetId").is(assetId))
                .with(Sort.by(Sort.Direction.DESC, "systemDate"))
                .limit(1);
        FinancialAsset result = mongo.findOne(q, FinancialAsset.class);
        if (result == null || result.isDeleted()) return Optional.empty();
        return Optional.of(result);
    }

    @Override
    public List<FinancialAsset> findAll(String assetId) {
        Query q = Query.query(Criteria.where("assetId").is(assetId))
                .with(Sort.by(Sort.Direction.DESC, "systemDate"));
        return mongo.find(q, FinancialAsset.class);
    }

    // ── Extra queries ─────────────────────────────────────────────────────

    /**
     * Returns the distinct assetIds of all non-deleted latest assets,
     * sorted alphabetically, with limit/offset pagination (Q1).
     */
    public List<String> findAllAssetIds(int offset, int limit) {
        // Aggregate: group by assetId, pick max systemDate, filter !deleted
        // Simplified approach: fetch all latest non-deleted and page in memory
        // (acceptable for moderate datasets; switch to aggregation pipeline at scale)
        Query q = new Query().with(Sort.by(Sort.Direction.ASC, "assetId")
                .and(Sort.by(Sort.Direction.DESC, "systemDate")));
        List<FinancialAsset> all = mongo.find(q, FinancialAsset.class);

        return all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        FinancialAsset::getAssetId,
                        a -> a,
                        (a, b) -> a.getSystemDate().isAfter(b.getSystemDate()) ? a : b))
                .values()
                .stream()
                .filter(a -> !a.isDeleted())
                .map(FinancialAsset::getAssetId)
                .sorted()
                .skip(offset)
                .limit(limit)
                .toList();
    }

    /** All latest active assets for a specific asset class (Q1 filter). */
    public List<FinancialAsset> findLatestByClass(AssetClass assetClass) {
        Query q = Query.query(
                Criteria.where("assetClass").is(assetClass)
                        .and("deleted").is(false))
                .with(Sort.by(Sort.Direction.DESC, "systemDate"));
        List<FinancialAsset> all = mongo.find(q, FinancialAsset.class);
        return all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        FinancialAsset::getAssetId,
                        a -> a,
                        (a, b) -> a.getSystemDate().isAfter(b.getSystemDate()) ? a : b))
                .values().stream()
                .filter(a -> !a.isDeleted())
                .toList();
    }
}
