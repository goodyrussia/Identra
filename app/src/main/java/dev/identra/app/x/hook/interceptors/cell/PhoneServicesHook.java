package dev.identra.app.x.hook.interceptors.cell;

import android.content.Context;

import dev.identra.xposed.callbacks.XC_LoadPackage;

/**
 * 0.7.40: system-server / phone-process telephony spoofing is deliberately NOT implemented.
 *
 * <p>What used to be here:
 * <ul>
 *   <li>{@code deployHook()} delegated to
 *       {@link dev.identra.app.x.hook.interceptors.cell.stubs.SubscriptionControllerHooks}, whose
 *       {@code getActiveSubscriptionInfoList} hooks installed after-hooks that did nothing with
 *       the result (empty {@code if (subs != null) {}} branch) — log-only hooks that looked
 *       like coverage while changing nothing. The one path that tried to build a fake result
 *       called {@code new ArrayList<>(fakeCount)} then {@code subs.set(i, ...)} on the empty
 *       list, which throws {@code IndexOutOfBoundsException}, and its per-subscription handler
 *       was an empty stub. All of that is deleted, not uncommented.</li>
 *   <li>{@code deployHook_getImeiForSlot()} was entirely commented out, and its body called a
 *       {@code getSettingValue(...)} helper that does not exist in this file — it could never
 *       have been uncommented as-is.</li>
 * </ul>
 *
 * <p>Why not (a): making the system-server path real would need, inside system_server, a
 * caller (Binder calling uid) -&gt; package -&gt; profile resolution plus constructing fake
 * {@code SubscriptionInfo} objects from per-slot profile values. No code in this tree does
 * that, and the value channel the app process uses (the SettingsProvider command channel)
 * was never wired for a system_server caller. Until that path is designed and verified,
 * telephony spoofing stays client-side only: hooks in the app process, driven by the app's
 * assigned profile. {@code dev.identra.app.Identra#hookApplication} likewise leaves
 * {@code com.android.phone} unhooked on purpose.
 *
 * <p>{@link #deployHook} is kept as an explicit no-op (rather than deleting the class) so any
 * existing caller still compiles; it installs nothing and must not be read as coverage.
 */
public class PhoneServicesHook {

    /** Intentionally installs no hooks. See the class comment. */
    public static void deployHook(final XC_LoadPackage.LoadPackageParam lpparam, Context context) {
        // No-op by design (0.7.40): system-server telephony spoofing is not implemented.
        // Callers must not assume any SubscriptionInfo / IMEI value is spoofed by this call.
    }
}
