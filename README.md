# SmartMonitor

**Zero-config, in-process monitoring for Spring Boot applications.**

Add one dependency, open `/monitor`, and see exactly which services, repositories, and controllers are your real bottlenecks — no Prometheus, no Grafana, no external agent, no manual instrumentation.

[![Maven Central](https://img.shields.io/maven-central/v/io.github.issa-khodadadi/smartmonitor)](https://central.sonatype.com/artifact/io.github.issa-khodadadi/smartmonitor)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](#license)
[![Java](https://img.shields.io/badge/Java-17%2B-orange)](#requirements)

---

## Why SmartMonitor

A typical monitoring setup means standing up a separate stack — agent, collector, time-series database, dashboard — and wiring configuration through all of it before you see a single graph. That's the right call for production-scale observability, but it's a lot of overhead when you just want to answer one question during development: **"which part of my app is actually slow?"**

SmartMonitor takes the opposite approach: add the dependency, and you get a dashboard. It uses Spring AOP to transparently wrap your `@Service`, `@Repository`, `@Controller`, and `@RestController` methods, records timing, DB, and memory metrics in-process, and serves a self-contained dashboard at `/monitor` — no annotations, no bean registration, no external infrastructure.

## Features

| Feature | Description |
|---|---|
| **Zero-config activation** | Add the dependency; Spring Boot auto-configuration wires everything up. |
| **Automatic instrumentation** | Every `@Service`, `@Repository`, `@Controller`, and `@RestController` method is monitored via AOP — no code changes required. |
| **Endpoint-level grouping** | Metrics are grouped by the root API call, with drill-down into every internal method behind it. |
| **Self-time vs. total-time** | Separates a method's own execution time from time spent in calls it makes, so you find the actual slow line — not just the slowest outer call. |
| **Bottleneck classification** | Each endpoint is auto-classified as **CPU**, **IO/Database**, or **Other**, based on DB-time ratio and error rate, with a "top bottleneck" card per category. |
| **Memory tracking** | Approximate per-method heap allocation, aggregated per endpoint. |
| **Live dashboard** | Self-hosted HTML page at `/monitor`: summary cards, a live CPU-vs-DB composition chart, a top-5 slowest-endpoints chart, and a sortable, drill-down endpoint/method table. |
| **Bounded memory** | A rolling time window automatically evicts stale metrics, and endpoint/method counts are capped (oldest evicted first) to protect against unbounded cardinality under long-running or high-traffic loads. |

## Requirements

- Java 17+
- Spring Boot 3.x
- `spring-boot-starter-aop` on the classpath (SmartMonitor uses AspectJ-based AOP)

## Quick Start

Add the dependency:

```xml
<dependency>
    <groupId>io.github.issa-khodadadi</groupId>
    <artifactId>smartmonitor</artifactId>
    <version>0.2.3</version>
</dependency>
```

Start your application and open:

```
http://localhost:<port>/monitor
```

That's it — no annotations, no bean registration, no manual setup.

## Configuration

All settings are optional; sensible defaults apply if omitted.

```yaml
smartmonitor:
  window-minutes: 15              # how long metrics are kept before eviction
  cleanup-interval-seconds: 60    # how often the eviction sweep runs
  max-endpoints: 200              # max endpoints tracked at once (oldest evicted first)
  max-methods-per-endpoint: 100   # max methods tracked per endpoint (oldest evicted first)
```

**About `window-minutes`:** metrics are kept in a rolling window, not accumulated indefinitely. Any endpoint or method not called within `window-minutes` is evicted automatically to keep memory usage bounded. If you leave the app idle longer than the window (default 15 minutes), the dashboard will appear to reset — this is expected. For local development sessions with long idle gaps, increase the window:

```yaml
smartmonitor:
  window-minutes: 480   # 8 hours
```

## How It Works

1. A Spring AOP `@Around` advice wraps calls to `@Service`, `@Repository`, `@Controller`, and `@RestController` beans, excluding SmartMonitor's own internal classes and framework controllers (springdoc/OpenAPI, Spring Boot Actuator) to keep metrics clean.
2. The **first** method entered in a call chain is treated as the "endpoint" (root); every nested call underneath it is attributed back to that endpoint.
3. **Self-time** is computed by subtracting time spent in child calls from a method's total execution time, isolating the actual slow line inside a call chain.
4. A method is classified as a **DATABASE** bottleneck if its DB-time ratio exceeds a threshold (or if it's a `@Repository` method); otherwise it's classified as **CPU**. Error-heavy endpoints are flagged separately.
5. A background scheduled task periodically evicts stale data per the configured rolling window.

## Things to Know Before Using in Production

- **Instrumentation overhead**: AOP interception adds a small per-call cost. Benchmark numbers for your workload are welcome as a contribution — see [Roadmap](#roadmap).
- **In-memory only**: metrics don't survive an application restart and aren't currently exportable to external systems (e.g. Prometheus format). This is by design for the zero-config use case, but export support is on the roadmap.
- **`/monitor` security bypass**: if Spring Security is on the classpath, `/monitor/**` is automatically permitted so the dashboard stays reachable without a token, without touching the rest of your app's security config. Review this before deploying to any publicly reachable environment.

## Roadmap

- [x] Automatic AOP-based instrumentation
- [x] Endpoint grouping with method drill-down
- [x] CPU / IO / Other bottleneck classification
- [x] Live dashboard with charts
- [x] Rolling window + bounded memory usage
- [ ] Published overhead/performance benchmarks
- [ ] Optional metrics export (e.g. Prometheus exposition format)
- [ ] Configurable/opt-out `/monitor` security bypass
- [ ] AI-powered analysis and optimization suggestions

## Contributing

Contributions are welcome — whether that's a bug report, a benchmark, a feature from the roadmap above, or something not listed here. Open an issue to discuss before starting on larger changes.

## License

MIT — see [LICENSE](LICENSE) for details.
