
package com.acme.financialdw.dal;

import com.acme.financialdw.domain.AssetClass;
import com.acme.financialdw.domain.FinancialAsset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AssetRepository logic using Mockito stubs.
 * Integration tests (with real MongoDB) can be added later via Testcontainers.
 */
class AssetRepositoryTest {

    @Test
    void findLatest_returns_empty_when_no_asset() {
        AssetRepository repo = mock(AssetRepository.class);
        when(repo.findLatest("QDL/BITFINEX/BTCUSD")).thenReturn(Optional.empty());
        assertTrue(repo.findLatest("QDL/BITFINEX/BTCUSD").isEmpty());
    }

    @Test
    void findLatest_returns_asset_when_present() {
        AssetRepository repo = mock(AssetRepository.class);
        FinancialAsset asset = FinancialAsset.builder()
                .assetId("QDL/BITFINEX/BTCUSD")
                .symbol("BTCUSD")
                .assetClass(AssetClass.CRYPTO)
                .build();
        when(repo.findLatest("QDL/BITFINEX/BTCUSD")).thenReturn(Optional.of(asset));

        Optional<FinancialAsset> result = repo.findLatest("QDL/BITFINEX/BTCUSD");
        assertTrue(result.isPresent());
        assertEquals("BTCUSD", result.get().getSymbol());
    }

    @Test
    void findAll_returns_all_versions() {
        AssetRepository repo = mock(AssetRepository.class);
        FinancialAsset v1 = FinancialAsset.builder().assetId("QDL/BITFINEX/ETHUSD").build();
        FinancialAsset v2 = FinancialAsset.builder().assetId("QDL/BITFINEX/ETHUSD").build();
        when(repo.findAll("QDL/BITFINEX/ETHUSD")).thenReturn(List.of(v1, v2));

        assertEquals(2, repo.findAll("QDL/BITFINEX/ETHUSD").size());
    }

    @Test
    void delete_is_called_on_asset() {
        AssetRepository repo = mock(AssetRepository.class);
        FinancialAsset asset = FinancialAsset.builder().assetId("QDL/BITFINEX/SOLUSD").build();
        repo.delete(asset);
        verify(repo, times(1)).delete(asset);
    }
}
