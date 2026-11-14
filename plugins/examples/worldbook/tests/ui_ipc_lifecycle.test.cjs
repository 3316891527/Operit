"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { installMockRuntime, loadWorldBookModules } = require("../tests-dist/support/mock_runtime.js");
const { createNodeIpc } = require("../../../tools/toolpkg/node_storage_runtime.cjs");

function fixture(name) {
  return { name, content: `content ${name}`, keywords: "test" };
}

function coldMain() {
  const runtime = installMockRuntime();
  let metadataRegistrations = 0;
  ToolPkg.registerUiRoute = () => { metadataRegistrations++; };
  const modules = loadWorldBookModules();
  assert.equal(metadataRegistrations, 0, "the main runtime must not rerun metadata discovery");
  return { runtime, ...modules };
}

test("cold main serves all three UI lists without calling registerToolPkg", async () => {
  const { runtime, service, client } = coldMain();
  const entries = [];
  for (let i = 0; i < 6; i++) entries.push(await service.createWorldBookEntry(fixture(`existing-${i}`)));
  const group = await service.createWorldBookGroup({ name: "existing group", entry_ids: entries.map(x => x.id), character_card_ids: [] });
  const originalEntries = runtime.readPackageFile("entries.json");
  const originalGroups = runtime.readPackageFile("groups.json");
  const [listed, groups, cards] = await Promise.all([
    client.listWorldBookEntries(), client.listWorldBookGroups(), client.listWorldBookCharacterCards()
  ]);
  assert.deepEqual(listed.map(x => x.id).sort(), entries.map(x => x.id).sort());
  assert.equal(groups[0].id, group.id);
  assert.equal(cards[0].id, "card_1");
  assert.equal(runtime.readPackageFile("entries.json"), originalEntries);
  assert.equal(runtime.readPackageFile("groups.json"), originalGroups);
});

test("metadata discovery cannot substitute for registration in a separate main runtime", async () => {
  installMockRuntime();
  const discovery = loadWorldBookModules();
  const previousIcons = globalThis.Icons;
  globalThis.Icons = { Book: "book" };
  try {
    assert.equal(discovery.main.registerToolPkg(), true);
  } finally {
    globalThis.Icons = previousIcons;
  }
  const { service, client } = coldMain();
  const stored = await service.createWorldBookEntry(fixture("after discovery"));
  assert.equal((await client.listWorldBookEntries())[0].id, stored.id);
  assert.deepEqual(await client.listWorldBookGroups(), []);
  assert.equal((await client.listWorldBookCharacterCards())[0].id, "card_1");
});

test("a restarted main registry serves persisted data and warm calls share the storage queue", async () => {
  const first = coldMain();
  const stored = await first.service.createWorldBookEntry(fixture("persisted"));
  const original = first.runtime.readPackageFile("entries.json");
  ToolPkg.ipc = createNodeIpc();
  const restarted = loadWorldBookModules();
  assert.equal((await restarted.client.listWorldBookEntries())[0].id, stored.id);
  assert.equal(first.runtime.readPackageFile("entries.json"), original);
  await Promise.all([
    restarted.client.createWorldBookEntry(fixture("UI")),
    restarted.service.createWorldBookEntry(fixture("tool"))
  ]);
  assert.equal((await restarted.client.listWorldBookEntries()).length, 3);
  assert.equal((await restarted.client.listWorldBookEntries()).length, 3);
});