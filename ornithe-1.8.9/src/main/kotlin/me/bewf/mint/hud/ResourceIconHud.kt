package me.bewf.mint.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import me.bewf.mint.config.MintConfig
import me.bewf.mint.util.ResourceTracker
import net.minecraft.item.Item
import net.minecraft.item.Items
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.height
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.layout.PolyInsets
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.ui.v1.item.PolyItemIcon

class ResourceIconHud : Hud("mint_resource_hud.json", "Resource Tracker", Hud.Category.PLAYER) {
    @Switch(title = "Show Iron", category = "General", subcategory = "Resources")
    var showIron: Boolean = true

    @Switch(title = "Show Gold", category = "General", subcategory = "Resources")
    var showGold: Boolean = true

    @Switch(title = "Show Diamond", category = "General", subcategory = "Resources")
    var showDiamond: Boolean = true

    @Switch(title = "Show Emerald", category = "General", subcategory = "Resources")
    var showEmerald: Boolean = true

    @Switch(
        title = "Horizontal Layout",
        description = "Off = vertical rows, on = a single horizontal row.",
        category = "General", subcategory = "Display"
    )
    var horizontalLayout: Boolean = false

    @Switch(
        title = "Compact Numbers",
        description = "Shortens numbers like 1200 to 1.2k.",
        category = "General", subcategory = "Display"
    )
    var compactNumbers: Boolean = true

    @Switch(
        title = "Hide When Zero",
        description = "Hides a resource if its total (inventory + ender chest) is 0.",
        category = "General", subcategory = "Display"
    )
    var hideWhenZero: Boolean = true

    @Switch(
        title = "Track Team Chest",
        description = "Tracks team chest. Note that the HUD will not update if a teammate takes something in or out of the chest.",
        category = "General", subcategory = "Display"
    )
    var trackTeamChest: Boolean = false

    @Switch(
        title = "Storage Colors",
        description = "Colors inventory, ender chest, total, and separators. Off = everything uses the Text Color from the designer.",
        category = "General", subcategory = "Colors"
    )
    var storageColors: Boolean = true

    @Color(title = "Inventory Color", category = "General", subcategory = "Colors")
    var inventoryColor: PolyColor = PolyColor(0xFFE8D9C2.toInt())

    @Color(title = "Ender Chest Color", category = "General", subcategory = "Colors")
    var enderChestColor: PolyColor = PolyColor(0xFFBE3FFF.toInt())

    @Color(title = "Team Chest Color", category = "General", subcategory = "Colors")
    var teamChestColor: PolyColor = PolyColor(0xFF55AAFF.toInt())

    @Color(title = "Total Color", category = "General", subcategory = "Colors")
    var totalColor: PolyColor = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Separator Color", category = "General", subcategory = "Colors")
    var separatorColor: PolyColor = PolyColor(0xFF787878.toInt())

    @Text(
        title = "Addition Label",
        description = "Character(s) used between inventory and ender chest counts.",
        category = "General", subcategory = "Labels"
    )
    var additionLabel: String = "+"

    @Text(
        title = "Equal Label",
        description = "Character(s) used between ender chest and total counts.",
        category = "General", subcategory = "Labels"
    )
    var equalLabel: String = ":"

    init {
        padLeft = 4f
        padRight = 4f
        padTop = 4f
        padBottom = 4f

        growthAnchor = HudAnchor.BottomLeft
        alignment = PolyAlign.BottomLeft

        staticWidth = false
        staticW = -1f
        staticH = -1f

        showShadow = true
    }

    override val description: String? =
        "Iron, gold, diamonds and emeralds across your inventory, ender chest and team chest."

    override fun showByDefault(): Boolean = true

    override fun defaultPosition(): Pair<Float, Float> = 10f to 10f

    override fun canMergeBackground(): Boolean = true

    override fun minimumSize(): Pair<Float, Float> =
        (padLeft + padRight + 2f) to (padTop + padBottom + 2f)

    private data class Seg(val text: String, val color: PolyColor?)

    private data class ResourceRow(val item: Item, val segs: List<Seg>)

    private data class Model(val rows: List<ResourceRow>, val horizontal: Boolean)

    private var modelState: MutableState<Model> = mutableStateOf(Model(emptyList(), false))

    private var visibleNow: Boolean = false

    override fun setup() {
        super.setup()
        update()
        reseedStaticSizeIfNeeded()
        captureStaticSizeDefaults()
    }

    override fun clone(): Hud = (super.clone() as ResourceIconHud).also {
        it.modelState = mutableStateOf(Model(emptyList(), false))
    }

    override fun update(): Boolean {
        val example = HudManager.isEditing || !isReal
        val model = buildModel(example)
        modelState.value = model

        val live = ResourceTracker.isActive() || MintConfig.INSTANCE?.showOutsideBedwars == true
        visibleNow = model.rows.isNotEmpty() && live
        return true
    }

    override fun shouldShow(): Boolean = visibleNow

    @Composable
    override fun Content() {
        val model = modelState.value
        val scale = textScale
        val fg = PolyColor(textColor, textChroma, textChromaSpeed)

        val padInsets = PolyInsets(padLeft, padTop, padRight, padBottom)
        val isStaticValid = staticWidth && staticW > 0f && staticH > 0f

        val bg = hudBackground()
        val outer =
            if (isStaticValid) bg.size(staticW, staticH).padding(padInsets)
            else bg.padding(padInsets)

        PolyBox(modifier = outer) {
            val inner = if (isStaticValid) PolyModifier.align(alignment) else PolyModifier
            if (model.horizontal) {
                PolyRow(gap = H_GAP * scale, modifier = inner) {
                    for (row in model.rows) ResourceLine(row, fg, scale)
                }
            } else {
                PolyColumn(modifier = inner) {
                    for (row in model.rows) ResourceLine(row, fg, scale)
                }
            }
        }
    }

    @Composable
    private fun ResourceLine(row: ResourceRow, fg: PolyColor, scale: Float) {
        PolyRow(gap = ICON_PAD * scale, modifier = PolyModifier.height(ROW_H * scale)) {
            PolyItemIcon(row.item, ICON * scale, PolyModifier.align(PolyAlign.Left))
            PolyRow(modifier = PolyModifier.align(PolyAlign.Left)) {
                for (seg in row.segs) Segment(seg, fg, scale)
            }
        }
    }

    @Composable
    private fun Segment(seg: Seg, fg: PolyColor, scale: Float) {
        val color = seg.color ?: fg
        val text = when (caseType) {
            1 -> seg.text.uppercase()
            2 -> seg.text.lowercase()
            else -> seg.text
        }
        val mod = PolyModifier.align(PolyAlign.Left)

        if (font == Font.Poppins) {
            PolyText(
                text = text,
                color = color,
                fontSize = 8f * scale,
                shadow = showShadow,
                shadowColor = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed),
                shadowOffset = shadowOffsetX,
                font = getPoppinsFontName(),
                modifier = mod,
            )
        } else {
            val formatted = buildString {
                if (textBold) append("§l")
                if (textItalic) append("§o")
                if (textUnderline) append("§n")
                append(text)
                if (textBold || textItalic || textUnderline) append("§r")
            }
            PolyMcText(
                text = formatted,
                color = color,
                shadow = showShadow,
                scale = scale,
                modifier = mod,
            )
        }
    }

    private fun buildModel(example: Boolean): Model {
        val teamChest = trackTeamChest

        fun n(real: Int) = if (example) 10 else real
        fun z(real: Int) = if (example) 0 else real
        fun t(real: Int) = if (example) 0 else if (teamChest) real else 0

        val ironInv = n(ResourceTracker.ironInv)
        val goldInv = n(ResourceTracker.goldInv)
        val diaInv = n(ResourceTracker.diaInv)
        val emeInv = n(ResourceTracker.emeInv)

        val ironEc = z(ResourceTracker.ironEc)
        val goldEc = z(ResourceTracker.goldEc)
        val diaEc = z(ResourceTracker.diaEc)
        val emeEc = z(ResourceTracker.emeEc)

        val ironTc = t(ResourceTracker.teamChestIron)
        val goldTc = t(ResourceTracker.teamChestGold)
        val diaTc = t(ResourceTracker.teamChestDiamond)
        val emeTc = t(ResourceTracker.teamChestEmerald)

        val rows = ArrayList<ResourceRow>(4)

        fun add(show: Boolean, item: Item, inv: Int, ec: Int, tc: Int) {
            if (!show) return
            if (!example && hideWhenZero && inv + ec + tc == 0) return
            rows.add(ResourceRow(item, segments(inv, ec, tc)))
        }

        add(showIron, Items.IRON_INGOT, ironInv, ironEc, ironTc)
        add(showGold, Items.GOLD_INGOT, goldInv, goldEc, goldTc)
        add(showDiamond, Items.DIAMOND, diaInv, diaEc, diaTc)
        add(showEmerald, Items.EMERALD, emeInv, emeEc, emeTc)

        if (rows.isEmpty() && example) {
            rows.add(ResourceRow(Items.IRON_INGOT, segments(10, 0, 0)))
            rows.add(ResourceRow(Items.GOLD_INGOT, segments(10, 0, 0)))
            rows.add(ResourceRow(Items.DIAMOND, segments(10, 0, 0)))
            rows.add(ResourceRow(Items.EMERALD, segments(10, 0, 0)))
        }

        return Model(rows, horizontalLayout)
    }

    private fun segments(inv: Int, ec: Int, tc: Int): List<Seg> {
        val add = additionLabel
        val eq = equalLabel

        val invS = formatCount(inv)
        val ecS = formatCount(ec)
        val tcS = formatCount(tc)
        val totalS = formatCount(inv + ec + tc)

        val hasInv = inv > 0
        val hasEc = ec > 0
        val hasTc = tc > 0

        if (!storageColors) {
            val sb = StringBuilder()
            if (hasInv) sb.append(invS)
            if (hasEc) {
                if (hasInv) sb.append(add)
                sb.append(ecS)
            }
            if (hasTc) {
                if (hasInv || hasEc) sb.append(add)
                sb.append(tcS)
            }
            sb.append(eq).append(totalS)
            return listOf(Seg(sb.toString(), null))
        }

        val invC = snap(inventoryColor)
        val ecC = snap(enderChestColor)
        val tcC = snap(teamChestColor)
        val totalC = snap(totalColor)
        val sepC = snap(separatorColor)

        if (hasInv && !hasEc && !hasTc) return listOf(Seg(invS, invC))
        if (!hasInv && hasEc && !hasTc) return listOf(Seg(ecS, ecC))
        if (!hasInv && !hasEc && hasTc) return listOf(Seg(tcS, tcC))
        if (!hasInv && !hasEc && !hasTc) return listOf(Seg("0", totalC))

        val out = ArrayList<Seg>(8)
        if (hasInv) out.add(Seg(invS, invC))
        if (hasEc) {
            if (hasInv) out.add(Seg(add, sepC))
            out.add(Seg(ecS, ecC))
        }
        if (hasTc) {
            if (hasInv || hasEc) out.add(Seg(add, sepC))
            out.add(Seg(tcS, tcC))
        }
        out.add(Seg(eq, sepC))
        out.add(Seg(totalS, totalC))
        return out
    }

    private fun snap(c: PolyColor): PolyColor = PolyColor(c.rawArgb, c.chroma, c.chromaSpeed)

    private fun formatCount(n: Int): String {
        if (!compactNumbers) return n.toString()
        if (n < 1000) return n.toString()

        val k = n / 1000.0
        if (k < 10.0) {
            val oneDec = Math.round(k * 10.0) / 10.0
            var s = oneDec.toString()
            if (s.endsWith(".0")) s = s.substring(0, s.length - 2)
            return s + "k"
        }
        return Math.round(k).toString() + "k"
    }

    private companion object {
        const val ICON = 16f
        const val ROW_H = 18f
        const val ICON_PAD = 2f
        const val H_GAP = 3f
    }
}
