package com.example.lazyroutingrepro.probe;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingProbeRepository extends JpaRepository<RoutingProbe, Long> {

  Optional<RoutingProbe> findFirstByOrderByIdAsc();
}
