--TelephonyManager.getVoiceMailAlphaTag()
--Same logic as assets/unique/telephony/id_telephony_getvoicemailalphatag.lua,
--kept here under telephony/ so telephony/hooks.json resolves it from its
--own directory.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local fake = param:getSettingReMap("cell.voice.mail.alpha.tag.1", "unique.gsm.voicemail.id", "000000000")
	if fake == nil then
		fake = param:getSetting("phone.voicemailid", "000000000")
	end
	if fake == nil then
		return false
	end

	param:setResult(fake)
	return true, ret, fake
end
