--TelephonyManager.getImei(int slotIndex)
--Slot variant of getImei: returns the profile IMEI for the requested
--slot. Profile is the source of truth: a slot with no profile value
--leaves the real value.
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

	local key = "cell.unique.imei." .. suffix
	local old = "unique.gsm.imei"
	if suffix == 2 then
		old = "unique.gsm.imei.2"
	end
	local fake = param:getSettingReMap(key, old)
	if fake == nil and suffix == 1 then
		fake = param:getSetting("value.imei", "000")
	end
	if fake == nil then
		return false
	end

	param:setResult(fake)
	return true, ret, fake
end
