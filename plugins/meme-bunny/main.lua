-- 恶搞兔表情包（Lua 脚本插件）
--
-- 资源在 resources/ 下：
--   resources/icon.webp        插件图标
--   resources/emojis/*.jpg     表情图片（宿主渲染）
-- 宿主渲染图片，Lua 只提供路径（host.resource.path）
-- 文件名使用 ASCII，避免 zip/跨平台中文路径乱码导致 list 为空。

local plugin = {}

local CATEGORY = "恶搞兔"

-- file -> 展示名（insertText 用展示名）
local EMOJIS = {
    { file = "01.jpg", text = "立刻走" },
    { file = "02.jpg", text = "你给老子爬" },
    { file = "03.jpg", text = "你竟然赶我走" },
    { file = "04.jpg", text = "你走" },
    { file = "05.jpg", text = "行了行了，你不走我走" },
    { file = "06.jpg", text = "再不走就打死你" },
}

function plugin.getCategories()
    return { CATEGORY }
end

function plugin.getEmojis(query)
    local searchText = query.keyword or ""
    local topK = query.topK or 100
    local list = {}
    for i, item in ipairs(EMOJIS) do
        local name = item.text
        local path = host.resource.path("emojis/" .. item.file)
        if path and (searchText == "" or string.find(name, searchText, 1, true)) then
            table.insert(list, {
                id = "emoji_" .. (i - 1),
                text = name,
                insertText = "[表情" .. name .. "]",
                imageUrl = path,
            })
        end
        if #list >= topK then break end
    end
    return list
end

function plugin.getIcon()
    return { assetName = "icon.webp" }
end

return plugin
