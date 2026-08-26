# smart-monitor

A lightweight, zero-config Spring Boot monitoring library. Add it as a dependency, hit `/monitor`, and get a live dashboard showing which services, repositories, and controllers are your real bottlenecks — no Prometheus, Grafana, or external monitoring stack required.

## Why

Most monitoring setups require a separate stack (agent + collector + dashboard) and non-trivial configuration. SmartMonitor is meant to be the opposite: **add the dependency, get a dashboard.** It uses Spring AOP to transparently wrap your `@Service`, `@Repository`, and `@Controller`/`@RestController` methods, records timing/DB/memory metrics in-process, and serves a self-contained dashboard at `/monitor` — all dynamically, with no manual instrumentation.

## Features

- **Zero-config activation** — just add the dependency; Spring Boot auto-configuration wires everything up automatically.
- **Automatic instrumentation** — every `@Service`, `@Repository`, `@Controller`, and `@RestController` method is monitored via AOP, with no annotations needed on your code.
- **Endpoint-level grouping** — metrics are grouped by the root API call (endpoint), with drill-down into the internal methods behind each one.
- **Self-time vs total-time** — distinguishes a method's own execution time from time spent in methods it calls, so you can find the *actual* slow line, not just the outermost slow call.
- **Bottleneck classification** — each endpoint is automatically categorized as a **CPU**, **IO/Database**, or **Other** bottleneck based on its DB-time ratio and error rate, with a dedicated "top bottleneck" card for each category.
- **Memory tracking** — approximate per-method heap allocation, aggregated per endpoint.
- **Live dashboard** — a self-hosted HTML page at `/monitor` with:
  - Summary cards (total calls, errors, DB time, CPU time, top bottleneck per category)
  - A live-updating line chart showing CPU vs DB load composition over time
  - A bar chart ranking the top 5 slowest endpoints
  - A sortable, click-to-drill-down endpoint/method table
- **Rolling window** — metrics older than a configurable window are automatically evicted, so memory usage stays bounded even under heavy, long-running traffic. A capped number of endpoints/methods is also enforced (oldest evicted first) to protect against unbounded cardinality.
- **Optional security bypass** — if Spring Security is on the classpath, `/monitor/**` is automatically opened up (without touching the rest of your app's security config) so the dashboard is reachable without a token.


## Quick Start

Add SmartMonitor to your Spring Boot application:

```xml
<dependency>
    <groupId>io.github.issa-khodadadi</groupId>
    <artifactId>smartmonitor</artifactId>
    <version>0.1.1</version>
</dependency>
```
Start your application and open:
```
http://localhost:8080/monitor
```
No annotations, bean registration, or manual instrumentation are required.

Make sure your project has `spring-boot-starter-aop` on the classpath (SmartMonitor uses AspectJ-based AOP).

That's it — no annotation, no bean registration, no manual setup. Start your app and open:

```
http://localhost:<port>/monitor
```

## Configuration

All settings are optional; sensible defaults are used if omitted.

```yaml
smartmonitor:
  window-minutes: 15              # how long metrics are kept before eviction
  cleanup-interval-seconds: 60    # how often the eviction sweep runs
  max-endpoints: 200              # max endpoints tracked at once (oldest evicted first)
  max-methods-per-endpoint: 100   # max methods tracked per endpoint (oldest evicted first)
```

### About `window-minutes`

Metrics are kept in a **rolling window**, not accumulated forever. Any endpoint or method that hasn't been called within `window-minutes` is evicted from memory automatically. This is intentional — it keeps memory usage bounded on high-traffic or long-running services.

**In practice:** if you leave the app idle for longer than the window (default 15 minutes), the dashboard will appear to "reset" — this is expected, not a bug. For local development where you don't want the dashboard to clear between breaks, increase the window, e.g.:

```yaml
smartmonitor:
  window-minutes: 480   # 8 hours
```

## How it works

- A Spring AOP `@Around` advice wraps calls to annotated Spring stereotypes (`@Service`, `@Repository`, `@Controller`, `@RestController`), excluding SmartMonitor's own internal classes as well as framework-internal controllers (e.g. springdoc/OpenAPI, Spring Boot Actuator) to avoid polluting the metrics with noise.
- The **first** method entered in a call chain is treated as the "endpoint" (root); every method called underneath it (services, repositories, nested calls) is attributed back to that endpoint.
- **Self-time** is calculated by subtracting the time spent in child calls from a method's total execution time, so the dashboard can point to the actual slow line inside a call chain — not just the outermost slow method.
- A method is classified as a **DATABASE** bottleneck if its DB-time ratio exceeds a threshold (or if it's a `@Repository` method), as **CPU** otherwise, and error-heavy endpoints are flagged separately.
- A background scheduled task periodically evicts stale data based on the configured rolling window.

## Roadmap

- [x] Automatic AOP-based instrumentation
- [x] Endpoint grouping with method drill-down
- [x] CPU / IO / Other bottleneck classification
- [x] Live dashboard with charts
- [x] Rolling window + bounded memory usage
- [ ] AI-powered analysis and optimization suggestions (in progress)

## License

