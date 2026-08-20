# ADR-0014: Request dispatcher architecture

Status: accepted

The LoL runtime uses `AbstractRequestDispatcher<R>` with `RequestQueue`,
`RequestWorker`, immutable `Request` and deduplicated `RequestTask`. The
concrete owners are `RiotRequestDispatcher`, `ComputeRequestDispatcher` and
`SyncRequestDispatcher`.

Sync is volatile by design. It owns no Redis backlog, pending set or retry map.
Missing API matches are background Sync work; OP.GG match loading is immediate
Sync work. A `RequestRun` exists only while a Sync batch has unfinished children.
