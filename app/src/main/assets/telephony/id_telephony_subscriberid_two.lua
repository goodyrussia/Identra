--TelephonyManager.getSubscriberId(int slotIndex)
--Replacement for __depricated_id_telephony_subscriberid_two.lua, which
--read the slot-1 keys for every slot. This file picks the keys for the
--slot the caller asked for: slotIndex 0 -> profile slot 1, slotIndex 1
-- -> profile slot 2. Any other slot leaves the real value untouched.
--Profile is the source of truth: when the profile has no value for the
--requested slot, the hook returns false instead of reusing slot 1.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local slot = param:tryGetArgumentInt(0, 0)
	local suffix = slot + 1
	if suffix ~= 1 and suffix ~= 2 then
		return false
	end

	--Full IMSI stored in the profile for this slot, if the user set one.
	local subKey = "cell.unique.subscriber.id." .. suffix
	local subOld = "unique.gsm.subscription.id"
	if suffix == 2 then
		subOld = "unique.gsm.subscription.id.2"
	end
	local setting = param:getSettingReMap(subKey, subOld)
	if setting ~= nil and param:isNumericString(setting) then
		param:setResult(setting)
		return true, ret, setting
	end

	--Otherwise build the IMSI from MCC + MNC + MSIN for this slot.
	local mccKey = "cell.operator.mcc." .. suffix
	local mccOld = "gsm.operator.mcc"
	local mncKey = "cell.operator.mnc." .. suffix
	local mncOld = "gsm.operator.mnc"
	if suffix == 2 then
		mccOld = "gsm.operator.mcc.2"
		mncOld = "gsm.operator.mnc.2"
	end
	local mcc = param:getSettingReMap(mccKey, mccOld)
	local mnc = param:getSettingReMap(mncKey, mncOld)
	local msinKey = "cell.unique.msin." .. suffix
	local msinOld = "unique.gsm.operator.msin"
	if suffix == 2 then
		msinOld = "unique.gsm.operator.msin.2"
	end
	--next line is 9 chars MSIN
	local msin = param:getSettingReMap(msinKey, msinOld)
	if suffix == 1 then
		--Same fallbacks the deprecated slot-1 file used; slot 2 has none.
		if mcc == nil then
			mcc = param:getSetting("phone.mcc", "274")
		end
		if mnc == nil then
			mnc = param:getSetting("phone.mnc", "299")
		end
		if msin == nil then
			msin = param:getSetting("phone.msin", "842762952")
		end
	end
	if mcc == nil or mnc == nil or msin == nil then
		return false
	end

	--Returns the unique subscriber ID, for example, the IMSI for a GSM phone.
	if param:isNumericString(mcc) and param:isNumericString(mnc) and param:isNumericString(msin) then
		local fake = mcc .. mnc .. msin
		param:setResult(fake)
		return true, ret, fake
	end

	return false
end
