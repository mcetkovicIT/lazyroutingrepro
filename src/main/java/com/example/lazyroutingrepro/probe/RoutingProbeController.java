package com.example.lazyroutingrepro.probe;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/probe")
public class RoutingProbeController {

  private final RoutingProbeService routingProbeService;

  public RoutingProbeController(RoutingProbeService routingProbeService) {
    this.routingProbeService = routingProbeService;
  }

  @GetMapping("/readonly")
  public ProbeResponse readonly() {
    return this.routingProbeService.probeReadonly();
  }

  @PostMapping("/write")
  public ProbeResponse write(@RequestParam(defaultValue = "test-note") String note) {
    return this.routingProbeService.probeWrite(note);
  }

}
