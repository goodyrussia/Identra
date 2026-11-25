--TelephonyManager.getSubscriberId() (no slot: the default subscription)
--Same logic as assets/unique/telephony/id_subscriber_id_string.lua,
--kept here under telephony/ so telephony/hooks.json resolves it from its
--own directory. Slot-1 (default subscription) keys only.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local setting = param:getSettingReMap("cell.unique.subscriber.id.1", "unique.gsm.subscription.id")
	if setting == nil then
		setting = param:getSetting("phone.subscriberid")
	end
	if setting ~= nil and param:isNumericString(setting) then
		param:setResult(setting)
		return true, ret, setting
	end

	local mcc = param:getSettingReMap("cell.operator.mcc.1", "gsm.operator.mcc", "274")
	local mnc = param:getSettingReMap("cell.operator.mnc.1", "gsm.operator.mnc", "299")
	--next line is 9 chars MSIN
	local msin = param:getSettingReMap("cell.unique.msin.1", "unique.gsm.operator.msin", "842762952")
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
