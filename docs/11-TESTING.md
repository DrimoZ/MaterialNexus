# 11 — Tests

## JUnit

Pure logic:

- material matching;
- confidence ordering;
- policy precedence;
- canonical resolution;
- recipe normalization;
- duplicate classification;
- change-plan determinism;
- codec validation.

## GameTest

Runtime behavior:

- tags exist after reload;
- recipe changes are applied correctly;
- a failed analysis shows diagnostics and generates nothing;
- the generated pack is injected in a fresh world;
- Apply then revert restores the original recipes/tags;
- analysis of an overridden recipe reads the pre-MNX JSON;
- permission is enforced server-side for command, item and packets;
- optional integrations load absent/present;
- server can start without client classes;
- generated recipes are usable;
- viewer hooks do not alter gameplay state.

## Regression principle

A fixed bug gets a regression test. Do not mock `Level` for behavior that genuinely requires Minecraft; use a GameTest.
