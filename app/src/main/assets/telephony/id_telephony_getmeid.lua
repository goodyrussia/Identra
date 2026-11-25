--TelephonyManager.getMeid()
--Same logic as assets/unique/telephony/id_telephony_getmeid.lua, kept
--here under telephony/ so telephony/hooks.json resolves it from its own
--directory.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local fake = param:getSettingReMap("cell.unique.meid.1", "unique.gsm.meid", "000")
	if fake == nil then
		fake = param:getSetting("value.meid", "000")
	end
	if fake == nil then
		return false
	end

	param:setResult(fake)
	return true, ret, fake
end
