--SubscriptionInfo.CREATOR:createFromParcel
--Runs after the system (or a parcel round-trip) rebuilds a
--SubscriptionInfo from a parcel. The parcel was written outside this
--process by the system, so its fields still hold the REAL subscription.
--This hook overwrites the fields that have a profile value, using the
--slot the object itself reports. Field layout follows
--id_subscriptioninfo_writetoparcel.lua, including its SDK branches.
--Field assignment is best-effort (pcall): SubscriptionInfo fields are
--private, so if the engine cannot set them this hook degrades to a
--no-op and read-path spoofing still comes from the getter hooks
--(getIccId, getNumber, getMcc, ...) and from writeToParcel.
--Same pattern as id_configuration_createfromparcel.lua, which assigns
--fields on the object createFromParcel returns.
function after(hook, param)
	local result = param:getResult()
	if result == nil then
		return false
	end

	local slot = result:getSimSlotIndex()
	if slot ~= 0 and slot ~= 1 then
		return false
	end
	local suffix = slot + 1
	local code = param:getSDKCode()

	--LuaJava reaches a Java field only through a literal "obj.field"
	--assignment, so each field gets its own setter closure.
	local function setf(name, assign, value)
		if value == nil then
			return
		end
		local ok, err = pcall(function() assign(value) end)
		if not ok then
			log("createFromParcel: cannot set " .. name .. ": " .. tostring(err))
		end
	end

	--String / CharSequence fields, present on every SDK.
	local iccKey = "cell.unique.sim.icc.id." .. suffix
	local iccOld = "unique.gsm.sim.serial"
	if suffix == 2 then
		iccOld = "unique.gsm.sim.serial.2"
	end
	setf("mIccId", function(v) result.mIccId = v end, param:getSettingReMap(iccKey, iccOld))
	setf("mSimSlotIndex", function(v) result.mSimSlotIndex = v end, slot)

	local nameKey = "cell.operator.name." .. suffix
	local nameOld = "gsm.network.carrier"
	if suffix == 2 then
		nameOld = "gsm.network.carrier.2"
	end
	setf("mCarrierName", function(v) result.mCarrierName = v end, param:getSettingReMap(nameKey, nameOld))
	local dispKey = "cell.display.name." .. suffix
	local dispOld = "gsm.setting.display.name"
	if suffix == 2 then
		dispOld = "gsm.setting.display.name.2"
	end
	setf("mDisplayName", function(v) result.mDisplayName = v end, param:getSettingReMap(dispKey, dispOld))

	local isoKey = "cell.sim.country.iso." .. suffix
	setf("mCountryIso", function(v) result.mCountryIso = v end, param:getSettingReMap(isoKey, "zone.country.iso2"))

	local numKey = "cell.unique.phone.number." .. suffix
	local numOld = "unique.gsm.phone.number"
	if suffix == 2 then
		numOld = "unique.gsm.phone.number.2"
	end
	setf("mNumber", function(v) result.mNumber = v end, param:getSettingReMap(numKey, numOld))

	--MCC / MNC: String on 10.0+, int on 9.0 and lower.
	local mccKey = "cell.operator.mcc." .. suffix
	local mccOld = "gsm.operator.mcc"
	local mncKey = "cell.operator.mnc." .. suffix
	local mncOld = "gsm.operator.mnc"
	if suffix == 2 then
		mccOld = "gsm.operator.mcc.2"
		mncOld = "gsm.operator.mnc.2"
	end
	local mcc = param:getSettingReMap(mccKey, mccOld)
	local mnc = param:getSettingReMap(mncKey, mncOld)
	if code > 28 then
		setf("mMcc", function(v) result.mMcc = v end, mcc)
		setf("mMnc", function(v) result.mMnc = v end, mnc)
	else
		if mcc ~= nil and tonumber(mcc) ~= nil then
			setf("mMcc", function(v) result.mMcc = v end, tonumber(mcc))
		end
		if mnc ~= nil and tonumber(mnc) ~= nil then
			setf("mMnc", function(v) result.mMnc = v end, tonumber(mnc))
		end
	end

	--Embedded / opportunistic flags.
	local embKey = "cell.sim.is.embedded." .. suffix
	setf("mIsEmbedded", function(v) result.mIsEmbedded = v end, param:getSettingBool(embKey, result:isEmbedded()))
	if code > 28 then
		local oppKey = "cell.data.is.opportunistic." .. suffix
		setf("mIsOpportunistic", function(v) result.mIsOpportunistic = v end, param:getSettingBool(oppKey, result:isOpportunistic()))
	end

	--Carrier id: same value the getCarrierId hook returns (operator id,
	--or MCC+MNC when no operator id is set).
	if code > 28 then
		local idKey = "cell.operator.id." .. suffix
		local idOld = "gsm.operator.id"
		if suffix == 2 then
			idOld = "gsm.operator.id.2"
		end
		local carrier = param:getSettingReMap(idKey, idOld)
		if carrier == nil and mcc ~= nil and mnc ~= nil then
			carrier = mcc .. mnc
		end
		if carrier ~= nil and tonumber(carrier) ~= nil then
			setf("mCarrierId", function(v) result.mCarrierId = v end, tonumber(carrier))
		end
	end

	--Subscription type, port index, usage setting, data roaming:
	--only overwritten when the profile actually has a value.
	if code > 28 then
		local typeKey = "cell.sim.type." .. suffix
		local typeOld = "gsm.sim.type"
		if suffix == 2 then
			typeOld = "gsm.sim.type.2"
		end
		local subType = param:getSettingReMap(typeKey, typeOld)
		if subType ~= nil and tonumber(subType) ~= nil then
			setf("mSubscriptionType", function(v) result.mSubscriptionType = v end, tonumber(subType))
		end
	end
	if code >= 33 then
		local port = param:getSetting("gsm.index.sim.port")
		if port ~= nil and tonumber(port) ~= nil then
			setf("mPortIndex", function(v) result.mPortIndex = v end, tonumber(port))
		end
		local usage = param:getSetting("gsm.subscription.usage.setting")
		if usage ~= nil and tonumber(usage) ~= nil then
			setf("mUsageSetting", function(v) result.mUsageSetting = v end, tonumber(usage))
		end
	end
	local roamKey = "cell.data.roaming.enabled.flag." .. suffix
	local roaming = param:getSetting(roamKey)
	if roaming == nil then
		roaming = param:getSetting("cell.data.roaming.enabled." .. suffix)
	end
	if roaming ~= nil then
		if roaming == "true" or roaming == "1" then
			setf("mDataRoaming", function(v) result.mDataRoaming = v end, 1)
		elseif roaming == "false" or roaming == "0" then
			setf("mDataRoaming", function(v) result.mDataRoaming = v end, 0)
		end
	end

	--Deliberately not overwritten: mId (the int subscription id; the
	--profile IMSI does not fit in an int, and getSubscriptionId() is
	--already hooked), mCardId / mCardString (no canonical profile key),
	--mIconTint, mDisplayNameSource / mNameSource, mIconBitmap,
	--mNativeAccessRules, mGroupUuid, mIsGroupDisabled, mProfileClass,
	--mEhplmns, mHplmns, mGroupOwner, mCarrierConfigAccessRules,
	--mAreUiccApplicationsEnabled. Those keep their parcelled values.

	return true, "slot=" .. tostring(slot), "spoofed"
end
