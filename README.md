# SmartMonitor

**Zero-config, in-process monitoring for Spring Boot applications.**

Add one dependency, open `/monitor`, and see which services, repositories, and controllers are slow. Click **Analyze** for a short report: where the problem is and how to fix it. No Prometheus, no Grafana, no API key, and no agent.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.issa-khodadadi/smartmonitor)](https://central.sonatype.com/artifact/io.github.issa-khodadadi/smartmonitor)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](#license)
[![Java](https://img.shields.io/badge/Java-25-orange)](#requirements)

---

## Why SmartMonitor

A typical monitoring setup means standing up an agent, a collector, a time-series database, and a dashboard before you see one graph. That fits production-scale observability. It is a lot of machinery when the question is **"which part of this app is slow, and what should I change?"**

SmartMonitor stays inside the process. Spring AOP wraps `@Service`, `@Repository`, `@Controller`, and `@RestController` methods, keeps timing, database, error, and heap metrics in memory, and serves a dashboard at `/monitor`. **Analyze** runs a small model that ships inside the jar. It reads those metrics and returns a report. It does not call the network and it does not read your source code.

<img width="1269" height="750" alt="SmartMonitor dashboard" src="https://github.com/user-attachments/assets/a8ef830c-16ae-46b1-bcf4-d56c2b4d4fec" />

## Features

| Feature | Description |
|---|---|
| **Zero-config activation** | Add the dependency. Spring Boot auto-configuration wires the aspect, the APIs, and the page. |
| **Automatic instrumentation** | Every `@Service`, `@Repository`, `@Controller`, and `@RestController` method is timed. SmartMonitor, springdoc, and Actuator are excluded. |
| **Endpoint grouping** | An HTTP call is the root. Nested monitored methods are attributed to that endpoint. Work with no HTTP trace is listed under background roots. |
| **Self-time vs total-time** | Self-time is the method's own time after monitored child calls are subtracted. |
| **Bottleneck classification** | Endpoints are marked **CPU**, **DATABASE**, or **UNKNOWN** from database-time ratio and error rate. |
| **N+1 hint** | A method called many times per request is badged on the method drill-down. |
| **Latency and memory** | p95 and p99 come from a fixed-size reservoir. Heap delta is tracked per endpoint. |
| **Live dashboard** | Overview cards, a CPU-vs-DB chart, slowest endpoints, method drill-down, errors, traces, call graph, top called, and background roots. The page renders whatever the API schema describes. |
| **Analyze** | A trained model embedded in the jar. It names the endpoint, the problem, a confidence, and a concrete fix. Up to three findings. |
| **Bounded memory** | A rolling window drops stale endpoints. Endpoint, method, error, and trace counts are capped. |

## Requirements

- Java 25
- Spring Boot 4.1
- A servlet app. The library brings Spring MVC and AspectJ.

Spring Security is optional. When it is on the classpath, `/monitor/**` is permitted on its own filter chain so the page stays reachable. The rest of your security rules are unchanged.

## Quick Start

```xml
<dependency>
    <groupId>io.github.issa-khodadadi</groupId>
    <artifactId>smartmonitor</artifactId>
    <version>0.3.0</version>
</dependency>
```

Start the app and open:

```
http://localhost:<port>/monitor
```

If the app has a servlet context path, the page is that path plus `/monitor`. Example: context path `/accounting/api` serves the dashboard at `http://localhost:8089/accounting/api/monitor`.

No annotations and no bean registration. Generate some traffic, then open the page. **Analyze** is on by default.

Classes that are not `@Service`, `@Repository`, `@Controller`, or `@RestController` are not timed. A slow HTTP client, a lock, or a plain Java call inside a controller shows up as time the dashboard cannot attribute to a child method.

## Analyze

For now, the Analyze feature and the embedded diagnostic model were built with [Cursor](https://cursor.com). I am training the next version on real application data with pandas, scikit-learn, and [Google Colab](https://colab.research.google.com). The jar still ships the current weights, so you do not wait for that training to use Analyze.

The model file is `models/diagnostic/model.json` inside the jar (`smartmonitor-diagnostic`). Inference is a few dozen multiplications on the endpoint's metrics: call count, average and tail latency, database ratio, error rate, calls per request, self-time, heap, and whether the trace is split across roots.

The report is filled from the winning class. It can describe an N+1 loop, a slow repository call, a CPU hotspot, an error burst (including the latest exception), tail latency, memory allocated on the method's own path, a wait outside monitored code, or a fragmented trace.

You do not train anything and you do not install Python. The weights are already in the library. Override the resource only if you ship your own file:

```yaml
smartmonitor:
  ai:
    enabled: true
    provider: diagnostic
    model-resource: models/diagnostic/model.json
    cache-seconds: 15
    max-findings: 3
```

`provider` stays `diagnostic`. There is no OpenAI, Anthropic, Ollama, or local language-model path.

## Configuration

Every setting has a default in `smartmonitor/src/main/resources/smartmonitor-defaults.yaml`. Put the same keys in the application's `application.yml` to override them. View columns and report fields live in `smartmonitor/src/main/resources/smartmonitor/monitor-views.json`.

```yaml
smartmonitor:
  window-minutes: 15
  cleanup-interval-millis: 60000
  max-endpoints: 200
  max-methods-per-endpoint: 100
  paths:
    page: /monitor
    permit-pattern: /monitor/**
  ai:
    enabled: true
    provider: diagnostic
```

Paths in that file are relative to the servlet context path. They do not include it.

Each monitor API returns one envelope: `ok`, `code`, `view`, `generatedAt`, `message`, `meta`, `schema`, `data`. The dashboard draws tables, cards, and the report from `schema`.

**About `window-minutes`:** metrics live in a rolling window. An endpoint that is quiet for longer than the window is dropped. After a long idle the dashboard looks empty again. For a long local session:

```yaml
smartmonitor:
  window-minutes: 480
```

## How It Works

1. An `@Around` advice wraps `@Service`, `@Repository`, `@Controller`, and `@RestController` beans, excluding `com.issa.smartmonitor`, springdoc, and Actuator.
2. A filter stores the inbound trace (`X-Request-Id`, `X-Correlation-Id`, `X-Trace-Id`, or `traceparent`) and returns `X-Trace-Id`. The first monitored method of that HTTP call is the endpoint. Later monitored calls in the same thread are children of it.
3. Self-time is total time minus time spent in monitored children. `@Repository` time is added to the endpoint's database time.
4. An endpoint is **DATABASE** when database time exceeds 40% of total time, **UNKNOWN** when the error rate exceeds 5%, and **CPU** otherwise. A `@Repository` method is **DATABASE** on its own row.
5. A scheduled task evicts endpoints and background roots older than the window, and samples CPU and database rates for the chart.
6. **Analyze** scores the current window with the embedded model and keeps the highest-scoring findings. Results are cached for 15 seconds unless you press Refresh.

## Things to Know Before Using in Production

- **Overhead.** The aspect runs on every monitored call. Published benchmark numbers are still on the roadmap.
- **In memory only.** Metrics disappear on restart and are not exported to Prometheus.
- **`/monitor` is permitted** when Spring Security is present. The matcher is `smartmonitor.paths.permit-pattern` (default `/monitor/**`). Check that before exposing the app on a public network.
- **Heap numbers are noisy.** The model uses them only together with self-time. A large heap delta alone is not treated as a memory problem.
- **Unmonitored calls.** Time inside a class the aspect does not wrap is visible only as a gap between an endpoint's total time and its self-time.

## Roadmap

- [x] Automatic AOP instrumentation
- [x] Endpoint grouping with method drill-down
- [x] CPU / database / other classification
- [x] Live dashboard driven by a shared response schema
- [x] Rolling window and capped metric storage
- [x] In-process Analyze with an embedded model and a fix suggestion
- [ ] Published overhead numbers
- [ ] Optional metrics export (Prometheus)
- [ ] A switch to turn off the `/monitor` permit without editing the matcher

## Contributing

Bug reports, benchmarks, and roadmap items are welcome. Open an issue before a large change.

## License

MIT — see [LICENSE](LICENSE) for details.
