--TelephonyManager.getSimSerialNumber()
--Same logic as assets/unique/telephony/id_telephony_getsimserialnumber.lua,
--kept here under telephony/ so telephony/hooks.json resolves it from its
--own directory. The SIM serial is the ICC ID (see remap_settings.json:
--"unique.gsm.sim.serial" is an old id of "cell.unique.sim.icc.id.1").
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local fake = param:getSettingReMap("cell.unique.sim.icc.id.1", "unique.gsm.sim.serial", "000000000")
	if fake == nil then
		return false
	end

	param:setResult(fake)
	return true, ret, fake
end
