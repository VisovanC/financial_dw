
package com.acme.financialdw.analytics;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Reads Spark job outputs from MongoDB and exposes them via REST.
 * Collections are written by the spark-jobs module.
 */
@RestController
@RequestMapping("/api/v1/spark")
@RequiredArgsConstructor
@Tag(name = "Spark Results", description = "Read aggregation and regression results produced by Spark jobs")
public class SparkResultsController {

    private final MongoTemplate mongo;

    @GetMapping("/aggregation")
    public ResponseEntity<List<Map>> aggregation() {
        return ResponseEntity.ok(mongo.findAll(Map.class, "analytics_totals"));
    }

    @GetMapping("/regression/results")
    public ResponseEntity<List<Map>> regressionResults() {
        return ResponseEntity.ok(mongo.findAll(Map.class, "regression_results"));
    }

    @GetMapping("/regression/predictions")
    public ResponseEntity<List<Map>> regressionPredictions() {
        return ResponseEntity.ok(mongo.findAll(Map.class, "regression_predictions"));
    }
}
