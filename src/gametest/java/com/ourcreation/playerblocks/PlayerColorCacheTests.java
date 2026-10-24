package com.ourcreation.playerblocks;

import com.ourcreation.playerblocks.block.entity.PlayerChestEntity;
import com.ourcreation.playerblocks.block.PlayerConcreteBlock;
import com.ourcreation.playerblocks.block.entity.PlayerConcreteEntity;
import com.ourcreation.playerblocks.color.PlayerColors;
import com.ourcreation.playerblocks.color.TeamColors;
import com.ourcreation.playerblocks.registry.ModContent;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(PlayerBlocks.MODID)
@PrefixGameTestTemplate(false)
public final class PlayerColorCacheTests {

    @GameTest(template = "empty")
    public static void unchangedColorsSkipInvalidationButPersistLimitedUses(GameTestHelper helper) {
        var colors = new PlayerColors();
        var owner = UUID.randomUUID();
        int slot = colors.slot(owner);
        colors.set(owner, 0x234567);
        helper.assertTrue(colors.paletteChanged(), "A new color publishes its palette");
        long revision = colors.colorRevision();
        colors.setDirty(false);
        for (int index = 0; index < 2000; index++) {
            colors.set(owner, 0xFF234567);
        }
        colors.reset(UUID.randomUUID());
        colors.resetTeam("cache:missing");
        helper.assertTrue(!colors.isDirty() && colors.colorRevision() == revision && !colors.paletteChanged(),
                "Identical normalized overrides and missing resets do not rebuild, resend, or dirty saved data");
        var snapshot = colors.palette();
        snapshot.putInt(Integer.toString(slot), 0);
        helper.assertTrue(colors.palette().getInt(Integer.toString(slot)) == 0x234567 && !colors.paletteChanged(),
                "Recipients cannot mutate the cached palette through a snapshot");
        colors.applyPersonalChange(owner, 0x234567, true);
        helper.assertTrue(colors.isDirty() && colors.hexChanges(owner) == 1 && colors.colorRevision() == revision,
                "A limited confirmation persists its use even when the RGB stays unchanged");
        var restored = PlayerColors.load(colors.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        helper.assertTrue(restored.hexChanges(owner) == 1 && restored.personalColor(owner) == 0x234567,
                "No-op confirmations retain their use counter after reload");
        var defaults = new PlayerColors();
        defaults.setDirty(false);
        defaults.applyPersonalChange(owner, null, true);
        helper.assertTrue(defaults.isDirty() && defaults.hexChanges(owner) == 1,
                "Resetting an already-default color still persists a limited use");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void teamAndPersonalChangesInvalidateCachedTints(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var colors = new PlayerColors();
        var owner = UUID.randomUUID();
        var leader = UUID.randomUUID();
        var membership = new AtomicReference<>(new TeamColors.Team("cache:team", leader));
        TeamColors.registerProvider("cache:revisions",
                (world, member) -> world == server && member.equals(owner) ? membership.get() : null);
        int slot = colors.slot(owner);
        colors.set(leader, 0x123456);
        colors.refreshTeam(server, owner);
        helper.assertTrue(colors.color(owner) == 0x123456, "An owner inherits its leader's personal override");
        long revision = colors.colorRevision();
        colors.refreshTeam(server, owner);
        helper.assertTrue(colors.colorRevision() == revision,
                "Resolving unchanged membership leaves cached tints valid");
        colors.setTeam("cache:team", 0xABCDEF);
        helper.assertTrue(colors.colorRevision() != revision && colors.color(owner) == 0xABCDEF,
                "A team override invalidates member tints");
        colors.paletteChanged();
        revision = colors.colorRevision();
        colors.setDirty(false);
        colors.setTeam("cache:team", 0xFFABCDEF);
        helper.assertTrue(!colors.isDirty() && colors.colorRevision() == revision && !colors.paletteChanged(),
                "Repeating a normalized team override keeps the palette clean");
        colors.resetTeam("cache:team");
        colors.set(leader, 0x6789AB);
        helper.assertTrue(colors.palette().getInt(Integer.toString(slot)) == 0x6789AB && colors.paletteChanged(),
                "Reading an inherited palette cannot consume the pending leader recolor");
        revision = colors.colorRevision();
        membership.set(null);
        colors.refreshTeam(server, owner);
        helper.assertTrue(colors.colorRevision() != revision && colors.color(owner) == colors.personalColor(owner),
                "Leaving a team invalidates the cached inheritance");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cachedEntitiesRecoverAfterStatePastesAndNbtReloads(GameTestHelper helper) {
        var level = helper.getLevel();
        var colors = PlayerColors.get(level.getServer());
        var owner = UUID.randomUUID();
        colors.set(owner, 0x285D9A);
        var concretePos = helper.absolutePos(new BlockPos(1, 2, 2));
        level.setBlock(concretePos, ModContent.CONCRETE.get().defaultBlockState(), 2);
        var concrete = (PlayerConcreteEntity) level.getBlockEntity(concretePos);
        concrete.link(owner);
        int slot = colors.slot(owner);
        level.setBlock(concretePos, level.getBlockState(concretePos).setValue(PlayerConcreteBlock.COLOR_ID, 0), 2);
        helper.assertTrue(level.getBlockEntity(concretePos) == concrete,
                "A state-only paste preserves the entity cache");
        concrete.refresh();
        helper.assertTrue(level.getBlockState(concretePos).getValue(PlayerConcreteBlock.COLOR_ID) == slot,
                "Slot-zero migration still runs when the color revision is unchanged");
        var chestPos = helper.absolutePos(new BlockPos(3, 2, 2));
        level.setBlock(chestPos, ModContent.CHEST.get().defaultBlockState(), 2);
        var chest = (PlayerChestEntity) level.getBlockEntity(chestPos);
        chest.link(owner);
        var stale = chest.getUpdateTag(level.registryAccess());
        stale.putInt("color", 0);
        chest.loadWithComponents(stale, level.registryAccess());
        helper.assertTrue(chest.color() == 0, "Loading old NBT replaces the cached tint");
        chest.refresh();
        helper.assertTrue(chest.color() == 0x285D9A, "Reloading NBT clears revision state before the next refresh");
        colors.set(owner, 0x6DBE31);
        chest.refresh();
        helper.assertTrue(chest.color() == 0x6DBE31, "An existing native entity observes the next color revision");
        helper.succeed();
    }
}
