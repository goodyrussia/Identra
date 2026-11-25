--TelephonyManager.getDeviceId()
--Same logic as assets/unique/telephony/id_telephony_getdeviceid.lua,
--kept here under telephony/ so telephony/hooks.json resolves it from its
--own directory. GSM returns the IMEI, CDMA returns the MEID.
function after(hook, param)
	local ths = param:getThis()
	local ret = param:getResult()
	if ths == nil or ret == nil then
		return false
	end

	local typ = ths:getPhoneType()
	local fake

	if typ == 1 then
		fake = param:getSettingReMap("cell.unique.imei.1", "unique.gsm.imei")
		if fake == nil then
			fake = param:getSetting("value.imei")
		end
		log("GSM [IMEI]")
	elseif typ == 2 then
		fake = param:getSettingReMap("cell.unique.meid.1", "unique.gsm.meid")
		if fake == nil then
			fake = param:getSetting("value.meid")
		end
		log("CDMA [MEID]")
	end

	if fake == nil then
		return false
	end

	--log("Setting Device ID [" .. ret .. "] => " .. fake)
	param:setResult(fake)
	return true, ret, fake
end
