--TelephonyManager.getLine1Number()
--Same logic as assets/id_phonenumber_str.lua, kept here under telephony/
--so telephony/hooks.json resolves it from its own directory.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local fake = param:getSettingReMap("cell.unique.phone.number.1", "unique.gsm.phone.number")
	if fake == nil then
		fake = param:getSetting("value.phone_number", "6666666666")
	end

	--log("Fake [line1Number] ... ");
	param:setResult(fake)
	return true, ret, fake
end
