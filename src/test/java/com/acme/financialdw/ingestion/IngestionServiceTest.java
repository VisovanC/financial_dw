
package com.acme.financialdw.ingestion;

import com.acme.financialdw.dal.AssetRepository;
import com.acme.financialdw.dal.DataSourceRepository;
import com.acme.financialdw.dal.TimeSeriesRepository;
import com.acme.financialdw.domain.DataSource;
import com.acme.financialdw.domain.FinancialAsset;
import com.acme.financialdw.ingestion.config.IngestionProperties;
import com.acme.financialdw.ingestion.pipeline.IngestionResult;
import com.acme.financialdw.ingestion.pipeline.IngestionService;
import com.acme.financialdw.ingestion.provider.FinancialDataProvider;
import com.acme.financialdw.ingestion.provider.RawRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionServiceTest {

    AssetRepository      assetRepo;
    DataSourceRepository dsRepo;
    TimeSeriesRepository tsRepo;
    FinancialDataProvider provider;
    IngestionService     service;

    @BeforeEach
    void setUp() {
        assetRepo = mock(AssetRepository.class);
        dsRepo    = mock(DataSourceRepository.class);
        tsRepo    = mock(TimeSeriesRepository.class);
        provider  = mock(FinancialDataProvider.class);

        when(provider.getProviderId()).thenReturn("BITFINEX");
        when(provider.getProviderName()).thenReturn("Nasdaq Data Link – Bitfinex");
        when(provider.getBaseUrl()).thenReturn("https://data.nasdaq.com/api/v3");
        when(provider.getProvenanceMetadata(anyString()))
                .thenReturn(Map.of("provider", "BITFINEX", "dataset", "BTCUSD"));

        // Simulate no existing asset/datasource so new ones get created
        when(assetRepo.findLatest(anyString())).thenReturn(Optional.empty());
        when(dsRepo.findLatest(anyString())).thenReturn(Optional.empty());
        when(assetRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(dsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tsRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        IngestionProperties props = new IngestionProperties();
        props.setBatchSize(10);
        service = new IngestionService(assetRepo, dsRepo, tsRepo, provider, props);
    }

    @Test
    void ingest_returns_correct_counts() {
        when(provider.extract(eq("BTCUSD"), any(), any())).thenReturn(List.of(
                new RawRecord("QDL/BITFINEX/BTCUSD", LocalDate.of(2024, 1, 1), Map.of("close", 42000.0)),
                new RawRecord("QDL/BITFINEX/BTCUSD", LocalDate.of(2024, 1, 2), Map.of("close", 43000.0))
        ));

        IngestionResult result = service.ingest("BTCUSD", null, null);

        assertEquals(2, result.fetched());
        assertEquals(2, result.stored());
        assertEquals(0, result.failed());
    }

    @Test
    void ingest_handles_empty_response() {
        when(provider.extract(anyString(), any(), any())).thenReturn(List.of());

        IngestionResult result = service.ingest("BTCUSD", null, null);

        assertEquals(0, result.fetched());
        assertEquals(0, result.stored());
    }

    @Test
    void ingest_handles_extraction_failure_gracefully() {
        when(provider.extract(anyString(), any(), any()))
                .thenThrow(new RuntimeException("network error"));

        IngestionResult result = service.ingest("XRPUSD", null, null);

        assertTrue(result.failed() > 0);
        assertEquals(0, result.stored());
    }
}
