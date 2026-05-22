package com.example.lazyroutingrepro.probe;

import java.sql.Connection;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoutingProbeService {

  private final RoutingProbeRepository routingProbeRepository;
  private final JdbcTemplate jdbcTemplate;

  public RoutingProbeService(
      RoutingProbeRepository routingProbeRepository,
      JdbcTemplate jdbcTemplate
  ) {
    this.routingProbeRepository = routingProbeRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional(readOnly = true)
  public ProbeResponse probeReadonly() {
    String marker = this.routingProbeRepository.findFirstByOrderByIdAsc()
        .orElseThrow(() -> new IllegalStateException("Missing routing probe row"))
        .getMarker();
    return buildResponse("readonly", marker);
  }

  @Transactional
  public ProbeResponse probeWrite(String note) {
    this.jdbcTemplate.update("insert into write_audit(note) values (?)", note);
    String marker = this.routingProbeRepository.findFirstByOrderByIdAsc()
        .orElseThrow(() -> new IllegalStateException("Missing routing probe row"))
        .getMarker();
    return buildResponse("write", marker);
  }

  private ProbeResponse buildResponse(String mode, String marker) {
    String currentDatabase = this.jdbcTemplate.queryForObject("select current_database()", String.class);
    Integer writeAuditCount = this.jdbcTemplate.queryForObject("select count(*) from write_audit", Integer.class);
    Boolean readOnly = this.jdbcTemplate.execute((ConnectionCallback<Boolean>) Connection::isReadOnly);
    return new ProbeResponse(
        mode,
        marker,
        currentDatabase,
        resolveNodeRole(currentDatabase),
        Boolean.TRUE.equals(readOnly),
        writeAuditCount == null ? 0 : writeAuditCount,
        Instant.now()
    );
  }

  private String resolveNodeRole(String currentDatabase) {
    return "masterdb".equalsIgnoreCase(currentDatabase) ? "WRITER" : "READER";
  }
}