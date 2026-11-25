--SubscriptionInfo.getSubscriptionId()
--Same logic as assets/unique/telephony/id_subscriber_id_int.lua, kept
--here under telephony/ so telephony/hooks.json resolves it from its own
--directory, and made slot-aware: the slot comes from the
--SubscriptionInfo object itself. Required by the
--SubscriptionInfo.writeToParcel hook, which reads getSubscriptionId()
--when it parcels a subscription.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local ths = param:getThis()
	local suffix = 1
	if ths ~= nil then
		local slot = ths:getSimSlotIndex()
		if slot == 0 or slot == 1 then
			suffix = slot + 1
		end
	end

	local subKey = "cell.unique.subscriber.id." .. suffix
	local subOld = "unique.gsm.subscription.id"
	if suffix == 2 then
		subOld = "unique.gsm.subscription.id.2"
	end
	local setting = param:getSettingReMap(subKey, subOld)
	if setting == nil then
		setting = param:getSetting("phone.subscriberid")
	end
	if setting ~= nil and param:isNumericString(setting) then
		param:setResultToLongInt(setting)
		return true, tostring(ret), setting
	end

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
		--Same fallbacks the unique/telephony file used; slot 2 has none.
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
		param:setResultToLongInt(fake)
		return true, tostring(ret), fake
	end

	return false
end
