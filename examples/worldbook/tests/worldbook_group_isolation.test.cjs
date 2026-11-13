"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { installMockRuntime, loadWorldBookModules } = require("../tests-dist/support/mock_runtime.js");

function setup(opts) {
  const runtime = installMockRuntime(opts || {});
  return { runtime, ...loadWorldBookModules() };
}

// --- group card binding regression ---

test("entry in group bound to card_A is injected for card_A", async () => {
  const { service, main } = setup();
  const entry = await service.createWorldBookEntry({
    name: "group_scoped",
    content: "GROUP_CONTENT",
    keywords: "magic",
    always_active: true,
    inject_target: "system",
    inject_position: "append"
  });
  await service.createWorldBookGroup({
    name: "card_a_group",
    entry_ids: [entry.id],
    character_card_ids: ["card_1"]
  });

  const result = await main.systemPromptHook({
    event: "after_compose_system_prompt",
    eventName: "after_compose_system_prompt",
    eventPayload: { chatId: "chat_1", systemPrompt: "BASE", metadata: {} }
  });
  assert.ok(result, "hook must return a result for card_1");
  assert.match(String(result.systemPrompt || ""), /GROUP_CONTENT/,
    "group-bound entry should appear for card_1");
});

test("entry in group bound to card_A is NOT injected for card_B", async () => {
  const { runtime, service, main } = setup({ rootDirName: "group_isolation_card_b" });
  // Override getCallerCardId to simulate card_2 caller
  globalThis.getCallerCardId = () => "card_2";
  try {
    const entry = await service.createWorldBookEntry({
      name: "group_scoped_b",
      content: "CARD_A_ONLY",
      keywords: "magic",
      always_active: true,
      inject_target: "system",
      inject_position: "append"
    });
    await service.createWorldBookGroup({
      name: "card_a_only_group",
      entry_ids: [entry.id],
      character_card_ids: ["card_1"]
    });

    const result = await main.systemPromptHook({
      event: "after_compose_system_prompt",
      eventName: "after_compose_system_prompt",
      eventPayload: { chatId: "chat_1", systemPrompt: "BASE", metadata: {} }
    });
    assert.ok(!result || !String(result.systemPrompt || "").includes("CARD_A_ONLY"),
      "card_1-only group entry must not appear for card_2 caller");
  } finally {
    globalThis.getCallerCardId = () => "card_1";
  }
});

test("entry in group with no card binding remains global", async () => {
  const { service, main } = setup({ rootDirName: "group_no_binding" });
  const entry = await service.createWorldBookEntry({
    name: "unbound_group_entry",
    content: "GLOBAL_CONTENT",
    keywords: "magic",
    always_active: true,
    inject_target: "system",
    inject_position: "append"
  });
  await service.createWorldBookGroup({
    name: "unbound_group",
    entry_ids: [entry.id],
    character_card_ids: []  // no binding -> global
  });

  const result = await main.systemPromptHook({
    event: "after_compose_system_prompt",
    eventName: "after_compose_system_prompt",
    eventPayload: { chatId: "chat_1", systemPrompt: "BASE", metadata: {} }
  });
  assert.ok(result, "unbound group entry must still produce a result");
  assert.match(String(result.systemPrompt || ""), /GLOBAL_CONTENT/,
    "unbound group entry must remain globally active");
});

test("entry-level character_card_id takes priority over group binding", async () => {
  const { service, main } = setup({ rootDirName: "group_entry_priority" });
  // Entry is bound directly to card_1, but group is bound to card_2 only.
  // The entry's own binding should override the group: active for card_1.
  const entry = await service.createWorldBookEntry({
    name: "entry_priority",
    content: "ENTRY_PRIORITY_CONTENT",
    keywords: "magic",
    always_active: true,
    inject_target: "system",
    inject_position: "append",
    character_card_id: "card_1"
  });
  await service.createWorldBookGroup({
    name: "card_2_group",
    entry_ids: [entry.id],
    character_card_ids: ["card_2"]
  });

  // Should appear for card_1 (entry-level binding wins)
  const result = await main.systemPromptHook({
    event: "after_compose_system_prompt",
    eventName: "after_compose_system_prompt",
    eventPayload: { chatId: "chat_1", systemPrompt: "BASE", metadata: {} }
  });
  assert.ok(result, "entry-level card_1 binding must win over group card_2 binding");
  assert.match(String(result.systemPrompt || ""), /ENTRY_PRIORITY_CONTENT/);
});