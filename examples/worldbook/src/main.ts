import worldbookManagerScreen from "./ui/worldbook_manager/index.ui.js";
import { registerWorldBookStorageService } from "./shared/worldbook_rpc.js";
import { readWorldBookEntries, readWorldBookGroups } from "./shared/worldbook_storage.js";
import {
  renderWorldBookContent,
  syncWorldBookVariableContext,
  type WorldBookVariableRenderContext
} from "./shared/worldbook_variables.js";

declare function getCallerCardId(): string | undefined;

const WORLDBOOK_ROUTE = "toolpkg:com.operit.worldbook:ui:worldbook_manager";

// Metadata discovery and live IPC use different engines. Bind handlers when the
// main module loads, before the host dispatches its first UI request. This only
// declares handlers; storage access stays in the serialized main-runtime service.
registerWorldBookStorageService();

interface WorldBookEntry {
  id: string;
  name: string;
  content: string;
  keywords?: string[];
  is_regex?: boolean;
  case_sensitive?: boolean;
  always_active?: boolean;
  enabled?: boolean;
  priority?: number;
  scan_depth?: number;
  inject_target?: "system" | "user" | "assistant";
  inject_position?: "prepend" | "append" | "at_depth";
  insertion_depth?: number;
  character_card_id?: string;
}

interface CharacterCardSummary {
  id?: string;
  name?: string;
}

function matchesEntry(entry: WorldBookEntry, text: string): boolean {
  if (!entry.keywords || entry.keywords.length === 0) {
    return false;
  }

  for (const keyword of entry.keywords) {
    if (!keyword) {
      continue;
    }
    try {
      if (entry.is_regex) {
        if (new RegExp(keyword, entry.case_sensitive ? "g" : "gi").test(text)) {
          return true;
        }
        continue;
      }
      if (entry.case_sensitive) {
        if (text.includes(keyword)) {
          return true;
        }
      } else if (text.toLowerCase().includes(keyword.toLowerCase())) {
        return true;
      }
    } catch (_error) {
      // Ignore malformed regex entries instead of breaking prompt assembly.
    }
  }

  return false;
}

function buildInjection(entries: WorldBookEntry[]): string {
  const parts = ["<worldbook>"];
  for (const entry of entries) {
    parts.push(`<entry name="${entry.name}">`);
    parts.push(entry.content);
    parts.push("</entry>");
  }
  parts.push("</worldbook>");
  return parts.join("\n");
}

function normalizeInjectPosition(entry: WorldBookEntry): "prepend" | "append" | "at_depth" {
  return entry.inject_position === "prepend" || entry.inject_position === "at_depth"
    ? entry.inject_position
    : "append";
}

function applyTextInjection(
  baseText: string,
  prependEntries: WorldBookEntry[],
  appendEntries: WorldBookEntry[]
): string {
  const parts: string[] = [];
  if (prependEntries.length > 0) {
    parts.push(buildInjection(prependEntries));
  }
  if (baseText) {
    parts.push(baseText);
  }
  if (appendEntries.length > 0) {
    parts.push(buildInjection(appendEntries));
  }
  return parts.join("\n");
}

function splitEntriesByPosition(entries: WorldBookEntry[]): {
  prependEntries: WorldBookEntry[];
  appendEntries: WorldBookEntry[];
} {
  const prependEntries: WorldBookEntry[] = [];
  const appendEntries: WorldBookEntry[] = [];

  for (const entry of entries) {
    if (normalizeInjectPosition(entry) === "prepend") {
      prependEntries.push(entry);
    } else {
      appendEntries.push(entry);
    }
  }

  return { prependEntries, appendEntries };
}

function toPromptTurnKind(entry: WorldBookEntry): ToolPkg.PromptTurnKind {
  if (entry.inject_target === "assistant") {
    return "ASSISTANT";
  }
  if (entry.inject_target === "user") {
    return "USER";
  }
  return "SYSTEM";
}

function insertAtDepthEntries(
  history: ToolPkg.PromptTurn[],
  entries: WorldBookEntry[]
): ToolPkg.PromptTurn[] {
  if (entries.length === 0) {
    return history;
  }

  const grouped = new Map<string, { depth: number; kind: ToolPkg.PromptTurnKind; entries: WorldBookEntry[] }>();
  for (const entry of entries) {
    const depth = Math.max(0, Number(entry.insertion_depth ?? 0) || 0);
    const kind = toPromptTurnKind(entry);
    const key = `${kind}:${depth}`;
    const group = grouped.get(key);
    if (group) {
      group.entries.push(entry);
      continue;
    }
    grouped.set(key, { depth, kind, entries: [entry] });
  }

  const nextHistory = [...history];
  const groups = [...grouped.values()].sort((left, right) => right.depth - left.depth);
  for (const group of groups) {
    const insertIndex = Math.max(0, nextHistory.length - group.depth);
    nextHistory.splice(insertIndex, 0, {
      kind: group.kind,
      content: buildInjection(group.entries)
    });
  }
  return nextHistory;
}

function matchesCharacterCard(entry: WorldBookEntry, callerCardId: string): boolean {
  const targetCardId = (entry.character_card_id || "").trim();
  if (!targetCardId) {
    return true;
  }
  return !!callerCardId && callerCardId === targetCardId;
}

// A group may bind multiple character card IDs. When an entry belongs to such a
// group, the group's card list acts as an additional scope: the entry is active
// if the caller's card is in ANY of the groups that contain it. If the entry has
// its own character_card_id it still takes precedence via matchesCharacterCard.
function buildGroupCardIndex(
  entries: WorldBookEntry[],
  groups: Array<{ entry_ids: string[]; character_card_ids: string[] }>
): Map<string, string[]> {
  // entryId -> Set of cardIds inherited from groups
  const index = new Map<string, Set<string>>();
  for (const group of groups) {
    const cardIds = (group.character_card_ids || []).map((id) => String(id || "").trim()).filter(Boolean);
    if (cardIds.length === 0) {
      continue; // No card binding on this group – does not restrict entries
    }
    for (const entryId of group.entry_ids || []) {
      const normalized = String(entryId || "").trim();
      if (!normalized) {
        continue;
      }
      let set = index.get(normalized);
      if (!set) {
        set = new Set();
        index.set(normalized, set);
      }
      for (const cardId of cardIds) {
        set.add(cardId);
      }
    }
  }
  const result = new Map<string, string[]>();
  for (const [entryId, set] of index) {
    result.set(entryId, [...set]);
  }
  return result;
}

function matchesCharacterCardWithGroups(
  entry: WorldBookEntry,
  callerCardId: string,
  groupCardIndex: Map<string, string[]>
): boolean {
  // Entry-level binding takes priority.
  const targetCardId = (entry.character_card_id || "").trim();
  if (targetCardId) {
    return !!callerCardId && callerCardId === targetCardId;
  }
  // If this entry belongs to one or more groups that have card bindings, the
  // entry is scoped to only those cards.
  const groupCards = groupCardIndex.get(entry.id);
  if (groupCards && groupCards.length > 0) {
    return !!callerCardId && groupCards.includes(callerCardId);
  }
  // No binding at all: global.
  return true;
}

async function resolveCurrentCharacterCardId(
  event: ToolPkg.SystemPromptComposeHookEvent | ToolPkg.PromptFinalizeHookEvent
): Promise<string> {
  try {
    const directCardId = typeof getCallerCardId === "function" ? getCallerCardId() : undefined;
    if (directCardId && String(directCardId).trim()) {
      return String(directCardId).trim();
    }
  } catch (_error) {
    // Ignore direct getter failure and fall back to chat lookup.
  }

  try {
    const chatId = event?.eventPayload?.chatId;
    if (!chatId) {
      return "";
    }

    const chatResult = await Tools.Chat.findChat({
      query: String(chatId),
      match: "exact",
      index: 0
    });
    const cardName = String(chatResult?.chat?.characterCardName || "").trim();
    if (!cardName) {
      return "";
    }

    const cardResult = await Tools.Chat.listCharacterCards();
    const cards = Array.isArray(cardResult?.cards)
      ? (cardResult.cards as CharacterCardSummary[])
      : [];
    const matchedCard = cards.find((card) => String(card?.name || "").trim() === cardName);
    return matchedCard?.id ? String(matchedCard.id).trim() : "";
  } catch (_error) {
    return "";
  }
}

interface EnabledEntriesResult {
  entries: WorldBookEntry[];
  groupCardIndex: Map<string, string[]>;
}

async function readEnabledEntries(): Promise<EnabledEntriesResult> {
  try {
    const [parsed, groups] = await Promise.all([
      readWorldBookEntries<WorldBookEntry>(),
      readWorldBookGroups<{ entry_ids: string[]; character_card_ids: string[] }>()
    ]);
    const enabledEntries = parsed.filter((entry) => entry && entry.enabled !== false) as WorldBookEntry[];
    enabledEntries.sort((left, right) => (right.priority || 50) - (left.priority || 50));
    const groupCardIndex = buildGroupCardIndex(enabledEntries, groups);
    return { entries: enabledEntries, groupCardIndex };
  } catch (_error) {
    return { entries: [], groupCardIndex: new Map() };
  }
}

function renderEntries(
  entries: WorldBookEntry[],
  variableContext: WorldBookVariableRenderContext | null
): WorldBookEntry[] {
  if (!variableContext) {
    return entries;
  }
  return entries.map((entry) => ({
    ...entry,
    content: renderWorldBookContent(String(entry.content || ""), variableContext)
  }));
}

async function resolveVariableContext(
  event: ToolPkg.SystemPromptComposeHookEvent | ToolPkg.PromptFinalizeHookEvent,
  callerCardId: string
): Promise<WorldBookVariableRenderContext | null> {
  const chatId = String(event?.eventPayload?.chatId || "").trim();
  if (!chatId) {
    return null;
  }

  try {
    const allEntries = await readWorldBookEntries<WorldBookEntry>();
    return await syncWorldBookVariableContext(chatId, allEntries, callerCardId);
  } catch (_error) {
    return null;
  }
}

export async function systemPromptHook(
  event: ToolPkg.SystemPromptComposeHookEvent
) {
  const stage = event.eventName || event.event;
  if (stage !== "after_compose_system_prompt") {
    return null;
  }

  const { entries: enabledEntries, groupCardIndex } = await readEnabledEntries();
  const callerCardId = await resolveCurrentCharacterCardId(event);
  const variableContext = await resolveVariableContext(event, callerCardId);
  const hitEntries = enabledEntries.filter(
    (entry) =>
      entry.always_active &&
      entry.inject_target === "system" &&
      normalizeInjectPosition(entry) !== "at_depth" &&
      matchesCharacterCardWithGroups(entry, callerCardId, groupCardIndex)
  );
  if (hitEntries.length === 0) {
    return null;
  }

  const currentPrompt = event.eventPayload?.systemPrompt || "";
  const renderedEntries = renderEntries(hitEntries, variableContext);
  const { prependEntries, appendEntries } = splitEntriesByPosition(renderedEntries);
  return { systemPrompt: applyTextInjection(currentPrompt, prependEntries, appendEntries) };
}

export async function finalizeHook(
  event: ToolPkg.PromptFinalizeHookEvent
) {
  const stage = event.eventName || event.event;
  if (stage !== "before_finalize_prompt") {
    return null;
  }

  const { entries: enabledEntries, groupCardIndex } = await readEnabledEntries();
  const promptEntries = enabledEntries.filter(
    (entry) => entry.inject_target === "user" || normalizeInjectPosition(entry) === "at_depth"
  );
  const keywordEntries = enabledEntries.filter((entry) => !entry.always_active);
  const payload = event.eventPayload || {};
  const history = (payload.preparedHistory || payload.chatHistory || []) as ToolPkg.PromptTurn[];
  const callerCardId = await resolveCurrentCharacterCardId(event);
  const variableContext = await resolveVariableContext(event, callerCardId);

  const hitSystemEntries: WorldBookEntry[] = [];
  const hitUserEntries: WorldBookEntry[] = [];
  const hitChatEntries: WorldBookEntry[] = [];

  for (const entry of promptEntries) {
    if (!matchesCharacterCardWithGroups(entry, callerCardId, groupCardIndex)) {
      continue;
    }
    if (entry.always_active) {
      if (normalizeInjectPosition(entry) === "at_depth" || entry.inject_target === "assistant") {
        hitChatEntries.push(entry);
      } else {
        hitUserEntries.push(entry);
      }
    }
  }

  for (const entry of keywordEntries) {
    if (!matchesCharacterCardWithGroups(entry, callerCardId, groupCardIndex)) {
      continue;
    }

    const texts: string[] = [];
    if (payload.rawInput) {
      texts.push(payload.rawInput);
    }
    if (payload.processedInput && payload.processedInput !== payload.rawInput) {
      texts.push(payload.processedInput);
    }

    const depth = entry.scan_depth != null ? entry.scan_depth : 0;
    if (depth > 0) {
      const userTurns = history.filter((turn) => turn && turn.kind === "USER" && turn.content);
      const startIndex = Math.max(0, userTurns.length - depth);
      for (let index = startIndex; index < userTurns.length; index += 1) {
        const turn = userTurns[index];
        if (turn?.content) {
          texts.push(turn.content);
        }
      }
    }

    const scanText = texts.join("\n");
    if (scanText && matchesEntry(entry, scanText)) {
      if (normalizeInjectPosition(entry) === "at_depth" || entry.inject_target === "assistant") {
        hitChatEntries.push(entry);
      } else if (entry.inject_target === "user") {
        hitUserEntries.push(entry);
      } else {
        hitSystemEntries.push(entry);
      }
    }
  }

  if (hitSystemEntries.length === 0 && hitUserEntries.length === 0 && hitChatEntries.length === 0) {
    return null;
  }

  let nextHistory = [...history];
  let nextProcessedInput = String(payload.processedInput || payload.rawInput || "");

  if (hitSystemEntries.length > 0) {
    const renderedSystemEntries = renderEntries(hitSystemEntries, variableContext);
    const { prependEntries, appendEntries } = splitEntriesByPosition(renderedSystemEntries);
    let injected = false;
    const sysNext: ToolPkg.PromptTurn[] = [];

    for (const turn of nextHistory) {
      if (!injected && turn.kind === "SYSTEM") {
        const nextContent = applyTextInjection(turn.content || "", prependEntries, appendEntries);
        sysNext.push({ ...turn, content: nextContent });
        injected = true;
        continue;
      }
      sysNext.push(turn);
    }

    if (!injected) {
      sysNext.unshift({
        kind: "SYSTEM",
        content: applyTextInjection("", prependEntries, appendEntries)
      });
    }
    nextHistory = sysNext;
  }

  if (hitUserEntries.length > 0) {
    const renderedUserEntries = renderEntries(hitUserEntries, variableContext);
    const { prependEntries, appendEntries } = splitEntriesByPosition(renderedUserEntries);
    nextProcessedInput = applyTextInjection(nextProcessedInput, prependEntries, appendEntries);
  }

  if (hitChatEntries.length > 0) {
    nextHistory = insertAtDepthEntries(nextHistory, renderEntries(hitChatEntries, variableContext));
  }

  return {
    preparedHistory: nextHistory,
    processedInput: nextProcessedInput
  };
}

export function registerToolPkg() {
  ToolPkg.registerUiRoute({
    id: "worldbook_manager",
    route: WORLDBOOK_ROUTE,
    runtime: "compose_dsl",
    screen: worldbookManagerScreen,
    params: {},
    title: {
      zh: "世界书管理",
      en: "World Book Manager"
    }
  });

  ToolPkg.registerNavigationEntry({
    id: "worldbook_manager_details",
    route: WORLDBOOK_ROUTE,
    surface: "package_details",
    title: {
      zh: "世界书管理",
      en: "World Book Manager"
    },
    icon: Icons.Book,
    order: 210
  });

  ToolPkg.registerSystemPromptComposeHook({
    id: "worldbook_always_active",
    function: systemPromptHook
  });

  ToolPkg.registerPromptFinalizeHook({
    id: "worldbook_keyword_inject",
    function: finalizeHook
  });

  return true;
}
