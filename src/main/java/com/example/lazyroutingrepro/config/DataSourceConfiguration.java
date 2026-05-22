package com.example.lazyroutingrepro.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

@Configuration
public class DataSourceConfiguration {

  @Bean
  @Primary
  public DataSource lazyConnectionPoolDataSource(
      @Qualifier("masterDataSource") DataSource masterDataSource,
      @Qualifier("slaveDataSource") DataSource slaveDataSource
  ) {
    var lazyConnectionDataSourceProxy = new LazyConnectionDataSourceProxy(masterDataSource);
    lazyConnectionDataSourceProxy.setReadOnlyDataSource(slaveDataSource);
    return lazyConnectionDataSourceProxy;
  }

  @Bean
  @ConfigurationProperties(prefix = "spring.datasource.master.hikari")
  public DataSource masterDataSource(
      @Qualifier("masterDataSourceProperties") DataSourceProperties masterDataSourceProperties
  ) {
    return masterDataSourceProperties.initializeDataSourceBuilder().build();
  }

  @Bean
  @ConfigurationProperties(prefix = "spring.datasource.slave.hikari")
  public DataSource slaveDataSource(
      @Qualifier("slaveDataSourceProperties") DataSourceProperties slaveDataSourceProperties
  ) {
    return slaveDataSourceProperties.initializeDataSourceBuilder().build();
  }

  @Bean
  @ConfigurationProperties(prefix = "spring.datasource.master")
  public DataSourceProperties masterDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @ConfigurationProperties(prefix = "spring.datasource.slave")
  public DataSourceProperties slaveDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @Primary
  public JdbcTemplate jdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean
  public JdbcTemplate masterJdbcTemplate(@Qualifier("masterDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean
  public JdbcTemplate slaveJdbcTemplate(@Qualifier("slaveDataSource") DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }
}
