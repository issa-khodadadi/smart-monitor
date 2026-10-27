"""Train the in-process SmartMonitor diagnostic model.

The model is a multinomial logistic regression over bottleneck features.
It is intentionally tiny: inference is a few dozen multiplies, so Analyze
stays in-process and returns in well under a millisecond.

Run from the repo root:
    python tools/train_diagnostic_model.py
"""

import json
import math
import random
from pathlib import Path

SEED = 27
PER_CLASS = 1400
EPOCHS = 320
LEARNING_RATE = 0.35
L2 = 1e-4
TAIL_AVG_FLOOR = 0.1
MIN_CONFIDENCE = 0.55
OUTPUT = Path("smartmonitor/src/main/resources/models/diagnostic/model.json")

LABELS = [
    "HEALTHY",
    "N_PLUS_ONE",
    "SLOW_DATABASE",
    "CPU_HOTSPOT",
    "ERROR_BURST",
    "TAIL_LATENCY",
    "MEMORY_PRESSURE",
    "FRAGMENTED_TRACE",
]

FEATURE_NAMES = [
    "log1p_calls",
    "log1p_avg_ms",
    "log1p_p95_ms",
    "log1p_p99_ms",
    "log1p_max_ms",
    "db_ratio",
    "error_rate",
    "log1p_calls_per_request",
    "self_ratio",
    "log1p_memory_kb",
    "tail_ratio",
    "fragmented",
    "repository_share",
    "log1p_errors",
    "db_x_log_avg",
    "cpr_x_db",
    "error_x_log_calls",
]

RAW_KEYS = [
    "calls",
    "avg_ms",
    "p95_ms",
    "p99_ms",
    "max_ms",
    "db_ratio",
    "error_rate",
    "calls_per_request",
    "self_ratio",
    "memory_kb",
    "fragmented",
    "repository_share",
    "errors",
]


def clamp(value, low, high):
    return max(low, min(high, value))


def sample(rng, label):
    """Draw one raw observation from the class-conditional ranges."""
    if label == "HEALTHY":
        avg = rng.uniform(4, 140)
        db = rng.uniform(0.02, 0.62)
        raw = dict(
            calls=rng.uniform(20, 800),
            avg_ms=avg,
            p95_ms=avg * rng.uniform(1.1, 2.2),
            p99_ms=avg * rng.uniform(1.3, 3.2),
            max_ms=avg * rng.uniform(1.8, 5.0),
            db_ratio=db,
            error_rate=rng.uniform(0.0, 0.03),
            calls_per_request=rng.uniform(0.8, 7),
            self_ratio=rng.uniform(0.2, 0.82),
            memory_kb=rng.uniform(8, 12000),
            fragmented=0.0,
            repository_share=clamp(db * rng.uniform(0.6, 1.1), 0, 0.7),
            errors=rng.uniform(0, 6),
        )
    elif label == "N_PLUS_ONE":
        avg = rng.uniform(40, 1100)
        db = rng.uniform(0.4, 0.97)
        raw = dict(
            calls=rng.uniform(30, 700),
            avg_ms=avg,
            p95_ms=avg * rng.uniform(1.15, 2.4),
            p99_ms=avg * rng.uniform(1.4, 3.4),
            max_ms=avg * rng.uniform(2.2, 6.5),
            db_ratio=db,
            error_rate=rng.uniform(0.0, 0.05),
            calls_per_request=rng.uniform(12, 240),
            self_ratio=rng.uniform(0.04, 0.55),
            memory_kb=rng.uniform(40, 20000),
            fragmented=0.0,
            repository_share=clamp(db * rng.uniform(0.85, 1.05), 0, 0.99),
            errors=rng.uniform(0, 12),
        )
    elif label == "SLOW_DATABASE":
        avg = rng.uniform(220, 3800)
        db = rng.uniform(0.5, 0.99)
        raw = dict(
            calls=rng.uniform(8, 500),
            avg_ms=avg,
            p95_ms=avg * rng.uniform(1.05, 2.0),
            p99_ms=avg * rng.uniform(1.2, 3.0),
            max_ms=avg * rng.uniform(1.8, 5.5),
            db_ratio=db,
            error_rate=rng.uniform(0.0, 0.06),
            calls_per_request=rng.uniform(0.6, 7),
            self_ratio=rng.uniform(0.05, 0.5),
            memory_kb=rng.uniform(20, 15000),
            fragmented=0.0,
            repository_share=clamp(db * rng.uniform(0.85, 1.05), 0, 0.99),
            errors=rng.uniform(0, 15),
        )
    elif label == "CPU_HOTSPOT":
        avg = rng.uniform(90, 2800)
        raw = dict(
            calls=rng.uniform(10, 600),
            avg_ms=avg,
            p95_ms=avg * rng.uniform(1.05, 2.0),
            p99_ms=avg * rng.uniform(1.2, 2.8),
            max_ms=avg * rng.uniform(1.6, 4.5),
            db_ratio=rng.uniform(0.0, 0.28),
            error_rate=rng.uniform(0.0, 0.04),
            calls_per_request=rng.uniform(0.7, 5),
            self_ratio=rng.uniform(0.72, 0.99),
            memory_kb=rng.uniform(30, 25000),
            fragmented=0.0,
            repository_share=rng.uniform(0.0, 0.25),
            errors=rng.uniform(0, 8),
        )
    elif label == "ERROR_BURST":
        avg = rng.uniform(8, 900)
        raw = dict(
            calls=rng.uniform(30, 1000),
            avg_ms=avg,
            p95_ms=avg * rng.uniform(1.1, 3.0),
            p99_ms=avg * rng.uniform(1.3, 4.5),
            max_ms=avg * rng.uniform(1.8, 7.0),
            db_ratio=rng.uniform(0.0, 0.7),
            error_rate=rng.uniform(0.12, 0.95),
            calls_per_request=rng.uniform(0.7, 10),
            self_ratio=rng.uniform(0.15, 0.9),
            memory_kb=rng.uniform(10, 20000),
            fragmented=0.0,
            repository_share=rng.uniform(0.0, 0.7),
            errors=rng.uniform(15, 500),
        )
    elif label == "TAIL_LATENCY":
        avg = rng.uniform(8, 160)
        raw = dict(
            calls=rng.uniform(40, 1400),
            avg_ms=avg,
            p95_ms=avg * rng.uniform(3, 12),
            p99_ms=avg * rng.uniform(10, 50),
            max_ms=avg * rng.uniform(20, 90),
            db_ratio=rng.uniform(0.02, 0.65),
            error_rate=rng.uniform(0.0, 0.06),
            calls_per_request=rng.uniform(0.7, 6),
            self_ratio=rng.uniform(0.15, 0.8),
            memory_kb=rng.uniform(15, 12000),
            fragmented=0.0,
            repository_share=rng.uniform(0.02, 0.6),
            errors=rng.uniform(0, 12),
        )
    elif label == "MEMORY_PRESSURE":
        avg = rng.uniform(20, 900)
        raw = dict(
            calls=rng.uniform(8, 400),
            avg_ms=avg,
            p95_ms=avg * rng.uniform(1.1, 2.8),
            p99_ms=avg * rng.uniform(1.4, 4.0),
            max_ms=avg * rng.uniform(2.0, 7.0),
            db_ratio=rng.uniform(0.0, 0.4),
            error_rate=rng.uniform(0.0, 0.05),
            calls_per_request=rng.uniform(0.6, 5),
            self_ratio=rng.uniform(0.3, 0.92),
            memory_kb=rng.uniform(60000, 950000),
            fragmented=0.0,
            repository_share=rng.uniform(0.0, 0.35),
            errors=rng.uniform(0, 8),
        )
    else:
        avg = rng.uniform(30, 400)
        raw = dict(
            calls=rng.uniform(20, 400),
            avg_ms=avg,
            p95_ms=avg * rng.uniform(1.3, 2.5),
            p99_ms=avg * rng.uniform(1.8, 4.0),
            max_ms=avg * rng.uniform(2.5, 7.0),
            db_ratio=rng.uniform(0.15, 0.55),
            error_rate=rng.uniform(0.0, 0.04),
            calls_per_request=rng.uniform(2.0, 8.0),
            self_ratio=rng.uniform(0.15, 0.55),
            memory_kb=rng.uniform(30, 3000),
            fragmented=1.0,
            repository_share=rng.uniform(0.15, 0.6),
            errors=rng.uniform(0, 6),
        )

    raw["db_ratio"] = clamp(raw["db_ratio"], 0.0, 0.999)
    raw["error_rate"] = clamp(raw["error_rate"], 0.0, 0.99)
    raw["self_ratio"] = clamp(raw["self_ratio"], 0.0, 0.999)
    raw["repository_share"] = clamp(raw["repository_share"], 0.0, 0.999)
    raw["p95_ms"] = max(raw["p95_ms"], raw["avg_ms"])
    raw["p99_ms"] = max(raw["p99_ms"], raw["p95_ms"])
    raw["max_ms"] = max(raw["max_ms"], raw["p99_ms"])
    return raw


def featurize(raw):
    return [
        math.log1p(raw["calls"]),
        math.log1p(raw["avg_ms"]),
        math.log1p(raw["p95_ms"]),
        math.log1p(raw["p99_ms"]),
        math.log1p(raw["max_ms"]),
        raw["db_ratio"],
        raw["error_rate"],
        math.log1p(raw["calls_per_request"]),
        raw["self_ratio"],
        math.log1p(raw["memory_kb"]),
        raw["p99_ms"] / max(raw["avg_ms"], TAIL_AVG_FLOOR),
        raw["fragmented"],
        raw["repository_share"],
        math.log1p(raw["errors"]),
        raw["db_ratio"] * math.log1p(raw["avg_ms"]),
        math.log1p(raw["calls_per_request"]) * raw["db_ratio"],
        raw["error_rate"] * math.log1p(raw["calls"]),
    ]


def softmax(logits):
    peak = max(logits)
    exps = [math.exp(v - peak) for v in logits]
    total = sum(exps)
    return [v / total for v in exps]


def predict_row(weights, bias, row):
    logits = []
    for c in range(len(bias)):
        total = bias[c]
        wc = weights[c]
        for j, value in enumerate(row):
            total += wc[j] * value
        logits.append(total)
    probs = softmax(logits)
    label = max(range(len(probs)), key=probs.__getitem__)
    return label, probs[label]


def train(rows, targets, class_count):
    feature_count = len(rows[0])
    mean = [0.0] * feature_count
    for row in rows:
        for j, value in enumerate(row):
            mean[j] += value
    mean = [v / len(rows) for v in mean]
    var = [0.0] * feature_count
    for row in rows:
        for j, value in enumerate(row):
            delta = value - mean[j]
            var[j] += delta * delta
    std = [math.sqrt(v / len(rows)) or 1.0 for v in var]
    std = [v if v > 1e-8 else 1.0 for v in std]

    normed = []
    for row in rows:
        normed.append([(row[j] - mean[j]) / std[j] for j in range(feature_count)])

    weights = [[0.0] * feature_count for _ in range(class_count)]
    bias = [0.0] * class_count
    indexes = list(range(len(normed)))
    rng = random.Random(SEED + 1)

    for epoch in range(EPOCHS):
        rng.shuffle(indexes)
        lr = LEARNING_RATE * (0.35 if epoch > EPOCHS * 0.7 else 1.0)
        for i in indexes:
            row = normed[i]
            target = targets[i]
            logits = []
            for c in range(class_count):
                total = bias[c]
                wc = weights[c]
                for j, value in enumerate(row):
                    total += wc[j] * value
                logits.append(total)
            probs = softmax(logits)
            for c in range(class_count):
                grad = probs[c] - (1.0 if c == target else 0.0)
                bias[c] -= lr * grad
                wc = weights[c]
                for j, value in enumerate(row):
                    wc[j] -= lr * (grad * value + L2 * wc[j])
        if epoch % 40 == 0 or epoch == EPOCHS - 1:
            correct = 0
            for i, row in enumerate(normed):
                label, _ = predict_row(weights, bias, row)
                correct += label == targets[i]
            print(f"epoch {epoch:3d}  train_acc {correct / len(normed):.4f}")

    return weights, bias, mean, std


def accuracy(weights, bias, rows, targets, mean, std):
    correct = 0
    per_class = {i: [0, 0] for i in range(len(LABELS))}
    for row, target in zip(rows, targets):
        normed = [(row[j] - mean[j]) / std[j] for j in range(len(row))]
        label, _ = predict_row(weights, bias, normed)
        per_class[target][1] += 1
        if label == target:
            correct += 1
            per_class[target][0] += 1
    return correct / len(rows), per_class


REPORTS = {
    "HEALTHY": {
        "problem": "No dominant bottleneck",
        "severity": "LOW",
        "weight": 0.15,
        "locate": "endpoint",
        "summary": "{where} is the busiest endpoint in this window and it still looks healthy: average {avgMs} ms, p99 {p99Ms} ms, database share {dbPercent}%.",
        "suggestion": "Nothing to change right now. If p99 on {where} starts climbing far above the average, check pool waits and cold caches before adding capacity.",
        "evidence": ["avgMs", "p99Ms", "dbPercent"],
    },
    "N_PLUS_ONE": {
        "problem": "N+1 queries",
        "severity": "HIGH",
        "weight": 1.0,
        "locate": "maxCallsPerRequest",
        "summary": "{where} runs about {callsPerRequest} times per request, and the database accounts for {dbPercent}% of the time.",
        "suggestion": "The loop is issuing one query per item. Load the related rows once with a JOIN, an entity graph, or a single IN query, then match them in memory.",
        "evidence": ["callsPerRequest", "dbPercent", "avgMs"],
    },
    "SLOW_DATABASE": {
        "problem": "Slow database call",
        "severity": "HIGH",
        "weight": 0.95,
        "locate": "maxDbRepository",
        "summary": "{where} spends {dbPercent}% of its time in the database, with an average of {avgMs} ms.",
        "suggestion": "This is one slow query, not a loop. Check the filter and join columns on {where} for a missing index, and stop selecting columns the caller does not use.",
        "evidence": ["dbPercent", "avgMs", "p99Ms"],
    },
    "CPU_HOTSPOT": {
        "problem": "CPU hotspot",
        "severity": "HIGH",
        "weight": 0.9,
        "locate": "maxSelf",
        "summary": "{where} burns CPU inside the method itself: about {selfPercent}% of the endpoint time is its own work, average {avgMs} ms.",
        "suggestion": "Open {where} and look for nested loops, large JSON mapping, or repeated compilation of patterns. Move that work out of the request path or do it once.",
        "evidence": ["selfPercent", "avgMs", "dbPercent"],
    },
    "ERROR_BURST": {
        "problem": "Error burst",
        "severity": "HIGH",
        "weight": 1.0,
        "locate": "maxErrors",
        "summary": "{where} is failing on {errorPercent}% of calls in this window.",
        "suggestion": "Fix the exception at {where} before tuning speed. Guard the failing input, and do not retry a deterministic error.",
        "evidence": ["errorPercent", "avgMs"],
    },
    "TAIL_LATENCY": {
        "problem": "Tail latency",
        "severity": "MEDIUM",
        "weight": 0.75,
        "locate": "endpoint",
        "summary": "{where} is usually fast ({avgMs} ms average) but p99 is {p99Ms} ms.",
        "suggestion": "The average hides a stall. On {where}, check connection-pool waits, locks, and a cold cache. A timeout or a bound on the slow path will cut the tail.",
        "evidence": ["avgMs", "p99Ms", "dbPercent"],
    },
    "MEMORY_PRESSURE": {
        "problem": "Memory pressure",
        "severity": "MEDIUM",
        "weight": 0.8,
        "locate": "maxMemory",
        "summary": "{where} is allocating about {memoryKb} KB. That allocation, not the database, dominates the endpoint.",
        "suggestion": "Stop buffering the whole result in {where}. Stream it, page it, or cap the payload so the request does not hold a large graph on the heap.",
        "evidence": ["memoryKb", "avgMs", "selfPercent"],
    },
    "FRAGMENTED_TRACE": {
        "problem": "Split trace",
        "severity": "MEDIUM",
        "weight": 0.7,
        "locate": "endpoint",
        "summary": "{where} shares its trace with other roots, so one user action is being counted as several endpoints.",
        "suggestion": "A single request is hopping threads without carrying the trace. Pass the monitoring context into the async handoff so the time stays on {where}.",
        "evidence": ["avgMs", "dbPercent", "callsPerRequest"],
    },
}

EVIDENCE = {
    "callsPerRequest": "{callsPerRequest} calls per request",
    "dbPercent": "database is {dbPercent}% of time",
    "avgMs": "average {avgMs} ms",
    "p99Ms": "p99 {p99Ms} ms",
    "errorPercent": "{errorPercent}% of calls fail",
    "memoryKb": "{memoryKb} KB allocated",
    "selfPercent": "own time is {selfPercent}% of the endpoint",
}


def main():
    rng = random.Random(SEED)
    raw_rows = []
    targets = []
    for class_index, label in enumerate(LABELS):
        for _ in range(PER_CLASS):
            raw_rows.append(sample(rng, label))
            targets.append(class_index)

    features = [featurize(raw) for raw in raw_rows]
    order = list(range(len(features)))
    rng.shuffle(order)
    split = int(len(order) * 0.8)
    train_idx = order[:split]
    test_idx = order[split:]

    train_x = [features[i] for i in train_idx]
    train_y = [targets[i] for i in train_idx]
    test_x = [features[i] for i in test_idx]
    test_y = [targets[i] for i in test_idx]

    weights, bias, mean, std = train(train_x, train_y, len(LABELS))
    holdout, per_class = accuracy(weights, bias, test_x, test_y, mean, std)
    print(f"holdout_acc {holdout:.4f}  n={len(test_x)}")
    for index, label in enumerate(LABELS):
        hit, total = per_class[index]
        recall = hit / total if total else 0
        print(f"  {label:20s} recall {recall:.3f}  ({hit}/{total})")

    if holdout < 0.93:
        raise SystemExit(f"holdout accuracy {holdout:.4f} is below 0.93")

    fixtures = []
    for class_index, label in enumerate(LABELS):
        chosen = None
        for i in test_idx:
            if targets[i] != class_index:
                continue
            row = features[i]
            normed = [(row[j] - mean[j]) / std[j] for j in range(len(row))]
            predicted, confidence = predict_row(weights, bias, normed)
            if predicted == class_index and confidence >= 0.8:
                chosen = {
                    "label": label,
                    "confidence": round(confidence, 4),
                    "raw": {key: round(raw_rows[i][key], 6) for key in RAW_KEYS},
                }
                break
        if chosen is None:
            raise SystemExit(f"no confident fixture for {label}")
        fixtures.append(chosen)

    model = {
        "id": "smartmonitor-diagnostic",
        "version": "1.0.0",
        "trainedOn": "synthetic-bottleneck-v1",
        "trainer": "tools/train_diagnostic_model.py",
        "seed": SEED,
        "holdoutAccuracy": round(holdout, 4),
        "minConfidence": MIN_CONFIDENCE,
        "tailAvgFloor": TAIL_AVG_FLOOR,
        "featureNames": FEATURE_NAMES,
        "labels": LABELS,
        "mean": [round(v, 8) for v in mean],
        "std": [round(v, 8) for v in std],
        "weights": [[round(v, 8) for v in row] for row in weights],
        "bias": [round(v, 8) for v in bias],
        "reports": REPORTS,
        "evidence": EVIDENCE,
        "footer": "{modelId} {modelVersion} · {inferenceMs} ms · holdout accuracy {holdoutAccuracy}",
        "fixtures": fixtures,
    }

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(model, indent=2), encoding="utf-8")
    print(f"wrote {OUTPUT}")


if __name__ == "__main__":
    main()
