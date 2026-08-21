# ADR-0014: Request dispatcher architecture

Status: accepted

The LoL runtime uses `AbstractRequestDispatcher<R>` with `RequestQueue`,
`RequestWorker`, immutable `Request` and deduplicated `RequestTask`. The
concrete owners are `RiotRequestDispatcher`, `ComputeRequestDispatcher` and
`SyncRequestDispatcher`.

Compute has physical `PROFILE` and `CHAMPION` workers. Profile work is placed
on the less-loaded worker only while champion has no queued or running
statistics matrix, build, or full champion-data refresh. Those heavy champion
tasks reserve the `CHAMPION` worker: later profile work stays on `PROFILE`,
regardless of its current load. Already placed profile tasks are never moved;
once the heavy champion work completes, normal insert-time balancing resumes.

Sync is volatile by design. It owns no Redis backlog, pending set or retry map.
Missing API matches are background Sync work; OP.GG match loading is immediate
Sync work. A `RequestRun` exists only while a Sync batch has unfinished children.
