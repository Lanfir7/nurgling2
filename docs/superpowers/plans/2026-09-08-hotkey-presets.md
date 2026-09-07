# Hotkey Presets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add immutable built-in and editable user hotkey presets with staged application, global persistence, and clipboard share codes.

**Architecture:** Keep preset values, built-in catalog, JSON persistence, and share-code parsing independent of Haven UI. A preset draft model coordinates with the existing `HotkeyDraftModel`; one save coordinator commits runtime bindings and the preset library with rollback. `HotkeySettings` receives a focused preset-control widget while the existing resolver and action rows remain unchanged.

**Tech Stack:** Java 8 source compatibility, Haven widgets and clipboard API, `org.json`, JDK GZIP/Base64, JUnit 5, Ant.

**Spec:** `docs/superpowers/specs/2026-09-08-hotkey-presets-design.md`

## Global Constraints

- Presets are global for the client, not scoped to character or account.
- `Default` and future built-in presets are immutable and are never serialized as user presets.
- Selecting or editing a preset changes only drafts until the containing settings window is saved.
- Cancelling or failing a save leaves runtime bindings and persistent presets unchanged.
- Existing customized bindings must migrate into `Пользовательский 1` without changing any binding.
- Share codes use `NURGLING-HOTKEYS-1:<base64url(gzip(utf8-json))>` and contain no account or unrelated settings.
- Missing current actions use defaults; unknown newer action IDs are preserved but ignored.
- Persistence uses `NFileUtils.writeAtomically`; no network calls are added.
- Run all shell commands through `rtk` and preserve unrelated worktree changes.

---

### Task 1: Immutable preset values and built-in catalog

**Files:**
- Create: `src/nurgling/hotkeys/presets/HotkeyPreset.java`
- Create: `src/nurgling/hotkeys/presets/HotkeyPresetCatalog.java`
- Create: `test/nurgling/hotkeys/presets/HotkeyPresetCatalogTest.java`

**Interfaces:**
- Consumes: `HotkeyRegistry.snapshot()`, `HotkeyAction.defaultGesture()`, `InputGesture`.
- Produces: `HotkeyPreset`, `HotkeyPresetCatalog.DEFAULT_ID`, `HotkeyPresetCatalog.builtIns(HotkeyRegistry)`, and `HotkeyPresetCatalog.defaultPreset(HotkeyRegistry)`.

- [ ] **Step 1: Write failing catalog tests**

```java
@Test void defaultPresetContainsEveryRegisteredDefault() {
    HotkeyRegistry registry = new HotkeyRegistry();
    HotkeyCatalog.registerCore(registry);
    HotkeyPreset preset = HotkeyPresetCatalog.defaultPreset(registry);
    assertEquals(HotkeyPresetCatalog.DEFAULT_ID, preset.id());
    assertTrue(preset.builtIn());
    for(HotkeyAction action : registry.snapshot())
        assertEquals(action.defaultGesture(), preset.gesture(action.id()));
}

@Test void presetSnapshotCannotBeMutated() {
    Map<String, InputGesture> source = new HashMap<>();
    source.put("item.take", InputGesture.none());
    HotkeyPreset preset = new HotkeyPreset("user-1", "Mine", false, source);
    source.clear();
    assertEquals(InputGesture.none(), preset.gesture("item.take"));
    assertThrows(UnsupportedOperationException.class,
            () -> preset.gestures().put("x", InputGesture.none()));
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails because `HotkeyPreset` and `HotkeyPresetCatalog` do not exist.

- [ ] **Step 3: Implement the immutable value and catalog**

```java
public final class HotkeyPreset {
    private final String id;
    private final String name;
    private final boolean builtIn;
    private final Map<String, InputGesture> gestures;

    public HotkeyPreset(String id, String name, boolean builtIn,
                        Map<String, InputGesture> gestures) {
        if(id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty() || gestures == null)
            throw new IllegalArgumentException("invalid preset");
        this.id = id;
        this.name = name.trim();
        this.builtIn = builtIn;
        this.gestures = Collections.unmodifiableMap(new TreeMap<>(gestures));
    }
    public String id() { return id; }
    public String name() { return name; }
    public boolean builtIn() { return builtIn; }
    public Map<String, InputGesture> gestures() { return gestures; }
    public InputGesture gesture(String actionId) { return gestures.get(actionId); }
    public HotkeyPreset renamed(String value) { return new HotkeyPreset(id, value, builtIn, gestures); }
}
```

```java
public final class HotkeyPresetCatalog {
    public static final String DEFAULT_ID = "builtin.default";

    public static HotkeyPreset defaultPreset(HotkeyRegistry registry) {
        Map<String, InputGesture> values = new TreeMap<>();
        for(HotkeyAction action : registry.snapshot())
            values.put(action.id(), action.defaultGesture());
        return new HotkeyPreset(DEFAULT_ID, "Default", true, values);
    }

    public static List<HotkeyPreset> builtIns(HotkeyRegistry registry) {
        return Collections.singletonList(defaultPreset(registry));
    }
}
```

- [ ] **Step 4: Run the catalog tests and verify GREEN**

Run: `rtk ant test`

Expected: `HotkeyPresetCatalogTest` passes and the full suite has zero failures.

- [ ] **Step 5: Commit Task 1**

```powershell
rtk git add src/nurgling/hotkeys/presets/HotkeyPreset.java src/nurgling/hotkeys/presets/HotkeyPresetCatalog.java test/nurgling/hotkeys/presets/HotkeyPresetCatalogTest.java
rtk git commit -m "feat(hotkeys): model built-in presets"
```

---

### Task 2: Versioned clipboard share-code codec

**Files:**
- Create: `src/nurgling/hotkeys/presets/HotkeyPresetCodec.java`
- Create: `test/nurgling/hotkeys/presets/HotkeyPresetCodecTest.java`

**Interfaces:**
- Consumes: `HotkeyPreset`, `InputGesture.encode()`, `InputGesture.decode(String)`.
- Produces: `String HotkeyPresetCodec.encode(HotkeyPreset)` and `HotkeyPreset HotkeyPresetCodec.decode(String)`.

- [ ] **Step 1: Write failing round-trip and validation tests**

```java
@Test void shareCodeRoundTripsEveryGestureFamilyAndUnknownIds() {
    Map<String, InputGesture> values = new LinkedHashMap<>();
    values.put("disabled", InputGesture.none());
    values.put("key", InputGesture.key(KeyMatch.forcode(KeyEvent.VK_K, KeyMatch.C)));
    values.put("mouse", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.C | KeyMatch.S));
    values.put("wheel", InputGesture.wheel(-1, KeyMatch.MODS, KeyMatch.S));
    values.put("modifier", InputGesture.modifier(KeyMatch.M));
    values.put("future.unknown", InputGesture.mouse(4, KeyMatch.MODS, 0));
    HotkeyPreset source = new HotkeyPreset("user-1", "Raid", false, values);
    String code = HotkeyPresetCodec.encode(source);
    assertTrue(code.startsWith("NURGLING-HOTKEYS-1:"));
    HotkeyPreset decoded = HotkeyPresetCodec.decode(code);
    assertEquals("Raid", decoded.name());
    assertFalse(decoded.builtIn());
    assertEquals(values, decoded.gestures());
}

@Test void codecIsDeterministicAndRejectsUnsafePayloads() {
    assertEquals(HotkeyPresetCodec.encode(presetInOrder("b", "a")),
            HotkeyPresetCodec.encode(presetInOrder("a", "b")));
    assertThrows(IllegalArgumentException.class, () -> HotkeyPresetCodec.decode("bad"));
    assertThrows(IllegalArgumentException.class, () -> HotkeyPresetCodec.decode(duplicateBindingCode()));
    assertThrows(IllegalArgumentException.class, () -> HotkeyPresetCodec.decode(oversizedCode()));
    assertThrows(IllegalArgumentException.class,
            () -> HotkeyPresetCodec.encode(new HotkeyPreset("builtin.default", "Default", true,
                    Collections.emptyMap())));
}
```

Define the test helpers in the same test class so the payload shape and size limits are explicit:

```java
private static HotkeyPreset presetInOrder(String first, String second) {
    Map<String, InputGesture> values = new LinkedHashMap<>();
    values.put(first, InputGesture.none());
    values.put(second, InputGesture.modifier(KeyMatch.S));
    return new HotkeyPreset("user-order", "Order", false, values);
}

private static String codeForJson(String json) {
    return HotkeyPresetCodec.PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(gzipForTest(json.getBytes(StandardCharsets.UTF_8)));
}

private static String duplicateBindingCode() {
    return codeForJson("{\"version\":1,\"name\":\"Duplicate\",\"bindings\":[" +
            "{\"id\":\"item.take\",\"gesture\":\"n\"}," +
            "{\"id\":\"item.take\",\"gesture\":\"n\"}]}");
}

private static String oversizedCode() {
    char[] chars = new char[HotkeyPresetCodec.MAX_JSON_BYTES + 1];
    Arrays.fill(chars, 'x');
    return codeForJson(new String(chars));
}
```

`gzipForTest` is a 6-line `GZIPOutputStream` helper local to the test; it writes the supplied bytes into a `ByteArrayOutputStream`, closes the stream, and returns `toByteArray()`.

- [ ] **Step 2: Run the codec tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails because `HotkeyPresetCodec` does not exist.

- [ ] **Step 3: Implement deterministic JSON, GZIP, Base64URL, and limits**

Use a sorted `bindings` JSON array so duplicate IDs can be detected instead of silently overwritten by `JSONObject`.

```java
public final class HotkeyPresetCodec {
    public static final String PREFIX = "NURGLING-HOTKEYS-1:";
    static final int MAX_CODE_CHARS = 64 * 1024;
    static final int MAX_JSON_BYTES = 256 * 1024;
    static final int MAX_NAME_CHARS = 64;
    static final int MAX_BINDINGS = 1024;

    public static String encode(HotkeyPreset preset) {
        if(preset == null || preset.builtIn()) throw new IllegalArgumentException("user preset required");
        byte[] json = toJson(preset).toString().getBytes(StandardCharsets.UTF_8);
        if(json.length > MAX_JSON_BYTES) throw new IllegalArgumentException("preset is too large");
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(gzip(json));
    }

    public static HotkeyPreset decode(String code) {
        if(code == null || code.length() > MAX_CODE_CHARS || !code.startsWith(PREFIX))
            throw new IllegalArgumentException("invalid preset code");
        byte[] json = gunzipLimited(Base64.getUrlDecoder().decode(code.substring(PREFIX.length())), MAX_JSON_BYTES);
        JSONObject root = new JSONObject(new String(json, StandardCharsets.UTF_8));
        if(root.getInt("version") != 1) throw new IllegalArgumentException("unsupported preset version");
        String name = requireName(root.getString("name"));
        JSONArray bindings = root.getJSONArray("bindings");
        if(bindings.length() > MAX_BINDINGS) throw new IllegalArgumentException("too many bindings");
        Set<String> seen = new HashSet<>();
        Map<String, InputGesture> values = new TreeMap<>();
        for(int i = 0; i < bindings.length(); i++) {
            JSONObject binding = bindings.getJSONObject(i);
            String id = binding.getString("id");
            if(id.isEmpty() || !seen.add(id)) throw new IllegalArgumentException("duplicate binding");
            values.put(id, InputGesture.decode(binding.getString("gesture")));
        }
        return new HotkeyPreset(UUID.randomUUID().toString(), name, false, values);
    }
}
```

`gunzipLimited` must stop and throw before writing byte `MAX_JSON_BYTES + 1`; `toJson` must emit bindings in `preset.gestures()` sorted order.

- [ ] **Step 4: Run codec tests and verify GREEN**

Run: `rtk ant test`

Expected: codec tests and the full suite pass with zero failures.

- [ ] **Step 5: Commit Task 2**

```powershell
rtk git add src/nurgling/hotkeys/presets/HotkeyPresetCodec.java test/nurgling/hotkeys/presets/HotkeyPresetCodecTest.java
rtk git commit -m "feat(hotkeys): encode shareable presets"
```

---

### Task 3: Global atomic preset persistence

**Files:**
- Create: `src/nurgling/hotkeys/presets/HotkeyPresetLibrary.java`
- Create: `src/nurgling/hotkeys/presets/HotkeyPresetRepository.java`
- Create: `src/nurgling/hotkeys/presets/HotkeyPresetStore.java`
- Create: `test/nurgling/hotkeys/presets/HotkeyPresetStoreTest.java`

**Interfaces:**
- Consumes: `NConfig.getGlobalInstance().getProfileAwarePath(String)`, `NFileUtils.writeAtomically(String, String)`, `HotkeyPreset`.
- Produces: immutable `HotkeyPresetLibrary`, repository `load()`/`save()`, and `HotkeyPresetStore.global()`.

- [ ] **Step 1: Write failing persistence tests using `@TempDir`**

```java
@Test void storeRoundTripsUserPresetsAndSelection(@TempDir Path dir) throws Exception {
    HotkeyPresetStore store = new HotkeyPresetStore(dir.resolve("hotkey-presets.json"));
    HotkeyPreset user = userPreset("user-1", "Mine", "item.take", InputGesture.none());
    HotkeyPresetLibrary expected = new HotkeyPresetLibrary("user-1", Collections.singletonList(user));
    store.save(expected);
    HotkeyPresetStore.LoadResult loaded = store.load();
    assertFalse(loaded.migrationRequired());
    assertNull(loaded.warningKey());
    assertEquals(expected, loaded.library());
    assertTrue(Files.exists(dir.resolve("hotkey-presets.json")));
}

@Test void missingAndCorruptFilesRequestMigrationWithoutOverwriting(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("hotkey-presets.json");
    HotkeyPresetStore store = new HotkeyPresetStore(file);
    assertTrue(store.load().migrationRequired());
    Files.write(file, "not-json".getBytes(StandardCharsets.UTF_8));
    HotkeyPresetStore.LoadResult corrupt = store.load();
    assertTrue(corrupt.migrationRequired());
    assertEquals("hotkeys.presets.warning.corrupt", corrupt.warningKey());
    assertEquals("not-json", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
}

@Test void builtInsCannotBePersisted(@TempDir Path dir) {
    HotkeyPresetLibrary invalid = new HotkeyPresetLibrary("builtin.default",
            Collections.singletonList(builtInPreset()));
    assertThrows(IllegalArgumentException.class,
            () -> new HotkeyPresetStore(dir.resolve("x.json")).save(invalid));
}

@Test void checkpointRestoresBothMissingFileAndOriginalBytes(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("hotkey-presets.json");
    HotkeyPresetStore store = new HotkeyPresetStore(file);
    HotkeyPresetRepository.Checkpoint missing = store.checkpoint();
    store.save(new HotkeyPresetLibrary("user-1",
            Collections.singletonList(userPreset("user-1", "Mine", "item.take", InputGesture.none()))));
    store.restore(missing);
    assertFalse(Files.exists(file));

    Files.write(file, "original-corrupt-bytes".getBytes(StandardCharsets.UTF_8));
    HotkeyPresetRepository.Checkpoint corrupt = store.checkpoint();
    Files.write(file, "replacement".getBytes(StandardCharsets.UTF_8));
    store.restore(corrupt);
    assertArrayEquals("original-corrupt-bytes".getBytes(StandardCharsets.UTF_8),
            Files.readAllBytes(file));
}
```

- [ ] **Step 2: Run store tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails for the missing library, repository, and store types.

- [ ] **Step 3: Implement immutable library and repository contract**

```java
public interface HotkeyPresetRepository {
    HotkeyPresetStore.LoadResult load();
    void save(HotkeyPresetLibrary library) throws IOException;
    Checkpoint checkpoint() throws IOException;
    void restore(Checkpoint checkpoint) throws IOException;

    interface Checkpoint {}
}

public final class HotkeyPresetLibrary {
    private final String selectedPresetId;
    private final List<HotkeyPreset> userPresets;
    public HotkeyPresetLibrary(String selectedPresetId, Collection<HotkeyPreset> presets) {
        this.selectedPresetId = selectedPresetId;
        List<HotkeyPreset> copy = new ArrayList<>();
        for(HotkeyPreset preset : presets) {
            if(preset.builtIn()) throw new IllegalArgumentException("built-in preset cannot be persisted");
            copy.add(preset);
        }
        this.userPresets = Collections.unmodifiableList(copy);
    }
    public String selectedPresetId() { return selectedPresetId; }
    public List<HotkeyPreset> userPresets() { return userPresets; }
    @Override public boolean equals(Object other) {
        if(this == other) return true;
        if(!(other instanceof HotkeyPresetLibrary)) return false;
        HotkeyPresetLibrary that = (HotkeyPresetLibrary) other;
        return Objects.equals(selectedPresetId, that.selectedPresetId) &&
                userPresets.equals(that.userPresets);
    }
    @Override public int hashCode() { return Objects.hash(selectedPresetId, userPresets); }
}
```

Give `HotkeyPreset` the corresponding value-based `equals`/`hashCode`, comparing ID, name, built-in flag, and gesture map.

- [ ] **Step 4: Implement version-1 JSON load/save and global path**

```java
public final class HotkeyPresetStore implements HotkeyPresetRepository {
    public static final String FILE = "hotkey-presets.json";
    private final Path file;

    public HotkeyPresetStore(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public static HotkeyPresetStore global() {
        return new HotkeyPresetStore(Paths.get(
                NConfig.getGlobalInstance().getProfileAwarePath(FILE)));
    }

    public LoadResult load() {
        if(!Files.isRegularFile(file)) return LoadResult.migrationRequired(null);
        try {
            return LoadResult.loaded(parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)));
        } catch(Exception failure) {
            return LoadResult.migrationRequired("hotkeys.presets.warning.corrupt");
        }
    }

    public void save(HotkeyPresetLibrary library) throws IOException {
        NFileUtils.writeAtomically(file.toString(), encode(library).toString(2));
    }

    public HotkeyPresetRepository.Checkpoint checkpoint() throws IOException {
        return new FileCheckpoint(Files.isRegularFile(file),
                Files.isRegularFile(file) ? Files.readAllBytes(file) : null);
    }

    public void restore(HotkeyPresetRepository.Checkpoint value) throws IOException {
        FileCheckpoint saved = (FileCheckpoint)value;
        if(saved.existed) NFileUtils.writeAtomically(file.toString(), saved.bytes);
        else Files.deleteIfExists(file);
    }
}
```

`FileCheckpoint` is a private immutable pair of `boolean existed` and a defensive copy of `byte[] bytes`. This preserves a missing or corrupt file byte-for-byte if the logical save transaction must roll back.

Store JSON as follows, with presets and bindings sorted by ID before encoding:

```json
{
  "version": 1,
  "selectedPresetId": "user-1",
  "presets": [
    {"id":"user-1","name":"Mine","bindings":[{"id":"item.take","gesture":"n"}]}
  ]
}
```

Decode each gesture with `InputGesture.decode()`. Reject duplicate preset IDs, duplicate binding IDs, unsupported schema versions, built-in IDs inside `presets`, names longer than 64 characters, and more than 1024 bindings per preset. Validate the whole document into temporary collections before constructing the immutable library.

- [ ] **Step 5: Run store tests and verify GREEN**

Run: `rtk ant test`

Expected: store tests and the full suite pass with zero failures.

- [ ] **Step 6: Commit Task 3**

```powershell
rtk git add src/nurgling/hotkeys/presets/HotkeyPresetLibrary.java src/nurgling/hotkeys/presets/HotkeyPresetRepository.java src/nurgling/hotkeys/presets/HotkeyPresetStore.java test/nurgling/hotkeys/presets/HotkeyPresetStoreTest.java
rtk git commit -m "feat(hotkeys): persist preset library"
```

---

### Task 4: Preset draft, migration, selection, and automatic forking

**Files:**
- Modify: `src/nurgling/hotkeys/HotkeyDraftModel.java`
- Create: `src/nurgling/hotkeys/presets/HotkeyPresetDraftModel.java`
- Create: `test/nurgling/hotkeys/presets/HotkeyPresetDraftModelTest.java`
- Modify: `test/nurgling/hotkeys/HotkeyDraftModelTest.java`

**Interfaces:**
- Consumes: `HotkeyPresetCatalog`, `HotkeyPresetStore.LoadResult`, `HotkeyRegistry`, `HotkeyDraftModel`.
- Produces: effective binding snapshots, whole-preset staging, migration, CRUD, unique names, checkpoints, and dirty-state tracking.

- [ ] **Step 1: Write failing snapshot/staging tests for `HotkeyDraftModel`**

```java
@Test void stageSnapshotUsesDefaultsForMissingActionsAndRejectsWrongFamilies() {
    HotkeyDraftModel draft = modelWithMouseAndWheelActions();
    Map<String, InputGesture> values = new HashMap<>();
    values.put("mouse", InputGesture.mouse(3, KeyMatch.MODS, KeyMatch.S));
    draft.stageSnapshot(values);
    assertEquals(values.get("mouse"), draft.effective("mouse"));
    assertEquals(defaultWheel(), draft.effective("wheel"));
    values.put("mouse", InputGesture.wheel(1, KeyMatch.MODS, 0));
    assertThrows(IllegalArgumentException.class, () -> draft.stageSnapshot(values));
}
```

- [ ] **Step 2: Write failing preset-draft behavior tests**

```java
@Test void existingCustomBindingsMigrateWithoutChangingThem() {
    HotkeyRegistry registry = registryWithCurrentBindingDifferentFromDefault();
    HotkeyPresetDraftModel presets = HotkeyPresetDraftModel.open(registry,
            HotkeyPresetCatalog.builtIns(registry), LoadResult.migrationRequired(null));
    assertEquals("Пользовательский 1", presets.selected().name());
    assertEquals(currentSnapshot(registry), presets.selected().gestures());
}

@Test void editingBuiltInForksOnceAndFurtherEditsUpdateSameUserPreset() {
    Fixture f = defaultFixture();
    f.presets.onBindingsEdited(snapshot("item.take", disabled()));
    String forkId = f.presets.selected().id();
    assertFalse(f.presets.selected().builtIn());
    assertEquals("Пользовательский 1", f.presets.selected().name());
    f.presets.onBindingsEdited(snapshot("item.take", rightClick()));
    assertEquals(forkId, f.presets.selected().id());
    assertEquals(rightClick(), f.presets.selected().gesture("item.take"));
}

@Test void selectCreateImportDeleteAndCancelAreFullyStaged() {
    Fixture f = fixtureWithSavedUserPreset();
    HotkeyPresetDraftModel.Checkpoint before = f.presets.checkpoint();
    f.presets.create("Mine", currentSnapshot(f.registry));
    f.presets.importPreset(importedNamedMine());
    assertEquals("Mine (2)", f.presets.selected().name());
    f.presets.deleteSelected();
    assertEquals(HotkeyPresetCatalog.DEFAULT_ID, f.presets.selected().id());
    f.presets.restore(before);
    assertEquals(before.selectedPresetId(), f.presets.selected().id());
    assertFalse(f.presets.isDirty());
}
```

- [ ] **Step 3: Run draft tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails for `stageSnapshot`, `effectiveSnapshot`, and `HotkeyPresetDraftModel`.

- [ ] **Step 4: Extend `HotkeyDraftModel` without changing existing save semantics**

```java
public Map<String, InputGesture> effectiveSnapshot() {
    Map<String, InputGesture> values = new TreeMap<>();
    for(HotkeyAction action : registry.snapshot()) values.put(action.id(), effective(action.id()));
    return Collections.unmodifiableMap(values);
}

public void stageSnapshot(Map<String, InputGesture> values) {
    Map<String, Change> replacement = new LinkedHashMap<>();
    for(HotkeyAction action : registry.snapshot()) {
        InputGesture value = values.get(action.id());
        if(value == null) value = action.defaultGesture();
        if(!action.allows(value.type())) throw new IllegalArgumentException("gesture type is not allowed for " + action.id());
        replacement.put(action.id(), value.equals(action.defaultGesture())
                ? new Change(ChangeKind.RESET, null) : new Change(ChangeKind.SET, value));
    }
    changes.clear();
    changes.putAll(replacement);
}
```

Build the replacement map before touching `changes`, so validation failure leaves the previous draft intact.

- [ ] **Step 5: Implement preset draft and migration**

```java
public final class HotkeyPresetDraftModel {
    public static HotkeyPresetDraftModel open(HotkeyRegistry registry,
            List<HotkeyPreset> builtIns, HotkeyPresetStore.LoadResult loaded);
    public List<HotkeyPreset> presets();
    public HotkeyPreset selected();
    public Map<String, InputGesture> select(String id);
    public HotkeyPreset create(String requestedName, Map<String, InputGesture> bindings);
    public HotkeyPreset importPreset(HotkeyPreset decoded);
    public Map<String, InputGesture> deleteSelected();
    public void onBindingsEdited(Map<String, InputGesture> effectiveBindings);
    public HotkeyPresetLibrary persistentState();
    public Checkpoint checkpoint();
    public void restore(Checkpoint checkpoint);
    public void restoreSavedState();
    public void markSaved();
    public boolean isDirty();
}
```

`open` compares the registry's current snapshot with the default built-in when migration is required. `uniqueName` trims input, rejects empty or longer-than-64 names, and appends ` (2)`, ` (3)`, and so on across built-in and user display names. `onBindingsEdited` creates exactly one UUID-backed `Пользовательский N` when selection is built-in, then replaces that selected user preset on later edits.

- [ ] **Step 6: Run draft tests and verify GREEN**

Run: `rtk ant test`

Expected: draft tests and the full suite pass with zero failures.

- [ ] **Step 7: Commit Task 4**

```powershell
rtk git add src/nurgling/hotkeys/HotkeyDraftModel.java src/nurgling/hotkeys/presets/HotkeyPresetDraftModel.java test/nurgling/hotkeys/HotkeyDraftModelTest.java test/nurgling/hotkeys/presets/HotkeyPresetDraftModelTest.java
rtk git commit -m "feat(hotkeys): stage preset changes"
```

---

### Task 5: Transactional save coordinator and rollback

**Files:**
- Create: `src/nurgling/hotkeys/presets/HotkeyPresetSaveCoordinator.java`
- Create: `test/nurgling/hotkeys/presets/HotkeyPresetSaveCoordinatorTest.java`

**Interfaces:**
- Consumes: `HotkeyDraftModel`, `HotkeyPresetDraftModel`, `HotkeyPresetRepository`, `HotkeyRegistry`.
- Produces: `void save()` and `void cancel()` with logical transaction and rollback.

- [ ] **Step 1: Write failing commit and rollback tests with a fake repository**

```java
@Test void successfulSaveCommitsBindingsAndSelectedUserPreset() {
    Fixture f = fixture();
    f.hotkeys.assign("item.take", disabled());
    f.presets.onBindingsEdited(f.hotkeys.effectiveSnapshot());
    f.coordinator.save();
    assertEquals(disabled(), f.registry.find("item.take").current());
    assertEquals(f.presets.selected().id(), f.repository.saved.selectedPresetId());
    assertFalse(f.hotkeys.isDirty());
    assertFalse(f.presets.isDirty());
}

@Test void repositoryFailureRestoresBindingsLibraryAndDrafts() {
    Fixture f = fixture();
    InputGesture original = f.registry.find("item.take").current();
    f.hotkeys.assign("item.take", disabled());
    f.presets.onBindingsEdited(f.hotkeys.effectiveSnapshot());
    f.repository.failure = new IOException("disk full");
    assertThrows(RuntimeException.class, f.coordinator::save);
    assertEquals(original, f.registry.find("item.take").current());
    assertTrue(f.hotkeys.isDirty());
    assertTrue(f.presets.isDirty());
    assertEquals(f.repository.original, f.repository.saved);
}

@Test void conflictsPreventEveryWrite() {
    Fixture f = conflictingFixture();
    assertThrows(IllegalStateException.class, f.coordinator::save);
    assertEquals(0, f.repository.saveCalls);
    assertEquals(f.originalRuntime, runtimeSnapshot(f.registry));
}
```

- [ ] **Step 2: Run coordinator tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails because `HotkeyPresetSaveCoordinator` does not exist.

- [ ] **Step 3: Implement save, rollback, and cancel**

```java
public void save() {
    if(!hotkeys.conflicts().isEmpty())
        throw new IllegalStateException("hotkey conflicts must be resolved before save");
    Map<String, InputGesture> runtimeBefore = runtimeSnapshot(registry);
    HotkeyDraftModel.Checkpoint hotkeyBefore = hotkeys.checkpoint();
    HotkeyPresetDraftModel.Checkpoint presetsBefore = presets.checkpoint();
    HotkeyPresetRepository.Checkpoint storeBefore;
    try {
        storeBefore = repository.checkpoint();
    } catch(IOException failure) {
        throw new RuntimeException("unable to snapshot hotkey presets", failure);
    }
    try {
        hotkeys.save();
        repository.save(presets.persistentState());
        presets.markSaved();
    } catch(Exception failure) {
        RuntimeException result = new RuntimeException("unable to save hotkey preset", failure);
        restoreRuntime(runtimeBefore, result);
        restoreStore(storeBefore, result);
        hotkeys.restore(hotkeyBefore);
        presets.restore(presetsBefore);
        throw result;
    }
}

public void cancel() {
    hotkeys.cancel();
    presets.restoreSavedState();
}
```

`restoreRuntime` calls `binding().set(original)` for every action and attaches rollback failures as suppressed exceptions. `restoreStore` calls `repository.restore(storeBefore)` and also attaches rollback failures as suppressed exceptions. The fake repository implements `Checkpoint` as a copy of its saved library, failure flag, and save-call count. Do not clear either draft until both runtime and file commits succeed.

- [ ] **Step 4: Run coordinator tests and verify GREEN**

Run: `rtk ant test`

Expected: coordinator tests and the full suite pass with zero failures.

- [ ] **Step 5: Commit Task 5**

```powershell
rtk git add src/nurgling/hotkeys/presets/HotkeyPresetSaveCoordinator.java test/nurgling/hotkeys/presets/HotkeyPresetSaveCoordinatorTest.java
rtk git commit -m "feat(hotkeys): save presets transactionally"
```

---

### Task 6: Preset controls in the hotkey settings page

**Files:**
- Create: `src/nurgling/widgets/nsettings/HotkeyPresetControls.java`
- Create: `src/nurgling/widgets/nsettings/HotkeyPresetPrompt.java`
- Modify: `src/nurgling/widgets/nsettings/HotkeySettingsModel.java`
- Modify: `src/nurgling/widgets/nsettings/HotkeySettings.java`
- Modify: `src/nurgling/widgets/nsettings/HotkeySettingsLayout.java`
- Create: `test/nurgling/widgets/nsettings/HotkeyPresetControlsTest.java`
- Modify: `test/nurgling/widgets/nsettings/HotkeySettingsModelTest.java`
- Modify: `test/nurgling/widgets/nsettings/HotkeySettingsLifecycleTest.java`

**Interfaces:**
- Consumes: preset draft/coordinator/codec/store, Haven `Dropbox`, `Clipboard`, `Window`, `TextEntry`, and existing hotkey-row callbacks.
- Produces: responsive preset row, staged selection, name prompt, clipboard import/export, delete enablement, and unsaved-switch confirmation.

- [ ] **Step 1: Write failing model integration tests**

```java
@Test void assigningGestureForksBuiltInAndSaveUpdatesSameUserPreset() {
    HotkeySettingsModel model = presetModelOnDefault();
    model.assign("item.take", disabled());
    String userId = model.presets().selected().id();
    assertFalse(model.presets().selected().builtIn());
    model.save();
    model.assign("item.take", rightClick());
    model.save();
    assertEquals(userId, model.presets().selected().id());
    assertEquals(rightClick(), model.presets().selected().gesture("item.take"));
}

@Test void selectingPresetStagesRowsUntilSaveAndCancelRestoresView() {
    HotkeySettingsModel model = modelWithDefaultAndUser();
    InputGesture runtimeBefore = model.registry().find("item.take").current();
    model.selectPreset("user-1");
    assertEquals(disabled(), model.draft().effective("item.take"));
    assertEquals(runtimeBefore, model.registry().find("item.take").current());
    model.cancel();
    assertEquals(model.savedPresetId(), model.presets().selected().id());
}
```

- [ ] **Step 2: Write failing control/layout/lifecycle tests**

```java
@Test void controlsShowSelectionAndRestrictBuiltInButtons() {
    HotkeyPresetControls controls = controlsOnDefault();
    assertEquals("Default", controls.selectedName());
    assertFalse(controls.copyButton().enabled);
    assertFalse(controls.deleteButton().enabled);
    controls.select("user-1");
    assertTrue(controls.copyButton().enabled);
    assertTrue(controls.deleteButton().enabled);
}

@Test void presetRowLeavesSearchTabsAndRowsNonOverlappingAtLargeFonts() {
    HotkeySettingsLayout layout = HotkeySettingsLayout.calculate(560, 530,
            28, 32, 40, 30, 44);
    assertTrue(layout.presets.y + layout.presets.h <= layout.search.y);
    assertTrue(layout.search.y + layout.search.h <= layout.tabs.y);
    assertTrue(layout.filter.y + layout.filter.h <= layout.rows.y);
}

@Test void destroyingPageClosesPresetPromptsAndClipboardCallbacks() {
    HotkeySettings page = pageWithPresetControls();
    page.controls().openCreatePrompt();
    page.disposeLifecycle();
    assertFalse(page.controls().hasOpenPrompt());
    assertFalse(page.controls().acceptsClipboardResult());
}
```

- [ ] **Step 3: Run UI/model tests and verify RED**

Run: `rtk ant test`

Expected: compilation fails for the new controls, prompt, layout rectangle, and model methods.

- [ ] **Step 4: Integrate presets through `HotkeySettingsModel`**

```java
public void assign(String id, InputGesture gesture) {
    draft.assign(id, gesture);
    presets.onBindingsEdited(draft.effectiveSnapshot());
}

public void reset(String id) {
    draft.reset(id);
    presets.onBindingsEdited(draft.effectiveSnapshot());
}

public void replace(HotkeyConflict conflict) {
    draft.replace(conflict);
    presets.onBindingsEdited(draft.effectiveSnapshot());
}

public void selectPreset(String id) {
    draft.stageSnapshot(presets.select(id));
}

public void save() { saveCoordinator.save(); }
public void cancel() { saveCoordinator.cancel(); }
public boolean hasUnsavedChanges() { return draft.isDirty() || presets.isDirty(); }
```

Route row capture, row reset, conflict replacement, category reset, and reset-all through these wrappers so every hotkey mutation updates the selected user preset.

- [ ] **Step 5: Implement controls, prompts, and clipboard behavior**

```java
public final class HotkeyPresetControls extends Widget {
    public interface Actions {
        String selectedPresetId();
        void select(String presetId);
        void create(String name);
        String copyCode();
        void importCode(String code);
        void deleteSelected();
        boolean hasUnsavedChanges();
    }
}
```

Use `Dropbox<HotkeyPreset>` for selection. Before replacing a dirty draft, open `HotkeyPresetPrompt.confirm(...)`; run the selection only from the discard callback. If the user keeps editing, restore the dropdown's displayed item to `actions.selectedPresetId()` so its visual selection cannot diverge from the model. `HotkeyPresetPrompt.name(...)` contains a `TextEntry`, localized confirm/cancel buttons, rejects blank input, and returns the trimmed name.

Copy text with:

```java
ui.wnd.clipboard(Clipboard.Std.CLIPBOARD).put(
        new Clipboard.Contents(new Clipboard.Item<CharSequence>(Clipboard.Format.TEXT, actions.copyCode())));
```

Paste asynchronously with:

```java
ReadLine.PCLine.cliptext(ui.wnd.clipboard(Clipboard.Std.CLIPBOARD))
        .map(text -> { synchronized(ui) { if(acceptsClipboardResult()) actions.importCode(text.toString()); } })
        .report(ui, L10n.get("hotkeys.presets.error.clipboard"));
```

Catch codec/store validation failures, call `ui.error(localizedMessage)`, and leave models unchanged. Disable copy/delete for built-ins. Ellipsize the selected display name to the dropdown width and set the full name as tooltip.

- [ ] **Step 6: Move the existing hotkey page below a responsive preset row**

Extend `HotkeySettingsLayout.calculate` with `presetHeight`; return `presets`, `search`, `tabs`, `filter`, and `rows` rectangles. In `HotkeySettings.resize`, use these rectangles instead of fixed Y coordinates. The row order is presets, search/conflicts, category tabs, reset buttons, scrollport. Keep active category highlighting and row ellipsis unchanged.

- [ ] **Step 7: Run UI/model tests and verify GREEN**

Run: `rtk ant test`

Expected: preset UI/model tests and the full suite pass with zero failures.

- [ ] **Step 8: Commit Task 6**

```powershell
rtk git add src/nurgling/widgets/nsettings/HotkeyPresetControls.java src/nurgling/widgets/nsettings/HotkeyPresetPrompt.java src/nurgling/widgets/nsettings/HotkeySettingsModel.java src/nurgling/widgets/nsettings/HotkeySettings.java src/nurgling/widgets/nsettings/HotkeySettingsLayout.java test/nurgling/widgets/nsettings/HotkeyPresetControlsTest.java test/nurgling/widgets/nsettings/HotkeySettingsModelTest.java test/nurgling/widgets/nsettings/HotkeySettingsLifecycleTest.java
rtk git commit -m "feat(hotkeys): add preset controls"
```

---

### Task 7: Localization, compatibility audit, and final verification

**Files:**
- Modify: `src/lang/messages.properties`
- Modify: `src/lang/messages_ru.properties`
- Modify: `test/nurgling/hotkeys/GameplayHotkeyAuditTest.java`
- Create: `test/nurgling/hotkeys/presets/HotkeyPresetCompatibilityTest.java`
- Modify: `test/nurgling/widgets/nsettings/HotkeySettingsLifecycleTest.java`

**Interfaces:**
- Consumes: all preceding preset components.
- Produces: complete English/Russian UI text, cross-version compatibility guarantees, lazy-load regression coverage, and a clean full suite.

- [ ] **Step 1: Write failing compatibility and lazy-load tests**

```java
@Test void oldPresetDefaultsNewActionsAndPreservesUnknownNewerActions() {
    HotkeyPreset old = presetWith("known", disabled(), "future.action", rightClick());
    HotkeyRegistry current = registryWith("known", "new.action");
    Map<String, InputGesture> applied = HotkeyPresetDraftModel.valuesFor(current, old);
    assertEquals(disabled(), applied.get("known"));
    assertEquals(current.find("new.action").defaultGesture(), applied.get("new.action"));
    assertEquals(rightClick(), old.gesture("future.action"));
}

@Test void initializingHotkeyRegistryDoesNotReadPresetFile() {
    CountingRepository repository = new CountingRepository();
    Hotkeys.registry();
    assertEquals(0, repository.loadCalls);
    HotkeySettingsModel.open(Hotkeys.registry(), repository);
    assertEquals(1, repository.loadCalls);
}
```

- [ ] **Step 2: Run compatibility tests and verify RED**

Run: `rtk ant test`

Expected: the missing compatibility helper/factory or missing localized keys fail.

- [ ] **Step 3: Add complete English and Russian strings**

Add the same key set to both bundles:

```properties
hotkeys.presets.label=Preset:
hotkeys.presets.default=Default
hotkeys.presets.create=Create
hotkeys.presets.copy=Copy code
hotkeys.presets.paste=Paste code
hotkeys.presets.delete=Delete
hotkeys.presets.user_name=User {0}
hotkeys.presets.name.title=Preset name
hotkeys.presets.name.empty=Enter a preset name.
hotkeys.presets.discard.title=Unsaved hotkey changes
hotkeys.presets.discard.question=Discard unsaved changes and switch presets?
hotkeys.presets.discard=Discard and switch
hotkeys.presets.keep_editing=Keep editing
hotkeys.presets.error.invalid_code=The clipboard does not contain a valid hotkey preset.
hotkeys.presets.error.clipboard=Could not access the clipboard.
hotkeys.presets.error.store=Could not save hotkey presets.
hotkeys.presets.warning.corrupt=The hotkey preset file is damaged; current bindings were preserved.
```

Use natural Russian translations, including `Пресет`, `Создать`, `Копировать код`, `Вставить код`, `Удалить`, and `Пользовательский {0}`. Extend `GameplayHotkeyAuditTest` so every `hotkeys.presets.*` key must exist and resolve in both bundles.

- [ ] **Step 4: Finish compatibility and lazy initialization behavior**

Implement `HotkeySettingsModel.open(HotkeyRegistry, HotkeyPresetRepository)` as the only production entry that loads the file. Keep `Hotkeys.registry()` and client startup free of preset I/O. Apply missing action IDs from `action.defaultGesture()` while leaving the immutable imported preset map untouched.

- [ ] **Step 5: Run focused preset and hotkey suites**

Run:

```powershell
rtk ant test-compile
rtk java -cp "build/classes;build/test-classes;lib/*;lib/ext/*;lib/ext/junit/junit-platform-console-standalone-1.14.2.jar" org.junit.platform.console.ConsoleLauncher execute --disable-banner --select-package nurgling.hotkeys --select-package nurgling.widgets.nsettings --details=summary
```

Expected: all selected tests pass with zero failures.

- [ ] **Step 6: Run full verification**

Run: `rtk ant test`

Expected: `BUILD SUCCESSFUL`, every test successful, zero failures.

- [ ] **Step 7: Inspect the final diff and commit**

```powershell
rtk git diff --check
rtk git status --short
rtk git add src/lang/messages.properties src/lang/messages_ru.properties test/nurgling/hotkeys/GameplayHotkeyAuditTest.java test/nurgling/hotkeys/presets/HotkeyPresetCompatibilityTest.java test/nurgling/widgets/nsettings/HotkeySettingsLifecycleTest.java
rtk git commit -m "test(hotkeys): verify preset compatibility"
rtk git show --stat --oneline HEAD
```

Expected: only Task 7 files are committed; unrelated worktree changes remain untouched.

---

## Final acceptance check

- [ ] Open the hotkey page and confirm `Default` is visibly selected.
- [ ] Change one key while `Default` is selected and confirm `Пользовательский 1` appears automatically.
- [ ] Save, reopen settings, edit another key, and confirm the same user preset updates.
- [ ] Create a named preset, copy its code, delete it, paste the code, and confirm the restored bindings are only staged until Save.
- [ ] Try a malformed code and confirm no draft or runtime binding changes.
- [ ] Delete the selected user preset and confirm `Default` is staged.
- [ ] Cancel after selection/create/import/delete and confirm runtime bindings and persisted presets are unchanged.
- [ ] Re-run `rtk ant test` immediately before reporting completion.
