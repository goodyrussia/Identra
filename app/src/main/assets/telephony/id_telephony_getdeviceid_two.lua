--TelephonyManager.getDeviceId(int slotIndex)
--Slot variant of getDeviceId: GSM returns the IMEI for the requested
--slot, CDMA returns the MEID for the requested slot. Profile is the
--source of truth: a slot with no profile value leaves the real value.
function after(hook, param)
	local ths = param:getThis()
	local ret = param:getResult()
	if ths == nil or ret == nil then
		return false
	end

	local slot = param:tryGetArgumentInt(0, 0)
	local suffix = slot + 1
	if suffix ~= 1 and suffix ~= 2 then
		return false
	end

	local typ = ths:getPhoneType()
	local fake

	if typ == 1 then
		local key = "cell.unique.imei." .. suffix
		local old = "unique.gsm.imei"
		if suffix == 2 then
			old = "unique.gsm.imei.2"
		end
		fake = param:getSettingReMap(key, old)
		if fake == nil and suffix == 1 then
			fake = param:getSetting("value.imei")
		end
		log("GSM [IMEI]")
	elseif typ == 2 then
		local key = "cell.unique.meid." .. suffix
		local old = "unique.gsm.meid"
		if suffix == 2 then
			old = "unique.gsm.meid.2"
		end
		fake = param:getSettingReMap(key, old)
		if fake == nil and suffix == 1 then
			fake = param:getSetting("value.meid")
		end
		log("CDMA [MEID]")
	end

	if fake == nil then
		return false
	end

	param:setResult(fake)
	return true, ret, fake
end
