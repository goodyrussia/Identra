--TelephonyManager.getImei()
--Same logic as assets/unique/telephony/id_telephony_getimei.lua, kept
--here under telephony/ so telephony/hooks.json resolves it from its own
--directory.
function after(hook, param)
	--local ths = param:getThis()
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local fake = param:getSettingReMap("cell.unique.imei.1", "unique.gsm.imei", "000")
	if fake == nil then
		fake = param:getSetting("value.imei", "000")
	end
	if fake == nil then
		return false
	end

	param:setResult(fake)
	return true, ret, fake
end
