
package com.acme.financialdw.dal;

import java.util.List;
import java.util.Optional;

/**
 * Generic warehouse repository — append-only, no in-place updates.
 *
 * @param <E> entity type
 * @param <K> partition key type
 */
public interface WarehouseRepository<E, K> {

    /** Append a new version. systemDate is set automatically if absent. */
    E save(E entity);

    /** Soft-delete: appends a marker record with deleted=true. */
    void delete(E entity);

    /** Soft-delete all records for the partition. */
    void deleteAll(K partitionKey);

    /**
     * Returns the newest non-deleted version for this partition key,
     * i.e. the record with the highest systemDate.
     */
    Optional<E> findLatest(K partitionKey);

    /**
     * Returns ALL versions (including deleted markers) for this partition key,
     * ordered by systemDate DESC.
     */
    List<E> findAll(K partitionKey);
}
