-- value_phone_number.lua
-- 0.7.40: reads the canonical name directly. The legacy hook-side alias
-- value.phone_number is bound to this canonical name in remap_settings.json,
-- so either spelling resolves; the canonical read also works if the resolver
-- was never loaded. One-line change vs upstream (237-byte blob 86b79356):
-- getSetting('value.phone_number') -> getSetting('cell.unique.phone.number.1').
function after(hook, param)
    local result = param:getResult()
    if result == nil then
        return false
    end

    local fake = param:getSetting('cell.unique.phone.number.1')
    param:setResult(fake)
    return true, result, fake
end
