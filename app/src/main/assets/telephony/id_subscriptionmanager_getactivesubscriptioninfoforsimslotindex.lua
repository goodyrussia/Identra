--SubscriptionManager.getActiveSubscriptionInfoForSimSlotIndex(int slotIndex)
--Rebuilds the returned SubscriptionInfo from profile values (same
--parcel round-trip as getActiveSubscriptionInfoList: the hooked
--writeToParcel writes profile values, the hooked CREATOR
--:createFromParcel reads them back). When the requested slot has no
--SIM in the profile, the subscription is hidden (null): the profile
--is the source of truth. A profile SIM the real device does not have
--cannot be fabricated here (there is no object to rebuild from). If
--the round-trip fails, the original object is kept: its getter hooks
--still spoof reads.
function after(hook, param)
	local info = param:getResult()
	local slot = param:tryGetArgumentInt(0, -1)
	if slot ~= 0 and slot ~= 1 then
		return false
	end
	local suffix = slot + 1
	local subKey = "cell.unique.subscriber.id." .. suffix
	local subOld = "unique.gsm.subscription.id"
	if suffix == 2 then
		subOld = "unique.gsm.subscription.id.2"
	end
	if param:getSettingReMap(subKey, subOld) == nil then
		param:setResult(null)
		return true, "slot=" .. tostring(slot), "null"
	end
	if info == nil then
		return false
	end

	local rebuilt = rebuildFromProfile(param, info)
	if rebuilt == nil then
		rebuilt = info
	end
	param:setResult(rebuilt)
	return true, "slot=" .. tostring(slot), "rebuilt"
end

function rebuildFromProfile(param, info)
	local Parcel = luajava.bindClass("android.os.Parcel")
	local parcel = Parcel:obtain()
	local ok = pcall(function() info:writeToParcel(parcel, 0) end)
	if not ok then
		parcel:recycle()
		return nil
	end
	parcel:setDataPosition(0)
	local rebuilt = nil
	local ok2, res2 = pcall(function()
		local Creator = luajava.bindClass("android.telephony.SubscriptionInfo").CREATOR
		return Creator:createFromParcel(parcel)
	end)
	if ok2 then
		rebuilt = res2
	else
		log("getActiveSubscriptionInfoForSimSlotIndex: rebuild failed: " .. tostring(res2))
	end
	parcel:recycle()
	return rebuilt
end
