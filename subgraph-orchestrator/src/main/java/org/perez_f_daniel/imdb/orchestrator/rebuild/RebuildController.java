package org.perez_f_daniel.imdb.orchestrator.rebuild;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.perez_f_daniel.imdb.orchestrator.rebuild.RebuildService.Step;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately REST, not GraphQL: the router exposes GraphQL to any authenticated
 * client, while /admin/* is reachable only by Cloud Run IAM invokers calling the
 * service directly. steps= lets a driver script chunk the long rebuild into
 * requests that fit within the Cloud Run request timeout.
 */
@RestController
public class RebuildController {

  private final RebuildService rebuild;
  private final MongoTemplate mongo;

  public RebuildController(RebuildService rebuild, MongoTemplate mongo) {
    this.rebuild = rebuild;
    this.mongo = mongo;
  }

  @PostMapping("/admin/rebuild")
  public ResponseEntity<Map<String, Object>> rebuild(
      @RequestParam(name = "steps", required = false) String steps) {
    List<Step> requested = steps == null || steps.isBlank()
        ? List.of(Step.values())
        : Arrays.stream(steps.split(","))
            .map(s -> Step.valueOf(s.strip().toUpperCase()))
            .toList();
    try {
      return ResponseEntity.ok(rebuild.run(requested));
    } catch (RebuildService.RebuildLockedException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("status", "RUNNING", "error", e.getMessage()));
    }
  }

  @GetMapping("/admin/rebuild/status")
  public Map<String, Object> status() {
    Document meta = mongo.findById("rebuild", Document.class, "search_meta");
    if (meta == null) {
      return Map.of("status", "NEVER_RUN");
    }
    meta.remove("_id");
    return meta;
  }
}
