--SubscriptionManager.getActiveSubscriptionInfoList()
--Rebuilds the returned List<SubscriptionInfo> from profile values.
--How it works: each real SubscriptionInfo is round-tripped through a
--Parcel. Writing it calls the hooked SubscriptionInfo.writeToParcel,
--which writes the profile values (it reads the hooked getters), and
--reading it back calls the hooked SubscriptionInfo.CREATOR
--:createFromParcel, which overwrites the fields from the profile.
--Only slots configured in the profile are kept: the profile is the
--source of truth, so a real subscription with no profile SIM is
--dropped from the list. A profile SIM the real device does not have
--cannot be fabricated here (there is no object to rebuild from).
--If the round-trip fails for an entry, the original object is kept:
--its getter hooks still spoof reads.
function after(hook, param)
	local list = param:getResult()
	if list == nil then
		return false
	end

	local size = list:size()
	local fake = luajava.newInstance("java.util.ArrayList")
	for i = 0, size - 1 do
		local info = list:get(i)
		if info ~= nil then
			local slot = info:getSimSlotIndex()
			if slot == 0 or slot == 1 then
				local suffix = slot + 1
				local subKey = "cell.unique.subscriber.id." .. suffix
				local subOld = "unique.gsm.subscription.id"
				if suffix == 2 then
					subOld = "unique.gsm.subscription.id.2"
				end
				if param:getSettingReMap(subKey, subOld) ~= nil then
					local rebuilt = rebuildFromProfile(param, info)
					if rebuilt == nil then
						rebuilt = info
					end
					fake:add(rebuilt)
				end
			end
		end
	end

	param:setResult(fake)
	return true, tostring(size), tostring(fake:size())
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
		log("getActiveSubscriptionInfoList: rebuild failed: " .. tostring(res2))
	end
	parcel:recycle()
	return rebuilt
end
