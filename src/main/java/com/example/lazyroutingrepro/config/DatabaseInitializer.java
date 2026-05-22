package com.example.lazyroutingrepro.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseInitializer {

  @Bean
  public ApplicationRunner initializeDatabases(
      @Qualifier("masterJdbcTemplate") JdbcTemplate masterJdbcTemplate,
      @Qualifier("slaveJdbcTemplate") JdbcTemplate slaveJdbcTemplate
  ) {
    return args -> {
      initialize(masterJdbcTemplate, "MASTER");
      initialize(slaveJdbcTemplate, "SLAVE");
    };
  }

  private void initialize(JdbcTemplate jdbcTemplate, String marker) {
    jdbcTemplate.execute("""
        create table if not exists routing_probe (
          id bigint generated always as identity primary key,
          marker varchar(32) not null
        )
        """);
    jdbcTemplate.execute("""
        create table if not exists write_audit (
          id bigint generated always as identity primary key,
          note varchar(128) not null,
          created_at timestamptz not null default now()
        )
        """);
    jdbcTemplate.update("truncate table routing_probe restart identity");
    jdbcTemplate.update("insert into routing_probe(marker) values (?)", marker);
  }
}