--SubscriptionInfo.getNumber()
--Line-1 number of the subscription this object represents. Slot-aware:
--the slot comes from the SubscriptionInfo object itself. Required by
--the SubscriptionInfo.writeToParcel hook, which reads getNumber() when
--it parcels a subscription.
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

	local key = "cell.unique.phone.number." .. suffix
	local old = "unique.gsm.phone.number"
	if suffix == 2 then
		old = "unique.gsm.phone.number.2"
	end
	local fake = param:getSettingReMap(key, old)
	if fake == nil and suffix == 1 then
		fake = param:getSetting("value.phone_number", "6666666666")
	end
	if fake == nil then
		return false
	end

	param:setResult(fake)
	return true, ret, fake
end
