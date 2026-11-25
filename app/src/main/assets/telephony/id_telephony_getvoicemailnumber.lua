--TelephonyManager.getVoiceMailNumber()
--No dedicated voicemail-number key exists in the profile (see
--remap_settings.json: only the voicemail alpha tag has keys). Upstream
--maps this method to the line-1 number in unique/telephony/hooks.json,
--so this file mirrors that: the profile line-1 number is returned as
--the voice mail number.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local fake = param:getSettingReMap("cell.unique.phone.number.1", "unique.gsm.phone.number")
	if fake == nil then
		fake = param:getSetting("value.phone_number", "6666666666")
	end

	param:setResult(fake)
	return true, ret, fake
end
