package dev.identra.app.x.hook.interceptors.cell.stubs;

import android.content.Context;

import dev.identra.xposed.callbacks.XC_LoadPackage;

/**
 * 0.7.40: system-server subscription spoofing is deliberately NOT implemented.
 *
 * <p>What used to be here: {@link #deployHook_getActiveSubscriptionInfoList} hooked
 * {@code getActiveSubscriptionInfoList} on {@code SubscriptionController} and on
 * {@code SubscriptionManagerService} with after-hooks that only logged (or did nothing at
 * all with the result), plus a second hook, gated on the phone process, that tried to
 * rebuild the result list from {@code PhoneHookUtils.getActiveSimCount()} but called
 * {@code subs.set(i, ...)} on a freshly created {@code ArrayList} — which throws
 * {@code IndexOutOfBoundsException} before any value could be faked — and whose
 * per-subscription handler ({@code handleSubscriptionInfoObject}) was an empty stub. None
 * of it ever changed a returned value. It is deleted, not uncommented.
 *
 * <p>Why not (a): a real implementation would need, inside system_server, a caller (Binder
 * calling uid) -&gt; package -&gt; profile resolution plus constructing fake
 * {@code SubscriptionInfo} objects from the per-slot profile values, including the slot-2
 * values this release adds to the profile. No code in this tree does that. Until that path
 * is designed and verified, telephony spoofing stays client-side only (hooks in the app
 * process, driven by the app's assigned profile). See {@code PhoneServicesHook} and
 * {@code dev.identra.app.Identra}.
 *
 * <p>The method below is kept as an explicit no-op (rather than deleting the class) so any
 * existing caller still compiles; it installs nothing and must not be read as coverage.
 */
public class SubscriptionControllerHooks {

    /** Intentionally installs no hooks. See the class comment. */
    public static void deployHook_getActiveSubscriptionInfoList(final XC_LoadPackage.LoadPackageParam lpparam, Context context) {
        // No-op by design (0.7.40): system-server subscription spoofing is not implemented.
        // Callers must not assume getActiveSubscriptionInfoList() returns spoofed values.
    }
}
