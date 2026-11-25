package dev.identra.app.x.core.hook;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.identra.xposed.callbacks.XC_LoadPackage;
import dev.identra.app.BuildConfig;
import dev.identra.app.DebugUtil;
import dev.identra.app.XParam;
import dev.identra.app.api.properties.MockPropConversions;
import dev.identra.app.api.core.IdentraQuery;
import dev.identra.app.api.xmock.XMockQuery;
import dev.identra.app.x.Str;
import dev.identra.app.x.data.string.StrBuilder;
import dev.identra.app.x.data.utils.ListUtil;
import dev.identra.app.x.data.utils.random.RandomGenerator;
import dev.identra.app.x.ui.core.view_registry.SharedRegistry;
import dev.identra.app.x.core.LibUtil;
import dev.identra.app.x.core.commands.call.GetSettingExCommand;
import dev.identra.app.x.core.commands.query.GetSettingsExCommand;
import dev.identra.app.x.core.identity.UserIdentity;
import dev.identra.app.x.core.settings.NameInformation;
import dev.identra.app.x.core.settings.SettingHolder;
import dev.identra.app.x.core.settings.data.CanonicalSettingResolver;
import dev.identra.app.x.core.settings.random.RandomizerSessionContext;
import dev.identra.app.x.core.settings.random.interfaces.IRandomizer;
import dev.identra.app.x.core.settings.random.randomizers.RandomizersCache;

public class PackageHookContext {
    private static final String TAG = LibUtil.generateTag(PackageHookContext.class);

    public static PackageHookContext create(final XC_LoadPackage.LoadPackageParam lpparam, int uid, final Context context) { return new PackageHookContext(lpparam.packageName, uid, context); }

    public static final String RANDOM_VALUE = "%random%";
    public static final String RANDOMIZE_VALUE = "%randomize%";

    public static final List<String> RANDOM_INDICATORS = Arrays.asList(RANDOM_VALUE, RANDOMIZE_VALUE);

    public final String packageName;
    public final int uid;
    public final String temporaryKey;
    public final boolean useDefault;

    public final HashMap<String, String> settings = new HashMap<>();
    public final HashMap<String, Integer> buildPropSettings = new HashMap<>();
    public final HashMap<String, String> buildPropMaps = new HashMap<>();

    public PackageHookContext(String packageName, int uid, final Context context) {
        this.packageName = packageName;
        this.uid = uid;
        this.temporaryKey = UUID.randomUUID().toString();
        this.useDefault = GetSettingExCommand.getBool(context, GetSettingExCommand.SETTING_USE_DEFAULT, uid, packageName);

        // 0.7.40: load the central canonical resolver once per process, from the
        // MODULE's own assets (remap_settings.json). XParam.getSetting() reads the
        // resolver singleton, so this must run before any hook runs.
        // NOTE: the Context this constructor receives is the HOOKED TARGET app's
        // Application (Identra.hookApplication hooks Instrumentation.newApplication
        // / LoadedApk.makeApplication and passes its result; in system_server it
        // is the ActivityManagerService context) — its assets do NOT contain the
        // remap file, so ensureLoaded(context) here would silently fail and the
        // resolver would stay empty (exact-name lookups only). Resolve the
        // module's own assets through a package context for the module itself
        // instead. If that fails, the resolver stays empty and lookups degrade
        // to the old exact-name behaviour; nothing is invented.
        try {
            Context moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY);
            CanonicalSettingResolver.ensureLoaded(moduleContext.getAssets());
        } catch (Throwable ignored) {
        }

        Map<String, String> local = GetSettingsExCommand.getAsMap(context, true, uid, packageName, GetSettingsExCommand.FLAG_ONE);
        Map<String, String> global = GetSettingsExCommand.getAsMap(context, true, UserIdentity.DEFAULT_USER, UserIdentity.GLOBAL_NAMESPACE, GetSettingsExCommand.FLAG_ONE);

        // 0.7.40: canonical-aware merge. Per-app settings override global ones, but
        // the two sides may spell one logical setting under different names (per-app
        // "gsm.operator.mcc" vs global "cell.operator.mcc.1"). An exact-name merge
        // leaves both spellings in the map, and XParam.getSetting() tries the exact
        // name first, so asking for the canonical name would return the global value
        // even though the per-app value should win. putCanonical() writes every merged
        // value under all of its storage keys (original + canonical + slot-expanded),
        // so a per-app value overrides the global value for every alias, in both
        // directions. (This adapts the staged .PATCH: the actual merge is not two
        // plain putAll loops — the global loop already applies the per-app exact-name
        // value when both sides hold the same name, and the local loop only adds keys
        // the map does not have yet. Both loops are kept, with put() replaced by
        // putCanonical(); the local loop's containsKey guard is dropped, because a
        // global canonical value now also occupies its legacy alias keys, and the
        // guard would skip a per-app legacy value that must override it. Re-putting
        // an exact duplicate is a no-op.)
        // Within one side, bare / "[1,2]"-container names are applied before explicit
        // ".1" / ".2" names, so an explicit per-slot value is never overwritten by a
        // bare/container value expanding over it. Global passes run before local
        // passes, so per-app always wins over global.
        if(ListUtil.isValid(global)) {
            for(Map.Entry<String, String> entry : global.entrySet()) {
                if(isExplicitSlotName(entry.getKey())) continue;
                putCanonical(this.settings, entry.getKey(), valueForMerge(entry.getKey(), entry.getValue(), local));
            }
            for(Map.Entry<String, String> entry : global.entrySet()) {
                if(!isExplicitSlotName(entry.getKey())) continue;
                putCanonical(this.settings, entry.getKey(), valueForMerge(entry.getKey(), entry.getValue(), local));
            }
        }

        if(ListUtil.isValid(local)) {
            for(Map.Entry<String, String> entry : local.entrySet()) {
                if(isExplicitSlotName(entry.getKey())) continue;
                putCanonical(this.settings, entry.getKey(), entry.getValue());
            }
            for(Map.Entry<String, String> entry : local.entrySet()) {
                if(!isExplicitSlotName(entry.getKey())) continue;
                putCanonical(this.settings, entry.getKey(), entry.getValue());
            }
        }

        if(ListUtil.isValid(this.settings)) {
            for(String name : new ArrayList<>(this.settings.keySet())) {
                String value = this.settings.get(name);
                if(!Str.isEmpty(value) && value.length() > 2 && Str.countOccurrencesRegex(value, "-") == 1) {
                    try {
                        String trimmed = Str.trimOriginal(value);
                        String[] parts = trimmed.split("-");
                        if(!Str.isEmpty(parts[0]) && Str.isNumeric(parts[0]) && !Str.isEmpty(parts[1]) && Str.isNumeric(parts[1])) {
                            Long one = Str.tryParseLong(parts[0]);
                            Long two = Str.tryParseLong(parts[1]);
                            if(one != null && two != null) {
                                long fake = RandomGenerator.nextLong(one, two);
                                this.settings.put(name, String.valueOf(fake));
                                if(DebugUtil.isDebug())
                                    Log.d(TAG, Str.fm("Generated [%s] Long from [0][%s] [1][%s] Name=%s",
                                            fake,
                                            one,
                                            two,
                                            name));
                            } else {
                                if(DebugUtil.isDebug())
                                    Log.d(TAG, Str.fm("Failed to Generate Long from  [0][%s] [1][%s] Name=%s",
                                            one,
                                            two,
                                            name));
                            }
                        }
                    }catch (Exception ignored) {

                    }
                }
            }
        }

        if(DebugUtil.isDebug())
            Log.d(TAG, Str.fm("App [%s] Uid [%s] Local Settings Count=%s Global Settings Count=%s Total Settings Parsed Count=%s",
                    this.packageName,
                    this.uid,
                    ListUtil.size(local),
                    ListUtil.size(global),
                    ListUtil.size(this.settings)));

        /*settings.putAll(GetSettingsExCommand.getAsMap(
                context,
                true,
                UserIdentity.DEFAULT_USER,  //We need to get User ID
                UserIdentity.GLOBAL_NAMESPACE,
                GetSettingsExCommand.FLAG_ONE));

        settings.putAll(GetSettingsExCommand.getAsMap(
                context,
                true,
                uid,
                packageName,
                GetSettingsExCommand.FLAG_ONE));*/


        if(!this.settings.isEmpty()) {
            boolean forceWhiteList = Str.toBoolean(this.settings.get(RandomizersCache.SETTING_XP_FORCE_IS_WHITE_LIST), true);
            String forceOptions = this.settings.get(RandomizersCache.SETTING_XP_DEFAULTS);
            if(!Str.isEmpty(forceOptions)) {
                String decoded = Str.fromBase64String(forceOptions, Str.CHAR_SET_UTF_8);
                if(!Str.isEmpty(decoded) && !decoded.equalsIgnoreCase(forceOptions)) {
                    List<String> list = Str.splitToList(decoded, Str.NEW_LINE);
                    if(DebugUtil.isDebug())
                        Log.d(TAG, Str.fm("Parsing (%s) Force Settings, is White List (%s)",
                                ListUtil.size(list),
                                forceWhiteList));

                    if(ListUtil.isValid(list)) {
                        for(String s : list) {
                            String trimmed = Str.trimOriginal(s);
                            if(!Str.isEmpty(trimmed)) {
                                this.settings.put(XParam.generateSettingForceName(trimmed), String.valueOf(forceWhiteList));
                            }
                        }
                    }
                }
            }
        }

        //search_settings_hint

        initRandomizers(context);
        //final Map<String, Integer> propSettings = MockPropConversions.toMap(XMockQuery.getModifiedProperties(context, uid, pName));
        //final Map<String, String> propMaps = XMockQuery.getMockPropMapsMap(context, true, settings, false);
    }

    // Value the global loop merges for a name: the per-app value when both sides
    // hold the same exact name (per-app wins), else the global value. A null
    // per-app value falls back to the global value, as before; a null global value
    // with no per-app value is skipped by putCanonical.
    private static String valueForMerge(String name, String globalValue, Map<String, String> local) {
        if(local != null && local.containsKey(name)) {
            String otherValue = local.get(name);
            if(otherValue != null)
                return otherValue;
        }
        return globalValue;
    }

    // Put a value under every storage key its name can be asked for, so a per-app
    // value stored under a legacy name still overrides the global canonical value
    // (and vice versa). Original name first, so exact-name behaviour is preserved.
    // A null value writes nothing (putCanonical skips it) — the pre-existing merge
    // never stored a null global value, and a null per-app value is the "fall back
    // to global" case handled by valueForMerge.
    private static void putCanonical(Map<String, String> merged, String name, String value) {
        if (name == null || value == null)
            return;
        for (String key : CanonicalSettingResolver.getInstance().storageKeysForMerge(name))
            merged.put(key, value);
    }

    // True for an explicit per-slot name ("....1" / "....2"). Bare and "[1,2]"
    // container names are not explicit: they expand to slot 1 / both slots.
    private static boolean isExplicitSlotName(String name) {
        return name != null && (name.endsWith(".1") || name.endsWith(".2"));
    }

    private void initRandomizers(Context context) {
        if(ListUtil.isValid(settings)) {
            try {
                List<SettingHolder> holders = new ArrayList<>(settings.size());
                List<SettingHolder> needToRandomize = new ArrayList<>();
                if(DebugUtil.isDebug())
                    Log.d(TAG, Str.fm("Starting Pre Randomization on [%s] Settings, Package Name [%s] UID [%s] Use Default [%s]", this.settings.size(), packageName, uid, useDefault));

                Map<String, IRandomizer> randomizers = RandomizersCache.getCopy();
                SharedRegistry sharedRegistry = new SharedRegistry();
                for(Map.Entry<String, String> entry : settings.entrySet()) {
                    String name = entry.getKey();
                    String value = entry.getValue();

                    SettingHolder holder = new SettingHolder(NameInformation.createRaw(name), value, null);
                    holders.add(holder);

                    if(!Str.isEmpty(name) && !Str.isEmpty(value)) {
                        if(!randomizers.containsKey(name)) {
                            Log.w(TAG, Str.fm("Setting [%s] Does not have a Randomizer! Failed to Pre Randomize... ", name));
                            continue;
                        }

                        if(value.contains("%") && value.length() < 15) {
                            String low = value.toLowerCase();
                            if(RANDOM_INDICATORS.contains(low)) {
                                sharedRegistry.setChecked(SharedRegistry.STATE_TAG_SETTINGS, RandomizerSessionContext.sharedSettingName(name), true);
                                needToRandomize.add(holder);
                                if(DebugUtil.isDebug())
                                    Log.d(TAG, Str.fm("Pushed Setting [%s] with Value [%s] to the list of to Randomize! To Randomize Count=[%s] Holder=[%s]", name, value, needToRandomize.size(), Str.toStringOrNull(Str.noNL(holder))));
                            }
                        }
                    } else {
                        if(DebugUtil.isDebug())
                            Log.w(TAG, Str.fm("Error Null or Empty ? Skipping Setting:" + name + " Value=" + value));
                    }
                }

                if(DebugUtil.isDebug())
                    Log.d(TAG, Str.fm("Finished Parsing Settings for Pre Randomization, Settings Count=[%s] To Randomize Count=[%s] Now Randomizing the need!", holders.size(), needToRandomize.size()));

                RandomizerSessionContext.create()
                        .randomize(
                                holders,
                                needToRandomize,
                                context,
                                sharedRegistry);
                if(DebugUtil.isDebug())
                    Log.d(TAG, Str.fm("Finished Pre Randomizing [%s] Settings, Total Settings [%s] Now pushing!", needToRandomize.size(), holders.size()));

                for(SettingHolder holder : needToRandomize) {
                    String name = holder.getName();
                    boolean hasSetting = this.settings.containsKey(name);
                    String oldVal = this.settings.get(name);
                    String newVal = holder.getNewValue();
                    if(DebugUtil.isDebug())
                        Log.d(TAG, Str.fm("Updating Setting [%s] with new Value [%s] from old Value [%s], exists=[%s]", name, newVal, oldVal, hasSetting));

                    this.settings.put(name, newVal);
                }
            }catch (Exception e) {
                Log.e(TAG, Str.fm("Failed to Pre Randomize Settings! Settings Count [%s] Package Name [%s] UID [%s] Use Default [%s] Error=", this.settings.size(), packageName, uid, useDefault, e));
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return StrBuilder.create()
                .ensureOneNewLinePer(true)
                .appendFieldLine("PackageName", this.packageName)
                .appendFieldLine("UID", this.uid)
                .appendFieldLine("Temp Key", this.temporaryKey)
                .appendFieldLine("Use Default", this.useDefault)
                .appendFieldLine("Settings Count", this.settings.size())
                .appendFieldLine("Build Prop Settings Count", this.buildPropSettings.size())
                .appendFieldLine("Build Prop Settings Map Count", this.buildPropMaps.size())
                .toString(true);
    }
}
