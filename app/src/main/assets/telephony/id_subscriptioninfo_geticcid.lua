--SubscriptionInfo.getIccId()
--Same logic as assets/unique/telephony/id_subscriptioninfo_iccid.lua,
--kept here under telephony/ so telephony/hooks.json resolves it from its
--own directory, and made slot-aware: the slot comes from the
--SubscriptionInfo object itself. Required by the
--SubscriptionInfo.writeToParcel hook, which reads getIccId() when it
--parcels a subscription.
function after(hook, param)
	local ret = param:getResult()
	if ret == nil then
		return false
	end

	local ths = param:getThis()
	local suffix = 1
	if ths ~= nil then
		local slot = ths:getSimSlotIndex()
		if slot == 0 or slot == 1 then
			suffix = slot + 1
		end
	end

	local key = "cell.unique.sim.icc.id." .. suffix
	local old = "unique.gsm.sim.serial"
	if suffix == 2 then
		old = "unique.gsm.sim.serial.2"
	end
	local fake = param:getSettingReMap(key, old, "891004234814455936F")
	if fake == nil then
		return false
	end

	param:setResult(fake)
	return true, ret, fake
end
