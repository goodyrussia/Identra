package dev.identra.app.x.core.settings.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Central canonical setting resolver.
 *
 * Problem it fixes: three name systems never met. A profile stores a value
 * under its canonical per-slot name (e.g. "cell.operator.mcc.1"), the
 * per-app screen lists the bracket container ("cell.operator.mcc.[1,2]"),
 * and Lua hooks ask for a legacy name ("gsm.operator.mcc" / "value.imei").
 * XParam.getSetting() used to be a plain map lookup, so a correctly saved
 * profile value was never read by the hook that needed it.
 *
 * This resolver is the single place that knows how the names relate:
 *   (a) bracket/container keys "cell.*.[1,2]" expand to ".1" / ".2" by slot,
 *   (b) canonical <-> legacy via assets/remap_settings.json (both directions,
 *       so a Lua script asking "gsm.operator.mcc" finds a stored
 *       "cell.operator.mcc.1", and a caller asking the canonical name finds
 *       a value stored long ago under a legacy name),
 *   (c) a bare name ("cell.operator.mcc") defaults to slot 1 (".1").
 *
 * Loading: exactly once per process, from the MODULE's own assets, by
 * PackageHookContext at process start (see PackageHookContext.java). XParam and
 * SettingsApi only read the singleton; they never load assets themselves.
 * That matters: in a hooked process XParam's Context belongs to the target
 * app, whose assets do not contain remap_settings.json. SettingsApi runs in
 * the manager app process, where the passed Context IS Identra's own, so it
 * may call ensureLoaded(context) itself.
 *
 * If the remap asset cannot be loaded, the resolver is empty and every
 * method degrades to the old behaviour (exact-name lookup, suffix-only
 * container derivation). Nothing is invented: a name with no stored value
 * under any of its aliases still resolves to null.
 *
 * remap_settings.json is not strict JSON (trailing commas, duplicate ids),
 * so it is parsed with a small tolerant scanner that only reads each
 * entry's "id" and "oldIds" strings, instead of a strict JSON parser.
 * Duplicate canonical ids are merged, not overwritten, and one legacy name
 * may map to several canonical ids (e.g. "gsm.operator.id" is an oldId of
 * both "cell.operator.id.1" and "cell.operator.numeric.id.1"); candidates
 * are tried in file order, first stored value wins.
 */
public final class CanonicalSettingResolver {

    private static final String TAG = "Identra.CanonicalSettingResolver";
    private static final String REMAP_ASSET = "remap_settings.json";
    private static final String DELETE_ID = "__delete";
    private static final String SLOT_CONTAINER = ".[1,2]";
    private static final String SLOT_1_SUFFIX = ".1";
    private static final String SLOT_2_SUFFIX = ".2";

    private static final CanonicalSettingResolver EMPTY = new CanonicalSettingResolver(null);

    private static volatile CanonicalSettingResolver sInstance;
    private static volatile boolean sLoadAttempted;

    // Legacy name -> canonical ids, in remap file order.
    private final Map<String, List<String>> mLegacyToCanonical = new LinkedHashMap<>();
    // Canonical id -> legacy names, in remap file order.
    private final Map<String, List<String>> mCanonicalToLegacy = new LinkedHashMap<>();
    private final Set<String> mCanonicalIds = new HashSet<>();
    // Names listed under the "__delete" remap id: never resolved by alias.
    private final Set<String> mDeleted = new HashSet<>();
    private final boolean mLoaded;

    private CanonicalSettingResolver(String remapText) {
        boolean loaded = false;
        if (remapText != null) {
            try {
                parseRemap(remapText);
                loaded = true;
            } catch (Throwable e) {
                Log.e(TAG, "Failed to parse " + REMAP_ASSET + ", resolver disabled: " + e);
                mLegacyToCanonical.clear();
                mCanonicalToLegacy.clear();
                mCanonicalIds.clear();
                mDeleted.clear();
            }
        }
        mLoaded = loaded;
    }

    /** Singleton; never null. Empty (exact-name only) until ensureLoaded runs. */
    public static CanonicalSettingResolver getInstance() {
        CanonicalSettingResolver instance = sInstance;
        return instance != null ? instance : EMPTY;
    }

    /**
     * Load remap_settings.json once per process from the MODULE's own assets.
     * Call from PackageHookContext at process start, and from SettingsApi in
     * the manager app process. Never pass a hooked target app's Context: its
     * assets do not contain the remap file. Safe to call repeatedly; only the
     * first call reads the asset.
     */
    public static CanonicalSettingResolver ensureLoaded(Context context) {
        if (context == null)
            return getInstance();
        try {
            return ensureLoaded(context.getAssets());
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load " + REMAP_ASSET + " from context assets: " + e);
            return getInstance();
        }
    }

    /** Same as ensureLoaded(Context), for callers that already hold an AssetManager. */
    public static CanonicalSettingResolver ensureLoaded(AssetManager assets) {
        if (sLoadAttempted)
            return getInstance();
        synchronized (CanonicalSettingResolver.class) {
            if (sLoadAttempted)
                return getInstance();
            sLoadAttempted = true;
            if (assets == null)
                return getInstance();
            try {
                InputStream in = assets.open(REMAP_ASSET);
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    StringBuilder text = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                        text.append(line).append('\n');
                    sInstance = new CanonicalSettingResolver(text.toString());
                } finally {
                    try { in.close(); } catch (Throwable ignore) { }
                }
            } catch (Throwable e) {
                Log.e(TAG, "Failed to load " + REMAP_ASSET + ", resolver disabled: " + e);
            }
            return getInstance();
        }
    }

    public boolean isLoaded() { return mLoaded; }

    /** Slot index from XParam.getTargetSubIndex(): only 0 and 1 are slots; anything else means slot 1. */
    public static int clampSlot(int slot) { return slot == 1 ? 1 : 0; }

    public boolean isDeleted(String name) {
        return name != null && (DELETE_ID.equals(name) || mDeleted.contains(name));
    }

    /**
     * The storage name a setting should be read from / written to.
     * Exact ".1" / ".2" names are returned unchanged (explicit wins).
     * A "[1,2]" container expands to ".1" or ".2" by slot.
     * A legacy name maps to its canonical id; a bare canonical name gains
     * the slot suffix when a per-slot canonical id exists. Unknown names
     * are returned unchanged.
     */
    public String canonicalName(String name, int slot) {
        if (name == null || name.isEmpty())
            return name;
        int slotIndex = clampSlot(slot);
        if (name.endsWith(SLOT_CONTAINER))
            return name.substring(0, name.length() - SLOT_CONTAINER.length()) + "." + (slotIndex + 1);
        if (name.endsWith(SLOT_1_SUFFIX) || name.endsWith(SLOT_2_SUFFIX))
            return name;

        List<String> canonicals = mLegacyToCanonical.get(name);
        if (canonicals != null && !canonicals.isEmpty()) {
            String canonical = canonicals.get(0);
            if (canonical.endsWith(SLOT_CONTAINER))
                return canonical.substring(0, canonical.length() - SLOT_CONTAINER.length()) + "." + (slotIndex + 1);
            if (canonical.endsWith(SLOT_1_SUFFIX) || canonical.endsWith(SLOT_2_SUFFIX))
                return canonical;
            String slotted = canonical + "." + (slotIndex + 1);
            return mCanonicalIds.contains(slotted) ? slotted : canonical;
        }

        if (mCanonicalIds.contains(name)) {
            String slotted = name + "." + (slotIndex + 1);
            return mCanonicalIds.contains(slotted) ? slotted : name;
        }

        String slotted = name + "." + (slotIndex + 1);
        return mCanonicalIds.contains(slotted) ? slotted : name;
    }

    /**
     * Value lookup over a settings map. Candidate order, first stored value wins:
     *   1. the exact name (preserves per-app override and all existing behaviour),
     *   2. the slot-expanded / canonical form of the name,
     *   3. every canonical id the name is a legacy alias of (legacy -> canonical),
     *   4. every legacy alias of the name, when the name itself is canonical
     *      (canonical -> legacy: a value stored long ago under an old name),
     *   5. the bare name with a slot suffix, and the name with its ".1"/".2"
     *      suffix stripped (bare stored form).
     * Deleted ("__delete") names are never produced by steps 2-5; an explicitly
     * stored value under such a name is still returned by step 1, as before.
     * Returns null when no candidate is stored. Callers must hold whatever
     * lock guards the map, if any (XParam.getSetting synchronizes on it).
     */
    public String lookup(Map<String, String> settings, String name, int slot) {
        if (settings == null || name == null || name.isEmpty())
            return null;
        if (settings.containsKey(name))
            return settings.get(name);
        if (isDeleted(name))
            return null;

        int slotIndex = clampSlot(slot);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        String canonical = canonicalName(name, slotIndex);
        if (canonical != null && !canonical.equals(name))
            candidates.add(canonical);

        List<String> canonicals = mLegacyToCanonical.get(name);
        if (canonicals != null) {
            for (String candidate : canonicals) {
                addExpandedCandidate(candidates, candidate, slotIndex);
            }
        }

        List<String> legacyNames = mCanonicalToLegacy.get(name);
        if (legacyNames != null) {
            for (String legacy : legacyNames)
                candidates.add(legacy);
        }

        if (!name.endsWith(SLOT_1_SUFFIX) && !name.endsWith(SLOT_2_SUFFIX) && !name.endsWith(SLOT_CONTAINER)) {
            candidates.add(name + "." + (slotIndex + 1));
            candidates.add(name + ".1");
        }
        if (name.endsWith(SLOT_1_SUFFIX) || name.endsWith(SLOT_2_SUFFIX))
            candidates.add(name.substring(0, name.length() - 2));

        for (String candidate : candidates) {
            if (candidate == null || candidate.equals(name) || isDeleted(candidate))
                continue;
            if (settings.containsKey(candidate))
                return settings.get(candidate);
        }
        return null;
    }

    private void addExpandedCandidate(Set<String> candidates, String candidate, int slotIndex) {
        if (candidate == null || candidate.isEmpty())
            return;
        candidates.add(candidate);
        if (candidate.endsWith(SLOT_CONTAINER)) {
            candidates.add(candidate.substring(0, candidate.length() - SLOT_CONTAINER.length()) + "." + (slotIndex + 1));
            candidates.add(candidate.substring(0, candidate.length() - SLOT_CONTAINER.length()) + ".1");
        } else if (!candidate.endsWith(SLOT_1_SUFFIX) && !candidate.endsWith(SLOT_2_SUFFIX)) {
            candidates.add(candidate + "." + (slotIndex + 1));
            candidates.add(candidate + ".1");
        }
    }

    /**
     * The "[1,2]" container name for a stored row, or null when the row has
     * no per-slot container. Works without a loaded remap for plain ".1"/".2"
     * suffixes; with a loaded remap it also handles legacy names
     * ("gsm.operator.mcc" -> "cell.operator.mcc.[1,2]").
     * Used by SettingsApi to fill container rows for display.
     */
    public String containerNameFor(String name) {
        if (name == null || name.isEmpty())
            return null;
        if (name.endsWith(SLOT_CONTAINER))
            return name;
        String canonical = canonicalName(name, 0);
        if (canonical != null && (canonical.endsWith(SLOT_1_SUFFIX) || canonical.endsWith(SLOT_2_SUFFIX)))
            return canonical.substring(0, canonical.length() - 2) + SLOT_CONTAINER;
        if (name.endsWith(SLOT_1_SUFFIX) || name.endsWith(SLOT_2_SUFFIX))
            return name.substring(0, name.length() - 2) + SLOT_CONTAINER;
        return null;
    }

    /**
     * Storage keys a merged value should be written under, for the
     * PackageHookContext process-start merge (per-app over global). The
     * original name is always first, so exact-name behaviour is preserved;
     * canonical / slot-expanded keys follow so a per-app value stored under
     * a legacy name still overrides the global canonical value (and vice
     * versa) for every name the hook may ask for.
     */
    public List<String> storageKeysForMerge(String name) {
        if (name == null || name.isEmpty())
            return Collections.emptyList();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add(name);
        if (name.endsWith(SLOT_CONTAINER)) {
            String base = name.substring(0, name.length() - SLOT_CONTAINER.length());
            keys.add(base + ".1");
            keys.add(base + ".2");
            return new ArrayList<>(keys);
        }
        String canonical = canonicalName(name, 0);
        if (canonical != null && !canonical.equals(name))
            keys.add(canonical);
        if (canonical != null && canonical.endsWith(SLOT_CONTAINER)) {
            String base = canonical.substring(0, canonical.length() - SLOT_CONTAINER.length());
            keys.add(base + ".1");
            keys.add(base + ".2");
        }
        // 0.7.40 merge correction: canonical -> legacy as well. A value stored
        // under the canonical name must also occupy its legacy alias keys.
        // Without this, a global value stored under a legacy name survives under
        // that alias key in the merged map, and a per-app canonical value —
        // written only under the canonical key — loses to it when Lua asks by
        // the legacy name, because lookup() tries the exact name first. The
        // staged resolver only expanded legacy -> canonical, so the merge's
        // "both directions" guarantee did not hold for global-legacy vs
        // per-app-canonical. (A legacy name shared by two canonical ids, e.g.
        // "gsm.operator.id", is written by both; lookup prefers the exact /
        // canonical key first, so the shared alias is only a fallback.)
        String canonicalId = canonical != null ? canonical : name;
        List<String> legacyNames = mCanonicalToLegacy.get(canonicalId);
        if (legacyNames != null)
            keys.addAll(legacyNames);
        return new ArrayList<>(keys);
    }

    // ---- tolerant remap parsing (remap_settings.json is not strict JSON) ----

    private void parseRemap(String text) {
        for (String entry : splitTopLevelObjects(text)) {
            String id = extractStringValue(entry, "id");
            if (id == null || id.isEmpty())
                continue;
            List<String> oldIds = extractStringArray(entry, "oldIds");
            if (DELETE_ID.equals(id)) {
                mDeleted.add(id);
                mDeleted.addAll(oldIds);
                continue;
            }
            mCanonicalIds.add(id);
            List<String> legacy = mCanonicalToLegacy.get(id);
            if (legacy == null) {
                legacy = new ArrayList<>();
                mCanonicalToLegacy.put(id, legacy);
            }
            for (String oldId : oldIds) {
                if (oldId == null || oldId.isEmpty())
                    continue;
                if (!legacy.contains(oldId))
                    legacy.add(oldId);
                List<String> canonicals = mLegacyToCanonical.get(oldId);
                if (canonicals == null) {
                    canonicals = new ArrayList<>();
                    mLegacyToCanonical.put(oldId, canonicals);
                }
                if (!canonicals.contains(id))
                    canonicals.add(id);
            }
        }
    }

    /** Top-level {...} objects of the outer [...] array, string-aware. */
    private static List<String> splitTopLevelObjects(String text) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth == 0)
                    start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(text.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private static String extractStringValue(String entry, String key) {
        int keyAt = entry.indexOf("\"" + key + "\"");
        if (keyAt < 0)
            return null;
        int colon = entry.indexOf(':', keyAt + key.length() + 2);
        if (colon < 0)
            return null;
        int quote = entry.indexOf('"', colon + 1);
        if (quote < 0)
            return null;
        int end = findStringEnd(entry, quote);
        return end < 0 ? null : unescape(entry.substring(quote + 1, end));
    }

    private static List<String> extractStringArray(String entry, String key) {
        List<String> values = new ArrayList<>();
        int keyAt = entry.indexOf("\"" + key + "\"");
        if (keyAt < 0)
            return values;
        int open = entry.indexOf('[', keyAt + key.length() + 2);
        if (open < 0)
            return values;
        // The closing ']' must be found string-aware: oldIds values
        // themselves contain "[1,2]" (e.g. "cell.sim.country.numeric.code.[1,2]").
        int close = findArrayEnd(entry, open);
        if (close < 0)
            return values;
        int i = open + 1;
        while (i < close) {
            if (entry.charAt(i) == '"') {
                int end = findStringEnd(entry, i);
                if (end < 0 || end > close)
                    break;
                values.add(unescape(entry.substring(i + 1, end)));
                i = end + 1;
            } else {
                i++;
            }
        }
        return values;
    }

    /** Index of the ']' closing the array opened at open, ignoring brackets inside quoted strings, or -1. */
    private static int findArrayEnd(String text, int open) {
        boolean inString = false;
        for (int i = open + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == ']') {
                return i;
            }
        }
        return -1;
    }

    /** Index of the closing '"' of the string starting at quote, or -1. */
    private static int findStringEnd(String text, int quote) {
        for (int i = quote + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static String unescape(String raw) {
        if (raw.indexOf('\\') < 0)
            return raw;
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                out.append(raw.charAt(++i));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Visible for logging / tests: how many canonical ids the remap holds. */
    public int remapEntryCount() { return mCanonicalIds.size(); }
}
