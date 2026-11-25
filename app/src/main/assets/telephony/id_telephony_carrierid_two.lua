-- CellIdentity*.getMobileNetworkOperator (Wcdma/Tdscdma/Lte/Gsm)
-- 0.7.40: upstream id_telephony_carrierid_two.lua was 0 bytes, so the four
-- CellIdentity hooks that point at this script ran an empty script and the
-- app saw the real MCC+MNC. This file implements it, mirroring the slot-1
-- pattern of id_telephony_carrierid.lua: return the stored operator id when
-- it is numeric, else compose MCC+MNC from the profile.
-- The "_two" suffix is a variant name, NOT a slot-2 selector: this hook has
-- no slot signal to select on. CellIdentity.getMobileNetworkOperator() takes
-- no arguments, and a CellIdentity exposes no slot index (unlike
-- SubscriptionInfo, which has getSimSlotIndex), so getTargetSubIndex() is 0
-- and the bare names below resolve to slot 1 through the resolver. Reading
-- .2 keys here would spoof SIM-2 values onto any cell identity, including
-- SIM-1 cells. A true per-slot CellIdentity spoof would need the caller's
-- subscription context, which this hook does not have.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

    local setting = param:getSetting("gsm.operator.id")
    if param:isNumericString(setting) then
        param:setResult(setting)
        return true, ret, setting
    else
        local mcc = param:getSettingReMap("gsm.operator.mcc", "phone.mcc", "274")
        local mnc = param:getSettingReMap("gsm.operator.mnc", "phone.mnc", "299")
        if mcc == nil or mnc == nil then
            return false
        end

        if param:isNumericString(mcc) and param:isNumericString(mnc) then
            local fake = mcc .. mnc
            param:setResult(fake)
            return true, ret, fake
        end
    end
	return false
end
