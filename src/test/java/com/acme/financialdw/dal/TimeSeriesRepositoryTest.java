
package com.acme.financialdw.dal;

import com.acme.financialdw.dal.TimeSeriesRepository.PartitionKey;
import com.acme.financialdw.domain.TimeSeriesPoint;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TimeSeriesRepositoryTest {

    static final PartitionKey KEY = new PartitionKey("QDL/BITFINEX/BTCUSD", "BITFINEX");

    @Test
    void findLatest_returns_empty_when_no_data() {
        TimeSeriesRepository repo = mock(TimeSeriesRepository.class);
        when(repo.findLatest(KEY)).thenReturn(Optional.empty());
        assertTrue(repo.findLatest(KEY).isEmpty());
    }

    @Test
    void findLatest_returns_most_recent_point() {
        TimeSeriesRepository repo = mock(TimeSeriesRepository.class);
        TimeSeriesPoint point = TimeSeriesPoint.builder()
                .assetId("QDL/BITFINEX/BTCUSD")
                .dataSourceId("BITFINEX")
                .businessDate(LocalDate.of(2024, 1, 1))
                .indicators(Map.of("close", 42000.0))
                .build();
        when(repo.findLatest(KEY)).thenReturn(Optional.of(point));

        Optional<TimeSeriesPoint> result = repo.findLatest(KEY);
        assertTrue(result.isPresent());
        assertEquals(LocalDate.of(2024, 1, 1), result.get().getBusinessDate());
    }

    @Test
    void findTimeRange_returns_list() {
        TimeSeriesRepository repo = mock(TimeSeriesRepository.class);
        TimeSeriesPoint p1 = TimeSeriesPoint.builder()
                .businessDate(LocalDate.of(2024, 1, 2))
                .indicators(Map.of("close", 43000.0)).build();
        TimeSeriesPoint p2 = TimeSeriesPoint.builder()
                .businessDate(LocalDate.of(2024, 1, 1))
                .indicators(Map.of("close", 42000.0)).build();

        when(repo.findTimeRange(KEY, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3)))
                .thenReturn(List.of(p1, p2));

        List<TimeSeriesPoint> result = repo.findTimeRange(
                KEY, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 3));
        assertEquals(2, result.size());
        // newest first
        assertEquals(LocalDate.of(2024, 1, 2), result.get(0).getBusinessDate());
    }

    @Test
    void save_and_delete_are_invoked() {
        TimeSeriesRepository repo = mock(TimeSeriesRepository.class);
        TimeSeriesPoint point = TimeSeriesPoint.builder()
                .assetId("QDL/BITFINEX/BTCUSD")
                .businessDate(LocalDate.now()).build();
        when(repo.save(point)).thenReturn(point);

        TimeSeriesPoint saved = repo.save(point);
        assertNotNull(saved);
        repo.delete(saved);
        verify(repo).delete(saved);
    }
}
