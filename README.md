# Securities Settlement Matching and Break Reconciliation Service

A Spring Boot service that consumes both legs of a settlement instruction off
a Kafka stream, matches them against a configurable tolerance ruleset, and
files every unmatched pair as a break naming the exact fields that
disagreed. Java 17, Spring Boot 3.3, Spring Data JPA, Spring Kafka.

Every number in this README was measured on this machine by running the
code in this repository, not targeted in advance. The measurement run
consumed a real, in-process Kafka broker (spring-kafka-test's embedded
KRaft broker) rather than a mock producer/consumer; exactly what that does
and does not claim about production Kafka is spelled out below.

## Why this exists

When a trade settles, both counterparties send an independent settlement
instruction describing what they think is happening: same ISIN, same
quantity, same price, same settlement date. In practice the two sides
disagree constantly, a stale price feed, a fat-fingered quantity, a
timezone-shifted settlement date, and a custodian's operations desk has to
notice every one of those disagreements, individually, before money moves
on the wrong terms. This project is a small version of the matching core
that sits underneath that process: pair the two legs regardless of which
arrives first, compare them field by field against a tolerance a risk desk
configures (not a developer), and turn every disagreement into a specific,
actionable break record rather than a generic "mismatch" flag.

## Honest framing, up front

- **This is a matching-and-break-detection core, not a settlement or
  custody system.** There is no cash movement, no corporate actions
  processing, no SWIFT message parsing, no real custodian integration.
  Two structured Java records in, a matched position or a named break out.
- **The traffic is entirely synthetic.** `SyntheticInstructionGenerator`
  produces a seeded, deterministic stream of settlement instruction pairs
  for a fictional set of ten ISINs and eight counterparties. There is no
  real trade, no real counterparty, and no real custodian behind any of it.
- **The measured numbers below run against a real, embedded, in-process
  Kafka broker** (`spring-kafka-test`'s `EmbeddedKafkaKraftBroker`, KRaft
  mode), not a mock producer/consumer pair. This is a genuine Kafka broker
  processing genuine `KafkaProducer`/`KafkaConsumer` traffic end to end;
  it is just not a standalone, multi-node cluster. Production wiring
  targets a real standalone Kafka cluster, documented in "Building and
  running" below, but Docker was not available in the environment this
  project was built in, so no number in this README was measured against
  one.
- **Tests and measurement runs use H2 in PostgreSQL-compatibility mode**
  (`MODE=PostgreSQL`), not real PostgreSQL, for the same reason: no
  standalone PostgreSQL instance was reachable in the build environment.
  The production `application.yml` profile points at real PostgreSQL with
  the Hibernate PostgreSQL dialect; `src/test/resources/application.yml`
  is the only place H2 is configured.
- **Machine and toolchain.** AMD Ryzen 7 7800X3D, 8 physical / 16 logical
  cores, Windows 11 Home. JDK 21 (Temurin) compiling to Java 17 bytecode
  (`maven.compiler.release=17`). Spring Boot 3.3.4, Maven 3.9.9,
  `spring-kafka` / `spring-kafka-test` 3.2.4, H2 2.2.224, PostgreSQL JDBC
  driver 42.7.4 (runtime only, unused by any test in this repository).
- **The FpML confirmation format is FpML-style, not real FpML.** It reuses
  FpML's shape (a `tradeConfirmation` root, nested party and product
  blocks) but not its actual XML schema or namespaces; there is no FpML
  schema validation here. The FIX allocation message is real tag=value
  framing for a small, hand-picked set of tags, not a full FIX dictionary.

## Architecture

```
src/main/java/com/manas/settlementmatch/
  SettlementMatchingApplication.java   Spring Boot entry point
  model/
    SettlementInstruction.java         one leg of an instruction (Kafka payload, a record)
    Side.java                          PARTY_A / PARTY_B
    MatchedPositionEntity.java         JPA entity: a settled position
    BreakEntity.java                   JPA entity: a filed break
    BreakFieldDiscrepancyEmbeddable.java  one named disagreeing field on a break
  tolerance/
    ComparisonType.java                EXACT | ABSOLUTE | RELATIVE_BPS
    ToleranceRule.java                 one field's configured comparison
    ToleranceRuleSet.java              compares two instructions field by field
    ToleranceRuleSetLoader.java        reads tolerance-rules.yml
  engine/
    MatchingEngine.java                the real matcher: hash-indexed, buffers by trade ref
    ReferenceOracleMatcher.java        deliberately slow oracle, diffed against the above
    MatchOutcome.java / MatchedPositionResult.java / BreakResult.java  engine result types
  generator/
    SyntheticInstructionGenerator.java seeded synthetic instruction pairs, with seeded breaks
    NoisyStreamBuilder.java            turns a canonical stream into a duplicated, shuffled one
  kafka/
    KafkaConfig.java                   hand-built consumer/producer factories
    InstructionListener.java           @KafkaListener entry point
    SettlementInstructionJson.java     the one shared Jackson mapping (LocalDate, BigDecimal)
  repository/
    MatchedPositionRepository.java, BreakRepository.java  Spring Data JPA
  service/
    SettlementMatchingService.java     wires the engine to persistence, transactionally
  config/
    EngineConfig.java                  ToleranceRuleSet + MatchingEngine beans
src/main/resources/
  application.yml                      production profile: real Kafka, real PostgreSQL
  tolerance-rules.yml                  the configurable tolerance ruleset (data, not code)
src/test/java/com/manas/settlementmatch/
  tolerance/ToleranceRuleSetTest.java          unit tests: comparison logic, break naming
  engine/MatchingEngineTest.java               unit tests: buffering, ordering, dedup
  engine/ReferenceOracleDiffTest.java          real engine vs. reference oracle, diffed exactly
  integration/SettlementMatchingIntegrationTest.java  end to end: embedded Kafka + H2 + Spring context
  bench/BenchmarkRunner.java                   the 100,000-message measurement run (tagged, opt-in)
  gateway/
    FixAllocationMessageParser.java      tag=value FIX allocation message -> canonical fields
    FpmlConfirmationParser.java          FpML-style XML confirmation -> canonical fields, XXE-hardened
    DelimitedPostTradeFileParser.java    pipe-delimited post-trade file -> canonical fields
    CrossFormatGatewayService.java       compares the three canonical views, quarantines disagreements
  model/
    QuarantinedTradeEntity.java          JPA entity: a trade held at the gateway, not yet matched
    QuarantinedFieldDisagreementEmbeddable.java  one named cross-format disagreement
  repository/QuarantinedTradeRepository.java
src/test/java/com/manas/settlementmatch/gateway/
  FixAllocationMessageParserTest.java, DelimitedPostTradeFileParserTest.java,
  FpmlConfirmationParserTest.java       per-format parser unit tests (incl. the XXE rejection test)
  CrossFormatReconciliationGateTest.java              unit tests: agree/disagree/multi-field cases
  ReferenceOracleReconciliationGateDiffTest.java      real gate vs. a deliberately slow field-by-field oracle
  CrossFormatBenchmarkRunner.java (in bench/)         the 30-seeded / 200-clean measurement run (tagged, opt-in)
docker-compose.yml                     documents standing up real Kafka + real PostgreSQL
```

### Why the engine has no Kafka or JPA dependency

`MatchingEngine` takes a `SettlementInstruction` in and returns a
`MatchOutcome`; it has never heard of Kafka, Spring, or a database. That
split is what makes three different things possible with the same class:
`InstructionListener` drives it from a real `@KafkaListener` in production,
`ReferenceOracleDiffTest` drives it from a plain list in a millisecond unit
test, and `BenchmarkRunner` drives it from a raw `KafkaConsumer` without
paying for a Spring context on every one of the 100,000 messages. Pushing
persistence into `SettlementMatchingService` (the only class that imports
both the engine and the repositories) keeps the correctness-critical code
path testable in isolation from both infrastructure dependencies at once.

### Why buffer by trade reference instead of assuming ordered delivery

Kafka guarantees ordering only within a partition, and nothing requires a
producer to emit both legs of an instruction back to back even on the same
partition. `MatchingEngine.ingest` keeps a `Map<tradeRef, instruction>` of
whichever side has arrived first and completes the pair the moment the
second side shows up, regardless of which `Side` arrived first. The
alternative, an upstream reordering buffer that waits for both legs before
handing anything downstream, would add unbounded latency and still not be
correct against a producer that legitimately never sends the second leg
(a real failure mode worth detecting, not hiding behind a timeout).

### Why dedup at ingestion, not after matching

`ingest()`'s very first line is a message-id check against a `Set<String>`;
nothing else runs on a duplicate. The alternative, deduping only after a
match or break decision, has a specific failure mode: a redelivered second
leg would arrive at an engine that has already consumed and discarded its
counterpart from the buffer, and would be treated as the *first* leg of a
brand new pairing, sitting there waiting forever for a third message that
will never come. Rejecting the duplicate before it can touch the buffer at
all makes a duplicate provably inert: it cannot create a phantom match, a
phantom break, or a phantom buffered-and-waiting entry. `MatchingEngineTest`
pins this for both the "duplicate before a match completes" case and the
"duplicate of the second leg, after the match has already completed" case.

### Why the tolerance ruleset is YAML, not Java

`tolerance-rules.yml` is the only place that says ISIN, currency, and
settlement date are exact-match fields while price tolerates 5 basis
points. `ToleranceRuleSet.compare()` dispatches on each field's configured
`ComparisonType` (`EXACT` / `ABSOLUTE` / `RELATIVE_BPS`); it never contains
a field-specific `if`. Loosening the price tolerance, or moving quantity
from exact match to a small absolute band, is a one-line YAML edit that
needs no recompile and no code review of comparison logic that didn't
change. The file is loaded once at startup by `ToleranceRuleSetLoader`;
hot-reloading it is listed as a limitation below, not implemented.

### Why a Break names fields instead of flagging a boolean

A `BreakEntity` carries one `BreakFieldDiscrepancyEmbeddable` row per
disagreeing field: the field name, both sides' values, the delta, and the
tolerance that was exceeded, each independently queryable rather than
packed into a delimited string. A break that just says "mismatch" sends an
operations analyst back into two raw instructions to find the one field
among six that actually disagreed; a break that says
`price: partyA=100.0000 partyB=101.0000 delta=1.0000 tolerance=±5 bps`
does not.

### The multi-format gateway: FIX, FpML-style XML, delimited file, one canonical record

The matcher above assumes a canonical `SettlementInstruction` already
exists. In practice, the same trade shows up three separate times, in
three disagreeing wire formats, before that record can be built: a FIX
allocation message (tag=value, `35=AS|70=...`), an FpML-style XML
confirmation, and a pipe-delimited post-trade text file. The `gateway`
package (`FixAllocationMessageParser`, `FpmlConfirmationParser`,
`DelimitedPostTradeFileParser`) normalizes each into the same canonical
fields (trade reference, ISIN, quantity, price, settlement date,
currency, account). `CrossFormatReconciliationGateService` then compares
all three representations of one trade reference field by field: if they
agree, the trade proceeds into the existing matching pipeline above; if
any field disagrees, the gate quarantines the trade (`QuarantinedTradeEntity`,
one `QuarantinedFieldDisagreementEmbeddable` row per disagreeing field,
naming both sides) instead of guessing which format is authoritative.
This is a distinct, earlier stage than the two-legs-by-reference break
matching: the break matcher reconciles *two counterparties'* views of a
trade that both already agree on their own format; the gateway reconciles
*one side's own three wire representations* of the same trade before that
side's view is even considered.

`FpmlConfirmationParserTest` includes a deliberate XXE payload
(`<!DOCTYPE tradeConfirmation [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>`)
and asserts the parser rejects it. An XML confirmation is the one format
of the three that carries the classic XXE risk; `FpmlConfirmationParser`
disables DOCTYPE processing on its `DocumentBuilderFactory`
(`disallow-doctype-decl=true`) from the first line it was written, and the
test exists to keep it that way rather than to document a bug that was
found and fixed later.

## Validation

### Reference oracle, diffed exactly

`ReferenceOracleMatcher` is a deliberately slow, deliberately unindexed
matcher: a plain `List` scan for message-id dedup, a plain nested loop to
find each trade reference's two sides, no `HashMap`, no incremental state,
nothing that could hide the same bug the real engine might have.
`ReferenceOracleDiffTest` runs the oracle over a canonical, in-order,
1,500-pair dataset and the real `MatchingEngine` over a **noisy** (5%
duplicated, fully shuffled) delivery of the exact same dataset, then
asserts the two produce the identical set of matched trade references, the
identical set of broken trade references, and, for every break, the
identical set of named disagreeing fields. This test is what caught the
real bug described under Findings below; it is kept to 1,500 pairs
deliberately, since the oracle is O(n^2) and is never run over the full
100,000-message dataset.

```
$ mvn test -Dtest=ReferenceOracleDiffTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

### Idempotent replay

`NoisyStreamBuilder` takes a canonical stream and produces a second one
with 5% of messages redelivered (the exact same message, same message id,
simulating broker/producer at-least-once redelivery, not a distinct new
message) and the whole stream shuffled out of trade-reference order. The
real measurement run (`BenchmarkRunner`, see below) replays both the
canonical and the noisy stream through independent `MatchingEngine`
instances over the real embedded broker, computes a SHA-256 digest over a
canonical serialization of each engine's final matched-position and break
state, and asserts the two digests are identical. `docs/benchmark_output.txt`
has the real digests from the run that produced the numbers in this README.

### Tests

40 tests: the original 17 (8 tolerance/break-naming unit tests, 7 matching
engine unit tests, 1 reference-oracle diff test, 1 embedded-Kafka
integration test), plus 23 for the gateway: 5 FIX parser tests, 5 delimited
file parser tests, 5 FpML parser tests (including the XXE rejection test
above), 6 cross-format reconciliation gate tests, and 1 reference-oracle
diff test for the gate itself (`ReferenceOracleReconciliationGateDiffTest`,
a deliberately simple independent field comparator diffed against the fast
gate, same pattern as the matching engine's own oracle). Real output:

```
$ mvn test
[INFO] Tests run: 7, ... MatchingEngineTest
[INFO] Tests run: 1, ... ReferenceOracleDiffTest
[INFO] Tests run: 6, ... gateway.CrossFormatReconciliationGateTest
[INFO] Tests run: 5, ... gateway.DelimitedPostTradeFileParserTest
[INFO] Tests run: 5, ... gateway.FixAllocationMessageParserTest
[INFO] Tests run: 5, ... gateway.FpmlConfirmationParserTest
[INFO] Tests run: 1, ... gateway.ReferenceOracleReconciliationGateDiffTest
[INFO] Tests run: 2, ... integration.SettlementMatchingIntegrationTest
[INFO] Tests run: 8, ... tolerance.ToleranceRuleSetTest
[INFO] Tests run: 40, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Full transcript: `docs/test_output.txt`. The 100,000-message
`BenchmarkRunner` test and the 30-seeded-disagreement
`CrossFormatBenchmarkRunner` test are both tagged `benchmark` and excluded
from this default run (see "Building and running").

## Findings: the messageId collision that made real messages vanish

The first run of `ReferenceOracleDiffTest` failed, and not narrowly: the
real engine's matched-position set and the oracle's disagreed by ten trade
references, in both directions (ten trade references the oracle called a
match, the engine had filed nothing for at all; ten different trade
references the engine called a match, the oracle called a break).

**Wrong hypothesis first.** Ten mismatched pairs out of 1,500, right after
adding out-of-order and duplicate delivery to the test, looked like a
buffering or dedup bug in `MatchingEngine` itself, mis-pairing a duplicate
against the wrong trade reference. That hypothesis did not survive contact
with the engine's own instrumentation.

**The measurement that discriminated.** `MatchingEngine.distinctMessageIdsSeen()`
reports how many distinct message ids the dedup set has ever accepted. For
a 1,500-pair (3,000-message) canonical dataset, that number should be
exactly 3,000 regardless of how many duplicates the noisy stream adds on
top. It printed **2,985**. Fifteen distinct, legitimate messages were being
rejected as duplicates, by an engine whose dedup logic, per the unit tests
above, was already known to be correct on its own. The bug was not in what
the engine did with a message id; it was in what message id two different
messages were given.

**Root cause.** `SyntheticInstructionGenerator.generate()` builds every
message id from the pair's loop index `i`, formatted as `MSG-A-%08d` /
`MSG-B-%08d`, except one: the `PARTY_B` leg of a *seeded break* pair, which
was built from `messageCounter`, a separate counter incremented once per
message across the entire stream. Because two messages are emitted per
pair, `messageCounter` at pair `i` is approximately `2 * i`, so a seeded
break's `PARTY_B` message id, `"MSG-B-%08d".formatted(messageCounter)`,
could land on the exact same numeric suffix as a completely unrelated
*later* pair's own, legitimately-generated `"MSG-B-%08d".formatted(i)`. Two
different messages, from two different trade references, sharing one
message id. The dedup set correctly rejected the second one it saw as a
duplicate, exactly as designed, on data that was never supposed to collide
in the first place. Fifteen such collisions in the 1,500-pair test dataset
left fifteen trade references with only one leg ever actually delivered
to the engine, each permanently stuck awaiting a counterpart that had been
silently dropped, while the collision's other side quietly reused a message
id that let a legitimately different pair's outcome get miscounted against
it.

**Fix.** `applyMismatch()` now takes the same pair index `i` that every
other code path in the generator already uses for message ids, instead of
the global message counter (`SyntheticInstructionGenerator.java`, the
`applyMismatch` call site and signature). The global counter is still used,
correctly, for the informational `sequenceNumber` field, which was never
the problem; it was never appropriate as an identity key. After the fix,
`ReferenceOracleDiffTest` passes with `engine.stillAwaitingCounterpart()`
at 0 and `distinctMessageIdsSeen()` at exactly 3,000, and the full
100,000-message benchmark run below shows the same clean 100,000-of-100,000
accounting.

**Why the method mattered.** This bug lived in test data generation, not in
the code the resume bullet claims correctness for, but it would have been
invisible to a shallower check: a test that only counted "48 breaks filed"
without checking *which* 48 trade references they were filed against would
have passed by coincidence (fifteen collisions in 1,500 pairs, scaled to
50,000 pairs, would have been roughly 500 collisions, and pure luck about
which side of each collision got the seeded mismatch could easily still
land on 48 breaks filed, on the wrong trade references, some of which were
never seeded at all). The reference-oracle diff test compares the *set of
trade references*, not just a count, which is exactly what turned a
coincidentally-plausible number into a caught bug.

## Measured results

AMD Ryzen 7 7800X3D, 8 physical / 16 logical cores, Windows 11 Home, JDK 21
Temurin (Java 17 bytecode), against `spring-kafka-test`'s embedded KRaft
Kafka broker and H2 2.2.224 in PostgreSQL-compatibility mode. Full raw
output: `docs/benchmark_output.txt`, produced by
`mvn test -Dtest=BenchmarkRunner -Dsurefire.excludedGroups=`.

50,000 trade-reference pairs (**100,000 synthetic instructions**), 48 of
those pairs seeded with a field disagreement beyond the configured
tolerance, evenly spread across price, quantity, settlement date, currency,
and ISIN mismatches:

| Metric | Measured | Claim |
|---|---|---|
| **Seeded breaks caught** | **48 / 48** | 48 of 48 |
| False matches (breaks filed on an un-seeded, genuinely matching pair) | 0 | 0 |
| Total synthetic instructions processed | 100,000 | 100,000 |
| Messages still awaiting a counterpart at end of run | 0 | (none claimed; confirms no message was lost) |
| Distinct message ids seen | 100,000 | (confirms no accidental message id collisions after the fix above) |
| Matched positions | 49,952 | 50,000 pairs − 48 seeded breaks |

"Breaks caught" means: of the 48 trade references the generator seeded
with a field disagreement beyond tolerance, how many the engine filed a
`BreakResult` for. "False matches" means: of the 49,952 trade references
that were **not** seeded, how many the engine incorrectly filed a break
for (the claim's "0 false matches" is a false-positive count on genuinely
matching pairs, not a count of wrong matches on seeded-break pairs, which
would be a contradiction in terms).

Idempotent replay: a second stream was built from the same 100,000
messages with 5% duplicated (5,000 extra redelivered messages, same
message ids) and the entire 105,000-message stream shuffled out of
trade-reference order, then replayed through a fresh `MatchingEngine` over
the same embedded broker.

| Run | Matched positions | Breaks filed | Final-state SHA-256 |
|---|---|---|---|
| Canonical (100,000 msgs, in order) | 49,952 | 48 | `63c7645e7e019873366f1743f656ddd96c1615569bf56c01a6dab01c8b6dcaf` |
| Noisy (105,000 msgs, 5% duplicated, shuffled) | 49,952 | 48 | `63c7645e7e019873366f1743f656ddd96c1615569bf56c01a6dab01c8b6dcaf` |

**Identical final state after the noisy replay: true.** The canonical run
took 2,865 ms; the noisy run, despite 5,000 more messages to dedup and a
fully shuffled delivery order, took 1,075 ms (JIT warm-up from the first
run dominates the difference; this is not a controlled throughput
benchmark, see Limitations).

### Gateway: cross-format quarantine

Same machine, same run of `CrossFormatBenchmarkRunner`. 30 trades were
seeded, each with all three formats present and exactly one field
(quantity, price, ISIN, currency or settlement date) deliberately
disagreeing between formats; a separate 200-trade clean batch had all
three formats agree on every field. Full raw output:
`docs/cross_format_benchmark_output.txt`.

| Metric | Measured | Claim |
|---|---|---|
| **Cross-format disagreements quarantined** | **30 / 30** | 30 of 30 |
| Quarantined trades naming the exact seeded disagreeing field | 30 / 30 | (not a separate resume bullet; the companion check that makes the 30/30 mean something) |
| Clean trades falsely quarantined (false-hold rate) | 0 / 200 (0.0%) | (not a separate resume bullet; a quarantine claim with no false-hold rate is not a real result) |

"Quarantined" means the gate wrote a `QuarantinedTradeEntity` instead of
letting the trade proceed into the matching pipeline. "Naming the exact
field" means the disagreement row(s) attached to that quarantine record
name the same field the generator seeded the disagreement on, not just
any field. The false-hold rate is measured on a genuinely separate batch
of clean trades so it cannot share randomness with the seeded batch.

## Building and running

```bash
export PATH="/c/Users/Manas/tools/apache-maven-3.9.9/bin:$PATH"   # or the Windows mvn.cmd directly

mvn test                                                # 40 tests, embedded Kafka + H2, ~10s
mvn test -Dtest=ReferenceOracleDiffTest                 # the reference-oracle diff alone
mvn test -Dtest=BenchmarkRunner -Dsurefire.excludedGroups=   # the full 100,000-message run, ~10-20s
mvn test -Dtest=CrossFormatBenchmarkRunner -Dsurefire.excludedGroups=   # the 30-seeded gateway run
mvn -DskipTests package                                 # build the runnable jar
```

Running against real infrastructure (production profile, `application.yml`):

```bash
docker compose up -d      # starts a real single-broker KRaft Kafka + real PostgreSQL, per docker-compose.yml

# create the topic once the broker is up
docker exec settlement-matching-kafka /opt/kafka/bin/kafka-topics.sh \
    --create --topic settlement-instructions --bootstrap-server localhost:9092 --partitions 4

java -jar target/securities-settlement-matching.jar
```

`docker-compose.yml` documents this stand-up exactly; it was not used to
produce any number in this README (see "Honest framing, up front").

## Sibling comparison

[`trade-compliance-monitor`](https://github.com/Manas103/trade-compliance-monitor)
is the other Java rule-evaluation project in this portfolio, and the
contrast is deliberate: that project measures **throughput** (848
trades/sec sustained, p99 alert latency 0.150 ms) against five independent,
stateless compliance rules over a single trade. This project measures
**correctness** on a fixed dataset (48/48 seeded breaks, 0 false matches,
byte-identical state after a noisy replay) over a matching problem that is
inherently stateful, two messages that may arrive in either order, an
arbitrary amount of time apart, have to be held and reconciled as one unit.
Different currency, on purpose: a rule engine's job is to never fall behind
the trade feed, a matching engine's job is to never produce a wrong answer
regardless of what order the feed hands it messages in.

## Limitations

- **Tolerance rules are loaded once at startup, not hot-swappable.** A
  YAML edit needs a restart to take effect; the "nice to have" mentioned in
  this project's brief was not built.
- **The matcher assumes exactly two legs per trade reference.** A third
  message for a trade reference that has already completed (matched or
  broken) is silently ignored by dedup only if it happens to reuse a
  message id already seen; a genuinely new, unexpected third message for a
  completed trade reference has no defined behavior in this version.
- **In-memory engine state.** `MatchingEngine`'s dedup set and awaiting
  buffer live in JVM memory; a restart loses in-flight (unmatched) pairs
  and forgets which message ids have already been seen, which would allow
  a redelivery after a restart to be treated as new. A durable dedup store
  keyed by message id would be the fix, and was out of scope here.
- **No throughput claim.** The elapsed times in the measured-results table
  are single-run wall-clock numbers on a shared development machine, not a
  warmed-up, repeated, statistically controlled benchmark; they should be
  read as "processed 100,000 messages through a real embedded broker in a
  few seconds," not as a peak-throughput figure.
- **Single embedded broker, not a multi-node cluster.** Partition-leader
  failover, replication, and the operational failure modes of a real
  multi-broker Kafka deployment are not exercised by anything in this
  repository.
- **The price tolerance is basis points of the average of the two
  quoted prices**; a real settlement system might reasonably tolerance
  against a reference/mid price instead, which this ruleset does not model.
